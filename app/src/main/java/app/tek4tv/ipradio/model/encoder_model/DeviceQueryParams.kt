package app.tek4tv.voicestream.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceQueryParams(
    @Json(name = "GroupIDs")
    var groupIds  : List<Int> = listOf(),
    @Json(name = "pageNumber")
    var pageNumber : Int = 1,
    @Json(name = "pageSize")
    var pageSize : Int = 200
)
