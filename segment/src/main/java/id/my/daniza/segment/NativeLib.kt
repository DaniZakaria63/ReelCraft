package id.my.daniza.segment

class NativeLib {

    /**
     * A native method that is implemented by the 'segment' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'segment' library on application startup.
        init {
            System.loadLibrary("segment")
        }
    }
}