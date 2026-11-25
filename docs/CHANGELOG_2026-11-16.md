# Nhật Ký Thay Đổi & Nâng Cấp Hệ Thống (Changelog) - Nhánh `ipradio_mqtt`

**Ngày thực hiện:** 16/11/2026  
**Nhánh áp dụng:** `ipradio_mqtt`  
**Mục tiêu chính:** Tích hợp và đồng bộ toàn bộ các cải tiến cốt lõi và ổn định từ nhánh `4.2.2b` sang nhánh `ipradio_mqtt`:
- **Nhóm 1 (Core & Hardware):** Module tự động nạp chứng chỉ SSL CA động (`CaCertHelper`), cơ chế bảo vệ công suất loa (PA Safety Watchdog 2 chiều), xử lý triệt để lỗi crash đệ quy và kẹt trạng thái của Player, tối ưu hóa dọn dẹp bộ nhớ an toàn (tiết kiệm 4G) và giảm 50% dung lượng APK.
- **Nhóm 2 (Stability, Resilience & Testing):** Ghi log lỗi crash ra file vật lý và chống bão crash lặp (`CrashHandler`), chuẩn hóa an toàn phân tích thời gian (`TimeUtils`), vòng lặp định kỳ 30 phút tự kiểm tra lịch phát (`MainViewModel`), dọn dẹp file APK sau nâng cấp (`UpdateUtils`), làm sạch URL mã hóa dấu `#` và chống NPE trong `ForceMedia`/`MediaItem`, cùng bộ 37 Unit Tests tự động.

---

## 1. Tóm Tắt Toàn Bộ Các Hạng Mục Nâng Cấp

| STT | Hạng mục / Lỗi xử lý | Mức độ quan trọng | File liên quan | Tình trạng |
| :---: | :--- | :--- | :--- | :---: |
| 1 | **Tự động lấy & nạp chứng chỉ SSL động (`CaCertHelper`)** | **Bảo mật & Kết nối (ATTT)** | `CaCertHelper.kt`, `MyApp.kt`, `NetworkModule.kt`, `MediaRepo.kt`, `VlcMediaPlayer.kt` | Đã tích hợp |
| 2 | **Cơ chế PA Safety Watchdog 2 chiều (Chống cháy loa / hú rít)** | **Cực kỳ nghiêm trọng (Phần cứng)** | `SerialPortController.kt`, `MainActivity.kt` | Đã xử lý |
| 3 | **Sửa lỗi đệ quy vô tận gây Crash (`StackOverflowError`)** | **Nghiêm trọng (Crash ứng dụng)** | `VlcMediaPlayer.kt` | Đã xử lý |
| 4 | **Sửa lỗi rò rỉ cờ FM Player khiến `isPlaying()` luôn trả về `true`** | **Nghiêm trọng (Kẹt PA bật liên tục)** | `PlayersManager.kt` | Đã xử lý |
| 5 | **Chuẩn hóa State Machine `playMode` về `PLAY_MODE_IDLE`** | **Kiến trúc & Tính ổn định** | `PlayersManager.kt` | Đã xử lý |
| 6 | **Hỗ trợ khung giờ phát vắt qua nửa đêm (Cross-midnight 23:00 - 01:00)** | **Độ chính xác lịch phát** | `PlayersManager.kt` | Đã xử lý |
| 7 | **Hỗ trợ Resume `:start-time` cho File khẩn cấp khi thiết bị reboot** | **Nâng cấp trải nghiệm phát** | `VlcMediaPlayer.kt`, `PlayersManager.kt` | Đã xử lý |
| 8 | **Phân biệt `EndReached` file hoàn tất 100% vs Đứt luồng stream sớm** | **Độ ổn định luồng phát** | `VlcMediaPlayer.kt` | Đã xử lý |
| 9 | **Vô hiệu hóa module thừa `ExoMediaPlayer` (dùng LibVLC duy nhất)** | **Nhẹ RAM & Giảm xung đột** | `ExoMediaPlayer.kt`, `PlayersManager.kt` | Đã xử lý |
| 10 | **Tối ưu dọn dẹp bộ nhớ an toàn `clearDatastore()` (Bảo vệ file hôm nay)** | **Tiết kiệm tài nguyên & 4G** | `MainActivity.kt` | Đã xử lý |
| 11 | **Giảm 50% dung lượng file cài đặt APK (từ ~183MB xuống ~85MB)** | **Tối ưu OTA qua 4G** | `app/build.gradle` | Đã cấu hình |
| 12 | **Chuẩn hóa tương thích thuộc tính OkHttp 4.x** | **Tính tương thích thư viện** | `CaCertHelper.kt`, `OkHttpUtils.kt` | Đã xử lý |
| 13 | **Ghi log crash ra `logerror.txt` & Tránh bão crash CPU box** | **Độ bền thiết bị** | `CrashHandler.kt` | Đã xử lý |
| 14 | **An toàn bounds check `toCalendar()` & Zero-out Milliseconds** | **Tính chính xác hẹn giờ** | `TimeUtils.kt` | Đã xử lý |
| 15 | **Vòng lặp định kỳ 30 phút tự kiểm tra lịch phát khi hôm nay trống** | **Tự phục hồi dịch vụ** | `MainViewModel.kt`, `MainActivity.kt` | Đã xử lý |
| 16 | **Kiểm tra và xóa file `ipradio.apk` rác sau khi cập nhật OTA** | **Tiết kiệm bộ nhớ trong** | `UpdateUtils.kt`, `MainActivity.kt` | Đã xử lý |
| 17 | **Làm sạch URL (mã hóa ký tự `#`, bỏ `//`) & Chống NPE `ForceMedia`** | **Ổn định Media/URL** | `MediaItem.kt`, `ForceMedia.kt` | Đã xử lý |
| 18 | **Tích hợp bộ 37 Unit Tests tự động (CA Cert, Playback, Timeframe)** | **Kiểm thử chất lượng CI/CD** | `CaCertLogicTest.kt`, `PlaybackLogicTest.kt`, `TimeframeAndForceMediaTest.kt` | 37/37 PASSED |

