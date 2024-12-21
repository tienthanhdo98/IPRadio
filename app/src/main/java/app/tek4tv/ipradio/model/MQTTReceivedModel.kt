package app.tek4tv.ipradio.model

import app.tek4tv.ipradio.core.TacGia
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MQTTReceivedModel(
    val id: String?,
    val topic: String?,
    val payload: ReceivedPayloadSchedule?
)

data class ReceivedPayloadSchedule (
    @Json(name = "Id")
    val id: String?,

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
    val danhSachNgayPhat: List<DanhSachNgayPhat>?,

    @Json(name = "CreateTime")
    val createTime: Long?,

    @Json(name = "CreateDate")
    val createDate: Long?,

    @Json(name = "UserName")
    val userName: String?
)

data class DanhSachDiaBanNhan (
    @Json(name = "DichID")
    val dichID: String?,

    @Json(name = "TenDich")
    val tenDich: String?,

    @Json(name = "Email")
    val email: String?,

    @Json(name = "IsRead")
    val isRead: Boolean?,

    @Json(name = "CumLoaID")
    val cumLoaID: List<Any?>?,

    @Json(name = "BangDienTuID")
    val bangDienTuID: List<Any?>
)

data class DanhSachNgayPhat (
    @Json(name = "NgayPhat")
    val ngayPhat: Long?,

    @Json(name = "ThoiDiemPhat")
    val thoiDiemPhat: List<ThoiDiemPhat>
)

data class ThoiDiemPhat (
    @Json(name = "ThoiGianBatDau")
    val thoiGianBatDau: Long?,

    @Json(name = "ThoiGianKetThuc")
    val thoiGianKetThuc: Long?,

    @Json(name = "DanhSachBanTin")
    val danhSachBanTin: List<DanhSachBanTin>?
)

data class DanhSachBanTin (
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
    val thongTinChiTietBanTin: List<Any?>?,

    @Json(name = "NguonTin")
    val nguonTin: String?,

    @Json(name = "DanhSachNgayPhat")
    val danhSachNgayPhat: List<Any?>
)

