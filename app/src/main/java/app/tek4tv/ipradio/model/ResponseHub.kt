package app.tek4tv.ipradio.model


data class ResponseHub(

    var imei: String? = null,


    var message: String? = null,


    var volume: String = "",


    var duration: Int = 0,

    var level: Int = 0,

    var mediaInfo: MediaInfo? = null,

    var listMediaInfo: MutableList<MediaInfo>? = null,

    var networkConfig: NetworkConfig? = null,

    var serverConfig: ServerData? = null

//    var mediaInfo: List<MediaInfo>? = emptyList(),


)
