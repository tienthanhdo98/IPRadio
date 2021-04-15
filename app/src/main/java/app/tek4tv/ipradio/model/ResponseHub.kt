package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
class ResponseHub : Serializable {
    @Json(name = "IMEI")
    var imei: String? = null

    @Json(name = "Message")
    var message: String? = null

    @Json(name = "Volume")
    var volume: String = ""

    @Json(name = "Start")
    var start: String? = null

    @Json(name = "Duration")
    var duration : Int = 0

    @Json(name = "PI")
    var piCode = ""

    @Json(name = "PS")
    var psName = ""

    @Json(name = "Log")
    var logEnabled : Boolean? = null
}
