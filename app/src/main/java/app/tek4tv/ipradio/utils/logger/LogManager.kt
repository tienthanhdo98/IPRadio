package app.tek4tv.ipradio.utils.logger

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * LogManager: Điều phối ghi log xuống file cục bộ, dọn dẹp file quá 7 ngày và upload qua TUS.
 */
class LogManager(
    private val context: Context,
    private val uploader: TusLogUploader = TusLogUploader(context),
    private val locationProvider: (() -> String)? = null
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val imei = Utils.getDeviceId(context)

    init {
        LogFileUtils.initFolders()
    }

    fun uploadPendingDeviceErrorLogs() {
        coroutineScope.launch {
            uploadPendingLogsInFolder(
                folderPath = LogFileUtils.deviceErrorLogFolderPath,
                adId = AD_ID_DEVICE_ERROR,
                logTag = "DeviceErrorUpload"
            )
        }
    }

    fun uploadPendingCampaignLogs() {
        coroutineScope.launch {
            uploadPendingLogsInFolder(
                folderPath = LogFileUtils.campaignLogFolderPath,
                adId = AD_ID_CAMPAIGN,
                logTag = "CampaignLogUpload"
            )
        }
    }

    fun uploadAllPendingLogs() {
        uploadPendingDeviceErrorLogs()
        uploadPendingCampaignLogs()
    }

    internal suspend fun uploadPendingLogsInFolder(
        folderPath: String,
        adId: String,
        logTag: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        try {
            val logDir = File(folderPath)
            if (!logDir.exists()) return

            cleanupExpiredLogs(logDir, nowMs, logTag)

            if (!NetworkUtils.isNetworkConnected(context)) {
                Log.d(logTag, "Không có kết nối mạng -> hoãn upload log")
                return
            }

            val pendingFiles = logDir.listFiles { f ->
                f.isFile && f.extension == "jsonl" && !f.name.startsWith("uploading_") && f.length() > 0L
            }?.sortedBy { it.lastModified() } ?: return

            if (pendingFiles.isEmpty()) {
                Log.d(logTag, "Không có file log cần upload trong ${logDir.name}")
                return
            }

            val location = locationProvider?.invoke() ?: buildDefaultLocation()
            Log.d(logTag, "Bắt đầu upload ${pendingFiles.size} file log...")

            for (file in pendingFiles) {
                val uploadFile = File(file.parent, "uploading_${file.name}")
                val renamed = synchronized(LogFileUtils.fileLock) {
                    file.renameTo(uploadFile)
                }
                if (!renamed) {
                    Log.w(logTag, "Không thể rename ${file.name} sang uploading_, bỏ qua")
                    continue
                }

                val success = uploader.uploadLogFile(
                    logFile = uploadFile,
                    adId = adId,
                    imei = imei,
                    location = location,
                    logTag = logTag
                )
                if (success) {
                    uploadFile.delete()
                    Log.d(logTag, "Upload thành công và đã xóa: ${uploadFile.name}")
                } else {
                    synchronized(LogFileUtils.fileLock) {
                        uploadFile.renameTo(file)
                    }
                    Log.e(logTag, "Upload thất bại, giữ lại để retry: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(logTag, "uploadPendingLogsInFolder lỗi: ${e.message}", e)
        }
    }

    private fun buildDefaultLocation(): String {
        val props = GlobalProperties.getInst()
        return "${props.destinationId},${props.destinationName}"
    }

    companion object {
        const val AD_ID_DEVICE_ERROR = "LOGGER"
        const val AD_ID_CAMPAIGN = "CAMPAIGN_LOGGER"
        const val MAX_LOG_AGE_MS = 7L * 24 * 60 * 60 * 1000L

        /** Xóa các file .jsonl cũ quá 7 ngày để bảo vệ bộ nhớ Flash */
        fun cleanupExpiredLogs(logDir: File, nowMs: Long = System.currentTimeMillis(), logTag: String = "LogManager") {
            val allFiles = logDir.listFiles { f ->
                f.isFile && f.extension == "jsonl" && !f.name.startsWith("uploading_")
            } ?: return
            allFiles.filter { nowMs - it.lastModified() > MAX_LOG_AGE_MS }.forEach { old ->
                if (old.delete()) {
                    try {
                        Log.d(logTag, "Đã xóa file log quá 7 ngày: ${old.name}")
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
