#include <jni.h>
#include <cstdint>

#include "decoder.h"
#include "encoder.h"
#include "filter_graph.h"
#include "compositor.h"
#include "temporal.h"

// ─── Verify ────────────────────────────────────────────────────────────────

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeVerifyFFmpeg(JNIEnv*, jclass) {
    FFFilterGraph* fg = filter_graph_create(1, 1, 30, "copy");
    if (!fg) return false;
    filter_graph_close(fg);
    return true;
}

// ─── Decoder JNI ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderOpen(JNIEnv* env, jclass, jstring path) {
    const char* c_path = env->GetStringUTFChars(path, nullptr);
    if (!c_path) return 0;
    FFDecoder* d = decoder_open(c_path);
    env->ReleaseStringUTFChars(path, c_path);
    return reinterpret_cast<jlong>(d);
}

extern "C" JNIEXPORT void JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderClose(JNIEnv*, jclass, jlong handle) {
    decoder_close(reinterpret_cast<FFDecoder*>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderWidth(JNIEnv*, jclass, jlong handle) {
    return decoder_width(reinterpret_cast<FFDecoder*>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderHeight(JNIEnv*, jclass, jlong handle) {
    return decoder_height(reinterpret_cast<FFDecoder*>(handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderDurationUs(JNIEnv*, jclass, jlong handle) {
    return decoder_duration_us(reinterpret_cast<FFDecoder*>(handle));
}

extern "C" JNIEXPORT jlong JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderFrameRate(JNIEnv*, jclass, jlong handle) {
    return decoder_frame_rate_millifps(reinterpret_cast<FFDecoder*>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderRotation(JNIEnv*, jclass, jlong handle) {
    return decoder_rotation(reinterpret_cast<FFDecoder*>(handle));
}

extern "C" JNIEXPORT jint JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderFrameSize(JNIEnv*, jclass, jlong handle) {
    return decoder_frame_size(reinterpret_cast<FFDecoder*>(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderSeek(JNIEnv*, jclass,
                                                         jlong handle, jlong timestamp_us) {
    return decoder_seek(reinterpret_cast<FFDecoder*>(handle), timestamp_us);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeDecoderReadFrame(JNIEnv* env, jclass,
                                                              jlong handle, jobject buffer) {
    FFDecoder* d = reinterpret_cast<FFDecoder*>(handle);
    if (!d) return false;
    void* buf = env->GetDirectBufferAddress(buffer);
    if (!buf) return false;
    jlong cap = env->GetDirectBufferCapacity(buffer);
    return decoder_read_frame(d, (uint8_t*)buf, (int)cap);
}

// ─── Encoder JNI ──────────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeEncoderOpen(JNIEnv* env, jclass,
                                                         jstring path,
                                                         jint width, jint height,
                                                         jint frame_rate_num,
                                                         jint frame_rate_den,
                                                         jint bit_rate) {
    const char* c_path = env->GetStringUTFChars(path, nullptr);
    if (!c_path) return 0;
    FFEncoder* e = encoder_open(c_path, width, height,
                                 frame_rate_num, frame_rate_den, bit_rate);
    env->ReleaseStringUTFChars(path, c_path);
    return reinterpret_cast<jlong>(e);
}

extern "C" JNIEXPORT void JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeEncoderClose(JNIEnv*, jclass, jlong handle) {
    encoder_close(reinterpret_cast<FFEncoder*>(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeEncoderEncodeFrameRgba(JNIEnv* env, jclass,
                                                                     jlong handle,
                                                                     jobject buffer) {
    FFEncoder* e = reinterpret_cast<FFEncoder*>(handle);
    if (!e) return false;
    void* buf = env->GetDirectBufferAddress(buffer);
    if (!buf) return false;
    jlong cap = env->GetDirectBufferCapacity(buffer);
    return encoder_encode_frame_rgba(e, (const uint8_t*)buf, (int)cap);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeEncoderFinalize(JNIEnv*, jclass, jlong handle) {
    return encoder_finalize(reinterpret_cast<FFEncoder*>(handle));
}

// ─── Filter Graph JNI ─────────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeFilterGraphCreate(JNIEnv* env, jclass,
                                                                jint width, jint height,
                                                                jint fps,
                                                                jstring filter_desc) {
    const char* c_desc = env->GetStringUTFChars(filter_desc, nullptr);
    if (!c_desc) return 0;
    FFFilterGraph* fg = filter_graph_create(width, height, fps, c_desc);
    env->ReleaseStringUTFChars(filter_desc, c_desc);
    return reinterpret_cast<jlong>(fg);
}

extern "C" JNIEXPORT void JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeFilterGraphClose(JNIEnv*, jclass, jlong handle) {
    filter_graph_close(reinterpret_cast<FFFilterGraph*>(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeFilterGraphProcess(JNIEnv* env, jclass,
                                                                 jlong handle,
                                                                 jobject in_buffer,
                                                                 jint in_w, jint in_h,
                                                                 jobject out_buffer,
                                                                 jint out_w, jint out_h) {
    FFFilterGraph* fg = reinterpret_cast<FFFilterGraph*>(handle);
    if (!fg) return false;
    void* in_buf = env->GetDirectBufferAddress(in_buffer);
    void* out_buf = env->GetDirectBufferAddress(out_buffer);
    if (!in_buf || !out_buf) return false;
    return filter_graph_process(fg,
                                 (const uint8_t*)in_buf, in_w, in_h,
                                 (uint8_t*)out_buf, out_w, out_h);
}

// ─── Compositor JNI ───────────────────────────────────────────────────────

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeCompositeFrame(JNIEnv* env, jclass,
                                                             jobject bg_buf,
                                                             jint bg_w, jint bg_h,
                                                             jobject fg_buf,
                                                             jint fg_w, jint fg_h,
                                                             jint pos_x, jint pos_y,
                                                             jfloat opacity) {
    void* bg = env->GetDirectBufferAddress(bg_buf);
    void* fg = env->GetDirectBufferAddress(fg_buf);
    if (!bg || !fg) return false;
    return composite_frame((uint8_t*)bg, bg_w, bg_h,
                            (const uint8_t*)fg, fg_w, fg_h,
                            pos_x, pos_y, opacity);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeCompositeWithMask(JNIEnv* env, jclass,
                                                                jobject bg_buf,
                                                                jobject fg_buf,
                                                                jobject mask_buf,
                                                                jint width, jint height,
                                                                jfloat intensity) {
    void* bg = env->GetDirectBufferAddress(bg_buf);
    void* fg = env->GetDirectBufferAddress(fg_buf);
    void* mask = env->GetDirectBufferAddress(mask_buf);
    if (!bg || !fg || !mask) return false;
    return composite_with_mask((uint8_t*)bg, (const uint8_t*)fg,
                                (const float*)mask, width, height, intensity);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeCompositeCheckerboard(JNIEnv* env, jclass,
                                                                    jobject buf,
                                                                    jint w, jint h,
                                                                    jint tile_size) {
    void* b = env->GetDirectBufferAddress(buf);
    if (!b) return false;
    return composite_checkerboard((uint8_t*)b, w, h, tile_size);
}

// ─── Compositor: Transitions ───────────────────────────────────────────────

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeCompositeCrossfade(JNIEnv* env, jclass,
                                                                jobject a_buf,
                                                                jobject b_buf,
                                                                jobject out_buf,
                                                                jint w, jint h,
                                                                jfloat progress) {
    void* a = env->GetDirectBufferAddress(a_buf);
    void* b = env->GetDirectBufferAddress(b_buf);
    void* out = env->GetDirectBufferAddress(out_buf);
    if (!a || !b || !out) return false;
    return composite_crossfade((const uint8_t*)a, (const uint8_t*)b,
                                (uint8_t*)out, w, h, progress);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeCompositeWipe(JNIEnv* env, jclass,
                                                            jobject a_buf,
                                                            jobject b_buf,
                                                            jobject out_buf,
                                                            jint w, jint h,
                                                            jfloat progress,
                                                            jint direction) {
    void* a = env->GetDirectBufferAddress(a_buf);
    void* b = env->GetDirectBufferAddress(b_buf);
    void* out = env->GetDirectBufferAddress(out_buf);
    if (!a || !b || !out) return false;
    return composite_wipe((const uint8_t*)a, (const uint8_t*)b,
                           (uint8_t*)out, w, h, progress, direction);
}

// ─── Temporal JNI ─────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeTemporalSpeedChange(JNIEnv* env, jclass,
                                                                  jobject src_buf,
                                                                  jint src_count,
                                                                  jfloat speed,
                                                                  jobject dst_buf,
                                                                  jint dst_capacity,
                                                                  jint w, jint h) {
    void* src = env->GetDirectBufferAddress(src_buf);
    void* dst = env->GetDirectBufferAddress(dst_buf);
    if (!src || !dst) return 0;
    return temporal_speed_change((const uint8_t*)src, src_count, speed,
                                  (uint8_t*)dst, dst_capacity, w, h);
}

extern "C" JNIEXPORT jint JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeTemporalReverse(JNIEnv* env, jclass,
                                                              jobject src_buf,
                                                              jint src_count,
                                                              jobject dst_buf,
                                                              jint dst_capacity,
                                                              jint w, jint h) {
    void* src = env->GetDirectBufferAddress(src_buf);
    void* dst = env->GetDirectBufferAddress(dst_buf);
    if (!src || !dst) return 0;
    return temporal_reverse((const uint8_t*)src, src_count,
                             (uint8_t*)dst, dst_capacity, w, h);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_ffmpeg_NativeFFmpeg_nativeTemporalBlendFrames(JNIEnv* env, jclass,
                                                                  jobject a_buf,
                                                                  jobject b_buf,
                                                                  jfloat factor,
                                                                  jobject out_buf,
                                                                  jint w, jint h) {
    void* a = env->GetDirectBufferAddress(a_buf);
    void* b = env->GetDirectBufferAddress(b_buf);
    void* out = env->GetDirectBufferAddress(out_buf);
    if (!a || !b || !out) return false;
    temporal_blend_frames((const uint8_t*)a, (const uint8_t*)b,
                           (uint8_t*)out, factor, w, h);
    return true;
}
