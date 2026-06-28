package id.my.daniza.reelcraft.engine

import id.my.daniza.reelcraft.model.AppliedEffect
import id.my.daniza.reelcraft.model.Interpolation
import id.my.daniza.reelcraft.model.MaskType
import id.my.daniza.reelcraft.model.Presets

object PresetEngine {

    fun buildFilterString(
        effects: List<AppliedEffect>,
        intensity: Float = 1f
    ): String? {
        val active = effects.filter { it.enabled }
        if (active.isEmpty()) return null

        val parts = mutableListOf<String>()

        for (effect in active) {
            val preset = Presets.byId(effect.presetId) ?: continue
            if (preset.tier > 0 || preset.filterDesc.isBlank()) continue

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