---

## 2. Chi Tiết Thay Đổi Kỹ Thuật

### 2.1. Module Tự Động Nạp Chứng Chỉ SSL CA (`CaCertHelper.kt` & `OkHttpUtils.kt`)
* **File tạo mới:** [`CaCertHelper.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/network/CaCertHelper.kt)
  * **Giải quyết vấn đề:** Các server lưu trữ âm thanh / CDN / Gateway ở nhiều địa phương dùng SSL tự ký hoặc CA nội bộ khiến Android báo lỗi `SSLHandshakeException` / `Trust anchor for certification path not found`.
  * **Cơ chế hoạt động:**
    - Tạo thư mục quản lý chứng chỉ cục bộ `files/ca-certs` và nạp sẵn cặp cert chuẩn (Sectigo Root R46 + OV R36).
    - `ensureCertForUrlAsync()` / `fetchAndSaveCertFromUrl()`: Tự động mở socket TLS, lấy chuỗi Certificate Chain của server và lưu vào thư mục `ca-certs`.
    - `createConfiguredTrustManager()`: TrustManager kép kết hợp giữa TrustManager mặc định của hệ điều hành Android và TrustManager nạp toàn bộ file `.crt`/`.pem` từ `ca-certs`.
    - **Tuân thủ ATTT:** Không tắt `HostnameVerifier` (không bypass SSL bừa bãi), ngăn chặn nguy cơ tấn công Man-in-the-Middle.
    - `SslRetryInterceptor`: OkHttp Interceptor tự động bắt lỗi SSL khi gọi API, tự động kéo cert của host đó và thử lại request ngay lập tức.
* **File cập nhật:** [`OkHttpUtils.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/network/OkHttpUtils.kt)
  * Bổ sung `OkHttpClientUtil.getUnsafeOkHttpClient()` phục vụ bước lấy chứng chỉ ban đầu trong `CaCertHelper`.
