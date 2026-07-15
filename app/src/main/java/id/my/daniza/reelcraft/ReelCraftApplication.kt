package id.my.daniza.reelcraft

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import id.my.daniza.segment.SegmentEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@HiltAndroidApp
class ReelCraftApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SegmentEngine.init(this)
        appScope.launch {
            SegmentEngine.getInstance().preloadAll()
        }
        createExportChannel()
    }

    private fun createExportChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_EXPORT,
                "Video Export",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows export progress for video rendering"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_EXPORT = "reelcraft_export"
    }

    override fun onTerminate() {
        appScope.cancel()
        SegmentEngine.close()
        super.onTerminate()
    }
}
