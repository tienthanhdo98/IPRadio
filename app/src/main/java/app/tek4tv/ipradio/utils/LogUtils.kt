package app.tek4tv.ipradio.utils

import android.content.Context
import kotlinx.coroutines.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class LogUtils(private val appContext: Context) {
    private var writeFileJob: Job? = null
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
    private var logFilePath = ""
    private val logFolderPath = "${appContext.filesDir}/$MEDIA_LOG_FOLDER_NAME"

    init {

        if(!File(logFolderPath).exists())
        {
            createFolder(logFolderPath)
        }

        val logName = "${SimpleDateFormat("ddMMyyyy").format(Date())}.txt"
        logFilePath = "$logFolderPath/$logName"
    }

    fun logStartTimeFrame(frame : String)
    {
        writeLogToFile("------------------------")
        writeLogToFile("Bắt đầu khung giờ $frame")
    }

    fun logEndTimeFrame(frame : String)
    {
        writeLogToFile("Kết thúc khung giờ $frame")
        writeLogToFile("------------------------\n")
    }

    fun logPlayMedia(mediaName : String)
    {
        writeLogToFile("Phát $mediaName")
    }

    private fun writeLogToFile(line : String)
    {
        coroutineScope.launch {
            writeFileJob?.join()

            writeFileJob = launch {
                val time = dateFormat.format(Date())
                writeLine("$time $line")
            }
        }
    }

    private fun writeLine(line : String)
    {
        try {
            val out = PrintWriter(BufferedWriter(FileWriter(logFilePath, true)))
            out.println(line)
            out.close()
        }
        catch (e : Exception)
        {
            e.printStackTrace()
        }
    }

    fun onActivityStop()
    {
        //coroutineScope.cancel()
    }
}