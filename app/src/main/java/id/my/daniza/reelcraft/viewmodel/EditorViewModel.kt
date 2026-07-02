package id.my.daniza.reelcraft.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.ffmpeg.NativeFFmpeg
import id.my.daniza.reelcraft.data.DummyProjects
import id.my.daniza.reelcraft.engine.PresetEngine
import id.my.daniza.reelcraft.model.AppliedEffect
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.MaskType
import id.my.daniza.reelcraft.model.MusicTrack
import id.my.daniza.reelcraft.model.Presets
import id.my.daniza.reelcraft.model.Project
import id.my.daniza.reelcraft.model.TextOverlay
import id.my.daniza.reelcraft.model.TimelineState
import id.my.daniza.reelcraft.model.Transition
import id.my.daniza.reelcraft.model.TransitionType
import id.my.daniza.segment.SegmentEngine
import java.util.UUID
import kotlin.collections.plus
import kotlin.random.Random
import javax.inject.Inject

private const val MAX_UNDO = 50

enum class EditorTool {
    SELECT, TRIM, SPLIT, EFFECTS, TEXT, AUDIO, SPEED, TRANSITIONS
}

sealed class BottomSheetContent {
    data object Presets : BottomSheetContent()
    data class ClipProperties(val clipId: String) : BottomSheetContent()
    data class Effects(val clipId: String, val effectId: String? = null) : BottomSheetContent()
    data class TextEditor(val textOverlay: TextOverlay, val clipId: String) : BottomSheetContent()
    data object Music : BottomSheetContent()
    data class Transitions(val clipId: String) : BottomSheetContent()
}

