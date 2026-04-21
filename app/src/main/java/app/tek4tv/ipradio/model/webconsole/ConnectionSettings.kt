package app.tek4tv.ipradio.model.webconsole

data class ConnectionSettings(
    val localIp: String? = null,
    val subnet: String? = null,
    val gateway: String? = null,
    val mqttSecurity: String? = null,
    val mqttServer: String? = null,
    val mqttPort: String? = null,
    val networkType: String? = null,
    val simSelection: String? = null,
    val mobileDataMode: String? = null,
//    val enableIOControl: String? = null,
//    val classDType: String? = null,
    val activation: String? = null,
    val normalVolume: String? = null,
    val fmVolume: String? = null,
    val fmAutoStop: String? = null,
    val fmAutoThreshold: String? = null,
    val wifiSsid: String? = null,
    val wifiPassword: String? = null
)

data class ServerSettings(
    val bootstrapServer: String, val mqttServer: String, val mqttPort: Int, val mqttSecurity: Int
)

data class DeviceBasicInfo(
    val mgwId: String = "",
    val verifyCode: String = "",
    val firmwareVersion: String = ""
)

data class StatusPageData(
    val appUptime: Long = 0,
    val systemUptime: Long = 0,
    val storageTotalMiB: Double = 0.0,
    val storageFreeMiB: Double = 0.0,
    val connectionType: String = "ETH",
    val disconnectionCnt: Int = 0,
    val isServerConnected: Boolean = false,
    val isFmPlaying: Boolean = false,
    val fmRssi: Int = 0,
    val isStreamBack: Boolean = false,
    val spk1Run: Boolean = false, val spk1Err: Boolean = false,
    val spk2Run: Boolean = false, val spk2Err: Boolean = false,
    val spk3Run: Boolean = false, val spk3Err: Boolean = false,
    val spk4Run: Boolean = false, val spk4Err: Boolean = false,
    var serverMacAddress: String = ""
)

data class FirmwareUpdateData(
    val url: String, val updateMode: Int
)

data class PlayerStatusData(
    val mediaId: Long? = null,
    val cycleSec: Long = 0,
    val fileChunkCurrent: Int = 0,
    val fileChunkTotal: Int = 0,
    val currentFilePositionMs: Long = 0,
    val currentFileDurationMs: Long = 0,
    // Player types active
    val isMpdOn: Boolean = false,
    val isFmOn: Boolean = false,
    val fmRssi: Int = 0,
    val isSilenceOn: Boolean = false,
    val isLiveMicOn: Boolean = false,
    val playMode: String = "INIT"
)