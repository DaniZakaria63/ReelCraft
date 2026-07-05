package id.my.daniza.segment

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

object SegmentEffects {

    data class EffectParams(
        val type: Int,
        val intensity: Float = 1f,
        val color1R: Int = 0, val color1G: Int = 0, val color1B: Int = 0,
        val color2R: Int = 0, val color2G: Int = 0, val color2B: Int = 0,
        val params: FloatArray = FloatArray(8),
        val seed: Int = 0,
    )

    fun generateParams(effectType: Int): EffectParams {
        val rng = Random((System.nanoTime() xor effectType.toLong()).toInt())
        val p = FloatArray(8)

        val colors = when (effectType) {
            EFFECT_BG_REPLACE -> {
                p[0] = rng.nextFloat() * 180f
                p[1] = 0.1f + rng.nextFloat() * 0.3f
                val h1 = rng.nextFloat() * 360f
                listOf(h1, 0.3f + rng.nextFloat() * 0.7f, 0.2f + rng.nextFloat() * 0.6f,
                    h1 + 30f + rng.nextFloat() * 120f, 0.3f + rng.nextFloat() * 0.7f, 0.2f + rng.nextFloat() * 0.6f)
            }
            EFFECT_INK_SPLASH -> {
                p[0] = (rng.nextInt(3, 13)).toFloat()
                p[1] = 0.05f + rng.nextFloat() * 0.2f
                p[2] = 0.1f + rng.nextFloat() * 0.4f
                listOf(rng.nextFloat() * 360f, 0.5f + rng.nextFloat() * 0.5f, 0.2f + rng.nextFloat() * 0.4f, 0f, 0f, 0f)
            }
            EFFECT_CYBERPUNK_GRID -> {
                val h = if (rng.nextFloat() < 0.5f) 180f else 300f
                p[0] = (rng.nextInt(20, 81)).toFloat()
                p[1] = 1f + rng.nextFloat() * 3f
                p[2] = 0.3f + rng.nextFloat() * 0.5f
                listOf(h, 0.8f + rng.nextFloat() * 0.2f, 0.5f + rng.nextFloat() * 0.3f,
                    if (h > 200f) 300f else 180f, 0.8f + rng.nextFloat() * 0.2f, 0.5f + rng.nextFloat() * 0.3f)
            }
            EFFECT_GLITCH_BG -> {
                p[0] = (rng.nextInt(4, 17)).toFloat()
                p[1] = 5f + rng.nextFloat() * 40f
                p[2] = 1f + rng.nextFloat() * 5f
                listOf(0f, 0f, 0f, 0f, 0f, 0f)
            }
            EFFECT_PIXELATE_BG -> {
                p[0] = (rng.nextInt(8, 49)).toFloat()
                p[1] = 0.05f + rng.nextFloat() * 0.15f
                listOf(0f, 0f, 0f, 0f, 0f, 0f)
            }
            EFFECT_GLOW_SILHOUETTE -> {
                p[0] = 3f + rng.nextFloat() * 10f
                p[1] = 0.5f + rng.nextFloat() * 0.5f
                listOf(rng.nextFloat() * 360f, 0.6f + rng.nextFloat() * 0.4f, 0.4f + rng.nextFloat() * 0.4f, 0f, 0f, 0f)
            }
            EFFECT_NEON_OUTLINE -> {
                p[0] = 1f + rng.nextFloat() * 3f
                p[1] = 0.05f
                listOf(rng.nextFloat() * 360f, 0.8f + rng.nextFloat() * 0.2f, 0.5f, 0f, 0f, 0f)
            }
            EFFECT_VAN_GOGH -> {
                p[0] = 1.5f + rng.nextFloat() * 3f
                p[1] = 0.1f + rng.nextFloat() * 0.3f
                listOf(0f, 0f, 0f, 0f, 0f, 0f)
            }
            EFFECT_DRAMATIC -> {
                p[0] = 1.3f + rng.nextFloat() * 0.5f
                p[1] = 0.3f + rng.nextFloat() * 0.4f
                p[2] = -0.05f - rng.nextFloat() * 0.1f
                listOf(0f, 0f, 0f, 0f, 0f, 0f)
            }
            EFFECT_COLOR_POP -> {
                p[0] = 1.2f + rng.nextFloat() * 0.5f
                p[1] = 0f
                p[2] = 0.02f + rng.nextFloat() * 0.05f
                listOf(0f, 0f, 0f, 0f, 0f, 0f)
            }
            EFFECT_DREAMY -> {
                p[0] = 1f + rng.nextFloat() * 3f
                listOf(30f, 0.3f, 0.6f, 210f, 0.2f, 0.5f)
            }
            EFFECT_GOLDEN_HOUR -> {
                p[0] = 0.2f + rng.nextFloat() * 0.3f
                listOf(45f + rng.nextFloat() * 15f, 0f, 0f, 200f + rng.nextFloat() * 40f, 0f, 0f)
            }
            EFFECT_DOUBLE_EXPOSURE -> {
                p[0] = 0.3f + rng.nextFloat() * 0.5f
                p[1] = rng.nextInt(0, 4).toFloat()
                val h1 = rng.nextFloat() * 360f
                listOf(h1, 0.3f + rng.nextFloat() * 0.5f, 0.3f + rng.nextFloat() * 0.4f,
                    h1 + 180f, 0.3f + rng.nextFloat() * 0.5f, 0.3f + rng.nextFloat() * 0.4f)
            }
            else -> listOf(0f, 0f, 0f, 0f, 0f, 0f)
        }

        val hsl = colors
        val h1 = hsl[0]; val s1 = hsl[1]; val l1 = hsl[2]
        val h2 = hsl[3]; val s2 = hsl[4]; val l2 = hsl[5]
        val (c1r, c1g, c1b) = hslToRgb((h1 % 360f + 360f) % 360f, s1.coerceIn(0f, 1f), l1.coerceIn(0f, 1f))
        val (c2r, c2g, c2b) = hslToRgb((h2 % 360f + 360f) % 360f, s2.coerceIn(0f, 1f), l2.coerceIn(0f, 1f))

        return EffectParams(
            type = effectType,
            color1R = c1r, color1G = c1g, color1B = c1b,
            color2R = c2r, color2G = c2g, color2B = c2b,
            params = p,
            seed = rng.nextInt(),
        )
    }

