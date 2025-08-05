package app.tek4tv.ipradio.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatusModel(
    var id: String?,
    var topic: String?,
    var payload: Any,
)

data class PayloadStatus(
    @Json(name = "NguonID")
    var nguonID: String,

    @Json(name = "TenNguon")
    var tenNguon: String,

    @Json(name = "DichID")
    var dichID: String,

    @Json(name = "TenDich")
    var tenDich: String,

    @Json(name = "BangDienTuID")
    var bangDienTuID: String,

    @Json(name = "TenThietBi")
    var tenThietBI: String,

    @Json(name = "TenLoaiThietBi")
    var tenLoaiThietBI: String,

    @Json(name = "MaNhaCungCap")
    var maNhaCungCap: String,

    @Json(name = "TenNhaCungCap")
    var tenNhaCungCap: String,

    @Json(name = "AmLuong")
    var amLuong: String,

    @Json(name = "TrangThaiKetNoi")
    var trangThaiKetNoi: String,

    @Json(name = "TrangThai")
    var trangThai: String,

    @Json(name = "ViDo")
    var viDo: Float,

    @Json(name = "KinhDo")
    var kinhDo: Float,

    @Json(name = "ThongTinThietBi")
    var thongTinThietBI: ThongTinThietBI,

    @Json(name = "ThoiDiemBatDau")
    var thoiDiemBatDau: Long,

    @Json(name = "NoiDungPhat")
    var noiDungPhat: List<String>,

//    @Json(name = "NoiDungPhat")
//    var noiDungPhat: String,

    @Json(name = "PhienBanUngDung")
    var phienBanUngDung: String,

    )

data class ThongTinThietBI(
    @Json(name = "CongSuat")
    var congSuat: String,

    @Json(name = "NhietDo")
    var nhietDo: String,

    @Json(name = "DungLuongSuDung")
    var dungLuongSuDung: String,

    @Json(name = "DuLieuKhac")
    var duLieuKhac: String
)