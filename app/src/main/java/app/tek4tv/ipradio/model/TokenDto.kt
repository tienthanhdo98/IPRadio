package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class TokenDto(
    @Json(name = "Token")
    val token: String,
    @Json(name = "Message")
    val message: String
)
