package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatusPayload(
    @Json(name = "AmLuong") val amLuong: String? = null,
    @Json(name = "CumLoaID") val cumLoaId: String? = null,
    @Json(name = "DichID") val dichId: String? = null,

    @Json(name = "KinhDo") val kinhDo: Double? = null,
    @Json(name = "ViDo") val viDo: Double? = null,

    @Json(name = "MaNhaCungCap") val maNhaCungCap: String? = null,
    @Json(name = "NguonID") val nguonId: String? = null,

    @Json(name = "NoiDungPhat") val noiDungPhat: String? = null,
    @Json(name = "PhienBanUngDung") val phienBanUngDung: String? = null,

    @Json(name = "TenDich") val tenDich: String? = null,
    @Json(name = "TenLoaiThietBi") val tenLoaiThietBi: String? = null,
    @Json(name = "TenNguon") val tenNguon: String? = null,
    @Json(name = "TenNhaCungCap") val tenNhaCungCap: String? = null,
    @Json(name = "TenThietBi") val tenThietBi: String? = null,

    @Json(name = "ThoiDiemBatDau") val thoiDiemBatDau: Long? = null,

    @Json(name = "TrangThaiHoatDong") val trangThaiHoatDong: String? = null,

    @Json(name = "TrangThaiKetNoi") val trangThaiKetNoi: String? = null
)