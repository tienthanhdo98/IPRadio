package app.tek4tv.ipradio.model

import app.tek4tv.ipradio.core.*

data class WssMediaLogModel(
    val nguonID: String? = "",
    val tenNguon: String? = "",
    val dichID: String? = "",
    val tenDich: String? = "",
    val cumLoaID: String? = "",
    val tenThietBi: String? = "",
    val maNhaCungCap: String? = "",
    val tenNhaCungCap: String? = "",
    val banTinID: String?= "",
    val loaiBanTin: String?= "",
    val mucDoUuTien: String?= "",
    val tieuDe: String?= "",
    val loaiLinhVuc: String? = "",
    val noiDungTomTat: String?= "",
    val thoiGianSanXuat: Long? = 0,
    val thoiLuong: String? = "",
    val tacGia: TacGia?,
    val thoiDiemBatDau: Long? = 0,
    val tiepAm: Long? = 0,
    val nguonTiepAm: String? = "",
    val noiDung: String? = "",
    val thongTinChiTietBanTin: List<ThongTinChiTietBanTin>? = listOf(),
    val nguonTin: String? = "",
    val amLuong: String? = "",
    val congSuat: String? = "",
    var lichPhatID: String? = "",
    var tenLichPhat: String? = "",
    var danhSachDiaBan: List<DanhSachDiaBanNhan>?,
    var danhSachBanTin:  List<DanhSachBanTin>?,
    var danhSachThietBi:  List<DanhSachThietBi>?,

    )