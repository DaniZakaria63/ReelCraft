package id.my.daniza.reelcraft.model

data class Clip(
    val id: String,
    val sourcePath: String,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = 0L,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val effects: List<AppliedEffect> = emptyList(),
    val textOverlays: List<TextOverlay> = emptyList(),
    val orderIndex: Int = 0
) {
    val durationUs: Long get() = trimEndUs - trimStartUs
}

data class TextOverlay(
    val id: String,
    val text: String,
    val fontName: String = "Default",
    val fontSize: Int = 36,
    val colorArgb: Long = 0xFFFFFFFF,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val rotationDeg: Float = 0f,
    val startOffsetUs: Long = 0L,
    val endOffsetUs: Long = 0L
)

data class MusicTrack(
    val sourcePath: String,
    val name: String = "Background Music",
    val volume: Float = 0.5f,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = 0L
)
