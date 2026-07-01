#pragma once

#include <cstdint>

void feather_mask(const float* mask, float* out,
                  int width, int height,
                  float radius, float steepness);
