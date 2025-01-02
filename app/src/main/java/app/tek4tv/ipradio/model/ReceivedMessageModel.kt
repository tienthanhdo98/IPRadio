package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReceivedMessageModel(
    val id: String?,
    val topic: String?,
    val payload: Any?
)


data class TacGia (
    @Json(name = "TenDayDu")
    val tenDayDu: String?,

    @Json(name = "TenDangNhap")
    val tenDangNhap: String?,

    @Json(name = "ButDanh")
    val butDanh: String?,

    @Json(name = "Email")
    val email: String?
)

//data class ThongTinChiTietBanTin(
//    @Json(name = "ThoiGianBatDau")
//    val thoiGianBatDau: Long,
//
//    @Json(name = "ThoiGianKetThuc")
//    val thoiGianKetThuc: Long
//)
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




