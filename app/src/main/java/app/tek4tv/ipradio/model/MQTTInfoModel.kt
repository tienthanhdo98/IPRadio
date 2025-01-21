package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class MQTTInfoModel(
    val CumLoaID: String? = "",
)

data class MQTTInfoValueModel(
    @Json(name = "NoiDung")
    val noiDung: MutableList<NoiDungMQTT>?,

    @Json(name = "TrangThaiGui")
    val trangThaiGUI: Long?,

    @Json(name = "MoTa")
    val moTa: String?
)
data class NoiDungMQTT (
    val topic: String?,
    val action: String?
)

data class SourceMQTT(
    @Json(name = "Code")
    var code : String?,
    @Json(name = "Name")
    var name : String?
)