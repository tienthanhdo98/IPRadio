package app.tek4tv.digitalsignage.utils


import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.core.view.children
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.storage.createFolder
import kotlinx.coroutines.*
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.videolan.libvlc.util.VLCVideoLayout
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


    fun captureSurfaceView(
        isPortrait: Boolean, mVideoLayout: VLCVideoLayout
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        scope.launch(Dispatchers.Default) {

            try {
                val surfaceView = (mVideoLayout.getChildAt(
                    0) as FrameLayout).children.filter { it is SurfaceView }
                    .first() as SurfaceView

                val bitmap = if (!isPortrait) Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_4444)
                else Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_4444)
                PixelCopy.request(surfaceView, bitmap, {
                    saveMediaToStorage(bitmap)
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

//    private fun sendPictureToSever(service: PlaylistService, bitmap: Bitmap) {
//        scope.launch {
//            val base64Img = withContext(Dispatchers.Default) {
//                val bs = ByteArrayOutputStream()
//                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bs)
//                Base64.encodeToString(bs.toByteArray(), Base64.DEFAULT)
//            }
//
//            withContext(Dispatchers.IO) {
//                try {
//                    val body = mapOf("Data" to base64Img, "Imei" to Utils.getDeviceId(appContext),
//                        "Extension" to ".jpg")
//                    service.postScreenshot(body)
//                    Log.i("capture", "Send picture by base64 to server success ")
//                    Log.i("capture", "base64:  + ${base64Img.trim()}")
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//    }
    // this method saves the image to gallery
    fun storeScreenShot(bm: Bitmap?, context: Context?): File {
        val fileName = "${System.currentTimeMillis()}.jpg"
        val dir = File(context?.filesDir?.path)
        if (!dir.exists())
            dir.mkdirs()
        val file = File(context?.filesDir?.path, fileName)

        try {
            val fOut = FileOutputStream(file)
            bm?.compress(Bitmap.CompressFormat.JPEG, 100, fOut)
            fOut.flush()
            fOut.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return file
    }
    // this method saves the image to gallery
    private fun saveMediaToStorage(bitmap: Bitmap) {
        // Generating a file name
        val filename = "${System.currentTimeMillis()}.jpg"

        // Output stream
        var fos: OutputStream? = null

        // For devices running android >= 0
        // These for devices running on android < Q
        val imagesDir = File(appContext.filesDir?.path)
        val image = File(imagesDir, filename)
        fos = FileOutputStream(image)

        fos.use {
            // Finally writing the bitmap to the output stream that we opened
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            Toast.makeText(appContext, "Captured View and saved to Gallery", Toast.LENGTH_SHORT).show()
        }


    }

}