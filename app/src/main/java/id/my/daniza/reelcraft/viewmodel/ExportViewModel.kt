package id.my.daniza.reelcraft.viewmodel

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import id.my.daniza.reelcraft.data.DummyProjects
import id.my.daniza.reelcraft.model.Project

enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    P480("480p", 854, 480),
    P720("720p", 1280, 720),
    P1080("1080p", 1920, 1080)
}

enum class ExportFormat(val label: String) {
    MP4("MP4")
}

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.P1080,
    val format: ExportFormat = ExportFormat.MP4,
    val frameRate: Int = 30,
    val bitrateMbps: Int = 20
)

class ExportViewModel : ViewModel() {

    var project by mutableStateOf<Project?>(null)
        private set

    var settings by mutableStateOf(ExportSettings())
        private set

    var isExporting by mutableStateOf(false)
        private set

    var progress by mutableFloatStateOf(0f)
        private set

    var isComplete by mutableStateOf(false)
        private set

    fun loadProject(projectId: String) {
        project = DummyProjects.projectById(projectId)
    }

    fun updateResolution(resolution: ExportResolution) {
        settings = settings.copy(resolution = resolution)
    }

    fun updateFrameRate(frameRate: Int) {
        settings = settings.copy(frameRate = frameRate)
    }

    fun updateBitrate(bitrate: Int) {
        settings = settings.copy(bitrateMbps = bitrate)
    }

    fun startExport() {
        isExporting = true
        progress = 0f
        isComplete = false
    }

    fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        if (progress >= 1f) {
            isExporting = false
            isComplete = true
        }
    }

    fun cancelExport() {
        isExporting = false
        progress = 0f
    }

    fun reset() {
        isExporting = false
        progress = 0f
        isComplete = false
    }
}