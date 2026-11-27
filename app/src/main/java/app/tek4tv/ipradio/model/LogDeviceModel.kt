package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LogDeviceModel(
    @Json(name = "ServiceName")
    val serviceName: String,

    @Json(name = "UserName")
    val userName: String,

    @Json(name = "ActionName")
    val actionName: String,

    @Json(name = "ObjectName")
    val objectName: String,

    @Json(name = "Metadata")
    val metadata: String,

    @Json(name = "Message")
    val message: String,

    @Json(name = "FromServer")
    val fromServer: String,

    @Json(name = "Platform")
    val platform: String,

    @Json(name = "Code")
    val code: String
)
