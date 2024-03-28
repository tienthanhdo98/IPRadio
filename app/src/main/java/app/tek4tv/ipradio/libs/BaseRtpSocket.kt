package app.tek4tv.ipradio.libs

import com.pedro.rtsp.rtp.sockets.RtpSocketTcp
import com.pedro.rtsp.rtp.sockets.RtpSocketUdp
import com.pedro.rtsp.rtsp.Protocol
import com.pedro.rtsp.rtsp.RtpFrame
import java.io.IOException
import java.io.OutputStream

/**
 * Created by pedro on 7/11/18.
 */
abstract class BaseRtpSocketTek {

    protected val TAG = "BaseRtpSocket"

    companion object {
        @JvmStatic
        fun getInstance(protocol: Protocol, videoSourcePort: Int, audioSourcePort: Int): BaseRtpSocketTek {
            return if (protocol === Protocol.TCP) {
                RtpSocketTcpTek()
            } else {
                RtpSocketUdpTek(videoSourcePort, audioSourcePort)
            }
        }
    }

    @Throws(IOException::class)
    abstract fun setDataStream(outputStream: OutputStream, host: String)

    @Throws(IOException::class)
    abstract fun sendFrame(rtpFrame: RtpFrame, isEnableLogs: Boolean)

    abstract fun close()
}