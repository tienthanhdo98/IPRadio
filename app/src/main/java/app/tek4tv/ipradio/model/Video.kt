package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class Video(
    @Json(name = "Index") var index: String?,
    @Json(
        name = "Time"
    ) var time: String?,

    @Json(name = "MediaName") var mediaName: String? = null,

    @Json(name = "AudioName") var audioName: String? = null,

    @Json(name = "Path") var path: String? = null,

    @Json(name = "Level") var level: Int? = -1
)