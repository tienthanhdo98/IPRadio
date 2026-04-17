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

    val DEFAULT_FM_VOLUME = "DEFAULT_FM_VOLUME"
    val DEFAULT_FM_THRESHOLD = "DEFAULT_FM_VOLUME"
    val DEFAULT_FM_AUTO_STOP = "DEFAULT_FM_AUTO_STOP"
    val COUNT_RESTART_APP = "COUNT_RESTART_APP"

    //reset app
    private val KEY_COUNT_RESTART_APP = "KEY_COUNT_RESTART_APP"
    private val KEY_LAST_RESTART_TIME = "KEY_LAST_RESTART_TIME"
    private val KEY_APP_START_TIME = "KEY_APP_START_TIME"
    private val KEY_RESTART_BLOCKED_UNTIL = "KEY_RESTART_BLOCKED_UNTIL"

    val ADMIN_PASSWORD = "ADMIN_PASSWORD"
    var adminPassword = "1"


    var mainVolume = "90"
    var fmVolume = "90"
    var fmAutoStop = 1
    var fmThreshold = "36"
    var piCode = "0"
    var psName = "AAA"


    var bootstrapServer = ""
    var mqttServer = "aiot.mobifone.vn"
    var mqttPort = 6668
    var mqttSecurity = 1
    var bootstrapInfo: BootstrapResp? = null

    var settingsDisable = 1
    var settingsTxType = 0
    var settingsSimType = 0
    var settingsSimMode = 0

    val SETTINGS_LOCAL_IP = "SETTINGS_LOCAL_IP"
    val SETTINGS_SUBNET = "SETTINGS_SUBNET"
    val SETTINGS_GATEWAY = "SETTINGS_GATEWAY"

    var localIp = "192.168.1.231"
    var subnet = "255.255.255.0"
    var gateway = "192.168.1.1"


    init {
        mainVolume = ConfigUtil.getString(appContext, DEFAULT_VOLUME, "90") ?: "90"
        fmVolume = ConfigUtil.getString(appContext, DEFAULT_FM_VOLUME, "90") ?: "90"
        fmThreshold = ConfigUtil.getString(appContext, DEFAULT_FM_THRESHOLD, "36") ?: "36"
        fmAutoStop = ConfigUtil.getInt(appContext, DEFAULT_FM_AUTO_STOP, 1)
        piCode = ConfigUtil.getString(appContext, DEFAULT_PI_CODE, "0") ?: "0"
        psName = ConfigUtil.getString(appContext, DEFAULT_PS_NAME, "AAA") ?: "AAA"
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
        settingsDisable = ConfigUtil.getInt(appContext, SETTINGS_DISABLE, 1)
        settingsTxType = ConfigUtil.getInt(appContext, SETTINGS_TXTYPE, 0)
        settingsSimType = ConfigUtil.getInt(appContext, SETTINGS_SIM, 0)
        settingsSimMode = ConfigUtil.getInt(appContext, SETTINGS_SIM_MODE, 0)
        
        localIp = ConfigUtil.getString(appContext, SETTINGS_LOCAL_IP, "192.168.1.231") ?: "192.168.1.231"
        subnet = ConfigUtil.getString(appContext, SETTINGS_SUBNET, "255.255.255.0") ?: "255.255.255.0"
        gateway = ConfigUtil.getString(appContext, SETTINGS_GATEWAY, "192.168.1.1") ?: "192.168.1.1"

        adminPassword = ConfigUtil.getString(appContext, ADMIN_PASSWORD, "1") ?: "1"
    }

    fun saveSettingConfig(settingConfig: McuSettingsData?) {
        if (settingConfig == null) return
        if (settingConfig.txtype != null) {
            ConfigUtil.putInt(appContext, SETTINGS_TXTYPE, settingConfig.txtype)
            settingsTxType = settingConfig.txtype
        }
        if (settingConfig.sim != null) {
            ConfigUtil.putInt(appContext, SETTINGS_SIM, settingConfig.sim)
            settingsSimType = settingConfig.sim
        }
        if (settingConfig.mobileMode != null) {
            ConfigUtil.putInt(appContext, SETTINGS_SIM_MODE, settingConfig.mobileMode)
            settingsSimMode = settingConfig.mobileMode
        }
        if (settingConfig.disabled != null) {
            ConfigUtil.putInt(appContext, SETTINGS_DISABLE, settingConfig.disabled)
            settingsDisable = settingConfig.disabled
        }
    }

    fun saveServerConfig(serverData: ServerData?) {
        if (serverData != null) {
            ConfigUtil.putStringEncrypt(appContext, BOOTSTRAP_SERVER, serverData.bootstrapServer)
            bootstrapServer = serverData.bootstrapServer ?: "aiot.mobifone.vn"
            ConfigUtil.putInt(appContext, MQTT_SECURITY, serverData.mqttSecurity ?: 1)
            mqttSecurity = serverData.mqttSecurity ?: 1
            ConfigUtil.putStringEncrypt(appContext, MQTT_SERVER, serverData.mqttServer)
            mqttServer = serverData.mqttServer ?: "aiot.mobifone.vn"
            ConfigUtil.putInt(appContext, MQTT_PORT, serverData.mqttPort ?: 6668)
            mqttPort = serverData.mqttPort ?: 6668
        }
    }

    fun saveConnectionConfig(bootstrapResp: BootstrapResp?) {
        if (bootstrapResp != null) {
            ConfigUtil.putStringEncrypt(
                appContext,
                BOOTSTRAP_INFO,
                bootstrapAdapter.toJson(bootstrapResp)
            )
            bootstrapInfo = bootstrapResp
        }
    }

    fun saveNetworkIpConfig(ip: String?, sub: String?, gate: String?) {
        ip?.let {
            localIp = it
            ConfigUtil.putString(appContext, SETTINGS_LOCAL_IP, it)
        }
        sub?.let {
            subnet = it
            ConfigUtil.putString(appContext, SETTINGS_SUBNET, it)
        }
        gate?.let {
            gateway = it
            ConfigUtil.putString(appContext, SETTINGS_GATEWAY, it)
        }
    }

    fun saveMainVolume(vol: String) {
        mainVolume = vol
        ConfigUtil.putString(appContext, DEFAULT_VOLUME, vol)
    }

    fun saveFMVolume(vol: String) {
        fmVolume = vol
        ConfigUtil.putString(appContext, DEFAULT_FM_VOLUME, vol)
    }

    fun saveFmThreshold(value: String) {
        fmThreshold = value
        ConfigUtil.putString(appContext, DEFAULT_FM_THRESHOLD, value)
    }

    fun saveFmAutoStop(value: Int) {
        fmAutoStop = value
        ConfigUtil.putInt(appContext, DEFAULT_FM_AUTO_STOP, value)

    }

    fun saveSimType(type: Int) {
        ConfigUtil.putInt(appContext, SETTINGS_SIM, type)
    }

    fun saveAdminPassword(password: String) {
        adminPassword = password
        ConfigUtil.putString(appContext, ADMIN_PASSWORD, password)
    }

    fun clearAllLocalConfigData() {
        ConfigUtil.clearAllPrefs(appContext)

    }
    
    fun clearConnectionConfig() {
        ConfigUtil.removeSharefEncrypt(appContext, MQTT_SERVER)
        ConfigUtil.removeSharef(appContext, MQTT_SECURITY)
        ConfigUtil.removeSharef(appContext, MQTT_PORT)
        ConfigUtil.removeSharef(appContext, SETTINGS_LOCAL_IP)
        ConfigUtil.removeSharef(appContext, SETTINGS_SUBNET)
        ConfigUtil.removeSharef(appContext, SETTINGS_GATEWAY)
        
        mqttServer = "aiot.mobifone.vn"
        mqttSecurity = 1
        mqttPort = 6668
        localIp = "192.168.1.231"
        subnet = "255.255.255.0"
        gateway = "192.168.1.1"
    }

    fun clearSettingsConfig() {
        ConfigUtil.removeSharef(appContext, SETTINGS_TXTYPE)
        ConfigUtil.removeSharef(appContext, SETTINGS_SIM)
        ConfigUtil.removeSharef(appContext, SETTINGS_SIM_MODE)
        ConfigUtil.removeSharef(appContext, SETTINGS_DISABLE)
        ConfigUtil.removeSharef(appContext, DEFAULT_VOLUME)
        ConfigUtil.removeSharef(appContext, DEFAULT_FM_VOLUME)
        ConfigUtil.removeSharef(appContext, DEFAULT_FM_AUTO_STOP)
        ConfigUtil.removeSharef(appContext, DEFAULT_FM_THRESHOLD)
        
        settingsTxType = 0
        settingsSimType = 0
        settingsSimMode = 0
        settingsDisable = 1
        mainVolume = "90"
        fmVolume = "90"
        fmAutoStop = 1
        fmThreshold = "36"
    }

    //Reset app
    fun getCountRestartApp(): Int {
        return ConfigUtil.getInt(appContext, KEY_COUNT_RESTART_APP, 0)
    }

    fun saveCountRestartApp(count: Int) {
        ConfigUtil.putInt(appContext, KEY_COUNT_RESTART_APP, count)
    }

    fun getLastRestartTime(): Long {
        return ConfigUtil.getLong(appContext, KEY_LAST_RESTART_TIME, 0L)
    }

    fun saveLastRestartTime(time: Long) {
        ConfigUtil.putLong(appContext, KEY_LAST_RESTART_TIME, time)
    }

    fun getAppStartTime(): Long {
        return ConfigUtil.getLong(appContext, KEY_APP_START_TIME, 0L)
    }

    fun saveAppStartTime(time: Long) {
        ConfigUtil.putLong(appContext, KEY_APP_START_TIME, time)
    }

    fun getRestartBlockedUntil(): Long {
        return ConfigUtil.getLong(appContext, KEY_RESTART_BLOCKED_UNTIL, 0L)
    }

    fun saveRestartBlockedUntil(time: Long) {
        ConfigUtil.putLong(appContext, KEY_RESTART_BLOCKED_UNTIL, time)
    }

    fun clearRestartGuard() {
        ConfigUtil
            .putInt(appContext, KEY_COUNT_RESTART_APP, 0)
        ConfigUtil
            .putLong(appContext, KEY_LAST_RESTART_TIME, 0L)
        ConfigUtil.putLong(appContext, KEY_RESTART_BLOCKED_UNTIL, 0L)

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