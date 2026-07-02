package id.my.daniza.reelcraft.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import id.my.daniza.reelcraft.data.DummyProjects
import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.Project
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class HomeViewModel : ViewModel() {

    private val _projects = mutableStateListOf<Project>()
    val projects: List<Project> get() = _projects

    var isLoading by mutableStateOf(true)
        private set

    private var nextId = 100

    init {
        loadProjects()
    }

    private fun loadProjects() {
        _projects.clear()
        _projects.addAll(DummyProjects.projects)
        isLoading = false
    }

    fun createProjectFromVideo(context: Context, videoUri: Uri, onComplete: (String) -> Unit) {
        try {
            val tempDir = File(context.cacheDir, "imported_videos")
            tempDir.mkdirs()
            val tempFile = File(tempDir, "video_${nextId}_${System.currentTimeMillis()}.mp4")

            context.contentResolver.openInputStream(videoUri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val projectId = "proj_$nextId"
            val clipId = "clip_${nextId}"
            nextId++

            val project = Project(
                id = projectId,
                name = "Imported Video",
                durationUs = 0L,
                thumbnailPath = null,
                clips = listOf(
                    Clip(
                        id = clipId,
                        sourcePath = tempFile.absolutePath,
                        trimStartUs = 0L,
                        trimEndUs = 0L,
                        orderIndex = 0
                    )
                ),
                aspectRatio = AspectRatio.SixteenNine
            )
            _projects.add(0, project)
            onComplete(projectId)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun createProject(name: String, videoUri: String?) {
        val newProject = Project(
            id = "proj_$nextId",
            name = name.ifBlank { "Untitled Project" },
            thumbnailPath = null,
            durationUs = 0L,
            clips = emptyList(),
            musicTrack = null,
            aspectRatio = AspectRatio.SixteenNine
        )
        nextId++
        _projects.add(0, newProject)
    }

    fun deleteProject(projectId: String) {
        _projects.removeAll { it.id == projectId }
    }

    fun duplicateProject(projectId: String) {
        val original = _projects.find { it.id == projectId } ?: return
        val copy = original.copy(
            id = "proj_${nextId}",
            name = "${original.name} (Copy)",
            dateCreatedMs = System.currentTimeMillis(),
            dateModifiedMs = System.currentTimeMillis()
        )
        nextId++
        _projects.add(0, copy)
    }
}
