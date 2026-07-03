package id.my.daniza.reelcraft.data.json

import id.my.daniza.reelcraft.model.AppliedEffect
import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.Interpolation
import id.my.daniza.reelcraft.model.Keyframe
import id.my.daniza.reelcraft.model.MaskType
import id.my.daniza.reelcraft.model.MusicTrack
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TextOverlay
import id.my.daniza.reelcraft.model.TimelineState
import id.my.daniza.reelcraft.model.Transition
import id.my.daniza.reelcraft.model.TransitionType
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectJsonSerializer @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    fun serialize(project: Project, timelineState: TimelineState?): String {
        val dto = project.toDto(timelineState)
        return json.encodeToString(ProjectJsonDto.serializer(), dto)
    }

    fun deserialize(jsonString: String): DeserializeResult {
        val dto = json.decodeFromString(ProjectJsonDto.serializer(), jsonString)
        return DeserializeResult(
            project = dto.toProject(),
            timelineState = dto.timelineState?.toTimelineState()
        )
    }

    data class DeserializeResult(
        val project: Project,
        val timelineState: TimelineState?
    )

    private fun Project.toDto(timelineState: TimelineState?) = ProjectJsonDto(
        schemaVersion = 1,
        id = id,
        name = name,
        thumbnailPath = thumbnailPath,
        durationUs = durationUs,
        dateCreatedMs = dateCreatedMs,
        dateModifiedMs = dateModifiedMs,
        aspectRatio = aspectRatio.name,
        volume = volume,
        clips = clips.map { it.toDto() },
        musicTrack = musicTrack?.toDto(),
        timelineState = timelineState?.toDto()
    )

    private fun Clip.toDto() = ClipDto(
        id = id,
        sourcePath = sourcePath,
        trimStartUs = trimStartUs,
        trimEndUs = trimEndUs,
        speed = speed,
        volume = volume,
        orderIndex = orderIndex,
        effects = effects.map { it.toDto() },
        textOverlays = textOverlays.map { it.toDto() },
        transitionOut = transitionOut?.toDto()
    )

    private fun AppliedEffect.toDto() = AppliedEffectDto(
        id = id,
        presetId = presetId,
        maskType = maskType.name,
        intensity = intensity,
        enabled = enabled,
        keyframes = keyframes.map { it.toDto() },
        params = params?.toList()
    )

    private fun Keyframe.toDto() = KeyframeDto(
        positionUs = positionUs,
        intensity = intensity,
        interpolation = interpolation.name
    )

    private fun TextOverlay.toDto() = TextOverlayDto(
        id = id,
        text = text,
        fontName = fontName,
        fontSize = fontSize,
        colorArgb = colorArgb,
        positionX = positionX,
        positionY = positionY,
        rotationDeg = rotationDeg,
        startOffsetUs = startOffsetUs,
        endOffsetUs = endOffsetUs
    )

    private fun Transition.toDto() = TransitionDto(
        type = type.name,
        durationUs = durationUs
    )

    private fun MusicTrack.toDto() = MusicTrackDto(
        sourcePath = sourcePath,
        name = name,
        volume = volume,
        trimStartUs = trimStartUs,
        trimEndUs = trimEndUs
    )

    private fun TimelineState.toDto() = TimelineStateDto(
        currentPositionUs = currentPositionUs,
        isPlaying = isPlaying,
        durationUs = durationUs,
        zoomLevel = zoomLevel,
        selectedClipId = selectedClipId,
        selectedEffectId = selectedEffectId
    )

    private fun ProjectJsonDto.toProject() = Project(
        id = id,
        name = name,
        thumbnailPath = thumbnailPath,
        durationUs = durationUs,
        dateCreatedMs = dateCreatedMs,
        dateModifiedMs = dateModifiedMs,
        clips = clips.map { it.toClip() },
        musicTrack = musicTrack?.toMusicTrack(),
        aspectRatio = try { AspectRatio.valueOf(aspectRatio) } catch (_: Exception) { AspectRatio.SixteenNine },
        volume = volume
    )

    private fun ClipDto.toClip() = Clip(
        id = id,
        sourcePath = sourcePath,
        trimStartUs = trimStartUs,
        trimEndUs = trimEndUs,
        speed = speed,
        volume = volume,
        orderIndex = orderIndex,
        effects = effects.map { it.toEffect() },
        textOverlays = textOverlays.map { it.toTextOverlay() },
        transitionOut = transitionOut?.toTransition()
    )

    private fun AppliedEffectDto.toEffect() = AppliedEffect(
        id = id,
        presetId = presetId,
        maskType = try { MaskType.valueOf(maskType) } catch (_: Exception) { MaskType.WholeFrame },
        intensity = intensity,
        enabled = enabled,
        keyframes = keyframes.map { it.toKeyframe() },
        params = params?.toFloatArray()
    )

    private fun KeyframeDto.toKeyframe() = Keyframe(
        positionUs = positionUs,
        intensity = intensity,
        interpolation = try { Interpolation.valueOf(interpolation) } catch (_: Exception) { Interpolation.Linear }
    )

    private fun TextOverlayDto.toTextOverlay() = TextOverlay(
        id = id,
        text = text,
        fontName = fontName,
        fontSize = fontSize,
        colorArgb = colorArgb,
        positionX = positionX,
        positionY = positionY,
        rotationDeg = rotationDeg,
        startOffsetUs = startOffsetUs,
        endOffsetUs = endOffsetUs
    )

    private fun TransitionDto.toTransition() = Transition(
        type = try { TransitionType.valueOf(type) } catch (_: Exception) { TransitionType.None },
        durationUs = durationUs
    )

    private fun MusicTrackDto.toMusicTrack() = MusicTrack(
        sourcePath = sourcePath,
        name = name,
        volume = volume,
        trimStartUs = trimStartUs,
        trimEndUs = trimEndUs
    )

    private fun TimelineStateDto.toTimelineState() = TimelineState(
        currentPositionUs = currentPositionUs,
        isPlaying = isPlaying,
        durationUs = durationUs,
        zoomLevel = zoomLevel,
        selectedClipId = selectedClipId,
        selectedEffectId = selectedEffectId
    )
}
