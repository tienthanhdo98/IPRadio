package app.tek4tv.ipradio.utils

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.ui.MainActivity
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler(private val activity: Activity) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, ex: Throwable) {

        val appInstance = MyApp.instance

        // TODO : add crash handler to app when building for customer

        if (appInstance != null) {
            restartApp(appInstance, activity)
        }
    }


    companion object {
        fun restartApp(appInstance: MyApp, activity: Activity) {
            val intent = Intent(activity, MainActivity::class.java)
            intent.putExtra("crash", true)
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            )
            val pendingIntent = PendingIntent.getActivity(
                appInstance.baseContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            val mgr =
                appInstance.baseContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = pendingIntent
            activity.finish()
            System.exit(2)
        }
    }
}