package app.tek4tv.ipradio.utils

import android.content.Context
import android.util.Log
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.model.ResponseHub
import app.tek4tv.ipradio.utils.ConfigUtil.PROVIDER

class GlobalProperties {

    private val appContext: Context = MyApp.instance!!.applicationContext

    val DEFAULT_VOLUME = "DEFAULT_VOLUME"
    val DEFAULT_PI_CODE = "DEFAULT_PI_CODE"
    val DEFAULT_PS_NAME = "DEFAULT_PS_NAME"
    val DEFAULT_RESUME_FROM_START = "DEFAULT_RESUME_FROM_START"
    val NAME_DEVICE = "NAME_DEVICE"
    val LATITUDE = "LATITUDE"
    val LONGTITUDE = "LONGTITUDE"
    val USER_NAME_CONTROLLER = "USER_NAME_CONTROLLER"

    //MGW
    val BOOTSTRAP_SERVER = "BOOTSTRAP_SERVER"
    val MQTT_SECURITY = "MQTT_SECURITY"
    val MQTT_SERVER = "MQTT_SERVER"
    val MQTT_PORT = "MQTT_PORT"


    var volume = "100"
    var userNameController = ""
    var piCode = "0"
    var psName = "AAA"
    var nameDevice = ""
    var resumeFromStart = false
    var lat: String = "0.0"
    var lng: String = "0.0"
    var providerValue: String = "TEK4TV"

    init {
        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "Thiết bị Demo - TEK4TV")
            ?: "Thiết bị Demo - TEK4TV"
        //nameDevice = ConfigUtil.getString(appContext, NAME_DEVICE, "TB Xã Ba Cung") ?: "TB Xã Ba Cung"
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
        lat = ConfigUtil.getString(appContext, LATITUDE, "0.0") ?: "0.0"
        lng = ConfigUtil.getString(appContext, LONGTITUDE, "0.0") ?: "0.0"
        userNameController = ConfigUtil.getString(appContext, USER_NAME_CONTROLLER, "") ?: ""
        providerValue = ConfigUtil.getString(appContext, PROVIDER, "TEK4TV") ?: "TEK4TV"
    }

    fun saveProperties(responseHub: ResponseHub) {
        if (responseHub.volume != "") {
            saveVolume(responseHub.volume)
        }

    }

    fun saveServerConfig(responseHub: ResponseHub) {
        val serverConfig = responseHub.serverConfig
        if (serverConfig != null) {
            ConfigUtil.putString(appContext, BOOTSTRAP_SERVER, serverConfig.bootstrapServer)
            ConfigUtil.putInt(appContext, MQTT_SECURITY, serverConfig.mqttSecurity ?: 0)
            ConfigUtil.putString(appContext, MQTT_SERVER, serverConfig.mqttServer)
            ConfigUtil.putInt(appContext, MQTT_PORT, serverConfig.mqttPort ?: 6668)
        }
    }


    fun saveVolume(vol: String) {
        volume = vol
        ConfigUtil.putString(appContext, DEFAULT_VOLUME, vol)

    }

    companion object {

        private var instance: GlobalProperties? = null

        //MGW
        val BOOTSTRAP_SERVER = "BOOTSTRAP_SERVER"
        val MQTT_SECURITY = "MQTT_SECURITY"
        val MQTT_SERVER = "MQTT_SERVER"
        val MQTT_PORT = "MQTT_PORT"

        fun getInst(): GlobalProperties {
            if (instance == null)
                instance = GlobalProperties()

            return instance!!
        }
    }
}