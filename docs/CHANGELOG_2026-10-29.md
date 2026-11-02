# Nhật Ký Sửa Đổi & Khắc Phục Lỗi Hệ Thống (Changelog) - Ngày 29/10/2026

**Ngày thực hiện:** 29/10/2026  
**Dự án:** IPRadio  
**Mục tiêu chính:** Chuẩn hóa toàn diện State Machine (`PLAY_MODE_IDLE`), khắc phục lỗi biên dịch OkHttp 3 trong `CaCertHelper`, đồng bộ trạng thái Tuner PA khẩn cấp, phân tích tối ưu dung lượng APK và khảo sát luồng phát RTSP.

---

## 1. Tóm Tắt Các Vấn Đề Đã Xử Lý

| STT | Vấn đề / Hạng mục | Mức độ | File liên quan | Tình trạng |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Chuẩn hóa State Machine `playMode` về `PLAY_MODE_IDLE`** | **Kiến trúc & Tính ổn định** | `PlayersManager.kt`, `MainActivity.kt` | Đã hoàn thành |
| 2 | **Cập nhật thiếu trạng thái PA trong `playTunerLiveForce`** | **Trung bình (Sai lệch trạng thái PA)** | `SerialPortController.kt` | Đã xử lý |
| 3 | **Lỗi biên dịch OkHttp 3 (package-private / private field access)** | **Lỗi biên dịch (Compile Error)** | `CaCertHelper.kt` | Đã xử lý |
| 4 | **Phân tích & Tối ưu hóa dung lượng APK (từ 183MB xuống ~85MB)** | **Tối ưu hóa tài nguyên & OTA** | `app/build.gradle` | Đã cấu hình |
| 5 | **Khảo sát cơ chế bắt tay và fallback luồng RTSP của LibVLC** | **Điều tra kỹ thuật & Tương thích mạng** | `VlcMediaPlayer.kt`, `MediaItem.kt` | Đã xác minh |
| 6 | **Khôi phục phát tiếp (Resume) file âm thanh khi Live khẩn cấp bị reset** | **Nâng cấp tính năng (Feature Enhancement)** | `VlcMediaPlayer.kt`, `PlayersManager.kt` | Đã hoàn thành |

---

## 2. Chi Tiết Các Thay Đổi Theo Từng File

### 2.1. `PlayersManager.kt`
- **Gộp trạng thái rảnh/dừng về `PLAY_MODE_IDLE`:**
  - Trước đây: Khởi tạo dùng `PLAY_MODE_INIT` (`"0"`), nhưng khi dừng ca phát lại gán `""`, gây phân mảnh trạng thái và sai sót khi kiểm tra `isNotStarted`.
  - **Khắc phục:** 
    - Định nghĩa hằng số duy nhất: `const val PLAY_MODE_IDLE = ""` (đồng thời gán alias `PLAY_MODE_INIT = PLAY_MODE_IDLE`, `PLAY_MODE_STOPPED = PLAY_MODE_IDLE`).
    - Khởi tạo ban đầu: `var playMode = PLAY_MODE_IDLE`.
    - Hàm `resetPlayMode()`: gán `playMode = PLAY_MODE_IDLE`.
    - Bổ sung hàm tiện ích: `fun isIdle(): Boolean = playMode == PLAY_MODE_IDLE`.
- **Chuẩn hóa các hàm dừng phát:**
  - `stopForceMedia()`: Bổ sung `playMode = PLAY_MODE_IDLE` khi kết thúc bản tin khẩn cấp, tránh việc app bị kẹt vĩnh viễn ở cờ `PLAY_MODE_FORCE` khi không có ca phát tiếp theo.
  - `stopTimeFrame()`: Bổ sung `stopAllPlayer()`, `playMode = PLAY_MODE_IDLE`, và `isTimeOver = true` khi hết bài trong khung giờ.
  - `stopPlaylist()` & `stopAndEndTimeFrame()`: Đồng bộ đưa `playMode` về `PLAY_MODE_IDLE` và hủy toàn bộ player.
- **Hỗ trợ tính điểm Resume cho bản tin khẩn cấp dạng File:**
  - Trong hàm `playMedia()`: Nếu đang ở chế độ khẩn cấp (`playMode == PLAY_MODE_FORCE`) và `timeStamp == 0`, tự động tính toán `calculateResumeLivePoint(mediaItem)` để truyền vị trí tua tiếp cho VLC.

---

