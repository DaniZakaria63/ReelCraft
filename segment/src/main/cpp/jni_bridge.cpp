#include <jni.h>
#include <cstdint>

#include "sinet.h"

extern "C" JNIEXPORT jlong JNICALL
Java_id_my_daniza_segment_NativeSegment_nativeLoadModel(
    JNIEnv* env, jclass, jobject model_buffer, jint model_type)
{
    if (!model_buffer) return 0;
    void* data = env->GetDirectBufferAddress(model_buffer);
    if (!data) return 0;
    jlong capacity = env->GetDirectBufferCapacity(model_buffer);
    if (capacity <= 0) return 0;

    ModelType type = (model_type == 1) ? ModelType::MediaPipeSelfie : ModelType::SINet;
    SINetModel* sm = sinet_create((const uint8_t*)data, (size_t)capacity, type);
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
