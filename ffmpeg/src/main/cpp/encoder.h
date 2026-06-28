#pragma once

#include <cstdint>

struct FFEncoder;

FFEncoder* encoder_open(const char* path,
                         int width, int height,
                         int frame_rate_num, int frame_rate_den,
                         int bit_rate);
void encoder_close(FFEncoder* e);
bool encoder_encode_frame_rgba(FFEncoder* e, const uint8_t* rgba, int buffer_size);
bool encoder_encode_frame_nv12(FFEncoder* e, const uint8_t* nv12, int buffer_size);
bool encoder_finalize(FFEncoder* e);
