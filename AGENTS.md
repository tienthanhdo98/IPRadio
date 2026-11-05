# Quy tắc làm việc trong dự án (Project Rules)

## 1. Nghiêm cấm tự ý sửa code (Approval Required Before Code Modification)
- **Tuyệt đối KHÔNG tự ý chỉnh sửa, tạo mới hoặc xóa mã nguồn** (không gọi `edit_file`, `write_file` để sửa code dự án) khi chưa có sự phê duyệt rõ ràng từ người dùng.
- Quy trình bắt buộc khi có yêu cầu sửa đổi, fix bug hoặc thêm tính năng:
  1. **Phân tích và đề xuất phương án**:
     - Nêu rõ nguyên nhân gốc rễ (Root Cause).
     - Liệt kê danh sách các file cần sửa (`Path`).
     - Trình bày chi tiết nội dung dự kiến sửa đổi (giải thích rõ thay đổi cái gì, code trước/sau khi sửa ra sao).
     - Đánh giá các rủi ro hoặc ảnh hưởng liên đới (nếu có).
  2. **Chờ người dùng chấp thuận**:
     - Dừng lại và đợi người dùng phản hồi xác nhận (ví dụ: *"Đồng ý"*, *"Triển khai"*, *"OK"*, *"Tiến hành"*...).
  3. **Thực thi khi có lệnh**:
     - Chỉ bắt đầu chỉnh sửa file mã nguồn sau khi người dùng đã duyệt phương án.
  4. **Kiểm tra và báo cáo**:
     - Biên dịch kiểm tra (`./gradlew compileDebugKotlin`) để đảm bảo dự án luôn trong trạng thái `BUILD SUCCESSFUL`.
     - Cập nhật đầy đủ vào `CHANGELOG.md`.

