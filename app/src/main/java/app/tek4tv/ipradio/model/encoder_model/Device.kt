package app.tek4tv.ipradio.model.encoder_model

import com.squareup.moshi.Json

data class Device(
    @Json(name = "ID")
    var id: Int,
    @Json(name = "Name")
    var name  : String,
    @Json(name = "IMEI")
    var imei : String,
    @Json(name = "Config")
    var config : String,
    @Json(name = "TotalScenarioID")
    var totalScenarioId : Int?,
    @Json(name = "Groups")
    var groups : List<Group>,
)
{
    var connectionId : String = ""
    var startTime : String = ""
    var playing = ""
}

data class ScenarioName(
    @Json(name = "Name")
    var name: String
)
