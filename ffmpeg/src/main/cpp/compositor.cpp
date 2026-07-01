#include "compositor.h"
#include <cstdint>
#include <cmath>
#include <algorithm>

static inline uint8_t clamp_f(float v) {
    if (v < 0) return 0;
    if (v > 255) return 255;
    return (uint8_t)(v + 0.5f);
}

static void blend_rgba(uint8_t* bg, const uint8_t* fg, int count, float opacity) {
    float a = opacity;
    float inv_a = 1.0f - a;
    for (int i = 0; i < count; i++) {
        int idx = i * 4;
        bg[idx + 0] = clamp_f(bg[idx + 0] * inv_a + fg[idx + 0] * a);
        bg[idx + 1] = clamp_f(bg[idx + 1] * inv_a + fg[idx + 1] * a);
        bg[idx + 2] = clamp_f(bg[idx + 2] * inv_a + fg[idx + 2] * a);
    }
}

static void mask_blend_rgba(uint8_t* bg, const uint8_t* fg,
                             const float* mask, int count, float intensity) {
    for (int i = 0; i < count; i++) {
        float a = mask[i] * intensity;
        float inv_a = 1.0f - a;
        int idx = i * 4;
        bg[idx + 0] = clamp_f(bg[idx + 0] * inv_a + fg[idx + 0] * a);
        bg[idx + 1] = clamp_f(bg[idx + 1] * inv_a + fg[idx + 1] * a);
        bg[idx + 2] = clamp_f(bg[idx + 2] * inv_a + fg[idx + 2] * a);
    }
}

bool composite_frame(uint8_t* bg_rgba, int bg_w, int bg_h,
                      const uint8_t* fg_rgba, int fg_w, int fg_h,
                      int pos_x, int pos_y, float opacity) {
    if (!bg_rgba || !fg_rgba) return false;

    int copy_w = fg_w < bg_w ? fg_w : bg_w;
    int copy_h = fg_h < bg_h ? fg_h : bg_h;

    for (int y = 0; y < copy_h; y++) {
        int bg_y = y + pos_y;
        if (bg_y < 0 || bg_y >= bg_h) continue;

        blend_rgba(
            bg_rgba + (bg_y * bg_w + pos_x) * 4,
            fg_rgba + (y * fg_w) * 4,
            copy_w, opacity
        );
    }
    return true;
}

bool composite_with_mask(uint8_t* bg_rgba, const uint8_t* fg_rgba,
                          const float* mask, int width, int height,
                          float intensity) {
    if (!bg_rgba || !fg_rgba || !mask) return false;
    mask_blend_rgba(bg_rgba, fg_rgba, mask, width * height, intensity);
    return true;
}

bool composite_checkerboard(uint8_t* rgba, int width, int height,
                             int tile_size) {
    if (!rgba) return false;
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int idx = (y * width + x) * 4;
            bool light = ((x / tile_size) + (y / tile_size)) % 2 == 0;
            uint8_t v = light ? 0xDD : 0x33;
            rgba[idx + 0] = v;
            rgba[idx + 1] = v;
            rgba[idx + 2] = v;
            rgba[idx + 3] = 0xFF;
        }
    }
    return true;
}

bool composite_crossfade(const uint8_t* frame_a, const uint8_t* frame_b,
                          uint8_t* out, int width, int height, float progress) {
    if (!frame_a || !frame_b || !out) return false;
    float a = std::max(0.0f, std::min(1.0f, progress));
    float inv_a = 1.0f - a;
    int count = width * height;
    for (int i = 0; i < count; i++) {
        int idx = i * 4;
        out[idx + 0] = clamp_f(frame_a[idx + 0] * inv_a + frame_b[idx + 0] * a);
        out[idx + 1] = clamp_f(frame_a[idx + 1] * inv_a + frame_b[idx + 1] * a);
        out[idx + 2] = clamp_f(frame_a[idx + 2] * inv_a + frame_b[idx + 2] * a);
        out[idx + 3] = 255;
    }
    return true;
}

bool composite_wipe(const uint8_t* frame_a, const uint8_t* frame_b,
                     uint8_t* out, int width, int height, float progress,
                     int direction) {
    if (!frame_a || !frame_b || !out) return false;
    float p = std::max(0.0f, std::min(1.0f, progress));
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            int idx = (y * width + x) * 4;
            float edge;
            switch (direction) {
                case 0: edge = (float)x / (float)width; break;
                case 1: edge = 1.0f - (float)x / (float)width; break;
                case 2: edge = (float)y / (float)height; break;
                case 3: edge = 1.0f - (float)y / (float)height; break;
                default: edge = (float)x / (float)width; break;
            }
            if (edge < p) {
                out[idx + 0] = frame_b[idx + 0];
                out[idx + 1] = frame_b[idx + 1];
                out[idx + 2] = frame_b[idx + 2];
            } else {
                out[idx + 0] = frame_a[idx + 0];
                out[idx + 1] = frame_a[idx + 1];
                out[idx + 2] = frame_a[idx + 2];
            }
            out[idx + 3] = 255;
        }
    }
    return true;
}
