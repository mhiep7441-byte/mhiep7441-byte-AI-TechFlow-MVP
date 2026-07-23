# AI TechFlow Studio

Ứng dụng quản lý quy trình tạo video công nghệ: từ ý tưởng, research, dựng cảnh, xem lại đến lịch xuất bản.

## Kiến trúc

- Backend: Java 21, Spring Boot, Hibernate/Spring Data JPA.
- Database: PostgreSQL, migration bằng Flyway.
- API docs: Swagger UI tại `http://localhost:8080/swagger`.
- Frontend: React, Vite, React Router và Lucide icons.
- Worker: Python, OpenAI Responses API (tùy chọn), Pillow, edge-tts, FFmpeg và Cloudinary.

## Chạy local

```powershell
docker compose up -d postgres
cd web-manager
mvn spring-boot:run
```

Phát triển frontend riêng:

```powershell
cd web-manager/frontend
npm install
npm run dev
```

Vite chuyển tiếp `/api` sang backend ở cổng 8080. Hoặc chạy toàn bộ bằng:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\run.ps1
```

## Worker tạo video

Worker tạo video dọc 1080×1920 gồm 4–6 cảnh, host minh họa tự vẽ bằng Pillow,
giọng đọc, phụ đề `.srt`, caption, `research.json` và `metadata.json`.

```powershell
python video_worker.py --topic "AI agent kiểm thử mã nguồn" --no-upload
```

Để dùng OpenAI và Cloudinary, sao chép `.env.example` thành `.env` rồi điền
secret ở môi trường chạy (không commit secret). `RESEARCH_URLS` nhận danh sách
URL HTTPS cách nhau bằng dấu phẩy; nếu để trống, worker chọn nguồn chính thức
theo chủ đề. Chỉ domain trong `RESEARCH_ALLOWED_DOMAINS` mới được đọc.

## Review và TikTok

Video luôn bắt đầu ở `DRAFT_REQUIRES_REVIEW`. Người dùng phải xem cảnh, nhân vật,
phụ đề và nguồn rồi bấm **Xác nhận đã duyệt**; API `POST /api/tasks/{id}/review`
mới chuyển task sang `DONE`. Backend chặn `READY/PUBLISHED` nếu task chưa được
duyệt hoặc chưa có MP4, nên không có đường tắt để tự đăng nhầm.

TikTok Direct Post yêu cầu app được duyệt scope `video.publish`; Upload API gửi
bản nháp yêu cầu `video.upload`, OAuth và URL đã xác minh trên TikTok. Các token
phải nằm ở server/secret manager, không nằm trong React hay Git.

## Deploy Render

Repository có `Dockerfile` và `render.yaml` cho React, Spring Boot và PostgreSQL.
Trong Render chọn **New → Blueprint**, kết nối repository rồi áp dụng Blueprint.
Sau khi cấu hình `OPENAI_API_KEY` và `CLOUDINARY_URL` trong Environment, Render
sẽ build và chạy ứng dụng qua HTTPS.

## Kiểm thử

```powershell
python -m unittest discover -s tests -v
cd web-manager
mvn test
cd frontend
npm run build
```

### API xu hướng an toàn

`GET /api/trends` đọc RSS/Atom từ các URL trong `TREND_FEEDS` (phân cách bằng dấu phẩy).
Chỉ URL HTTPS có host trong `TREND_ALLOWED_DOMAINS` được truy cập; feed có timeout ngắn,
giới hạn kích thước XML và chống external entity. Khi feed lỗi hoặc chưa cấu hình, API trả
gợi ý cố định với `fallback: true`; các gợi ý này không được coi là tin mới đã xác minh.
