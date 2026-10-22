package app.tek4tv.ipradio.utils

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs


fun isInRangeTimePlay(): Boolean {
    val currentTime = Calendar.getInstance()
    //Nếu thời gian nằm trong khoảng từ 21:00 -> 23:59
    val rangeNightStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 21)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    val rangeNightEnd = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 58)
        set(Calendar.SECOND, 0)
    }
    // Nếu thời gian nằm trong khoảng từ 00:01 đến 4:00
    val rangeEveningStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 1)
        set(Calendar.SECOND, 0)
    }
    val rangeEveningEnd = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 4)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    if (currentTime.after(rangeNightStart) && currentTime.before(rangeNightEnd)) return false
    if (currentTime.after(rangeEveningStart) && currentTime.before(rangeEveningEnd)) return false

    return true
}

fun getCurrentTime(): Long {
    return Calendar.getInstance().time.time
}

fun secToReadable(sec: Long): String {
    val h = sec / 3600
    val m = (sec - h * 3600) / 60
    val s = sec - h * 3600 - m * 60

    val hs = if (h >= 10) "$h" else "0$h"
    val ms = if (m >= 10) "$m" else "0$m"
    val ss = if (s >= 10) "$s" else "0$s"

    return if (h > 0) "$hs:$ms:$ss"
    else "$ms:$ss"
}

fun toCalendar(time: String): Calendar {
    return try {
        Calendar.getInstance().apply {
            val t = time.split(":").map { it.toInt() }
            if (t.isNotEmpty()) set(Calendar.HOUR_OF_DAY, t[0])
            if (t.size > 1) set(Calendar.MINUTE, t[1]) else set(Calendar.MINUTE, 0)
            if (t.size > 2) set(Calendar.SECOND, t[2]) else set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    } catch (e: Exception) {
        Log.w("toCalendar()", "Error parsing time '$time': ${e.message}")
        Calendar.getInstance().apply {
            set(Calendar.MILLISECOND, 0)
        }
    }
}

//duration: HH:mm:ss
fun getDurationInSecond(duration: String): Int {
    return try {
        (duration.split(":").mapIndexed { index, s ->
            when (index) {
                0 -> s.toInt() * 3600
                1 -> s.toInt() * 60
                2 -> s.toInt()
                else -> 0
            }
        }.fold(0) { acc, it -> acc + it })
    } catch (e: Exception) {
        0
    }
}


fun setSystemTime(requireTime: String) {
    try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)

        val t = requireTime.split(" ")
        val date = t[0].split("/")
        val time = t[1].split(":")

        val command = "date ${date[1]}${date[0]}${time[0]}${time[1]}${date[2]}.${time[2]} \n"
        os.writeBytes(command)
        os.flush()
        os.writeBytes("exit\n")
        os.flush()
        process.waitFor()
        os.close()
    } catch (e: Exception) {
        Log.e("timesync", e.message ?: "")
        e.printStackTrace()
    }
}


