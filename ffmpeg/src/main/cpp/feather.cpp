#include "feather.h"

#include <cmath>
#include <cstring>
#include <algorithm>

static inline float sigmoid(float x, float steepness) {
    return 1.0f / (1.0f + std::exp(-steepness * (x - 0.5f)));
}

void feather_mask(const float* mask, float* out,
                  int width, int height,
                  float radius, float steepness) {
    if (!mask || !out || width <= 0 || height <= 0) return;

    const int pixels = width * height;
    const int kr = std::max(1, (int)std::ceil(radius));

    float* blur = new float[pixels];
    float* weights = new float[kr * 2 + 1];

    int weight_count = 0;
    float weight_sum = 0.0f;
    for (int k = -kr; k <= kr; k++) {
        float w = std::exp(-(float)(k * k) / (2.0f * radius * radius + 1e-6f));
        weights[weight_count++] = w;
        weight_sum += w;
    }

    for (int k = 0; k < weight_count; k++) {
        weights[k] /= weight_sum;
    }

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float sum = 0.0f;
            for (int key = -kr; key <= kr; key++) {
                int sy = std::max(0, std::min(height - 1, y + key));
                float w_v = weights[key + kr];
                float row_sum = 0.0f;
                for (int kex = -kr; kex <= kr; kex++) {
                    int sx = std::max(0, std::min(width - 1, x + kex));
                    row_sum += mask[sy * width + sx] * weights[kex + kr];
                }
                sum += row_sum * w_v;
            }
            blur[y * width + x] = sum;
        }
    }

    for (int i = 0; i < pixels; i++) {
        out[i] = sigmoid(blur[i], steepness);
    }

    delete[] weights;
    delete[] blur;
}
