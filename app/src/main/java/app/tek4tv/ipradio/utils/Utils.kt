package app.tek4tv.ipradio.utils

import app.tek4tv.ipradio.di.JsonAdapterProvider.jsonListMediaItemAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import app.tek4tv.ipradio.model.TacGia
import app.tek4tv.ipradio.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.net.NetworkInterface
import java.util.*

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
        const val FULL_SCREEN = "0"
        const val MULTIPLE_AREA = "1"
        const val POTRAIT_FULL = "2"
        const val POTRAIT_SPLIT = "3"
        const val TOP_AREA = "1"
        const val LEFT_AREA = "2"
        const val RIGHT_AREA = "3"

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

        fun saveListForceToPref(appContext: Context, jsonListMedia: String) {
            ConfigUtil.run {
                putBool(appContext, HAVE_FORCE_MEDIA, true)
                putString(appContext, FORCE_MEDIA, jsonListMedia)
            }
        }

        fun getLocalForceMedia(appContext: Context): List<MediaItem>? {
            val value = ConfigUtil.getString(appContext, ConfigUtil.FORCE_MEDIA, "")
            if (value == null || value == "") return null
            return jsonListMediaItemAdapter.fromJson(value)
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

        //the loai
        fun convertContentType(strContentType: String): ContentType {

            val contentType: ContentType = when (strContentType) {
                "1" -> {
                    ContentType(1, "1")
                }
                "2" -> {
                    ContentType(2, "2")
                }
                "3" -> {
                    ContentType(3, "3")
                }
                "4" -> {
                    ContentType(4, "4")
                }
                "5" -> {
                    ContentType(5, "5")
                }
                "6" -> {
                    ContentType(6, "6")
                }
                else -> {
                    ContentType(1, "1")
                }
            }
            return contentType
        }

        //do uu tien
        fun convertPriority(priorityLevel: String): Priority {
//            val priority : Priority = when (priorityLevel) {
//                "1" -> {
//                    Priority(1,StringValue.priority1)
//
//                }
//                "2" -> {
//                    Priority(2,StringValue.priority2)
//                }
//                "3" -> {
//                    Priority(3, StringValue.priority3)
//                }
//                else -> {
//                    Priority(1, StringValue.priority1)
//                }
//            }
            val priority: Priority = when (priorityLevel) {
                "1" -> {
                    Priority(1, "1")

                }
                "2" -> {
                    Priority(2, "2")
                }
                "3" -> {
                    Priority(3, "3")
                }
                else -> {
                    Priority(1, "1")
                }
            }
            return priority
        }

        //linh vuc
        fun convertFiend(fieldType: String): Category {
            val category: Category = when (fieldType) {
                "1" -> {
                    Category(1, "1")

                }
                "2" -> {
                    Category(2, "2")
                }
                "3" -> {
                    Category(3, "3")
                }
                "4" -> {
                    Category(3, "4")
                }
                "5" -> {
                    Category(3, "5")
                }
                "6" -> {
                    Category(3, "6")
                }
                "7" -> {
                    Category(3, "7")
                }
                else -> {
                    Category(1, "3")
                }
            }
            return category
        }

        fun convertAuthor(tacGia: TacGia): Author {
            return Author(
                fullName = tacGia.tenDayDu,
                userName = tacGia.butDanh,
                email = tacGia.email,
            )
        }

        fun getTypeOfMediaItem(contentTypeId: Int): String {
            var type = "VOD"
            if (contentTypeId == 5) {
                type = "VOD"
            }
            if (contentTypeId == 6) {
                type = "FM"
            }
            return type

        }

        fun getDeviceInfo(valueName: String, deviceInfo: String?): String {
            // Check if deviceInfo is null or empty
            if (deviceInfo.isNullOrEmpty()) {
                return ""
            }
            // Check if deviceInfo contains expected pattern
            if (deviceInfo.contains("\$\$,1,8")) {
                val listInfo = deviceInfo.split(",")

                // Ensure the list has enough elements before accessing indexes
                if (listInfo.size < 12) {  // max value device is 12
                    return ""
                }

                val result = when (valueName) {
                    StringValue.deviceInfoVolume -> listInfo.getOrNull(6) ?: ""
                    StringValue.deviceInfoPA -> listInfo.getOrNull(8) ?: ""
                    StringValue.deviceInfoTemperature -> listInfo.getOrNull(11) ?: ""
                    else -> ""
                }
                return result
            } else {
                return ""
            }
        }

        fun bytesToBinaryGB(bytes: Long): Double {
            // 1 Binary GB = 1024^3 bytes
            val divisor = 1024.0 * 1024.0 * 1024.0  // or 1073741824.0
            return bytes / divisor
        }

        fun bytesToDecimalGB(bytes: Long): Double {
            // 1 Decimal GB = 1,000,000,000 bytes
            val divisor = 1_000_000_000.0
            return bytes / divisor
        }
    }
}