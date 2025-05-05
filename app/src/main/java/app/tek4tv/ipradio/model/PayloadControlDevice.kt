package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ReceivedMessageControlDeviceModel(
    val id: String?,
    val topic: String?,
    val payload: PayloadControlDevice?
)

@JsonClass(generateAdapter = true)
data class PayloadControlDevice (
    @Json(name = "Id")
    val id: String?,

    @Json(name = "NguonID")
    val nguonID: String?,

    @Json(name = "TenNguon")
    val tenNguon: String?,

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DanhSachDiaBanNhanControlDevice>?,

    @Json(name = "DanhSachThietBi")
    val danhSachThietBI: DanhSachThietBIControlDevice?,

    @Json(name = "DieuKhienThietBi")
    val dieuKhienThietBI: DieuKhienThietBI?,

    @Json(name = "CreateTime")
    val createTime: Long?,

    @Json(name = "CreateDate")
    val createDate: Long?,

    @Json(name = "UserName")
    val userName: String?
)
data class DieuKhienThietBI (
    @Json(name = "MaLenh")
    val maLenh: String?,

    @Json(name = "ThamSo")
    val thamSo: String?
)
data class DanhSachThietBIControlDevice  (
    @Json(name = "Id")
    val id: String?,

    @Json(name = "BangDienTuID")
    val bangDienTuID: List<String?>?
)
data class DanhSachDiaBanNhanControlDevice (
    @Json(name = "DichID")
    val dichID: String?,

    @Json(name = "TenDich")
    val tenDich: String?,

    @Json(name = "Email")
    val email: String?,

    @Json(name = "IsRead")
    val isRead: Boolean?,

    @Json(name = "CumLoaID")
    val cumLoaID: List<String>?,

    @Json(name = "BangDienTuID")
    val bangDienTuID: List<String?>?
)