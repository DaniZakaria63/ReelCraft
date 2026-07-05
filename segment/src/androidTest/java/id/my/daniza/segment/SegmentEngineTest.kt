package id.my.daniza.segment

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class SegmentEngineTest {

    private lateinit var context: Context
    private val width = 640
    private val height = 480
    private val rgbaSize = width * height * 4
    private val maskSize = width * height * 4

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SegmentEngine.init(context)
        SegmentEngine.getInstance().preloadSinet()
    }

    @After
    fun tearDown() {
        SegmentEngine.close()
    }

    @Test
    fun loadSinetModel_doesNotCrash() {
        assertTrue(SegmentEngine.getInstance().segmentReady)
    }

    @Test
    fun segmentFrame_returnsValidMask() {
        val engine = SegmentEngine.getInstance()
        assertTrue(engine.segmentReady)

        val rgba = createRandomRgba()
        val mask = ByteBuffer.allocateDirect(maskSize).apply { order(ByteOrder.nativeOrder()) }

        val ok = engine.segmentFrame(rgba, width, height, mask)
        assertTrue(ok)

        mask.rewind()
        val values = FloatArray(width * height)
        for (i in values.indices) values[i] = mask.float

        for (v in values) {
            assertTrue("Mask value $v out of [0,1]", v in 0f..1f)
        }
    }

    @Test
    fun segmentFrame_largeFrame_doesNotCrash() {
        val engine = SegmentEngine.getInstance()
        val bigW = 1920; val bigH = 1080
        val rgba = ByteBuffer.allocateDirect(bigW * bigH * 4).apply {
            order(ByteOrder.nativeOrder())
            val rng = Random(42)
            val bytes = ByteArray(bigW * bigH * 4)
            rng.nextBytes(bytes)
            put(bytes)
            rewind()
        }
        val mask = ByteBuffer.allocateDirect(bigW * bigH * 4).apply { order(ByteOrder.nativeOrder()) }

        val ok = engine.segmentFrame(rgba, bigW, bigH, mask)
        assertTrue(ok)
    }

    @Test
    fun applyEffect_doesNotCrash() {
        val engine = SegmentEngine.getInstance()
        val rgba = createRandomRgba()
        val mask = createFakeMask()
        val out = ByteBuffer.allocateDirect(rgbaSize).apply { order(ByteOrder.nativeOrder()) }

        for (effectType in 0..12) {
            rgba.rewind(); mask.rewind(); out.clear()
            val ok = engine.applyEffect(effectType, rgba, width, height, mask, out)
            assertTrue("Effect $effectType failed", ok)
        }
    }

    @Test
    fun segmentAndApply_producesOutput() {
        val engine = SegmentEngine.getInstance()
        val rgba = createRandomRgba()
        val out = ByteBuffer.allocateDirect(rgbaSize).apply { order(ByteOrder.nativeOrder()) }

        val ok = engine.segmentAndApply(
            SegmentEffects.EFFECT_DRAMATIC, rgba, width, height, out
        )
        assertTrue(ok)
        out.rewind()
        val firstPixel = out.int
        assertTrue(firstPixel != 0)
    }

    @Test
    fun effectsGenerateParams_hasValidColors() {
        for (effectType in 0..12) {
            val params = SegmentEffects.generateParams(effectType)
            assertEquals(effectType, params.type)
            assertTrue(params.color1R in 0..255)
            assertTrue(params.color1G in 0..255)
            assertTrue(params.color1B in 0..255)
        }
    }

    @Test
    fun effectsApply_toByteArray_doesNotCrash() {
        val rgba = ByteArray(rgbaSize).apply { Random(7).nextBytes(this) }
        val mask = FloatArray(width * height) { Random(7).nextFloat().coerceIn(0f, 1f) }
        val out = ByteArray(rgbaSize)

        for (effectType in 0..12) {
            val params = SegmentEffects.generateParams(effectType)
            val ok = SegmentEffects.apply(params, rgba, mask, out, width, height)
            assertTrue("Effect $effectType applyByteArray failed", ok)
            assertNotNull(out[0])
        }
    }

    @Test
    fun segmentFrame_multipleInvocations_consistent() {
        val engine = SegmentEngine.getInstance()
        val rgba = createRandomRgba()
        val maskA = ByteBuffer.allocateDirect(maskSize).apply { order(ByteOrder.nativeOrder()) }
        val maskB = ByteBuffer.allocateDirect(maskSize).apply { order(ByteOrder.nativeOrder()) }

        engine.segmentFrame(rgba, width, height, maskA)
        rgba.rewind()
        engine.segmentFrame(rgba, width, height, maskB)

        maskA.rewind(); maskB.rewind()
        val a = FloatArray(width * height); val b = FloatArray(width * height)
        for (i in a.indices) { a[i] = maskA.float; b[i] = maskB.float }
        for (i in a.indices) {
            assertEquals("Masks differ at index $i", a[i], b[i], 0.001f)
        }
    }

    @Test
    fun segmentFrame_tinyFrame_doesNotCrash() {
        val engine = SegmentEngine.getInstance()
        val tiny = 32
        val rgba = ByteBuffer.allocateDirect(tiny * tiny * 4).apply {
            order(ByteOrder.nativeOrder())
            val bytes = ByteArray(tiny * tiny * 4); Random(1).nextBytes(bytes)
            put(bytes); rewind()
        }
        val mask = ByteBuffer.allocateDirect(tiny * tiny * 4).apply { order(ByteOrder.nativeOrder()) }
        val ok = engine.segmentFrame(rgba, tiny, tiny, mask)
        assertTrue(ok)
    }

    // ─── helpers ────────────────────────────────────────────────────────

    private fun createRandomRgba(): ByteBuffer {
        return ByteBuffer.allocateDirect(rgbaSize).apply {
            order(ByteOrder.nativeOrder())
            val bytes = ByteArray(rgbaSize)
            Random(42).nextBytes(bytes)
            put(bytes)
            rewind()
        }
    }

    private fun createFakeMask(): ByteBuffer {
        return ByteBuffer.allocateDirect(maskSize).apply {
            order(ByteOrder.nativeOrder())
            for (i in 0 until width * height) {
                putFloat(if (i % 3 == 0) 1f else 0f)
            }
            rewind()
        }
    }
}
