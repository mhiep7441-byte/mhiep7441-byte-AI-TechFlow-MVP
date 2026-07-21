from __future__ import annotations

import argparse
import json
import logging
import os
import re
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from PIL import Image, ImageDraw, ImageFont
import pyttsx3

try:
    from openai import OpenAI
except ImportError:
    OpenAI = None


load_dotenv()

OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "outputs"))
CHANNEL_NAME = os.getenv("CHANNEL_NAME", "TechFlow VN")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-5-mini")
VOICE_RATE = int(os.getenv("VOICE_RATE", "180"))
MAX_SCENES = int(os.getenv("MAX_SCENES", "7"))

WIDTH = 1080
HEIGHT = 1920
FPS = 30

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s",
)


@dataclass
class Scene:
    title: str
    narration: str
    on_screen_text: str
    duration_hint: float = 5.0


@dataclass
class VideoPlan:
    topic: str
    hook: str
    scenes: list[Scene]
    caption: str
    hashtags: list[str]
    source_notes: list[str]


def slugify(value: str) -> str:
    value = value.lower().strip()
    value = re.sub(r"[^\w\s-]", "", value, flags=re.UNICODE)
    value = re.sub(r"[-\s]+", "-", value)
    return value[:60].strip("-") or "video"


def ensure_tools() -> None:
    missing = [tool for tool in ("ffmpeg", "ffprobe") if shutil.which(tool) is None]
    if missing:
        raise RuntimeError(
            "Thiếu công cụ: "
            + ", ".join(missing)
            + ". Hãy cài FFmpeg và mở lại terminal."
        )


def mock_plan(topic: str) -> VideoPlan:
    scenes = [
        Scene(
            title="HOOK",
            narration=f"{topic}. Nghe phức tạp, nhưng mình giải thích trong chưa đầy một phút.",
            on_screen_text=topic,
            duration_hint=5,
        ),
        Scene(
            title="VẤN ĐỀ",
            narration="Trước đây, lập trình viên phải tự dò lỗi, đọc log và sửa từng phần bằng tay.",
            on_screen_text="Đọc log → tìm lỗi → sửa code",
            duration_hint=6,
        ),
        Scene(
            title="AI CODING",
            narration="Coding agent có thể đọc code liên quan, chạy test và đề xuất bản sửa.",
            on_screen_text="Đọc code • Chạy test • Đề xuất sửa",
            duration_hint=6,
        ),
        Scene(
            title="VÒNG LẶP",
            narration="Khi build thất bại, hệ thống gửi log trở lại agent, rồi chạy lại cho đến khi đạt điều kiện dừng.",
            on_screen_text="Build → Log → Fix → Test",
            duration_hint=7,
        ),
        Scene(
            title="GIỚI HẠN",
            narration="Nhưng vẫn cần giới hạn quyền truy cập, ngân sách và bước duyệt trước khi triển khai thật.",
            on_screen_text="Sandbox • Budget • Human review",
            duration_hint=7,
        ),
        Scene(
            title="KẾT",
            narration="Đó là cách một AI agent hỗ trợ lập trình, chứ không phải phép màu tự làm mọi thứ.",
            on_screen_text="AI hỗ trợ, con người chịu trách nhiệm",
            duration_hint=6,
        ),
    ]
    return VideoPlan(
        topic=topic,
        hook=scenes[0].narration,
        scenes=scenes,
        caption=f"{topic} — giải thích ngắn gọn, dễ hiểu.",
        hashtags=["#congnghe", "#laptrinh", "#AI", "#TechFlowVN"],
        source_notes=["Mẫu offline. Cần bổ sung nguồn chính thức trước khi đăng."],
    )


def generate_plan(topic: str) -> VideoPlan:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key or OpenAI is None:
        logging.warning("Không có API key hoặc OpenAI SDK; dùng kịch bản mẫu.")
        return mock_plan(topic)

    client = OpenAI(api_key=api_key)

    prompt = f"""
Bạn là biên tập viên video ngắn tiếng Việt cho kênh {CHANNEL_NAME}.
Hãy tạo video 40-55 giây về chủ đề: {topic}

Yêu cầu:
- Chính xác, dễ hiểu, không giật tít sai.
- Tối đa {MAX_SCENES} cảnh.
- Mỗi cảnh có title, narration, on_screen_text, duration_hint.
- Narration tự nhiên, câu ngắn.
- Không bịa số liệu hoặc sự kiện.
- source_notes phải ghi những thông tin nào cần kiểm chứng bằng nguồn chính thức.
- Trả về JSON thuần, không markdown.

Schema:
{{
  "topic": "...",
  "hook": "...",
  "scenes": [
    {{
      "title": "...",
      "narration": "...",
      "on_screen_text": "...",
      "duration_hint": 5
    }}
  ],
  "caption": "...",
  "hashtags": ["#..."],
  "source_notes": ["..."]
}}
"""

    response = client.responses.create(
        model=OPENAI_MODEL,
        input=prompt,
    )

    raw = response.output_text.strip()
    raw = re.sub(r"^```json\s*|\s*```$", "", raw, flags=re.IGNORECASE)
    data = json.loads(raw)

    scenes = [
        Scene(
            title=str(item["title"]),
            narration=str(item["narration"]),
            on_screen_text=str(item["on_screen_text"]),
            duration_hint=float(item.get("duration_hint", 5)),
        )
        for item in data["scenes"][:MAX_SCENES]
    ]

    if not scenes:
        raise ValueError("Model không trả về cảnh nào.")

    return VideoPlan(
        topic=str(data.get("topic", topic)),
        hook=str(data.get("hook", scenes[0].narration)),
        scenes=scenes,
        caption=str(data.get("caption", "")),
        hashtags=[str(x) for x in data.get("hashtags", [])],
        source_notes=[str(x) for x in data.get("source_notes", [])],
    )