    fun apply(
        params: EffectParams,
        rgba: ByteArray, mask: FloatArray, out: ByteArray,
        width: Int, height: Int,
    ): Boolean {
        val rng = Random(params.seed)
        when (params.type) {
            EFFECT_BG_REPLACE -> applyBgReplace(rgba, mask, out, width, height, params)
            EFFECT_INK_SPLASH -> applyInkSplash(rgba, mask, out, width, height, params, rng)
            EFFECT_CYBERPUNK_GRID -> applyCyberpunkGrid(rgba, mask, out, width, height, params)
            EFFECT_GLITCH_BG -> applyGlitchBg(rgba, mask, out, width, height, params, rng)
            EFFECT_PIXELATE_BG -> applyPixelateBg(rgba, mask, out, width, height, params)
            EFFECT_GLOW_SILHOUETTE -> applyGlowSilhouette(rgba, mask, out, width, height, params)
            EFFECT_NEON_OUTLINE -> applyNeonOutline(rgba, mask, out, width, height, params)
            EFFECT_VAN_GOGH -> applyVanGogh(rgba, mask, out, width, height, params, rng)
            EFFECT_DRAMATIC -> applyDramatic(rgba, mask, out, width, height, params)
            EFFECT_COLOR_POP -> applyColorPop(rgba, mask, out, width, height, params)
            EFFECT_DREAMY -> applyDreamy(rgba, mask, out, width, height, params)
            EFFECT_GOLDEN_HOUR -> applyGoldenHour(rgba, mask, out, width, height, params)
            EFFECT_DOUBLE_EXPOSURE -> applyDoubleExposure(rgba, mask, out, width, height, params, rng)
        }
        return true
    }

