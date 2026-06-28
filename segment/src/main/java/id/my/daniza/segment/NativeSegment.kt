package id.my.daniza.segment

import java.nio.ByteBuffer
import java.nio.ByteOrder

object NativeSegment {

    init {
        System.loadLibrary("segment")
    }

    const val MODEL_SINET = 0
    const val MODEL_MEDIAPIPE_SELFIE = 1

    fun loadModel(modelBuffer: ByteBuffer, modelType: Int): Long {
        return nativeLoadModel(modelBuffer, modelType)
    }

    fun closeModel(handle: Long) {
        nativeCloseModel(handle)
    }

    fun segmentFrame(
        handle: Long,
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        maskBuffer: ByteBuffer,
    ): Boolean {
        return nativeSegmentFrame(handle, rgbaBuffer, width, height, maskBuffer)
    }

    private external fun nativeLoadModel(modelBuffer: ByteBuffer, modelType: Int): Long
    private external fun nativeCloseModel(handle: Long)
    private external fun nativeSegmentFrame(
        handle: Long,
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        maskBuffer: ByteBuffer,
    ): Boolean
}
