package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.model.FbMediaReport
import app.tek4tv.ipradio.model.MediaItem
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

const val LOG_FOLDER_NAME = "app_logs"
const val SYSTEM_LOG_FOLDER_NAME = "system_logs"
const val PLAYER_LOG_FOLDER_NAME = "player_logs"
const val PERFORMANCE_LOG_FOLDER_NAME = "performance_logs"

class LogUtils(private val appContext: Context) {
    private var writeFileJob: Job? = null
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private val fileNameFormat = SimpleDateFormat("ddMMyyyy")
    private val logFolderPath = "${appContext.filesDir}/$LOG_FOLDER_NAME"
    private val systemLogFolderPath = "$logFolderPath/$SYSTEM_LOG_FOLDER_NAME"
    private val playerLogFolderPath = "$logFolderPath/$PLAYER_LOG_FOLDER_NAME"
    private val performanceLogFolderPath = "$logFolderPath/$PERFORMANCE_LOG_FOLDER_NAME"

    private val rtDatabase : DatabaseReference
    private val devicePlaylistDb : DatabaseReference
    private val imei = Utils.getDeviceId(appContext)

    init {
        listOf(
            logFolderPath,
            playerLogFolderPath,
            systemLogFolderPath,
            performanceLogFolderPath
        ).forEach {
            if (!isFolderExisted(it)) createFolder(it)
        }

        rtDatabase = Firebase.database.reference
        devicePlaylistDb = rtDatabase.child("ipradio").child("device")
            .child(imei).child("playlist")
    }

    fun logPerformance(appContext: Context, appStorageManager: AppStorageManager) {
        val str =
            "Used RAM: ${appStorageManager.getUsedRam()} - Avail RAM: ${appStorageManager.getAvailRam()} - Network Info: ${
                NetworkUtils.getNetworkInfo(appContext)
            }"

        writeLogToFile(getLogFilePath(performanceLogFolderPath), str)
    }

    fun logHubCommand(command: String, message: String) {
        val filePath = getLogFilePath(systemLogFolderPath)
        writeLogToFile(filePath, "$command : $message")
    }

    fun logStartTimeFrame(frame: String) {
        val filePath = getLogFilePath(playerLogFolderPath)
        writeLogToFile(filePath, "------------------------")
        writeLogToFile(filePath, "Bắt đầu khung giờ $frame")
    }

    fun logEndTimeFrame(frame: String) {
        val filePath = getLogFilePath(playerLogFolderPath)
        writeLogToFile(filePath, "Kết thúc khung giờ $frame")
        writeLogToFile(filePath, "------------------------\n")
    }

    fun logPlayMedia(mediaItem: MediaItem) {
        val filePath = getLogFilePath(playerLogFolderPath)
        writeLogToFile(filePath, "Phát ${mediaItem.name}")

        val reportObj = FbMediaReport(mediaItem.name ?: "", mediaItem.fileId)
        devicePlaylistDb.push().setValue(reportObj)
    }

    private fun writeLogToFile(filePath: String, line: String) {
        coroutineScope.launch {
            writeFileJob?.join()

            writeFileJob = launch {
                val time = dateFormat.format(Date())
                writeLine(filePath, "$time $line")
            }
        }
    }

    private fun writeLine(filePath: String, line: String) {
        try {
            val out = PrintWriter(BufferedWriter(FileWriter(filePath, true)))
            out.println(line)
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getLogFilePath(folderPath: String): String {
        val logFileName = "${fileNameFormat.format(Date())}.txt"
        return "$folderPath/$logFileName"
    }
}