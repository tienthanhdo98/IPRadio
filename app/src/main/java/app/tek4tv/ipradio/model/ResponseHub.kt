package app.tek4tv.ipradio.model


data class ResponseHub(

    var imei: String? = null,


    var message: String? = null,


    var volume: String = "",


    var duration: Int = 0,

    var level: Int = 0,

    var listLiveMediaInfo: List<MediaInfo>? = null,

    var listMediaInfo: List<MediaInfo>? = null,

    var listSaveOnlyMediaInfo: List<MediaInfo>? = null,

    var settingConfig: McuSettingsData? = null,

    var serverConfig: ServerData? = null,

    var updateFirmData: McuUpdateFirmData? = null,

    var streamBackData: McuStreamBackData? = null


)
