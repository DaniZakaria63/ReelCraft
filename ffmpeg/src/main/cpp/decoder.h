#pragma once

#include <cstdint>

struct FFDecoder;

FFDecoder* decoder_open(const char* path);
void decoder_close(FFDecoder* d);
int decoder_width(FFDecoder* d);
int decoder_height(FFDecoder* d);
int64_t decoder_duration_us(FFDecoder* d);
int64_t decoder_frame_rate_millifps(FFDecoder* d);
int decoder_rotation(FFDecoder* d);
bool decoder_seek(FFDecoder* d, int64_t timestamp_us);
bool decoder_read_frame(FFDecoder* d, uint8_t* out_rgba, int buffer_size);
int decoder_frame_size(FFDecoder* d);
