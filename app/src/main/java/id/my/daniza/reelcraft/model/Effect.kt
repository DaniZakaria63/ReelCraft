package id.my.daniza.reelcraft.model

enum class Interpolation {
    Linear, EaseIn, EaseOut, EaseInOut
}

data class Keyframe(
    val positionUs: Long,
    val intensity: Float,
    val interpolation: Interpolation = Interpolation.Linear
)

enum class MaskType(val label: String) {
    WholeFrame("Full Frame"),
    Foreground("Person Only"),
    Background("Background Only")
}

data class AppliedEffect(
    val id: String,
    val presetId: String,
    val maskType: MaskType = MaskType.WholeFrame,
    val intensity: Float = 1f,
    val enabled: Boolean = true,
    val keyframes: List<Keyframe> = emptyList(),
    val params: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AppliedEffect) return false
        return id == other.id &&
                presetId == other.presetId &&
                maskType == other.maskType &&
                intensity == other.intensity &&
                enabled == other.enabled &&
                keyframes == other.keyframes &&
                params.contentEquals(other.params)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + presetId.hashCode()
        result = 31 * result + maskType.hashCode()
        result = 31 * result + intensity.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + keyframes.hashCode()
        result = 31 * result + (params?.contentHashCode() ?: 0)
        return result
    }
}
