#pragma once

#include <cstdint>

int temporal_speed_change(const uint8_t* src_frames, int src_count,
                           float speed, uint8_t* dst_frames,
                           int dst_capacity_bytes,
                           int width, int height);

int temporal_reverse(const uint8_t* src_frames, int src_count,
                      uint8_t* dst_frames, int dst_capacity_bytes,
                      int width, int height);

void temporal_blend_frames(const uint8_t* a, const uint8_t* b,
                            uint8_t* out, float factor,
                            int width, int height);
