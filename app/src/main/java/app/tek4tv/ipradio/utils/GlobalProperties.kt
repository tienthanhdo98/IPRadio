package app.tek4tv.ipradio.utils

import android.content.Context
import app.tek4tv.ipradio.MyApp
import app.tek4tv.ipradio.di.JsonAdapterProvider.bootstrapAdapter
import app.tek4tv.ipradio.model.BootstrapResp
import app.tek4tv.ipradio.model.McuSettingsData
import app.tek4tv.ipradio.model.ServerData


class GlobalProperties {

    private val appContext: Context = MyApp.instance!!.applicationContext

    val DEFAULT_VOLUME = "DEFAULT_VOLUME"
    val DEFAULT_PI_CODE = "DEFAULT_PI_CODE"
    val DEFAULT_PS_NAME = "DEFAULT_PS_NAME"
    val DEFAULT_RESUME_FROM_START = "DEFAULT_RESUME_FROM_START"

    //MGW_SERVER
    val BOOTSTRAP_SERVER = "BOOTSTRAP_SERVER"
    val MQTT_SECURITY = "MQTT_SECURITY"
    val MQTT_SERVER = "MQTT_SERVER"
    val MQTT_PORT = "MQTT_PORT"
    val BOOTSTRAP_INFO = "BOOTSTRAP_INFO"

    //MGW_SETTINGS
    val SETTINGS_DISABLE = "SETTINGS_DISABLE"
    val SETTINGS_TXTYPE = "SETTINGS_TXTYPE"
    val SETTINGS_SIM = "SETTINGS_SIM"
    val SETTINGS_SIM_MODE = "SETTINGS_SIM_MODE"


    var volume = "100"
    var piCode = "0"
    var psName = "AAA"
    var resumeFromStart = false

    var bootstrapServer = ""
    var mqttServer = "aiot.mobifone.vn"
    var mqttPort = 6668
    var mqttSecurity = 1
    var bootstrapInfo: BootstrapResp? = null

    var settingsDisable = 0
    var settingsTxType = 0
    var settingsSim = 0
    var settingsSimMode = 0


    init {
        volume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "100") ?: "100"
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
        resumeFromStart = ConfigUtil.getBool(appContext, DEFAULT_RESUME_FROM_START, false)
        //
        bootstrapServer =
            ConfigUtil.getStringEncrypt(appContext, BOOTSTRAP_SERVER, "aiot.mobifone.vn")
                ?: "aiot.mobifone.vn"
        //
        mqttServer =
            ConfigUtil.getStringEncrypt(appContext, MQTT_SERVER, "aiot.mobifone.vn")
                ?: "aiot.mobifone.vn"
        mqttSecurity = ConfigUtil.getInt(
            appContext, MQTT_SECURITY, 1
        )
        mqttPort = ConfigUtil.getInt(appContext, MQTT_PORT, 6668)

        val bootstrapJson = ConfigUtil.getStringEncrypt(appContext, BOOTSTRAP_INFO, null)
        bootstrapInfo = try {
            if (bootstrapJson.isNullOrBlank()) null
            else bootstrapAdapter.fromJson(bootstrapJson)
        } catch (e: Exception) {
            //xóa key lỗi khi parse fail
            ConfigUtil.putStringEncrypt(appContext, BOOTSTRAP_INFO, null)
            null
        }
        //setting
        settingsDisable = ConfigUtil.getInt(appContext, SETTINGS_DISABLE, 0)
        settingsTxType = ConfigUtil.getInt(appContext, SETTINGS_TXTYPE, 0)
        settingsSim = ConfigUtil.getInt(appContext, SETTINGS_SIM, 0)
        settingsSimMode = ConfigUtil.getInt(appContext, SETTINGS_SIM_MODE, 0)
    }

    fun saveSettingConfig(settingConfig: McuSettingsData?) {
        if (settingConfig == null) return
        if (settingConfig.txtype != null) {
            ConfigUtil.putInt(appContext, SETTINGS_TXTYPE, settingConfig.txtype)
        }
        if (settingConfig.sim != null) {
            ConfigUtil.putInt(appContext, SETTINGS_SIM, settingConfig.sim)
        }
        if (settingConfig.mobileMode != null) {
            ConfigUtil.putInt(appContext, SETTINGS_SIM_MODE, settingConfig.mobileMode)
        }
        if (settingConfig.disabled != null) {
            ConfigUtil.putInt(appContext, SETTINGS_DISABLE, settingConfig.disabled)
        }
    }

    fun saveServerConfig(serverData: ServerData?) {
        if (serverData != null) {
            ConfigUtil.putStringEncrypt(appContext, BOOTSTRAP_SERVER, serverData.bootstrapServer)
            ConfigUtil.putInt(appContext, MQTT_SECURITY, serverData.mqttSecurity ?: 1)
            ConfigUtil.putStringEncrypt(appContext, MQTT_SERVER, serverData.mqttServer)
            ConfigUtil.putInt(appContext, MQTT_PORT, serverData.mqttPort ?: 6668)
        }
    }

    fun saveConnectionConfig(bootstrapResp: BootstrapResp?) {
        if (bootstrapResp != null) {
            ConfigUtil.putStringEncrypt(
                appContext,
                BOOTSTRAP_INFO,
                bootstrapAdapter.toJson(bootstrapResp)
            )
        }
    }

    fun saveVolume(vol: String) {
        volume = vol
        ConfigUtil.putString(appContext, DEFAULT_VOLUME, vol)
    }

    fun clearAllLocalConfigData() {
        ConfigUtil.clearAllPrefs(appContext)

    }

    companion object {

        private var instance: GlobalProperties? = null

        fun getInst(): GlobalProperties {
            if (instance == null)
                instance = GlobalProperties()

            return instance!!
        }
    }
}