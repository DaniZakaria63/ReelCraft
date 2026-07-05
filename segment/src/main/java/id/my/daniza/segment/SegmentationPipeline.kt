package id.my.daniza.segment

import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

class SegmentationPipeline(private val config: ModelConfig) {

    fun preprocess(rgbaBuffer: ByteBuffer, srcW: Int, srcH: Int): ByteBuffer {
        val input = ByteBuffer.allocateDirect(config.inputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        bilinearResizeRgbaToRgbFloat(rgbaBuffer, srcW, srcH, input, config.inputWidth, config.inputHeight)
        input.rewind()
        return input
    }

    fun process(interpreter: Interpreter, input: ByteBuffer): ByteBuffer {
        val output = ByteBuffer.allocateDirect(config.outputSize * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        input.rewind()
        interpreter.run(input, output)
        output.rewind()
        return output
    }

    fun postprocess(output: ByteBuffer, modelType: Int = 0): FloatArray {
        output.rewind()
        val pixels = config.outputWidth * config.outputHeight
        val mask = FloatArray(pixels)

        if (modelType == 1) {
            for (i in 0 until pixels) {
                val v = output.float
                mask[i] = 1.0f / (1.0f + exp(-v))
            }
        } else {
            for (i in 0 until pixels) {
                val bg = output.float
                val fg = output.float
                val maxVal = maxOf(bg, fg)
                val sum = exp(bg - maxVal) + exp(fg - maxVal)
                mask[i] = exp(fg - maxVal) / sum
            }
        }
        return mask
    }

    fun upscaleMask(mask: FloatArray, dst: ByteBuffer, dstW: Int, dstH: Int) {
        dst.rewind()
        bilinearUpscaleMask(mask, config.outputWidth, config.outputHeight, dst, dstW, dstH)
        dst.rewind()
    }

    fun execute(
        interpreter: Interpreter,
        rgbaBuffer: ByteBuffer,
        srcW: Int, srcH: Int,
        maskBuffer: ByteBuffer,
        modelType: Int = 0,
    ): Boolean {
        val input = preprocess(rgbaBuffer, srcW, srcH)
        val output = process(interpreter, input)
        val mask = postprocess(output, modelType)
        upscaleMask(mask, maskBuffer, srcW, srcH)
        return true
    }

    // ── bilinear helpers ──────────────────────────────────────────────

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
