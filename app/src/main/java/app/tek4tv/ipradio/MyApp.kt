package app.tek4tv.ipradio

import android.app.Application
import android.util.Log
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.utils.ConfigUtil
import app.tek4tv.ipradio.utils.storage.AppStorageManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application() {

    @Inject
    lateinit var authService: AuthService
    private val UART_S1_NAME = "/dev/ttyS1"
    private val UART_S4_NAME = "/dev/ttyS4"
    var appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        appStorageManager = AppStorageManager(applicationContext)
        hubManager = HubManager(applicationContext, authService)
        hubManager.connectToServer()
        hubManager.appStorageManager = appStorageManager

        serialPortController = SerialPortController(applicationContext, appScope, hubManager)
        serialPortController.connectToSerialPort(UART_S1_NAME)
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME, "tek4tv_quangngai")
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.PASSWORD,
            "4bfb2c4a-e049-4d21-86e0-4ffdf854b8a1"
        )
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME_KEY, "USERNAME")
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_TOKEN,
            "https://gateway-thongtinnguon.tek4tv.vn/api/v2/oauth/token"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_MQTT,
            "tcp://mqtt-digital-thongtinnguon.quangngai.gov.vn:1887"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_MQTT_INFO,
            "https://gateway-thongtinnguon.tek4tv.vn/v2/mqtt-digital/digital/mqtt/dangkythietbi"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_MQTT_SOURCE,
            "https://v2-provider-data-warehouse.tek4tv.vn/Source/"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_WSS,
            "wss://websocket-thongtinnguon.quangngai.gov.vn/ws/digital"
        )
        appScope.launch {
            delay(10000)
            if (!serialPortController.isUart1) {
                serialPortController.connectToSerialPort(UART_S4_NAME)
                Log.i("PA", "onCreate: s4")
            }
        }
    }


    companion object {
        var instance: MyApp? = null
        lateinit var hubManager: HubManager
        lateinit var serialPortController: SerialPortController
        lateinit var appStorageManager: AppStorageManager
    }
}