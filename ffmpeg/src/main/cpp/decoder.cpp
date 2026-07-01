#include "decoder.h"

#include <cstring>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
#include <libavutil/rational.h>
#include <libswscale/swscale.h>
}

struct FFDecoder {
    AVFormatContext* fmt_ctx = nullptr;
    AVCodecContext* codec_ctx = nullptr;
    AVFrame* frame = nullptr;
    AVFrame* rgb_frame = nullptr;
    SwsContext* sws_ctx = nullptr;
    int stream_idx = -1;
    int width = 0;
    int height = 0;
    AVRational time_base{0, 0};
    AVRational frame_rate{0, 0};
    int64_t duration_us = 0;
    int rotation = 0;
    uint8_t* rgb_buffer = nullptr;
    int rgb_buf_size = 0;
};

FFDecoder* decoder_open(const char* path) {
    auto* d = new FFDecoder();

    if (avformat_open_input(&d->fmt_ctx, path, nullptr, nullptr) < 0) {
        decoder_close(d);
        return nullptr;
    }

    if (avformat_find_stream_info(d->fmt_ctx, nullptr) < 0) {
        decoder_close(d);
        return nullptr;
    }

    for (int i = 0; i < d->fmt_ctx->nb_streams; i++) {
        if (d->fmt_ctx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            d->stream_idx = i;
            break;
        }
    }
    if (d->stream_idx < 0) { decoder_close(d); return nullptr; }

    AVStream* stream = d->fmt_ctx->streams[d->stream_idx];
    AVCodecParameters* cp = stream->codecpar;
    const AVCodec* codec = avcodec_find_decoder(cp->codec_id);
    if (!codec) { decoder_close(d); return nullptr; }

    d->codec_ctx = avcodec_alloc_context3(codec);
    if (!d->codec_ctx) { decoder_close(d); return nullptr; }
    avcodec_parameters_to_context(d->codec_ctx, cp);

    if (avcodec_open2(d->codec_ctx, codec, nullptr) < 0) {
        decoder_close(d);
        return nullptr;
    }

    d->width = d->codec_ctx->width;
    d->height = d->codec_ctx->height;
    d->time_base = stream->time_base;
    d->frame_rate = stream->avg_frame_rate;
    if (d->frame_rate.num == 0 || d->frame_rate.den == 0) {
        d->frame_rate = av_guess_frame_rate(d->fmt_ctx, stream, nullptr);
    }
    d->duration_us = d->fmt_ctx->duration;

    // Rotation from metadata tag (covers 99% of phone/camera videos)
    AVDictionaryEntry* tag = av_dict_get(stream->metadata, "rotate", nullptr, 0);
    if (tag) d->rotation = atoi(tag->value);

    d->frame = av_frame_alloc();
    d->rgb_frame = av_frame_alloc();

    d->rgb_buf_size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, d->width, d->height, 1);
    d->rgb_buffer = (uint8_t*)av_malloc(d->rgb_buf_size);
    av_image_fill_arrays(d->rgb_frame->data, d->rgb_frame->linesize,
                         d->rgb_buffer, AV_PIX_FMT_RGBA, d->width, d->height, 1);

    return d;
}

void decoder_close(FFDecoder* d) {
    if (!d) return;
    av_frame_free(&d->frame);
    if (d->rgb_frame) {
        av_frame_free(&d->rgb_frame);
    }
    if (d->rgb_buffer) {
        av_free(d->rgb_buffer);
        d->rgb_buffer = nullptr;
    }
    avcodec_free_context(&d->codec_ctx);
    avformat_close_input(&d->fmt_ctx);
    sws_freeContext(d->sws_ctx);
    delete d;
}

int decoder_width(FFDecoder* d) { return d ? d->width : 0; }
int decoder_height(FFDecoder* d) { return d ? d->height : 0; }
int64_t decoder_duration_us(FFDecoder* d) { return d ? d->duration_us : 0; }
int decoder_frame_size(FFDecoder* d) { return d ? d->rgb_buf_size : 0; }

int64_t decoder_frame_rate_millifps(FFDecoder* d) {
    if (!d || d->frame_rate.den == 0) return 30000;
    return (int64_t)d->frame_rate.num * 1000 / d->frame_rate.den;
}

int decoder_rotation(FFDecoder* d) { return d ? d->rotation : 0; }

bool decoder_seek(FFDecoder* d, int64_t timestamp_us) {
    if (!d) return false;
    int64_t ts = av_rescale_q(timestamp_us, (AVRational){1, 1000000}, d->time_base);
    av_seek_frame(d->fmt_ctx, d->stream_idx, ts, AVSEEK_FLAG_BACKWARD);
    avcodec_flush_buffers(d->codec_ctx);
    return true;
}

bool decoder_read_frame(FFDecoder* d, uint8_t* out_rgba, int buffer_size) {
    if (!d || !out_rgba) return false;
    if (buffer_size < d->rgb_buf_size) return false;

    AVPacket* pkt = av_packet_alloc();
    if (!pkt) return false;

    bool got_frame = false;
    int ret;

    while ((ret = av_read_frame(d->fmt_ctx, pkt)) >= 0) {
        if (pkt->stream_index != d->stream_idx) {
            av_packet_unref(pkt);
            continue;
        }
        ret = avcodec_send_packet(d->codec_ctx, pkt);
        av_packet_unref(pkt);
        if (ret < 0) continue;

        ret = avcodec_receive_frame(d->codec_ctx, d->frame);
        if (ret == 0) { got_frame = true; break; }
    }

    if (!got_frame) {
        avcodec_send_packet(d->codec_ctx, nullptr);
        ret = avcodec_receive_frame(d->codec_ctx, d->frame);
        if (ret == 0) got_frame = true;
    }

    if (!got_frame) { av_packet_free(&pkt); return false; }

    d->sws_ctx = sws_getCachedContext(
        d->sws_ctx,
        d->frame->width, d->frame->height, (AVPixelFormat)d->frame->format,
        d->width, d->height, AV_PIX_FMT_RGBA,
        SWS_BILINEAR, nullptr, nullptr, nullptr);

    sws_scale(d->sws_ctx, d->frame->data, d->frame->linesize,
              0, d->frame->height, d->rgb_frame->data, d->rgb_frame->linesize);

    std::memcpy(out_rgba, d->rgb_buffer, (size_t)d->rgb_buf_size);
    av_packet_free(&pkt);
    return true;
}
