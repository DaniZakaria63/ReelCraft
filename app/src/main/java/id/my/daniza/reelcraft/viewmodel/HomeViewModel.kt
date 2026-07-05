package id.my.daniza.reelcraft.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.ffmpeg.NativeFFmpeg
import id.my.daniza.local.db.entity.ProjectEntity
import id.my.daniza.reelcraft.data.repository.ProjectRepository
import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProjectRepository
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var validationError by mutableStateOf<ValidationError?>(null)
        private set

    sealed class ValidationError {
        data class Broken(val projectId: String, val projectName: String, val reasons: List<String>) : ValidationError()
    }

    fun clearValidationError() {
        validationError = null
    }

    fun openProject(projectId: String, onValid: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repository.loadFullProject(projectId) }
            if (result == null) {
                val entity = withContext(Dispatchers.IO) { repository.getProjectEntity(projectId) }
                val name = entity?.name ?: "Unknown"
                validationError = ValidationError.Broken(
                    projectId, name,
                    listOf("Project data is missing from the database")
                )
                return@launch
            }

            val error = withContext(Dispatchers.IO) { validateProjectAssets(projectId, result.project) }
            if (error != null) {
                validationError = error
            } else {
                onValid(projectId)
            }
        }
    }

    private fun validateProjectAssets(projectId: String, project: Project): ValidationError? {
        val reasons = mutableListOf<String>()

        val jsonFile = repository.projectJsonFile(projectId)
        if (!jsonFile.exists()) {
            reasons.add("Project configuration file is missing")
        }

        for (clip in project.clips) {
            if (!File(clip.sourcePath).exists()) {
                reasons.add("Source video was deleted or moved: ${File(clip.sourcePath).name}")
            }
        }

        if (project.thumbnailPath != null && !File(project.thumbnailPath).exists()) {
            reasons.add("Thumbnail image was deleted")
        }

        return if (reasons.isEmpty()) null
        else ValidationError.Broken(projectId, project.name, reasons)
    }

    fun createProjectFromVideo(context: Context, videoUri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val tempDir = File(context.cacheDir, "imported_videos")
                tempDir.mkdirs()
                val tempFile = File(tempDir, "video_${System.currentTimeMillis()}.mp4")

                context.contentResolver.openInputStream(videoUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val thumbResult = withContext(Dispatchers.IO) {
                    extractThumbnailWithDuration(tempFile.absolutePath, context)
                }

                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val projectName = dateFormat.format(Date(now))

                val projectId = UUID.randomUUID().toString()
                val fullProject = Project(
                    id = projectId,
                    name = projectName,
                    thumbnailPath = thumbResult?.thumbnailPath,
                    durationUs = thumbResult?.durationUs ?: 0L,
                    clips = listOf(
                        Clip(
                            id = "clip_${projectId.takeLast(8)}",
                            sourcePath = tempFile.absolutePath,
                            orderIndex = 0
                        )
                    ),
                    aspectRatio = AspectRatio.SixteenNine
                )
                repository.saveFullProject(fullProject)
                onComplete(projectId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private data class ThumbResult(
        val thumbnailPath: String?,
        val durationUs: Long,
    )

    private fun extractThumbnailWithDuration(videoPath: String, context: Context): ThumbResult? {
        val handle = NativeFFmpeg.decoderOpen(videoPath)
        if (handle == 0L) return null
        return try {
            val w = NativeFFmpeg.decoderWidth(handle)
            val h = NativeFFmpeg.decoderHeight(handle)
            val durationUs = NativeFFmpeg.decoderDurationUs(handle)
            val frameSize = NativeFFmpeg.decoderFrameSize(handle)
            if (w <= 0 || h <= 0 || durationUs <= 0 || frameSize <= 0) {
                return ThumbResult(null, durationUs.coerceAtLeast(0))
            }

            val buf = ByteBuffer.allocateDirect(frameSize)
            val bitmap = findNonBlankFrame(handle, buf, w, h, durationUs, frameSize)
            val thumbPath = if (bitmap != null) {
                val thumbDir = File(context.cacheDir, "thumbnails")
                thumbDir.mkdirs()
                val thumbFile = File(thumbDir, "thumb_${System.currentTimeMillis()}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bitmap.recycle()
                thumbFile.absolutePath
            } else {
                null
            }
            ThumbResult(thumbPath, durationUs)
        } finally {
            NativeFFmpeg.decoderClose(handle)
        }
    }

    private fun findNonBlankFrame(
        handle: Long,
        buf: ByteBuffer,
        w: Int,
        h: Int,
        durationUs: Long,
        frameSize: Int,
    ): Bitmap? {
        val stepUs = durationUs / 20
        val positions = listOf(
            durationUs / 3,
            durationUs / 2,
            durationUs * 2 / 3,
            durationUs / 4,
            durationUs * 3 / 4,
            durationUs / 10,
        )

        for (pos in positions) {
            NativeFFmpeg.decoderSeek(handle, pos)
            buf.rewind()
            if (!NativeFFmpeg.decoderReadFrame(handle, buf)) continue
            buf.rewind()
            if (isFrameBlank(buf, frameSize)) continue

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buf)
            return bitmap
        }

        // fallback: scan with step
        var pos = 0L
        while (pos < durationUs) {
            NativeFFmpeg.decoderSeek(handle, pos)
            buf.rewind()
            if (!NativeFFmpeg.decoderReadFrame(handle, buf)) {
                pos += stepUs
                continue
            }
            buf.rewind()
            if (!isFrameBlank(buf, frameSize)) {
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buf)
                return bitmap
            }
            pos += stepUs
        }
        return null
    }

    private fun isFrameBlank(buf: ByteBuffer, frameSize: Int): Boolean {
        val sampleSize = minOf(frameSize / 4, 4096)
        val step = frameSize / sampleSize
        var sum = 0L
        var count = 0
        for (i in 0 until sampleSize) {
            val b = buf[i * step].toInt() and 0xFF
            sum += b
            count++
        }
        val avg = (sum / count).toInt()
        // if near-uniform (very low variance), frame is likely blank/monochrome
        var variance = 0L
        for (i in 0 until sampleSize) {
            val diff = (buf[i * step].toInt() and 0xFF) - avg
            variance += diff * diff
        }
        val stdDev = kotlin.math.sqrt(variance.toDouble() / count)
        return stdDev < 8.0
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch {
            val result = repository.loadFullProject(projectId) ?: return@launch
            val original = result.project
            val copy = original.copy(
                name = "${original.name} (Copy)",
                dateCreatedMs = System.currentTimeMillis(),
                dateModifiedMs = System.currentTimeMillis()
            )
            repository.saveFullProject(copy)
        }
    }
}
