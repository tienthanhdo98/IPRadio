package app.tek4tv.ipradio.utils

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs


fun convertTimeInMillisToHHmmss(timeInMillis: Long): String {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date(timeInMillis))
}

fun toCalendar(time: String): Calendar {
    return try {
        Calendar.getInstance().apply {
            val t = time.split(":").map { it.toInt() }
            set(Calendar.HOUR_OF_DAY, t[0])
            set(Calendar.MINUTE, t[1])
            set(Calendar.SECOND, t[2])
        }
    } catch (e: Exception) {
        Log.d("toCalendar()", e.message!!)
        Calendar.getInstance()
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

@RequiresApi(Build.VERSION_CODES.O)
fun strFormatToCalendar(end: String, zoneId: ZoneId = ZoneId.systemDefault()): Calendar {
    return try {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val ldt = LocalDateTime.parse(end, fmt)              // "2025-09-27 14:21:40"
        val zdt = ldt.atZone(zoneId)
        GregorianCalendar.from(zdt)
    } catch (e: Exception) {
        e.message?.let { Log.d("strFormatToCalendar", it) }
        Calendar.getInstance()
    }
}

fun getCurrentTime(): Long {
    return Calendar.getInstance().time.time
}



