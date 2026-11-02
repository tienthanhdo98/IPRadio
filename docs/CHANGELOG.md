# Nhật Ký Sửa Đổi & Khắc Phục Lỗi Hệ Thống (Changelog)

**Ngày cập nhật:** 27/10/2026  
**Mục tiêu chính:** Kiểm tra toàn diện và xử lý triệt để các lỗi liên quan đến logic Bật/Tắt công suất (PA), đồng bộ trạng thái phát Media (VOD/Live/FM), an toàn vòng đời ứng dụng (Lifecycle), và tối ưu tiêu thụ dữ liệu 4G.

---

## 1. Tóm Tắt Các Vấn Đề Đã Xử Lý

| STT | Vấn đề / Lỗi | Mức độ nghiêm trọng | File liên quan | Tình trạng |
| :--- | :--- | :--- | :--- | :--- |
| 1 | Đệ quy vô tận gây Crash (StackOverflowError) | **Nghiêm trọng (Crash)** | `VlcMediaPlayer.kt` | Đã xử lý |
| 2 | Treo cờ phát FM khiến `isPlaying()` luôn trả về `true` | **Nghiêm trọng (Sai logic PA)** | `PlayersManager.kt` | Đã xử lý |
| 3 | Hủy vĩnh viễn LibVLC khi app chuyển sang chạy ngầm (`onStop`) | **Nghiêm trọng (Mất tiếng)** | `MainActivity.kt` | Đã xử lý |
| 4 | Rò rỉ PA (PA vẫn mở dù media đã dừng phát hoặc nhận lệnh dừng) | **Nghiêm trọng (Cháy loa / hú rít)** | `SerialPortController.kt`, `MainActivity.kt` | Đã xử lý |
| 5 | Watchdog tự động giám sát & bảo vệ PA 2 chiều | **Tính năng an toàn mới** | `SerialPortController.kt` | Đã triển khai |
| 6 | Lỗi tính toán khung giờ phát vắt qua nửa đêm (Cross-midnight) | **Trung bình (Lỡ lịch phát)** | `PlayersManager.kt` | Đã xử lý |
| 7 | Tải lại toàn bộ media gây lãng phí 4G khi dọn bộ nhớ | **Tối ưu hóa tài nguyên** | `MainViewModel.kt` | Đã xử lý |
| 8 | Cơ chế tải lịch phát (Online First) | **Độ tin cậy dữ liệu** | `MediaRepo.kt` | Giữ nguyên Online First (luôn kéo mạng đè cache khi có mạng, fallback cache khi offline) |
| 9 | Chống kết nối chồng lấn SignalR Hub | **Ổn định kết nối** | `HubManager.kt` | Đã xử lý |

---

## 2. Chi Tiết Các Thay Đổi Theo Từng File

### 2.1. `VlcMediaPlayer.kt`
- **Sửa lỗi đệ quy vô tận:** 
  - Tại dòng 245, hàm thành viên `checkLiveStream(mediaItem)` trước đó gọi lại chính nó gây tràn ngăn xếp (`StackOverflowError`).
  - **Khắc phục:** Gọi hàm của companion object: `Companion.checkLiveStream(mediaItem)`.
- **Cập nhật chính xác cờ `isPlaying`:**
  - Bổ sung xử lý sự kiện `MediaPlayer.Event.Paused` và `MediaPlayer.Event.Stopped` trong Event Listener của VLC để gán `isPlaying = false`.
  - Hàm `pause()` gán rõ ràng `isPlaying = false`.
- **Dọn dẹp trạng thái triệt để trong `stop()`:**
  - Đảm bảo gán `isPlaying = false`, `currentMedia = null`, `isLiveMode = false` trong cả khối `try` và `catch`.
- **Chuẩn hóa biến cờ:**
  - Đổi tên biến cờ `isCurrentMediaForce` thành `isLiveMode` phản ánh đúng bản chất luồng Live/HLS/RTSP.

---

### 2.2. `PlayersManager.kt`
- **Sửa lỗi rò rỉ Handler FM Player:**
  - `handlerFMPlayer` trước đó không được set `null` khi chạy xong hoặc khi gọi `stopAllPlayer()`. Do hàm `isPlaying()` kiểm tra `handlerFMPlayer != null`, trạng thái hệ thống luôn bị nhận định là "đang phát", dẫn đến việc PA không thể tự tắt an toàn.
  - **Khắc phục:** Gán `handlerFMPlayer = null` ngay khi hoàn thành callback phát FM và trong hàm `stopAllPlayer()`.
- **Hỗ trợ khung giờ vắt qua nửa đêm (23:00 - 01:00):**
  - Trong `startFirstTimeFrame()`, bổ sung kiểm tra nếu `start.after(end)`: cộng thêm 1 ngày cho `end` nếu thời điểm hiện tại ở trước nửa đêm, hoặc trừ 1 ngày cho `start` nếu hiện tại ở sau nửa đêm.
- **Dừng phát an toàn khi Activity bị Destroy:**
  - Trong `onActivityDestroy()`, gọi `stopAllPlayer()` trước khi gọi `vlcPlayer.release()`.

---

### 2.3. `SerialPortController.kt`
- **Cơ chế Safety Watchdog 2 chiều (Giám sát chu kỳ 5 giây):**
  - Bổ sung callback `isPlayingProvider: (() -> Boolean)?` để SerialPortController trực tiếp truy vấn trạng thái phát thực tế từ tầng Player.
  - Trong vòng lặp `readDevice()` (chu kỳ ~5s đọc gói tin `$$ ,1,8` từ MCU):
    1. **Khi app KHÔNG phát:** Nếu MCU báo `paState == "1"` hoặc biến quản lý `playMode != MODE_NONE`, lập tức gửi lệnh dập tắt PA cưỡng bức (`turnOffPA()`).
    2. **Khi app ĐANG phát:** Nếu MCU báo `paState == "0"` hoặc `volume == "0"` (do MCU bị reset nguồn hoặc glitch), tự động kích hoạt hàm `syncPAState()` để bật lại PA và khôi phục âm lượng.
