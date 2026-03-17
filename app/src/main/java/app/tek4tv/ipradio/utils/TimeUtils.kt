package app.tek4tv.ipradio.utils

import android.util.Log
import app.tek4tv.ipradio.model.MediaInfo
import app.tek4tv.ipradio.model.RepeatDays
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs


//fun unixTimeStampToDateTime(unixTimeStamp: Int): LocalDateTime {
//    return LocalDateTime.ofInstant(Instant.ofEpochSecond(unixTimeStamp.toLong()), ZoneId.systemDefault())
//}
val sdf = SimpleDateFormat("HH:mm:ss")

fun unixSecondsToCalendar(
    unixSeconds: Long, tz: TimeZone = TimeZone.getDefault()
): Calendar {
    return Calendar.getInstance(tz).apply {
        timeInMillis = unixSeconds * 1000L
    }
}


fun todayWeeklyIndex(now: Calendar): Int {
    val dow = now.get(Calendar.DAY_OF_WEEK) // 1..7 (Sun..Sat)
    return when (dow) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        Calendar.SUNDAY -> 6
        else -> 0
    }
}

fun repeatDaysToText(result: RepeatDays): String = when (result) {
    is RepeatDays.Weekly -> {
        val names = listOf("Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "CN")
        result.weekdays.joinToString(", ") { names[it] }
    }

    is RepeatDays.Monthly -> {
        result.daysOfMonth.joinToString(", ") { "Day $it" }
    }
}

fun decodeRepeatDays(repeat: Int, daysMask: Int): RepeatDays {
    return when (repeat) {
        0 -> { // weekly: bit 0..6
            val weekdays = (0..6).filter { bit ->
                (daysMask and (1 shl bit)) != 0
            }
            RepeatDays.Weekly(weekdays)
        }

        1 -> { // monthly: bit 0..30 => day 1..31
            val dom = (1..31).filter { day ->
                val bit = day - 1
                (daysMask and (1 shl bit)) != 0
            }
            RepeatDays.Monthly(dom)
        }

        else -> {
            // nếu repeat lạ thì coi như không chọn ngày nào
            RepeatDays.Weekly(emptyList())
        }
    }
}

fun isMonthlyDaySelected(days: Int, dayOfMonth: Int): Boolean {
    return (days and (1 shl (dayOfMonth - 1))) != 0
}

fun isWeeklyDaySelected(daysMask: Int, weekdayIndex: Int): Boolean {
    if (weekdayIndex !in 0..6) return false
    val mask = 1 shl weekdayIndex
    return (daysMask and mask) != 0
}

fun isTodayAllowed(now: Calendar, result: RepeatDays): Boolean = when (result) {
    is RepeatDays.Weekly -> {
        val idx = todayWeeklyIndex(now)
        idx in result.weekdays
    }

    is RepeatDays.Monthly -> {
        val dom = now.get(Calendar.DAY_OF_MONTH)
        dom in result.daysOfMonth
    }
}

fun convertCalendarToString(
    calendar: Calendar, format: String = "dd/MM/yyyy HH:mm:ss"
): String {
    // 1. Khởi tạo công cụ định dạng với múi giờ/ngôn ngữ hệ thống
    val sdf = SimpleDateFormat(format, Locale.getDefault())

    // 2. Lấy đối tượng Date từ Calendar và chuyển sang String
    return sdf.format(calendar.time)
}


fun unixTimeStampToDateTime(
    epochTime: Long?,
    pattern: String = "dd-MM-yyyy",
    timeZone: TimeZone = TimeZone.getDefault()
): String {
    if (epochTime == null) return ""

    // nếu > 10^12 thì coi là milliseconds, còn lại là seconds
    val millis = if (epochTime >= 1_000_000_000_000L) epochTime else epochTime * 1000L

    val formatter = SimpleDateFormat(pattern, Locale.getDefault()).apply {
        this.timeZone = timeZone
    }
    return formatter.format(Date(millis))
}


fun convertTimeStampToHHMMSS(s: Long?): String {
    return try {
        if (s == null) return ""

        val millis = if (s >= 1_000_000_000_000L) s else s * 1000L
        val date = Date(millis)

        // nếu sdf là biến global, nhớ set timezone/locale khi khởi tạo
        sdf.format(date)
    } catch (e: Exception) {
        Log.d("convertTimeStampToHHMMSS", "Error: ${e.message} - value: $s")
        ""
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

fun checkTime() {
    SNTPClient.getDate(Calendar.getInstance().timeZone, object : SNTPClient.Listener {
        override fun onTimeReceived(rawDate: String?) {
            if (rawDate == null) return

            try {
                val receiveFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                val receivedDate = receiveFormat.parse(rawDate)

                if (abs(receivedDate.time - Date().time) > 30000) {

                    Log.d("Time", "Sync")

                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                    setSystemTime(dateFormat.format(receivedDate))

                    // Uncomment 2 below line be4 use this method

                    //cancelAllAlarm()

                    //CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onError(ex: java.lang.Exception?) {
            ex?.printStackTrace()
        }
    })
}

