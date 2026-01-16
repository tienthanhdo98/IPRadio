package app.tek4tv.ipradio.utils.media


import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.storage.createFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.*
import java.text.SimpleDateFormat
import java.util.*


class MediaCapture(
    private val appContext: Context, private val scope: CoroutineScope
) {

    private lateinit var mediaRecorder: MediaRecorder
    private val recordedAudioPath = "${appContext.filesDir.path}/recorded"
    var isRecordingAudio = false

    private fun getBestSampleRate(): Int {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateString = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateString?.toInt() ?: 44100
    }

    fun startCaptureAudio() {
        isRecordingAudio = true

        if (!File(recordedAudioPath).exists()) createFolder(recordedAudioPath)

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            setAudioEncodingBitRate(32000)
            setAudioSamplingRate(getBestSampleRate())
            val dateformat = SimpleDateFormat("ddMMyyyyHHmmss")
            val fileName =
                "${dateformat.format(Date())}_${GlobalProperties.getInst().userNameController}.aac"
            setOutputFile("$recordedAudioPath/$fileName")
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            prepare()
        }

        mediaRecorder.start()
    }

    fun stopAudioCapture() {
        isRecordingAudio = false
        mediaRecorder.stop()
    }

}
