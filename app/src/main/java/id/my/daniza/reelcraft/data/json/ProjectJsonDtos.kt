package id.my.daniza.reelcraft.data.json

import kotlinx.serialization.Serializable

@Serializable
data class ProjectJsonDto(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val thumbnailPath: String? = null,
    val durationUs: Long = 0L,
    val dateCreatedMs: Long,
    val dateModifiedMs: Long,
    val aspectRatio: String,
    val volume: Float = 1f,
    val clips: List<ClipDto> = emptyList(),
    val musicTrack: MusicTrackDto? = null,
    val timelineState: TimelineStateDto? = null
)

@Serializable
data class ClipDto(
    val id: String,
    val sourcePath: String,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = 0L,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val orderIndex: Int = 0,
    val effects: List<AppliedEffectDto> = emptyList(),
    val textOverlays: List<TextOverlayDto> = emptyList(),
    val transitionOut: TransitionDto? = null
)

@Serializable
data class AppliedEffectDto(
    val id: String,
    val presetId: String,
    val maskType: String,
    val intensity: Float = 1f,
    val enabled: Boolean = true,
    val keyframes: List<KeyframeDto> = emptyList(),
    val params: List<Float>? = null
)

@Serializable
data class KeyframeDto(
    val positionUs: Long,
    val intensity: Float,
    val interpolation: String
)

@Serializable
data class TextOverlayDto(
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

@Serializable
data class TransitionDto(
    val type: String,
    val durationUs: Long = 500_000L
)

@Serializable
data class MusicTrackDto(
    val sourcePath: String,
    val name: String = "Background Music",
    val volume: Float = 0.5f,
    val trimStartUs: Long = 0L,
    val trimEndUs: Long = 0L
)

@Serializable
data class TimelineStateDto(
    val currentPositionUs: Long = 0L,
    val isPlaying: Boolean = false,
    val durationUs: Long = 0L,
    val zoomLevel: Float = 1f,
    val selectedClipId: String? = null,
    val selectedEffectId: String? = null
)
