package app.tek4tv.ipradio.core

import android.content.Context
import android.telephony.CellSignalStrength
import android.util.Log
import app.tek4tv.ipradio.utils.Define
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.network.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class SerialPortController(
    private val appContext: Context,
    private val coroutineScope: CoroutineScope,
    private val hubManager: HubManager
) {
    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val deviceMac = Utils.getMacAddr()


    var dataFinal = StringBuffer()
    var playMode = ""
    var isUart1 = false

    fun connectToSerialPort(uart: String) {
        try {
            initSerialPort(uart)
            readDevice()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun initSerialPort(uart: String) {
        serialPort = SerialPort(File(uart), 9600, 0)

        inputStream = serialPort!!.inputStream
        outputStream = serialPort!!.outputStream

//        coroutineScope.launch(Dispatchers.IO) {
//            while (true) {
//                updateStatus()
//                delay(5000)
//            }
//        }
//
//
//        if (serialPort != null && inputStream != null && outputStream != null) {
//            coroutineScope.launch(Dispatchers.IO) {
//                while (true) {
//                    onWatchDog()
//                    delay(5000)
//                }
//            }
//        }

        coroutineScope.launch(Dispatchers.IO) {
            var watchdogTick = 0
            var statusTick = 0

            while (isActive) {
                if (watchdogTick >= 5 && serialPort != null && inputStream != null && outputStream != null) {
                    onWatchDog()
                    watchdogTick = 0
                }

                if (statusTick >= 15) {
                    updateStatus()
                    statusTick = 0
                }

                delay(1000)
                watchdogTick++
                statusTick++
            }
        }
    }

    private fun onWatchDog() {
        val looseHubConnection = hubManager.pingCounter >= 3

        if (outputStream != null) {

            //$$,1,8,1,910,675,0,0,0,1,0,25
            //0: not internet, 1: ok , 2: internet, disconnected
            //$$,1,2,0: normal(1: be restarted neet to jump to realtime)
            //$$,1,8,1,910,675,5,0,0,1,0,28: 1: fm/am, fm freq, am freq, vol, audio source, pa, mute/unmute, external mic, tempareture
            //$$,1,9,software version, device versionnải
            try {
                val onAir: Boolean = NetworkUtils.isNetworkConnected(appContext)
                //0: không có mạng
                //1: có mạng, kết nối được đến hub
                //2: không xác định
                var param = if (onAir) "1" else "0"
                if (onAir && looseHubConnection) {
                    param = "2"
                }
                //Log.d(LOG_TAG, "status: $param")
                val networkClass = NetworkUtils.getNetworkClass(appContext)
                val strength = when (networkClass) {
                    "2G", "3G", "4G" -> {
                        val cellStrength = NetworkUtils.getCellSignalStrengthInDbm(appContext)
                        if (cellStrength != CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN) "${cellStrength}dbm"
                        else "undefine"
                    }

                    "WIFI" -> "${NetworkUtils.getWifiSignalStrength(appContext)}dbm"
                    else -> "---"
                }
                param = "$param,$deviceMac,$networkClass,${strength}"
                val mes: String = buildWriteMessage(Define.FUNC_WRITE_WATCH_DOG, param)

                outputStream!!.write(mes.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }


    private fun buildWriteMessage(functionId: String, data: String): String {
        val s = "$$,$functionId,$data,\r\n"
        Log.d(LOG_TAG, s)
        return s
    }

    private fun buildReadMessage(functionId: String, message: String): String {
        val s = "$$,$functionId,$message,\r\n"
        Log.d(LOG_TAG, s)
        return s
    }

    private fun readDevice() {
        coroutineScope.launch(Dispatchers.IO) {
            while (!Thread.currentThread().isInterrupted) {
                var size: Int
                try {
                    val buffer = ByteArray(64)
                    if (inputStream == null) return@launch
                    size = inputStream!!.read(buffer)
                    isUart1 = true
                    if (size > 0) {
                        val data = String(buffer, 0, size)
                        dataFinal.append(data)
                        if (dataFinal.toString().endsWith("\r\n")) {
                            val s: String = dataFinal.toString()
                            Log.i(LOG_TAG, "readDevice: $s")
                            if (s.startsWith("\$\$,1,8")) {
                                deviceInfo = s
                            }
                            dataFinal = StringBuffer()
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    return@launch
                }
            }
        }

    }


    private fun writeToDevice(message: String) {
        if (outputStream != null) {
            try {
                outputStream!!.write(message.toByteArray())
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
    }

    fun turnOffPA() {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_NO_SOURCE, "", true)
        playMode = MODE_NONE
        Log.e("PA", "OFF")
    }

    fun turnOnPAVod() {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_VOD_LIVE, GlobalProperties.getInst().mainVolume)
//        playMode = MODE_VOD_LIVE
        Log.e("PA", "ON")
    }

    fun writeForceSetVolume(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_FORCE_SET_VOLUME, data)
    }

    fun updateStatus() {
        invokeDeviceFunc(Define.FUNC_WRITE_READ_STATUS_PARAM, "", true)
    }

    fun hardRestart() {
        invokeDeviceFunc(Define.FUNC_WRITE_FORCE_RESTART_DEVICE, "", true)
    }

    fun setMuteDevice(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_FORCE_SET_MUTE, data)
    }

    fun setTimeOver() {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_NO_SOURCE, "", true)
    }

    fun setTimeOn(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_VOD_LIVE, data, true)
//        playMode = MODE_VOD_LIVE
    }

    fun setUpPiPsName(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_SETUP_PI_PS_NAME, data)
    }

    fun readDeviceInfo() {
        invokeDeviceFunc(Define.FUNC_WRITE_READ_DEVICE_INFO, "", true)
    }

    fun writePlayVodLiveForce(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_VOD_LIVE_FORCE, data)
//        playMode = MODE_VOD_LIVE
    }

    fun playTunerLiveForce(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_TUNER_FORCE, data)
    }

    fun playTuner(pipsData: String, tunerData: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_SETUP_PI_PS_NAME, pipsData, true)
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_TUNER, tunerData)
//        playMode = MODE_TUNER
        Log.e("PA", "TUNER")
    }

    fun writeVolumeTuner(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_VOLUME_TUNER, data)
    }


    //Tuner threshold
    fun writeRSSI(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_RSSI, data)
    }

    //Switch Esim
    fun writeSetESimMode(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_P4_0, data)
    }

    //    $$,3,0 Không chọn kênh
