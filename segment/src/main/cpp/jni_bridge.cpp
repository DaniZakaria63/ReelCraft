#include <jni.h>
#include <cstdint>
#include <cstring>

#include "sinet.h"
#include "effects.h"

extern "C" JNIEXPORT jlong JNICALL
Java_id_my_daniza_segment_NativeSegment_nativeLoadModel(
    JNIEnv* env, jclass, jobject model_buffer, jint model_type, jint delegate_flags, jint num_threads)
{
    if (!model_buffer) return 0;
    void* data = env->GetDirectBufferAddress(model_buffer);
    if (!data) return 0;
    jlong capacity = env->GetDirectBufferCapacity(model_buffer);
    if (capacity <= 0) return 0;

    ModelType type = (model_type == 1) ? ModelType::MediaPipeSelfie : ModelType::SINet;
    SINetModel* sm = sinet_create((const uint8_t*)data, (size_t)capacity, type, delegate_flags, (int)num_threads);
    return reinterpret_cast<jlong>(sm);
}

extern "C" JNIEXPORT void JNICALL
Java_id_my_daniza_segment_NativeSegment_nativeCloseModel(JNIEnv*, jclass, jlong handle) {
    sinet_destroy(reinterpret_cast<SINetModel*>(handle));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_segment_NativeSegment_nativeSegmentFrame(
    JNIEnv* env, jclass, jlong handle,
    jobject rgba_buffer, jint width, jint height,
    jobject mask_buffer)
{
    SINetModel* sm = reinterpret_cast<SINetModel*>(handle);
    if (!sm) return false;

    void* rgba = env->GetDirectBufferAddress(rgba_buffer);
    void* mask = env->GetDirectBufferAddress(mask_buffer);
    if (!rgba || !mask) return false;

    return sinet_segment(sm, (const uint8_t*)rgba, width, height, (float*)mask);
}

extern "C" JNIEXPORT void JNICALL
Java_id_my_daniza_segment_NativeSegment_nativeGenerateEffectParams(
    JNIEnv* env, jclass, jint effect_type, jobject params_buffer)
{
    if (!params_buffer) return;
    void* data = env->GetDirectBufferAddress(params_buffer);
    if (!data) return;
    jlong cap = env->GetDirectBufferCapacity(params_buffer);
    if (cap < (jlong)sizeof(EffectParams)) return;

    auto* p = reinterpret_cast<EffectParams*>(data);
    std::memset(p, 0, sizeof(EffectParams));
    effects_generate_params(static_cast<SegmentEffect>(effect_type), p);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_my_daniza_segment_NativeSegment_nativeApplyEffect(
    JNIEnv* env, jclass,
    jobject params_buffer,
    jobject rgba_buffer, jint width, jint height,
    jobject mask_buffer,
    jobject out_buffer)
{
    if (!params_buffer || !rgba_buffer || !mask_buffer || !out_buffer) return false;

    EffectParams* p = reinterpret_cast<EffectParams*>(env->GetDirectBufferAddress(params_buffer));
    if (!p) return false;

    uint8_t* rgba = (uint8_t*)env->GetDirectBufferAddress(rgba_buffer);
    float* mask = (float*)env->GetDirectBufferAddress(mask_buffer);
    uint8_t* out = (uint8_t*)env->GetDirectBufferAddress(out_buffer);
    if (!rgba || !mask || !out) return false;

    return effect_apply(*p, rgba, mask, out, width, height);
}
