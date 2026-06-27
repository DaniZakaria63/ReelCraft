package id.my.daniza.reelcraft.model

data class AppliedEffect(
    val id: String,
    val presetId: String,
    val maskType: MaskType = MaskType.WholeFrame,
    val intensity: Float = 1f,
    val enabled: Boolean = true,
    val keyframes: List<Keyframe> = emptyList()
)

enum class MaskType(val label: String) {
    WholeFrame("Full Frame"),
    Foreground("Person Only"),
    Background("Background Only")
}

data class Keyframe(
    val positionUs: Long,
    val intensity: Float,
    val interpolation: Interpolation = Interpolation.Linear
)

enum class Interpolation {
    Linear, EaseIn, EaseOut, EaseInOut
}
