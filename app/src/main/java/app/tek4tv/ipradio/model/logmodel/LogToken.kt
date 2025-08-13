package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class LogToken(
    @Json(name = "NoiDung")
    val noiDung: NoiDung,

    @Json(name = "TrangThaiGui")
    val trangThaiGUI: Long,

    @Json(name = "MoTa")
    val moTa: String
)

data class NoiDung(
    @Json(name = "Token")
    val token: String,

    @Json(name = "ExpirationTime")
    val expirationTime: Long,

    @Json(name = "RefreshToken")
    val refreshToken: String,

    @Json(name = "Message")
    val message: String
)