    const val EFFECT_BG_REPLACE = 0
    const val EFFECT_INK_SPLASH = 1
    const val EFFECT_CYBERPUNK_GRID = 2
    const val EFFECT_GLITCH_BG = 3
    const val EFFECT_PIXELATE_BG = 4
    const val EFFECT_GLOW_SILHOUETTE = 5
    const val EFFECT_NEON_OUTLINE = 6
    const val EFFECT_VAN_GOGH = 7
    const val EFFECT_DRAMATIC = 8
    const val EFFECT_COLOR_POP = 9
    const val EFFECT_DREAMY = 10
    const val EFFECT_GOLDEN_HOUR = 11
    const val EFFECT_DOUBLE_EXPOSURE = 12

    // ─── helpers (private below) ────────────────────────────────────────

    private fun clamp(v: Float): Int = v.toInt().coerceIn(0, 255)
    private fun clampF(v: Float): Float = v.coerceIn(0f, 255f)
    private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun lerpI(a: Int, b: Int, t: Float): Int = clamp(a + (b - a) * t)
    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = h / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when ((hp.toInt() % 6 + 6) % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return Triple(clamp((r1 + m) * 255f), clamp((g1 + m) * 255f), clamp((b1 + m) * 255f))
    }

    private fun h1_or(default: Float, rng: Random): Float = default

    // ─── effects ────────────────────────────────────────────────────────

