package app.tek4tv.ipradio.model.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_logs")
data class MediaLogModel(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    var banTinID: String? = "",
    var loaiBanTin: String? = "",
    var mucDoUuTien: String? = "",
    var tieuDe: String? = "",
    var loaiLinhVuc: String? = "",
    var noiDungTomTat: String? = "",
    var thoiGianSanXuat: Long? = 0,
    var thoiLuong: String? = "",
    var tacGia: String?,
    var thoiDiemBatDau: Long? = 0,
    var tiepAm: Long? = 0,
    var nguonTiepAm: String? = "",
    var noiDung: String? = "",
    var nguonTin: String? = "",
    var amLuong: String? = "",
    var congSuat: String? = "",
    var lichPhatID: String? = "",
    var tenLichPhat: String? = "",
    var vungPhat : String? = ""

    )