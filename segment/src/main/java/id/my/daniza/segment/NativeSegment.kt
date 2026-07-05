package id.my.daniza.segment

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

object NativeSegment {

    private val sinetPipeline = SegmentationPipeline(ModelConfig.SINET)
    private val mediapipePipeline = SegmentationPipeline(ModelConfig.MEDIAPIPE)

    @Volatile
    var sinetInterpreter: Interpreter? = null
        internal set

    @Volatile
    var mediapipeInterpreter: Interpreter? = null
        internal set

    @Volatile
    var sinetReady = false
        private set

    @Volatile
    var mediapipeReady = false
        private set

    fun loadSinet(context: Context, assetPath: String) {
        closeSinet()
        sinetInterpreter = loadModel(context, assetPath)
        sinetReady = sinetInterpreter != null
    }

    fun loadMediapipe(context: Context, assetPath: String) {
        closeMediapipe()
        mediapipeInterpreter = loadModel(context, assetPath)
        mediapipeReady = mediapipeInterpreter != null
    }

    private fun loadModel(context: Context, assetPath: String): Interpreter? {
        return try {
            val fd = context.assets.openFd(assetPath)
            val stream = FileInputStream(fd.fileDescriptor)
            val channel = stream.channel
            val buf = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            Interpreter(buf, Interpreter.Options().apply {
                setNumThreads(maxOf(2, Runtime.getRuntime().availableProcessors() / 2))
            })
        } catch (e: Exception) {
            null
        }
    }

    fun closeSinet() {
        sinetInterpreter?.close()
        sinetInterpreter = null
        sinetReady = false
    }

    fun closeMediapipe() {
        mediapipeInterpreter?.close()
        mediapipeInterpreter = null
        mediapipeReady = false
    }

    fun closeAll() {
        closeSinet()
        closeMediapipe()
    }

    fun segmentFrame(
        interpreter: Interpreter,
        rgbaBuffer: ByteBuffer,
        width: Int,
        height: Int,
        maskBuffer: ByteBuffer,
        modelType: Int = 0,
    ): Boolean {
        val pipeline = if (modelType == 1) mediapipePipeline else sinetPipeline
        return pipeline.execute(interpreter, rgbaBuffer, width, height, maskBuffer, modelType)
    }
}
