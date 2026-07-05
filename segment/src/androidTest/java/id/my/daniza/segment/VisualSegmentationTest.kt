package id.my.daniza.segment

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class VisualSegmentationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SegmentEngine.init(context)
        SegmentEngine.getInstance().preloadSinet()
        SegmentEngine.getInstance().preloadMediapipe()
    }

    @After
    fun tearDown() {
        SegmentEngine.close()
    }

    @Test
    fun processTestFrames_withBothModels() {
        val engine = SegmentEngine.getInstance()
        val testAssets = context.assets.list("test_frames") ?: emptyArray()
        val imageFiles = testAssets.filter { it.endsWith(".jpg") || it.endsWith(".png") }

        if (imageFiles.isEmpty()) {
            android.util.Log.w("VisualTest", "No test images found in assets/test_frames/")
            return
        }

        val resolver = context.contentResolver
        var savedCount = 0

        for (fileName in imageFiles) {
            val inputStream = context.assets.open("test_frames/$fileName")
            val srcBitmap = BitmapFactory.decodeStream(inputStream)
            if (srcBitmap == null) {
                android.util.Log.w("VisualTest", "Failed to decode $fileName")
                continue
            }

            val w = srcBitmap.width
            val h = srcBitmap.height
            val baseName = fileName.substringBeforeLast(".")
            val rgba = ByteBuffer.allocateDirect(w * h * 4).apply { order(ByteOrder.nativeOrder()) }
            srcBitmap.copyPixelsToBuffer(rgba)
            rgba.rewind()

            savedCount += runModel(engine, resolver, srcBitmap, rgba, w, h, baseName, "sinet", modelType = 0)

            rgba.rewind()
            savedCount += runModel(engine, resolver, srcBitmap, rgba, w, h, baseName, "mediapipe", modelType = 1)

            srcBitmap.recycle()
        }

        android.util.Log.i("VisualTest", "Done. Open Gallery > Pictures/ReelCraftTest to view $savedCount results.")
    }

    private fun runModel(
        engine: SegmentEngine,
        resolver: android.content.ContentResolver,
        srcBitmap: Bitmap,
        rgba: ByteBuffer,
        w: Int, h: Int,
        baseName: String,
        modelLabel: String,
        modelType: Int,
    ): Int {
        val mask = ByteBuffer.allocateDirect(w * h * 4).apply { order(ByteOrder.nativeOrder()) }
        val ok = if (modelType == 1) {
            engine.segmentFrameWithMediapipe(rgba, w, h, mask)
        } else {
            engine.segmentFrame(rgba, w, h, mask)
        }
        if (!ok) {
            android.util.Log.w("VisualTest", "Segmentation failed for ${baseName}_$modelLabel")
            return 0
        }

        mask.rewind()
        val maskValues = FloatArray(w * h)
        for (i in maskValues.indices) maskValues[i] = mask.float

        val outBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        for (i in 0 until w * h) {
            val m = (maskValues[i] * 255f).toInt().coerceIn(0, 255)
            val sr = (srcBitmap.getPixel(i % w, i / w) shr 16) and 0xFF
            val sg = (srcBitmap.getPixel(i % w, i / w) shr 8) and 0xFF
            val sb = srcBitmap.getPixel(i % w, i / w) and 0xFF
            val overlay = m > 128
            val r = if (overlay) (sr * 0.3f + 255 * 0.7f).toInt().coerceIn(0, 255) else sr
            val g = if (overlay) (sg * 0.3f + 0 * 0.7f).toInt().coerceIn(0, 255) else sg
            val b = if (overlay) (sb * 0.3f + 0 * 0.7f).toInt().coerceIn(0, 255) else sb
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        outBitmap.setPixels(pixels, 0, w, 0, 0, w, h)

        val displayName = "${baseName}_${modelLabel}_mask.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ReelCraftTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { stream ->
                outBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            android.util.Log.i("VisualTest", "Saved: Pictures/ReelCraftTest/$displayName")
        }

        outBitmap.recycle()
        return 1
    }
}
