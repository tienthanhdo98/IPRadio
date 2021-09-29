package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class GetLogFile(
    @Json(name = "Name")
    var name: String ="",
    @Json(name = "Imei")
    var imei: String="",
    @Json(name = "Provider")
    var provider:String ="",
    @Json(name = "Data")
    var data:String = "",
    @Json(name = "Start")
    var start:Long = 0L
)
