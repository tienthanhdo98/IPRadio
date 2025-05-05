package app.tek4tv.ipradio.model

import com.squareup.moshi.Json

data class WssPlaylist (
    @Json(name = "DichID")
    val dichID: String,

    @Json(name = "TenDich")
    val tenDich: String,

    @Json(name = "NguonID")
    val nguonID: String,

    @Json(name = "TenNguon")
    val tenNguon: String,

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DanhSachDiaBanNhan>,

    @Json(name = "LichPhatID")
    val lichPhatID: String,

    @Json(name = "TenLichPhat")
    val tenLichPhat: String,

    @Json(name = "DanhSachBanTin")
    val danhSachBanTin: List<DanhSachBanTin>
)






