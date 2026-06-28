package id.my.daniza.reelcraft.engine

import id.my.daniza.reelcraft.model.AppliedEffect
import id.my.daniza.reelcraft.model.Interpolation
import id.my.daniza.reelcraft.model.MaskType
import id.my.daniza.reelcraft.model.Presets

object PresetEngine {

    // Segment effect type constants (must match SegmentEffect enum in effects.h)
    const val EFFECT_BACKGROUND_REPLACE = 0
    const val EFFECT_INK_SPLASH = 1
    const val EFFECT_CYBERPUNK_GRID = 2
    const val EFFECT_GLITCH_BACKGROUND = 3
    const val EFFECT_PIXELATE_BACKGROUND = 4
    const val EFFECT_GLOW_SILHOUETTE = 5
    const val EFFECT_NEON_OUTLINE = 6
    const val EFFECT_VAN_GOGH = 7
    const val EFFECT_DRAMATIC = 8
    const val EFFECT_COLOR_POP = 9
    const val EFFECT_DREAMY = 10
    const val EFFECT_GOLDEN_HOUR = 11
    const val EFFECT_DOUBLE_EXPOSURE = 12

    private val segmentEffectIds = mapOf(
        "bg_replace" to EFFECT_BACKGROUND_REPLACE,
        "ink_splash" to EFFECT_INK_SPLASH,
        "cyberpunk_grid" to EFFECT_CYBERPUNK_GRID,
        "glitch_bg" to EFFECT_GLITCH_BACKGROUND,
        "pixelate_bg" to EFFECT_PIXELATE_BACKGROUND,
        "glow_silhouette" to EFFECT_GLOW_SILHOUETTE,
        "neon_outline" to EFFECT_NEON_OUTLINE,
        "van_gogh" to EFFECT_VAN_GOGH,
        "dramatic" to EFFECT_DRAMATIC,
        "color_pop" to EFFECT_COLOR_POP,
        "dreamy" to EFFECT_DREAMY,
        "golden_hour" to EFFECT_GOLDEN_HOUR,
        "double_exposure" to EFFECT_DOUBLE_EXPOSURE,
    )

    fun isSegmentEffect(presetId: String): Boolean {
        return presetId in segmentEffectIds
    }

    fun getSegmentEffectType(presetId: String): Int {
        return segmentEffectIds[presetId] ?: -1
    }

    fun buildFilterString(
        effects: List<AppliedEffect>,
        intensity: Float = 1f
    ): String? {
        val active = effects.filter { it.enabled }
        if (active.isEmpty()) return null

        val parts = mutableListOf<String>()

        for (effect in active) {
            val preset = Presets.byId(effect.presetId) ?: continue
            if (preset.tier > 0) continue // segment/neural effects handled separately

            val scaled = effect.intensity
            val desc = if (scaled < 0.99f && containsBlendableParams(preset.filterDesc)) {
                scaleFilter(preset.filterDesc, scaled)
            } else {
                preset.filterDesc
            }

            parts.add(desc)
        }

        if (parts.isEmpty()) return null
        return parts.joinToString(",")
    }

    private fun containsBlendableParams(desc: String): Boolean {
        return desc.contains("eq=") || desc.contains("colorbalance") || desc.contains("saturation")
    }

    private fun scaleFilter(desc: String, factor: Float): String {
        val f = factor.coerceIn(0f, 1f)
        if (f >= 0.99f) return desc

        var result = desc

        val contrastPattern = Regex("contrast=([0-9.]+)")
        result = result.replace(contrastPattern) { match ->
            val base = match.groupValues[1].toFloat()
            val scaled = 1.0f + (base - 1.0f) * f
            "contrast=${"%.2f".format(scaled)}"
        }

        val saturationPattern = Regex("saturation=([0-9.]+)")
        result = result.replace(saturationPattern) { match ->
            val base = match.groupValues[1].toFloat()
            val scaled = 1.0f + (base - 1.0f) * f
            "saturation=${"%.2f".format(scaled)}"
        }

        val brightnessPattern = Regex("brightness=([-0-9.]+)")
        result = result.replace(brightnessPattern) { match ->
            val base = match.groupValues[1].toFloat()
            val scaled = base * f
            "brightness=${"%.3f".format(scaled)}"
        }

        return result
    }

    fun intensityOverTime(
        effect: AppliedEffect,
        positionUs: Long
    ): Float {
        if (effect.keyframes.isEmpty()) return effect.intensity

        val kfs = effect.keyframes.sortedBy { it.positionUs }

        if (positionUs <= kfs.first().positionUs) return kfs.first().intensity
        if (positionUs >= kfs.last().positionUs) return kfs.last().intensity

        for (i in 0 until kfs.size - 1) {
            val a = kfs[i]
            val b = kfs[i + 1]
            if (positionUs in a.positionUs..b.positionUs) {
                val t = if (b.positionUs > a.positionUs) {
                    (positionUs - a.positionUs).toFloat() / (b.positionUs - a.positionUs).toFloat()
                } else 0f
                val interpolated = when (a.interpolation) {
                    Interpolation.Linear -> a.intensity + (b.intensity - a.intensity) * t
                    Interpolation.EaseIn -> a.intensity + (b.intensity - a.intensity) * (t * t)
                    Interpolation.EaseOut -> a.intensity + (b.intensity - a.intensity) * (t * (2f - t))
                    Interpolation.EaseInOut -> {
                        val t2 = if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t
                        a.intensity + (b.intensity - a.intensity) * t2
                    }
                }
                return interpolated.coerceIn(0f, 1f)
            }
        }

        return effect.intensity
    }
}
