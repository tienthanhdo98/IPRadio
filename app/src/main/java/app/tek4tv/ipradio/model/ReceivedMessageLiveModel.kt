package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ReceivedMessageLiveModel(
    val id: String?,
    val topic: String?,
    val payload: ReceivedPayloadLive?
)



data class ReceivedPayloadLive(
    @Json(name = "Id")
    val id: String?,

    @Json(name = "NguonID")
    val nguonID: String?,

    @Json(name = "TenNguon")
    val tenNguon: String?,

    @Json(name = "DanhSachDiaBanNhan")
    val danhSachDiaBanNhan: List<DanhSachDiaBanNhan>?,

    @Json(name = "PhanVungManHinh")
    val phanVungManHinh: String?,

    @Json(name = "DanhSachBanTin")
    val danhSachBanTin: List<DanhSachBanTin>?,

    @Json(name = "CreateTime")
    val createTime: Long?,

    @Json(name = "CreateDate")
    val createDate: Long?,

    @Json(name = "UserName")
    val userName: String?
)