* **File cập nhật:** [`MyApp.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/MyApp.kt)
  * Thêm `CaCertHelper.setupGlobalConfiguredSsl(this)` trong `onCreate()` để áp dụng chứng chỉ CA cho `HttpsURLConnection` và `PRDownloader` toàn cục.
* **File cập nhật:** [`NetworkModule.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/di/NetworkModule.kt)
  * Khởi tạo `okHttpClient` qua `CaCertHelper.buildConfiguredOkHttpClient()` kèm cấu hình timeout và tự động retry an toàn.
* **File cập nhật:** [`MediaRepo.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/repo/MediaRepo.kt)
  * Trước khi tải file audio HTTPS, gọi `CaCertHelper.ensureCertForUrlAsync(appContext, url)`.
  * Nếu quá trình tải file bị lỗi (trong `downloadListener.onError`), tự động kích hoạt `CaCertHelper.fetchAndSaveCertFromUrlAsync(appContext, url, force = true)` để lấy lại cert mới.

---

### 2.2. Cơ Chế Bảo Vệ Công Suất Loa (PA Safety Watchdog)
* **File cập nhật:** [`SerialPortController.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt)
  * Bổ sung callback: `var isPlayingProvider: (() -> Boolean)? = null`.
  * Trong luồng `readDevice()` xử lý gói tin báo trạng thái từ MCU (`$$,1,8`):
    1. **Khi Player ĐÃ DỪNG:** Nếu MCU báo PA vẫn bật (`paState == "1"`) hoặc biến `playMode != MODE_NONE` ➔ Lập tức gửi lệnh cưỡng bức dập tắt PA (`turnOffPA()`). Giúp triệt tiêu hoàn toàn hiện tượng rò rỉ điện PA gây cháy loa hoặc tiếng hú rít khi hết lịch.
    2. **Khi Player ĐANG PHÁT:** Nếu MCU báo PA tắt (`paState == "0"`) hoặc âm lượng = 0 (do sụt áp nguồn hoặc bo vi điều khiển bị reset ngầm) ➔ Tự động kích hoạt `syncPAState()` để bật lại PA và khôi phục âm lượng tương ứng với chế độ phát (VOD/Live hoặc Tuner FM).
  * Chuẩn hóa `setTimeOver()`: Chuyển sang gọi hàm chuẩn `turnOffPA()` (vừa gửi mã lệnh vừa reset `playMode = MODE_NONE`).
  * Lưu trữ `lastPipsData` và `lastTunerData` để khôi phục chính xác tần số FM/AM khi đồng bộ lại Tuner.
* **File cập nhật:** [`MainActivity.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt)
  * Gắn liên kết trạng thái trong `onCreate()`: `serialPortController.isPlayingProvider = { playerManager.isPlaying() }`.
  * Bổ sung `serialPortController.turnOffPA()` tại các điểm then chốt: `onStop()`, `onDestroy()`, `STOP_FORCE` và `onEndForceMedia()`.

---

### 2.3. Khắc Phục Lỗi & Ổn Định Trình Phát
* **File cập nhật:** [`VlcMediaPlayer.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/VlcMediaPlayer.kt)
  * **Sửa lỗi crash đệ quy vô tận:** `checkLiveStream(mediaItem)` chuyển sang gọi `Companion.checkLiveStream(mediaItem)`.
  * **Phân biệt kết thúc file:** Khi nhận sự kiện `EndReached` từ VLC, nếu là file âm thanh cục bộ đã tải về máy (`File(body).exists()`), xác nhận hoàn tất 100% thay vì nhầm thành lỗi đứt stream.
  * **Tự động kết nối lại luồng Live:** Nếu luồng Live bị ngắt sớm nhưng vẫn còn trong giờ phát, sử dụng `retryHandler` thử lại tối đa 5 lần (khoảng cách 8 giây) trước khi kết thúc ca phát.
  * **Hỗ trợ Resume `:start-time`:** Khi thiết bị reboot giữa ca phát khẩn cấp dạng File, nhận diện file âm thanh qua `isAudioFile()` và nạp option `:start-time` để phát tiếp đúng giây đã trôi qua.
  * Tích hợp `--gnutls-dir-trust` trỏ vào thư mục `ca-certs` để LibVLC xác thực an toàn các luồng phát trực tiếp HTTPS.
