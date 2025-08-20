package app.tek4tv.ipradio.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Base64
import android.util.Base64.encodeToString
import android.util.Log
import androidx.core.content.ContextCompat
import app.tek4tv.ipradio.di.JsonAdapterProvider.adapterThongTinKhac
import app.tek4tv.ipradio.model.*
import app.tek4tv.ipradio.ui.MainActivity
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import com.squareup.moshi.*
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
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

        fun bitmapToBase64(bitmap: Bitmap): String {
            val outputStream = ByteArrayOutputStream()
            // Compress the bitmap to JPEG format, you can use PNG or WEBP as well
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            // Convert the byte array to a Base64 string
            return encodeToString(byteArray, Base64.NO_WRAP)
        }

        //do uu tien
        fun convertPriority(priorityLevel: String): Priority {
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

        fun roundNumber(value: Double): Double {
            return String.format("%.2f", value).toDouble()
        }

        fun getMemoryUsage(): String {
            val usedRam = roundNumber(MainActivity.appStorageManager.getUsedRam())
            val totalRam = roundNumber(MainActivity.appStorageManager.getTotalRam())
            return "${usedRam}/${totalRam}"
        }

        fun getCpuUsage(): String {
            try {
                val reader = RandomAccessFile("/proc/stat", "r")

                // Đọc lần 1
                val load1 = reader.readLine()
                val toks1 = load1.split(" +".toRegex()).drop(1)
                val idle1 = toks1[3].toLong()
                val total1 = toks1.take(7).map { it.toLong() }.sum()

                // Chờ 360ms để lấy mẫu tiếp theo
                Thread.sleep(360)

                // Đọc lại từ đầu file
                reader.seek(0)
                val load2 = reader.readLine()
                val toks2 = load2.split(" +".toRegex()).drop(1)
                val idle2 = toks2[3].toLong()
                val total2 = toks2.take(7).map { it.toLong() }.sum()

                reader.close()

                val totalDiff = total2 - total1
                val idleDiff = idle2 - idle1
                val usedCpu = (totalDiff - idleDiff).toFloat() / totalDiff.toFloat() * 100f
                val res = String.format("%.2f%%/100%%", usedCpu)
                // Format thành chuỗi như yêu cầu
                Log.d("getSystemInfo", "getCpuInfo: $res")
                return res
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return "N/A"
        }

        fun getOtherInfo(deviceInfo: String, appStorageManager: AppStorageManager): String {
            val usedStorageGb = roundNumber(
                appStorageManager.getUsedRomStorage().toDouble() / 1_073_741_824
            ) // 1024^3
            val totalStorageGb =
                roundNumber(appStorageManager.getTotalRomStorage().toDouble() / 1_073_741_824)

            val infoList = listOf(
                ThongTinKhac("deviceInfo", deviceInfo),
                ThongTinKhac("cpu", getCpuUsage()),
                ThongTinKhac("memory", getMemoryUsage()),
                ThongTinKhac("storage", "$usedStorageGb/$totalStorageGb")
            )

            return adapterThongTinKhac.toJson(infoList)
        }

        fun parseJson(jsonMessage: String, moshi: Moshi): MQTTReceivedScheduleModel? {
            val mqttReceivedScheduleModelAdapter: JsonAdapter<MQTTReceivedScheduleModel> =
                moshi.adapter(MQTTReceivedScheduleModel::class.java)
            return try {
                // Attempt to parse the JSON message
                mqttReceivedScheduleModelAdapter.fromJson(jsonMessage)
            } catch (e: JsonDataException) {
                // Handle errors related to data mismatch
                println("Error: Malformed JSON structure or unexpected data type - ${e.message}")
                null
            } catch (e: JsonEncodingException) {
                // Handle errors related to encoding issues (e.g., missing brackets, etc.)
                println("Error: Malformed JSON encoding - ${e.message}")
                null
            } catch (e: Exception) {
                // Handle any other unexpected exceptions
                println("Error: Unexpected error during JSON parsing - ${e.message}")
                null
            }
        }
    }


}