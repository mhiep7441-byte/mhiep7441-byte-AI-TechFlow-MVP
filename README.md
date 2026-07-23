# AI TechFlow Studio

Ứng dụng quản lý quy trình tạo video công nghệ: từ ý tưởng, research, dựng cảnh, xem lại đến lịch xuất bản.

## Kiến trúc

- Backend: Java 21, Spring Boot, Hibernate/Spring Data JPA.
- Database: PostgreSQL, migration bằng Flyway.
- API docs: Swagger UI tại `http://localhost:8080/swagger`.
- Frontend: React, Vite, React Router và Lucide icons.
- Worker: Python, Gemini API/OpenAI Responses API (tùy chọn), Pillow, edge-tts, FFmpeg và Cloudinary.

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

Worker tạo video dọc 1080×1920, host minh họa tự vẽ bằng Pillow, hiệu ứng Ken Burns,
chuyển cảnh `xfade`, giọng đọc, phụ đề `.srt`, caption, `research.json` và `metadata.json`.
Thời lượng mục tiêu hỗ trợ từ 30 giây đến 10 phút; số cảnh tự tăng theo thời lượng.

```powershell
python video_worker.py --topic "AI agent kiểm thử mã nguồn" --duration 180 --no-upload
```

Để dùng Gemini/OpenAI và Cloudinary, sao chép `.env.example` thành `.env` rồi điền
secret ở môi trường chạy (không commit secret). `AI_PROVIDER=auto` ưu tiên Gemini nếu
có `GEMINI_API_KEY`, sau đó thử OpenAI, cuối cùng dùng kịch bản mẫu. Có thể ép provider
bằng `AI_PROVIDER=gemini` hoặc `AI_PROVIDER=openai`. `RESEARCH_URLS` nhận danh sách
URL HTTPS cách nhau bằng dấu phẩy; nếu để trống, worker chọn nguồn chính thức
theo chủ đề. Chỉ domain trong `RESEARCH_ALLOWED_DOMAINS` mới được đọc.

## Campaign và series

Trang **Campaign & series** cho phép đặt chủ đề xuyên suốt, 1–30 tập, thời lượng
30–600 giây/tập, phong cách hình ảnh và nhân vật nhất quán. API
`POST /api/campaigns/{id}/episodes` tạo toàn bộ tập thành các task riêng; mỗi tập
vẫn được research, tạo video và duyệt độc lập. Swagger mô tả đầy đủ nhóm API `Campaigns`.

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
Sau khi cấu hình `GEMINI_API_KEY` (hoặc `OPENAI_API_KEY`) và `CLOUDINARY_URL` trong Environment, Render
sẽ build và chạy ứng dụng qua HTTPS.

## Kiểm thử

```powershell
python -m unittest discover -s tests -v
cd web-manager
mvn test
cd frontend
npm run build
```

## Quality Gate

Worker tạo thêm `quality.json` với điểm 0–100 và các kiểm tra về số cảnh, lời
thoại, minh họa nhân vật, caption, hashtag và nguồn. Điểm chỉ là tín hiệu hỗ trợ;
video vẫn phải ở `DRAFT_REQUIRES_REVIEW` và cần người dùng duyệt trước khi lên lịch.

### API xu hướng an toàn

`GET /api/trends` đọc RSS/Atom từ các URL trong `TREND_FEEDS` (phân cách bằng dấu phẩy).
Chỉ URL HTTPS có host trong `TREND_ALLOWED_DOMAINS` được truy cập; feed có timeout ngắn,
giới hạn kích thước XML và chống external entity. Khi feed lỗi hoặc chưa cấu hình, API trả
gợi ý cố định với `fallback: true`; các gợi ý này không được coi là tin mới đã xác minh.
