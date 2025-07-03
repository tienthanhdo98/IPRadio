package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class MediaLogModel(
    @Json(name = "NguonID")
    val nguonID: String,

    @Json(name = "TenNguon")
    val tenNguon: String,

    @Json(name = "DichID")
    val dichID: String,

    @Json(name = "TenDich")
    val tenDich: String,

    @Json(name = "BangDienTuID")
    val bangDienTuID: String,

    @Json(name = "TenThietBi")
    val tenThietBI: String,

    @Json(name = "MaNhaCungCap")
    val maNhaCungCap: String,

    @Json(name = "TenNhaCungCap")
    val tenNhaCungCap: String,

    @Json(name = "VungPhat")
    val vungPhat: String,

    @Json(name = "BanTinCongCongID")
    val banTinCongCongID: String,

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

    @Json(name = "ThoiDiemBatDau")
    val thoiDiemBatDau: Long,

    @Json(name = "NoiDung")
    val noiDung: List<String>,

    @Json(name = "ThongTinChiTietBanTin")
    val thongTinChiTietBanTin: List<ThongTinChiTietBanTinTa>,

    @Json(name = "NguonTin")
    val nguonTin: String

)