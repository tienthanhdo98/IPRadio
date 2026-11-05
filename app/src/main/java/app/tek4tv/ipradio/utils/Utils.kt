package app.tek4tv.ipradio.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.TrafficStats
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Base64.encodeToString
import android.util.Log
import androidx.core.content.ContextCompat
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import java.util.*
import android.util.Base64
import app.tek4tv.ipradio.model.*
import com.squareup.moshi.*


class Utils {
    companion object {
        const val STORAGE_INFO = "STORAGE_INFO"
        const val ENCRYPTED_STORAGE_INFO = "ENCRYPTED_STORAGE_INFO"
        const val ping = "Ping"
        const val DEVICE_ID = "device_id"
        const val APP_VERSION = "APP_VERSION"
        const val NETWORK_INFO = "NETWORK_INFO"
        const val DEVICE_LOCATION = "DEVICE_LOCATION"
        const val DIRECT_MESSAGE = "DirectMessage"
        const val DEVICE_INFO = "DEVICE_INFO"
        const val GET_AUDIO_PATH = "GET_AUDIO_PATH"
        const val GET_MEDIA_PATH = "GET_MEDIA_PATH"
        const val GET_RECORD = "GET_RECORD"


        @SuppressLint("HardwareIds")
        fun getDeviceId(context: Context): String {
//            var deviceId: String? = ConfigUtil.getString(context, DEVICE_ID, null)
//            if (deviceId == null) {
//                deviceId = getMacAddr()
//                ConfigUtil.putString(context, DEVICE_ID, deviceId)
//            }
//            return deviceId
            return "54:78:C9:B8:0F:54"
        }

        fun getMacAddr(): String {
            try {
                val all: List<NetworkInterface> =
                    Collections.list(NetworkInterface.getNetworkInterfaces())
                for (nif in all) {
                    if (!nif.name.equals("wlan0", ignoreCase = true)) continue
                    val macBytes = nif.hardwareAddress ?: return ""
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


        fun measureStreamingBandwidth(intervalMs: Long = 1000L) {
            val uid = Process.myUid()
            val handler = Handler(Looper.getMainLooper())

            var prevBytes = TrafficStats.getUidRxBytes(uid)
            var prevTime = System.currentTimeMillis()

            val runnable = object : Runnable {
                override fun run() {
                    val currBytes = TrafficStats.getUidRxBytes(uid)
                    val currTime = System.currentTimeMillis()

                    val bytesDiff = currBytes - prevBytes
                    val timeDiffSec = (currTime - prevTime) / 1000.0
                    val bandwidthBps = (bytesDiff * 8) / timeDiffSec // bits/sec
                    val bandwidthKbps = bandwidthBps / 1024

                    Log.d("LOGDEVICEINFO", "Download: %.2f Kbps".format(bandwidthKbps))

                    prevBytes = currBytes
                    prevTime = currTime

                    handler.postDelayed(this, intervalMs)
                }
            }

            handler.post(runnable)
        }

        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            // Compress the bitmap to JPEG format, you can use PNG or WEBP as well
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            // Convert the byte array to a Base64 string
            return encodeToString(byteArray, Base64.NO_WRAP)
        }


    }
}