package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CancelSchedulePayload(
    @Json(name = "NguonID") val nguonID: String? = null,
    @Json(name = "TenNguon") val tenNguon: String? = null,
    @Json(name = "LichPhatID") val lichPhatID: String? = null,
    @Json(name = "TenLichPhat") val tenLichPhat: String? = null,
    @Json(name = "DanhSachDiaBanNhan") val danhSachDiaBanNhan: List<DiaBanNhan>? = emptyList()
)

data class LogCancelSchedulePayload(
    @Json(name = "DichID") val dichID: String?,
    @Json(name = "LichPhatID") val lichPhatID: String?,
    @Json(name = "NguonID") val nguonID: String?,
    @Json(name = "TenDich") val tenDich: String?,
    @Json(name = "TenLichPhat") val tenLichPhat: String?,
    @Json(name = "TenNguon") val tenNguon: String?,
    @Json(name = "DanhSachThietBi") val danhSachThietBi: List<ThietBi>?,
)
