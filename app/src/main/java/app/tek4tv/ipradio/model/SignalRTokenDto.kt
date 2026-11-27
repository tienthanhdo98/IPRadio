package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SignalRTokenDto(
    @Json(name = "Token")
    val token: String? = "",

    @Json(name = "Message")
    val message: String? = "",

    @Json(name = "ExpirationTime")
    val expirationTime: Long? = 0
)
