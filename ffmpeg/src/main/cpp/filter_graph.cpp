#include "filter_graph.h"

#include <cstdio>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavfilter/avfilter.h>
#include <libavfilter/buffersink.h>
#include <libavfilter/buffersrc.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

struct FFFilterGraph {
    AVFilterGraph* graph = nullptr;
    AVFilterContext* buffersrc_ctx = nullptr;
    AVFilterContext* buffersink_ctx = nullptr;
    AVFrame* frame = nullptr;
    AVFrame* filtered = nullptr;
    SwsContext* sws_in = nullptr;
    SwsContext* sws_out = nullptr;
    int width = 0;
    int height = 0;
};

FFFilterGraph* filter_graph_create(int width, int height, const char* desc) {
    auto* fg = new FFFilterGraph();
    fg->width = width;
    fg->height = height;

    fg->graph = avfilter_graph_alloc();
    if (!fg->graph) { filter_graph_close(fg); return nullptr; }

    const AVFilter* buffersrc = avfilter_get_by_name("buffer");
    const AVFilter* buffersink = avfilter_get_by_name("buffersink");

    char args[256];
    snprintf(args, sizeof(args),
             "video_size=%dx%d:pix_fmt=%d:time_base=1/30:pixel_aspect=1/1",
             width, height, AV_PIX_FMT_RGBA);

    if (avfilter_graph_create_filter(&fg->buffersrc_ctx, buffersrc, "in",
                                      args, nullptr, fg->graph) < 0) {
        filter_graph_close(fg);
        return nullptr;
    }

    if (avfilter_graph_create_filter(&fg->buffersink_ctx, buffersink, "out",
                                      nullptr, nullptr, fg->graph) < 0) {
        filter_graph_close(fg);
        return nullptr;
    }

    AVFilterInOut* outputs = avfilter_inout_alloc();
    AVFilterInOut* inputs = avfilter_inout_alloc();

    outputs->name = av_strdup("in");
    outputs->filter_ctx = fg->buffersrc_ctx;
    outputs->pad_idx = 0;
    outputs->next = nullptr;

    inputs->name = av_strdup("out");
    inputs->filter_ctx = fg->buffersink_ctx;
    inputs->pad_idx = 0;
    inputs->next = nullptr;

    int ret = avfilter_graph_parse_ptr(fg->graph, desc, &inputs, &outputs, nullptr);
    if (ret < 0) {
        avfilter_inout_free(&inputs);
        avfilter_inout_free(&outputs);
        filter_graph_close(fg);
        return nullptr;
    }

    avfilter_inout_free(&inputs);
    avfilter_inout_free(&outputs);

    if (avfilter_graph_config(fg->graph, nullptr) < 0) {
        filter_graph_close(fg);
        return nullptr;
    }

    fg->frame = av_frame_alloc();
    fg->frame->width = width;
    fg->frame->height = height;
    fg->frame->format = AV_PIX_FMT_RGBA;
    av_frame_get_buffer(fg->frame, 32);

    fg->filtered = av_frame_alloc();

    return fg;
}

void filter_graph_close(FFFilterGraph* fg) {
    if (!fg) return;
    avfilter_graph_free(&fg->graph);
    av_frame_free(&fg->frame);
    av_frame_free(&fg->filtered);
    sws_freeContext(fg->sws_in);
    sws_freeContext(fg->sws_out);
    delete fg;
}

bool filter_graph_process(FFFilterGraph* fg,
                           const uint8_t* in_rgba, int in_width, int in_height,
                           uint8_t* out_rgba, int out_width, int out_height) {
    if (!fg || !fg->graph || !in_rgba || !out_rgba) return false;

    int ret = av_frame_make_writable(fg->frame);
    if (ret < 0) return false;

    fg->sws_in = sws_getCachedContext(
        fg->sws_in, in_width, in_height, AV_PIX_FMT_RGBA,
        fg->width, fg->height, AV_PIX_FMT_RGBA,
        SWS_BILINEAR, nullptr, nullptr, nullptr);

    uint8_t* in_data[4] = {const_cast<uint8_t*>(in_rgba), nullptr, nullptr, nullptr};
    int in_linesize[4] = {in_width * 4, 0, 0, 0};

    sws_scale(fg->sws_in, in_data, in_linesize, 0, in_height,
              fg->frame->data, fg->frame->linesize);

    if (av_buffersrc_add_frame(fg->buffersrc_ctx, fg->frame) < 0) return false;

    ret = av_buffersink_get_frame(fg->buffersink_ctx, fg->filtered);
    if (ret < 0) return false;

    fg->sws_out = sws_getCachedContext(
        fg->sws_out, fg->filtered->width, fg->filtered->height, (AVPixelFormat)fg->filtered->format,
        out_width, out_height, AV_PIX_FMT_RGBA,
        SWS_BILINEAR, nullptr, nullptr, nullptr);

    uint8_t* out_data[4] = {out_rgba, nullptr, nullptr, nullptr};
    int out_linesize[4] = {out_width * 4, 0, 0, 0};

    sws_scale(fg->sws_out, fg->filtered->data, fg->filtered->linesize, 0, fg->filtered->height,
              out_data, out_linesize);

    av_frame_unref(fg->filtered);
    return true;
}
