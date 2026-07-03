package id.my.daniza.reelcraft.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.my.daniza.local.db.entity.ProjectEntity
import id.my.daniza.reelcraft.data.repository.ProjectRepository
import id.my.daniza.reelcraft.model.AspectRatio
import id.my.daniza.reelcraft.model.Clip
import id.my.daniza.reelcraft.model.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProjectRepository
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

                val projectId = UUID.randomUUID().toString()
                val fullProject = Project(
                    id = projectId,
                    name = "Imported Video",
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

    fun createProject(name: String) {
        viewModelScope.launch {
            repository.createProject(
                name = name.ifBlank { "Untitled Project" },
                aspectRatio = AspectRatio.SixteenNine
            )
        }
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
