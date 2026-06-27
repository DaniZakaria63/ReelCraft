package id.my.daniza.ffmpeg

import java.nio.ByteBuffer

object NativeFFmpeg {

    init {
        System.loadLibrary("ffmpeg")
    }

    // ─── Decoder ──────────────────────────────────────────────────────────

    fun decoderOpen(path: String): Long = nativeDecoderOpen(path)

    fun decoderClose(handle: Long) = nativeDecoderClose(handle)

    fun decoderWidth(handle: Long): Int = nativeDecoderWidth(handle)

    fun decoderHeight(handle: Long): Int = nativeDecoderHeight(handle)

    fun decoderDurationUs(handle: Long): Long = nativeDecoderDurationUs(handle)

    fun decoderFrameRate(handle: Long): Double {
        val millifps = nativeDecoderFrameRate(handle)
        return if (millifps > 0) millifps / 1000.0 else 30.0
    }

    fun decoderRotation(handle: Long): Int = nativeDecoderRotation(handle)

    fun decoderFrameSize(handle: Long): Int = nativeDecoderFrameSize(handle)

    fun decoderSeek(handle: Long, timestampUs: Long): Boolean =
        nativeDecoderSeek(handle, timestampUs)

    fun decoderReadFrame(handle: Long, buffer: ByteBuffer): Boolean =
        nativeDecoderReadFrame(handle, buffer)

    // ─── Encoder ──────────────────────────────────────────────────────────

    fun encoderOpen(
        path: String,
        width: Int,
        height: Int,
        frameRateNum: Int = 30,
        frameRateDen: Int = 1,
        bitRate: Int = 8_000_000,
    ): Long = nativeEncoderOpen(path, width, height, frameRateNum, frameRateDen, bitRate)

    fun encoderClose(handle: Long) = nativeEncoderClose(handle)

    fun encoderEncodeFrameRgba(handle: Long, buffer: ByteBuffer): Boolean =
        nativeEncoderEncodeFrameRgba(handle, buffer)

    fun encoderFinalize(handle: Long): Boolean = nativeEncoderFinalize(handle)

    // ─── Filter Graph ─────────────────────────────────────────────────────

    fun filterGraphCreate(width: Int, height: Int, filterDesc: String): Long =
        nativeFilterGraphCreate(width, height, filterDesc)

    fun filterGraphClose(handle: Long) = nativeFilterGraphClose(handle)

    fun filterGraphProcess(
        handle: Long,
        inBuffer: ByteBuffer,
        inWidth: Int,
        inHeight: Int,
        outBuffer: ByteBuffer,
        outWidth: Int,
        outHeight: Int,
    ): Boolean = nativeFilterGraphProcess(handle, inBuffer, inWidth, inHeight, outBuffer, outWidth, outHeight)

    // ─── Compositor ───────────────────────────────────────────────────────

    fun compositeFrame(
        bgBuffer: ByteBuffer, bgWidth: Int, bgHeight: Int,
        fgBuffer: ByteBuffer, fgWidth: Int, fgHeight: Int,
        posX: Int = 0, posY: Int = 0, opacity: Float = 1f,
    ): Boolean = nativeCompositeFrame(bgBuffer, bgWidth, bgHeight, fgBuffer, fgWidth, fgHeight, posX, posY, opacity)

    fun compositeWithMask(
        bgBuffer: ByteBuffer, fgBuffer: ByteBuffer, maskBuffer: ByteBuffer,
        width: Int, height: Int, intensity: Float = 1f,
    ): Boolean = nativeCompositeWithMask(bgBuffer, fgBuffer, maskBuffer, width, height, intensity)

    fun compositeCheckerboard(buffer: ByteBuffer, width: Int, height: Int, tileSize: Int = 16): Boolean =
        nativeCompositeCheckerboard(buffer, width, height, tileSize)

    // ─── Temporal ─────────────────────────────────────────────────────────

