package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.*

open class LogRecord {}

@JsonClass(generateAdapter = true)
data class MediaRecord(
    val imei: String? = null,
    val mediaID: String? = null,
    val sourceID: String? = null,
    val sourceName: String? = null,
    val destinationId: String? = null,
    val destinationName: String? = null,
    val title: String? = null,
    val description: String? = null,
    val category: Category? = null,
    val createDate: Long? = null,
    val duration: String? = null,
    val author: Author? = null,
    val startTime: Long? = null,
    val isExternal: Int? = null,
    val externalSource: String? = null,
    val body: String? = null,
    val volume: String? = null,
    val paState: String? = null,
    val priority: Priority? = null,
    val contentType: ContentType? = null,
    //thuoc tinh cua end
    val endTime: Long? = null,
    val status: String? = null

) : LogRecord() {
    var provider: String = "Tek4tv"
}

@JsonClass(generateAdapter = true)
data class HubStateRecord(
    var appName: String = "IpRadio",
    val imei: String,
    val provider: String,
    val state: String,
    val networkType: String? = null,
    val start: String? = null,
    val destinationID: String,
    val destinationName: String,
    val createDate: Long? = null

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