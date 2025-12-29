package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LivePayload(
    @Json(name = "NguonID")
    val nguonID: String,

    @Json(name = "TenNguon")
    val tenNguon: String,

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DiaBanNhan>,

    @Json(name = "BanTinID")
    val banTinID: String,

    @Json(name = "LoaiBanTin")
    val loaiBanTin: String,

    @Json(name = "MucDoUuTien")
    val mucDoUuTien: String,

    @Json(name = "TieuDe")
    val tieuDe: String,

    @Json(name = "LoaiLinhVuc")
    val loaiLinhVuc: String,

    @Json(name = "NoiDungTomTat")
    val noiDungTomTat: String,

    @Json(name = "ThoiGianSanXuat")
    val thoiGianSANXuat: Long,

    @Json(name = "ThoiLuong")
    val thoiLuong: String,

    @Json(name = "TacGia")
    val tacGia: TacGia,

    @Json(name = "NoiDung")
    val noiDung: String,

    @Json(name = "DiaBanTao")
    val diaBanTAO: String,

    @Json(name = "TenDiaBanTao")
    val tenDiaBanTAO: String,

    @Json(name = "ThongTinChiTietBanTin")
    val thongTinChiTietBanTin: Any? = null,

    @Json(name = "NguonTin")
    val nguonTin: String
)


