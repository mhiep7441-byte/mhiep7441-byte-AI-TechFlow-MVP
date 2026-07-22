from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import re
import shutil
import subprocess
import sys
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path

WIDTH, HEIGHT, FPS = 1080, 1920, 24
FFMPEG_THREADS = max(1, int(os.getenv("FFMPEG_THREADS", "1")))
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "/tmp/techflow-outputs"))
CHANNEL_NAME = os.getenv("CHANNEL_NAME", "TechFlow VN")
VOICE = os.getenv("TTS_VOICE", "vi-VN-HoaiMyNeural")
logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")


@dataclass
class Scene:
    title: str
    narration: str
    on_screen_text: str


@dataclass
class VideoPlan:
    topic: str
    scenes: list[Scene]
    caption: str
    hashtags: list[str]


def slugify(value: str) -> str:
    cleaned = re.sub(r"[^\w\s-]", "", value.lower(), flags=re.UNICODE)
    return re.sub(r"[-\s]+", "-", cleaned).strip("-")[:60] or "video"


def fallback_plan(topic: str) -> VideoPlan:
    return VideoPlan(
        topic,
        [
            Scene(
                "MỞ ĐẦU",
                f"Bạn đang tìm hiểu về {topic}? Đây là phần tóm tắt dễ hiểu trong chưa đầy một phút.",
                topic,
            ),
            Scene(
                "VÌ SAO QUAN TRỌNG",
                "Công nghệ này giúp giảm thao tác lặp lại và dành nhiều thời gian hơn cho việc giải quyết vấn đề.",
                "Giảm việc lặp lại\nTập trung vào giá trị",
            ),
            Scene(
                "CÁCH HOẠT ĐỘNG",
                "Quy trình tốt bắt đầu từ yêu cầu rõ ràng, xử lý dữ liệu, sau đó kiểm tra kết quả trước khi sử dụng.",
                "Yêu cầu → Xử lý → Kiểm tra",
            ),
            Scene(
                "LƯU Ý",
                "Không nên tin kết quả tuyệt đối. Hãy kiểm tra nguồn, bảo vệ dữ liệu và giữ bước duyệt của con người.",
                "Kiểm chứng nguồn\nBảo vệ dữ liệu\nCon người duyệt",
            ),
            Scene(
                "KẾT LUẬN",
                f"Đó là cách tiếp cận {topic} an toàn và hiệu quả. Theo dõi TechFlow để xem thêm.",
                "AI hỗ trợ\nCon người chịu trách nhiệm",
            ),
        ],
        f"{topic} — giải thích ngắn gọn.",
        ["#congnghe", "#laptrinh", "#AI", "#TechFlowVN"],
    )


def generate_plan(topic: str) -> VideoPlan:
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key:
        return fallback_plan(topic)

    try:
        from openai import OpenAI
    except ImportError:
        logging.warning("Thiếu thư viện OpenAI, chuyển sang kịch bản mẫu.")
        return fallback_plan(topic)

    prompt = f'''Viết kịch bản video công nghệ tiếng Việt 40-55 giây về: {topic}.
Trả JSON thuần: {{"topic":"...","scenes":[{{"title":"...","narration":"...","on_screen_text":"..."}}],"caption":"...","hashtags":["#..."]}}.
Tối đa 6 cảnh, câu ngắn, chính xác, không bịa số liệu.'''
    response = OpenAI(api_key=key).responses.create(
        model=os.getenv("OPENAI_MODEL", "gpt-5-mini"),
        input=prompt,
    )
    raw_json = re.sub(r"^```json\s*|\s*```$", "", response.output_text.strip(), flags=re.I)
    data = json.loads(raw_json)
    scenes = [
        Scene(str(item["title"]), str(item["narration"]), str(item["on_screen_text"]))
        for item in data["scenes"][:6]
    ]
    return VideoPlan(
        str(data.get("topic", topic)),
        scenes,
        str(data.get("caption", "")),
        [str(item) for item in data.get("hashtags", [])],
    )


@lru_cache(maxsize=8)
def selected_font(size: int, bold: bool = False):
    from PIL import ImageFont

    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{name}", size)


def wrap(draw, value: str, font, max_width: int) -> list[str]:
    lines: list[str] = []
    for paragraph in value.splitlines():
        current = ""
        for word in paragraph.split():
            candidate = f"{current} {word}".strip()
            if draw.textbbox((0, 0), candidate, font=font)[2] <= max_width:
                current = candidate
            else:
                if current:
                    lines.append(current)
                current = word
        if current:
            lines.append(current)
    return lines


def draw_scene(scene: Scene, index: int, count: int, output: Path) -> None:
    from PIL import Image, ImageDraw

    image = Image.new("RGB", (WIDTH, HEIGHT), "#070B14")
    try:
        draw = ImageDraw.Draw(image)
        draw.rounded_rectangle((65, 80, 1015, 235), radius=38, fill="#14233A", outline="#2E83B7", width=3)
        draw.text((105, 128), scene.title.upper(), font=selected_font(54, True), fill="#67D7FF")
        for x in range(80, WIDTH, 140):
            draw.line((x, 310, x, 1560), fill="#122039", width=2)
        for y in range(330, 1560, 140):
            draw.line((80, y, 1000, y), fill="#122039", width=2)

        body = selected_font(76, True)
        lines = wrap(draw, scene.on_screen_text, body, 850)
        y = max(470, 880 - len(lines) * 58)
        for line in lines:
            box = draw.textbbox((0, 0), line, font=body)
            draw.text(((WIDTH - box[2]) / 2, y), line, font=body, fill="#F5FAFF")
            y += 110

        progress = int(860 * (index + 1) / count)
        draw.rounded_rectangle((110, 1640, 970, 1660), radius=10, fill="#253451")
        draw.rounded_rectangle((110, 1640, 110 + progress, 1660), radius=10, fill="#4BCBFF")
        draw.text((110, 1745), CHANNEL_NAME, font=selected_font(36, True), fill="#67D7FF")
        draw.text((890, 1745), f"{index + 1}/{count}", font=selected_font(34), fill="#A4B1C7")
        image.save(output, optimize=True)
    finally:
        image.close()


