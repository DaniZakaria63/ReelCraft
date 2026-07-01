package id.my.daniza.reelcraft

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import id.my.daniza.segment.SegmentEngine

@HiltAndroidApp
class ReelCraftApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("ReelCraftApp", "Initializing SegmentEngine on app start")
        SegmentEngine.init(this)
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
        Log.i("ReelCraftApp", "Cleaning up SegmentEngine")
        SegmentEngine.close()
        super.onTerminate()
    }
}