//    $$,3,1 Chọn kênh 1
//    $$,3,2 Chọn kênh 2
//    $$,3,3 Chọn kênh 3
//    $$,3,4 Chọn kênh 4
    fun streamBack(speakerPos: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_STREAM_BACK, speakerPos, true)
    }

    //    $$,2,0,0 Mute cả 2 kênh
//    $$,2,0,1 Mute Left, Unmute Right
//    $$,2,1,0 UnMute Left, Mute Right
//    $$,2,1,1 Unmute cả 2 kênh
    fun setMuteSpeak(leftSpeak: String, rightSpeak: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_STREAM_BACK, "$leftSpeak,$rightSpeak", true)
    }

    fun invokeDeviceFunc(funcId: String, data: String, isRead: Boolean = false) {
        if (isRead) {
            writeToDevice(buildReadMessage(funcId, data))
        } else {
            writeToDevice(buildWriteMessage(funcId, data))
        }
        Log.d(LOG_TAG, "invoke $funcId with $data, isRead: $isRead")
    }

    companion object {
        const val MODE_VOD_LIVE = "MODE_VOD_LIVE"
        const val MODE_TUNER = "MODE_TUNER"
        const val MODE_NONE = "MODE_NONE"
        const val LOG_TAG = "SerialPortController"
        var deviceInfo: String = ""
    }
}