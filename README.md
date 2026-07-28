# AI TechFlow Studio

## Tài liệu hệ thống

- [Giới thiệu dự án, SRS và ERD](docs/SRS-ERD.md)
- [Kiến trúc, data model và pipeline](docs/ARCHITECTURE.md)
- [User flow, role và acceptance criteria](docs/USER-FLOWS.md)
- [Vận hành, YouTube OAuth và production QA](docs/OPERATIONS.md)

Ứng dụng quản lý quy trình tạo video công nghệ, từ ý tưởng đến lịch xuất bản.

## Kiến trúc

- Backend: Java 21, Spring Boot, Hibernate/Spring Data JPA.
- Database: PostgreSQL, Hibernate/Spring Data JPA, migration bằng Flyway.
- API docs: Swagger UI.
- Frontend: React hooks, React Router DOM, Vite, Lucide icons.
- Security: session authentication, BCrypt, CSRF, phân quyền `ADMIN`/`USER`, Google OAuth 2.0.
- Video: Python worker, Gemini/OpenAI, FFmpeg, giọng đọc tiếng Việt và Cloudinary.

## Chạy PostgreSQL

```powershell
docker compose up -d postgres
```

## Chạy backend

```powershell
cd web-manager
mvn spring-boot:run
```

Swagger: `http://localhost:8080/swagger`

Tài khoản quản trị đầu tiên được tạo từ `TECHFLOW_ADMIN_EMAIL` và
`TECHFLOW_ADMIN_PASSWORD`. Không đặt mật khẩu production trong source code.

## Phát triển frontend

```powershell
cd web-manager/frontend
npm install
npm run dev
```

Vite chuyển tiếp `/api` sang backend tại cổng 8080.

## Build frontend vào Spring Boot

```powershell
cd web-manager/frontend
npm run build
Copy-Item -Recurse -Force .\dist\* ..\src\main\resources\static\
```

Biến môi trường mẫu nằm trong `.env.example`. Không commit mật khẩu production hoặc API key.

## Google Login

Tạo OAuth client loại **Web application**, sau đó cấu hình:

- JavaScript origin: `https://ai-techflow-studio.onrender.com`
- Redirect URI: `https://ai-techflow-studio.onrender.com/login/oauth2/code/google`
- Render env: `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID`
- Render env: `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET`

Ứng dụng chỉ yêu cầu OpenID, email và hồ sơ cơ bản. Secret chỉ nằm trong biến môi trường.

Hoặc chạy toàn bộ bằng:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\run.ps1
```

## Deploy to Render

The repository includes `Dockerfile` and `render.yaml` for React, Spring Boot,
and PostgreSQL. In Render, choose **New > Blueprint**, connect this repository,
and apply the Blueprint. Render provisions HTTPS and the database automatically.

Health check công khai: `/api/health`. Các API dữ liệu còn lại yêu cầu đăng nhập.

Tài liệu chi tiết: [`docs/WEB-MANAGER.md`](docs/WEB-MANAGER.md) và
[`docs/SECURITY.md`](docs/SECURITY.md).

## Research, storyboard và TikTok Direct Post

- `research_agent.py` dùng OpenAI Responses API với web search, ưu tiên nguồn chính
  thức/sơ cấp, lưu claim và URL; khi thiếu API key, chế độ mẫu được ghi rõ là offline.
- `video_worker.py` tạo storyboard nhiều cảnh, nhân vật xuyên suốt, giọng Việt, phụ đề,
  Ken Burns, chuyển cảnh `xfade` và báo cáo QC. Nguồn, storyboard, fact-check và quality score được
  lưu trong PostgreSQL.
- Script Agent dùng `AI_PROVIDER=auto`: ưu tiên Gemini khi có `GEMINI_API_KEY`, sau đó
  dùng OpenAI và cuối cùng là storyboard offline. Khóa chỉ được lưu trong Environment
  của Render, không nằm trong React hoặc Git.
- Trang **Campaign & Series** tạo 1–30 tập từ một chủ đề, thời lượng 30 giây–10 phút/tập,
  giữ nhất quán phong cách và nhân vật. Mỗi tập là một task riêng và luôn ở
  `DRAFT_REQUIRES_REVIEW` sau khi dựng.
- TikTok OAuth token được mã hóa AES-GCM. Direct Post chỉ chạy sau khi người dùng mở
  hộp duyệt, chọn quyền riêng tư TikTok trả về và tích xác nhận đồng ý.
- TikTok client chưa được audit có thể chỉ được đăng `SELF_ONLY`. Đăng công khai cần
  TikTok duyệt scope `video.publish`; ứng dụng không vượt qua giới hạn này.

Các biến production nằm trong `.env.example`. Không đưa secret thật vào Git. Tài liệu
chính thức: [TikTok Direct Post](https://developers.tiktok.com/doc/content-posting-api-reference-direct-post),
[TikTok Creator Info](https://developers.tiktok.com/doc/content-posting-api-get-started/) và
[OpenAI models](https://developers.openai.com/api/docs/models).

## Chạy test

```powershell
python -m pytest -q
cd web-manager\frontend
npm test
npm run build
cd ..
mvn test
```
