# AI TechFlow Studio

Ứng dụng quản lý quy trình tạo video công nghệ, từ ý tưởng đến lịch xuất bản.

## Kiến trúc

- Backend: Java 21, Spring Boot, Hibernate/Spring Data JPA.
- Database: PostgreSQL 17, migration bằng Flyway.
- API docs: Swagger UI.
- Frontend: React, Vite, Lucide icons.

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

Hoặc chạy toàn bộ bằng:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\run.ps1
```

## Deploy to Render

The repository includes `Dockerfile` and `render.yaml` for React, Spring Boot,
and PostgreSQL. In Render, choose **New > Blueprint**, connect this repository,
and apply the Blueprint. Render provisions HTTPS and the database automatically.

## Research, hình ảnh và TikTok an toàn

Worker Python tạo kịch bản 4–6 cảnh, host minh họa tự vẽ bằng Pillow, giọng đọc,
phụ đề `.srt`, `research.json` và `metadata.json`. Nguồn nghiên cứu chỉ đọc qua
HTTPS từ allowlist; truyền `RESEARCH_URLS` hoặc nhập URL trong form. Khi không
đọc được nguồn, video được gắn `NEEDS_REVIEW` để kiểm chứng thủ công.

Video luôn bắt đầu ở `DRAFT_REQUIRES_REVIEW`. Chỉ thao tác **Xác nhận đã duyệt**
mới chuyển sang `DONE`; API chặn `READY/PUBLISHED` khi task chưa được duyệt hoặc
chưa có MP4. TikTok Direct Post yêu cầu app được duyệt `video.publish`; bản nháp
dùng Upload API với `video.upload` sau khi tích hợp OAuth và xác minh URL.
