package app.tek4tv.ipradio.libs.audio

import android.os.HandlerThread
import android.util.Log
import com.pedro.encoder.Frame
import com.pedro.encoder.GetFrame

import java.nio.ByteBuffer


class MicrophoneManagerManualTek : MicrophoneManagerTek(null), GetFrame {
    private val TAG = "MicMM"

    /**
     * Start record and get data
     */
    @Synchronized
    override fun start() {
        init()
    }

    private fun init() {
        audioRecord?.startRecording()
        isRunning = true
        Log.i(TAG, "Microphone started")
    }

    /**
     * Call when you need mic samples.
     * This method will block until numBytes worth of samples are ready.
     */
    fun read(directBuffer: ByteBuffer, numBytes: Int): Int {
        directBuffer.rewind()
        // write to the buffer and return number of bytes written.
        return audioRecord!!.read(directBuffer, numBytes)
    }

    /**
     * Stop and release microphone
     */
    @Synchronized
    override fun stop() {
        // handlerThread must not be null, else the stop impl will throw
        handlerThread = HandlerThread("nothing")
        super.stop()
    }

    val getFrame: GetFrame
        get() = this

    override fun getInputFrame(): Frame? {
        return read()
    }
}
