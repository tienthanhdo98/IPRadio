

import android.util.Log
import com.pedro.rtsp.utils.ConnectCheckerRtsp


open class StreamBaseFrag : ConnectCheckerRtsp {



    val TAG = "StreamBaseFrag"

    override fun onAuthErrorRtsp() {
        Log.e(TAG, "onAuthErrorRtsp: ", )
    }

    override fun onAuthSuccessRtsp() {
        Log.e(TAG, "onAuthSuccessRtsp: ", )
    }

    override fun onConnectionFailedRtsp(reason: String) {
        Log.e(TAG, "onConnectionFailedRtsp: ", )
    }

    override fun onConnectionStartedRtsp(rtspUrl: String) {
        Log.e(TAG, "onConnectionStartedRtsp: ", )
    }



    override fun onConnectionSuccessRtsp() {
        Log.e(TAG, "onConnectionSuccessRtsp: ", )
    }

    override fun onDisconnectRtsp() {
        Log.e(TAG, "onDisconnectRtsp: ", )
    }

    override fun onNewBitrateRtsp(bitrate: Long) {
        Log.e(TAG, "onNewBitrateRtsp: $bitrate", )
    }


}