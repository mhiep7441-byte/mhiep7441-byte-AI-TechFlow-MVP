# AI TechFlow Studio — Kiến trúc hệ thống

## Mục tiêu

AI TechFlow là content operating system cho video dọc tiếng Việt. Hệ thống quản lý user, campaign/series, nghiên cứu nguồn, kịch bản nhiều cảnh, dựng video, review và upload có xác nhận. Nguyên tắc bất biến: worker chỉ tạo `DRAFT_REQUIRES_REVIEW`; scheduler không tự xuất bản.

## System context

```mermaid
flowchart LR
    Creator["Creator / User"] --> Web["React Web App"]
    Admin["Administrator"] --> Web
    Web --> API["Spring Boot API"]
    API --> DB[("PostgreSQL")]
    API --> Worker["Python AI + FFmpeg Worker"]
    Worker --> OpenAI["OpenAI Research / Image"]
    Worker --> Gemini["Gemini Script / Series Planner"]
    Worker --> Cloudinary["Cloudinary Video Storage"]
    API --> TikTok["TikTok Content Posting API"]
    API --> YouTube["YouTube Data API"]
    Web -. "review + explicit consent" .-> TikTok
    Web -. "review + explicit consent" .-> YouTube
```

## Container architecture

```mermaid
flowchart TB
    subgraph Browser["Browser"]
      Router["React Router"]
      UserUI["User Workspace\n/videos /campaigns /profile"]
      AdminUI["Admin Workspace\n/admin /admin/users"]
      Router --> UserUI
      Router --> AdminUI
    end
    subgraph JVM["Spring Boot 3 / Java 21"]
      Security["Session Auth + CSRF + RBAC"]
      Controllers["REST + OpenAPI/Swagger"]
      Domain["Task / Campaign / Publication Services"]
      Scheduler["Campaign Scheduler"]
      OAuth["TikTok + YouTube OAuth"]
      Security --> Controllers --> Domain
      Scheduler --> Domain
      OAuth --> Domain
    end
    subgraph Python["Python 3.11+ worker"]
      Planner["Series Planner\nGemini → OpenAI → Offline"]
      Research["Research Agent\nofficial/primary sources"]
      Script["Storyboard Agent"]
      Guard["Content Guard + Fact Check"]
      Render["TTS + FFmpeg motion/xfade"]
      Planner --> Script
      Research --> Script --> Guard --> Render
    end
    Browser --> Security
    Domain --> PostgreSQL[("PostgreSQL + Flyway")]
    Domain --> Python
    Render --> Cloudinary["Cloudinary"]
    OAuth --> Platforms["TikTok / YouTube"]
```

## Luồng campaign theo lịch

```mermaid
sequenceDiagram
    actor User
    participant UI as Campaign UI
    participant API as CampaignService
    participant Planner as series_planner.py
    participant DB as PostgreSQL
    participant Scheduler
    participant Worker as video_worker.py
    User->>UI: Tạo chủ đề, audience, nhân vật, cadence
    UI->>API: POST /api/campaigns
    User->>UI: Lên ý tưởng AI
    UI->>API: POST /api/campaigns/{id}/plan
    API->>Planner: theme + audience + episode count
    Planner-->>API: Series Bible JSON
    API->>DB: Lưu series_plan_json
    User->>UI: Tạo danh sách tập
    UI->>API: POST /api/campaigns/{id}/episodes
    API->>DB: Tạo task TODO theo episode
    loop Mỗi phút khi server hoạt động
      Scheduler->>API: Claim campaign đến hạn
      API->>DB: TODO → GENERATING; tính next_run_at
      API->>Worker: Tạo bản nháp
      Worker->>Worker: Research → Script → Guard → FFmpeg
      Worker-->>DB: DRAFT_REQUIRES_REVIEW
    end
    User->>UI: Xem video + nguồn + xác nhận
    UI->>API: Upload TikTok hoặc YouTube
```

## Data model

```mermaid
erDiagram
    APP_USERS ||--o{ CAMPAIGNS : owns
    APP_USERS ||--o{ WORK_TASKS : owns
    APP_USERS ||--o| TIKTOK_ACCOUNTS : connects
    APP_USERS ||--o| YOUTUBE_ACCOUNTS : connects
    CAMPAIGNS ||--o{ WORK_TASKS : contains
    WORK_TASKS ||--o{ PUBLICATIONS : publishes
    APP_USERS { bigint id PK
      varchar email UK
      varchar role
      boolean enabled
      varchar auth_provider
    }
    CAMPAIGNS { bigint id PK
      bigint owner_id FK
      varchar cadence
      boolean production_enabled
      timestamp next_run_at
      text series_plan_json
    }
    WORK_TASKS { bigint id PK
      bigint owner_id FK
      bigint campaign_id FK
      varchar status
      text research_json
      text storyboard_json
      integer quality_score
    }
    PUBLICATIONS { bigint id PK
      bigint task_id FK
      varchar platform
      varchar status
      varchar external_id
    }
```

## Security boundaries

- Session cookie HttpOnly, Secure trên production, SameSite Lax.
- CSRF token bắt buộc cho request thay đổi dữ liệu.
- `/api/admin/**` chỉ dành cho `ROLE_ADMIN`.
- User chỉ đọc/sửa task và campaign của mình; admin quan sát toàn hệ thống.
- OAuth state được gắn với session và owner id.
- TikTok/YouTube token mã hóa AES-GCM bằng `TECHFLOW_TOKEN_ENCRYPTION_KEY`.
- URL media upload chỉ nhận HTTPS từ `res.cloudinary.com`.
- API key/client secret chỉ đi qua environment variables; không lưu trong source, response hoặc log.

## Trạng thái review-first

```mermaid
stateDiagram-v2
    [*] --> TODO
    TODO --> GENERATING: user hoặc scheduler
    GENERATING --> DRAFT_REQUIRES_REVIEW: worker thành công
    GENERATING --> FAILED: pipeline lỗi
    DRAFT_REQUIRES_REVIEW --> DONE: user duyệt
    DRAFT_REQUIRES_REVIEW --> GENERATING: user dựng lại
    DRAFT_REQUIRES_REVIEW --> PROCESSING: user xác nhận upload
```

`PROCESSING` phía cuối là trạng thái `Publication`, không thay thế trạng thái review của `WorkTask`.
