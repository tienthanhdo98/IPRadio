package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class PingHubRequest {
    @Json(name = "ConnectionId")

    var connectionId: String? = null

    @Json(name = "IMEI")

    var imei: String? = null

    @Json(name = "StartTime")

    var startTime: String? = null

    @Json(name = "Status")

    var status: String? = null

    @Json(name = "Volume")

    var volume: String? = null

    @Json(name = "Message")

    var message: String? = null

    @Json(name = "Video")

    var video: String? = null

    @Json(name = "NetworkInfo")
    var networkInfo = ""

    @Json(name = "Version")
    var version = ""

    @Json(name = "DestinationID")
    var destinationId: String? = ""

    @Json(name = "DestinationName")
    var destinationName: String? = ""

    //
    @Json(name = "AppName")
    var appName: String = ""

    @Json(name = "LogType")
    var logType: String = ""

    @Json(name = "Provider")
    var provider: String = ""

    @Json(name = "CreateDate")
    var createDate: Long = 0

    @Json(name = "DeviceName")
    var deviceName: String = ""

    @Json(name = "Lat")
    var lat: Float? = null

    @Json(name = "Lng")
    var lng: Float? = null

    @Json(name = "UserNameControl")
    var userNameControl: String? = null

    @Json(name = "IsRecord")
    var isRecord: String? = null

    @Json(name = "DeviceInfo")
    var deviceInfo: String? = null

}