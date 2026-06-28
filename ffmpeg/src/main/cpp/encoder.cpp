#include "encoder.h"

#include <cstring>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
#include <libavutil/opt.h>
#include <libswscale/swscale.h>
}

struct FFEncoder {
    AVFormatContext* fmt_ctx = nullptr;
    AVCodecContext* codec_ctx = nullptr;
    AVStream* stream = nullptr;
    AVFrame* frame = nullptr;
    SwsContext* sws_ctx = nullptr;
    int width = 0;
    int height = 0;
    AVRational time_base{0, 0};
    bool header_written = false;
};

static const AVCodec* find_h264_encoder() {
    const AVCodec* c = avcodec_find_encoder_by_name("h264_mediacodec");
    if (c) return c;
    c = avcodec_find_encoder_by_name("libx264");
    if (c) return c;
    return avcodec_find_encoder(AV_CODEC_ID_H264);
}

FFEncoder* encoder_open(const char* path,
                         int width, int height,
                         int frame_rate_num, int frame_rate_den,
                         int bit_rate) {
    auto* e = new FFEncoder();
    e->width = width;
    e->height = height;
    e->time_base = AVRational{frame_rate_den, frame_rate_num};

    avformat_alloc_output_context2(&e->fmt_ctx, nullptr, nullptr, path);
    if (!e->fmt_ctx) { encoder_close(e); return nullptr; }

    const AVCodec* codec = find_h264_encoder();
    if (!codec) { encoder_close(e); return nullptr; }

    e->stream = avformat_new_stream(e->fmt_ctx, codec);
    if (!e->stream) { encoder_close(e); return nullptr; }

    e->codec_ctx = avcodec_alloc_context3(codec);
    if (!e->codec_ctx) { encoder_close(e); return nullptr; }

    e->codec_ctx->width = width;
    e->codec_ctx->height = height;
    e->codec_ctx->time_base = e->time_base;
    e->stream->time_base = e->time_base;
    e->codec_ctx->framerate = AVRational{frame_rate_num, frame_rate_den};
    e->codec_ctx->pix_fmt = AV_PIX_FMT_YUV420P;
    e->codec_ctx->bit_rate = bit_rate;
    e->codec_ctx->gop_size = 12;
    e->codec_ctx->max_b_frames = 1;

    av_opt_set(e->codec_ctx->priv_data, "preset", "fast", 0);
    av_opt_set(e->codec_ctx->priv_data, "tune", "zerolatency", 0);

    if (avcodec_open2(e->codec_ctx, codec, nullptr) < 0) {
        encoder_close(e);
        return nullptr;
    }

    avcodec_parameters_from_context(e->stream->codecpar, e->codec_ctx);

    if (!(e->fmt_ctx->oformat->flags & AVFMT_NOFILE)) {
        if (avio_open(&e->fmt_ctx->pb, path, AVIO_FLAG_WRITE) < 0) {
            encoder_close(e);
            return nullptr;
        }
    }

    if (avformat_write_header(e->fmt_ctx, nullptr) < 0) {
        encoder_close(e);
        return nullptr;
    }
    e->header_written = true;

    e->frame = av_frame_alloc();
    e->frame->width = width;
    e->frame->height = height;
    e->frame->format = AV_PIX_FMT_YUV420P;
    av_frame_get_buffer(e->frame, 32);

    return e;
}

void encoder_close(FFEncoder* e) {
    if (!e) return;
    if (e->fmt_ctx && e->header_written) {
        av_write_trailer(e->fmt_ctx);
    }
    av_frame_free(&e->frame);
    avcodec_free_context(&e->codec_ctx);
    if (e->fmt_ctx && e->fmt_ctx->pb && !(e->fmt_ctx->oformat->flags & AVFMT_NOFILE)) {
        avio_closep(&e->fmt_ctx->pb);
    }
    avformat_free_context(e->fmt_ctx);
    sws_freeContext(e->sws_ctx);
    delete e;
}

static bool encode_frame_internal(FFEncoder* e) {
    AVPacket* pkt = av_packet_alloc();
    if (!pkt) return false;

    int ret = avcodec_send_frame(e->codec_ctx, e->frame);
    if (ret < 0) { av_packet_free(&pkt); return false; }

    bool ok = false;
    ret = avcodec_receive_packet(e->codec_ctx, pkt);
    if (ret == 0) {
        pkt->stream_index = e->stream->index;
        av_packet_rescale_ts(pkt, e->codec_ctx->time_base, e->stream->time_base);
        av_interleaved_write_frame(e->fmt_ctx, pkt);
        ok = true;
    } else if (ret == AVERROR(EAGAIN)) {
        ok = true;
    }
    av_packet_free(&pkt);
    return ok;
}

bool encoder_encode_frame_rgba(FFEncoder* e, const uint8_t* rgba, int buffer_size) {
    if (!e || !rgba) return false;
    if (buffer_size < e->width * e->height * 4) return false;

    int ret = av_frame_make_writable(e->frame);
    if (ret < 0) return false;

    e->sws_ctx = sws_getCachedContext(
        e->sws_ctx,
        e->width, e->height, AV_PIX_FMT_RGBA,
        e->width, e->height, AV_PIX_FMT_YUV420P,
        SWS_BILINEAR, nullptr, nullptr, nullptr);

    if (!e->sws_ctx) return false;

    uint8_t* src_data[1] = {const_cast<uint8_t*>(rgba)};
    int src_linesize[1] = {e->width * 4};

    sws_scale(e->sws_ctx, src_data, src_linesize,
              0, e->height, e->frame->data, e->frame->linesize);

    return encode_frame_internal(e);
}

bool encoder_encode_frame_nv12(FFEncoder* e, const uint8_t* nv12, int buffer_size) {
    if (!e || !nv12) return false;
    if (buffer_size < e->width * e->height * 3 / 2) return false;

    int ret = av_frame_make_writable(e->frame);
    if (ret < 0) return false;

    e->sws_ctx = sws_getCachedContext(
        e->sws_ctx,
        e->width, e->height, AV_PIX_FMT_NV12,
        e->width, e->height, AV_PIX_FMT_YUV420P,
        SWS_BILINEAR, nullptr, nullptr, nullptr);

    if (!e->sws_ctx) return false;

    uint8_t* src_data[2];
    int src_linesize[2];
    src_data[0] = const_cast<uint8_t*>(nv12);
    src_data[1] = const_cast<uint8_t*>(nv12 + e->width * e->height);
    src_linesize[0] = e->width;
    src_linesize[1] = e->width;

    sws_scale(e->sws_ctx, src_data, src_linesize,
              0, e->height, e->frame->data, e->frame->linesize);

    return encode_frame_internal(e);
}

bool encoder_finalize(FFEncoder* e) {
    if (!e || !e->codec_ctx) return false;

    AVPacket* pkt = av_packet_alloc();
    if (!pkt) return false;

    avcodec_send_frame(e->codec_ctx, nullptr);
    while (true) {
        int ret = avcodec_receive_packet(e->codec_ctx, pkt);
        if (ret < 0) break;
        pkt->stream_index = e->stream->index;
        av_packet_rescale_ts(pkt, e->codec_ctx->time_base, e->stream->time_base);
        av_interleaved_write_frame(e->fmt_ctx, pkt);
        av_packet_unref(pkt);
    }
    av_packet_free(&pkt);
    return true;
}