* **File cập nhật:** [`PlayersManager.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/PlayersManager.kt)
  * **Sửa lỗi rò rỉ cờ FM:** Gán `handlerFMPlayer = null` ngay khi hoàn thành callback phát FM hoặc khi gọi `stopAllPlayer()`.
  * **Chuẩn hóa State Machine:** Thống nhất `PLAY_MODE_IDLE = ""` cho trạng thái rảnh rỗi/đã dừng.
  * **Hỗ trợ khung giờ vắt qua nửa đêm:** Tự động bù ngày cho `start` / `end` khi phát qua nửa đêm.
  * Đảm bảo gọi `stopAllPlayer()` trước khi hủy VLC trong `onActivityDestroy()`.
* **File cập nhật:** [`IMediaPlayer.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/IMediaPlayer.kt) & [`IPlayerEventListener.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/IPlayerEventListener.kt)
  * Cập nhật chữ ký hàm `onPlayerError(e, isLiveError, mediaItem)` và `playTest()`.
* **File cập nhật:** [`ExoMediaPlayer.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/ExoMediaPlayer.kt)
  * Vô hiệu hóa module thừa, thống nhất sử dụng duy nhất LibVLC.

---

### 2.4. Cải Tiến Nhóm 2 (Độ Bền, Tự Phục Hồi & Tối Ưu Hóa)
* **File cập nhật:** [`CrashHandler.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/CrashHandler.kt)
  * Ghi chi tiết stacktrace, thread name, timestamp vào `${context.filesDir}/app_logs/logerror.txt` khi ứng dụng gặp ngoại lệ bất ngờ.
  * Bổ sung cơ chế chống bão crash (Crash Storm Protection): Theo dõi số lần crash trong cửa sổ 10 giây; nếu $\ge 3$ lần, kéo giãn thời gian khởi động lại lên 5 giây (thay vì 1.5 giây thông thường) để tránh làm nghẽn CPU và bộ nhớ của thiết bị nhúng.
* **File cập nhật:** [`TimeUtils.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/TimeUtils.kt)
  * Thêm kiểm tra kích thước danh sách an toàn trong `toCalendar()` trước khi truy xuất chỉ mục (tránh `IndexOutOfBoundsException`).
  * Gán `set(Calendar.MILLISECOND, 0)` trong cả luồng bình thường lẫn ngoại lệ fallback để triệt tiêu sai số so sánh micro-giây trong AlarmManager và Scheduler.
* **File cập nhật:** [`MainViewModel.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/viewmodels/MainViewModel.kt) & [`MainActivity.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt)
  * Triển khai `startScheduleRetryLoop()` / `startPeriodicScheduleCheckLoop()`: Khi danh sách lịch hôm nay đang rỗng (`content.value` rỗng do cán bộ chưa cập nhật lịch trên Web), tự động lặp lại kiểm tra sau mỗi 30 phút. Khi phát hiện có lịch phát mới được cấu hình, tự động nạp lịch và hủy vòng lặp.
* **File cập nhật:** [`UpdateUtils.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/UpdateUtils.kt) & [`MainActivity.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt)
  * Bổ sung `checkDeleteFileUpdate()` và `getDestinationApk()`: Kiểm tra và xóa file `ipradio.apk` thừa trong thư mục Download khi khởi động ứng dụng hoặc ngay trước khi bắt đầu tải bản cài mới.
