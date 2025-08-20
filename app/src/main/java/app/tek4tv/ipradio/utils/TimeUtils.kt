package app.tek4tv.ipradio.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*


//fun unixTimeStampToDateTime(unixTimeStamp: Int): LocalDateTime {
//    return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixTimeStamp.toLong()), ZoneId.systemDefault())
//}

fun convertToUnixTime(dateString: String, format: String): Long {
    val sdf = SimpleDateFormat(format, Locale.getDefault())
    val date = sdf.parse(dateString) // Parse the string into a Date object
    return date?.time ?: 0L // Convert Date to Unix time in milliseconds
}

fun unixTimeStampToDateTime(
    epochTime: Long,
    pattern: String = "dd-MM-yyyy",
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    // Convert epoch time to milliseconds
    val date = Date(epochTime * 1000)

    // Define a date formatter
    val formatter = SimpleDateFormat(pattern)
    formatter.timeZone = timeZone // Set the formatter's time zone

    // Format the date
    return formatter.format(date)
}

fun formatDateByFormat(date: Calendar, pattern: String = "dd-MM-yyyy"): String {
    val formatter = SimpleDateFormat(pattern, Locale.getDefault())
    return formatter.format(date.time)
}

fun convertTimeStampToHHMMSS(s: Long): String {
    return try {
        val sdf = SimpleDateFormat("HH:mm:ss")
        val netDate = Date(s * 1000)
        sdf.format(netDate)
    } catch (e: Exception) {
        e.toString()
    }
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