@HiltViewModel
class EditorViewModel @Inject constructor(
    val segmentEngine: SegmentEngine
) : ViewModel() {

    val segmentReady: Boolean get() = segmentEngine.segmentReady

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

    var ffmpegReady by mutableStateOf(false)
        private set

    var initError by mutableStateOf<String?>(null)
        private set

    var canUndo by mutableStateOf(false)
        private set

    var canRedo by mutableStateOf(false)
        private set

    var previewBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    private var decoderHandle: Long = 0
    private var frameWidth = 0
    private var frameHeight = 0
    private var frameBuffer: java.nio.ByteBuffer? = null

    private var nextClipNumber = 100

    private val undoStack = mutableListOf<Project>()
    private val redoStack = mutableListOf<Project>()

    init {
        verifyFFmpeg()
    }

    // ─── Decoder / Preview ────────────────────────────────────────────────

    fun openDecoderForPath(path: String): Boolean {
        closeDecoder()
        val handle = NativeFFmpeg.decoderOpen(path)
        if (handle == 0L) return false
        decoderHandle = handle
        frameWidth = NativeFFmpeg.decoderWidth(handle)
        frameHeight = NativeFFmpeg.decoderHeight(handle)
        val size = NativeFFmpeg.decoderFrameSize(handle)
        if (size > 0) {
            frameBuffer = java.nio.ByteBuffer.allocateDirect(size)
        }
        return true
    }

    fun closeDecoder() {
        if (decoderHandle != 0L) {
            NativeFFmpeg.decoderClose(decoderHandle)
            decoderHandle = 0
        }
        frameBuffer = null
    }

    fun decodeFrameAt(positionUs: Long): Bitmap? {
        val handle = decoderHandle
        val buf = frameBuffer ?: return null
        if (handle == 0L) return null
        if (frameWidth <= 0 || frameHeight <= 0) return null

        NativeFFmpeg.decoderSeek(handle, positionUs)
        buf.rewind()
        if (!NativeFFmpeg.decoderReadFrame(handle, buf)) return null
        buf.rewind()

        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buf)
        return bitmap
    }

    fun updatePreviewAt(positionUs: Long) {
        val bitmap = decodeFrameAt(positionUs)
        previewBitmap = bitmap?.asImageBitmap()
    }

    // ─── Undo / Redo ──────────────────────────────────────────────────────

    private fun verifyFFmpeg() {
        try {
            val ffOk = NativeFFmpeg.verifyFFmpeg()
            ffmpegReady = ffOk
            Log.i("EditorViewModel", "FFmpeg verified: $ffOk, segment=$segmentReady")
            if (!ffOk) initError = "FFmpeg library loaded but filter graph test failed"
        } catch (e: UnsatisfiedLinkError) {
            ffmpegReady = false
            val msg = "FFmpeg native library not found: ${e.message}"
            Log.e("EditorViewModel", msg)
            initError = msg
        } catch (e: Exception) {
            ffmpegReady = false
            val msg = "FFmpeg verification failed: ${e.message}"
            Log.e("EditorViewModel", msg, e)
            initError = msg
        }
    }

    override fun onCleared() {
        closeDecoder()
        super.onCleared()
    }

    private fun saveState() {
        val p = project ?: return
        if (undoStack.size >= MAX_UNDO) undoStack.removeAt(0)
        undoStack.add(p.copy(clips = p.clips.map { it.copy() }))
        redoStack.clear()
        canUndo = true
        canRedo = false
    }

    fun undo() {
        val p = project ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.add(p.copy(clips = p.clips.map { it.copy() }))
        project = prev
        canUndo = undoStack.isNotEmpty()
        canRedo = true
    }

    fun redo() {
        val p = project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(p.copy(clips = p.clips.map { it.copy() }))
        project = next
        canUndo = true
        canRedo = redoStack.isNotEmpty()
    }

    fun loadProject(projectId: String) {
        val found = DummyProjects.projectById(projectId)
        if (found != null) {
            project = found
            val firstClip = found.clips.firstOrNull()
            if (firstClip != null && firstClip.sourcePath.startsWith("/")) {
                val ok = openDecoderForPath(firstClip.sourcePath)
                Log.i("EditorViewModel", "Decoder opened for ${firstClip.sourcePath}: $ok")
            }
            timelineState = timelineState.copy(
                durationUs = found.effectiveDurationUs,
                currentPositionUs = 0L
            )
            updatePreviewAt(0L)
        }
    }

    fun seekTo(positionUs: Long) {
        val clamped = positionUs.coerceIn(0L, maxOf(timelineState.durationUs, 1L))
        timelineState = timelineState.copy(currentPositionUs = clamped)
        updatePreviewAt(clamped)
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
            EditorTool.TRANSITIONS -> {
                val clipId = timelineState.selectedClipId ?: project?.clips?.firstOrNull()?.id ?: return
                showTransitions(clipId)
            }
            EditorTool.SELECT -> hideBottomSheet()
            else -> hideBottomSheet()
        }
    }

    fun buildFilterStringForClip(clipId: String, positionUs: Long? = null): String? {
        val clip = project?.clips?.find { it.id == clipId } ?: return null
        val effects = clip.effects.map { e ->
            if (positionUs != null && e.keyframes.isNotEmpty()) {
                val kfIntensity = PresetEngine.intensityOverTime(e, positionUs)
                e.copy(intensity = kfIntensity)
            } else e
        }
        return PresetEngine.buildFilterString(effects)
    }

    fun buildFilterStringForCurrentClip(): String? {
        val clipId = timelineState.selectedClipId ?: project?.clips?.firstOrNull()?.id ?: return null
        return buildFilterStringForClip(clipId, timelineState.currentPositionUs)
    }

    fun selectClip(clipId: String?) {
        timelineState = timelineState.copy(selectedClipId = clipId)
        if (clipId != null) showClipProperties(clipId)
    }

    fun setZoom(zoom: Float) {
        timelineState = timelineState.copy(zoomLevel = zoom.coerceIn(0.1f, 10f))
    }

    fun addEffect(presetId: String) {
        saveState()
        val clipId = timelineState.selectedClipId ?: project?.clips?.firstOrNull()?.id ?: return
        val preset = Presets.byId(presetId) ?: return

        val params = if (PresetEngine.isSegmentEffect(presetId)) {
            FloatArray(8) { Random.nextFloat() }
        } else null

        val effect = AppliedEffect(
            id = UUID.randomUUID().toString(),
            presetId = presetId,
            maskType = if (preset.tier >= 1) MaskType.Foreground else MaskType.WholeFrame,
            intensity = 1f,
            params = params
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
        saveState()
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
        saveState()
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(trimStartUs = trimStartUs, trimEndUs = trimEndUs)
                else clip
            })
        }
        syncDuration()
    }

    fun updateClipSpeed(clipId: String, speed: Float) {
        saveState()
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(speed = speed.coerceIn(0.1f, 4f))
                else clip
            })
        }
        syncDuration()
    }

    fun updateClipVolume(clipId: String, volume: Float) {
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(volume = volume.coerceIn(0f, 1f))
                else clip
            })
        }
    }

    fun updateClipTransition(clipId: String, transition: Transition) {
        saveState()
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(transitionOut = transition)
                else clip
            })
        }
        syncDuration()
    }

    fun splitClip(clipId: String, splitPositionUs: Long) {
        saveState()
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
        syncDuration()
    }

    fun addTextOverlay(clipId: String) {
        saveState()
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
        saveState()
        project = project?.let { p ->
            p.copy(clips = p.clips.map { clip ->
                if (clip.id == clipId) clip.copy(textOverlays = clip.textOverlays.map { t ->
                    if (t.id == textOverlay.id) textOverlay else t
                }) else clip
            })
        }
    }

    fun removeTextOverlay(clipId: String, overlayId: String) {
        saveState()
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
        saveState()
        project = project?.let { p ->
            val mutable = p.clips.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable.forEachIndexed { i, c -> mutable[i] = c.copy(orderIndex = i) }
            p.copy(clips = mutable)
        }
    }

    fun getActiveTransition(clipId: String): Transition? {
        val idx = project?.clips?.indexOfFirst { it.id == clipId } ?: return null
        if (idx < 0 || idx >= (project?.clips?.size ?: 0) - 1) return null
        return project?.clips?.get(idx)?.transitionOut
    }

    private fun syncDuration() {
        val effective = project?.effectiveDurationUs ?: return
        timelineState = timelineState.copy(durationUs = effective)
    }

    // ─── Bottom sheet navigation ────────────────────────────────────────

    fun showPresets() { bottomSheetContent = BottomSheetContent.Presets; isBottomSheetExpanded = true }
    fun showClipProperties(clipId: String) { bottomSheetContent = BottomSheetContent.ClipProperties(clipId); isBottomSheetExpanded = true }
    fun showEffectList(clipId: String) { bottomSheetContent = BottomSheetContent.Effects(clipId); isBottomSheetExpanded = true }
    fun showEffectDetail(clipId: String, effectId: String) { bottomSheetContent = BottomSheetContent.Effects(clipId, effectId); isBottomSheetExpanded = true }
    fun showTextEditor(textOverlay: TextOverlay, clipId: String) { bottomSheetContent = BottomSheetContent.TextEditor(textOverlay, clipId); isBottomSheetExpanded = true }
    fun showMusicPicker() { bottomSheetContent = BottomSheetContent.Music; isBottomSheetExpanded = true }
    fun showTransitions(clipId: String) { bottomSheetContent = BottomSheetContent.Transitions(clipId); isBottomSheetExpanded = true }
    fun hideBottomSheet() { bottomSheetContent = null; isBottomSheetExpanded = false }
}