* **File cập nhật:** [`MediaItem.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/model/MediaItem.kt) & [`ForceMedia.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/model/ForceMedia.kt)
  * Thêm `prepareMediaUrl()` làm sạch URL: Tự động mã hóa ký tự `#` thành `%23` và loại bỏ dấu gạch chéo kép `//` thừa ở path.
  * Sửa lỗi rò rỉ `body!!` trong `init` của `MediaItem`, chuyển sang `body ?: ""` an toàn.
  * Trong `ForceMedia.toMediaItem()`: Tự động khởi tạo `start = fixTime`, `startTime = item?.startTime ?: fixTime` và xử lý null-safety để không gây `NullPointerException`.
* **File cập nhật:** [`app/build.gradle`](file:///d:/Git/IPRadio/app/build.gradle)
  * Cấu hình `testOptions { unitTests.returnDefaultValues = true }` cho môi trường unit test cục bộ.
  * Cấu hình NDK ABI Filters (`armeabi-v7a`, `arm64-v8a`) giảm dung lượng file cài APK từ 183MB xuống ~85MB.

---

### 2.5. Hệ Thống Unit Tests Tự Động
Tích hợp 3 bộ test case tự động toàn diện kiểm thử tất cả các tình huống rủi ro thực tế:
1. [`CaCertLogicTest.kt`](file:///d:/Git/IPRadio/app/src/test/java/app/tek4tv/ipradio/CaCertLogicTest.kt):
   - Trích xuất Host/Port linh hoạt (URL chuẩn, URL có port tùy biến 8443/9443, IP trần).
   - Xử lý lệnh Hub nạp trực tiếp chuỗi PEM không cần internet.
   - Bóc tách danh sách nhiều domain/port từ lệnh Hub.
   - Xác thực cặp chứng chỉ mặc định (Sectigo Root R46 & OV R36).
   - Phân loại lỗi mạng vs lỗi SSL Handshake.
   - Nạp tự động vào KeyStore cục bộ.
   - Bóc tách và loại bỏ Private Key khỏi chuỗi PEM nếu có.
2. [`PlaybackLogicTest.kt`](file:///d:/Git/IPRadio/app/src/test/java/app/tek4tv/ipradio/PlaybackLogicTest.kt):
   - Phân tích link HTTPS từ MediaItem (ưu tiên Body rồi đến PathBackup).
   - Chuyển đổi ForceMedia an toàn không ném NPE.
   - Tính toán Resume Point chuẩn xác theo thời gian thực.
   - Kiểm tra ranh giới khung giờ phát Live.
   - Phân biệt file VOD kết thúc bình thường vs ngắt sớm do mất mạng.
   - Máy trạng thái retry tự động (Live tối đa 5 lần, File tối đa 10 lần).
3. [`TimeframeAndForceMediaTest.kt`](file:///d:/Git/IPRadio/app/src/test/java/app/tek4tv/ipradio/TimeframeAndForceMediaTest.kt):
   - Đặt tên và bóc tách khung giờ từ start/end markers.
   - Tìm khung giờ đang hiệu lực tại thời điểm hiện tại.
   - Tính vị trí bài hát và offset giây tiếp tục phát khi khởi động giữa ca.
   - Cơ chế phát bản tin khẩn cấp đè lên lịch thường.
   - Quản lý cấp độ ưu tiên (Priority Level) của bản tin khẩn cấp.
   - Tự động khôi phục lịch thường sau khi kết thúc bản tin khẩn cấp.
   - Chuyển bài tuần tự và dừng khung giờ chuẩn xác.

---

## 3. Kết Quả Kiểm Thử (Verification)

* **Biên dịch Kotlin (`compileDebugKotlin`):**
  * **Trạng thái:** **BUILD SUCCESSFUL in 58s** (0 lỗi biên dịch).
* **Kiểm thử Unit Tests (`testDebugUnitTest`):**
  * **Trạng thái:** **BUILD SUCCESSFUL in 28s**.
  * **Tổng số test cases:** **37 tests**
  * **Số test thành công:** **37 passed**
  * **Số test thất bại:** **0 failed** (100% Pass).
