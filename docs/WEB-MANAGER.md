# AI TechFlow Web Manager

## Chức năng

- Dashboard responsive cho ADMIN và USER.
- Đăng ký, đăng nhập, đăng xuất, Google OAuth 2.0 và phân quyền.
- Thư viện video có tìm kiếm, lọc trạng thái và phân trang từ backend.
- Video Studio để sửa tiêu đề, chủ đề, mô tả, caption, hashtag và xem MP4.
- Python worker dựng video bằng FFmpeg; kết quả luôn về trạng thái
  `DRAFT_REQUIRES_REVIEW` để người dùng kiểm tra trước.
- Lịch TikTok/YouTube và trang quản trị người dùng.
- PostgreSQL + Hibernate + Flyway; Swagger/OpenAPI tại `/swagger`.

## Chạy local

Cần Java 21, Maven 3.9+, Node.js 20+, Python, PostgreSQL và FFmpeg.

```powershell
Copy-Item .env.example .env
docker compose up -d postgres

cd web-manager/frontend
npm ci
npm run build

cd ..
mvn spring-boot:run
```

Mở `http://localhost:8080`. Với local HTTP, đặt `SESSION_COOKIE_SECURE=false`.

## API chính

| Method | Path | Quyền | Chức năng |
|---|---|---|---|
| GET | `/api/health` | Public | Health check |
| GET | `/api/auth/config` | Public | Kiểm tra Google Login đã bật |
| POST | `/api/auth/register` | Public | Tạo USER local |
| POST | `/api/auth/login` | Public | Đăng nhập session |
| GET | `/api/auth/me` | User | Người dùng hiện tại + CSRF token |
| POST | `/api/auth/logout` | User | Hủy session |
| GET | `/api/dashboard` | User | Chỉ số theo người dùng; ADMIN xem toàn bộ |
| GET | `/api/tasks` | User | Tìm/lọc/phân trang video |
| POST | `/api/tasks` | User | Tạo công việc video |
| GET | `/api/tasks/{id}` | Owner/Admin | Chi tiết Video Studio |
| PUT | `/api/tasks/{id}` | Owner/Admin | Sửa metadata |
| DELETE | `/api/tasks/{id}` | Owner/Admin | Xóa task |
| POST | `/api/tasks/{id}/generate` | Owner/Admin | Chạy Python worker |
| GET/POST | `/api/publications` | Owner/Admin | Danh sách/tạo lịch xuất bản |
| GET/PUT | `/api/admin/users` | ADMIN | Phân trang và cập nhật tài khoản |

Các API phân trang dùng `page`, `size`, `query`; task hỗ trợ thêm `status`.
Swagger mô tả request/response thực tế tại `http://localhost:8080/swagger`.

## Quy trình video an toàn

1. Người dùng tạo task và bấm **Tạo bản nháp**.
2. Backend đặt trạng thái `GENERATING` và gọi `video_worker.py` bất đồng bộ.
3. Worker tạo kịch bản/voice/slide, ghép FFmpeg và tải MP4 lên Cloudinary.
4. Backend chỉ đặt `DRAFT_REQUIRES_REVIEW`; không tự đăng mạng xã hội.
5. Người dùng xem lại video, sửa caption và tự quyết định lịch/xuất bản.

## Deploy Render

`render.yaml` tạo web service và PostgreSQL. Các secret cần nhập trong Render:

- `OPENAI_API_KEY`
- `CLOUDINARY_URL`
- `TECHFLOW_ADMIN_EMAIL`
- `TECHFLOW_ADMIN_PASSWORD`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`

Production phải đặt `SESSION_COOKIE_SECURE=true` và dùng HTTPS.

## Bảo trì an toàn

Không tự xóa dữ liệu người dùng. Script dọn dẹp chỉ được áp dụng cho cache/build có
thể tạo lại sau khi đã xem báo cáo:

```powershell
.\audit-cleanup.ps1
.\audit-cleanup.ps1 -Apply
```

## Research và TikTok API

| Method | Path | Chức năng |
|---|---|---|
| GET | `/api/tiktok/status` | Trạng thái cấu hình/kết nối, không lộ token |
| GET | `/api/tiktok/connect` | Bắt đầu OAuth có state gắn với session |
| GET | `/oauth/tiktok/callback` | Đổi authorization code và mã hóa token |
| GET | `/api/tiktok/creator-info` | Lấy privacy và tùy chọn tương tác được phép |
| POST | `/api/tasks/{id}/publish/tiktok` | Gửi một video đã duyệt sau consent rõ ràng |
| POST | `/api/publications/{id}/tiktok/refresh` | Đồng bộ trạng thái xử lý từ TikTok |

TikTok Developer Portal phải khai báo callback production chính xác:
`https://ai-techflow-studio.onrender.com/oauth/tiktok/callback` và scope
`video.publish` đã được duyệt. Trước khi TikTok audit client, Creator Info có thể chỉ
cho phép `SELF_ONLY`; đây là giới hạn của nền tảng.
