# Security model

## Authentication

- Tài khoản local dùng BCrypt cost 12; mật khẩu thô không được lưu.
- Google Login dùng Authorization Code flow do Spring Security quản lý.
- Session cookie có `HttpOnly`, `SameSite=Lax`; production bật `Secure`.
- Đăng xuất hủy session và xóa security context.

## Authorization

- `USER` chỉ thấy và sửa task/publication do mình sở hữu.
- `ADMIN` xem toàn workspace và quản lý vai trò/trạng thái tài khoản.
- Backend kiểm tra quyền sở hữu; giao diện ẩn nút không thay thế kiểm tra server.

## Request security

- CSRF token bắt buộc cho thao tác thay đổi dữ liệu sau đăng nhập.
- Bean Validation giới hạn trường đầu vào; lỗi API có cấu trúc và không trả stack trace.
- Security headers gồm CSP, chống iframe, content type sniffing và referrer policy.
- `/api/health`, auth config/login/register và static assets là public; dữ liệu nghiệp vụ cần session.

## Secrets

- Không commit `.env`, API key, OAuth secret hoặc access token.
- Production lưu secret trong Render Environment.
- Sau khi secret xuất hiện trong chat/log/screenshot, phải rotate tại nhà cung cấp và cập nhật Render.

## Publishing guardrail

Worker không tự publish. Video hoàn tất luôn vào `DRAFT_REQUIRES_REVIEW`; người dùng
phải xem lại nội dung, nguồn, caption và tài khoản đích trước bất kỳ thao tác đăng nào.
