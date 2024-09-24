package app.tek4tv.ipradio

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.telephony.CellSignalStrength
import android.util.Log
import app.tek4tv.ipradio.utils.Define
import app.tek4tv.ipradio.utils.network.NetworkUtils
import cn.wch.uartlib.WCHUARTManager
import dagger.hilt.android.HiltAndroidApp
import java.io.IOException
import java.nio.charset.StandardCharsets

@HiltAndroidApp
class MyApp : Application() {
    val TAG = "WUART"
    val wUart = WCHUARTManager.getInstance()
    var dataFinal = StringBuffer()
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this

//        wUart.init(this)
//        var list = wUart.enumDevice()
//        if (list.isNotEmpty()) {
//            list.forEach {
//                Log.d(TAG, "serialNumber: ${it.serialNumber}")
//                Log.d(TAG, "deviceName: ${it.deviceName}")
//                wUart.requestPermission(appContext, it)
//                val isOpenDeviceUsb = wUart.openDevice(it)
//                Log.d(TAG, "openDevice: ${isOpenDeviceUsb}")
//                val seriPort = wUart.getSerialCount(it)
//                Log.d(TAG, "getSerialCount: ${seriPort}")
//                val res = wUart.setSerialParameter(it, 0, 9600, 8, 1, 0, false)
//                Log.d(TAG, "setSerialParameter: ${res}")
//
//                GlobalScope.launch(Dispatchers.Main) {
//                    while (true) {
//                        onWatchDog(it)
//                        delay(3000)
//                    }
//                }
//            }
//
//        } else {
//            Log.d(TAG, "onCreate: null device")
//        }
    }

    private fun onWatchDog(device: UsbDevice) {
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
            if (onAir) {
                param = "2"
            }
            //Log.d(LOG_TAG, "status: $param")
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
            param = "$param,00:5F:0A:CE:08:1D,$networkClass,${strength}"
            val mes: String = buildWriteMessage(Define.FUNC_WRITE_WATCH_DOG, param)

//            val a = wUart!!.writeData(device, 0, mes.toByteArray(), mes.length, 5000)
            val a = wUart!!.writeData(device, 0, mes.toByteArray(), mes.length, 5000)
            Log.d(TAG, "writeData: ${mes.toByteArray()}")
            val readData = wUart.readData(device, 0)
            Log.d(TAG, "readData: ${readData}")
            if(readData != null) {
                var s = String(readData, StandardCharsets.UTF_8)
                Log.d(TAG, "readData decode: ${s}")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d(TAG, "writeData fail : ${e.message}")

        }
    }



    private fun buildWriteMessage(functionId: String, data: String): String {
        val s = "$$,$functionId,$data,\r\n"
        //Log.d(LOG_TAG, s)
        return s
    }

    companion object {
        lateinit var appContext: Context
        var instance: MyApp? = null
    }
}