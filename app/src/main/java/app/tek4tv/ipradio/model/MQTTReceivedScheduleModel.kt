package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

//model dv 014a gui len khi thiet bi nhan duoc lich(dv010)
data class MQTTLogScheduleModel(
    var NguonID: String?,
    var TenNguon: String?,
    var DichID: String?,
    var TenDich: String?,
    var DanhSachDiaBanNhan: List<DanhSachDiaBanNhan?>?,
    var PhanVungManHinh: String?,
    var LichPhatID: String?,
    var TenLichPhat: String?,
    var DanhSachNgayPhat: List<DanhSachNgayPhat?>?
)

@JsonClass(generateAdapter = true)
data class MQTTReceivedScheduleModel(
    var id: String?,
    var topic: String?,
    var payload: ReceivedPayloadSchedule?
)
@JsonClass(generateAdapter = true)
data class ReceivedPayloadSchedule(
    @Json(name = "Id")
    val Id: String?,

    @Json(name = "NguonID")
    val nguonID: String?,

    @Json(name = "TenNguon")
    val tenNguon: String?,

    @Json(name = "DichID")
    val dichID: String?,

    @Json(name = "TenDich")
    val tenDich: String?,

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DanhSachDiaBanNhan>?,

    @Json(name = "PhanVungManHinh")
    val phanVungManHinh: String?,

    @Json(name = "LichPhatID")
    val lichPhatID: String?,

    @Json(name = "TenLichPhat")
    val tenLichPhat: String?,

    @Json(name = "DanhSachNgayPhat")
    val DanhSachNgayPhat: List<DanhSachNgayPhat?>? = null,

    @Json(name = "GioBatDau")
    val GioBatDau: String?,

    @Json(name = "GioKeThuc")
    val GioKeThuc: String?,

    @Json(name = "DanhSachBanTin")
    val danhSachBanTin: List<DanhSachBanTin>?
)
@JsonClass(generateAdapter = true)
data class DanhSachDiaBanNhan (
    @Json(name = "DichID")
    var dichID: String?,

    @Json(name = "TenDich")
    var tenDich: String?,

    @Json(name = "CumLoaID")
    var cumLoaID: List<Any?>?,

    @Json(name = "BangDienTuID")
    var bangDienTuID: List<Any?>?
)
@JsonClass(generateAdapter = true)
data class DanhSachNgayPhat (
    @Json(name = "NgayPhat")
    var ngayPhat: Long?,

    @Json(name = "ThoiDiemPhat")
    var thoiDiemPhat: List<ThoiDiemPhat>?
)
@JsonClass(generateAdapter = true)
data class ThoiDiemPhat (
    @Json(name = "ThoiGianBatDau")
    var thoiGianBatDau: Long?,

    @Json(name = "ThoiGianKetThuc")
    var thoiGianKetThuc: Long?,

    @Json(name = "DanhSachBanTin")
    var danhSachBanTin: List<DanhSachBanTin>?
)
@JsonClass(generateAdapter = true)
data class DanhSachBanTin (
    @Json(name = "VungPhat")
    var vungPhat: String?,

    @Json(name = "BanTinCongCongID")
    var banTinCongCongID: String?,

    @Json(name = "LoaiBanTin")
    var loaiBanTin: String?,

    @Json(name = "MucDoUuTien")
    var mucDoUuTien: String?,

    @Json(name = "TieuDe")
    var tieuDe: String?,

    @Json(name = "LoaiLinhVuc")
    var loaiLinhVuc: String?,

    @Json(name = "NoiDungTomTat")
    var noiDungTomTat: String?,

    @Json(name = "ThoiGianSanXuat")
    var thoiGianSANXuat: Long?,

    @Json(name = "ThoiLuong")
    var thoiLuong: String?,

    @Json(name = "TacGia")
    var tacGia: TacGia?,

    @Json(name = "NoiDung")
    var noiDung: String?,

    @Json(name = "DiaBanTao")
    var diaBanTAO: String?,

    @Json(name = "TenDiaBanTao")
    var tenDiaBanTAO: String?,

    @Json(name = "ThongTinChiTietBanTin")
    var thongTinChiTietBanTin: List<ChiTietBanTin?>?,

    @Json(name = "NguonTin")
    var nguonTin: String?,

//    @Json(name = "DanhSachNgayPhat")
//    var danhSachNgayPhat: List<DanhSachNgayPhat?>
)

data class ChiTietBanTin(
    var Ten: String?,
    var GiaTri: String?
)



