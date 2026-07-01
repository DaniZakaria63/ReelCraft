#pragma once

#include <cstddef>
#include <cstdint>

constexpr int TFLITE_DELEGATE_XNNPACK = 1 << 0;
constexpr int TFLITE_DELEGATE_NNAPI   = 1 << 1;

struct TFLiteBackend;

TFLiteBackend* tflite_create(const uint8_t* model_data, size_t model_size,
                              int delegate_flags = 0);
void tflite_destroy(TFLiteBackend* tb);

int tflite_input_count(TFLiteBackend* tb);
int tflite_output_count(TFLiteBackend* tb);
bool tflite_get_input_dims(TFLiteBackend* tb, int32_t* out_dims, int max_len);
bool tflite_get_output_dims(TFLiteBackend* tb, int32_t* out_dims, int max_len, int index);

bool tflite_resize_input(TFLiteBackend* tb, const int32_t* dims, int dims_count);
bool tflite_allocate_tensors(TFLiteBackend* tb);
bool tflite_invoke(TFLiteBackend* tb);

bool tflite_copy_to_input_float(TFLiteBackend* tb, const float* data, size_t count);
bool tflite_copy_from_output_float(TFLiteBackend* tb, float* data, size_t count, int output_index);

int tflite_input_type(TFLiteBackend* tb);
int tflite_output_type(TFLiteBackend* tb, int index);
size_t tflite_output_byte_size(TFLiteBackend* tb, int index);
