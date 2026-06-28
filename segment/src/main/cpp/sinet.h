#pragma once

#include <cstddef>
#include <cstdint>

struct SINetModel;

enum class ModelType { SINet, MediaPipeSelfie };

SINetModel* sinet_create(const uint8_t* model_data, size_t model_size, ModelType type);
void sinet_destroy(SINetModel* sm);

bool sinet_segment(SINetModel* sm,
                    const uint8_t* rgba, int width, int height,
                    float* out_mask);
