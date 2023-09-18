package app.tek4tv.ipradio.utils.encoder

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import androidx.fragment.app.Fragment
import com.pedro.rtmp.utils.ConnectCheckerRtmp
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

open class StreamBaseFrag : ConnectCheckerRtmp {
    var streamUrl = ""
    var streamUsername = ""
    var streamPassword = ""
    var isAuthorized = false
    var listReason = ""
    lateinit var preference: SharedPreferences

    val mainHandler = Handler(Looper.getMainLooper())


    open fun startStream() {

    }

    open fun stopStream() {

    }


    private fun saveFileLogError(reason: String) {
        var destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .toString() + "/"
        val fileName = "LogReasonStopSteam.txt"
        destination += fileName
        //Delete update file if exists
        val file = File(destination)
        if (file.exists()) {
            file.delete()
        }
        else
        {
            file.createNewFile()
        }
        try {
            val out = PrintWriter(BufferedWriter(FileWriter(destination, true)))
            out.write(reason)
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    override fun onAuthErrorRtmp() {

    }

    override fun onAuthSuccessRtmp() {

    }

    override fun onConnectionFailedRtmp(reason: String) {
        val df =  SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        val now = Calendar.getInstance().time
        val logTime = df.format(now)
        listReason +="$logTime - $reason\n"
        mainHandler.post {
            Log.e("StreamFileAct", reason)
            stopStream()
        }

        saveFileLogError(listReason)
    }

    override fun onConnectionStartedRtmp(rtmpUrl: String) {
        Log.d("RTMP", "onConnectionStartedRtmp: $rtmpUrl")
    }

    override fun onConnectionSuccessRtmp() {

    }

    override fun onDisconnectRtmp() {

    }

    override fun onNewBitrateRtmp(bitrate: Long) {

    }
}