    private fun applyBgReplace(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val angle = p.params[0] * Math.PI.toFloat() / 180f
        val feather = p.params[1]
        val cosA = kotlin.math.cos(angle)
        val sinA = kotlin.math.sin(angle)
        val cx = w / 2f; val cy = h / 2f
        val maxDist = sqrt((w * w + h * h).toFloat()) / 2f
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val dx = x - cx; val dy = y - cy
            val t = ((dx * cosA + dy * sinA) / maxDist + 0.5f).coerceIn(0f, 1f)
            val br = lerpI(p.color1R, p.color2R, t)
            val bg = lerpI(p.color1G, p.color2G, t)
            val bb = lerpI(p.color1B, p.color2B, t)
            val m = mask[i / 4]
            val blend = smoothstep(0.5f - feather, 0.5f + feather, m)
            out[i] = lerpI(br, rgba[i].toInt() and 0xFF, blend).toByte()
            out[i + 1] = lerpI(bg, rgba[i + 1].toInt() and 0xFF, blend).toByte()
            out[i + 2] = lerpI(bb, rgba[i + 2].toInt() and 0xFF, blend).toByte()
            out[i + 3] = -1
        }
    }

    private fun applyInkSplash(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams, rng: Random) {
        val num = p.params[0].toInt()
        val radius = p.params[1] * w
        val noise = p.params[2]
        data class Spl(val x: Float, val y: Float, val r: Float, val cr: Int, val cg: Int, val cb: Int)
        val splashes = List(num) {
            val x = rng.nextFloat() * w; val y = rng.nextFloat() * h
            val r = radius * (0.3f + rng.nextFloat() * 0.7f)
            val (cr, cg, cb) = hslToRgb(rng.nextFloat() * 360f, 0.6f + rng.nextFloat() * 0.4f, 0.3f + rng.nextFloat() * 0.3f)
            Spl(x, y, r, cr, cg, cb)
        }
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            if (mask[i / 4] > 0.5f) { rgba.copyInto(out, i, i, i + 4); continue }
            var best = Float.MAX_VALUE; var sr = 0; var sg = 0; var sb = 0
            for (s in splashes) {
                val d = sqrt((x - s.x) * (x - s.x) + (y - s.y) * (y - s.y))
                if (d < best) { best = d; sr = s.cr; sg = s.cg; sb = s.cb }
            }
            var strength = max(0f, 1f - best / radius)
            strength = (strength + (rng.nextFloat() - 0.5f) * noise).coerceIn(0f, 1f)
            out[i] = lerpI(rgba[i].toInt() and 0xFF, sr, strength).toByte()
            out[i + 1] = lerpI(rgba[i + 1].toInt() and 0xFF, sg, strength).toByte()
            out[i + 2] = lerpI(rgba[i + 2].toInt() and 0xFF, sb, strength).toByte()
            out[i + 3] = -1
        }
    }

    private fun applyCyberpunkGrid(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val cell = p.params[0].toInt()
        val lineW = p.params[1]; val glow = p.params[2]
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            if (mask[i / 4] > 0.5f) { rgba.copyInto(out, i, i, i + 4); continue }
            val gx = x % cell; val gy = y % cell
            val dH = minOf(gx, cell - gx).toFloat(); val dV = minOf(gy, cell - gy).toFloat()
            val d = minOf(dH, dV)
            if (d < glow) {
                val intensity = smoothstep(glow, 0f, d)
                val main = smoothstep(glow, lineW, d)
                val r1 = lerpI(p.color1R, p.color2R, intensity)
                val g1 = lerpI(p.color1G, p.color2G, intensity)
                val b1 = lerpI(p.color1B, p.color2B, intensity)
                if (main <= 0f) {
                    out[i] = clamp(lerpF((rgba[i].toInt() and 0xFF).toFloat(), r1.toFloat(), 0.9f)).toByte()
                    out[i + 1] = clamp(lerpF((rgba[i + 1].toInt() and 0xFF).toFloat(), g1.toFloat(), 0.9f)).toByte()
                    out[i + 2] = clamp(lerpF((rgba[i + 2].toInt() and 0xFF).toFloat(), b1.toFloat(), 0.9f)).toByte()
                } else {
                    val gs = max(0f, intensity - main)
                    out[i] = lerpI(rgba[i].toInt() and 0xFF, r1, gs * 0.5f).toByte()
                    out[i + 1] = lerpI(rgba[i + 1].toInt() and 0xFF, g1, gs * 0.5f).toByte()
                    out[i + 2] = lerpI(rgba[i + 2].toInt() and 0xFF, b1, gs * 0.5f).toByte()
                }
            } else {
                out[i] = ((rgba[i].toInt() and 0xFF) * 7 / 10).toByte()
                out[i + 1] = ((rgba[i + 1].toInt() and 0xFF) * 7 / 10).toByte()
                out[i + 2] = ((rgba[i + 2].toInt() and 0xFF) * 7 / 10).toByte()
            }
            out[i + 3] = -1
        }
    }

    private fun applyGlitchBg(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams, rng: Random) {
        val numSlices = p.params[0].toInt()
        val maxOff = p.params[1].toInt(); val chroma = p.params[2].toInt()
        rgba.copyInto(out)
        data class Slice(val y0: Int, val y1: Int, val off: Int, val cR: Int, val cB: Int)
        val slices = mutableListOf<Slice>()
        var ypos = 0
        for (i in 0 until numSlices) {
            val sh = h / numSlices + rng.nextInt(-h / (numSlices * 3), h / (numSlices * 3) + 1)
            slices += Slice(ypos, min(ypos + max(sh, 1), h), rng.nextInt(-maxOff, maxOff + 1), rng.nextInt(-chroma, chroma + 1), rng.nextInt(-chroma, chroma + 1))
            ypos = slices.last().y1
            if (ypos >= h) break
        }
        for (s in slices) {
            for (y in s.y0 until s.y1) for (x in 0 until w) {
                val i = (y * w + x) * 4
                if (mask[i / 4] > 0.5f) continue
                val sx = (x + s.off).coerceIn(0, w - 1)
                val srcI = (y * w + sx) * 4
                out[i] = rgba[srcI]
                out[i + 1] = rgba[srcI + 1]
                out[i + 2] = rgba[srcI + 2]
                val rIdx = (y * w + (x + s.cR).coerceIn(0, w - 1)) * 4
                val bIdx = (y * w + (x + s.cB).coerceIn(0, w - 1)) * 4
                out[i] = rgba[rIdx]
                out[i + 2] = rgba[bIdx + 2]
            }
        }
    }

    private fun applyPixelateBg(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val block = p.params[0].toInt(); val feather = p.params[1]
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val m = mask[i / 4]
            val t = smoothstep(0.5f - feather, 0.5f + feather, m)
            val bx = ((x / block) * block + block / 2).coerceIn(0, w - 1)
            val by = ((y / block) * block + block / 2).coerceIn(0, h - 1)
            val bi = (by * w + bx) * 4
            out[i] = lerpI(rgba[bi].toInt() and 0xFF, rgba[i].toInt() and 0xFF, t).toByte()
            out[i + 1] = lerpI(rgba[bi + 1].toInt() and 0xFF, rgba[i + 1].toInt() and 0xFF, t).toByte()
            out[i + 2] = lerpI(rgba[bi + 2].toInt() and 0xFF, rgba[i + 2].toInt() and 0xFF, t).toByte()
            out[i + 3] = -1
        }
    }

    private fun applyGlowSilhouette(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val radius = p.params[0]; val opacity = p.params[1]
        val kr = (radius * 2f + 1f).toInt()
        val blurred = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var sum = 0f; var wsum = 0f
            for (ky in -kr..kr) {
                val sy = y + ky; if (sy < 0 || sy >= h) continue
                val gy = exp(-(ky * ky).toFloat() / (2f * radius * radius))
                for (kx in -kr..kr) {
                    val sx = x + kx; if (sx < 0 || sx >= w) continue
                    val g = exp(-(kx * kx).toFloat() / (2f * radius * radius)) * gy
                    sum += mask[sy * w + sx] * g; wsum += g
                }
            }
            if (wsum > 0f) blurred[y * w + x] = sum / wsum
        }
        for (i in 0 until w * h) {
            val edge = abs(blurred[i] - mask[i]) * opacity
            val idx = i * 4
            out[idx] = lerpI(rgba[idx].toInt() and 0xFF, p.color1R, edge).toByte()
            out[idx + 1] = lerpI(rgba[idx + 1].toInt() and 0xFF, p.color1G, edge).toByte()
            out[idx + 2] = lerpI(rgba[idx + 2].toInt() and 0xFF, p.color1B, edge).toByte()
            out[idx + 3] = -1
        }
    }

    private fun applyNeonOutline(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val threshold = p.params[1]
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            if (y == 0 || y == h - 1 || x == 0 || x == w - 1) { rgba.copyInto(out, i, i, i + 4); continue }
            fun m(yy: Int, xx: Int) = mask[yy * w + xx]
            val gx = -m(y - 1, x - 1) + m(y - 1, x + 1) - 2f * m(y, x - 1) + 2f * m(y, x + 1) - m(y + 1, x - 1) + m(y + 1, x + 1)
            val gy = -m(y - 1, x - 1) - 2f * m(y - 1, x) - m(y - 1, x + 1) + m(y + 1, x - 1) + 2f * m(y + 1, x) + m(y + 1, x + 1)
            val edge = min(1f, sqrt(gx * gx + gy * gy) * 3f)
            if (edge > threshold) {
                out[i] = lerpI(rgba[i].toInt() and 0xFF, p.color1R, edge).toByte()
                out[i + 1] = lerpI(rgba[i + 1].toInt() and 0xFF, p.color1G, edge).toByte()
                out[i + 2] = lerpI(rgba[i + 2].toInt() and 0xFF, p.color1B, edge).toByte()
            } else rgba.copyInto(out, i, i, i + 4)
            out[i + 3] = -1
        }
    }

    private fun applyVanGogh(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams, rng: Random) {
        val stroke = p.params[0]; val enhance = p.params[1]
        rgba.copyInto(out)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = (y * w + x) * 4
            if (mask[i / 4] < 0.5f) continue
            val lSelf = (rgba[i].toInt() and 0xFF) * 0.3f + (rgba[i + 1].toInt() and 0xFF) * 0.59f + (rgba[i + 2].toInt() and 0xFF) * 0.11f
            val li = ((y * w + x - 1) * 4)
            val lLeft = (rgba[li].toInt() and 0xFF) * 0.3f + (rgba[li + 1].toInt() and 0xFF) * 0.59f + (rgba[li + 2].toInt() and 0xFF) * 0.11f
            val edge = abs(lSelf - lLeft) / 255f * enhance
            val r = rgba[i].toInt() and 0xFF; val g = rgba[i + 1].toInt() and 0xFF; val b = rgba[i + 2].toInt() and 0xFF
            val gray = r * 0.3f + g * 0.59f + b * 0.11f
            val sat = 1f + stroke * 0.3f
            val nr = lerpF(gray, r.toFloat(), sat); val ng = lerpF(gray, g.toFloat(), sat); val nb = lerpF(gray, b.toFloat(), sat)
            val nz = (rng.nextFloat() - 0.5f) * stroke * 0.1f * 255f
            out[i] = lerpF(nr, clampF(nr + nz), edge * 0.5f).let { (clampF(it + edge * 30f)).toInt().toByte() }
            out[i + 1] = lerpF(ng, clampF(ng + nz), edge * 0.5f).let { (clampF(it + edge * 15f)).toInt().toByte() }
            out[i + 2] = lerpF(nb, clampF(nb + nz), edge * 0.5f).let { clampF(it).toInt().toByte() }
        }
    }

    private fun applyDramatic(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val contrast = p.params[0]; val bgSat = p.params[1]; val bgBright = p.params[2]
        for (i in 0 until w * h) {
            val idx = i * 4; val m = mask[i]
            val r = rgba[idx].toInt() and 0xFF; val g = rgba[idx + 1].toInt() and 0xFF; val b = rgba[idx + 2].toInt() and 0xFF
            val gray = r * 0.3f + g * 0.59f + b * 0.11f
            if (m > 0.5f) {
                out[idx] = clamp(((r / 255f - 0.5f) * contrast + 0.5f) * 255f).toByte()
                out[idx + 1] = clamp(((g / 255f - 0.5f) * contrast + 0.5f) * 255f).toByte()
                out[idx + 2] = clamp(((b / 255f - 0.5f) * contrast + 0.5f) * 255f).toByte()
            } else {
                val adj = lerpF(gray, gray + bgBright * 255f, 0.7f)
                out[idx] = lerpI(adj.toInt(), r, bgSat).toByte()
                out[idx + 1] = lerpI(adj.toInt(), g, bgSat).toByte()
                out[idx + 2] = lerpI(adj.toInt(), b, bgSat).toByte()
            }
            out[idx + 3] = -1
        }
    }

    private fun applyColorPop(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val personSat = p.params[0]; val bgSat = p.params[1]; val personBright = p.params[2]
        for (i in 0 until w * h) {
            val idx = i * 4; val m = mask[i]
            val r = rgba[idx].toInt() and 0xFF; val g = rgba[idx + 1].toInt() and 0xFF; val b = rgba[idx + 2].toInt() and 0xFF
            val gray = r * 0.3f + g * 0.59f + b * 0.11f
            if (m > 0.5f) {
                out[idx] = clamp(lerpF(gray.toFloat(), r.toFloat(), personSat) + personBright * 255f).toByte()
                out[idx + 1] = clamp(lerpF(gray.toFloat(), g.toFloat(), personSat) + personBright * 255f).toByte()
                out[idx + 2] = clamp(lerpF(gray.toFloat(), b.toFloat(), personSat) + personBright * 255f).toByte()
            } else {
                out[idx] = lerpI(gray.toInt(), r, bgSat).toByte()
                out[idx + 1] = lerpI(gray.toInt(), g, bgSat).toByte()
                out[idx + 2] = lerpI(gray.toInt(), b, bgSat).toByte()
            }
            out[idx + 3] = -1
        }
    }

    private fun applyDreamy(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val blurAmount = p.params[0]; val kr = (blurAmount * 2f).toInt()
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4; val m = mask[i / 4]
            if (m > 0.5f) {
                var r = 0f; var g = 0f; var b = 0f; var wsum = 0f
                for (ky in -kr..kr) for (kx in -kr..kr) {
                    val sx = (x + kx).coerceIn(0, w - 1); val sy = (y + ky).coerceIn(0, h - 1)
                    val gw = exp(-(kx * kx + ky * ky).toFloat() / (2f * blurAmount * blurAmount))
                    val si = (sy * w + sx) * 4
                    r += (rgba[si].toInt() and 0xFF) * gw; g += (rgba[si + 1].toInt() and 0xFF) * gw
                    b += (rgba[si + 2].toInt() and 0xFF) * gw; wsum += gw
                }
                out[i] = clamp(r / wsum * 1.1f).toByte()
                out[i + 1] = clamp(g / wsum * 0.95f).toByte()
                out[i + 2] = clamp(b / wsum * 0.85f).toByte()
            } else {
                out[i] = ((rgba[i].toInt() and 0xFF) * 9 / 10).toByte()
                out[i + 1] = ((rgba[i + 1].toInt() and 0xFF) * 9 / 10).toByte()
                out[i + 2] = clamp(((rgba[i + 2].toInt() and 0xFF).toFloat() * 1.1f)).toByte()
            }
            out[i + 3] = -1
        }
    }

    private fun applyGoldenHour(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams) {
        val warmth = p.params[0]
        for (i in 0 until w * h) {
            val idx = i * 4; val m = mask[i]
            if (m > 0.5f) {
                out[idx] = clamp((rgba[idx].toInt() and 0xFF) * (1f + warmth * 0.3f)).toByte()
                out[idx + 1] = clamp((rgba[idx + 1].toInt() and 0xFF) * (1f + warmth * 0.15f)).toByte()
                out[idx + 2] = clamp((rgba[idx + 2].toInt() and 0xFF) * (1f - warmth * 0.2f)).toByte()
            } else {
                out[idx] = clamp((rgba[idx].toInt() and 0xFF) * (1f - warmth * 0.15f)).toByte()
                out[idx + 1] = clamp((rgba[idx + 1].toInt() and 0xFF) * (1f - warmth * 0.05f)).toByte()
                out[idx + 2] = clamp((rgba[idx + 2].toInt() and 0xFF) * (1f + warmth * 0.2f)).toByte()
            }
            out[idx + 3] = -1
        }
    }

    private fun applyDoubleExposure(rgba: ByteArray, mask: FloatArray, out: ByteArray, w: Int, h: Int, p: EffectParams, rng: Random) {
        val blend = p.params[0]; val pattern = p.params[1].toInt()
        val overlay = ByteArray(w * h * 4)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y * w + x) * 4
            val t = when (pattern) {
                0 -> x.toFloat() / w
                1 -> if ((x / 40 + y / 40) % 2 == 0) 0f else 1f
                2 -> rng.nextFloat()
                3 -> {
                    val dx = x.toFloat() / w - 0.5f; val dy = y.toFloat() / h - 0.5f
                    min(1f, sqrt(dx * dx + dy * dy) * 1.4f)
                }
                else -> 0.5f
            }
            overlay[i] = lerpI(p.color1R, p.color2R, t).toByte()
            overlay[i + 1] = lerpI(p.color1G, p.color2G, t).toByte()
            overlay[i + 2] = lerpI(p.color1B, p.color2B, t).toByte()
            overlay[i + 3] = -1
        }
        for (i in 0 until w * h) {
            val idx = i * 4; val a = blend * 0.5f; val m = mask[i]
            val fa = a * (1f + m * 0.5f)
            out[idx] = lerpI(rgba[idx].toInt() and 0xFF, overlay[idx].toInt() and 0xFF, fa).toByte()
            out[idx + 1] = lerpI(rgba[idx + 1].toInt() and 0xFF, overlay[idx + 1].toInt() and 0xFF, fa).toByte()
            out[idx + 2] = lerpI(rgba[idx + 2].toInt() and 0xFF, overlay[idx + 2].toInt() and 0xFF, fa).toByte()
            out[idx + 3] = -1
        }
    }
}
