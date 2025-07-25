package app.tek4tv.ipradio.model.api

import com.squareup.moshi.Json

data class DirectMessage(
    @Json(name = "IMEI")
    var imei: String,

    @Json(name = "Message")
    var message: String
)

data class ScheduleDay(
    @Json(name = "Date")
    var date: String = "",
    @Json(name = "Link")
    var sourceUrl: String = "",
    @Json(name = "BroadcastRegionDigital")
    var broadcastRegionDigital: String = ""

)