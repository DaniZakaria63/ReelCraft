#include "sinet.h"
#include "tflite_backend.h"

#include <cmath>
#include <cstring>
#include <algorithm>

static const int MODEL_INPUT_SIZE = 256;

struct SINetModel {
    TFLiteBackend* backend = nullptr;
    ModelType type = ModelType::SINet;
    int model_w = MODEL_INPUT_SIZE;
    int model_h = MODEL_INPUT_SIZE;
};

static void bilinear_resize_rgba_to_rgb_float(
    const uint8_t* src, int src_w, int src_h,
    float* dst, int dst_w, int dst_h)
{
    for (int dy = 0; dy < dst_h; dy++) {
        float src_y_f = (float)dy * (src_h - 1) / (dst_h - 1);
        if (dst_h == 1) src_y_f = 0.0f;
        int sy0 = (int)src_y_f;
        int sy1 = std::min(sy0 + 1, src_h - 1);
        float fy = src_y_f - sy0;

        for (int dx = 0; dx < dst_w; dx++) {
            float src_x_f = (float)dx * (src_w - 1) / (dst_w - 1);
            if (dst_w == 1) src_x_f = 0.0f;
            int sx0 = (int)src_x_f;
            int sx1 = std::min(sx0 + 1, src_w - 1);
            float fx = src_x_f - sx0;

            int src_idx00 = (sy0 * src_w + sx0) * 4;
            int src_idx01 = (sy0 * src_w + sx1) * 4;
            int src_idx10 = (sy1 * src_w + sx0) * 4;
            int src_idx11 = (sy1 * src_w + sx1) * 4;

            int dst_idx = (dy * dst_w + dx) * 3;

            for (int c = 0; c < 3; c++) {
                float v00 = src[src_idx00 + c];
                float v01 = src[src_idx01 + c];
                float v10 = src[src_idx10 + c];
                float v11 = src[src_idx11 + c];

                float top = v00 + (v01 - v00) * fx;
                float bot = v10 + (v11 - v10) * fx;
                dst[dst_idx + c] = (top + (bot - top) * fy) / 255.0f;
            }
        }
    }
}

static void bilinear_upscale_mask(
    const float* src, int src_w, int src_h,
    float* dst, int dst_w, int dst_h)
{
    for (int dy = 0; dy < dst_h; dy++) {
        float src_y_f = (float)dy * (src_h - 1) / (dst_h - 1);
        if (dst_h == 1) src_y_f = 0.0f;
        int sy0 = (int)src_y_f;
        int sy1 = std::min(sy0 + 1, src_h - 1);
        float fy = src_y_f - sy0;

        for (int dx = 0; dx < dst_w; dx++) {
            float src_x_f = (float)dx * (src_w - 1) / (dst_w - 1);
            if (dst_w == 1) src_x_f = 0.0f;
            int sx0 = (int)src_x_f;
            int sx1 = std::min(sx0 + 1, src_w - 1);
            float fx = src_x_f - sx0;

            int idx00 = sy0 * src_w + sx0;
            int idx01 = sy0 * src_w + sx1;
            int idx10 = sy1 * src_w + sx0;
            int idx11 = sy1 * src_w + sx1;

            float v = src[idx00] * (1-fx)*(1-fy)
                    + src[idx01] * fx*(1-fy)
                    + src[idx10] * (1-fx)*fy
                    + src[idx11] * fx*fy;

            dst[dy * dst_w + dx] = v;
        }
    }
}

SINetModel* sinet_create(const uint8_t* model_data, size_t model_size, ModelType type) {
    if (!model_data || model_size == 0) return nullptr;
    auto* sm = new SINetModel();
    sm->type = type;

    sm->backend = tflite_create(model_data, model_size);
    if (!sm->backend) {
        sinet_destroy(sm);
        return nullptr;
    }

    int32_t input_dims[4] = {1, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, 3};
    if (!tflite_resize_input(sm->backend, input_dims, 4)) {
        sinet_destroy(sm);
        return nullptr;
    }
    if (!tflite_allocate_tensors(sm->backend)) {
        sinet_destroy(sm);
        return nullptr;
    }

    return sm;
}

void sinet_destroy(SINetModel* sm) {
    if (!sm) return;
    tflite_destroy(sm->backend);
    delete sm;
}

bool sinet_segment(SINetModel* sm,
                    const uint8_t* rgba, int width, int height,
                    float* out_mask)
{
    if (!sm || !sm->backend || !rgba || !out_mask || width <= 0 || height <= 0) {
        return false;
    }

    size_t input_pixels = (size_t)sm->model_w * sm->model_h;
    float* input_float = new float[input_pixels * 3];

    bilinear_resize_rgba_to_rgb_float(
        rgba, width, height,
        input_float, sm->model_w, sm->model_h);

    if (!tflite_copy_to_input_float(sm->backend, input_float, input_pixels * 3)) {
        delete[] input_float;
        return false;
    }
    delete[] input_float;

    if (!tflite_invoke(sm->backend)) return false;

    size_t output_size = tflite_output_byte_size(sm->backend, 0) / sizeof(float);
    float* raw_output = new float[output_size];
    if (!tflite_copy_from_output_float(sm->backend, raw_output, output_size, 0)) {
        delete[] raw_output;
        return false;
    }

    size_t mask_pixels = (size_t)sm->model_w * sm->model_h;
    float* small_mask = new float[mask_pixels];

    if (sm->type == ModelType::SINet) {
        int output_channels = (int)(output_size / mask_pixels);
        for (size_t i = 0; i < mask_pixels; i++) {
            float bg = raw_output[i * output_channels + 0];
            float fg = raw_output[i * output_channels + 1];
            float max_val = std::max(bg, fg);
            float sum = std::exp(bg - max_val) + std::exp(fg - max_val);
            small_mask[i] = std::exp(fg - max_val) / sum;
        }
    } else {
        for (size_t i = 0; i < mask_pixels; i++) {
            float v = raw_output[i];
            small_mask[i] = 1.0f / (1.0f + std::exp(-v));
        }
    }

    bilinear_upscale_mask(small_mask, sm->model_w, sm->model_h,
                           out_mask, width, height);

    delete[] raw_output;
    delete[] small_mask;
    return true;
}