async def make_voice(text: str, output: Path) -> None:
    import edge_tts

    await edge_tts.Communicate(text, VOICE, rate="+4%").save(str(output))


def media_duration(path: Path) -> float:
    result = subprocess.run(
        [
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "default=noprint_wrappers=1:nokey=1",
            str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    return float(result.stdout.strip())


def srt_time(seconds: float) -> str:
    milliseconds = int(seconds * 1000)
    hours, milliseconds = divmod(milliseconds, 3_600_000)
    minutes, milliseconds = divmod(milliseconds, 60_000)
    seconds_value, milliseconds = divmod(milliseconds, 1000)
    return f"{hours:02}:{minutes:02}:{seconds_value:02},{milliseconds:03}"


def build_ffmpeg_command(concat: Path, voice: Path, final: Path) -> list[str]:
    """Build a single-pass, low-memory FFmpeg command suitable for Render Free."""
    return [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-f",
        "concat",
        "-safe",
        "0",
        "-i",
        str(concat),
        "-i",
        str(voice),
        "-vf",
        f"fps={FPS},format=yuv420p",
        "-c:v",
        "libx264",
        "-preset",
        "ultrafast",
        "-tune",
        "stillimage",
        "-crf",
        "29",
        "-threads",
        str(FFMPEG_THREADS),
        "-x264-params",
        f"threads={FFMPEG_THREADS}:lookahead_threads=1:sliced_threads=0",
        "-c:a",
        "aac",
        "-b:a",
        "96k",
        "-shortest",
        "-movflags",
        "+faststart",
        str(final),
    ]


def create_video(plan: VideoPlan, job_dir: Path) -> Path:
    missing = [binary for binary in ("ffmpeg", "ffprobe") if not shutil.which(binary)]
    if missing:
        raise RuntimeError(f"Thiếu công cụ: {', '.join(missing)}")

    scenes_dir = job_dir / "scenes"
    scenes_dir.mkdir()
    for index, scene in enumerate(plan.scenes):
        draw_scene(scene, index, len(plan.scenes), scenes_dir / f"scene_{index:02}.png")

    voice = job_dir / "narration.mp3"
    asyncio.run(make_voice(" ".join(scene.narration for scene in plan.scenes), voice))
    total = media_duration(voice)
    weights = [max(1, len(scene.narration)) for scene in plan.scenes]
    weight_sum = sum(weights)
    cursor = 0.0
    slides: list[str] = []
    subtitles: list[str] = []

    for index, (scene, weight) in enumerate(zip(plan.scenes, weights), 1):
        seconds = total * weight / weight_sum
        image_path = (scenes_dir / f"scene_{index - 1:02}.png").resolve().as_posix()
        slides.extend([f"file '{image_path}'", f"duration {seconds:.3f}"])
        subtitles.append(
            f"{index}\n{srt_time(cursor)} --> {srt_time(cursor + seconds)}\n{scene.narration}\n"
        )
        cursor += seconds

    last_image = (scenes_dir / f"scene_{len(plan.scenes) - 1:02}.png").resolve().as_posix()
    slides.append(f"file '{last_image}'")
    concat = job_dir / "slides.txt"
    concat.write_text("\n".join(slides), encoding="utf-8")
    (job_dir / "subtitles.srt").write_text("\n".join(subtitles), encoding="utf-8")

    final = job_dir / "final.mp4"
    subprocess.run(build_ffmpeg_command(concat, voice, final), check=True)
    return final


def upload_video(video: Path, job_id: str) -> str:
    if not os.getenv("CLOUDINARY_URL", "").strip():
        raise RuntimeError("Chưa cấu hình CLOUDINARY_URL trên server.")

    import cloudinary
    import cloudinary.uploader

    cloudinary.config(secure=True)
    result = cloudinary.uploader.upload_large(
        str(video),
        resource_type="video",
        public_id=f"techflow/{job_id}",
        overwrite=True,
    )
    return str(result["secure_url"])


def run(topic: str) -> str:
    job_id = f"{datetime.now(timezone.utc):%Y%m%d_%H%M%S}_{slugify(topic)}"
    job_dir = OUTPUT_DIR / job_id
    job_dir.mkdir(parents=True)
    plan = generate_plan(topic)
    (job_dir / "script.json").write_text(
        json.dumps(asdict(plan), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    video = create_video(plan, job_dir)
    return upload_video(video, job_id)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--topic", required=True)
    args = parser.parse_args()
    try:
        print(f"VIDEO_READY={run(args.topic)}", flush=True)
    except Exception as exc:
        logging.exception("Tạo video thất bại: %s", exc)
        sys.exit(1)
