#pragma once

#include <vector>
#include <cmath>
#include <algorithm>
#include <cstring>

struct FeatherParams {
    int feather_radius = 15;
    float inner_threshold = 0.0f;
    float outer_threshold = 0.0f;
};

static void separable_box_blur_x(const float* src, float* dst, int w, int h, int radius) {
    int size = 2 * radius + 1;
    float scale = 1.0f / size;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            float sum = 0.0f;
            int count = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                int cx = x + dx;
                if (cx >= 0 && cx < w) {
                    sum += src[y * w + cx];
                    count++;
                }
            }
            dst[y * w + x] = sum / count;
        }
    }
}

static void separable_box_blur_y(const float* src, float* dst, int w, int h, int radius) {
    int size = 2 * radius + 1;
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            float sum = 0.0f;
            int count = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                int cy = y + dy;
                if (cy >= 0 && cy < h) {
                    sum += src[cy * w + x];
                    count++;
                }
            }
            dst[y * w + x] = sum / count;
        }
    }
}

static void box_blur(const float* src, float* dst, int w, int h, int radius) {
    std::vector<float> temp(w * h);
    separable_box_blur_x(src, temp.data(), w, h, radius);
    separable_box_blur_y(temp.data(), dst, w, h, radius);
}

static void subtract_from_one(const float* src, float* dst, size_t size) {
    for (size_t i = 0; i < size; i++) {
        dst[i] = 1.0f - src[i];
    }
}

static void multiply(const float* a, const float* b, float* dst, size_t size) {
    for (size_t i = 0; i < size; i++) {
        dst[i] = a[i] * b[i];
    }
}

static void add(const float* a, const float* b, float* dst, size_t size) {
    for (size_t i = 0; i < size; i++) {
        dst[i] = a[i] + b[i];
    }
}

static void threshold_0_1(const float* src, float* dst, size_t size, float thresh) {
    for (size_t i = 0; i < size; i++) {
        dst[i] = src[i] > thresh ? 1.0f : 0.0f;
    }
}

std::vector<float> generateFeatherMask(
    const std::vector<float>& binary_mask,
    int mask_width, int mask_height,
    const FeatherParams& params) {

    size_t size = binary_mask.size();
    std::vector<float> feather(size);

    if (params.feather_radius <= 0) {
        for (size_t i = 0; i < size; i++) {
            feather[i] = binary_mask[i];
        }
        return feather;
    }

    int r = std::max(1, params.feather_radius);

    // Signed distance approximation via repeated box blurs:
    // d = blur(mask) - blur(1 - mask)
    // This gives a smooth gradient across the mask boundary

    std::vector<float> blurred(size);
    std::vector<float> inv_blurred(size);
    std::vector<float> inverted(size);
    std::vector<float> scratch(size);

    // blur(mask)
    box_blur(binary_mask.data(), blurred.data(), mask_width, mask_height, r);
    // blur again for smoother result
    box_blur(blurred.data(), scratch.data(), mask_width, mask_height, r);
    std::swap(blurred, scratch);

    // blur(1 - mask)
    subtract_from_one(binary_mask.data(), inverted.data(), size);
    box_blur(inverted.data(), inv_blurred.data(), mask_width, mask_height, r);
    box_blur(inv_blurred.data(), scratch.data(), mask_width, mask_height, r);
    std::swap(inv_blurred, scratch);

    // Signed distance: d = blurred(mask) - blurred(1-mask)
    // Range: [-1, 1] where positive = inside mask
    for (size_t i = 0; i < size; i++) {
        float d = blurred[i] - inv_blurred[i];
        // Sigmoid: smooth step at d = 0
        float sigma = static_cast<float>(r) / 3.0f;
        feather[i] = 1.0f / (1.0f + std::exp(-d / sigma * 4.0f));
    }

    return feather;
}
