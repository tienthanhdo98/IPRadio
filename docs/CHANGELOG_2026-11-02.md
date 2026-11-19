# Nhật Ký Sửa Đổi & Khắc Phục Lỗi Hệ Thống (Changelog) - Ngày 02/11/2026

**Ngày thực hiện:** 02/11/2026  
**Nhánh:** `4.2.2-alpha`  
**Mục tiêu chính:** Đồng bộ các bản vá cốt lõi từ `4.2.2b`, chuẩn hóa cơ chế Reconnect SignalR Hub ngầm và bổ sung chốt chặn an toàn bảo vệ ca phát thanh Offline chống bị Force Restart.

---

## 1. Tóm Tắt Các Hạng Mục Đã Xử Lý

| STT | Hạng mục / Lỗi | Mức độ | File liên quan | Tình trạng |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Đồng bộ toàn diện bản vá từ nhánh `4.2.2b`** (VLC recursion fix, Watchdog PA 2 chiều, FM leak fix, Cross-midnight, Online-First recovery) | **Cốt lõi (Ổn định hệ thống)** | Nhiều file | Đã hoàn thành |
| 2 | **Giữ nguyên cấu hình đặc thù môi trường Alpha Lâm Đồng** (`TokenDto` NoiDung, Hub/Token URL Alpha, LogService Alpha) | **Cấu hình môi trường** | `HubManager.kt`, `TokenDto.kt`, `LogService.kt`, `GlobalProperties.kt` | Đã đảm bảo |
| 3 | **Chuẩn hóa Reconnect Hub ngầm (`checkConnection`)** (Gọi `connectToHub()` thay vì `openConnection()`, không reset sớm `pingCounter`) | **Nghiêm trọng (Mất sự kiện & Sai đèn LED MCU)** | `HubManager.kt` | Đã xử lý |
| 4 | **Bảo vệ ca phát Offline chống Force Restart** (Hoãn kill app khi `isPlaying() == true`) | **Nghiêm trọng (Chống ngắt tiếng loa)** | `MainActivity.kt` | Đã triển khai |
| 5 | **Phối hợp an toàn giữa `onHubConnectedListener` và `onChange`** | **Kiến trúc mạng** | `MainActivity.kt` | Đã xác thực & vận hành chuẩn |

---

## 2. Chi Tiết Các Thay Đổi Cụ Thể

### 2.1. `HubManager.kt`
* **Chuẩn hóa hàm `checkConnection()` khi mất kết nối ngầm:**
  - **Hiện trạng trước đây:** Khi `pingCounter >= 3` (sau 90 giây gửi heartbeat không có PONG), code gán ngay `pingCounter = 0` và gọi `openConnection()`.
    - Việc gán `pingCounter = 0` khiến `SerialPortController` đọc nhầm là Hub đang kết nối tốt ➔ MCU báo đèn mạng "CONNECTED" ảo dù thực tế đang mất mạng.
    - `openConnection()` chỉ thử kết nối 1 lần đơn độc, không có vòng lặp retry.
    - Đặc biệt, `openConnection()` **không gọi callback `onHubConnectedListener`**, khiến `MainActivity` không hề biết Hub vừa sống lại để kéo bù lịch phát.
  - **Khắc phục:**
    ```kotlin
    if (pingCounter >= 3) {
        Log.e(LOG_TAG, "Lost hub connection")
        hubToken = null
        connectToHub() // Gọi connectToHub() có retry và kích hoạt onHubConnectedListener
    }
    ```
  - **Lợi ích:**
    1. Giữ nguyên độ trễ an toàn 90 giây (`pingCounter >= 3`) trước khi xử lý ngắt kết nối.
    2. Không reset `pingCounter = 0` tại đây, giúp gói tin UART `$$,2,param...` gửi xuống MCU giữ đúng mã `param = 2` (hoặc `0`) phản ánh chính xác trạng thái mất mạng.
    3. `connectToHub()` chạy coroutine với cơ chế chốt chặn `isHubConnected()`, và khi kết nối thành công nó mới reset `pingCounter = 0` đồng thời kích hoạt `onHubConnectedListener?.invoke()` để kéo lại lịch phát.

---

### 2.2. `MainActivity.kt`
* **Bảo vệ an toàn cho ca phát Offline trong `forceRestartApp` & `forceRestartDevice`:**
  - **Hiện trạng trước đây:** Khi mất mạng kéo dài quá 10 phút (vòng lặp thử kết nối Hub quá 20 lần), `HubManager` tự động gọi `hubComm.forceRestartApp(...)` để reset modem 4G và khởi động lại app (sau 1 tiếng thì reboot cứng thiết bị qua MCU). Nếu trạm truyền thanh đang phát một chương trình bằng lịch Offline lưu sẵn trong máy, app sẽ bị kill ngay giữa chừng làm loa bị ngắt tiếng đột ngột.
  - **Khắc phục:**
    Bổ sung chốt chặn kiểm tra trạng thái phát thực tế tại cả 2 hàm restart:
    ```kotlin
    override fun forceRestartApp(countHardRestart: Int) {
        lifecycleScope.launch {
            if (::playerManager.isInitialized && playerManager.isPlaying()) {
                Log.w("resetApp", "Thiet bi dang phat am thanh -> Hoan force restart de tranh ngat loa!")
                return@launch
            }
            // Thực hiện restart app nếu đang không phát...
        }
    }

    override fun forceRestartDevice() {
        if (::playerManager.isInitialized && playerManager.isPlaying()) {
            Log.w("resetApp", "Thiet bi dang phat am thanh -> Hoan restart phan cung!")
            return
        }
        serialPortController.hardRestart()
    }
    ```
  - **Lợi ích:** Đảm bảo tiếng loa phát thanh của xã/phường tuyệt đối không bị ngắt giữa ca phát khi gặp sự cố mất mạng diện rộng.

---

### 2.3. Cơ chế phối hợp giữa `NetworkUtils.onChange` và `onHubConnectedListener`
* **Cơ chế hoạt động:**
  - `onChange`: Kích hoạt ngay lập tức khi modem phần cứng (4G/WiFi) có kết nối trở lại ➔ kéo lịch nhanh qua HTTP REST API.
  - `onHubConnectedListener`: Kích hoạt sau khi SignalR WebSocket bắt tay thành công với máy chủ ➔ phục vụ trường hợp 4G vẫn có nhưng Hub bị rớt ngầm.
* **Tính an toàn (Không xung đột, không đè nhau):**
  - Cả 2 đều kiểm tra điều kiện chốt chặn: `isScheduleEmpty` (lịch hôm nay phải đang rỗng), `playerManager.isIdle()` (chưa thiết lập ca phát) và `!playerManager.isPlaying()`.
  - Bên trong `MainViewModel.getPlayerContent()`, lệnh `getContentJob?.cancel()` luôn hủy job kéo lịch cũ trước khi tạo job mới, loại bỏ hoàn toàn khả năng xung đột dữ liệu.

---

## 3. Kết Quả Kiểm Thử (Verification)
- Toàn bộ source code trên nhánh `4.2.2-alpha` đã được kiểm thử:
  - Lệnh: `./gradlew testDebugUnitTest --offline`
  - Kết quả: **BUILD SUCCESSFUL in 44s**
  - Đạt: **100% Unit Tests Passed** (30 tasks executed/up-to-date, 0 lỗi, 0 cảnh báo nghiêm trọng).

