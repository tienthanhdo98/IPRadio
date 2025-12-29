package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LogMediaSuccessPayload(
    @Json(name = "AmLuong") val amLuong: String? = null,
    @Json(name = "BanTinID") val banTinId: String? = null,
    @Json(name = "CongSuat") val congSuat: String? = null,
    @Json(name = "CumLoaID") val cumLoaId: String? = null,
    @Json(name = "DichID") val dichId: String? = null,
    @Json(name = "LoaiBanTin") val loaiBanTin: String? = null,
    @Json(name = "LoaiLinhVuc") val loaiLinhVuc: String? = null,
    @Json(name = "MaNhaCungCap") val maNhaCungCap: String? = null,
    @Json(name = "MucDoUuTien") val mucDoUuTien: String? = null,
    @Json(name = "NguonID") val nguonId: String? = null,
    @Json(name = "NguonTin") val nguonTin: String? = null,
    @Json(name = "NoiDung") val noiDung: String? = null,
    @Json(name = "NoiDungTomTat") val noiDungTomTat: String? = null,
    @Json(name = "TacGia") val tacGia: TacGia? = null,
    @Json(name = "TenDich") val tenDich: String? = null,
    @Json(name = "TenNhaCungCap") val tenNhaCungCap: String? = null,
    @Json(name = "TenThietBi") val tenThietBi: String? = null,
    @Json(name = "ThoiDiemBatDau") val thoiDiemBatDau: Long? = null,
    @Json(name = "ThoiGianSanXuat") val thoiGianSanXuat: Long? = null,
    @Json(name = "ThoiLuong") val thoiLuong: String? = null,
    @Json(name = "TiepAm") val tiepAm: Int? = null,
    @Json(name = "TieuDe") val tieuDe: String? = null
)