package id.my.daniza.reelcraft.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
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

class ExportViewModel(application: Application) : AndroidViewModel(application) {

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

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var outputPath by mutableStateOf<String?>(null)
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
        val p = project ?: return
        isExporting = true
        progress = 0f
        isComplete = false
        errorMessage = null
        outputPath = null

        val intent = android.content.Intent(
            getApplication(),
            id.my.daniza.reelcraft.export.ExportService::class.java
        ).apply {
            putExtra(id.my.daniza.reelcraft.export.ExportService.EXTRA_PROJECT_ID, p.id)
            putExtra(id.my.daniza.reelcraft.export.ExportService.EXTRA_RESOLUTION, settings.resolution.ordinal)
            putExtra(id.my.daniza.reelcraft.export.ExportService.EXTRA_FRAME_RATE, settings.frameRate)
            putExtra(id.my.daniza.reelcraft.export.ExportService.EXTRA_BITRATE, settings.bitrateMbps)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            getApplication<android.app.Application>().startForegroundService(intent)
        } else {
            getApplication<android.app.Application>().startService(intent)
        }
    }

    private fun updateProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }

    fun cancelExport() {
        getApplication<android.app.Application>().let { app ->
            val intent = android.content.Intent(app, id.my.daniza.reelcraft.export.ExportService::class.java)
            app.stopService(intent)
        }
        isExporting = false
        progress = 0f
    }

    fun reset() {
        isExporting = false
        progress = 0f
        isComplete = false
        errorMessage = null
        outputPath = null
    }
}
