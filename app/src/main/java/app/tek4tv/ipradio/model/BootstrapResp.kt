package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BootstrapResp(
    @Json(name = "external_id")
    val externalID: String?,

    @Json(name = "external_key")
    val externalKey: String?,

    @Json(name = "thing_id")
    val thingID: String?,

    @Json(name = "thing_key")
    val thingKey: String?,

    val channels: List<String>,

    val content: Content?
)

data class Content(
    val txtype: Long,
    val sim: Long?,

    @Json(name = "mobile_mode")
    val mobileMode: Long?,

    val ioct: Long?,
    val classd: Long?,
    val disabled: Long?,

    @Json(name = "main_volume")
    val mainVolume: Long?,

    @Json(name = "fm_volume")
    val fmVolume: Long?,

    @Json(name = "fm_auto")
    val fmAuto: Long?,

    @Json(name = "fm_threshold")
    val fmThreshold: Long?
)
