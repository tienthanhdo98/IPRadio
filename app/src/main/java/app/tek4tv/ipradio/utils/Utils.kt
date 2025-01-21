package app.tek4tv.ipradio.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import androidx.core.content.ContextCompat
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.net.NetworkInterface
import java.util.*


class Utils {
    companion object {
        const val STORAGE_INFO = "STORAGE_INFO"
        const val ENCRYPTED_STORAGE_INFO  = "ENCRYPTED_STORAGE_INFO"
        const val ping = "Ping"
        const val heartbeat = "Heartbeat"
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

        //the loai
        fun convertContentType(strContentType: String): ContentType {
//            val contentType: ContentType =  when (strContentType) {
//                "1" -> {
//                    ContentType(1, StringValue.contentTypeId1)
//                }
//                "2" -> {
//                     ContentType(2, StringValue.contentTypeId2)
//                }
//                "3" -> {
//                    ContentType(3, StringValue.contentTypeId3)
//                }
//                "4" -> {
//                    ContentType(4, StringValue.contentTypeId4)
//                }
//                "5" -> {
//                    ContentType(5, StringValue.contentTypeId5)
//                }
//                "6" -> {
//                   ContentType(6, StringValue.contentTypeId6)
//                }
//                else -> {
//                   ContentType(1, StringValue.contentTypeId1)
//                }
//            }
            val contentType: ContentType =  when (strContentType) {
                "1" -> {
                    ContentType(1, "1")
                }
                "2" -> {
                    ContentType(2,  "2")
                }
                "3" -> {
                    ContentType(3,  "3")
                }
                "4" -> {
                    ContentType(4,  "4")
                }
                "5" -> {
                    ContentType(5,  "5")
                }
                "6" -> {
                    ContentType(6,  "6")
                }
                else -> {
                    ContentType(1,  "1")
                }
            }
            return contentType
        }

        //do uu tien
        fun convertPriority(priorityLevel: String) : Priority{
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
            val priority : Priority = when (priorityLevel) {
                "1" -> {
                    Priority(1,"1")

                }
                "2" -> {
                    Priority(2,"2")
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
        fun convertAuthor(tacGia : TacGia) : Author
        {
            return Author(
                fullName = tacGia.tenDayDu,
                userName = tacGia.butDanh,
                email = tacGia.email,
                )
        }
        fun getTypeOfMediaItem(contentTypeId : Int) : String
        {
            var type = "VOD"
            if (contentTypeId == 5) {
                type = "VOD"
            }
            if (contentTypeId == 6) {
                type = "FM"
            }
            return type

        }
        fun getDeviceInfo(valueName : String,deviceInfo  : String) : String
        {
            if(deviceInfo != "" && deviceInfo.contains("\$\$,1,8")) {
                var result = ""
                val listInfo = deviceInfo.split(",")
                when (valueName) {
                    StringValue.deviceInfoVolume -> {
                        result = listInfo[6]
                    }
                    StringValue.deviceInfoPA -> {
                        result = listInfo[8]
                    }
                    StringValue.deviceInfoTemperature -> {
                        result = listInfo[11]
                    }
                }
                return result
            }
            else
            {
                return  ""
            }
        }
    }

}