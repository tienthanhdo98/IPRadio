# Changelog - Digital 4.2.2 (Đồng bộ & Nâng cấp từ chuỗi 4.2.2b / 4.2.2c / 4.2.2e)

**Ngày cập nhật:** 05/11/2026  
**Nhánh:** `digital-4.2.2`

---

## 1. Mạng & Xác thực SSL (`CaCertHelper.kt`, `NetworkModule.kt`, `MyApp.kt`, `HubManager.kt`)
- **Tích hợp `CaCertHelper`**: Nạp sẵn chứng chỉ gốc Root CA (*Sectigo Root R46 & OV R36*), xây dựng cấu hình SSL TrustManager an toàn cho cả Retrofit và WebSocket SignalR. Xử lý triệt để lỗi `javax.net.ssl.SSLHandshakeException: Connection reset by peer` trên Android Box.
- **Kích hoạt SSL toàn cục (`MyApp.kt`)**: Khởi tạo `CaCertHelper.setupGlobalConfiguredSsl` ngay tại `onCreate`.
- **Tích hợp CA cert trong `HubManager` & `MainActivity`**: Cấu hình `setHttpClientBuilderCallback` cho `HubConnectionBuilder` trong cả kết nối chính và lệnh `UPDATE_CONFIG`.

## 2. Reconnect SignalR Hub chuẩn hóa (`HubManager.kt`)
- **Loại bỏ kẹt cờ `firstConnect`**: Kiểm tra trạng thái thực tế qua `isHubConnected()`.
- **Chống race condition**: Kiểm tra `openNewConnectionJob?.isActive == true` tránh mở luồng kết nối lặp.
- **Tự động Reconnect toàn diện**: Trong `checkConnection()`, khi `pingCounter >= 4`, tự động gọi `connectToHub()` để duy trì kết nối lại cho tới khi thành công.
- **Chống trùng listener**: Gọi `hubConnection.remove("ReceiveMessage")` trước khi gán handler mới.
- **Callback `onHubConnectedListener`**: Phát tín hiệu khi Hub kết nối lại thành công.

## 3. Bảo vệ công suất PA & Báo mạng MCU (`SerialPortController.kt`)
- **Watchdog bảo vệ PA 2 chiều**:
  - Khi app **không phát** mà MCU báo PA đang bật (`paState == 1`) hoặc mode chưa về `MODE_NONE`: Cưỡng bức dập tắt PA ngay (`turnOffPA()`) để chống hú rít/cháy loa.
  - Khi app **đang phát** mà MCU bị sụt nguồn hoặc reset về `paState == 0`: Tự động gọi `syncPAState()` để bật lại PA và khôi phục âm lượng.
- **Chuẩn hóa báo mạng xuống MCU**:
  - `param = 0`: Mất mạng hoàn toàn hoặc SIM hết data.
  - `param = 2`: Có mạng nhưng mất kết nối SignalR Hub.
  - `param = 1`: Có mạng và Hub hoạt động bình thường.
- **Âm lượng an toàn**: `turnOnPAVod()` fallback âm lượng `100` nếu cấu hình rỗng hoặc bằng 0.

## 4. Kịch bản phát & Tối ưu dữ liệu 4G (`MediaRepo.kt`, `MainViewModel.kt`)
- **Cơ chế Online-First (`MediaRepo.kt`)**: Khi có mạng, luôn ưu tiên kéo lịch mới từ server đè lên cache local (đảm bảo sáng đổi lịch hay trưa đổi lịch thì máy đều cập nhật ngay); khi offline mới fallback về đọc cache local.
- **Bảo vệ lịch hiện tại**: Bổ sung `safeUpdateScenario()`, không xóa đè lịch cũ nếu server trả về rỗng hoặc lỗi parse.
- **Trích xuất đuôi file thông minh**: Tự động lấy extension từ query param `type` trong URL (ví dụ `?type=.mp3` hoặc `?type=.mp4`) khi `guessFileName` không có đuôi.
- **Quét bù lịch định kỳ (`startScheduleRetryLoop`)**: Tự động thử kéo lại lịch theo chu kỳ (3 min → 5 min → 10 min → 20 min) khi khởi động không có mạng, và tự hủy loop ngay khi đã tải thành công.
- **Dọn bộ nhớ an toàn (`clearDatastore`)**: Chỉ xóa log, bản ghi âm cũ và các file media không thuộc lịch hôm nay (`broadcastList`), bảo vệ các file chuẩn bị phát để không tốn băng thông 4G tải lại.

## 5. Phát Media & Khung giờ phát (`PlayersManager.kt`)
- **Hỗ trợ khung giờ vắt ngày (Cross-midnight)**: Xử lý `start.after(end)` trong `startFirstTimeFrame()` (ví dụ 23:00 – 01:00 hôm sau) để máy nhận diện đúng đang trong giờ phát.
- Bổ sung `isIdle()`, `isPlaying()` và hằng số `PLAY_MODE_IDLE`.

