#include "tflite_backend.h"

#include <algorithm>
#include <cstring>
#include <thread>

#include "tflite/core/c/c_api.h"
#include "tflite/delegates/xnnpack/xnnpack_delegate.h"

extern "C" {
extern void TfLiteInterpreterOptionsSetUseNNAPI(
    TfLiteInterpreterOptions* options, bool enable);
}

struct TFLiteBackend {
    TfLiteModel* model = nullptr;
    TfLiteInterpreterOptions* options = nullptr;
    TfLiteInterpreter* interpreter = nullptr;
    TfLiteDelegate* xnnpack_delegate = nullptr;
};

TFLiteBackend* tflite_create(const uint8_t* model_data, size_t model_size,
                              int delegate_flags, int num_threads) {
    if (!model_data || model_size == 0) return nullptr;
    auto* tb = new TFLiteBackend();

    tb->model = TfLiteModelCreate(model_data, model_size);
    if (!tb->model) { tflite_destroy(tb); return nullptr; }

    tb->options = TfLiteInterpreterOptionsCreate();
    if (!tb->options) { tflite_destroy(tb); return nullptr; }

    if (num_threads <= 0) {
        int cores = std::max(1, (int)std::thread::hardware_concurrency());
        num_threads = std::max(2, cores / 2);
    }

    TfLiteInterpreterOptionsSetNumThreads(tb->options, num_threads);

    if (delegate_flags & TFLITE_DELEGATE_NNAPI) {
        TfLiteInterpreterOptionsSetUseNNAPI(tb->options, true);
    }

    if (delegate_flags & TFLITE_DELEGATE_XNNPACK) {
        TfLiteXNNPackDelegateOptions opts = TfLiteXNNPackDelegateOptionsDefault();
        opts.num_threads = num_threads;
        tb->xnnpack_delegate = TfLiteXNNPackDelegateCreate(&opts);
        if (tb->xnnpack_delegate) {
            TfLiteInterpreterOptionsAddDelegate(tb->options, tb->xnnpack_delegate);
        }
    }

    tb->interpreter = TfLiteInterpreterCreate(tb->model, tb->options);
    if (!tb->interpreter) { tflite_destroy(tb); return nullptr; }

    if (TfLiteInterpreterAllocateTensors(tb->interpreter) != kTfLiteOk) {
        tflite_destroy(tb);
        return nullptr;
    }

    return tb;
}

void tflite_destroy(TFLiteBackend* tb) {
    if (!tb) return;
    TfLiteInterpreterDelete(tb->interpreter);
    TfLiteInterpreterOptionsDelete(tb->options);
    TfLiteModelDelete(tb->model);
    TfLiteXNNPackDelegateDelete(tb->xnnpack_delegate);
    delete tb;
}

int tflite_input_count(TFLiteBackend* tb) {
    if (!tb || !tb->interpreter) return 0;
    return TfLiteInterpreterGetInputTensorCount(tb->interpreter);
}

int tflite_output_count(TFLiteBackend* tb) {
    if (!tb || !tb->interpreter) return 0;
    return TfLiteInterpreterGetOutputTensorCount(tb->interpreter);
}

bool tflite_get_input_dims(TFLiteBackend* tb, int32_t* out_dims, int max_len) {
    if (!tb || !tb->interpreter || !out_dims) return false;
    const TfLiteTensor* t = TfLiteInterpreterGetInputTensor(tb->interpreter, 0);
    if (!t) return false;
    int n = TfLiteTensorNumDims(t);
    if (n > max_len) n = max_len;
    for (int i = 0; i < n; i++) out_dims[i] = TfLiteTensorDim(t, i);
    return true;
}

bool tflite_get_output_dims(TFLiteBackend* tb, int32_t* out_dims, int max_len, int index) {
    if (!tb || !tb->interpreter || !out_dims) return false;
    const TfLiteTensor* t = TfLiteInterpreterGetOutputTensor(tb->interpreter, index);
    if (!t) return false;
    int n = TfLiteTensorNumDims(t);
    if (n > max_len) n = max_len;
    for (int i = 0; i < n; i++) out_dims[i] = TfLiteTensorDim(t, i);
    return true;
}

bool tflite_resize_input(TFLiteBackend* tb, const int32_t* dims, int dims_count) {
    if (!tb || !tb->interpreter || !dims) return false;
    return TfLiteInterpreterResizeInputTensor(tb->interpreter, 0, dims, dims_count) == kTfLiteOk;
}

bool tflite_allocate_tensors(TFLiteBackend* tb) {
    if (!tb || !tb->interpreter) return false;
    return TfLiteInterpreterAllocateTensors(tb->interpreter) == kTfLiteOk;
}

bool tflite_invoke(TFLiteBackend* tb) {
    if (!tb || !tb->interpreter) return false;
    return TfLiteInterpreterInvoke(tb->interpreter) == kTfLiteOk;
}

bool tflite_copy_to_input_float(TFLiteBackend* tb, const float* data, size_t count) {
    if (!tb || !tb->interpreter || !data) return false;
    TfLiteTensor* t = TfLiteInterpreterGetInputTensor(tb->interpreter, 0);
    if (!t) return false;
    return TfLiteTensorCopyFromBuffer(t, data, count * sizeof(float)) == kTfLiteOk;
}

bool tflite_copy_from_output_float(TFLiteBackend* tb, float* data, size_t count, int output_index) {
    if (!tb || !tb->interpreter || !data) return false;
    const TfLiteTensor* t = TfLiteInterpreterGetOutputTensor(tb->interpreter, output_index);
    if (!t) return false;
    return TfLiteTensorCopyToBuffer(t, data, count * sizeof(float)) == kTfLiteOk;
}

int tflite_input_type(TFLiteBackend* tb) {
    if (!tb || !tb->interpreter) return 0;
    const TfLiteTensor* t = TfLiteInterpreterGetInputTensor(tb->interpreter, 0);
    if (!t) return 0;
    return (int)TfLiteTensorType(t);
}

int tflite_output_type(TFLiteBackend* tb, int index) {
    if (!tb || !tb->interpreter) return 0;
    const TfLiteTensor* t = TfLiteInterpreterGetOutputTensor(tb->interpreter, index);
    if (!t) return 0;
    return (int)TfLiteTensorType(t);
}

size_t tflite_output_byte_size(TFLiteBackend* tb, int index) {
    if (!tb || !tb->interpreter) return 0;
    const TfLiteTensor* t = TfLiteInterpreterGetOutputTensor(tb->interpreter, index);
    if (!t) return 0;
    return TfLiteTensorByteSize(t);
}
