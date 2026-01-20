package app.tek4tv.ipradio.utils

import android.util.Log
import java.io.DataOutputStream
import java.text.SimpleDateFormat
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

fun getCurrentTime(): Long {
    return Calendar.getInstance().time.time
}

fun strFormatToCalendar(end: String): Calendar {
    val calendar = Calendar.getInstance()

    try {
        val trimmed = end.trim()

        val sdf = when {
            // Full datetime: "2026-01-05 16:46:24"
            trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}")) -> {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            }
            // Time only: "16:30:05"
            trimmed.matches(Regex("\\d{2}:\\d{2}:\\d{2}")) -> {
                SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            }
            // Time without seconds: "16:30"
            trimmed.matches(Regex("\\d{2}:\\d{2}")) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            }

            else -> {
                Log.w("strFormatToCalendar", "Unknown format: $end")
                return calendar
            }
        }

        val date = sdf.parse(trimmed)
        if (date != null) {
            if (trimmed.contains("-")) {
                // Full date-time: set toàn bộ
                calendar.time = date
            } else {
                // Time only: chỉ set time, giữ nguyên date hiện tại
                val tempCal = Calendar.getInstance()
                tempCal.time = date

                calendar.set(Calendar.HOUR_OF_DAY, tempCal.get(Calendar.HOUR_OF_DAY))
                calendar.set(Calendar.MINUTE, tempCal.get(Calendar.MINUTE))
                calendar.set(Calendar.SECOND, tempCal.get(Calendar.SECOND))
                calendar.set(Calendar.MILLISECOND, 0)
            }
        }
    } catch (e: Exception) {
        Log.e("strFormatToCalendar", "Error parsing: $end", e)
    }

    return calendar
}



