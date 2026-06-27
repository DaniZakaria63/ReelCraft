package id.my.daniza.reelcraft.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import id.my.daniza.reelcraft.data.DummyProjects
import id.my.daniza.reelcraft.model.AppliedEffect
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.MaskType
import id.my.daniza.reelcraft.model.MusicTrack
import id.my.daniza.reelcraft.model.Presets
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TextOverlay
import id.my.daniza.reelcraft.model.TimelineState
import java.util.UUID

enum class EditorTool {
    SELECT, TRIM, SPLIT, EFFECTS, TEXT, AUDIO, SPEED, TRANSITIONS
}

sealed class BottomSheetContent {
    data object Presets : BottomSheetContent()
    data class ClipProperties(val clipId: String) : BottomSheetContent()
    data class Effects(val clipId: String, val effectId: String? = null) : BottomSheetContent()
    data class TextEditor(val textOverlay: TextOverlay, val clipId: String) : BottomSheetContent()
    data object Music : BottomSheetContent()
}

class EditorViewModel : ViewModel() {

    var project by mutableStateOf<Project?>(null)
        private set

    var timelineState by mutableStateOf(TimelineState())
        private set

    var currentTool by mutableStateOf(EditorTool.SELECT)
        private set

    var bottomSheetContent by mutableStateOf<BottomSheetContent?>(null)
        private set

    var isBottomSheetExpanded by mutableStateOf(false)
        private set

    private var nextClipNumber = 100

    fun loadProject(projectId: String) {
        val found = DummyProjects.projectById(projectId)
        if (found != null) {
            project = found
            timelineState = timelineState.copy(
                durationUs = found.durationUs,
                currentPositionUs = 0L
            )
        }
    }

    fun seekTo(positionUs: Long) {
        val clamped = positionUs.coerceIn(0L, maxOf(timelineState.durationUs, 1L))
        timelineState = timelineState.copy(currentPositionUs = clamped)
    }

    fun togglePlayback() {
        timelineState = timelineState.copy(isPlaying = !timelineState.isPlaying)
    }

    fun selectTool(tool: EditorTool) {
        currentTool = tool
        when (tool) {
            EditorTool.EFFECTS -> {
                val clipId = timelineState.selectedClipId ?: project?.clips?.firstOrNull()?.id ?: return
                showEffectList(clipId)
            }
            EditorTool.TEXT -> {
                val clipId = timelineState.selectedClipId ?: project?.clips?.firstOrNull()?.id ?: return
                addTextOverlay(clipId)
            }
            EditorTool.AUDIO -> showMusicPicker()
            EditorTool.SELECT -> hideBottomSheet()
            else -> hideBottomSheet()
        }
    }

    fun selectClip(clipId: String?) {
        timelineState = timelineState.copy(selectedClipId = clipId)
        if (clipId != null) {
            showClipProperties(clipId)
        }
    }

    fun setZoom(zoom: Float) {
        timelineState = timelineState.copy(zoomLevel = zoom.coerceIn(0.1f, 10f))
    }

