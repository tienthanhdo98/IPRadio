package app.tek4tv.ipradio.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MetadataModel(
    var key: String?,
    var value: String?
)
