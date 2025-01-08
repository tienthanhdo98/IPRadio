package app.tek4tv.ipradio.utils

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.RetrofitInstance.moshi
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.utils.storage.createFolder
import app.tek4tv.ipradio.utils.storage.isFolderExisted
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
    private val mediaReportAdapter = moshi.adapter(MediaRecord::class.java)
    private val timeframeReportAdapter = moshi.adapter(TimeFrameRecord::class.java)
    private val hubStateReportAdapter = moshi.adapter(HubStateRecord::class.java)

    var logService: AuthService? = null
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

//    fun logStartTimeFrame(frame: String) {
//        val reportObj = TimeFrameRecord(imei, frame, TimeFrameRecord.STATUS_START)
//        logTimeFrame(reportObj)
//    }
//
//    fun logEndTimeFrame(frame: String) {
//        val reportObj = TimeFrameRecord(imei, frame, TimeFrameRecord.STATUS_STOP)
//        logTimeFrame(reportObj)
//    }

//    private fun logTimeFrame(reportObj: TimeFrameRecord) {
//        val filePath = getLogFilePath(timeframeLogFolderPath)
//        val str = getRecordStr(reportObj)
//
//        if (str != null)
//            writeLogToFile(filePath, str)
//
//        //timeframeDb.child(reportObj.time.toString()).setValue(reportObj)
//
//        coroutineScope.launch {
//            try {
//                logService?.postTimeframeInfo(reportObj)
//                Log.d("LogUtils", "log timeframe success")
//            } catch (e: Exception) {
//                Log.d("LogUtils", "Error send log timeframe")
//                e.printStackTrace()
//            }
//        }
//    }



    fun logDataHub(imei: String, data: DataValueModel) {
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
                    "Log hub data ${reportObj}-${a?.body()}"
                )
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error send log media ${e.message}")
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

    companion object {
        private var instance: LogUtils? = null

        @Synchronized
        fun getInst(): LogUtils {
            if (instance == null) instance = LogUtils()
            return instance!!
        }
    }
}