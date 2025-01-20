package app.tek4tv.ipradio.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Base64
import android.util.Base64.encodeToString
import androidx.core.content.ContextCompat
import app.tek4tv.ipradio.model.TacGia
import app.tek4tv.ipradio.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface
import java.util.*

class Utils {
    companion object {
        const val DEVICE_ID = "device_id"
        const val FULL_SCREEN = "0"
        const val MULTIPLE_AREA = "1"
        const val TOP_AREA = "1"
        const val LEFT_AREA = "2"
        const val RIGHT_AREA = "3"
        //Device info
        const val deviceInfoPA = "PA"
        const val deviceInfoVolume = "Volume"
        const val deviceInfoTemperature = "Temperature"
        const val deviceCPU = "Other"


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
            } catch (_: Exception) {
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
                ( number + (10 - remainder)) / 10
            }
        }
        //the loai
        fun convertContentType(strContentType: String): ContentType {

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
        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            // Compress the bitmap to JPEG format, you can use PNG or WEBP as well
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            // Convert the byte array to a Base64 string
            return encodeToString(byteArray, Base64.NO_WRAP)
        }

        //do uu tien
        fun convertPriority(priorityLevel: String) : Priority{
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
            if(deviceInfo != "") {
                var result = ""
                val listInfo = deviceInfo.split(",")
                when (valueName) {
                    deviceInfoVolume -> {
                        result = listInfo[6]
                    }
                    deviceInfoPA -> {
                        result = listInfo[8]
                    }
                    deviceInfoTemperature -> {
                        result = listInfo[11]
                    }
                }
                return result
            } else {
                return ""
            }
        }
    }


}