package id.my.daniza.segment

import java.nio.ByteBuffer
import java.nio.ByteOrder

object NativeSegment {

    init {
        System.loadLibrary("segment")
    }

    const val MODEL_SINET = 0
    const val MODEL_MEDIAPIPE_SELFIE = 1

    // ─── Model lifecycle ──────────────────────────────────────────────────

    fun loadModel(modelBuffer: ByteBuffer, modelType: Int): Long {
        return nativeLoadModel(modelBuffer, modelType)
    }

    fun closeModel(handle: Long) {
        nativeCloseModel(handle)
    }

    // ─── SINet segmentation ───────────────────────────────────────────────

    fun segmentFrame(
        handle: Long,
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        maskBuffer: ByteBuffer,
    ): Boolean {
        return nativeSegmentFrame(handle, rgbaBuffer, width, height, maskBuffer)
    }

    // ─── Effect params ────────────────────────────────────────────────────

    fun generateEffectParams(effectType: Int, paramsBuffer: ByteBuffer) {
        nativeGenerateEffectParams(effectType, paramsBuffer)
    }

    // ─── Effect apply ─────────────────────────────────────────────────────

    fun applyEffect(
        paramsBuffer: ByteBuffer,
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        maskBuffer: ByteBuffer,
        outBuffer: ByteBuffer,
    ): Boolean {
        return nativeApplyEffect(paramsBuffer, rgbaBuffer, width, height, maskBuffer, outBuffer)
    }

    // ─── Native declarations ──────────────────────────────────────────────

    private external fun nativeLoadModel(modelBuffer: ByteBuffer, modelType: Int): Long
    private external fun nativeCloseModel(handle: Long)
    private external fun nativeSegmentFrame(
        handle: Long,
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        maskBuffer: ByteBuffer,
    ): Boolean
    private external fun nativeGenerateEffectParams(effectType: Int, paramsBuffer: ByteBuffer)
    private external fun nativeApplyEffect(
        paramsBuffer: ByteBuffer,
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        maskBuffer: ByteBuffer,
        outBuffer: ByteBuffer,
    ): Boolean
}
