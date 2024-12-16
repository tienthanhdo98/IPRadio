package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class MQTTInfoModel(
    val BangDienTuID: String = "",
)

data class MQTTInfoValueModel(
    @Json(name = "NoiDung")
    val noiDung: List<NoiDungMQTT>,

    @Json(name = "TrangThaiGui")
    val trangThaiGUI: Long,

    @Json(name = "MoTa")
    val moTa: String
)
data class NoiDungMQTT (
    val topic: String,
    val action: String
)