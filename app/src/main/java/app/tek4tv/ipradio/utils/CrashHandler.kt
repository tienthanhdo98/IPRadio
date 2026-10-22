package app.tek4tv.ipradio.utils

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.ui.MainActivity
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler(private val activity: Activity) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Log.e(TAG, "FATAL CRASH in thread ${thread.name}: ${ex.message}", ex)
        val appInstance = MyApp.instance
        if (appInstance != null) {
            logToFile(thread, ex)
            restartApp(appInstance, activity)
        }
    }

    private fun logToFile(thread: Thread, ex: Throwable) {
        try {
            val logFile = File("${activity.applicationContext.filesDir.path}/$LOG_FOLDER_NAME/logerror.txt")
            logFile.parentFile?.mkdirs()
            val out = PrintWriter(BufferedWriter(FileWriter(logFile, true)))
            val df = SimpleDateFormat("yyyy.MM.dd 'at' HH:mm:ss", Locale.getDefault())

            out.println("------------------------")
            out.println("${df.format(Calendar.getInstance().time)}\n")
            out.println("Thread name: ${thread.name}")
            out.println("Message: ${ex.message}")
            out.println("Cause: ${ex.cause}")
            out.println("Stack trace: ")
            ex.printStackTrace(out)
            out.println("------------------------")
            out.close()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi ghi logerror.txt: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CrashHandler"
        private var lastRestartTime = 0L
        private var crashCount = 0

        fun restartApp(appInstance: MyApp, activity: Activity) {
            val now = System.currentTimeMillis()
            if (now - lastRestartTime < 10000) {
                crashCount++
            } else {
                crashCount = 1
            }
            lastRestartTime = now

            // Delay ít nhất 1.5s, nếu crash liên tục >= 3 lần thì delay 5s để tránh bão crash làm nghẽn CPU box
            val delayMs = if (crashCount >= 3) 5000L else 1500L

            val intent = Intent(activity, MainActivity::class.java).apply {
                putExtra("crash", true)
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            val pendingIntent = PendingIntent.getActivity(
                appInstance.baseContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            val mgr =
                appInstance.baseContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr[AlarmManager.RTC, System.currentTimeMillis() + delayMs] = pendingIntent
            activity.finish()
            System.exit(2)
        }
    }
}