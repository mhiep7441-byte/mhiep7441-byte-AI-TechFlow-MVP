# AI TechFlow MVP

Hệ thống tạo video ngắn 9:16 cho kênh công nghệ, lập trình và xu hướng.

## MVP làm được gì?

1. Nhận một chủ đề.
2. Dùng OpenAI để tạo kịch bản JSON nếu có `OPENAI_API_KEY`.
3. Nếu chưa có API key, chạy bằng dữ liệu mẫu để kiểm tra pipeline.
4. Tạo slide dọc 1080×1920.
5. Tạo giọng đọc offline bằng Windows SAPI/pyttsx3.
6. Ghép slide + voice thành MP4 bằng FFmpeg.
7. Tạo file SRT và metadata đăng bài.
8. Lưu toàn bộ kết quả theo từng job.

## Yêu cầu

- Windows 10/11
- Python 3.11+
- FFmpeg có trong PATH
- OpenAI API key (không bắt buộc để test)

## Cài đặt

Mở PowerShell trong thư mục dự án:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env
```

Kiểm tra FFmpeg:

```powershell
ffmpeg -version
ffprobe -version
```

Nếu chưa có:

```powershell
winget install Gyan.FFmpeg
```

## Chạy thử không cần API

```powershell
python main.py --topic "Codex có thể tự sửa bug như thế nào?"
```

## Chạy bằng OpenAI

Mở `.env` và thêm:

```env
OPENAI_API_KEY=sk-...
```

Sau đó:

```powershell
python main.py --topic "5 xu hướng AI coding đáng chú ý"
```

## Chạy tự động mỗi ngày

```powershell
python scheduler.py
```

Mặc định chạy lúc 08:00. Chỉnh trong `.env`:

```env
DAILY_RUN_TIME=08:00
```

## Kết quả

```text
outputs/
└── 20260721_210000_codex-co-the-tu-sua-bug/
    ├── script.json
    ├── narration.txt
    ├── narration.wav
    ├── subtitles.srt
    ├── metadata.json
    ├── scenes/
    └── final.mp4
```

## Lưu ý

- Đây là MVP tạo bản nháp, chưa tự đăng TikTok/YouTube.
- Luôn kiểm tra tính chính xác của tin công nghệ trước khi đăng.
- Không dùng logo, hình ảnh hoặc footage không có quyền sử dụng.
- Voice offline có thể chưa tự nhiên; có thể thay bằng OpenAI TTS hoặc ElevenLabs sau.
