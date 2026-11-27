package app.tek4tv.ipradio.core

import android.content.Context
import android.telephony.CellSignalStrength
import android.util.Log
import app.tek4tv.ipradio.utils.Define
import app.tek4tv.ipradio.utils.GlobalProperties
import app.tek4tv.ipradio.utils.Utils
import app.tek4tv.ipradio.utils.logger.DeviceLogHelper
import app.tek4tv.ipradio.utils.network.NetworkUtils
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class SerialPortController(
    private val appContext: Context,
    private val coroutineScope: CoroutineScope,
    private val hubManager: HubManager
) {
    private var serialPort: SerialPort? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val deviceMac = Utils.getDeviceId(appContext)

    private var isRead = false

    var dataFinal = StringBuffer()
    var playMode = ""
    var isUart1 = false

    var isPlayingProvider: (() -> Boolean)? = null
    var lastPipsData: String = ""
    var lastTunerData: String = ""

    fun connectToSerialPort(uart: String) {
        try {
            initSerialPort(uart)
            readDevice()
        } catch (e: Exception) {
            e.printStackTrace()
            DeviceLogHelper.logUartError(uart, e.message ?: "Không mở được cổng UART")
        }
    }

    @Throws(IOException::class)
    private fun initSerialPort(uart: String) {
        serialPort = SerialPort(File(uart), 9600, 0)

        inputStream = serialPort!!.inputStream
        outputStream = serialPort!!.outputStream

        coroutineScope.launch(Dispatchers.IO) {
            while (true) {
                updateStatus()
                delay(5000)
            }
        }

        if (serialPort != null && inputStream != null && outputStream != null) {
            coroutineScope.launch(Dispatchers.IO) {
                while (true) {
                    onWatchDog()
                    delay(3000)
                }
            }
        }
    }

    private fun onWatchDog() {
        val onAir: Boolean = NetworkUtils.isNetworkConnected(appContext)
        val isHubConnected = hubManager.isConnectSuccess
        val looseHubConnection = onAir && (!isHubConnected || hubManager.pingCounter >= 3)

        if (outputStream != null) {
            try {
                //0: không có mạng
                //1: có mạng, kết nối được đến hub
                //2: mất kết nối hub / chưa sẵn sàng
                var param = if (onAir) "1" else "0"
                if (looseHubConnection) {
                    param = "2"
                }

                val networkClass = NetworkUtils.getNetworkClass(appContext)
                val strength = when (networkClass) {
                    "2G", "3G", "4G" -> {
                        val cellStrength = NetworkUtils.getCellSignalStrengthInDbm(appContext)
                        if (cellStrength != CellSignalStrength.SIGNAL_STRENGTH_NONE_OR_UNKNOWN)
                            "${cellStrength}dbm"
                        else "undefine"
                    }

                    "WIFI" -> "${NetworkUtils.getWifiSignalStrength(appContext)}dbm"
                    else -> "---"
                }
                val data = "$param,$deviceMac,$networkClass,$strength"
                val mes: String = buildWriteMessage(Define.FUNC_WRITE_WATCH_DOG, data)

                outputStream!!.write(mes.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun buildWriteMessage(functionId: String, data: String): String {
        val s = "$$,$functionId,$data,\r\n"
        return s
    }

    private fun buildReadMessage(functionId: String, message: String): String {
        isRead = true
        val s = "$$,$functionId,$message,\r\n"
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
                            Log.i("serrialinfo", "readDevice: $s")
                            onDataReceived(s)
                            val list = s.replace("$$,", "").replace("\r\n", "").split(",")
                            if (s.contains("\$\$,1,8")) {
                                deviceInfo = s
                                volume = if (list.size > 5) list[5] else ""
                                val paState = if (list.size > 7) list[7] else ""

                                val isActuallyPlaying = isPlayingProvider?.invoke() ?: false
                                if (isActuallyPlaying) {
                                    // Tự động đồng bộ và kích hoạt lại PA/Volume nếu MCU đang báo tắt trong khi app đang phát
                                    if (paState == "0" || volume == "0") {
                                        syncPAState()
                                    }
                                } else {
                                    // Player ĐÃ DỪNG, nhưng MCU báo PA đang bật ("1") hoặc playMode chưa về MODE_NONE -> Cưỡng bức dập tắt PA ngay
                                    if (paState == "1" || (playMode != MODE_NONE && playMode.isNotEmpty())) {
                                        Log.w(
                                            LOG_TAG,
                                            "Player đã dừng nhưng PA vẫn mở (paState=$paState, mode=$playMode) -> Tắt PA an toàn"
                                        )
                                        turnOffPA()
                                    }
                                }
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

    private fun onDataReceived(data: String?) {
        // read data
        if (data == null) return
        if (data.endsWith("\r\n")) {
            if (data.isNotEmpty() && data.startsWith("$$,")) {
                if (isRead) {
                    isRead = false
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

    fun invokeDeviceFunc(funcId: String, data: String, isRead: Boolean = false) {
        if (isRead) {
            writeToDevice(buildReadMessage(funcId, data))
        } else {
            writeToDevice(buildWriteMessage(funcId, data))
        }
        Log.d(LOG_TAG, "invoke $funcId with $data, isRead: $isRead")
    }

    fun turnOffPA() {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_NO_SOURCE, "", true)
        playMode = MODE_NONE
        Log.e("PA", "OFF")
    }

    fun turnOnPAVod() {
        val vol = GlobalProperties.getInst().volume.takeIf { it.isNotEmpty() && it != "0" } ?: "100"
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_VOD_LIVE, vol)
        playMode = MODE_VOD_LIVE
        Log.e("PA", "ON with volume: $vol")
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
        turnOffPA()
    }

    fun setTimeOn(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_VOD_LIVE, data, true)
        playMode = MODE_VOD_LIVE
    }

    fun setUpPiPsName(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_SETUP_PI_PS_NAME, data)
    }

    fun readDeviceInfo() {
        invokeDeviceFunc(Define.FUNC_WRITE_READ_DEVICE_INFO, "", true)
    }

    fun writePlayVodLiveForce(data: String) {
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_VOD_LIVE_FORCE, data)
        playMode = MODE_VOD_LIVE
    }

    fun playTunerLiveForce(data: String) {
        lastTunerData = data
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_TUNER_FORCE, data)
        playMode = MODE_TUNER
        Log.e("PA", "TUNER FORCE")
    }

    fun playTuner(pipsData: String, tunerData: String) {
        lastPipsData = pipsData
        lastTunerData = tunerData
        invokeDeviceFunc(Define.FUNC_WRITE_SETUP_PI_PS_NAME, pipsData, true)
        invokeDeviceFunc(Define.FUNC_WRITE_PLAY_TUNER, tunerData)
        playMode = MODE_TUNER
        Log.e("PA", "TUNER")
    }

    fun syncPAState() {
        val isActuallyPlaying = isPlayingProvider?.invoke() ?: false
        if (!isActuallyPlaying) {
            // Player đã dừng phát: đảm bảo dập tắt PA nếu trước đó đang bật
            if (playMode != MODE_NONE && playMode.isNotEmpty()) {
                turnOffPA()
            }
            return
        }

        when (playMode) {
            MODE_VOD_LIVE -> {
                Log.w(
                    LOG_TAG,
                    "Đang phát VOD/Live nhưng MCU báo PA/Volume tắt -> Kích hoạt lại PA & Volume"
                )
                turnOnPAVod()
            }

            MODE_TUNER -> {
                Log.w(
                    LOG_TAG,
                    "Đang phát Tuner FM/AM nhưng MCU báo PA/Volume tắt -> Kích hoạt lại Tuner PA & Volume"
                )
                if (lastPipsData.isNotEmpty() && lastTunerData.isNotEmpty()) {
                    playTuner(lastPipsData, lastTunerData)
                }
            }
        }
    }

    companion object {
        const val MODE_VOD_LIVE = "MODE_VOD_LIVE"
        const val MODE_TUNER = "MODE_TUNER"
        const val MODE_NONE = "MODE_NONE"
        const val LOG_TAG = "SerialPortController"
        var volume: String = ""
        var deviceInfo: String = ""
    }
}