### 2.2. `VlcMediaPlayer.kt`
- **Hỗ trợ tua tiếp (`:start-time`) cho File âm thanh trong chế độ Live khẩn cấp:**
  - **Hiện trạng trước đây:** Khi thiết bị reset giữa chừng lúc phát khẩn cấp, app khởi động lại tính được thời gian đã trôi qua (vd: `resume time: 24s`), nhưng trong `playLiveStream()` lại không nạp `:start-time` vào VLC media, khiến file âm thanh luôn bị phát lại từ đầu.
  - **Khắc phục:**
    - Bổ sung hàm kiểm tra `isAudioFile(mediaItem: MediaItem)` toàn diện:
      + Nhận diện chắc chắn file âm thanh: file cục bộ đã tải về máy (`File(rawBody).exists()`, `file://`, `/files/`) hoặc link có đuôi mở rộng âm thanh chuẩn (`.mp3`, `.wav`, `.aac`, `.m4a`, `.ogg`, `.flac`, `.wma`, `.opus`).
      + Chặn tuyệt đối mọi giao thức Live streaming: `rtsp://`, `rtsps://`, `rtmp://`, `rtmps://`, `srt://`, `mms://`, `mmsh://`.
      + Chặn theo `contentType`: loại trừ `TYPE_LIVE` (5) và `TYPE_FM` (6).
      + Chặn playlist luồng trực tiếp: `.m3u8`, `.mpd`, `.flv`.
      + Xử lý chuẩn xác các luồng trực tiếp HTTP/HTTPS không có đuôi (như Icecast, Shoutcast, live streaming endpoints): do không chứa đuôi file âm thanh và không phải file cục bộ, hệ thống sẽ xác định là luồng trực tiếp và không thêm `:start-time`.
    - Trong `playLiveStream()`: Nếu là file âm thanh và `currentTimeStamp > 0`, bổ sung option `:start-time=$currentTimeStamp` vào đối tượng `Media` của LibVLC để tua tiếp chính xác.
    - Đối với luồng trực tiếp (RTSP/RTMP/HLS/HTTP Live): Giữ nguyên không thêm `:start-time` để bảo đảm kết nối live edge ổn định.

---

### 2.3. `SerialPortController.kt`
- **Bổ sung trạng thái cho `playTunerLiveForce(data: String)`:**
  - Trước đây: Hàm chỉ gửi mã lệnh `FUNC_WRITE_PLAY_TUNER_FORCE` xuống MCU nhưng quên cập nhật `playMode = MODE_TUNER`.
  - **Khắc phục:** 
    - Gán `playMode = MODE_TUNER`.
    - Lưu trữ `lastTunerData = data` phục vụ cơ chế tự phục hồi `syncPAState()`.
    - Ghi log rõ ràng: `Log.e("PA", "TUNER FORCE")`.

---

### 2.3. `CaCertHelper.kt`
- **Khắc phục lỗi biên dịch không truy cập được thuộc tính OkHttp 3:**
  - Dự án sử dụng thư viện OkHttp 3.14.9 (viết bằng Java thuần, getter không có tiền tố `get`). Cú pháp truy cập property của Kotlin dẫn đến việc truy cập nhầm vào các biến nội bộ `package-private` / `private` của Java.
  - **Khắc phục:** Chuyển sang gọi đúng method công khai có dấu ngoặc tròn `()`:
    - Dòng 233: `response.handshake?.peerCertificates` ➔ `response.handshake()?.peerCertificates()`
    - Dòng 453-454: `response.body != null` / `response.body!!.bytes()` ➔ `response.body() != null` / `response.body()!!.bytes()`
    - Dòng 476: `response.code` ➔ `response.code()`
    - Dòng 686-687: `request.url.toString()` / `request.url.host` ➔ `request.url().toString()` / `request.url().host()`

---

### 2.4. `MainActivity.kt`
- **Đồng bộ kiểm tra trạng thái mạng với `isIdle()`:**
  - Tại callback `NetworkUtils.onChange`: Thay thế `val isNotStarted = playerManager.playMode == PlayersManager.PLAY_MODE_INIT` thành:
    ```kotlin
    val isNotStarted = playerManager.isIdle()
    ```
  - Đảm bảo khi mạng kết nối lại, dù app mới mở hay vừa kết thúc một ca phát trước đó thì vẫn nhận diện chính xác trạng thái rảnh rỗi để kích hoạt `startPlayingMedia()`.

---

### 2.5. `app/build.gradle` (Tối ưu dung lượng APK)
- **Cấu hình lọc kiến trúc CPU (`abiFilters`):**
  - Phân tích chi tiết file APK release (`IPRadioV19.4.2.2e.apk` nặng 183MB): Riêng 4 file `libvlc.so` của 4 kiến trúc CPU (`x86_64`, `x86`, `arm64-v8a`, `armeabi-v7a`) đã chiếm tới 177.2 MB.
  - Thiết bị IPRadio thực tế chỉ chạy chip ARM (32-bit `armeabi-v7a` hoặc 64-bit `arm64-v8a`), hoàn toàn không dùng kiến trúc máy tính `x86`/`x86_64`.
  - **Cấu hình:**
    ```groovy
    defaultConfig {
        ...
        ndk {
            abiFilters "armeabi-v7a", "arm64-v8a"
        }
    }
    ```
  - **Kết quả:** Loại bỏ hoàn toàn 2 kiến trúc PC thừa thãi, giảm kích thước APK phát hành từ **183MB xuống còn ~85MB** (giảm gần 100MB, tăng tốc độ tải OTA qua 4G gấp 2 lần).

