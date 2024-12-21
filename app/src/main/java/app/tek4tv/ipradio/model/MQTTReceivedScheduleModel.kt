package app.tek4tv.ipradio.model

import app.tek4tv.ipradio.core.TacGia
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

data class ReceivedPayloadSchedule (
    @Json(name = "Id")
    var id: String?,

    @Json(name = "NguonID")
    var nguonID: String?,

    @Json(name = "TenNguon")
    var tenNguon: String?,

    @Json(name = "DichID")
    var dichID: String?,

    @Json(name = "TenDich")
    var tenDich: String?,

    @Json(name = "DanhSachDiaBanNhan")
    var danhSachDiaBanNhan: List<DanhSachDiaBanNhan>?,

    @Json(name = "PhanVungManHinh")
    var phanVungManHinh: String?,

    @Json(name = "LichPhatID")
    var lichPhatID: String?,

    @Json(name = "TenLichPhat")
    var tenLichPhat: String?,

    @Json(name = "DanhSachNgayPhat")
    var danhSachNgayPhat: List<DanhSachNgayPhat>?,

    @Json(name = "CreateTime")
    var createTime: Long?,

    @Json(name = "CreateDate")
    var createDate: Long?,

    @Json(name = "UserName")
    var userName: String?
)

data class DanhSachDiaBanNhan (
    @Json(name = "DichID")
    var dichID: String?,

    @Json(name = "TenDich")
    var tenDich: String?,

    @Json(name = "Email")
    var email: String?,

    @Json(name = "IsRead")
    var isRead: Boolean?,

    @Json(name = "CumLoaID")
    var cumLoaID: List<Any?>?,

    @Json(name = "BangDienTuID")
    var bangDienTuID: List<Any?>
)

data class DanhSachNgayPhat (
    @Json(name = "NgayPhat")
    var ngayPhat: Long?,

    @Json(name = "ThoiDiemPhat")
    var thoiDiemPhat: List<ThoiDiemPhat>
)

data class ThoiDiemPhat (
    @Json(name = "ThoiGianBatDau")
    var thoiGianBatDau: Long?,

    @Json(name = "ThoiGianKetThuc")
    var thoiGianKetThuc: Long?,

    @Json(name = "DanhSachBanTin")
    var danhSachBanTin: List<DanhSachBanTin>?
)

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

    @Json(name = "DanhSachNgayPhat")
    var danhSachNgayPhat: List<Any?>
)
data class ChiTietBanTin(
    var Ten: String?,
    var GiaTri: String?
)

