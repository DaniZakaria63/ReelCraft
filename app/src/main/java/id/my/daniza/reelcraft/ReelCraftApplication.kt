package id.my.daniza.reelcraft

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import id.my.daniza.segment.SegmentEngine

@HiltAndroidApp
class ReelCraftApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("ReelCraftApp", "Initializing SegmentEngine on app start")
        SegmentEngine.init(this)
    }

    override fun onTerminate() {
        Log.i("ReelCraftApp", "Cleaning up SegmentEngine")
        SegmentEngine.close()
        super.onTerminate()
    }
}
