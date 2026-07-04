package id.my.daniza.segment

import android.content.Context
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SegmentEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "SegmentEngine"
        private const val EFFECT_PARAMS_SIZE = 64

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

    @Volatile
    var sinetHandle = 0L
        private set

    @Volatile
    var mediapipeHandle = 0L
        private set

    @Volatile
    private var sinetLoaded = false

    @Volatile
    private var mediapipeLoaded = false

    private val loadLock = Any()

    val segmentReady: Boolean get() = sinetHandle != 0L

    fun preloadSinet() {
        ensureSinetLoaded()
    }

    fun preloadMediapipe() {
        ensureMediapipeLoaded()
    }

    private fun ensureSinetLoaded() {
        if (sinetLoaded) return
        synchronized(loadLock) {
            if (sinetLoaded) return
            val delegateFlags = NativeSegment.DELEGATE_XNNPACK or NativeSegment.DELEGATE_NNAPI
            loadModel(appContext, "models/sinet.tflite", NativeSegment.MODEL_SINET, delegateFlags) { sinetHandle = it }
            sinetLoaded = true
        }
    }

    private fun ensureMediapipeLoaded() {
        if (mediapipeLoaded) return
        synchronized(loadLock) {
            if (mediapipeLoaded) return
            val delegateFlags = NativeSegment.DELEGATE_XNNPACK or NativeSegment.DELEGATE_NNAPI
            loadModel(appContext, "models/mediapipe_selfie.tflite", NativeSegment.MODEL_MEDIAPIPE_SELFIE, delegateFlags) { mediapipeHandle = it }
            mediapipeLoaded = true
        }
    }

    private fun loadModel(context: Context, path: String, type: Int, delegateFlags: Int, onSuccess: (Long) -> Unit) {
        try {
            val bytes = context.assets.open(path).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            val threads = NativeSegment.getOptimalThreadCount()
            val handle = NativeSegment.loadModel(buf, type, delegateFlags, threads)
            if (handle != 0L) {
                onSuccess(handle)
                Log.i(TAG, "Loaded $path")
            } else {
                Log.e(TAG, "Failed to create model: $path")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Asset not found: $path")
        }
    }

    fun segmentFrame(rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer): Boolean {
        ensureSinetLoaded()
        return sinetHandle != 0L && NativeSegment.segmentFrame(sinetHandle, rgba, width, height, mask)
    }

    fun segmentFrameWithMediapipe(rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer): Boolean {
        ensureMediapipeLoaded()
        val h = if (mediapipeHandle != 0L) mediapipeHandle else {
            ensureSinetLoaded()
            sinetHandle
        }
        return h != 0L && NativeSegment.segmentFrame(h, rgba, width, height, mask)
    }

    fun applyEffect(effectType: Int, rgba: ByteBuffer, width: Int, height: Int, mask: ByteBuffer, out: ByteBuffer): Boolean {
        ensureSinetLoaded()
        if (sinetHandle == 0L) return false
        val paramsBuf = ByteBuffer.allocateDirect(EFFECT_PARAMS_SIZE).apply {
            order(ByteOrder.nativeOrder())
        }
        NativeSegment.generateEffectParams(effectType, paramsBuf)
        return NativeSegment.applyEffect(paramsBuf, rgba, width, height, mask, out)
    }

    fun segmentAndApply(effectType: Int, rgba: ByteBuffer, width: Int, height: Int, out: ByteBuffer): Boolean {
        ensureSinetLoaded()
        if (sinetHandle == 0L) return false
        val maskBuf = ByteBuffer.allocateDirect(width * height * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        if (!NativeSegment.segmentFrame(sinetHandle, rgba, width, height, maskBuf)) {
            Log.w(TAG, "segmentFrame failed")
            return false
        }
        maskBuf.rewind()
        return applyEffect(effectType, rgba, width, height, maskBuf, out)
    }

    private fun closeAll() {
        if (sinetHandle != 0L) {
            NativeSegment.closeModel(sinetHandle)
            sinetHandle = 0L
        }
        if (mediapipeHandle != 0L) {
            NativeSegment.closeModel(mediapipeHandle)
            mediapipeHandle = 0L
        }
        sinetLoaded = false
        mediapipeLoaded = false
    }
}