def find_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
        if bold
        else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size=size)
    return ImageFont.load_default()


def wrap_text(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont, max_width: int) -> list[str]:
    words = text.split()
    lines: list[str] = []
    current = ""
    for word in words:
        test = f"{current} {word}".strip()
        box = draw.textbbox((0, 0), test, font=font)
        if box[2] - box[0] <= max_width:
            current = test
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_centered_lines(
    draw: ImageDraw.ImageDraw,
    lines: list[str],
    font: ImageFont.ImageFont,
    y: int,
    spacing: int,
    fill: str,
) -> int:
    for line in lines:
        box = draw.textbbox((0, 0), line, font=font)
        x = (WIDTH - (box[2] - box[0])) // 2
        draw.text((x, y), line, font=font, fill=fill)
        y += (box[3] - box[1]) + spacing
    return y


def create_scene_image(scene: Scene, index: int, total: int, output: Path) -> None:
    image = Image.new("RGB", (WIDTH, HEIGHT), "#0B1020")
    draw = ImageDraw.Draw(image)

    title_font = find_font(58, bold=True)
    body_font = find_font(82, bold=True)
    small_font = find_font(38)
    brand_font = find_font(34, bold=True)

    draw.rounded_rectangle((70, 95, 1010, 230), radius=42, fill="#151D38")
    draw.text((110, 135), scene.title.upper(), font=title_font, fill="#8EDBFF")

    # Decorative tech grid
    for x in range(80, WIDTH, 140):
        draw.line((x, 300, x, 1550), fill="#182344", width=2)
    for y in range(330, 1550, 140):
        draw.line((80, y, 1000, y), fill="#182344", width=2)

    lines = wrap_text(draw, scene.on_screen_text, body_font, 860)
    total_height = sum(
        draw.textbbox((0, 0), line, font=body_font)[3] -
        draw.textbbox((0, 0), line, font=body_font)[1] + 28
        for line in lines
    )
    start_y = max(480, (HEIGHT - total_height) // 2 - 80)
    draw_centered_lines(draw, lines, body_font, start_y, 28, "#FFFFFF")

    progress_w = int(880 * ((index + 1) / total))
    draw.rounded_rectangle((100, 1650, 980, 1668), radius=9, fill="#293354")
    draw.rounded_rectangle((100, 1650, 100 + progress_w, 1668), radius=9, fill="#5DD6FF")

    draw.text((100, 1740), CHANNEL_NAME, font=brand_font, fill="#8EDBFF")
    counter = f"{index + 1}/{total}"
    counter_box = draw.textbbox((0, 0), counter, font=small_font)
    draw.text((980 - (counter_box[2] - counter_box[0]), 1740), counter, font=small_font, fill="#AAB4D3")

    image.save(output, quality=95)


def synthesize_voice(text: str, output_wav: Path) -> None:
    engine = pyttsx3.init()
    engine.setProperty("rate", VOICE_RATE)

    voices = engine.getProperty("voices")
    vietnamese = [
        v for v in voices
        if "vi" in (getattr(v, "id", "") + getattr(v, "name", "")).lower()
        or "vietnam" in (getattr(v, "name", "")).lower()
    ]
    if vietnamese:
        engine.setProperty("voice", vietnamese[0].id)

    engine.save_to_file(text, str(output_wav))
    engine.runAndWait()

    if not output_wav.exists() or output_wav.stat().st_size == 0:
        raise RuntimeError("Không tạo được voice. Hãy kiểm tra Windows Speech voices.")


def probe_duration(media: Path) -> float:
    result = subprocess.run(
        [
            "ffprobe", "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            str(media),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return float(result.stdout.strip())


def seconds_to_srt(value: float) -> str:
    ms = int(round(value * 1000))
    hours, ms = divmod(ms, 3_600_000)
    minutes, ms = divmod(ms, 60_000)
    seconds, ms = divmod(ms, 1000)
    return f"{hours:02}:{minutes:02}:{seconds:02},{ms:03}"


def create_srt(plan: VideoPlan, total_duration: float, output: Path) -> None:
    narration_lengths = [max(1, len(scene.narration)) for scene in plan.scenes]
    total_chars = sum(narration_lengths)
    cursor = 0.0
    chunks: list[str] = []

    for index, (scene, length) in enumerate(zip(plan.scenes, narration_lengths), start=1):
        duration = total_duration * length / total_chars
        end = min(total_duration, cursor + duration)
        chunks.append(
            f"{index}\n"
            f"{seconds_to_srt(cursor)} --> {seconds_to_srt(end)}\n"
            f"{scene.narration}\n"
        )
        cursor = end

    output.write_text("\n".join(chunks), encoding="utf-8")


def create_video(plan: VideoPlan, job_dir: Path) -> Path:
    ensure_tools()

    scenes_dir = job_dir / "scenes"
    scenes_dir.mkdir(parents=True, exist_ok=True)

    for i, scene in enumerate(plan.scenes):
        create_scene_image(
            scene,
            i,
            len(plan.scenes),
            scenes_dir / f"scene_{i:02}.png",
        )

    narration = " ".join(scene.narration for scene in plan.scenes)
    narration_txt = job_dir / "narration.txt"
    narration_txt.write_text(narration, encoding="utf-8")

    voice_path = job_dir / "narration.wav"
    synthesize_voice(narration, voice_path)
    duration = probe_duration(voice_path)

    scene_weights = [max(1, len(scene.narration)) for scene in plan.scenes]
    total_weight = sum(scene_weights)
    scene_durations = [duration * weight / total_weight for weight in scene_weights]

    concat_file = job_dir / "slides.txt"
    concat_lines: list[str] = []
    for i, scene_duration in enumerate(scene_durations):
        image_path = (scenes_dir / f"scene_{i:02}.png").resolve()
        concat_lines.append(f"file '{image_path.as_posix()}'")
        concat_lines.append(f"duration {scene_duration:.3f}")
    concat_lines.append(f"file '{(scenes_dir / f'scene_{len(plan.scenes)-1:02}.png').resolve().as_posix()}'")
    concat_file.write_text("\n".join(concat_lines), encoding="utf-8")

    srt_path = job_dir / "subtitles.srt"
    create_srt(plan, duration, srt_path)

    silent_video = job_dir / "silent.mp4"
    final_video = job_dir / "final.mp4"

    subprocess.run(
        [
            "ffmpeg", "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", str(concat_file),
            "-vf", f"fps={FPS},format=yuv420p",
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            str(silent_video),
        ],
        check=True,
    )

    subprocess.run(
        [
            "ffmpeg", "-y",
            "-i", str(silent_video),
            "-i", str(voice_path),
            "-c:v", "copy",
            "-c:a", "aac",
            "-b:a", "192k",
            "-shortest",
            "-movflags", "+faststart",
            str(final_video),
        ],
        check=True,
    )

    silent_video.unlink(missing_ok=True)
    return final_video


def save_plan(plan: VideoPlan, job_dir: Path) -> None:
    payload: dict[str, Any] = asdict(plan)
    (job_dir / "script.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    metadata = {
        "title": plan.topic,
        "caption": plan.caption,
        "hashtags": plan.hashtags,
        "full_caption": f"{plan.caption}\n\n{' '.join(plan.hashtags)}".strip(),
        "source_notes": plan.source_notes,
        "status": "DRAFT_REQUIRES_REVIEW",
        "created_at": datetime.now().isoformat(),
    }
    (job_dir / "metadata.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def run(topic: str) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    job_dir = OUTPUT_DIR / f"{timestamp}_{slugify(topic)}"
    job_dir.mkdir(parents=True, exist_ok=False)

    logging.info("Đang tạo kịch bản: %s", topic)
    plan = generate_plan(topic)
    save_plan(plan, job_dir)

    logging.info("Đang tạo video...")
    final_video = create_video(plan, job_dir)

    logging.info("Hoàn tất: %s", final_video.resolve())
    return final_video


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="AI TechFlow video generator")
    parser.add_argument(
        "--topic",
        required=True,
        help="Chủ đề video, ví dụ: Docker là gì?",
    )
    return parser.parse_args()


if __name__ == "__main__":
    try:
        args = parse_args()
        result = run(args.topic)
        print(f"\nVIDEO_READY={result.resolve()}")
    except Exception as exc:
        logging.exception("Pipeline thất bại: %s", exc)
        sys.exit(1)
