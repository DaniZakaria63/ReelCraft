package id.my.daniza.segment

import android.content.Context
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer

class SegmentEngine(private val context: Context) {

    companion object {
        private const val EFFECT_PARAMS_SIZE = 64
    }

    private var sinetHandle: Long = 0L
    private var mediapipeHandle: Long = 0L

    val isLoaded: Boolean get() = sinetHandle != 0L

    fun loadAll(): Boolean {
        sinetHandle = loadModel("models/sinet.tflite", NativeSegment.MODEL_SINET)
        mediapipeHandle = loadModel("models/mediapipe_selfie.tflite", NativeSegment.MODEL_MEDIAPIPE_SELFIE)
        return sinetHandle != 0L
    }

    fun segmentFrame(
        rgba: ByteBuffer,
        width: Int,
        height: Int,
        mask: ByteBuffer
    ): Boolean {
        if (sinetHandle == 0L) return false
        return NativeSegment.segmentFrame(sinetHandle, rgba, width, height, mask)
    }

    fun applyEffect(
        effectType: Int,
        rgba: ByteBuffer,
        width: Int,
        height: Int,
        mask: ByteBuffer,
        out: ByteBuffer
    ): Boolean {
        val paramsBuf = ByteBuffer.allocateDirect(EFFECT_PARAMS_SIZE)
        NativeSegment.generateEffectParams(effectType, paramsBuf)
        paramsBuf.rewind()
        return NativeSegment.applyEffect(paramsBuf, rgba, width, height, mask, out)
    }

    fun closeAll() {
        if (sinetHandle != 0L) {
            NativeSegment.closeModel(sinetHandle)
            sinetHandle = 0L
        }
        if (mediapipeHandle != 0L) {
            NativeSegment.closeModel(mediapipeHandle)
            mediapipeHandle = 0L
        }
    }

    private fun loadModel(assetPath: String, modelType: Int): Long {
        return try {
            context.assets.open(assetPath).use { stream ->
                val bytes = stream.readBytes()
                val buffer = ByteBuffer.allocateDirect(bytes.size)
                buffer.put(bytes)
                buffer.rewind()
                NativeSegment.loadModel(buffer, modelType)
            }
        } catch (e: IOException) {
            Log.e("SegmentEngine", "Failed to load $assetPath", e)
            0L
        }
    }

    protected fun finalize() {
        closeAll()
    }
}
