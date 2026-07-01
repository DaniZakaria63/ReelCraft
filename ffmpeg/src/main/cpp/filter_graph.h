#pragma once

#include <cstdint>

struct FFFilterGraph;

FFFilterGraph* filter_graph_create(int width, int height, int fps, const char* filter_desc);
void filter_graph_close(FFFilterGraph* fg);
bool filter_graph_process(FFFilterGraph* fg,
                           const uint8_t* in_rgba, int in_width, int in_height,
                           uint8_t* out_rgba, int out_width, int out_height);