    fun temporalSpeedChange(
        srcBuffer: ByteBuffer, srcCount: Int, speed: Float,
        dstBuffer: ByteBuffer, dstCapacity: Int, width: Int, height: Int,
    ): Int = nativeTemporalSpeedChange(srcBuffer, srcCount, speed, dstBuffer, dstCapacity, width, height)

    fun temporalReverse(
        srcBuffer: ByteBuffer, srcCount: Int,
        dstBuffer: ByteBuffer, dstCapacity: Int, width: Int, height: Int,
    ): Int = nativeTemporalReverse(srcBuffer, srcCount, dstBuffer, dstCapacity, width, height)

    fun temporalBlendFrames(
        frameA: ByteBuffer, frameB: ByteBuffer, factor: Float,
        outBuffer: ByteBuffer, width: Int, height: Int,
    ): Boolean = nativeTemporalBlendFrames(frameA, frameB, factor, outBuffer, width, height)

    // ─── Native declarations ──────────────────────────────────────────────

    private external fun nativeDecoderOpen(path: String): Long
    private external fun nativeDecoderClose(handle: Long)
    private external fun nativeDecoderWidth(handle: Long): Int
    private external fun nativeDecoderHeight(handle: Long): Int
    private external fun nativeDecoderDurationUs(handle: Long): Long
    private external fun nativeDecoderFrameRate(handle: Long): Long
    private external fun nativeDecoderRotation(handle: Long): Int
    private external fun nativeDecoderFrameSize(handle: Long): Int
    private external fun nativeDecoderSeek(handle: Long, timestampUs: Long): Boolean
    private external fun nativeDecoderReadFrame(handle: Long, buffer: ByteBuffer): Boolean

    private external fun nativeEncoderOpen(
        path: String, width: Int, height: Int,
        frameRateNum: Int, frameRateDen: Int, bitRate: Int,
    ): Long
    private external fun nativeEncoderClose(handle: Long)
    private external fun nativeEncoderEncodeFrameRgba(handle: Long, buffer: ByteBuffer): Boolean
    private external fun nativeEncoderFinalize(handle: Long): Boolean

    private external fun nativeFilterGraphCreate(width: Int, height: Int, filterDesc: String): Long
    private external fun nativeFilterGraphClose(handle: Long)
    private external fun nativeFilterGraphProcess(
        handle: Long, inBuffer: ByteBuffer, inWidth: Int, inHeight: Int,
        outBuffer: ByteBuffer, outWidth: Int, outHeight: Int,
    ): Boolean

    private external fun nativeCompositeFrame(
        bgBuffer: ByteBuffer, bgWidth: Int, bgHeight: Int,
        fgBuffer: ByteBuffer, fgWidth: Int, fgHeight: Int,
        posX: Int, posY: Int, opacity: Float,
    ): Boolean
    private external fun nativeCompositeWithMask(
        bgBuffer: ByteBuffer, fgBuffer: ByteBuffer, maskBuffer: ByteBuffer,
        width: Int, height: Int, intensity: Float,
    ): Boolean
    private external fun nativeCompositeCheckerboard(buffer: ByteBuffer, width: Int, height: Int, tileSize: Int): Boolean

    private external fun nativeTemporalSpeedChange(
        srcBuffer: ByteBuffer, srcCount: Int, speed: Float,
        dstBuffer: ByteBuffer, dstCapacity: Int, width: Int, height: Int,
    ): Int
    private external fun nativeTemporalReverse(
        srcBuffer: ByteBuffer, srcCount: Int,
        dstBuffer: ByteBuffer, dstCapacity: Int, width: Int, height: Int,
    ): Int
    private external fun nativeTemporalBlendFrames(
        frameA: ByteBuffer, frameB: ByteBuffer, factor: Float,
        outBuffer: ByteBuffer, width: Int, height: Int,
    ): Boolean
}