    fun addEffect(presetId: String) {
        val clipId = timelineState.selectedClipId ?: project?.clips?.firstOrNull()?.id ?: return
        val preset = Presets.byId(presetId) ?: return
        val effect = AppliedEffect(
            id = UUID.randomUUID().toString(),
            presetId = presetId,
            maskType = if (preset.tier >= 1) MaskType.Foreground else MaskType.WholeFrame,
            intensity = 1f
        )
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(effects = clip.effects + effect)
                else clip
            })
        }
        showEffectList(clipId)
    }

    fun removeEffect(effectId: String) {
        val clipId = timelineState.selectedClipId ?: return
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(effects = clip.effects.filter { it.id != effectId })
                else clip
            })
        }
        showEffectList(clipId)
    }

    fun updateEffectIntensity(effectId: String, intensity: Float) {
        val clipId = timelineState.selectedClipId ?: return
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(effects = clip.effects.map { e ->
                    if (e.id == effectId) e.copy(intensity = intensity) else e
                }) else clip
            })
        }
    }

    fun updateClipTrim(clipId: String, trimStartUs: Long, trimEndUs: Long) {
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(trimStartUs = trimStartUs, trimEndUs = trimEndUs)
                else clip
            })
        }
    }

    fun updateClipSpeed(clipId: String, speed: Float) {
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(speed = speed.coerceIn(0.1f, 4f))
                else clip
            })
        }
    }

    fun updateClipVolume(clipId: String, volume: Float) {
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(volume = volume.coerceIn(0f, 1f))
                else clip
            })
        }
    }

    fun splitClip(clipId: String, splitPositionUs: Long) {
        val clip = project?.clips?.find { it.id == clipId } ?: return
        val clipDuration = clip.trimEndUs - clip.trimStartUs
        if (clipDuration < 2_000_000L) return
        val relativeSplit = splitPositionUs - clip.trimStartUs
        if (relativeSplit <= 1_000_000 || relativeSplit >= clipDuration - 1_000_000) return
        val left = clip.copy(trimEndUs = splitPositionUs)
        val right = clip.copy(
            id = "clip_${nextClipNumber}",
            trimStartUs = splitPositionUs,
            orderIndex = clip.orderIndex + 1
        )
        nextClipNumber++
        project = project?.let { p ->
            val mutable = p.clips.toMutableList()
            val idx = mutable.indexOfFirst { it.id == clipId }
            if (idx != -1) {
                mutable[idx] = left
                mutable.add(idx + 1, right)
                mutable.forEachIndexed { i, c -> mutable[i] = c.copy(orderIndex = i) }
            }
            p.copy(clips = mutable)
        }
    }

    fun addTextOverlay(clipId: String) {
        val textOverlay = TextOverlay(
            id = UUID.randomUUID().toString(),
            text = "Double tap to edit",
            positionX = 0.5f,
            positionY = 0.5f
        )
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(textOverlays = clip.textOverlays + textOverlay)
                else clip
            })
        }
        showTextEditor(textOverlay, clipId)
    }

    fun updateTextOverlay(clipId: String, textOverlay: TextOverlay) {
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(textOverlays = clip.textOverlays.map { t ->
                    if (t.id == textOverlay.id) textOverlay else t
                }) else clip
            })
        }
    }

    fun removeTextOverlay(clipId: String, overlayId: String) {
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(textOverlays = clip.textOverlays.filter { it.id != overlayId })
                else clip
            })
        }
    }

    fun setMusicTrack(track: MusicTrack?) {
        project = project?.copy(musicTrack = track)
    }

    fun reorderClips(fromIndex: Int, toIndex: Int) {
        project = project?.let { p ->
            val mutable = p.clips.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable.forEachIndexed { i, c -> mutable[i] = c.copy(orderIndex = i) }
            p.copy(clips = mutable)
        }
    }

    fun showPresets() {
        bottomSheetContent = BottomSheetContent.Presets
        isBottomSheetExpanded = true
    }

    fun showClipProperties(clipId: String) {
        bottomSheetContent = BottomSheetContent.ClipProperties(clipId)
        isBottomSheetExpanded = true
    }

    fun showEffectList(clipId: String) {
        bottomSheetContent = BottomSheetContent.Effects(clipId)
        isBottomSheetExpanded = true
    }

    fun showEffectDetail(clipId: String, effectId: String) {
        bottomSheetContent = BottomSheetContent.Effects(clipId, effectId)
        isBottomSheetExpanded = true
    }

    fun showTextEditor(textOverlay: TextOverlay, clipId: String) {
        bottomSheetContent = BottomSheetContent.TextEditor(textOverlay, clipId)
        isBottomSheetExpanded = true
    }

    fun showMusicPicker() {
        bottomSheetContent = BottomSheetContent.Music
        isBottomSheetExpanded = true
    }

    fun hideBottomSheet() {
        bottomSheetContent = null
        isBottomSheetExpanded = false
    }
}
