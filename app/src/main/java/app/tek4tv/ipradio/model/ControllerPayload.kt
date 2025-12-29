package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ControllerPayload(
    @Json(name = "NguonID") val nguonId: String?,
    @Json(name = "TenNguon") val tenNguon: String?,

    @Json(name = "DanhSachDiaBanNhan") val danhSachDiaBanNhan: List<DiaBanNhan>?,

    @Json(name = "DanhSachThietBi") val danhSachThietBi: List<ThietBi>?,

    @Json(name = "DieuKhienThietBi") val dieuKhienThietBi: DieuKhienThietBi?
)

@JsonClass(generateAdapter = true)
data class DieuKhienThietBi(
    @Json(name = "MaLenh") val maLenh: String?,
    @Json(name = "ThamSo") val thamSo: String?
)