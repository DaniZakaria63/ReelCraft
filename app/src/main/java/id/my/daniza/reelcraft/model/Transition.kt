package id.my.daniza.reelcraft.model

data class Transition(
    val type: TransitionType = TransitionType.None,
    val durationUs: Long = 500_000L
)

enum class TransitionType(val label: String) {
    None("None"),
    Crossfade("Crossfade"),
    WipeLeft("Wipe Left"),
    WipeRight("Wipe Right"),
    WipeUp("Wipe Up"),
    WipeDown("Wipe Down")
}
