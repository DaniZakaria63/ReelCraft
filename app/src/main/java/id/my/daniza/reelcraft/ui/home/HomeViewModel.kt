package id.my.daniza.reelcraft.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import id.my.daniza.reelcraft.data.DummyProjects
import id.my.daniza.reelcraft.model.Project

class HomeViewModel : ViewModel() {

    private val _projects = mutableStateListOf<Project>()
    val projects: List<Project> get() = _projects

    private var _isLoading by mutableStateOf(true)
    val isLoading: Boolean get() = _isLoading

    private var nextId = 100

    init {
        loadProjects()
    }

    private fun loadProjects() {
        _projects.clear()
        _projects.addAll(DummyProjects.projects)
        _isLoading = false
    }

    fun createProject(name: String, videoUri: String?) {
        val newProject = Project(
            id = "proj_$nextId",
            name = name.ifBlank { "Untitled Project" },
            thumbnailPath = null,
            durationUs = 0L,
            clips = emptyList(),
            musicTrack = null,
            aspectRatio = id.my.daniza.reelcraft.model.AspectRatio.SixteenNine
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
