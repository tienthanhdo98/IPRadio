package app.tek4tv.ipradio.ui

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.pedro.rtsp.utils.ConnectCheckerRtsp


open class StreamBaseFrag : ConnectCheckerRtsp, AppCompatActivity() {

    val mainHandler = Handler(Looper.getMainLooper())
    var isStreaming = false

    open fun stopStream() {

    }
    open fun startStream() {

    }
    open fun startStopWatch(){}

    override fun onAuthErrorRtsp() {

    }

    override fun onAuthSuccessRtsp() {

    }

    override fun onConnectionFailedRtsp(reason: String) {
        mainHandler.post {
            Log.e("StreamFileAct", reason)
            Toast.makeText(baseContext,"Stream error $reason",Toast.LENGTH_LONG)
            stopStream()
            isStreaming = false
        }

    }
    override fun onConnectionStartedRtsp(rtmpUrl: String) {
        Log.d("RTMP", "onConnectionStartedRtsp: $rtmpUrl")
    }

    override fun onConnectionSuccessRtsp() {
        mainHandler.post {
            Toast.makeText(baseContext,"Stream succes",Toast.LENGTH_LONG)
            startStopWatch()
            isStreaming = true
        }
    }

    override fun onDisconnectRtsp() {
        Log.d("RTMP", "onDisconnectRtsp")
        mainHandler.post {
            Toast.makeText(baseContext,"Stream stop. Disconnect to server",Toast.LENGTH_LONG)
            stopStream()
            isStreaming = false
        }
    }


    override fun onNewBitrateRtsp(bitrate: Long) {

    }
}