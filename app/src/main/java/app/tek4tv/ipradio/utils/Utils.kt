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

        fun getRootDirPath(context: Context): String {
            return if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
                val file = ContextCompat.getExternalFilesDirs(
                    context.applicationContext,
                    null
                )[0]
                file.absolutePath
            } else {
                context.applicationContext.filesDir.absolutePath
            }
        }

        fun <T> toJsonString(moshi: Moshi, type: Class<T>, value: T): String {
            val adapter = moshi.adapter(type)
            return adapter.toJson(value)
        }

        fun toJsonList(moshi: Moshi, list: List<String>): String {
            val listType = Types.newParameterizedType(List::class.java, String::class.java)
            val adapter = moshi.adapter<List<String>>(listType)
            return adapter.toJson(list)
        }


        fun getDeviceInfo(valueName: String, deviceInfo: String?): String? {
            // Check if deviceInfo is null or empty
            if (deviceInfo.isNullOrEmpty()) {
                Log.d("debugLog", "getDeviceInfo: deviceInfo is null or empty")
                return null
            }
            // Check if deviceInfo contains expected pattern
            if (deviceInfo.contains("$$,1,8")) {
                val listInfo = deviceInfo.split(",")

                // Ensure the list has enough elements before accessing indexes
                if (listInfo.size < 12) {  // 12 because we're accessing index 11 later
                    Log.d("debugLog", "getDeviceInfo: Insufficient data in deviceInfo")
                    return null
                }

                val result = when (valueName) {
                    StringValue.deviceInfoVolume -> listInfo.getOrNull(6) ?: "0"
                    StringValue.deviceInfoPA -> listInfo.getOrNull(8) ?: "0"
                    StringValue.deviceInfoTemperature -> listInfo.getOrNull(11) ?: "0"
                    else -> null
                }
                Log.d("debugLog", "getDeviceInfo: Extracted result -> $result")
                return result
            } else {
                Log.d("debugLog", "getDeviceInfo: Pattern not found in deviceInfo $deviceInfo")
                return null
            }
        }

    }

}