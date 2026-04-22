package app.tek4tv.ipradio.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import app.tek4tv.ipradio.di.JsonAdapterProvider.listMediaFileItemAdapter
import app.tek4tv.ipradio.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.net.NetworkInterface
import java.util.*


class Utils {
    companion object {
        const val DEVICE_ID = "device_id"


        fun convertSecondsToCalendar(seconds: Int): Calendar {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, seconds / 3600)
            cal.set(Calendar.MINUTE, (seconds % 3600) / 60)
            cal.set(Calendar.SECOND, seconds % 60)
            cal.set(Calendar.MILLISECOND, 0)
            return cal
        }


        @SuppressLint("HardwareIds")
        fun getDeviceId(context: Context): String {
            var deviceId: String? = ConfigUtil.getString(context, DEVICE_ID, null)
            if (deviceId == null) {
                deviceId = getMacAddr()
                ConfigUtil.putString(context, DEVICE_ID, deviceId)
            }
            return deviceId
        }

        fun getMacAddr(): String {
            try {
                val all: List<NetworkInterface> =
                    Collections.list(NetworkInterface.getNetworkInterfaces())
                for (nif in all) {
                    if (!nif.name.equals("wlan0", ignoreCase = true)) continue
                    val macBytes = nif.hardwareAddress
                    val res1 = StringBuilder()
                    for (b in macBytes) {
                        //res1.append(Integer.toHexString(b & 0xFF) + ":");
                        res1.append(String.format("%02X:", b))
                    }

                    if (res1.length > 0) {
                        res1.deleteCharAt(res1.length - 1)
                    }
                    return res1.toString()
                }
            } catch (ex: Exception) {
            }
            return "02:00:00:00:00:00"
        }


        //
        fun roundToNearestNumber(number: Int): Int {
            // Calculate remainder when dividing by 10
            val remainder = number % 10

            // Round down if remainder is less than or equal to 5, else round up
            return if (remainder <= 4) {
                (number - remainder) / 10
            } else {
                (number + (10 - remainder)) / 10
            }
        }

    }

}