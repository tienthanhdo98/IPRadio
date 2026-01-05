package app.tek4tv.ipradio.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


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

//fun strFormatToCalendar(end: String): Calendar {
//    return try {
//        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
//        val date = sdf.parse(end)
//
//        Calendar.getInstance().apply {
//            if (date != null) {
//                time = date
//            }
//        }
//    } catch (e: Exception) {
//        Log.d("strFormatToCalendar", "Error parsing date: ${e.message}")
//        Calendar.getInstance()
//    }
//}
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

fun getCurrentTime(): Long {
    return Calendar.getInstance().time.time
}



