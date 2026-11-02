# Ma Trận Kịch Bản Phát & Quy Trình Bật / Tắt Công Suất PA

> **Dự án**: IPRadio (Hệ thống truyền thanh thông minh IP)  
> **Tài liệu**: Quy chuẩn kỹ thuật điều khiển công suất PA (Power Amplifier) theo từng chế độ phát.

---

## I. Tổng Hợp Các Kịch Bản Phát

Danh sách của bạn đã bao quát **5 kịch bản cốt lõi**. Để đảm bảo độ bền phần cứng và tính ổn định tuyệt đối ngoài thực địa, chúng tôi bổ sung thêm **6 kịch bản thực tế quan trọng** (tổng cộng **11 kịch bản**):

1. **Kịch bản 1**: Phát lịch chỉ toàn file âm thanh (VOD / MP3 / AAC).
2. **Kịch bản 2**: Phát lịch xen kẽ file âm thanh và luồng trực tiếp (MP3 ➔ Live ➔ MP3).
3. **Kịch bản 3**: Phát Live khẩn cấp chèn ngang lịch đang phát (`ForceMedia` Live đè lịch).
4. **Kịch bản 4**: Phát Live khẩn cấp độc lập (ngoài giờ lịch, không trùng lịch nào).
5. **Kịch bản 5**: Phát nhiều khung giờ lịch trong 24h (nhiều ca phát trong ngày).
6. ⭐ *(Bổ sung 1)*: **Phát lịch có đài FM / AM (Tuner ngoại vi)**: Chuyển đổi nguồn âm thanh giữa Android DAC và chip Tuner.
7. ⭐ *(Bổ sung 2)*: **Mất mạng / Lỗi luồng Live giữa chừng**: Cơ chế retry và dập PA chống phát tiếng xì/rác loa.
8. ⭐ *(Bổ sung 3)*: **Thiết bị bật nguồn / Khởi động lại ĐÚNG VÀO GIỜ ĐANG PHÁT LỊCH**: Tính điểm phát tiếp (resume point) và kích hoạt PA.
9. ⭐ *(Bổ sung 4)*: **Thiết bị bật nguồn NGOÀI GIỜ LỊCH (Trạng thái chờ - Standby)**: Giữ PA tắt tuyệt đối, không được rò điện ra loa.
10. ⭐ *(Bổ sung 5)*: **Nhận lệnh DỪNG KHẨN CẤP từ Web / Hub**: Cắt PA tức thì khi có lệnh dừng cưỡng bức.
11. ⭐ *(Bổ sung 6)*: **Bảo trì chuyển ngày lúc 00:15 hàng đêm (`ACTION_CHECKHUB_RESET`)**: Tắt PA an toàn trước khi khởi động lại dịch vụ.

---

## II. Chi Tiết Hành Vi & Trạng Thái Bật/Tắt PA Từng Kịch Bản

---

