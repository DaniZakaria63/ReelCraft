package id.my.daniza.reelcraft.model

data class TimelineState(
    val currentPositionUs: Long = 0L,
    val isPlaying: Boolean = false,
    val durationUs: Long = 0L,
    val zoomLevel: Float = 1f,
    val selectedClipId: String? = null,
    val selectedEffectId: String? = null
)
