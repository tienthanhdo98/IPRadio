package app.tek4tv.ipradio.utils.logger

import android.content.Context
import android.util.Log
import io.tus.android.client.TusPreferencesURLStore
import io.tus.java.client.TusClient
import io.tus.java.client.TusUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * TusLogUploader: Upload các tệp log JSONL (device_error_logs / campaign_logs) qua giao thức TUS v1.0.0.
 */
class TusLogUploader(
    context: Context,
    private val uploadEndpoint: String = DEFAULT_UPLOAD_ENDPOINT
) {
    private val tusStore = TusPreferencesURLStore(
        context.getSharedPreferences("tus_logger", Context.MODE_PRIVATE)
    )

    private val tusClient = TusClient().apply {
        uploadCreationURL = URL(uploadEndpoint)
        headers = mapOf(
            "X-Forwarded-Host" to "gateway-vinama.tek4tv.vn",
            "X-Forwarded-Proto" to "https"
        )
        enableResuming(tusStore)
    }

    suspend fun uploadLogFile(
        logFile: File,
        adId: String,
        imei: String,
        location: String,
        logTag: String = TAG
    ): Boolean = withContext(Dispatchers.IO) {
        if (!logFile.exists() || logFile.length() == 0L) {
            Log.w(logTag, "File log không tồn tại hoặc rỗng: ${logFile.name}")
            return@withContext false
        }

        val timestamp = System.currentTimeMillis().toString()
        val cleanFileName = logFile.name.removePrefix("uploading_")
        var attempt = 0
        var isSuccess = false

        while (attempt < MAX_UPLOAD_ATTEMPTS) {
            attempt++
            try {
                Log.d(
                    logTag,
                    "Uploading ${logFile.name} (ad_id=$adId) attempt $attempt/$MAX_UPLOAD_ATTEMPTS via TUS..."
                )
                val upload = TusUpload(logFile).apply {
                    metadata = mapOf(
                        "imei" to imei,
                        "ad_id" to adId,
                        "timestamp" to timestamp,
                        "location" to location,
                        "filename" to cleanFileName,
                        "name" to cleanFileName
                    )
                }
                val uploader = tusClient.createUpload(upload)
                uploader.chunkSize = 512 * 1024

                while (uploader.uploadChunk() > -1) {
                    // stream chunk
                }
                uploader.finish()

                val uploadUrl = uploader.uploadURL.toString()
                Log.d(logTag, "Finished uploading ${logFile.name}. Verifying: $uploadUrl")

                if (verifyUrlAccessible(uploadUrl)) {
                    Log.d(logTag, "Upload verified successfully: $uploadUrl")
                    isSuccess = true
                    try {
                        tusStore.remove(upload.fingerprint)
                    } catch (e: Exception) {
                        Log.w(logTag, "Lỗi xóa fingerprint khỏi tusStore: ${e.message}")
                    }
                    break
                } else {
                    Log.w(logTag, "Upload verification failed attempt $attempt for $uploadUrl")
                }
            } catch (e: Exception) {
                Log.e(logTag, "Upload attempt $attempt failed for ${logFile.name}: ${e.message}", e)
            }

            if (attempt < MAX_UPLOAD_ATTEMPTS) {
                val waitTime = BACKOFF_DELAYS_MS.getOrElse(attempt - 1) { 10000L }
                delay(waitTime)
            }
        }
        isSuccess
    }

    private suspend fun verifyUrlAccessible(urlStr: String): Boolean = withContext(Dispatchers.IO) {
        for (method in listOf("HEAD", "GET")) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 5000
                    readTimeout = 5000
                    instanceFollowRedirects = true
                }
                val code = connection.responseCode
                if (code in 200..299) {
                    return@withContext true
                }
            } catch (_: Exception) {
            } finally {
                connection?.disconnect()
            }
        }
        false
    }

    companion object {
        private const val TAG = "TusLogUploader"
        const val DEFAULT_UPLOAD_ENDPOINT = "https://upload-vinama.tek4tv.vn/files"
        private const val MAX_UPLOAD_ATTEMPTS = 3
        private val BACKOFF_DELAYS_MS = listOf(2000L, 5000L, 10000L)
    }
}