### Kịch Bản 1: Phát lịch chỉ toàn file âm thanh (MP3 / AAC)
- **Mô tả**: Khung giờ gồm danh sách các file ghi âm, bài hát (Ví dụ: 06:00 - 06:30 gồm 3 bài MP3).
- **Quy trình PA**:
  1. **Khi bắt đầu bài 1**: VLC decode thành công ➔ [`onPlayMediaSuccess`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L200) gửi [`turnOnPAVod()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt#L207) ➔ **PA BẬT**.
  2. **Khi chuyển tiếp giữa các bài (Bài 1 ➔ Bài 2 ➔ Bài 3)**: [`playNextMedia()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/PlayersManager.kt#L151) tăng index và phát tiếp ➔ **PA GIỮ BẬT LIÊN TỤC**, không ngắt rơ-le, âm thanh liền mạch.
  3. **Khi phát xong bài cuối cùng**: `playlistIndex >= currentPlaylist.size` ➔ gọi `stopTimeFrame()` ➔ [`onTimeOver()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L140) gửi [`turnOffPA()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt#L237) ➔ **PA TẮT NGAY LẬP TỨC**.

---

### Kịch Bản 2: Phát lịch xen kẽ file và luồng trực tiếp (Live ➔ MP3 ➔ m3u8 hoặc MP3 ➔ Live ➔ MP3)
- **Mô tả**: Khung giờ gồm các tiết mục kết hợp cả file âm thanh và luồng tiếp sóng (Ví dụ: 06:00 - 07:00: Bắt đầu bằng Live VOV ➔ Bài 2 MP3 ➔ Bài 3 luồng m3u8 đài tỉnh).
- **Quy trình PA**:
  1. **Khi bắt đầu (kể cả bài 1 là Live hay MP3)**: Khi VLC nhận luồng và bắt đầu có tiếng (`MediaPlayer.Event.Playing`) ➔ [`onPlayMediaSuccess`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L200) gửi `turnOnPAVod()` ➔ **PA BẬT**. *(Nếu Live 1 bị mất mạng, PA giữ tắt chống xì loa và tự động nhảy sang bài MP3 tiếp theo)*.
  2. **Chuyển tiếp giữa các tiết mục (Live ➔ MP3 ➔ m3u8)**: Khi một tiết mục kết thúc, `VlcMediaPlayer` báo `onMediaForceEndReached()` hoặc `onMediaEndReached()`. Do `playMode` là tên khung giờ lịch (không phải `PLAY_MODE_FORCE`), hàm `onEndForceMedia()` không hề bị gọi ➔ Hệ thống gọi thẳng [`playNextMedia()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/PlayersManager.kt#L151) ➔ **PA GIỮ NGUYÊN BẬT LIÊN TỤC**, không ngắt rơ-le, âm thanh chuyển tiếp mượt mà.
  3. **Khi hết bài cuối cùng hoặc hết giờ ca phát**: Gọi `stopTimeFrame()` ➔ [`onTimeOver()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L140) gửi [`turnOffPA()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt#L237) ➔ **PA TẮT DỨT ĐIỂM NGAY LẬP TỨC**.

---

### Kịch Bản 3: Phát Live khẩn cấp chèn vào lịch đang phát
- **Mô tả**: Lúc 06:15 đang phát bài theo lịch, Hub gửi lệnh Live khẩn cấp (thông báo bão) thời lượng 10 phút.
- **Quy trình PA**:
  1. **Khi nhận lệnh khẩn cấp**: Dừng bài đang phát trong lịch, nạp luồng khẩn cấp ➔ **PA GIỮ BẬT** (hoặc kích hoạt lại đảm bảo mức âm lượng khẩn cấp).
  2. **Trong lúc phát khẩn cấp**: Loa phát trực tiếp nội dung khẩn cấp, PA giữ bật ổn định.
  3. **Khi Live khẩn cấp kết thúc**:
     - [`onEndForceMedia()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L183) gọi [`turnOffPA()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt#L237) ➔ **PA TẮT TẠM THỜI** (ngắt ngay âm thanh luồng live cũ).
     - Gọi `startPlayingMedia()` kiểm tra: Hiện tại vẫn trong khung giờ 06:00 - 07:00 ➔ Tự động tính toán nạp tiếp bài hát theo lịch.
     - Khi bài theo lịch bắt đầu có tiếng (`onPlayMediaSuccess` sau ~0.5s) ➔ **PA BẬT LẠI** và tiếp tục phát lịch thường.

---

### Kịch Bản 4: Phát Live khẩn cấp độc lập (ngoài giờ lịch)
- **Mô tả**: Thiết bị đang ở trạng thái nghỉ (ví dụ lúc 10h trưa không có lịch phát), Hub gửi lệnh Live khẩn cấp.
- **Quy trình PA**:
  1. **Khi nhận lệnh**: Bắt đầu luồng Live khẩn cấp ➔ `onPlayMediaSuccess` gửi `turnOnPAVod()` ➔ **PA BẬT**.
  2. **Trong khi phát**: Loa phát thông báo khẩn cấp, PA giữ bật liên tục.
  3. **Khi Live khẩn cấp kết thúc**:
     - `onEndForceMedia()` gửi `turnOffPA()` ➔ **PA TẮT NGAY LẬP TỨC**.
     - Xóa dữ liệu khẩn cấp và gọi `startPlayingMedia()`. Vì ngoài giờ lịch, `startFirstTimeFrame()` không tìm thấy khung giờ nào ➔ xác nhận `onTimeOver()`.
     - Phản hồi 5s định kỳ từ MCU được `isPlayingProvider` bảo vệ: Vì player không phát (`isPlaying == false`), MCU báo PA tắt sẽ **KHÔNG bao giờ bị kích hoạt lại**. ➔ **PA GIỮ TẮT VĨNH VIỄN**.

---

### Kịch Bản 5: Phát nhiều khung giờ lịch trong ngày (chỉ trong 24h)
- **Mô tả**: Lịch trong ngày có 3 ca phát: Ca sáng (06:00 - 07:00), Ca trưa (11:30 - 12:00), Ca chiều (17:00 - 17:45).
- **Quy trình PA**:
  1. **Khoảng thời gian nghỉ giữa các ca (ví dụ 07:01 - 11:29)**: PA ở trạng thái **TẮT HOÀN TOÀN**. Loa im lặng, không tiêu thụ điện công suất, không bị xì loa.
  2. **Đến giờ hẹn ca trưa (11:30:00)**: AlarmManager đánh thức `MediaScheduleReceiver` ➔ kích hoạt `setTimeFrame("11:30:00-12:00:00")` ➔ **PA TỰ ĐỘNG BẬT**.
  3. **Hết giờ ca trưa (12:00:00)**: Alarm kết thúc kích hoạt `stopPlaylist()` ➔ `onTimeOver()` ➔ **PA TỰ ĐỘNG TẮT**.
  4. Lặp lại tương tự chuẩn xác cho ca chiều.

---

### Kịch Bản 6 (Bổ sung): Phát lịch có đài FM / AM (Tuner ngoại vi)
- **Mô tả**: Khung giờ gồm: Bài 1 MP3 ➔ Bài 2 Tiếp sóng đài FM 91MHz ➔ Bài 3 MP3.
- **Quy trình PA**:
  1. **Bài 1 (MP3)**: PA ở chế độ VOD/Android (`MODE_VOD_LIVE`).
  2. **Chuyển sang Bài 2 (FM)**: [`onPlayTuner()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L154) gửi lệnh [`playTuner()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt#L258) xuống MCU ➔ MCU chuyển mạch Audio sang chip FM và **BẬT PA CHẾ ĐỘ TUNER**.
  3. **Hết giờ FM**: Handler đếm hết thời lượng ➔ gọi chuyển sang Bài 3 MP3 ➔ Hệ thống gửi `turnOnPAVod()` chuyển mạch lại âm thanh Android ➔ **PA GIỮ BẬT**, không bị gián đoạn.

---

### Kịch Bản 7 (Bổ sung): Mất mạng / Lỗi luồng Live giữa chừng
- **Mô tả**: Đang tiếp sóng Live thì mất 4G hoặc server luồng bị sập.
- **Quy trình PA**:
  1. Khi mất kết nối: `VlcMediaPlayer` phát hiện `EncounteredError` / `EndReached sớm`.
  2. Hệ thống kiểm tra thời gian khung giờ: Nếu vẫn còn trong giờ phát, tiến hành **thử kết nối lại tối đa 5 lần** (khoảng 15s).
  3. **Nếu sau 5 lần vẫn mất mạng**: Xác nhận kết thúc luồng Live lỗi ➔ [`onLiveError()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/ui/MainActivity.kt#L190) gọi [`turnOffPA()`](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/SerialPortController.kt#L237) ➔ **PA DẬP TẮT NGAY LẬP TỨC**. 
  *(Mục đích: Không để PA mở khi không có âm thanh, tránh tiếng rít/xì loa gây phiền hà cho người dân).*

---

### Kịch Bản 8 (Bổ sung): Thiết bị mất điện bật lại ĐÚNG VÀO GIỜ ĐANG PHÁT LỊCH
- **Mô tả**: Khung giờ từ 06:00 - 07:00. Thiết bị mất điện từ tối, có điện trở lại lúc 06:15:00.
- **Quy trình PA**:
  1. Khi Android khởi động xong: `MainActivity` gọi `startPlayingMedia()` ➔ [startFirstTimeFrame()](file:///d:/Git/IPRadio/app/src/main/java/app/tek4tv/ipradio/core/players_helper/PlayersManager.kt#L107) quét các khung giờ.
  2. Phát hiện thời điểm hiện tại (06:15) nằm trong khung 06:00 - 07:00 ➔ Tính toán `resumePoint` (đang ở bài nào, giây thứ bao nhiêu).
  3. Khởi động phát đúng bài hát tại vị trí thời gian tương ứng ➔ Khi bài bắt đầu phát (`onPlayMediaSuccess`) ➔ **PA TỰ ĐỘNG BẬT** và phát tiếp phần còn lại của lịch.

---

### Kịch Bản 9 (Bổ sung): Thiết bị mất điện bật lại NGOÀI GIỜ LỊCH (Standby)
- **Mô tả**: Thiết bị cắm điện bật lên lúc 14:00 (thời điểm không có lịch phát nào).
- **Quy trình PA**:
  1. Khi khởi động: Tải lịch từ cache/mạng, đặt lại các mốc Alarm cho ca chiều.
  2. Quét kiểm tra khung giờ: Không có khung giờ nào khớp ➔ gọi `onTimeOver()` ➔ gửi `turnOffPA()`.
  3. **PA DUY TRÌ TRẠNG THÁI TẮT TUYỆT ĐỐI**, thiết bị vào chế độ trực chờ (Standby) êm ái.

---

### Kịch Bản 10 (Bổ sung): Nhận lệnh DỪNG KHẨN CẤP từ Web / Hub
- **Mô tả**: Đang phát (lịch hoặc khẩn cấp), người quản trị bấm nút "Dừng phát" trên phần mềm điều hành.
- **Quy trình PA**:
  1. Hub nhận lệnh ➔ gọi `playerManager.stopAllPlayer()` và `playerManager.stopForceMedia()`.
  2. Lệnh gọi ngay `turnOffPA()` ➔ **PA CẮT TỨC THÌ**, âm thanh ngắt ngay lập tức theo lệnh điều hành.

---

### Kịch Bản 11 (Bổ sung): Bảo trì chuyển ngày lúc 00:15 hàng đêm (`ACTION_CHECKHUB_RESET`)
- **Mô tả**: Đúng 00:15 mỗi đêm, hệ thống tự động dọn dẹp và nạp ngày mới.
- **Quy trình PA**:
  1. AlarmManager kích hoạt `ACTION_CHECKHUB_RESET`.
  2. Hủy toàn bộ hẹn giờ cũ, gọi `serialPortController.turnOffPA()` ➔ **ĐẢM BẢO PA TẮT TUYỆT ĐỐI**.
  3. Cập nhật lịch tháng, nghỉ 2 phút và khởi động lại ngoại vi để sẵn sàng cho ngày mới.

---

## III. Ma Trận Trạng Thái PA Tổng Hợp (Tra Cứu Nhanh)

| STT | Kịch Bản | Trạng Thái Bắt Đầu | Quá Trình Phát / Chuyển Bài | Trạng Thái Kết Thúc |
| :---: | :--- | :---: | :---: | :---: |
| **1** | **Chỉ toàn file âm thanh** | **BẬT** khi bài 1 chạy | **BẬT LIÊN TỤC** giữa các bài | **TẮT NGAY** khi hết bài cuối |
| **2** | **Xen kẽ File & Live** | **BẬT** khi bắt đầu | **BẬT LIÊN TỤC** (MP3 ➔ Live ➔ MP3) | **TẮT NGAY** khi hết khung giờ |
| **3** | **Live khẩn cấp chèn lịch** | **BẬT** ngay lập tức | Giữ bật suốt bản tin khẩn cấp | **TẮT TẠM** ngắt luồng live cũ ➔ **BẬT LẠI** khi khôi phục bài lịch |
| **4** | **Live khẩn cấp độc lập** | **BẬT** ngay lập tức | Giữ bật suốt bản tin khẩn cấp | **TẮT DỨT ĐIỂM NGAY**, chống bật lại vĩnh viễn |
| **5** | **Nhiều ca trong ngày** | **BẬT** đúng giờ từng ca | Giữ bật trong suốt ca | **TẮT HOÀN TOÀN** trong thời gian nghỉ giữa các ca |
| **6** | **Lịch phát đài FM/AM** | **BẬT** mode Tuner | Giữ bật khi chuyển Tuner ➔ VOD | **TẮT NGAY** khi hết ca |
| **7** | **Mất mạng khi Live** | Bật lúc có mạng | Retry tối đa 5 lần (~15s) | **TẮT NGAY** sau 5 lần thất bại để chống xì loa |
| **8** | **Bật nguồn ĐÚNG GIỜ lịch** | Standby ban đầu | Tính offset phát tiếp ➔ **TỰ BẬT PA** | **TẮT NGAY** khi hết ca phát |
| **9** | **Bật nguồn NGOÀI GIỜ lịch** | **TẮT TUYỆT ĐỐI** | **GIỮ TẮT** suốt thời gian chờ | Chờ đến giờ hẹn ca tiếp theo |
| **10** | **Lệnh Dừng từ Hub** | Đang phát (BẬT) | Ngắt tức thì | **CẮT PA NGAY LẬP TỨC** |
| **11** | **Reset đêm (00:15)** | Nghỉ đêm (TẮT) | Kiểm tra dập tắt PA | **GIỮ TẮT**, bảo trì phần cứng an toàn |
