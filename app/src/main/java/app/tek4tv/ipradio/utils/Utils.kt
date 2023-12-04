package app.tek4tv.ipradio.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.tek4tv.ipradio.core.players_helper.PlayersManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.NetworkInterface
import java.util.*
import kotlin.reflect.jvm.internal.impl.types.DynamicType

class Utils {
    companion object {
        const val STORAGE_INFO = "STORAGE_INFO"
        const val ENCRYPTED_STORAGE_INFO  = "ENCRYPTED_STORAGE_INFO"
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
        fun checkIsItemIsAvailable(list : List<String>, index: Int) : Boolean {
            return (index + 1) < list.size
        }
        fun convertFileToBase64(path : String) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val file = File(path)
                    val a = fileToBase64(file)
                    Log.d("readplaylist", "value2 ${a} ")

                } catch (e: IOException) {
                    Log.e("readplaylist", "error reading playlist!!!")
                }
            }
        }
        fun fileToBase64(file: File): String? {
            try {
                val inputStream = FileInputStream(file)
                val byteArray = ByteArray(file.length().toInt())
                inputStream.read(byteArray)
                inputStream.close()
                // Convert the byte array to Base64
                return Base64.encodeToString(byteArray, Base64.DEFAULT)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
        }
         fun sendSms(phoneNumber: String, message: String,context : Context) {
            val smsManager = SmsManager.getDefault()

            // If the message is longer than 160 characters, divide it into parts
            val parts = smsManager.divideMessage(message)

            // Create a PendingIntent to be called back when the SMS has been sent
            val sentIntent = PendingIntent.getBroadcast( context, 0,Intent("SMS_SENT"), 0)

            // Create a PendingIntent to be called back when the SMS has been delivered
            val deliveredIntent = PendingIntent.getBroadcast(context, 0, Intent("SMS_DELIVERED"), 0)

            // Send the SMS
            for (part in parts) {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    part,
                    sentIntent,
                    deliveredIntent
                )
            }
        }
    }
}