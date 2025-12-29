package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class LogAddSchedulePayload(
    @Json(name = "DanhSachBanTin") val danhSachBanTin: List<BanTin>?,
    @Json(name = "DanhSachDiaBan") val danhSachDiaBan: List<DiaBanNhan>?,
    @Json(name = "DanhSachThietBi") val danhSachThietBi: List<ThietBi>?,

    @Json(name = "DichID") val dichId: String?,
    @Json(name = "LichPhatID") val lichPhatId: String?,
    @Json(name = "NguonID") val nguonId: String?,

    @Json(name = "TenDich") val tenDich: String?,
    @Json(name = "TenLichPhat") val tenLichPhat: String?,
    @Json(name = "TenNguon") val tenNguon: String?
)

@JsonClass(generateAdapter = true)
data class ThietBi(
    @Json(name = "CumLoaID") val cumLoaId: String?,
    @Json(name = "MaNhaCungCap") val maNhaCungCap: String?,
    @Json(name = "TenNhaCungCap") val tenNhaCungCap: String?,
    @Json(name = "TenThietBi") val tenThietBi: String?
)