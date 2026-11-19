# BẢNG CHECKLIST KIỂM TRA TRƯỚC KHI RELEASE (IPRADIO)

Tài liệu này dùng để rà soát chất lượng, cấu hình và tính bảo mật của ứng dụng trước mỗi lần build và phát hành bản Release.

---

## I. KẾT QUẢ RÀ SOÁT BẢN HIỆN TẠI (Branch: `4.2.2b`)
*Thời điểm kiểm tra: 18/11/2026*

| Hạng mục kiểm tra | Trạng thái | Chi tiết phát hiện trên mã nguồn hiện tại |
| :--- | :---: | :--- |
| **1. Cấu hình Hub** | ⚠️ **CẦN XÁC NHẬN** | Trong [`HubManager.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/HubManager.kt#L124-L128), Hub URL mặc định vừa đổi thành `https://hubip-danang.tek4tv.vn/hubServer` (trước đó là Phú Thọ, có comment Bắc Ninh). Cần chốt đúng địa bàn release. |
| **2. API gửi Log & Base URL** | ⚠️ **CẢNH BÁO** | - [`NetworkUtils.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/network/NetworkUtils.kt#L114): `BASE_URL = "https://box.dev.tek4tv.vn/"` (vẫn đang trỏ domain **DEV**).<br>- [`LogService.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/network/LogService.kt#L27): Đang mở `v2-provider-data-warehouse` (dành cho Quảng Ngãi, Hà Nam, Phú Thọ...), trong khi comment ghi Đà Nẵng dùng `provider-data-warehouse` (v1). Cần đồng bộ giữa Hub URL và API log. |
| **3. Dữ liệu Hardcode / Fix cứng** | ⚠️ **CẢNH BÁO** | [`GlobalProperties.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/GlobalProperties.kt#L28-L30): Đang fix cứng `DESTINATION_ID = "H44.126"` ("Xã Trí Đám"), `NAME_DEVICE = "TB Xã Trí Đám"`. |
| **4. Lệch Version (Version Mismatch)** | ❌ **NGUY HIỂM** | - [`app/build.gradle`](file:///d:/Git/IPRadio/app/build.gradle#L26-L27): `versionCode 12`, `versionName "v4.2.1"`.<br>- [`GlobalProperties.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/utils/GlobalProperties.kt#L23): `VERSION_NAME = "v43.4.2.2f"`.<br>- Tên Git branch: `4.2.2b`.<br>=> Ba nơi đang có 3 thông tin version khác nhau! |
| **5. Cấu hình Keystore (Signing)** | ❌ **LỖI BUILD** | [`app/build.gradle`](file:///d:/Git/IPRadio/app/build.gradle#L13-L16): Đường dẫn keystore bị hardcode tuyệt đối vào máy cá nhân: `C:\Users\xxxx9\Documents\GitHub\IPRadio\key.jks` kèm mật khẩu plaintext. Nếu build trên máy khác sẽ không tìm thấy keystore. |
| **6. An toàn lệnh Media Control** | ✅ **ĐÃ XỬ LÝ** | Đã bọc `try-catch` an toàn cho [`HubCommand.CONTROL_MEDIA`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L853-L930), chống crash khi nhận message rỗng hoặc sai định dạng số.<br>*(Lưu ý: Khối lệnh điều khiển media gốc ở dòng 870-890 vẫn đang được comment để ưu tiên nhánh xử lý riêng của Bắc Ninh).* |
| **7. Trạng thái mạng Serial (SIM hết cước)** | ✅ **ĐÃ XỬ LÝ** | Đã bổ sung cơ chế kiểm tra `isTokenReachable` nội bộ trong [`HubManager.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/HubManager.kt) và cập nhật [`SerialPortController.kt`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt), đảm bảo SIM hết cước sẽ gửi mã `0` (Mất mạng) thay vì bị kẹt ở mã `2`. |
| **8. Logcat chưa được gỡ** | ⚠️ **CẢNH BÁO** | [`app/proguard-rules.pro`](file:///d:/Git/IPRadio/app/proguard-rules.pro) chưa có rule strip `android.util.Log`. Tất cả các lệnh `Log.d`, `Log.i` (in token, URL, dữ liệu thiết bị) vẫn sẽ in ra logcat ở bản Release. |
| **9. Bảo mật Manifest** | ⚠️ **CHÚ Ý** | [`AndroidManifest.xml`](file:///d:/Git/IPRadio/app/src/main/AndroidManifest.xml#L27-L32): `allowBackup="true"` và `usesCleartextTraffic="true"`. |

---

## II. QUY TRÌNH CHECKLIST CHUẨN TRƯỚC MỖI LẦN RELEASE

### 1. Code Hygiene & Dữ liệu
- [ ] **Hardcode & Mock data**: Đã loại bỏ hoặc đưa về giá trị cấu hình mặc định an toàn (ID xã/huyện, tên thiết bị, volume mặc định).
- [ ] **Debug Flag**: Đã tắt toàn bộ cờ test, endpoint test, bypass login/auth.
- [ ] **Code bị comment**: Kiểm tra lại các file core (`MainActivity.kt`, `HubManager.kt`, `MediaScheduler.kt`) xem có luồng logic nào bị comment tạm thời chưa mở lại không.
- [ ] **Rà soát TODO/FIXME**: Không còn tác vụ dở dang ảnh hưởng đến luồng chính.

### 2. Môi trường, Hub & API
- [ ] **Hub Server URL**: Đã trỏ đúng URL WebSocket của tỉnh/địa bàn release (`HubManager.kt`).
- [ ] **API gửi Log & Telemetry**: Đã trỏ đúng server Data Warehouse tương ứng (`LogService.kt`).
- [ ] **Base API URL**: `NetworkUtils.BASE_URL` đã trỏ về server Production (không để `dev.tek4tv.vn`).
- [ ] **SSL / Domain Whitelist**: Kiểm tra các CDN stream âm thanh mới đã được cho phép truy cập.

### 3. Versioning & Build Configuration
- [ ] **Đồng bộ phiên bản**: `versionCode`, `versionName` trong `build.gradle` phải khớp với `GlobalProperties.VERSION_NAME` và Changelog release.
- [ ] **Keystore**: Đảm bảo file keystore và mật khẩu trỏ đúng đường dẫn tương đối hoặc lấy từ biến môi trường/properties an toàn.
- [ ] **ProGuard / R8**:
  - `minifyEnabled true`, `shrinkResources true`.
  - Có rule xóa `Log.d`, `Log.v` trong `proguard-rules.pro`.
  - Đã lưu file `mapping.txt` sau khi build để phân tích crash.

### 4. Tính năng cốt lõi (Đặc thù IPRadio)
- [ ] **Phát ngầm (Background Playback)**: Ứng dụng không bị tắt tiếng khi chạy lâu hoặc khi hệ thống vào chế độ tiết kiệm pin.
- [ ] **Mất mạng & Tự kết nối lại**: Kiểm tra rút dây LAN/tắt Wifi/4G và bật lại xem app có tự reconnect hub và phát tiếp lịch phát không.
- [ ] **Giao tiếp Serial (UART)**: Trạng thái báo mạng và đồng bộ phần cứng loa hoạt động chính xác.
- [ ] **Phát khẩn cấp (Force Media)**: Lệnh ưu tiên và ngắt luồng thông thường hoạt động đúng.

### 5. Kiểm thử thực tế (Sanity Test)
- [ ] Cài đặt trực tiếp file APK Release đã ký lên thiết bị thật.
- [ ] Kiểm tra khởi động từ lúc bật nguồn (Boot Completed Receiver).
- [ ] Xác nhận không có Crash/ANR trong ít nhất 15-30 phút phát liên tục.
