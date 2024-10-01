package app.tek4tv.ipradio.core

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class ReceivedMessageRemovePlaylistModel(
    val id: String?,
    val topic: String?,
    val payload: PayloadRemovePlaylist?
)


data class PayloadRemovePlaylist(
    @Json(name = "Id")
    val id: String? = "",

    @Json(name = "NguonID")
    val nguonID: String? = "",

    @Json(name = "TenNguon")
    val tenNguon: String? = "",

    @Json(name = "LichPhatID")
    val lichPhatID: String? = "",

    @Json(name = "TenLichPhat")
    val tenLichPhat: String? = "",

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DanhSachDiaBanNhanControlDevice>?,

    @Json(name = "CreateTime")
    val createTime: Long?,

    @Json(name = "CreateDate")
    val createDate: Long?,

    @Json(name = "UserName")
    val userName: String? = ""
)

data class PayloadSendLogRemovePlaylist (
    @Json(name = "NguonID")
    val nguonID: String?,

    @Json(name = "TenNguon")
    val tenNguon: String?,

    @Json(name = "DichID")
    val dichID: String?,

    @Json(name = "TenDich")
    val tenDich: String?,

    @Json(name = "DanhSachThietBi")
    val danhSachThietBI: List<DanhSachThietBi>?,

    @Json(name = "LichPhatID")
    val lichPhatID: String?,

    @Json(name = "TenLichPhat")
    val tenLichPhat: String?
)