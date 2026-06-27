#include "temporal.h"
#include <cstring>
#include <algorithm>

static void blend_two(const uint8_t* a, const uint8_t* b,
                       uint8_t* out, int pixels, float factor) {
    float inv = 1.0f - factor;
    for (int i = 0; i < pixels; i++) {
        int idx = i * 4;
        out[idx + 0] = (uint8_t)(a[idx + 0] * inv + b[idx + 0] * factor + 0.5f);
        out[idx + 1] = (uint8_t)(a[idx + 1] * inv + b[idx + 1] * factor + 0.5f);
        out[idx + 2] = (uint8_t)(a[idx + 2] * inv + b[idx + 2] * factor + 0.5f);
        out[idx + 3] = (uint8_t)(a[idx + 3] * inv + b[idx + 3] * factor + 0.5f);
    }
}

int temporal_speed_change(const uint8_t* src_frames, int src_count,
                           float speed, uint8_t* dst_frames,
                           int dst_capacity_bytes,
                           int width, int height) {
    int bpf = width * height * 4;
    int max_dst = dst_capacity_bytes / bpf;
    if (max_dst < 1) return 0;

    int dst_count = 0;

    if (speed >= 1.0f) {
        int step = (int)speed;
        for (int i = 0; i < src_count && dst_count < max_dst; i += step) {
            std::memcpy(dst_frames + dst_count * bpf,
                        src_frames + i * bpf, bpf);
            dst_count++;
        }
    } else {
        float step = 1.0f / speed;
        float pos = 0.0f;
        while ((int)pos < src_count - 1 && dst_count < max_dst) {
            int idx_a = (int)pos;
            int idx_b = std::min(idx_a + 1, src_count - 1);
            float frac = pos - idx_a;

            blend_two(src_frames + idx_a * bpf,
                       src_frames + idx_b * bpf,
                       dst_frames + dst_count * bpf,
                       width * height, frac);
            dst_count++;
            pos += step;
        }
    }
    return dst_count;
}

int temporal_reverse(const uint8_t* src_frames, int src_count,
                      uint8_t* dst_frames, int dst_capacity_bytes,
                      int width, int height) {
    int bpf = width * height * 4;
    int dst_count = std::min(src_count, dst_capacity_bytes / bpf);

    for (int i = 0; i < dst_count; i++) {
        std::memcpy(dst_frames + i * bpf,
                    src_frames + (src_count - 1 - i) * bpf, bpf);
    }
    return dst_count;
}

void temporal_blend_frames(const uint8_t* a, const uint8_t* b,
                            uint8_t* out, float factor,
                            int width, int height) {
    blend_two(a, b, out, width * height, factor);
}
