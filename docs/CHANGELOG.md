# Danh Sách Nhật Ký Thay Đổi (Changelogs) - Dự án IPRadio

Tài liệu ghi lại toàn bộ lịch sử các đợt sửa lỗi, nâng cấp kiến trúc và tối ưu hóa hệ thống trên dự án IPRadio.

---

### Danh sách các bản cập nhật:

* **[16/11/2026 - Đồng bộ & Nâng cấp toàn diện nhánh `ipradio_mqtt`](file:///d:/Git/IPRadio/docs/CHANGELOG_2026-11-16.md)**
  * **Nhóm 1 (Core & Hardware):**
    * Tích hợp module tự động nạp chứng chỉ SSL CA động ([`CaCertHelper.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/network/CaCertHelper.kt)).
    * Triển khai cơ chế PA Safety Watchdog 2 chiều (bảo vệ chống cháy loa, rò rỉ PA và phục hồi âm lượng khi sụt áp nguồn).
    * Sửa lỗi crash đệ quy vô tận trong `VlcMediaPlayer` và sửa lỗi rò rỉ cờ FM Player trong `PlayersManager`.
    * Chuẩn hóa State Machine `playMode` về `PLAY_MODE_IDLE`.
    * Hỗ trợ khung giờ phát vắt qua nửa đêm (Cross-midnight).
    * Tối ưu dọn dẹp bộ nhớ an toàn (bảo vệ file lịch hôm nay, tiết kiệm 4G).
    * Giảm 50% dung lượng APK từ ~183MB xuống ~85MB qua `abiFilters`.
  * **Nhóm 2 (Stability, Resilience & Testing):**
    * Ghi log crash ra `logerror.txt` và cơ chế chống bão crash loop ([`CrashHandler.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/CrashHandler.kt)).
    * Phân tích thời gian an toàn và triệt tiêu sai số so sánh micro-giây ([`TimeUtils.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/TimeUtils.kt)).
    * Vòng lặp định kỳ 30 phút tự kiểm tra lịch phát khi hôm nay trống ([`MainViewModel.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/viewmodels/MainViewModel.kt), [`MainActivity.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt)).
    * Tự động dọn dẹp file `ipradio.apk` rác sau cập nhật OTA ([`UpdateUtils.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/UpdateUtils.kt)).
    * Làm sạch URL (mã hóa ký tự `#`) và chống `NullPointerException` khi nạp khẩn cấp ([`MediaItem.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/model/MediaItem.kt), [`ForceMedia.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/model/ForceMedia.kt)).
    * Tích hợp bộ 37 Unit Tests tự động, kiểm thử 100% Passed: [`CaCertLogicTest.kt`](file:///d:/Git/IPRadio/app/src/test/java/app/tek4tv/ipradio/CaCertLogicTest.kt), [`PlaybackLogicTest.kt`](file:///d:/Git/IPRadio/app/src/test/java/app/tek4tv/ipradio/PlaybackLogicTest.kt), [`TimeframeAndForceMediaTest.kt`](file:///d:/Git/IPRadio/app/src/test/java/app/tek4tv/ipradio/TimeframeAndForceMediaTest.kt).
  * **Kết quả kiểm thử:**
    * Biên dịch Kotlin: **BUILD SUCCESSFUL** (0 errors).
    * Chạy Unit Tests: **37/37 PASSED** (100% Pass).
