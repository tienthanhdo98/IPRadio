package app.tek4tv.ipradio.model.encoder_model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
class ResponseHubEncoder : Serializable {
    @Json(name = "ConnectionId")
    var connectionId: String = ""

    @Json(name = "StartTime")
    var startTime = ""

    @Json(name = "IMEI")
    var imei: String? = null

    @Json(name = "Message")
    var message: String? = null

    @Json(name = "Volume")
    var volume: String ?= null

    @Json(name = "Start")
    var start: String? = null

    @Json(name = "Duration")
    var duration: Int = 0

    @Json(name = "PI")
    var piCode = ""

    @Json(name = "PS")
    var psName = ""

    @Json(name = "Log")
    var logEnabled: Boolean? = null

    @Json(name = "Type")
    var type: String = ""

    @Json(name = "Level")
    var level: Int = 0

    @Json(name = "Resume")
    var resumeFromStart: Boolean? = null

    @Json(name = "Video")
    var mediaInfo: String = ""
}

@JsonClass(generateAdapter = true)
class ConfigModel : Serializable {
    @Json(name = "Path")
    var path: String ?=null

}
//  @Json(name = "UserName")
//    var userName: String = ""
//
//    @Json(name = "Password")
//    var password: String = ""
//
//    @Json(name = "Level")
//    var level: String = ""
//
//    @Json(name = "Duration")
//    var duration: String = ""
//
//    @Json(name = "Bitrate")
//    var bitrate: String = ""
//
//    @Json(name = "Protocol")
//    var protocol: String = ""
