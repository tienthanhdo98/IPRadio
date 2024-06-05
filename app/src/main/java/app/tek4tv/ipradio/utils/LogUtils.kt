package app.tek4tv.ipradio.utils

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.storage.*
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
const val HUB_LOG_FOLDER_NAME = "hub_logs"
const val PLAYER_LOG_FOLDER_NAME = "player_logs"
const val TIMEFRAME_LOG_FOLDER_NAME = "timerframe_logs"
const val COMMAND_LOG_FOLDER_NAME = "commands_logs"

class LogUtils private constructor() {
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

    /* private val rtDatabase : DatabaseReference
     private val rootDeviceDb : DatabaseReference
     private val playlistDb : DatabaseReference
     private val timeframeDb : DatabaseReference
     private val deviceStatusDb : DatabaseReference
     private val networkDb : DatabaseReference*/
    private val imei = Utils.getDeviceId(appContext)

    private val mediaReportAdapter = MoshiInst.moshi.adapter(MediaRecord::class.java)
    private val timeframeReportAdapter = MoshiInst.moshi.adapter(TimeFrameRecord::class.java)
    private val hubStateReportAdapter = MoshiInst.moshi.adapter(HubStateRecord::class.java)

    var logService: LogService? = null
    val destinationId = GlobalProperties.getInst().destinationId
    val destinationName = GlobalProperties.getInst().destinationName
    val providerValue = GlobalProperties.getInst().providerValue

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

        /*rtDatabase = Firebase.database.reference
        rootDeviceDb = rtDatabase.child("ipradio").child("device").child(imei)
        playlistDb = rootDeviceDb.child("playlist")
        timeframeDb = rootDeviceDb.child("time_frame")
        deviceStatusDb = rootDeviceDb.child("device_status")
        networkDb = rootDeviceDb.child("network")*/
        /*devicePlaylistDb = rtDatabase.child("ipradio").child("device")
            .child(imei).child("playlist")*/
    }

    fun logHubCommand(command: String, message: String) {
        val filePath = getLogFilePath(commandLogFolderPath)
        writeLogToFile(filePath, "$command : $message", true)
    }

    fun logHubError(error: String)
    {
        try {
            val logPath = "logFolderPath/${fileNameFormat.format(Date())}.txt"
            val out = PrintWriter(BufferedWriter(FileWriter(logPath, true)))
            val df = SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss")

            out.println("------------------------")

            out.println("${df.format(Calendar.getInstance().time)}\n")

            out.println("Error  ${error}")
            out.println("------------------------")

            out.close()
        } catch (e: Exception) {

        }
    }

    fun logMediaStart(mediaItem: MediaItem, duration: Long, volume: String, paState: String) {
        val filePath = getLogFilePath(playerLogFolderPath)
        val sourceId: String
        val sourceName: String

        if (mediaItem.sourceID != null && mediaItem.sourceName != null) {
            sourceId = mediaItem.sourceID
            sourceName = mediaItem.sourceName
        } else {
            sourceId = destinationId
            sourceName = destinationName
        }
        val externalSource: String
        val isExternalSource: Int
        if (mediaItem.type != "VOD") {
            externalSource = mediaItem.pathBackup
            isExternalSource = 1
        } else {
            externalSource = ""
            isExternalSource = 0
        }
        // remove at 4.1.3b
//        var link = mediaItem.body
        val currentTime = Calendar.getInstance().time
//        link = if (link!!.startsWith("rtsp")) {
//            "live"
//        } else {
//            mediaItem.pathBackup
//        }
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
            body =  mediaItem.pathBackup,
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
        )
        val str = getRecordStr(reportObj)

        if (str != null)
            writeLogToFile(filePath, str)
        Log.d(
            LOG_TAG,
            "Log media start send ${reportObj}"
        )
        coroutineScope.launch {
            try {
                logService?.postMediaInfoStart(reportObj)
                Log.d(
                    LOG_TAG,
                    "Log media start ${reportObj}"
                )
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error send log media ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun logMediaEnd(
        mediaItem: MediaItem
    ) {
        val filePath = getLogFilePath(playerLogFolderPath)
        var sourceId = ""
        var sourceName = ""
        if (mediaItem.sourceID != null && mediaItem.sourceName != null) {
            sourceId = mediaItem.sourceID
            sourceName = mediaItem.sourceName
        } else {
            sourceId = destinationId
            sourceName = destinationName
        }
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
        // remove at 4.1.3b
        //        var link = ""
//        link = if (link.startsWith("rtsp")) {
//            "live"
//        } else {
//            mediaItem.pathBackup
//        }
        val reportObj = MediaRecord(
            imei = imei,
            title = mediaItem.title ?: "",
            sourceID = sourceId,
            sourceName = sourceName,
            destinationId = destinationId,
            destinationName = destinationName,
            mediaID = mediaItem.mediaID.toString(),
            body = mediaItem.pathBackup,
            endTime = currentTime.time,
            status = "1",
            duration = mediaItem.duration,
            author = mediaItem.author,
            category = mediaItem.category,
            priority = mediaItem.priority,
            contentType = mediaItem.contentType,
            description = mediaItem.description,
            externalSource = externalSource,
            isExternal = isExternalSource,
            deviceName = GlobalProperties.getInst().nameDevice,
            sourceReference =  mediaItem.sourceReference,
            provider = providerValue
        )
        val str = getRecordStr(reportObj)

        if (str != null)
            writeLogToFile(filePath, str)

//        coroutineScope.launch {
//            try {
//                logService?.postMediaInfoEnd(reportObj)
//                Log.d(
//                    LOG_TAG,
//                    "Log media stop ${reportObj.title} $sourceId : $sourceName : status ${reportObj.status}"
//                )
//            } catch (e: Exception) {
//                Log.d(LOG_TAG, "Error send log media ${e.message}")
//                e.printStackTrace()
//            }
//        }
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

    private fun getRecordStr(record: LogRecord): String? {
        return when (record) {
            is MediaRecord -> mediaReportAdapter.toJson(record)
            is TimeFrameRecord -> timeframeReportAdapter.toJson(record)
            is HubStateRecord -> hubStateReportAdapter.toJson(record)

            else -> null
        }
    }

    fun logDataHub(imei: String, data: DataValueModel) {
        val LOG_TAG = "MYLOG"
        val strJs = data.toJson()
        coroutineScope.launch {
            val reportObj = DataLogModel(
                imei = imei,
                data = strJs
            )
            try {
                val a = logService?.postDeviceLog(reportObj)
                Log.d(
                    LOG_TAG,
                    "Log hub data ${reportObj}-${a!!.body()}"
                )
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error send log media ${e.message}")
                e.printStackTrace()
            }
        }


    }

    companion object {
        private var instance: LogUtils? = null

        @Synchronized
        fun getInst(): LogUtils {
            if (instance == null) instance = LogUtils()
            return instance!!
        }
    }
}