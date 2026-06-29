package id.my.daniza.segment

import android.content.Context
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SegmentEngine private constructor() {

    companion object {
        private const val TAG = "SegmentEngine"
        private const val EFFECT_PARAMS_SIZE = 64

        @Volatile
        private var instance: SegmentEngine? = null

        fun init(context: Context): SegmentEngine {
            val engine = instance
            if (engine != null) return engine
            return synchronized(this) {
                instance ?: SegmentEngine().also { e ->
                    e.loadAll(context)
                    instance = e
                }
            }
        }

        fun getInstance(): SegmentEngine {
            return instance ?: error("SegmentEngine not initialized — call SegmentEngine.init(context) first")
        }
    }

    private var sinetHandle: Long = 0L
    private var mediapipeHandle: Long = 0L
    private var sinetModelData: ByteArray? = null
    private var mediapipeModelData: ByteArray? = null

    val segmentReady: Boolean get() = sinetHandle != 0L

    private fun loadAll(context: Context) {
        sinetModelData = loadAssetBytes(context, "models/sinet.tflite")
        if (sinetModelData != null) {
            sinetHandle = createModel(sinetModelData!!, NativeSegment.MODEL_SINET)
            if (sinetHandle == 0L) {
                Log.e(TAG, "Failed to create SINet model handle")
            } else {
                Log.i(TAG, "SINet model loaded (handle=$sinetHandle)")
            }
        } else {
            Log.e(TAG, "SINet model data not found in assets")
        }

        mediapipeModelData = loadAssetBytes(context, "models/mediapipe_selfie.tflite")
        if (mediapipeModelData != null) {
            mediapipeHandle = createModel(mediapipeModelData!!, NativeSegment.MODEL_MEDIAPIPE_SELFIE)
            if (mediapipeHandle == 0L) {
                Log.w(TAG, "MediaPipe Selfie model failed to load")
            } else {
                Log.i(TAG, "MediaPipe Selfie model loaded (handle=$mediapipeHandle)")
            }
        } else {
            Log.w(TAG, "MediaPipe Selfie model not found in assets")
        }
    }

    fun segmentFrame(rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer): Boolean {
        if (sinetHandle == 0L) return false
        return NativeSegment.segmentFrame(sinetHandle, rgba, width, height, mask)
    }

    fun segmentFrameWithMediapipe(rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer): Boolean {
        val handle = if (mediapipeHandle != 0L) mediapipeHandle else sinetHandle
        if (handle == 0L) return false
        return NativeSegment.segmentFrame(handle, rgba, width, height, mask)
    }

    fun applyEffect(effectType: Int, rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer, out: ByteBuffer): Boolean {
        if (sinetHandle == 0L) return false

        val paramsBuf = ByteBuffer.allocateDirect(EFFECT_PARAMS_SIZE)
        paramsBuf.order(ByteOrder.nativeOrder())
        NativeSegment.generateEffectParams(effectType, paramsBuf)
        paramsBuf.rewind()
        return NativeSegment.applyEffect(paramsBuf, rgba, width, height, mask, out)
    }

    fun segmentAndApply(effectType: Int, rgba: ByteBuffer, width: Int, height: Int, out: ByteBuffer): Boolean {
        if (sinetHandle == 0L) return false

        val maskSize = width * height * 4
        val maskBuf = ByteBuffer.allocateDirect(maskSize)
        maskBuf.order(ByteOrder.nativeOrder())

        if (!NativeSegment.segmentFrame(sinetHandle, rgba, width, height, maskBuf)) {
            Log.w(TAG, "segmentFrame failed, falling back to passthrough")
            out.put(rgba)
            out.rewind()
            rgba.rewind()
            return false
        }
        maskBuf.rewind()

        return applyEffect(effectType, rgba, width, height, maskBuf, out)
    }

    fun closeAll() {
        if (sinetHandle != 0L) {
            NativeSegment.closeModel(sinetHandle)
            sinetHandle = 0L
            Log.i(TAG, "SINet model closed")
        }
        if (mediapipeHandle != 0L) {
            NativeSegment.closeModel(mediapipeHandle)
            mediapipeHandle = 0L
            Log.i(TAG, "MediaPipe model closed")
        }
        sinetModelData = null
        mediapipeModelData = null
    }

    private fun loadAssetBytes(context: Context, assetPath: String): ByteArray? {
        return try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read asset: $assetPath", e)
            null
        }
    }

    private fun createModel(data: ByteArray, modelType: Int): Long {
        val buffer = ByteBuffer.allocateDirect(data.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(data)
        buffer.rewind()
        return NativeSegment.loadModel(buffer, modelType)
    }

    protected fun finalize() {
        closeAll()
    }
}
