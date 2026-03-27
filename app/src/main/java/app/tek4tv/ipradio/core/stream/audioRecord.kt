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
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FFMEPGStreamer {
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recording = false
    private var audioExecutor: ExecutorService? = null

    fun startMicToIcecast(activity: Activity, streamUrl: String?) {

        if (streamUrl.isNullOrEmpty()) return
        if (recording) {
            Log.d("ICECAST", "Already streaming")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                activity, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("ICECAST", "RECORD_AUDIO permission not granted")
            return
        }

        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBuffer, 4096)

            val pipePath = FFmpegKitConfig.registerNewFFmpegPipe(activity)

//            val ffmpegArgs = arrayOf(
//                "-f",
//                "s16le",
//                "-ar",
//                sampleRate.toString(),
//                "-ac",
//                "1",
//                "-i",
//                pipePath,
//                "-vn",
//                "-c:a",
//                "aac",
//                "-b:a",
//                "128k",
//                "-content_type",
//                "audio/aac",
//                "-ice_name",
//                "Android Mic Live",
//                "-ice_description",
//                "Mic to Icecast",
//                "-f",
//                "adts",
//                streamUrl
//            )

            val ffmpegArgs = arrayOf(
                "-f", "s16le",
                "-ar", sampleRate.toString(),
                "-ac", "1",
                "-channel_layout", "mono",
                "-i", pipePath,
                "-vn",
                "-c:a", "aac",
                "-profile:a", "aac_low",
                "-b:a", "128k",
                "-cutoff", "17000",
                "-content_type", "audio/aac",
                "-f", "adts",
                streamUrl
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize
            )

            recording = true
            audioRecord?.startRecording()

            FFmpegKit.executeWithArgumentsAsync(ffmpegArgs) { session ->
                val rc = session.returnCode
                if (ReturnCode.isSuccess(rc)) {
                    Log.d("ICECAST", "Stream ended normally")
                } else if (ReturnCode.isCancel(rc)) {
                    Log.d("ICECAST", "Stream cancelled")
                } else {
                    Log.e("ICECAST", "Stream failed: ${session.allLogsAsString}")
                }

                stopMicToIcecast()
            }

            audioExecutor = Executors.newSingleThreadExecutor()
            audioExecutor?.execute {
                val buffer = ByteArray(bufferSize)
                try {
                    FileOutputStream(pipePath).use { fos ->
                        while (recording) {
                            val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                            if (read > 0) {
                                fos.write(buffer, 0, read)
                                fos.flush()
                            } else if (read < 0) {
                                Log.e("ICECAST", "AudioRecord read error: $read")
                                break
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e("ICECAST", "Pipe closed / broken pipe: ${e.message}")
                } catch (e: Exception) {
                    Log.e("ICECAST", "Audio thread error", e)
                } finally {
                    stopMicToIcecast()
                }
            }

        } catch (e: Exception) {
            Log.e("ICECAST", "startMicToIcecast failed", e)
            stopMicToIcecast()
        }
    }

    fun stopMicToIcecast() {
        if (!recording && audioRecord == null) return

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

        try {
            FFmpegKit.cancel()
        } catch (_: Exception) {
        }

        try {
            audioExecutor?.shutdownNow()
        } catch (_: Exception) {
        }

        audioExecutor = null
    }

}