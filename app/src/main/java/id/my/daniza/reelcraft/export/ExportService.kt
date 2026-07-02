package id.my.daniza.reelcraft.export

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.my.daniza.reelcraft.MainActivity
import id.my.daniza.reelcraft.ReelCraftApplication
import id.my.daniza.reelcraft.data.DummyProjects
import id.my.daniza.reelcraft.engine.ExportProcessor
import id.my.daniza.reelcraft.viewmodel.ExportResolution
import id.my.daniza.reelcraft.viewmodel.ExportSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var exportJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification(0f))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectId = intent?.getStringExtra(EXTRA_PROJECT_ID) ?: return START_NOT_STICKY
        val resolutionOrdinal = intent.getIntExtra(EXTRA_RESOLUTION, 0)
        val frameRate = intent.getIntExtra(EXTRA_FRAME_RATE, 30)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 20)

        val project = DummyProjects.projectById(projectId) ?: return START_NOT_STICKY
        val settings = ExportSettings(
            resolution = ExportSettingsAdapter.resolutions().getOrElse(resolutionOrdinal) { ExportResolution.P1080 },
            frameRate = frameRate,
            bitrateMbps = bitrate
        )

        exportJob?.cancel()
        exportJob = scope.launch {
            val result = ExportProcessor.export(
                context = this@ExportService,
                project = project,
                settings = settings,
                onProgress = { progress ->
                    updateNotification(progress)
                }
            )
            updateNotification(if (result.success) 1f else 0f)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        exportJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(progress: Float): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ReelCraftApplication.CHANNEL_EXPORT)
            .setContentTitle("Exporting Video")
            .setContentText("${(progress * 100).toInt()}% complete")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(progress: Float) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(progress))
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_RESOLUTION = "resolution"
        const val EXTRA_FRAME_RATE = "frame_rate"
        const val EXTRA_BITRATE = "bitrate"
    }
}

private object ExportSettingsAdapter {
    fun resolutions() = listOf(
        id.my.daniza.reelcraft.viewmodel.ExportResolution.P480,
        id.my.daniza.reelcraft.viewmodel.ExportResolution.P720,
        id.my.daniza.reelcraft.viewmodel.ExportResolution.P1080,
    )
}
