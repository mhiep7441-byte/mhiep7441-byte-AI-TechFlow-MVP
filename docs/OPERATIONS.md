# Operations runbook

## Biến môi trường

| Tên | Bắt buộc | Mục đích |
|---|---:|---|
| `DATABASE_URL` / `DATABASE_URL_RAW` | Có | PostgreSQL |
| `TECHFLOW_TOKEN_ENCRYPTION_KEY` | Khi dùng social | Khóa AES-GCM, tối thiểu 32 ký tự |
| `CLOUDINARY_URL` | Khi dựng production | Lưu video |
| `GEMINI_API_KEY` | Không | Script/Series Planner ưu tiên |
| `OPENAI_API_KEY` | Không | Research, script fallback, ảnh |
| `TIKTOK_CLIENT_KEY/SECRET` | Khi dùng TikTok | OAuth/Content Posting |
| `YOUTUBE_CLIENT_ID/SECRET` | Khi dùng YouTube | OAuth + `youtube.upload` |
| `YOUTUBE_REDIRECT_URI` | Khi dùng YouTube | Callback đã đăng ký |

Không commit giá trị thật. Render khai báo secret với `sync: false`.

## Google/YouTube setup

1. Bật YouTube Data API v3 trong Google Cloud project.
2. Tạo OAuth Client loại Web application.
3. Thêm redirect URI `https://ai-techflow-studio.onrender.com/oauth/youtube/callback`.
4. Điền `YOUTUBE_CLIENT_ID` và `YOUTUBE_CLIENT_SECRET` ở Render.
5. Publish OAuth consent screen hoặc thêm tài khoản test.

YouTube dùng OAuth 2.0 server-side với scope tối thiểu `youtube.upload`. Tài liệu chính thức:
[OAuth for web server apps](https://developers.google.com/youtube/v3/guides/auth/server-side-web-apps),
[videos.insert](https://developers.google.com/youtube/v3/docs/videos/insert),
[resumable upload](https://developers.google.com/youtube/v3/guides/using_resumable_upload_protocol).

Project API chưa audit có thể bị YouTube giới hạn video upload ở chế độ private.

## Scheduler

- Poll mặc định mỗi 60 giây.
- Mỗi lượt claim tối đa 3 campaign đến hạn.
- `HOURLY`: cộng một giờ; `DAILY`: cộng một ngày.
- Kết quả luôn là draft cần duyệt.
- Render free có thể sleep. Scheduler chỉ chạy khi web service thức; để đảm bảo đúng giờ cần always-on worker/cron.

## Health và QA production

```powershell
Invoke-WebRequest https://ai-techflow-studio.onrender.com/api/health
Invoke-WebRequest https://ai-techflow-studio.onrender.com/api-docs
```

Checklist:

- `/api/health` trả 200.
- Anonymous `/api/campaigns` trả 401.
- User thường vào `/admin` bị chuyển về `/`.
- Swagger có admin dashboard, Series Planner, produce-next và YouTube upload.
- Flyway chạy V6.
- React asset mới trả 200.

## Recovery

- Server restart khi task `GENERATING`: task được đánh dấu `FAILED`, user dựng lại.
- AI provider lỗi: Gemini → OpenAI → offline planner.
- Research lỗi: worker ghi rõ offline và Content Guard chặn “ready for review”.
- Upload social lỗi: publication không được đánh dấu published; video draft vẫn trên Cloudinary.