## 6. Lắng nghe phục hồi mạng & Vòng đời an toàn (`MainActivity.kt`)
- Đăng ký `NetworkUtils.instance.addCallback`: Tự động reconnect Hub và kéo bù lịch khi có mạng lại.
- Đăng ký `onHubConnectedListener` kéo bù lịch khi Hub kết nối lại (kèm throttle cooldown 5 phút).
- Liên kết `serialPortController.isPlayingProvider = { playerManager.isPlaying() }`.
- Dập tắt PA an toàn tại `onEndForceMedia` và `onDestroy` để không bị rò rỉ âm thanh.

## 7. Các bản vá bổ sung về độ ổn định chạy 24/7
- **Sửa phân tích thời gian an toàn (`TimeUtils.kt`)**: Sửa `toCalendar()` hỗ trợ đầy đủ các định dạng giờ (kể cả thiếu giây `HH:mm`) và set `MILLISECOND = 0`.
- **Tự phục hồi sau crash (`CrashHandler.kt`)**: Tự động tạo thư mục log, kích hoạt `restartApp()`, delay 5s chống bootloop khi crash liên tục, sử dụng `PendingIntent.FLAG_IMMUTABLE`.
- **Tương thích Android 12+ (`MediaScheduler.kt`)**: Đặt cờ `FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT` cho PendingIntent đặt báo thức lịch phát.
- **Chuẩn hóa thực thi lệnh Root (`SuUtils.kt`)**: Viết lệnh trực tiếp qua stdin của `su` process, tránh lỗi cú pháp và bổ sung hàm `reboot()`.
- **Dọn dẹp APK sau cài đặt (`UpdateUtils.kt`)**: Bổ sung `checkDeleteFileUpdate()` dọn sạch file `.apk` trong thư mục `Download/` sau khi khởi động.

## 8. Hợp nhất & Nâng cấp logic phát ảnh qua Media3 ExoPlayer (`ExoPlayer.kt`, `PlayersManager.kt`, `MainActivity.kt`)
- **Tích hợp Native Image Playback (`ExoPlayer.kt`)**:
  - Tận dụng tính năng phát ảnh native của Media3 với `setImageDurationMs(durationMs)`.
  - ExoPlayer tự động giải mã ảnh và render trực tiếp lên `PlayerView` (surface).
  - Tự động phát sinh sự kiện `Player.STATE_ENDED` khi hết thời lượng ảnh để chuyển bài mượt mà, không phụ thuộc vào `Handler.postDelayed` thủ công.
  - Tự động tính toán thời lượng còn lại khi resume từ giữa chừng: `remainingSeconds = duration - seekPosition`.
  - Bổ sung cơ chế phát hiện và xử lý ảnh hỏng (kích thước 0 bytes hoặc `BitmapFactory.decodeFile()` trả về null) để skip an toàn.
- **Đơn giản hóa trình điều khiển (`PlayersManager.kt`)**:
  - Chuyển toàn bộ luồng phát ảnh sang `ExoPlayer`, thay thế `ImagePlayer` (Picasso).
  - Bổ sung `imagePlaybackStartMs` và `getCurrentPlaybackPositionMs()` để theo dõi chính xác thời gian phát của ảnh tĩnh.
- **Đồng bộ giao diện hiển thị (`MainActivity.kt`)**:
  - Khi phát thành công (`onPlayMediaSuccess`), luôn hiển thị `exoVideoLayout` và ẩn `imgHolder` cho cả video và ảnh.
  - `imgHolder` đóng vai trò là placeholder ảnh nền chờ tĩnh khi không có lịch phát hoặc khi xảy ra lỗi.

## 9. Các tối ưu thực chiến chống đơ và bảo vệ dữ liệu từ nhánh `centek`
- **Chống Infinite Loop khi Media bị lỗi / file hỏng (`PlayersManager.kt`, `ExoPlayer.kt`)**:
  - Quản lý `itemFailCountMap`: Nếu một item bị lỗi, thử lại tối đa 3 lần (cách nhau 3s). Nếu tiếp tục lỗi sau 3 lần, đưa vào `skippedItemKeys` để tự động **Skip** bài đó.
  - Nếu kịch bản chỉ có $\le 1$ item hoặc tất cả các item đều lỗi: Dừng kịch bản, chuyển về trạng thái chờ an toàn, tuyệt đối không lặp lại đệ quy gây nghẽn 100% CPU Main Thread (triệt tiêu lỗi Watchdog kill app SIG 9).
  - Tự động kích hoạt **Auto-Recovery sau 60 giây**: Khi kịch bản bị bỏ do lỗi toàn bộ, sau 60s app sẽ tự thử nạp lại kịch bản (khi file đã tải xong hoặc mạng hồi phục).
