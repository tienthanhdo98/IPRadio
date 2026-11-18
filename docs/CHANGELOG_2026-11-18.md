# Nhật Ký Sửa Đổi & Khắc Phục Lỗi Hệ Thống (Changelog) - Ngày 18/11/2026

**Ngày thực hiện:** 18/11/2026  
**Dự án:** IPRadio  
**Phiên bản nhánh:** 4.2.2b  
**Mục tiêu chính:** 
1. Khắc phục lỗi tiềm ẩn Crash ứng dụng khi nhận lệnh điều khiển phát thanh từ Hub (`HubCommand.CONTROL_MEDIA`).
2. Xử lý triệt để lỗi báo sai trạng thái mạng xuống MCU/Loa qua cổng Serial (UART): Khắc phục hiện tượng SIM 4G hết cước/hết data nhưng hệ thống vẫn báo trạng thái `2` (Có Internet, mất Hub) thay vì báo `0` (Mất mạng hoàn toàn).

---

## 1. Tóm Tắt Các Hạng Mục Đã Xử Lý

| STT | Vấn đề / Hạng mục | Mức độ | File liên quan | Tình trạng |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **Bọc `try-catch` an toàn cho `HubCommand.CONTROL_MEDIA`** | **Nghiêm trọng (Crash App)** | `MainActivity.kt` | Đã xử lý |
| 2 | **Nhận diện trạng thái Internet thực tế qua Token API nội bộ** | **Trung bình (Logic kết nối)** | `HubManager.kt` | Đã xử lý |
| 3 | **Chuẩn hóa phân loại trạng thái mạng `0` vs `2` gửi MCU** | **Nghiêm trọng (Sai đèn báo / màn hình)** | `SerialPortController.kt` | Đã xử lý |

---

## 2. Chi Tiết Các Thay Đổi Theo Từng File

### 2.1. `MainActivity.kt`
* **Vấn đề trước đây:**
  Tại nhánh xử lý `HubCommand.CONTROL_MEDIA`, code thực hiện ép kiểu trực tiếp:
  ```kotlin
  val mess = responseHub.message!!.toInt()
  ```
  Nếu server gửi bản tin có `message` rỗng, `null` hoặc chuỗi text không phải định dạng số nguyên, ứng dụng sẽ văng ngoại lệ `NullPointerException` hoặc `NumberFormatException` dẫn đến crash đột ngột.
* **Khắc phục:**
  - Bọc toàn bộ khối lệnh xử lý điều khiển Media (`0: dừng`, `1: tiếp`, `2: tạm dừng`, `3: phát tiếp`) trong khối `try { ... } catch (e: Exception)`.
  - Ghi log lỗi chi tiết qua `Log.e("HubCommand", "Error processing CONTROL_MEDIA: ${e.message}")`, đảm bảo ứng dụng không bao giờ bị dừng đột ngột.

---

### 2.2. `HubManager.kt`
* **Vấn đề trước đây:**
  Khi SIM 4G hết data/tiền cước, modem di động vẫn giữ sóng kết nối với trạm phát sóng (BTS) và được cấp IP nội bộ nhà mạng, khiến Android OS báo `activeNetwork.isConnectedOrConnecting = true` (`onAir = true`). Ứng dụng không có cơ chế nào để biết thiết bị đã mất Internet thực tế nếu không ping ra ngoài.
* **Khắc phục:**
  - Bổ sung biến cờ trạng thái:
    ```kotlin
    @Volatile
    var isTokenReachable = false
    ```
  - Trong hàm `getHubToken(urlToken: String)` (gọi đến server nội bộ tek4tv):
    + Khi kết nối thành công (`response.isSuccessful`) hoặc nhận phản hồi từ server (mã 4xx/5xx): Gán `isTokenReachable = true` (chứng tỏ đường truyền Internet vẫn thông đến máy chủ).
    + Khi gặp ngoại lệ `Exception` (DNS resolution thất bại, `SocketTimeoutException`, `ConnectException` do SIM bị chặn Internet): Gán `isTokenReachable = false`.
  - **Lợi ích:** Không cần ping ra các server bên ngoài (Google 8.8.8.8), không tốn thêm byte dữ liệu nào của SIM, hoàn toàn dùng kết nối nội bộ tự nhiên của hệ thống.

---

### 2.3. `SerialPortController.kt`
* **Vấn đề trước đây:**
  Trong hàm `onWatchDog()` gửi bản tin chu kỳ xuống MCU:
  ```kotlin
  var param = if (onAir) "1" else "0"
  if (looseHubConnection) {
      param = "2" // Bị nhảy vào đây khi SIM hết data vì onAir vẫn là true!
  }
  ```
  Khi SIM hết tiền, `onAir` vẫn là `true` và `looseHubConnection` là `true`, dẫn đến chuỗi gửi xuống MCU luôn là `$$,2,2,...`. MCU đọc thấy mã `2` kết hợp sóng 4G tốt nên vẫn hiển thị "CONNECTED" hoặc bật đèn mạng, gây hiểu lầm cho người vận hành.
* **Khắc phục:**
  - Cập nhật logic phân loại `param` dựa trên cờ `isTokenReachable`:
    ```kotlin
    var param = if (onAir) "1" else "0"
    if (looseHubConnection) {
        // Phân biệt: mất Hub nhưng API vẫn gọi được = Hub sập/bảo trì ("2")
        //            mất Hub và API cũng lỗi = mất Internet thực sự / hết cước ("0")
        param = if (hubManager.isTokenReachable) "2" else "0"
    }
    ```
  - **Kết quả:** Khi SIM hết data, sau chu kỳ timeout kiểm tra token, `param` sẽ lập tức chuyển về **`0`**, MCU hiển thị đúng "DISCONNECTED" và tắt đèn mạng.

---

## 3. Ma Trận Kiểm Thử Trạng Thái Hoạt Động (Verification Matrix)

| Tình huống thực tế | Sóng di động (`onAir`) | Hub SignalR | Token API | `param` gửi MCU | Trạng thái hiển thị MCU |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Mọi thứ hoạt động bình thường** | `true` | ✅ Kết nối | ✅ Phản hồi | **`1`** | **CONNECTED** (Bình thường) |
| **Server Hub tek4tv bảo trì / sập** | `true` | ❌ Mất kết nối | ✅ Phản hồi | **`2`** | **HUB DISCONNECTED** (Báo lỗi máy chủ Hub) |
| **SIM 4G hết cước / hết data** | `true` | ❌ Mất kết nối | ❌ Lỗi kết nối | **`0`** | **NO INTERNET** (Tắt đèn / Báo mất mạng) |
| **Rút SIM / Mất hẳn sóng viễn thông** | `false` | ❌ Mất kết nối | ❌ Lỗi kết nối | **`0`** | **NO INTERNET** (Mất mạng) |
| **Rút dây LAN / Tắt WiFi** | `false` | ❌ Mất kết nối | ❌ Lỗi kết nối | **`0`** | **NO INTERNET** (Mất mạng) |
