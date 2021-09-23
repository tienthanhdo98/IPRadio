package app.tek4tv.ipradio.utils.media


import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.createFolder
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
            val fileName = "${dateformat.format(Date())}.aac"
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

    fun updateRecordedAudio(filePath: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (!File(filePath).exists()) return@withContext

                try {
                    val remoteFolder = Utils.getDeviceId(appContext).replace(":", "-")

                    val ftpClient = FTPClient()
                    ftpClient.connect("14.225.16.145")
                    ftpClient.login("sondev", "123456")
                    ftpClient.setFileType(FTP.BINARY_FILE_TYPE)


                    val isRemoteFolderExisted = ftpClient.changeWorkingDirectory(remoteFolder)

                    if (!isRemoteFolderExisted) {
                        ftpClient.makeDirectory("/$remoteFolder")
                        ftpClient.changeWorkingDirectory(remoteFolder)
                    }

                    val buffIn = BufferedInputStream(FileInputStream(filePath))
                    ftpClient.enterLocalPassiveMode()
                    val res = ftpClient.storeFile(File(filePath).name, buffIn)
                    Log.d("upfile", res.toString())
                    buffIn.close()
                    ftpClient.logout()
                    ftpClient.disconnect()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}