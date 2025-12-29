package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class itemMedia
    (
    var time: ThoiDiemPhat,
    var danhSachBanTin: BanTin
)

@JsonClass(generateAdapter = true)
data class SchedulePayload(
    @Json(name = "NguonID") val nguonID: String?,
    @Json(name = "TenNguon") val tenNguon: String?,
    @Json(name = "DichID") val dichID: String?,
    @Json(name = "TenDich") val tenDich: String?,
    @Json(name = "DanhSachDiaBanNhan") val danhSachDiaBanNhan: List<DiaBanNhan> = emptyList(),
    @Json(name = "LichPhatID") val lichPhatID: String?,
    @Json(name = "TenLichPhat") val tenLichPhat: String?,
    @Json(name = "DanhSachBanTin") val danhSachBanTin: List<BanTin> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DiaBanNhan(
    @Json(name = "DichID") val dichID: String?,
    @Json(name = "TenDich") val tenDich: String?,
    @Json(name = "CumLoaID") val cumLoaID: Any? = null

)

@JsonClass(generateAdapter = true)
data class BanTin(
    @Json(name = "BanTinID") val banTinID: String?,
    @Json(name = "LoaiBanTin") val loaiBanTin: String?,
    @Json(name = "MucDoUuTien") val mucDoUuTien: String?,
    @Json(name = "TieuDe") val tieuDe: String?,
    @Json(name = "LoaiLinhVuc") val loaiLinhVuc: String?,
    @Json(name = "NoiDungTomTat") val noiDungTomTat: String?,
    @Json(name = "ThoiGianSanXuat") val thoiGianSanXuat: Long?,
    @Json(name = "ThoiLuong") val thoiLuong: String?,
    @Json(name = "TacGia") val tacGia: TacGia?,
    @Json(name = "NoiDung") val noiDung: String?,
    @Json(name = "DiaBanTao") val diaBanTao: String?,
    @Json(name = "TenDiaBanTao") val tenDiaBanTao: String?,
    @Json(name = "ThongTinChiTietBanTin") val thongTinChiTietBanTin: Any? = null,
    @Json(name = "NguonTin") val nguonTin: String?,
    @Json(name = "TiepAm") val tiepAm: Int? = null,
    @Json(name = "NguonTiepAm") val nguonTiepAm: String? = null,
    @Json(name = "DanhSachNgayPhat") val danhSachNgayPhat: List<NgayPhat> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TacGia(
    @Json(name = "TenDayDu") val tenDayDu: String?,
    @Json(name = "ButDanh") val butDanh: String?,
    @Json(name = "Email") val email: String?
)

@JsonClass(generateAdapter = true)
data class NgayPhat(
    @Json(name = "NgayPhat") val ngayPhat: Long?,
    @Json(name = "ThoiDiemPhat") val thoiDiemPhat: List<ThoiDiemPhat> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ThoiDiemPhat(
    @Json(name = "ThoiGianBatDau") val thoiGianBatDau: Long?,
    @Json(name = "ThoiGianKetThuc") val thoiGianKetThuc: Long?
)