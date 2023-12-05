package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class RecordLogModel (
    @Json(name = "Duration")
    val duration: String,

    @Json(name = "UserId")
    var userID: String,

    @Json(name = "Extension")
    var extension: String,

    @Json(name = "Data")
    var data: String)