- **Ghi file nguyên tử (Atomic Write + `fsync`) & Self-Healing (`FileUtils.kt`, `BaseRepo.kt`)**:
  - `writeFile()`: Ghi qua file trung gian `.tmp`, gọi `fos.fd.sync()` để ép nhân Linux flush dữ liệu từ Page Cache xuống chip nhớ eMMC vật lý trước khi tráo đổi nguyên tử `renameTo()`. Tránh triệt để hiện tượng mất nguồn/tủ điện ngắt làm file bị ghi toàn byte null `\0\0\0...` (zero-fill corruption).
  - Self-Healing trong `BaseRepo.kt`: Tự động phát hiện file JSON chứa toàn ký tự null hoặc hỏng cấu trúc và gọi `delete()` xóa sạch file hỏng để app không bị kẹt vĩnh viễn ở các lần khởi động tiếp theo.
- **Adaptive Buffering Timeout & LoadControl tối ưu mạng yếu (`ExoPlayer.kt`)**:
  - Bổ sung cơ chế **Adaptive Buffering Timeout**: Tự động đo thời lượng media để điều chỉnh timeout động từ 25s đến tối đa 60s, cho phép thiết bị mạng 4G yếu có đủ thời gian đệm dữ liệu mà không bị skip oan gây chớp đen màn hình.
  - Tối ưu cấu hình `DefaultLoadControl`: `bufferForPlaybackMs = 1_500ms` (khởi phát nhanh), `bufferForPlaybackAfterRebufferMs = 2_500ms`, `minBufferMs = 15_000ms`, `maxBufferMs = 50_000ms` (tận dụng tối đa băng thông khi rảnh).

## 10. Chặn lệnh trùng lặp dồn dập (Command Deduplication & Debounce)
- **Lọc trùng ở tầng kết nối Hub (`HubManager.kt`)**:
  - Quản lý `lastMessageKey` và `lastMessageTime` với ngưỡng `COMMAND_DEDUP_INTERVAL_MS = 1500L` (1.5 giây).
  - Tự động bỏ qua các bản tin/lệnh trùng nhau hoàn toàn (cùng Command và Message Payload) khi Server/CMS gửi dồn dập trong khoảng thời gian $< 1.5$s.
  - Ngoại trừ lệnh `PONG` để không ảnh hưởng đến cơ chế keep-alive kiểm tra kết nối mạng.
  - Ngăn ngừa tình trạng app xử lý lặp lại các lệnh nặng như `UPDATE_CONFIG`, `UPDATE_MONTHLY_LIST`, `RESTART`, `SET_VOLUME_DEVICE`... gây nghẽn Main thread và quá tải buffer UART MCU.
- **Debounce tầng UI (`MainActivity.kt`)**:
  - Bổ sung `START_PLAYING_MEDIA_DEBOUNCE_MS = 3000L` (3 giây) cho hàm `startPlayingMedia()` nhằm chống kích hoạt phát lại kịch bản liên tục khi mạng chập chờn.





## 11. Sửa lỗi vùng Multi-view hiển thị đường dẫn file thay vì nội dung bản tin (`AutoScrollAdapter.kt`, `MainActivity.kt`)
- **Nguyên nhân gốc rễ**:
  - `AutoScrollAdapter` trước đây bind trực tiếp `holder.textView.text = item.body`.
  - Trong mô hình dữ liệu của Tek4TV / IPRadio, trường `body` thực chất là nguồn phát media (`Media Source URL` hoặc đường dẫn lưu file cục bộ trên máy như `/data/user/0/.../file.mp4` sau khi tải về).
  - Khi `MediaRepo` hoặc `MediaWatchDog` tải media về máy, trường `body` được cập nhật thành đường dẫn file cục bộ. Nếu đưa `body` lên `TextView`, màn hình sẽ hiện nguyên văn đường dẫn file thay vì nội dung chữ.
  - Nội dung thực sự của bản tin văn bản nằm ở `item.title` (Tiêu đề) và `item.description` (Nội dung chi tiết), hoặc bên trong file `.txt` đính kèm nếu có.
- **Giải pháp xử lý**:
  - **Trích xuất nội dung thông minh (`extractDisplayText`)**:
    - Ưu tiên trích xuất `title` và `description` của bản tin, tự động ghép và định dạng Tiêu đề in đậm (Bold) màu nổi bật phía trên, nội dung chi tiết phía dưới.
    - Hỗ trợ đọc nội dung từ file văn bản cục bộ (`.txt`) nếu `body` trỏ đến file text trên thiết bị.
    - Tự động nhận diện và loại trừ triệt để các đường dẫn file media (`.mp4`, `.mp3`, `.m4a`, `.jpg`, `.png`, v.v.) và URL `http(s)://`, không để lộ đường dẫn file thô lên giao diện.
  - **Lọc danh sách hợp lệ**:
    - `displayList` chỉ giữ lại các item thực sự có nội dung văn bản. Nếu danh sách rỗng, adapter trả về `itemCount = 0` và RecyclerView tự động ẩn đi, tránh crash lỗi chia cho 0 (`position % 0`) hoặc chạy cuộn trống.
  - **Chống cuộn chồng tốc độ**:
    - Hủy callback cuộn cũ `scrollRunnable` trước khi kích hoạt `startAutoScroll()` mới, chống hiện tượng cuộn giật nhanh bất thường khi nạp lại danh sách nhiều lần.
