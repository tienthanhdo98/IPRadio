package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

open class LogRecord {}

@JsonClass(generateAdapter = true)
data class MediaRecord(
    @Json(name = "Imei")
    var imei: String,
    @Json(name = "Name")
    var name: String = "",
    @Json(name = "Type")
    var type: String = "",
    @Json(name = "SourceID")
    var sourceId: String = "",
    @Json(name = "SourceName")
    var sourceName: String = "",
    @Json(name = "DestinationID") var destinationId: String? = "",
    @Json(name = "DestinationName") var destinationName: String? = "",
    @Json(name = "ID")
    var id: String,
    @Json(name = "Volume")
    var volume: String = "",
    @Json(name = "PAState")
    var paState: String = "",
    @Json(name = "Start")
    var start: Long = Date().time,
    @Json(name = "Duration")
    var duration: Long = 0,
    @Json(name = "Path")
    var path: String = "",
    @Json(name = "AppName")
    var appName: String = "IpRadio"
    //AppEncoder/AppMicIp.

) : LogRecord() {
    @Json(name = "Provider")
    var provider: String = "Tek4tv"
}

@JsonClass(generateAdapter = true)
data class HubStateRecord(
    @Json(name = "Imei")
    var imei: String,
    @Json(name = "Provider")
    var provider: String,
    @Json(name = "State")
    var state: String = "",
    @Json(name = "NetworkType")
    var networkType: String? = null,
    @Json(name = "Start")
    var start: Long = Date().time,
    @Json(name = "AppName")
    var appName: String = "IpRadio"
) : LogRecord()

@JsonClass(generateAdapter = true)
data class TimeFrameRecord(
    @Json(name = "Imei")
    var imei: String,
    @Json(name = "TimeframeKey")
    var timeframeKey: String? = null,
    @Json(name = "Status")
    var status: String? = null,
    @Json(name = "Time")
    var time: Long = Date().time,
    @Json(name = "AppName")
    var appName: String = "IpRadio"
) : LogRecord() {
    @Json(name = "Provider")
    var provider: String = "Tek4tv"

    companion object {
        const val STATUS_START = "START"
        const val STATUS_STOP = "STOP"
    }
}

data class FbDeviceStatusReport(
    var status: String? = null,
    var time: Long = Date().time
) {
    companion object {
        const val STATUS_ON = "ON"
    }
}