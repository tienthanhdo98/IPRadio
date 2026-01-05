package app.tek4tv.ipradio.utils

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.AppScope
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.model.logmodel.ExceptionLogModel
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.network.LogService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

const val LOG_FOLDER_NAME = "app_logs"
const val HUB_LOG_FOLDER_NAME = "hub_logs"
const val PLAYER_LOG_FOLDER_NAME = "player_logs"
const val TIMEFRAME_LOG_FOLDER_NAME = "timerframe_logs"
const val COMMAND_LOG_FOLDER_NAME = "commands_logs"

class LogUtils @Inject constructor(
    private val logService: LogService
) {
    private val appContext: Context = MyApp.instance!!.applicationContext
    val LOG_TAG = "LogUtils"

    private var writeFileJob: Job? = null
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private val fileNameFormat = SimpleDateFormat("ddMMyyyy")
    private val logFolderPath = "${appContext.filesDir}/$LOG_FOLDER_NAME"
    private val hubLogFolderPath = "$logFolderPath/$HUB_LOG_FOLDER_NAME"
    private val playerLogFolderPath = "$logFolderPath/$PLAYER_LOG_FOLDER_NAME"
    private val timeframeLogFolderPath = "$logFolderPath/$TIMEFRAME_LOG_FOLDER_NAME"
    private val commandLogFolderPath = "$logFolderPath/$COMMAND_LOG_FOLDER_NAME"

    private val imei = Utils.getDeviceId(appContext)

    private val mediaReportAdapter = MoshiInst.moshi.adapter(MediaRecord::class.java)
    private val timeframeReportAdapter = MoshiInst.moshi.adapter(TimeFrameRecord::class.java)
    private val hubStateReportAdapter = MoshiInst.moshi.adapter(HubStateRecord::class.java)


    val destinationId = GlobalProperties.getInst().destinationId
    val destinationName = GlobalProperties.getInst().destinationName
    var providerValue = GlobalProperties.getInst().providerValue


    init {
        listOf(
            logFolderPath,
            playerLogFolderPath,
            hubLogFolderPath,
            timeframeLogFolderPath,
            commandLogFolderPath
        ).forEach {
            if (!isFolderExisted(it)) createFolder(it)
        }
    }


    fun logHubCommand(command: String, message: String) {
        val filePath = getLogFilePath(commandLogFolderPath)
        writeLogToFile(filePath, "$command : $message", true)
    }

    fun logMediaStart(
        mediaItem: MediaItem, volume: String, paState: String, typeScreen: Int
    ) {

        val externalSource: String
        val isExternalSource: Int
        if (mediaItem.type != "VOD") {
            externalSource = mediaItem.pathBackup
            isExternalSource = 1
        } else {
            externalSource = ""
            isExternalSource = 0
        }

        val currentTime = Calendar.getInstance().time

        val reportObj = MediaRecord(
            imei = imei,
            title = mediaItem.title ?: "",
            sourceID = destinationId,
            sourceName = destinationName,
            destinationId = destinationId,
            destinationName = destinationName,
            mediaID = mediaItem.projectID,
            volume = volume,
            paState = paState,
            duration = mediaItem.duration,
            startTime = (currentTime.time / 1000),
            body = mediaItem.pathBackup,
            author = mediaItem.author,
            category = mediaItem.category,
            priority = mediaItem.priority,
            contentType = mediaItem.contentType,
            description = mediaItem.description,
            externalSource = externalSource,
            isExternal = isExternalSource,
            deviceName = GlobalProperties.getInst().nameDevice,
            sourceReference = mediaItem.sourceReference,
            provider = providerValue,
            mode = typeScreen.toString()
        )
        coroutineScope.launch {
            try {
                logService?.postMediaInfoStart(reportObj)
                Log.d(
                    LOG_TAG, "Log media start $reportObj"
                )
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error send log media start ${e.message}")
                e.printStackTrace()
            }
        }
    }


    private fun writeLogToFile(filePath: String, line: String, logTime: Boolean = false) {
        coroutineScope.launch {
            writeFileJob?.join()

            writeFileJob = launch {
                val time = dateFormat.format(Date())
                val content = if (logTime) "$time $line" else line
                writeLine(filePath, content)
            }
        }
    }

    fun logDataHub(imei: String, data: DataValueModel) {
        val LOG_TAG = "MYLOG"
        val strJs = data.toJson()
        coroutineScope.launch {
            val reportObj = DataLogModel(
                imei = imei, data = strJs
            )
            try {
                val a = logService?.postDeviceLog(reportObj)
                Log.d(
                    LOG_TAG, "Log hub data ${reportObj}-${a!!.body()}"
                )
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error send log media ${e.message}")
                e.printStackTrace()
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