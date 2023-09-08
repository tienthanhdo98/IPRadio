package app.tek4tv.ipradio.model.encoder_model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Group(
    @Json(name = "ID")
    var id : Int,
    @Json(name = "ParentID")
    var parentId : Int = -1,
    @Json(name = "Name")
    var name : String
)
{

    override fun toString(): String = name
}