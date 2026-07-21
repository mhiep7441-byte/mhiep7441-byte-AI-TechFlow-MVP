# AI TechFlow Web Manager

## Kiến trúc

- React/Vite: dashboard Kanban responsive.
- Java 21 + Spring Boot: REST API và chạy pipeline Python bất đồng bộ.
- H2 file database: tự tạo tại `web-manager/data/techflow.mv.db`, không cần cài DB server.
- Swagger UI: `http://localhost:8080/swagger`.
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/techflow`, user `sa`, mật khẩu trống).

## Chạy

Cần Java 21, Maven 3.9+, Node.js 20+, Python và FFmpeg. Từ thư mục gốc:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\run-web.ps1
```

Mở `http://localhost:8080`. Lần chạy đầu tự tải package, build React và tạo database.

## API

| Method | Path | Chức năng |
|---|---|---|
| GET | `/api/tasks` | Danh sách task |
| POST | `/api/tasks` | Tạo task |
| PUT | `/api/tasks/{id}` | Cập nhật task |
| DELETE | `/api/tasks/{id}` | Xóa task |
| POST | `/api/tasks/{id}/generate` | Chạy pipeline video |

Video thành công luôn có trạng thái `DRAFT_REQUIRES_REVIEW`; hệ thống không tự đăng.

## Bảo trì an toàn

Không tự xóa dữ liệu người dùng. Chỉ nên dọn các thư mục có thể sinh lại (`web-manager/target`, `web-manager/frontend/node_modules`) sau khi đã dừng ứng dụng. Luôn xem báo cáo dung lượng trước khi xóa.

```powershell
.\audit-cleanup.ps1        # chỉ quét và báo cáo
.\audit-cleanup.ps1 -Apply # chỉ xóa cache/build đã liệt kê
```
