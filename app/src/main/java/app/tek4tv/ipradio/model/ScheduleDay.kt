package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class ScheduleDay(
    @Json(name = "Date")
    var date : String = "",
    @Json(name = "Link")
    var sourceUrl : String = "",
    @Json(name = "BroadcastRegionDigital")
    var broadcastRegionDigital : String = ""

)