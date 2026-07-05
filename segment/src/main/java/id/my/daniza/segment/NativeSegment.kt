package id.my.daniza.segment

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

object NativeSegment {

    private const val MODEL_INPUT_SIZE = 256

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
        rgbaBuffer.rewind()
        val inputSize = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3
        val inputBuf = ByteBuffer.allocateDirect(inputSize * 4).apply { order(ByteOrder.nativeOrder()) }
        bilinearResizeRgbaToRgbFloat(rgbaBuffer, width, height, inputBuf, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)
        inputBuf.rewind()

        val outputElems = MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * (if (modelType == 1) 1 else 2)
        val outputBuf = ByteBuffer.allocateDirect(outputElems * 4).apply { order(ByteOrder.nativeOrder()) }

        interpreter.run(inputBuf, outputBuf)
        outputBuf.rewind()

        val smallMask = FloatArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        if (modelType == 1) {
            for (i in smallMask.indices) {
                val v = outputBuf.float
                smallMask[i] = 1.0f / (1.0f + exp(-v))
            }
        } else {
            for (i in smallMask.indices) {
                val bg = outputBuf.float
                val fg = outputBuf.float
                val maxVal = maxOf(bg, fg)
                val sum = exp(bg - maxVal) + exp(fg - maxVal)
                smallMask[i] = exp(fg - maxVal) / sum
            }
        }

        maskBuffer.rewind()
        bilinearUpscaleMask(smallMask, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, maskBuffer, width, height)
        maskBuffer.rewind()
        return true
    }

    private fun bilinearResizeRgbaToRgbFloat(
        src: ByteBuffer, srcW: Int, srcH: Int,
        dst: ByteBuffer, dstW: Int, dstH: Int,
    ) {
        val srcBytes = ByteArray(srcW * srcH * 4)
        src.get(srcBytes)
        for (dy in 0 until dstH) {
            val srcYf = if (dstH == 1) 0f else dy.toFloat() * (srcH - 1) / (dstH - 1)
            val sy0 = srcYf.toInt()
            val sy1 = minOf(sy0 + 1, srcH - 1)
            val fy = srcYf - sy0

            for (dx in 0 until dstW) {
                val srcXf = if (dstW == 1) 0f else dx.toFloat() * (srcW - 1) / (dstW - 1)
                val sx0 = srcXf.toInt()
                val sx1 = minOf(sx0 + 1, srcW - 1)
                val fx = srcXf - sx0

                for (c in 0 until 3) {
                    val v00 = (srcBytes[(sy0 * srcW + sx0) * 4 + c].toInt() and 0xFF).toFloat()
                    val v01 = (srcBytes[(sy0 * srcW + sx1) * 4 + c].toInt() and 0xFF).toFloat()
                    val v10 = (srcBytes[(sy1 * srcW + sx0) * 4 + c].toInt() and 0xFF).toFloat()
                    val v11 = (srcBytes[(sy1 * srcW + sx1) * 4 + c].toInt() and 0xFF).toFloat()
                    val top = v00 + (v01 - v00) * fx
                    val bot = v10 + (v11 - v10) * fx
                    dst.putFloat((top + (bot - top) * fy) / 255f)
                }
            }
        }
    }

    private fun bilinearUpscaleMask(
        src: FloatArray, srcW: Int, srcH: Int,
        dst: ByteBuffer, dstW: Int, dstH: Int,
    ) {
        for (dy in 0 until dstH) {
            val srcYf = if (dstH == 1) 0f else dy.toFloat() * (srcH - 1) / (dstH - 1)
            val sy0 = srcYf.toInt()
            val sy1 = minOf(sy0 + 1, srcH - 1)
            val fy = srcYf - sy0

            for (dx in 0 until dstW) {
                val srcXf = if (dstW == 1) 0f else dx.toFloat() * (srcW - 1) / (dstW - 1)
                val sx0 = srcXf.toInt()
                val sx1 = minOf(sx0 + 1, srcW - 1)
                val fx = srcXf - sx0

                val idx00 = sy0 * srcW + sx0
                val idx01 = sy0 * srcW + sx1
                val idx10 = sy1 * srcW + sx0
                val idx11 = sy1 * srcW + sx1

                val v = src[idx00] * (1 - fx) * (1 - fy) +
                        src[idx01] * fx * (1 - fy) +
                        src[idx10] * (1 - fx) * fy +
                        src[idx11] * fx * fy

                dst.putFloat(v)
            }
        }
    }
}
