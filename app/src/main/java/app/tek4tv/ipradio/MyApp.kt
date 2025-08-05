package app.tek4tv.ipradio

import android.app.Application
import android.util.Log
import app.tek4tv.ipradio.core.HubManager
import app.tek4tv.ipradio.core.SerialPortController
import app.tek4tv.ipradio.network.AuthService
import app.tek4tv.ipradio.utils.ConfigUtil
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
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME, "device")
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.PASSWORD, "Device@123")
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.USERNAME_KEY, "Username")
        ConfigUtil.putStringEncrypt(applicationContext, ConfigUtil.PASSWORD_KEY, "Password")
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_TOKEN,
            "https://oauth-device.tek4tv.vn/api/auth/login"
        )
        ConfigUtil.putStringEncrypt(
            applicationContext,
            ConfigUtil.URL_HUB,
            "https://hubdi-ninhbinh.tek4tv.vn/hubServer"
        )
        hubManager = HubManager(applicationContext, authService)
        hubManager.connectToHub()
        serialPortController = SerialPortController(applicationContext, appScope, hubManager)
        serialPortController.connectToSerialPort(UART_S1_NAME)

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
    }
}