package app.tek4tv.ipradio.utils.logger

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.LogDeviceModel
import app.tek4tv.ipradio.utils.storage.LOG_FOLDER_NAME
import app.tek4tv.ipradio.utils.storage.createFolder
import app.tek4tv.ipradio.utils.storage.isFolderExisted
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LogFileUtils: Quản lý thư mục, xoay vòng file JSONL (tối đa 2MB/file) và ghi log an toàn đa luồng.
 * Tham khảo kiến trúc từ Tek4TV.App.DigitalSignage (nhánh centek_cdc).
 */
object LogFileUtils {
    private const val LOG_TAG = "LogFileUtils"
    const val DEVICE_ERROR_LOG_FOLDER_NAME = "device_error_logs"
    const val CAMPAIGN_LOG_FOLDER_NAME = "campaign_logs"
    const val MAX_LOG_FILE_SIZE_BYTES = 2 * 1024 * 1024L // 2MB

    val fileLock = Any()

    @Volatile
    private var overrideFilesDir: File? = null

    private val dateLogFmt = SimpleDateFormat("dd-MM-yyyy", Locale.US)
    private val dateTimeFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
    private val campaignDateTimeFmt = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss.SSS", Locale.US)

    /** Cho phép gán thư mục gốc khi chạy Unit Test JVM */
    fun setBaseFilesDirForTest(dir: File?) {
        overrideFilesDir = dir
    }

    private fun resolveFilesDir(): File {
        overrideFilesDir?.let { return it }
        val ctx: Context = MyApp.instance?.applicationContext
            ?: error("Application context not initialized yet")
        return ctx.filesDir
    }

    val logFolderPath: String
        get() = "${resolveFilesDir().path}/$LOG_FOLDER_NAME"

    val deviceErrorLogFolderPath: String
        get() = "$logFolderPath/$DEVICE_ERROR_LOG_FOLDER_NAME"

    val campaignLogFolderPath: String
        get() = "$logFolderPath/$CAMPAIGN_LOG_FOLDER_NAME"

    fun initFolders() {
        listOf(
            logFolderPath,
            deviceErrorLogFolderPath,
            campaignLogFolderPath
        ).forEach { path ->
            if (!isFolderExisted(path)) createFolder(path)
        }
    }

    private fun jsonStr(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    fun logDeviceModelToJson(body: LogDeviceModel, timestamp: String = formatDateTime(Date())): String {
        return "{" +
            "\"ServiceName\":${jsonStr(body.serviceName)}," +
            "\"UserName\":${jsonStr(body.userName)}," +
            "\"ActionName\":${jsonStr(body.actionName)}," +
            "\"ObjectName\":${jsonStr(body.objectName)}," +
            "\"Metadata\":${jsonStr(body.metadata)}," +
            "\"Message\":${jsonStr(body.message)}," +
            "\"FromServer\":${jsonStr(body.fromServer)}," +
            "\"Platform\":${jsonStr(body.platform)}," +
            "\"Code\":${jsonStr(body.code)}," +
            "\"Timestamp\":${jsonStr(timestamp)}" +
            "}"
    }

    fun formatDateLog(date: Date = Date()): String = synchronized(dateLogFmt) { dateLogFmt.format(date) }

    fun formatDateTime(date: Date = Date()): String = synchronized(dateTimeFmt) { dateTimeFmt.format(date) }

    private fun getRotatedJsonlFilePath(folderPath: String, prefix: String): String {
        if (!isFolderExisted(folderPath)) createFolder(folderPath)
        val dateStr = formatDateLog(Date())
        val baseFile = File(folderPath, "${prefix}_${dateStr}.jsonl")
        if (!baseFile.exists() || baseFile.length() < MAX_LOG_FILE_SIZE_BYTES) {
            return baseFile.absolutePath
        }
        var part = 2
        while (true) {
            val partFile = File(folderPath, "${prefix}_${dateStr}_part${part}.jsonl")
            if (!partFile.exists() || partFile.length() < MAX_LOG_FILE_SIZE_BYTES) {
                return partFile.absolutePath
            }
            part++
        }
    }

    fun getDeviceErrorLogFilePath(): String =
        getRotatedJsonlFilePath(deviceErrorLogFolderPath, "device_errors")

    fun getCampaignLogFilePath(): String =
        getRotatedJsonlFilePath(campaignLogFolderPath, "campaign")

    private fun writeLine(filePath: String, line: String) {
        try {
            File(filePath).parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }
            PrintWriter(BufferedWriter(FileWriter(filePath, true))).use { out ->
                out.println(line)
            }
        } catch (e: Exception) {
            try {
                Log.e(LOG_TAG, "writeLine failed: ${e.message}")
            } catch (_: Throwable) {
            }
        }
    }

    fun writeLogDeviceErrorDirect(body: LogDeviceModel, logType: String = "") {
        synchronized(fileLock) {
            try {
                val json = logDeviceModelToJson(body)
                val filePath = getDeviceErrorLogFilePath()
                writeLine(filePath, json)
                try {
                    Log.d(LOG_TAG, "DeviceErrorLog ($logType) -> $filePath")
                } catch (_: Throwable) {
                }
            } catch (e: Exception) {
                try {
                    Log.e(LOG_TAG, "DeviceErrorLog failed ($logType): ${e.message}")
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun writeCampaignLogDirect(body: LogDeviceModel, nowMs: Long = System.currentTimeMillis()) {
        synchronized(fileLock) {
            try {
                val timestampStr = synchronized(campaignDateTimeFmt) {
                    campaignDateTimeFmt.format(Date(nowMs))
                }
                val json = logDeviceModelToJson(body, timestampStr)
                val filePath = getCampaignLogFilePath()
                writeLine(filePath, json)
                try {
                    Log.d(LOG_TAG, "CampaignLog (${body.code}) -> $filePath")
                } catch (_: Throwable) {
                }
            } catch (e: Exception) {
                try {
                    Log.e(LOG_TAG, "CampaignLog failed: ${e.message}")
                } catch (_: Throwable) {
                }
            }
        }
    }
}
