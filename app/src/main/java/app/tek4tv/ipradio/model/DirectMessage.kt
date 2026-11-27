package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DirectMessage(
    @Json(name = "IMEI")
    var imei: String,

    @Json(name = "Message")
    var message: String
)
