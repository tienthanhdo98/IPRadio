package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
@JsonClass(generateAdapter = true)
data class WssModel (
    val id: String?,
    val topic: String?
)

@JsonClass(generateAdapter = true)
data class WssLiveModel (
    val id: String?,
    val topic: String?,
    val payload: PayloadLive?
)

data class PayloadLive (
    @Json(name = "DichID")
    val dichID: String?,

    @Json(name = "TenDich")
    val tenDich: String?,

    @Json(name = "NguonID")
    val nguonID: String?,

    @Json(name = "TenNguon")
    val tenNguon: String?,

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DanhSachDiaBanNhan>?,

    @Json(name = "BanTinID")
    val banTinID: String?,

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

    @Json(name = "ThoiLuong")
    val thoiLuong: String?,

    @Json(name = "TacGia")
    val tacGia: TacGia?,

    @Json(name = "NoiDung")
    val noiDung: String?,

    @Json(name = "DiaBanTao")
    val diaBanTAO: String?,

    @Json(name = "TenDiaBanTao")
    val tenDiaBanTAO: String?,

    @Json(name = "ThongTinChiTietBanTin")
    val thongTinChiTietBanTin: List<ThongTinChiTietBanTin>?,

    @Json(name = "NguonTin")
    val nguonTin: String?,

    @Json(name = "DieuKhienThietBi")
    val dieuKhienThietBI: DieuKhienThietBI?,

    @Json(name = "LichPhatID")
    val lichPhatID: String?,

    @Json(name = "TenLichPhat")
    val tenLichPhat: String?,

    @Json(name = "DanhSachBanTin")
    val danhSachBanTin: List<DanhSachBanTin>?,

    @Json(name = "CreateTime")
    val createTime: Long?,

    @Json(name = "CreateDate")
    val createDate: Long?,
)



data class DanhSachDiaBanNhan (
    @Json(name = "DichID")
    val dichID: String?,

    @Json(name = "TenDich")
    val tenDich: String?,

    @Json(name = "CumLoaID")
    val cumLoaID: List<String>?
)

data class TacGia (
    @Json(name = "TenDayDu")
    val tenDayDu: String?,

    @Json(name = "ButDanh")
    val butDanh: String?,

    @Json(name = "Email")
    val email: String?
)

data class ThongTinChiTietBanTin(
    @Json(name = "Ten")
    val ten: String,

    @Json(name = "GiaTri")
    val giaTri: String
)

data class DieuKhienThietBI(
    @Json(name = "MaLenh")
    val maLenh: String?,

    @Json(name = "ThamSo")
    val thamSo: String?
)

data class DanhSachThietBi(
    @Json(name = "CumLoaID")
    val cumLoaID: String?,

    @Json(name = "MaNhaCungCap")
    val maNhaCungCap: String?,
    @Json(name = "TenNhaCungCap")
    val tenNhaCungCap: String?,
    @Json(name = "TenThietBi")
    val tenThietBi: String?,
)

data class DanhSachBanTin(
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
    val thongTinChiTietBanTin: List<ThongTinChiTietBanTin>,

    @Json(name = "NguonTin")
    val nguonTin: String,

    @Json(name = "TiepAm")
    val tiepAm: Long,

    @Json(name = "NguonTiepAm")
    val nguonTiepAm: String,

    @Json(name = "DanhSachNgayPhat")
    val danhSachNgayPhat: List<DanhSachNgayPhat>
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

