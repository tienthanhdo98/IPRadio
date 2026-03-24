package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BootstrapResp(
    @Json(name = "mainflux_id") val id: String? = null,

    @Json(name = "mainflux_key") val key: String? = null,

    @Json(name = "mainflux_channels") val channels: List<Channel>? = null
)

data class Channel(


    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "metadata") val metadata: Any? = null,

    )
