package app.tek4tv.ipradio

import android.app.Application
import android.util.Log
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.core.players_helper.PlayersManager
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
        ConfigUtil.putString(applicationContext, ConfigUtil.USERNAME, "tek4tv_hanam")
        ConfigUtil.putString(
            applicationContext,
            ConfigUtil.PASSWORD,
            "86229b68-b40a-48ed-a708-c69665d660c8"
        )
        ConfigUtil.putString(applicationContext, ConfigUtil.USERNAME_KEY, "USERNAME")
        ConfigUtil.putString(applicationContext, ConfigUtil.PASSWORD_KEY, "PASSWORD")
        ConfigUtil.putString(
            applicationContext,
            ConfigUtil.URL_TOKEN,
            "https://gateway-thongtinnguon.hanam.gov.vn/api/v2/oauth/token"
        )
        ConfigUtil.putString(
            applicationContext,
            ConfigUtil.URL_TOKEN,
            "https://gateway-thongtinnguon.hanam.gov.vn/api/v2/oauth/token"
        )
        ConfigUtil.putString(
            applicationContext,
            ConfigUtil.URL_MQTT,
            "tcp://mqtt-digital-thongtinnguon.hanam.gov.vn:1884"
        )
        ConfigUtil.putString(
            applicationContext,
            ConfigUtil.URL_MQTT_INFO,
            "https://gateway-thongtinnguon.hanam.gov.vn/v2/mqtt-digital/digital/mqtt/dangkythietbi"
        )
        ConfigUtil.putString(
            applicationContext,
            ConfigUtil.URL_MQTT_SOURCE,
            "https://v2-provider-data-warehouse.tek4tv.vn/Source/"
        )
        ConfigUtil.putString(
            applicationContext,
            ConfigUtil.URL_WSS,
            "wss://websocket-thongtinnguon.hanam.gov.vn/ws/digital"
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
        lateinit var playerManager: PlayersManager
    }
}