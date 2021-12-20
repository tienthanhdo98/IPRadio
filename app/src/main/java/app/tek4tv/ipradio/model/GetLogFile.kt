package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class GetLogFile(
    @Json(name = "Name")
    var name: String = "",
    @Json(name = "Imei")
    var imei: String = "",
    @Json(name = "Provider")
    var provider: String = "",
    @Json(name = "Data")
    var data: String = "",
    @Json(name = "Start")
    var start: Long = 0L,
    @Json(name = "AppName")
    var appName: String = "IpRadio",
    @Json(name = "DestinationID")
    var destinationID: String = "",
    @Json(name = "DestinationName")
    var destinationName: String = ""
)

data class Report(
    @Json(name = "AppName")
    val appName: String,

    @Json(name = "LogType")
    val logType: String,

    @Json(name = "Imei")
    val imei: String,

    @Json(name = "Provider")
    val provider: String,

    @Json(name = "ConnectionId")
    val connectionID: String,

    @Json(name = "DestinationID")
    val destinationID: String,

    @Json(name = "DestinationName")
    val destinationName: String,

    @Json(name = "NetworkInfo")
    val networkInfo: String,

    @Json(name = "StartTime")
    val startTime: Long,

    @Json(name = "Status")
    val status: String,

    @Json(name = "Version")
    val version: String,

    @Json(name = "Video")
    val video: String,

    @Json(name = "CreateDate")
    val createDate: Long,
    @Json(name = "DeviceName")
    val deviceName: String,
    @Json(name = "Lat")
    var lat: Float? = null,

    @Json(name = "Lng")
    var lng: Float? = null


)
