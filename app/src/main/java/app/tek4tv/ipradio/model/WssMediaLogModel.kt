package app.tek4tv.ipradio.model


data class WssMediaLogModel(
    //dv005 khac model
    //dv004 khac han
    //dv006 - fail, khac so voi 2455
    val nguonID: String = "",
    val tenNguon: String? = "",
    val dichID: String = "",
    val tenDich: String? = "",
    var danhSachDiaBan: List<DanhSachDiaBanNhan>?,
    var danhSachThietBi: List<DanhSachThietBi>,
    //thuoc danh sach tb
    val cumLoaID: String? = "",
    val tenThietBi: String? = "",
    val maNhaCungCap: String? = "",
    val tenNhaCungCap: String? = "",
    //
    var lichPhatID: String? = "",
    var tenLichPhat: String? = "",
    var danhSachBanTin: List<DanhSachBanTin>?,
    //thuoc DanhSachBanTin
    val banTinID: String? = "",
    val loaiBanTin: String? = "",
    val mucDoUuTien: String? = "",
    val tieuDe: String? = "",
    val loaiLinhVuc: String? = "",
    val noiDungTomTat: String? = "",
    val thoiGianSanXuat: Long? = 0,
    val thoiLuong: String? = "",
    val tacGia: TacGia?,
    val noiDung: String? = "",
    val thoiDiemBatDau: Long? = 0,
    val nguonTin: String? = "",
    val tiepAm: Long? = 0,
    val nguonTiepAm: String? = "",
    val thongTinChiTietBanTin: List<ThongTinChiTietBanTin>? = listOf(),

    val amLuong: String? = "",
    val congSuat: String? = "",
    //Thieu diaBanTao/ TenDiaBanTao/thong tin chi tiet ban tin  //Thieu DanhSachNgayPhat


)