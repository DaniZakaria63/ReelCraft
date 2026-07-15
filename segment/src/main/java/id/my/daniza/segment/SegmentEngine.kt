package id.my.daniza.segment

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SegmentEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "SegmentEngine"

        @Volatile
        private var instance: SegmentEngine? = null

        fun init(context: Context) {
            if (instance != null) return
            synchronized(this) {
                if (instance != null) return
                instance = SegmentEngine(context.applicationContext)
            }
        }

        fun getInstance(): SegmentEngine =
            instance ?: error("SegmentEngine.init(context) must be called first")

        fun close() {
            synchronized(this) {
                instance?.closeAll()
                instance = null
            }
        }
    }

    val segmentReady: Boolean get() = NativeSegment.sinetReady

    private val loadLock = Any()

    fun preloadSinet() {
        synchronized(loadLock) {
            if (NativeSegment.sinetReady) return
            NativeSegment.loadSinet(appContext, "models/sinet.tflite")
            if (NativeSegment.sinetReady) {
                Log.i(TAG, "SINet model loaded")
            } else {
                Log.e(TAG, "Failed to load SINet model")
            }
        }
    }

    fun preloadMediapipe() {
        synchronized(loadLock) {
            if (NativeSegment.mediapipeReady) return
            NativeSegment.loadMediapipe(appContext, "models/mediapipe_selfie.tflite")
            if (NativeSegment.mediapipeReady) {
                Log.i(TAG, "MediaPipe model loaded")
            } else {
                Log.e(TAG, "Failed to load MediaPipe model")
            }
        }
    }

    fun preloadAll() {
        preloadSinet()
        preloadMediapipe()
    }

    fun segmentFrame(rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer): Boolean {
        return NativeSegment.sinetReady &&
            NativeSegment.segmentFrame(
                NativeSegment.sinetInterpreter ?: return false,
                rgba, width, height, mask, 0,
            )
    }

    fun segmentFrameWithMediapipe(rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer): Boolean {
        val interp = if (NativeSegment.mediapipeReady) NativeSegment.mediapipeInterpreter
        else if (NativeSegment.sinetReady) NativeSegment.sinetInterpreter
        else return false
        return NativeSegment.segmentFrame(interp!!, rgba, width, height, mask, 1)
    }

    fun applyEffect(effectType: Int, rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer, out: ByteBuffer): Boolean {
        val params = SegmentEffects.generateParams(effectType)
        val rgbaArr = ByteArray(width * height * 4)
        val maskArr = FloatArray(width * height)
        val outArr = ByteArray(width * height * 4)

        rgba.rewind()
        rgba.get(rgbaArr)
        mask.rewind()
        for (i in maskArr.indices) maskArr[i] = mask.float

        SegmentEffects.apply(params, rgbaArr, maskArr, outArr, width, height)

        out.rewind()
        out.put(outArr)
        out.rewind()
        return true
    }

    fun segmentAndApply(effectType: Int, rgba: ByteBuffer, width: Int, height: Int, out: ByteBuffer): Boolean {
        if (!NativeSegment.sinetReady) return false
        val maskBuf = ByteBuffer.allocateDirect(width * height * 4).apply { order(ByteOrder.nativeOrder()) }
        if (!segmentFrame(rgba, width, height, maskBuf)) {
            Log.w(TAG, "segmentFrame failed")
            return false
        }
        maskBuf.rewind()
        return applyEffect(effectType, rgba, width, height, maskBuf, out)
    }

    private fun closeAll() {
        NativeSegment.closeAll()
    }
}
