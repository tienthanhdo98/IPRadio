package app.tek4tv.ipradio.utils

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.MoshiInst
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.LogService
import app.tek4tv.ipradio.utils.crypto.b64Encode
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

    fun logStartTimeFrame(frame: String) {
        val reportObj = TimeFrameRecord(imei, frame, TimeFrameRecord.STATUS_START)
        logTimeFrame(reportObj)
    }

    fun logEndTimeFrame(frame: String) {
        val reportObj = TimeFrameRecord(imei, frame, TimeFrameRecord.STATUS_STOP)
        logTimeFrame(reportObj)
    }

    private fun logTimeFrame(reportObj: TimeFrameRecord) {
        val filePath = getLogFilePath(timeframeLogFolderPath)
        val str = getRecordStr(reportObj)

        if (str != null)
            writeLogToFile(filePath, str)

        //timeframeDb.child(reportObj.time.toString()).setValue(reportObj)

        coroutineScope.launch {
            try {
                logService?.postTimeframeInfo(reportObj)
                Log.d("LogUtils", "log timeframe success")
            } catch (e: Exception) {
                Log.d("LogUtils", "Error send log timeframe")
                e.printStackTrace()
            }
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
        var link = mediaItem.body
        val currentTime = Calendar.getInstance().time
        link = if (link!!.startsWith("rtsp")) {
            "live"
        } else {
            mediaItem.pathBackup
        }
        val reportObj = MediaRecord(
            imei = imei,
            title = mediaItem.title ?: "",
            sourceID = sourceId,
            sourceName = sourceName,
            destinationId = destinationId,
            destinationName = destinationName,
            mediaID = mediaItem.mediaID.toString(),
            volume = volume,
            paState = paState,
            duration = mediaItem.duration,
            startTime = (currentTime.time / 1000),
            body = link,
            author = mediaItem.author,
            category = mediaItem.category,
            priority = mediaItem.priority,
            contentType = mediaItem.contentType,
            description = mediaItem.description,
            externalSource = externalSource,
            isExternal = isExternalSource,
            deviceName = GlobalProperties.getInst().nameDevice,
            sourceReference = mediaItem.sourceReference

        )
        val str = getRecordStr(reportObj)

        if (str != null)
            writeLogToFile(filePath, str)

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
        var link = ""
        val currentTime = Calendar.getInstance().time
        link = if (link.startsWith("rtsp")) {
            "live"
        } else {
            mediaItem.pathBackup
        }
        val reportObj = MediaRecord(
            imei = imei,
            title = mediaItem.title ?: "",
            sourceID = sourceId,
            sourceName = sourceName,
            destinationId = destinationId,
            destinationName = destinationName,
            mediaID = mediaItem.mediaID.toString(),
            body = link,
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
            sourceReference =  mediaItem.sourceReference
        )
        val str = getRecordStr(reportObj)

        if (str != null)
            writeLogToFile(filePath, str)

        coroutineScope.launch {
            try {
                logService?.postMediaInfoEnd(reportObj)
                Log.d(
                    LOG_TAG,
                    "Log media stop ${reportObj.title} $sourceId : $sourceName : status ${reportObj.status}"
                )
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error send log media ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun logHubConnectState(stateRecord: HubStateRecord) {
        //networkDb.child(stateRecord.start.toString()).setValue(stateRecord)
        val filePath = getLogFilePath(hubLogFolderPath)
        val str = getRecordStr(stateRecord)

        if (str != null)
            writeLogToFile(filePath, str)

        coroutineScope.launch {
            try {
                logService?.postHubStateInfo(stateRecord)
                Log.d("LogUtils", "log connect success ")
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error send log connection state")
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

    fun sendLogFile(type: String, name: String) {
        coroutineScope.launch {
            try {
                val path = when (type) {
                    "media" -> "$playerLogFolderPath/$name.txt"
                    "timeframe" -> "$timeframeLogFolderPath/$name.txt"
                    "connection" -> "$hubLogFolderPath/$name.txt"
                    else -> ""
                }
                val data = b64Encode(readFile(path))
                val start = fileNameFormat.parse(name).time
                val record =
                    GetLogFile(
                        name,
                        imei,
                        "Tek4tv",
                        data = data,
                        start = start,
                        destinationID = destinationId,
                        destinationName = destinationName,
                        appName = "IPRadio"
                    )
                logService?.postLogFile(record)
                Log.e(LOG_TAG, "uploading log file success")
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error uploading log file: ${e.message}")
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