- **Chuẩn hóa dập PA trong `setTimeOver()`:**
  - Thay vì chỉ gửi hàm `FUNC_WRITE_PLAY_NO_SOURCE`, chuyển sang ủy quyền cho hàm chuẩn `turnOffPA()` (vừa gửi mã lệnh vừa reset `playMode = MODE_NONE`).
- **Khôi phục Tuner chính xác khi đồng bộ lại:**
  - Lưu trữ `lastPipsData` và `lastTunerData` khi gọi `playTuner()` để `syncPAState()` có dữ liệu tái kích hoạt tần số FM/AM khi cần.
- **Âm lượng mặc định an toàn:**
  - `turnOnPAVod()` đảm bảo fallback volume `100` nếu giá trị cấu hình bị rỗng hoặc bằng 0.

---

### 2.4. `MainActivity.kt`
- **Bổ sung dập tắt PA tại các điểm then chốt trong Lifecycle:**
  - `onStop()`: Bổ sung `serialPortController.turnOffPA()`. Sửa từ `playerManager.onActivityDestroy()` thành `playerManager.onActivityStop()` để giữ nguyên instance VLC, tránh việc VLC bị hủy vĩnh viễn không thể phát lại khi app quay lại foreground.
  - `onDestroy()`: Bổ sung `serialPortController.turnOffPA()`.
- **Dập tắt PA và xử lý lệnh từ SignalR Hub:**
  - `HubCommand.STOP_FORCE`: Bổ sung `serialPortController.turnOffPA()`, đồng thời gọi `startPlayingMedia()` để quay lại lịch phát ngày thường thay vì chỉ gọi `getPlayerContent()`.
  - `HubCommand.UPDATE_MONTHLY_LIST`: Bổ sung `serialPortController.turnOffPA()` trước khi dừng player và nạp lịch mới.
  - `onEndForceMedia()`: Bổ sung `serialPortController.turnOffPA()` khi kết thúc luồng phát khẩn cấp.
- **Tự động phục hồi khi có mạng trở lại:**
  - Đăng ký `NetworkUtils.ConnectionCallback`: Khi mạng kết nối lại, tự động reconnect Hub; nếu thiết bị chưa phát và lịch trống thì khởi chạy `startPlayingMedia()`, nếu đang phát thì gọi `syncPAState()`.
- **Liên kết Watchdog:**
  - Gán `serialPortController.isPlayingProvider = { playerManager.isPlaying() }` ngay tại `onCreate()`.

---

### 2.5. `MainViewModel.kt`
- **Tối ưu hóa dọn dẹp bộ nhớ an toàn (`clearDatastore`):**
  - Trước đây, khi bộ nhớ trống < 1GB, hàm gọi `appStorageManager.deleteAllMedia()`, xóa sạch toàn bộ các tệp âm thanh đã tải về. Khi phát lịch, app buộc phải tải lại toàn bộ bằng mạng 4G, gây tốn băng thông và tạo vòng lặp xóa - tải.
  - **Khắc phục:** 
    - Chỉ xóa log và record rác (`deleteAllLog()`, `deleteAllRecord()`).
    - Lấy danh sách tệp của lịch phát hôm nay (`mediaRepo.broadcastList`).
    - Chỉ xóa các tệp phương tiện cũ không nằm trong lịch hôm nay, bảo vệ an toàn các file chuẩn bị phát.
- **Hủy Job retry cũ:**
  - Hủy bỏ và gán `retryJob = null` khi bắt đầu lấy danh sách phát mới.

---

### 2.6. `MediaRepo.kt`
- **Duy trì cơ chế Online First chuẩn (Đảm bảo luôn cập nhật đè lịch mới):**
  - Giữ nguyên logic Online First của dự án: Khi có mạng (`isOnline`), app luôn tải kịch bản mới nhất từ `todayScenario.sourceUrl` để ghi đè vào cache và cập nhật danh sách phát mới nhất (đảm bảo khi admin sửa lịch trên CMS hoặc app reboot thì lịch mới luôn được cập nhật ngay).
  - Tự động fallback về file cache cục bộ (`readItemsFromFile()`) khi offline hoặc khi mạng chập chờn / server lỗi.
- **Chuẩn hóa cú pháp:** Sử dụng `kotlin.time.Duration` cho các câu lệnh `delay()`.

---

### 2.7. `HubManager.kt`
- **Chống mở kết nối trùng lặp:**
  - Bổ sung hàm kiểm tra `isHubConnected()`.
  - Trong `connectToHub()`, kiểm tra nếu Hub đã CONNECTED hoặc `openNewConnectionJob` đang hoạt động thì bỏ qua, tránh tạo nhiều kết nối WebSocket song song gây xung đột dữ liệu.
- Reset `pingCounter = 0` sau khi kết nối thành công.

---

## 3. Kiểm Tra & Xác Nhận (Verification)
- Toàn bộ source code đã được biên dịch thành công:
  - Lệnh: `.\gradlew.bat compileDebugKotlin`
  - Kết quả: **BUILD SUCCESSFUL**.
- Không có lỗi biên dịch (0 compiler errors), không có cảnh báo nghiêm trọng mới.
