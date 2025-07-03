package app.tek4tv.ipradio.utils


import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import kotlin.math.abs

object TimeUtils {


    val DATE_FORMATTER = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())


    @RequiresApi(Build.VERSION_CODES.O)
    fun unixTimeStampToDateTime(unixTimeStamp: Int): LocalDateTime {
        return LocalDateTime.ofInstant(
            Instant.ofEpochSecond(unixTimeStamp.toLong()),
            ZoneId.systemDefault()
        )
    }

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


    fun convertTimeStampToHHMMSS(s: Long): String {
        return try {
            val sdf = SimpleDateFormat("HH:mm:ss")
            val netDate = Date(s.toLong() * 1000)
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
            Log.d("toCalendar() error", "${e.message}")
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
            Log.d("getDurationInSecond", "getDurationInSecond: ${e.message}")
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
}

