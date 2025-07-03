package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReceivedMessageModel(
    val id: String?,
    val topic: String?,
    val payload: Any?
)

data class itemMedia
    (
    var time: ThongTinChiTietBanTin,
    var danhSachBanTin: DanhSachBanTin
)

data class DanhSachDiaBanNhan(
    @Json(name = "DichID")
    val dichID: String?,

    @Json(name = "TenDich")
    val tenDich: String?,

    @Json(name = "CumLoaID")
    val cumLoaID: List<String>?,

    @Json(name = "BangDienTuID")
    val bangDienTuID: List<String>?,
)

data class TacGia(
    @Json(name = "TenDayDu")
    val tenDayDu: String,

    @Json(name = "TenDangNhap")
    val tenDangNhap: String,

    @Json(name = "ButDanh")
    val butDanh: String,

    @Json(name = "Email")
    val email: String
)

data class ThongTinChiTietBanTin(
    @Json(name = "ThoiGianBatDau")
    val thoiGianBatDau: Long,

    @Json(name = "ThoiGianKetThuc")
    val thoiGianKetThuc: Long
)

data class ThongTinChiTietBanTinTa(
    @Json(name = "Ten")
    val ten: String?,

    @Json(name = "GiaTri")
    val giaTri: String?
)


data class DanhSachThietBi(
    @Json(name = "BangDienTuID")
    val bangDienTuID: String?,

    @Json(name = "TenThietBi")
    val tenThietBI: String?,

    @Json(name = "MaNhaCungCap")
    val maNhaCungCap: String?,

    @Json(name = "TenNhaCungCap")
    val tenNhaCungCap: String?
)

data class DanhSachBanTin(

    @Json(name = "DanhSachNgayPhat")
    val danhSachNgayPhat: List<DanhSachNgayPhat>?,

//    @Json(name = "NguonID")
//    val nguonID: String?,
//
//    @Json(name = "TenNguon")
//    val tenNguon: String?,

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DanhSachDiaBanNhan>?,

    @Json(name = "VungPhat")
    val vungPhat: String?,

    @Json(name = "BanTinCongCongID")
    val banTinCongCongID: String?,

    @Json(name = "LoaiBanTin")
    val loaiBanTin: String?,

    @Json(name = "MucDoUuTien")
    val mucDoUuTien: String?,

    @Json(name = "TieuDe")
    val tieuDe: String?,

    @Json(name = "LoaiLinhVuc")
    val loaiLinhVuc: String?,

    @Json(name = "NoiDungTomTat")
    val noiDungTomTat: String?,

    @Json(name = "ThoiGianSanXuat")
    val thoiGianSANXuat: Long?,

    @Json(name = "TacGia")
    val tacGia: TacGia?,

    @Json(name = "NoiDung")
    val noiDung: String?,

    @Json(name = "NguonTin")
    val nguonTin: String?,

    @Json(name = "DiaBanTao")
    val diaBanTAO: String?,

    @Json(name = "TenDiaBanTao")
    val tenDiaBanTAO: String?,

    @Json(name = "ThoiLuong")
    val thoiLuong: String?,

    @Json(name = "TiepAm")
    val tiepAm: Long?,

    @Json(name = "NguonTiepAm")
    val nguonTiepAm: String?,

    @Json(name = "ThongTinChiTietBanTin")
    val thongTinChiTietBanTin: List<Any?>?
)

data class DanhSachNgayPhat(
    @Json(name = "NgayPhat")
    val ngayPhat: Long,

    @Json(name = "ThoiDiemPhat")
    val thoiDiemPhat: List<ThoiDiemPhat>
)

data class ThoiDiemPhat(
    @Json(name = "ThoiGianBatDau")
    val thoiGianBatDau: Long,

    @Json(name = "ThoiGianKetThuc")
    val thoiGianKetThuc: Long
)