---

### 2.6. Khảo sát & Kiểm thử luồng RTSP (`rtsp://stream.tek4tv.vn:8554/...`)
- **Thử nghiệm gói tin thực tế với server MediaMTX (`gortsplib`):**
  - Thử bằng UDP: Server trả về `RTSP/1.0 461 Unsupported Transport` (Server đã tắt UDP).
  - Thử bằng TCP (Interleaved): Server trả về `RTSP/1.0 200 OK`, thiết lập Session thành công ngay lập tức.
- **Làm rõ cơ chế Fallback của LibVLC:**
  - Khi không có `--rtsp-tcp`, LibVLC thử UDP trước ➔ In ra 2 dòng log cảnh báo `Unable to determine our source address: 0.0.0.0` ➔ LibVLC tự động fallback sang TCP sau ~0.039s ➔ Phát thành công (`Playing: vov`).
  - Xác nhận: Dòng log `0.0.0.0` chỉ là log của bước thử UDP ban đầu, app vẫn phát được bình thường qua TCP.

---

### 2.7. Tối ưu hoá Cơ chế đồng bộ lịch nhiều ngày & Khôi phục khi có mạng trở lại
- **Sửa logic `lastUpdateFailed` ([`MediaRepo.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/repo/MediaRepo.kt)):**
  - Khi danh sách tháng rỗng (`monthlyDataList.isEmpty()`) hoặc tải tháng thất bại do mất mạng/server timeout, đánh dấu chính xác `lastUpdateFailed = true`.
  - Phân biệt rõ giữa lỗi mất mạng (`lastUpdateFailed = true`) và ngày nghỉ bình thường không có lịch trên CMS (`lastUpdateFailed = false`).
- **Tiến trình kiểm tra định kỳ 30 phút ([`MainViewModel.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/viewmodels/MainViewModel.kt)):**
  - Cập nhật chu kỳ kiểm tra định kỳ cố định **30 phút/lần** khi lịch hôm nay đang rỗng (`content.value.isNullOrEmpty()`).
  - Đảm bảo luồng không bị dừng (chết yểu) sau 40 lần thử, giúp tự động kéo lịch chiều và phát chính xác khi mạng có lại (ví dụ lúc 07:00) hoặc khi quản trị viên tạo thêm lịch phát buổi chiều trên CMS.
  - Khi tải được lịch hợp lệ (`mediaList.isNotEmpty()`), tiến trình tự động hủy để tiết kiệm tài nguyên.
- **Kích hoạt kéo lịch tức thì khi Hub SignalR kết nối lại ([`HubManager.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/HubManager.kt) & [`MainActivity.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt)):**
  - Bổ sung callback `onHubConnectedListener` trong `HubManager`.
  - Trong `MainActivity`: Lắng nghe khi Hub kết nối thành công, có cơ chế cooldown tối thiểu **5 phút** và chỉ kích hoạt khi thiết bị chưa có lịch và đang rảnh rỗi (`!playerManager.isPlaying()`, `playerManager.isIdle()`). Tránh hoàn toàn việc gọi trùng lặp khi mạng chập chờn.

---

## 3. Kết Quả Kiểm Thử (Verification)
- **Đã bổ sung bộ Unit Test chuyên biệt:** [`PeriodicScheduleAndRecoveryTest.kt`](file:///d:/Git/IPRadio/app/src/test/java/app/tek4tv/ipradio/PeriodicScheduleAndRecoveryTest.kt) gồm 6 test cases:
  1. `testLastUpdateFailed_networkErrorAtBootOr0015_marksTrue`: Đạt (PASSED).
  2. `testLastUpdateFailed_validMonthWithoutToday_marksFalse`: Đạt (PASSED).
  3. `testLastUpdateFailed_todayScheduled_networkFetchSucceeds_marksFalse`: Đạt (PASSED).
  4. `testPeriodicSyncLoop_maintains30MinutesInterval_untilScheduleLoaded`: Đạt (PASSED).
  5. `testHubReconnect_5MinutesCooldown_preventsRapidDuplicateCalls`: Đạt (PASSED).
  6. `testHubReconnect_guardConditions_preventInterruptingPlayback`: Đạt (PASSED).
- **Kết quả thực thi Test:**
  - Lệnh thực thi: `./gradlew testDebugUnitTest --offline`
  - Kết quả: **BUILD SUCCESSFUL in 24s** (6/6 tests mới và toàn bộ unit test suite đều đạt 100%, 0 failures, 0 errors).
