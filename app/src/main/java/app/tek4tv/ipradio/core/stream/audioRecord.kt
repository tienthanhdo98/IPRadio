import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import java.io.FileOutputStream
import java.util.concurrent.Executors

private var audioRecord: AudioRecord? = null
private var recording = false

fun startMicToIcecast(activity: Activity, streamUrl: String) {
    if (recording) {
        Log.d("ICECAST", "Already streaming")
        return
    }

    if (ActivityCompat.checkSelfPermission(
            activity,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        Log.e("ICECAST", "RECORD_AUDIO permission not granted")
        return
    }

    val sampleRate = 44100
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    val bufferSize = maxOf(minBuffer, 4096)

    val pipePath = FFmpegKitConfig.registerNewFFmpegPipe(activity)

    val ffmpegArgs = arrayOf(
        "-f", "s16le",
        "-ar", sampleRate.toString(),
        "-ac", "1",
        "-i", pipePath,
        "-vn",
        "-c:a", "aac",
        "-b:a", "128k",
        "-content_type", "audio/aac",
        "-ice_name", "Android Mic Live",
        "-ice_description", "Mic to Icecast",
        "-f", "adts",
        streamUrl
    )

    Log.d("ICECAST", "FFmpeg args: ${ffmpegArgs.joinToString(" | ")}")

    FFmpegKit.executeWithArgumentsAsync(ffmpegArgs) { session ->
        val rc = session.returnCode
        when {
            ReturnCode.isSuccess(rc) -> Log.d("ICECAST", "Stream ended normally")
            ReturnCode.isCancel(rc) -> Log.d("ICECAST", "Stream cancelled")
            else -> Log.e("ICECAST", "Stream failed: ${session.allLogsAsString}")
        }
    }

    audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        channelConfig,
        audioFormat,
        bufferSize
    )

    recording = true
    audioRecord?.startRecording()

    Executors.newSingleThreadExecutor().execute {
        val buffer = ByteArray(bufferSize)
        FileOutputStream(pipePath).use { fos ->
            while (recording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    fos.write(buffer, 0, read)
                }
            }
        }
    }
}

fun stopMicToIcecast() {
    recording = false
    try {
        audioRecord?.stop()
    } catch (_: Exception) {
    }
    try {
        audioRecord?.release()
    } catch (_: Exception) {
    }
    audioRecord = null
    FFmpegKit.cancel()
}