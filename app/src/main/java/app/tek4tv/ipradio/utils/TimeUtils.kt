package app.tek4tv.ipradio.utils

import android.util.Log
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs


fun toCalendar(time: String): Calendar {
    return try {
        Calendar.getInstance().apply {
            val t = time.split(":").map { it.toInt() }
            set(Calendar.HOUR_OF_DAY, t[0])
            set(Calendar.MINUTE, t[1])
            set(Calendar.SECOND, t[2])
        }
    } catch (e: Exception) {
        Log.d("toCalendar()", e.message)
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

fun checkTime()
{
    SNTPClient.getDate(Calendar.getInstance().timeZone, object : SNTPClient.Listener
    {
        override fun onTimeReceived(rawDate: String?) {
            if(rawDate == null) return

            try {
                val receiveFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
                val receivedDate = receiveFormat.parse(rawDate)

                if(abs(receivedDate.time - Date().time) > 30000)
                {

                    Log.d("Time","Sync")

                    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                    setSystemTime(dateFormat.format(receivedDate))

                    // Uncomment 2 below line be4 use this method

                    //cancelAllAlarm()

                    //CrashHandler.restartApp(MyApp.instance!!, this@MainActivity)
                }
            }
            catch (e : Exception)
            {
                e.printStackTrace()
            }
        }

        override fun onError(ex: java.lang.Exception?) {
            ex?.printStackTrace()
        }
    })
}

