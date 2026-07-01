#pragma once

#include <cstdint>

bool composite_frame(uint8_t* bg_rgba, int bg_w, int bg_h,
                      const uint8_t* fg_rgba, int fg_w, int fg_h,
                      int pos_x, int pos_y, float opacity);

bool composite_with_mask(uint8_t* bg_rgba, const uint8_t* fg_rgba,
                          const float* mask, int width, int height,
                          float intensity);

bool composite_checkerboard(uint8_t* rgba, int width, int height,
                             int tile_size);

bool composite_crossfade(const uint8_t* frame_a, const uint8_t* frame_b,
                          uint8_t* out, int width, int height, float progress);

bool composite_wipe(const uint8_t* frame_a, const uint8_t* frame_b,
                     uint8_t* out, int width, int height, float progress,
                     int direction);
