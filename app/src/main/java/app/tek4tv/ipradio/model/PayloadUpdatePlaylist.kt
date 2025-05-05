package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReceivedMessageUpdatePlaylistModel(
    val id: String?,
    val topic: String?,
    val payload: PayloadUpdatePlaylist?
)

data class PayloadUpdatePlaylist (
    @Json(name = "NguonID")
    val nguonID: String,

    @Json(name = "TenNguon")
    val tenNguon: String,

    @Json(name = "DichID")
    val dichID: String,

    @Json(name = "TenDich")
    val tenDich: String,

    @Json(name = "DanhSachDiaBan")
    val danhSachDiaBan: List<DanhSachDiaBanNhan>,

    @Json(name = "DanhSachThietBi")
    val danhSachThietBI: List<DanhSachThietBi>,

    @Json(name = "PhanVungManHinh")
    val phanVungManHinh: String,

    @Json(name = "LichPhatID")
    val lichPhatID: String,

    @Json(name = "TenLichPhat")
    val tenLichPhat: String,

    @Json(name = "DanhSachBanTin")
    val danhSachBanTin: List<DanhSachBanTin>
)
