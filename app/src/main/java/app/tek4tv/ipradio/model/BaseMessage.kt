package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BaseMqttMessage(
    @Json(name = "payload") val payload: Any?,
    @Json(name = "id") val id: String,
    @Json(name = "topic") val topic: String
)