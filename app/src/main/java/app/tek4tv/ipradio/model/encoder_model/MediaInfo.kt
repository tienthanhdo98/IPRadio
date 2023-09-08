package app.tek4tv.voicestream.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class MediaInfo(
    @Json(name = "MediaName") var mediaName: String = "",
)