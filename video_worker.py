from __future__ import annotations

import argparse
import asyncio
import base64
import hashlib
import json
import logging
import os
import re
import shutil
import subprocess
import sys
import urllib.request
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from typing import Any

from research_agent import ResearchBrief, research_topic

WIDTH = max(360, int(os.getenv("VIDEO_WIDTH", "540")))
HEIGHT = max(640, int(os.getenv("VIDEO_HEIGHT", "960")))
FPS = max(8, int(os.getenv("VIDEO_FPS", "12")))
FFMPEG_THREADS = max(1, int(os.getenv("FFMPEG_THREADS", "1")))
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "/tmp/techflow-outputs"))
CHANNEL_NAME = os.getenv("CHANNEL_NAME", "TechFlow VN")
VOICE = os.getenv("TTS_VOICE", "vi-VN-HoaiMyNeural")
SCRIPT_MODEL = os.getenv("OPENAI_SCRIPT_MODEL", os.getenv("OPENAI_MODEL", "gpt-5.6-terra"))
IMAGE_MODEL = os.getenv("OPENAI_IMAGE_MODEL", "gpt-image-2")
IMAGE_QUALITY = os.getenv("OPENAI_IMAGE_QUALITY", "medium")
ENABLE_AI_IMAGES = os.getenv("ENABLE_AI_IMAGES", "true").lower() in {"1", "true", "yes", "on"}
MAX_AI_IMAGES = max(0, min(int(os.getenv("MAX_AI_IMAGES", "4")), 8))
MAX_SCENES = max(4, min(int(os.getenv("MAX_SCENES", "6")), 8))
logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")
LOGGER = logging.getLogger(__name__)


@dataclass
class Scene:
    title: str
    narration: str
    on_screen_text: str
    visual_prompt: str = ""
    character_action: str = ""
    camera_motion: str = "slow push in"
    source_ids: list[str] = field(default_factory=list)
    duration_hint: float = 6.0


@dataclass
class VideoPlan:
    topic: str
    scenes: list[Scene]
    caption: str
    hashtags: list[str]
    hook: str = ""
    character: str = "Linh, nữ kỹ sư AI người Việt trẻ, tóc đen ngắn, áo khoác tím than"
    visual_style: str = "cinematic editorial technology, lilac and cobalt, realistic light"


@dataclass
class FactCheck:
    approved: bool
    issues: list[str]
    checked_at: str
    mode: str


def slugify(value: str) -> str:
    cleaned = re.sub(r"[^\w\s-]", "", value.lower(), flags=re.UNICODE)
    return re.sub(r"[-\s]+", "-", cleaned).strip("-")[:60] or "video"


def fallback_plan(topic: str) -> VideoPlan:
    character = "Linh, nữ kỹ sư AI người Việt trẻ, tóc đen ngắn, áo khoác tím than"
    return VideoPlan(
        topic=topic,
        hook=f"{topic} thực sự thay đổi cách chúng ta làm việc như thế nào?",
        character=character,
        visual_style="cinematic editorial illustration, deep navy, electric lilac, warm skin tones",
        scenes=[
            Scene(
                "MỞ ĐẦU",
                f"{topic} nghe có vẻ phức tạp. Linh sẽ tóm tắt phần cốt lõi trong chưa đầy một phút.",
                topic,
                "Vietnamese AI engineer enters a luminous technology studio, confident eye contact",
                "bước vào studio và nhìn thẳng máy quay",
                "fast dolly in",
            ),
            Scene(
                "VẤN ĐỀ",
                "Khi công việc lặp lại quá nhiều, chúng ta mất thời gian cho thao tác thay vì giải quyết vấn đề thật.",
                "Bớt thao tác lặp lại\nTập trung vào giá trị",
                "engineer surrounded by floating repetitive task cards and code windows",
                "gạt các thẻ công việc lặp lại sang một bên",
                "slow orbit",
            ),
            Scene(
                "CÁCH HOẠT ĐỘNG",
                "Một quy trình tốt đi từ yêu cầu rõ ràng, qua xử lý có kiểm soát, rồi kiểm tra kết quả trước khi sử dụng.",
                "Yêu cầu → Xử lý → Kiểm tra",
                "three-stage holographic workflow in a professional software lab",
                "chỉ vào ba bước trên bảng hologram",
                "left to right pan",
            ),
            Scene(
                "KIỂM CHỨNG",
                "Đừng tin mọi kết quả ngay lập tức. Hãy đối chiếu nguồn, ngày công bố và giới hạn của công nghệ.",
                "Nguồn chính thức\nNgày công bố\nGiới hạn",
                "engineer compares verified official documents on a large transparent display",
                "đánh dấu các nguồn đã được xác minh",
                "subtle push in",
            ),
            Scene(
                "KẾT LUẬN",
                f"Đó là cách tiếp cận {topic} có trách nhiệm. Theo dõi TechFlow để xem bản phân tích tiếp theo.",
                "Hiểu đúng • Dùng đúng • Kiểm tra",
                "hero shot of the engineer in a modern Vietnamese technology studio",
                "mỉm cười và đóng bảng phân tích",
                "hero pull back",
            ),
        ],
        caption=f"{topic} — giải thích ngắn gọn, có kiểm chứng.",
        hashtags=["#congnghe", "#laptrinh", "#AI", "#TechFlowVN"],
    )


def _extract_json(value: str) -> dict[str, Any]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", value.strip(), flags=re.I)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("Model không trả về JSON hợp lệ")
    return json.loads(cleaned[start : end + 1])


def generate_plan(topic: str, research: ResearchBrief | None = None) -> VideoPlan:
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key:
        return fallback_plan(topic)

    try:
        from openai import OpenAI
    except ImportError:
        LOGGER.warning("Thiếu OpenAI SDK, chuyển sang storyboard offline.")
        return fallback_plan(topic)

    brief = research or research_topic(topic)
    evidence = json.dumps(brief.to_dict(), ensure_ascii=False)
    prompt = f"""
Bạn là Creative Director và biên tập viên video dọc cho kênh {CHANNEL_NAME}.
Tạo storyboard video công nghệ tiếng Việt 45-65 giây về: {topic}

Research brief (chỉ dùng dữ kiện có trong đây):
{evidence}

Yêu cầu:
- {MAX_SCENES} cảnh, hook mạnh trong 2 giây đầu; nhịp nhanh nhưng không giật tít sai.
- Có một nhân vật người Việt nhất quán xuyên suốt; mô tả ngoại hình cụ thể trong trường character.
- Mỗi cảnh có bối cảnh, hành động nhân vật, chuyển động camera và visual_prompt đủ chi tiết để tạo ảnh dọc 9:16.
- Mỗi khẳng định thực tế phải gắn source_ids hợp lệ. Nếu research offline, nói rõ đây là bản minh họa khái niệm.
- Câu đọc tự nhiên, ngắn; on_screen_text tối đa 12 từ; không sao chép nguyên văn nguồn.
- Caption không quá 350 ký tự; 4-7 hashtag liên quan, không spam.
- Trả JSON thuần, không markdown.

{{
  "topic":"...", "hook":"...",
  "character":"mô tả nhân vật nhất quán",
  "visual_style":"phong cách điện ảnh nhất quán",
  "scenes":[{{
    "title":"...", "narration":"...", "on_screen_text":"...",
    "visual_prompt":"...", "character_action":"...", "camera_motion":"...",
    "source_ids":["S1"], "duration_hint":6
  }}],
  "caption":"...", "hashtags":["#..."]
}}
""".strip()
    try:
        response = OpenAI(api_key=key).responses.create(model=SCRIPT_MODEL, input=prompt)
        data = _extract_json(response.output_text)
        scenes = [
            Scene(
                title=str(item.get("title") or f"CẢNH {index + 1}")[:80],
                narration=str(item.get("narration") or "").strip()[:900],
                on_screen_text=str(item.get("on_screen_text") or "").strip()[:180],
                visual_prompt=str(item.get("visual_prompt") or "").strip()[:1000],
                character_action=str(item.get("character_action") or "").strip()[:400],
                camera_motion=str(item.get("camera_motion") or "slow push in").strip()[:100],
                source_ids=[str(value)[:20] for value in item.get("source_ids", [])][:5],
                duration_hint=max(3.5, min(float(item.get("duration_hint", 6)), 12.0)),
            )
            for index, item in enumerate(data.get("scenes", [])[:MAX_SCENES])
            if isinstance(item, dict) and str(item.get("narration", "")).strip()
        ]
        if len(scenes) < 4:
            raise ValueError("Storyboard cần ít nhất bốn cảnh")
        return VideoPlan(
            topic=str(data.get("topic") or topic)[:500],
            hook=str(data.get("hook") or scenes[0].narration)[:500],
            character=str(data.get("character") or fallback_plan(topic).character)[:700],
            visual_style=str(data.get("visual_style") or fallback_plan(topic).visual_style)[:700],
            scenes=scenes,
            caption=str(data.get("caption") or topic).strip()[:350],
            hashtags=[str(value)[:60] for value in data.get("hashtags", [])][:7],
        )
    except Exception as exc:
        LOGGER.exception("Script Agent thất bại, dùng storyboard offline: %s", exc)
        return fallback_plan(topic)


def fact_check_plan(plan: VideoPlan, research: ResearchBrief) -> FactCheck:
    known_sources = {source.id for source in research.sources}
    issues: list[str] = []
    for index, scene in enumerate(plan.scenes, 1):
        unknown = [source_id for source_id in scene.source_ids if source_id not in known_sources]
        if unknown:
            issues.append(f"Cảnh {index} tham chiếu nguồn không tồn tại: {', '.join(unknown)}")
        has_specific_number = bool(re.search(r"\b\d+(?:[.,]\d+)?%?\b", scene.narration))
        if has_specific_number and not scene.source_ids:
            issues.append(f"Cảnh {index} có số liệu nhưng chưa gắn nguồn")
    if research.mode != "offline" and len(research.sources) < 2:
        issues.append("Research chưa có đủ hai nguồn độc lập")
    if research.mode == "offline":
        issues.append("Bản offline chưa có nguồn web; bắt buộc duyệt trước khi đăng")
    return FactCheck(
        approved=not issues and research.mode != "offline",
        issues=issues,
        checked_at=datetime.now(timezone.utc).isoformat(),
        mode="deterministic-evidence-check",
    )


@lru_cache(maxsize=16)
def selected_font(size: int, bold: bool = False):
    from PIL import ImageFont

    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf",
    ]
    for candidate in candidates:
        try:
            return ImageFont.truetype(candidate, size)
        except OSError:
            continue
    return ImageFont.load_default()


def wrap(draw, value: str, font, max_width: int, max_lines: int = 5) -> list[str]:
    lines: list[str] = []
    for paragraph in value.splitlines() or [value]:
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
    return lines[:max_lines]


def scale_x(value: int) -> int:
    return round(value * WIDTH / 1080)


def scale_y(value: int) -> int:
    return round(value * HEIGHT / 1920)


def scale_font(value: int) -> int:
    return max(12, round(value * min(WIDTH / 1080, HEIGHT / 1920)))


def _palette(seed: str) -> tuple[str, str, str]:
    palettes = [
        ("#090B14", "#806BFF", "#FFCA78"),
        ("#07172A", "#21C5D9", "#F05D8A"),
        ("#160D24", "#B978FF", "#55D6BE"),
        ("#10131D", "#FF8C69", "#7BA6FF"),
    ]
    return palettes[int(hashlib.sha256(seed.encode("utf-8")).hexdigest()[:4], 16) % len(palettes)]


def _offline_visual(scene: Scene, index: int, output: Path) -> None:
    from PIL import Image, ImageDraw

    background, accent, warm = _palette(f"{scene.title}:{index}")
    image = Image.new("RGB", (WIDTH, HEIGHT), background)
    draw = ImageDraw.Draw(image)
    for step in range(12, 0, -1):
        margin = scale_x(35 * step)
        draw.ellipse(
            (WIDTH // 2 - margin, HEIGHT // 3 - margin, WIDTH // 2 + margin, HEIGHT // 3 + margin),
            fill=accent if step % 2 else background,
        )
    # A stylised recurring human presenter keeps offline mode visually coherent.
    head_x, head_y = scale_x(680), scale_y(650)
    draw.ellipse((head_x, head_y, head_x + scale_x(230), head_y + scale_y(230)), fill="#F2B99B")
    draw.pieslice(
        (head_x - scale_x(25), head_y - scale_y(35), head_x + scale_x(250), head_y + scale_y(180)),
        180,
        355,
        fill="#16131C",
    )
    draw.rounded_rectangle(
        (head_x - scale_x(80), head_y + scale_y(210), head_x + scale_x(310), head_y + scale_y(720)),
        radius=scale_x(80),
        fill=accent,
    )
    draw.line(
        (head_x - scale_x(30), head_y + scale_y(370), scale_x(370), scale_y(920)),
        fill="#F2B99B",
        width=max(4, scale_x(30)),
    )
    draw.rounded_rectangle(
        (scale_x(90), scale_y(410), scale_x(570), scale_y(980)),
        radius=scale_x(35),
        fill="#111827",
        outline=warm,
        width=max(2, scale_x(7)),
    )
    for row in range(5):
        y = scale_y(505 + row * 82)
        draw.rounded_rectangle((scale_x(155), y, scale_x(500 - row * 25), y + scale_y(20)), radius=8, fill=warm)
    image.save(output, quality=92)
    image.close()


def _save_generated_image(result: Any, output: Path) -> bool:
    item = result.data[0]
    encoded = getattr(item, "b64_json", None)
    if encoded:
        output.write_bytes(base64.b64decode(encoded))
        return True
    url = getattr(item, "url", None)
    if url and str(url).startswith("https://"):
        request = urllib.request.Request(str(url), headers={"User-Agent": "AI-TechFlow/1.0"})
        with urllib.request.urlopen(request, timeout=45) as response:
            output.write_bytes(response.read(15_000_000))
        return True
    return False


def generate_scene_visuals(plan: VideoPlan, output_dir: Path) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    client = None
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if key and ENABLE_AI_IMAGES and MAX_AI_IMAGES:
        try:
            from openai import OpenAI

            client = OpenAI(api_key=key)
        except ImportError:
            LOGGER.warning("Thiếu OpenAI SDK; dùng minh họa vector.")

    paths: list[Path] = []
    for index, scene in enumerate(plan.scenes):
        raw = output_dir / f"raw_{index:02}.png"
        generated = False
        if client is not None and index < MAX_AI_IMAGES:
            prompt = (
                f"Vertical 9:16 cinematic editorial scene for a Vietnamese technology video. "
                f"Recurring character: {plan.character}. Consistent wardrobe and face in every scene. "
                f"Visual direction: {plan.visual_style}. Scene: {scene.visual_prompt}. "
                f"Character action: {scene.character_action}. Professional lighting, expressive composition, "
                "realistic hands, no text, no logos, no watermark, leave negative space for captions."
            )
            try:
                generated = _save_generated_image(
                    client.images.generate(
                        model=IMAGE_MODEL,
                        prompt=prompt,
                        size="1024x1536",
                        quality=IMAGE_QUALITY,
                    ),
                    raw,
                )
            except Exception as exc:
                LOGGER.warning("Không tạo được ảnh AI cho cảnh %s: %s", index + 1, exc)
        if not generated:
            _offline_visual(scene, index, raw)
        composed = output_dir / f"scene_{index:02}.jpg"
        compose_scene(raw, scene, index, len(plan.scenes), composed)
        raw.unlink(missing_ok=True)
        paths.append(composed)
    return paths


def compose_scene(raw: Path, scene: Scene, index: int, count: int, output: Path) -> None:
    from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageOps

    with Image.open(raw) as source:
        image = ImageOps.fit(source.convert("RGB"), (WIDTH, HEIGHT), method=Image.Resampling.LANCZOS)
    image = ImageEnhance.Color(image).enhance(0.92)
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    for y in range(HEIGHT):
        alpha = int(35 + 170 * (y / HEIGHT) ** 2)
        draw.line((0, y, WIDTH, y), fill=(5, 6, 14, min(alpha, 220)))
    draw.rounded_rectangle(
        (scale_x(55), scale_y(65), WIDTH - scale_x(55), scale_y(205)),
        radius=scale_x(30),
        fill=(7, 9, 18, 205),
        outline=(176, 147, 255, 220),
        width=max(2, scale_x(4)),
    )
    draw.text(
        (scale_x(92), scale_y(112)),
        f"{index + 1:02}  {scene.title.upper()}",
        font=selected_font(scale_font(47), True),
        fill="#E9E4FF",
    )
    card_top = scale_y(1280)
    draw.rounded_rectangle(
        (scale_x(55), card_top, WIDTH - scale_x(55), scale_y(1740)),
        radius=scale_x(36),
        fill=(7, 9, 18, 220),
    )
    font = selected_font(scale_font(67), True)
    lines = wrap(draw, scene.on_screen_text, font, WIDTH - scale_x(175), 4)
    y = card_top + scale_y(65)
    for line in lines:
        draw.text((scale_x(92), y), line, font=font, fill="#FFFFFF", stroke_width=1, stroke_fill="#05060A")
        y += scale_y(95)
    source_label = " • ".join(scene.source_ids) if scene.source_ids else "BẢN MINH HỌA"
    draw.text((scale_x(92), scale_y(1800)), CHANNEL_NAME, font=selected_font(scale_font(34), True), fill="#B093FF")
    draw.text((scale_x(92), scale_y(1850)), source_label, font=selected_font(scale_font(23)), fill="#D6D1E4")
    progress_width = round((WIDTH - scale_x(110)) * (index + 1) / count)
    draw.rounded_rectangle((scale_x(55), scale_y(1888), WIDTH - scale_x(55), scale_y(1906)), radius=8, fill="#343044")
    draw.rounded_rectangle((scale_x(55), scale_y(1888), scale_x(55) + progress_width, scale_y(1906)), radius=8, fill="#B093FF")
    image = Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")
    image.filter(ImageFilter.UnsharpMask(radius=1.2, percent=110, threshold=3)).save(output, quality=90, optimize=True)
    image.close()


async def make_voice(text: str, output: Path) -> None:
    import edge_tts

    await edge_tts.Communicate(text, VOICE, rate="+6%", pitch="+1Hz").save(str(output))


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
    """Compatibility command for the static low-memory renderer and unit tests."""
    return [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "concat", "-safe", "0", "-i", str(concat), "-i", str(voice),
        "-vf", f"fps={FPS},format=yuv420p", "-c:v", "libx264", "-preset", "ultrafast",
        "-tune", "stillimage", "-crf", "29", "-threads", str(FFMPEG_THREADS),
        "-x264-params", f"threads={FFMPEG_THREADS}:lookahead_threads=1:sliced_threads=0",
        "-c:a", "aac", "-b:a", "96k", "-shortest", "-movflags", "+faststart", str(final),
    ]


def build_motion_ffmpeg_command(
    images: list[Path], durations: list[float], voice: Path, final: Path
) -> list[str]:
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    for image in images:
        command.extend(["-i", str(image)])
    voice_index = len(images)
    command.extend(["-i", str(voice)])
    filters: list[str] = []
    labels: list[str] = []
    for index, duration in enumerate(durations):
        frames = max(1, round(duration * FPS))
        fade_out = max(0.1, duration - 0.32)
        direction_x = "iw/2-(iw/zoom/2)" if index % 2 == 0 else "iw-iw/zoom"
        filters.append(
            f"[{index}:v]zoompan=z='min(zoom+0.0009,1.075)':x='{direction_x}':"
            f"y='ih/2-(ih/zoom/2)':d={frames}:s={WIDTH}x{HEIGHT}:fps={FPS},"
            f"fade=t=in:st=0:d=0.22,fade=t=out:st={fade_out:.3f}:d=0.28,setsar=1[v{index}]"
        )
        labels.append(f"[v{index}]")
    filters.append(f"{''.join(labels)}concat=n={len(images)}:v=1:a=0,format=yuv420p[vout]")
    command.extend(
        [
            "-filter_complex", ";".join(filters),
            "-map", "[vout]", "-map", f"{voice_index}:a:0",
            "-c:v", "libx264", "-preset", "ultrafast", "-crf", "27",
            "-threads", str(FFMPEG_THREADS),
            "-x264-params", f"threads={FFMPEG_THREADS}:lookahead_threads=1:sliced_threads=0",
            "-c:a", "aac", "-b:a", "112k", "-shortest", "-movflags", "+faststart", str(final),
        ]
    )
    return command


def create_video(plan: VideoPlan, job_dir: Path) -> tuple[Path, float]:
    missing = [binary for binary in ("ffmpeg", "ffprobe") if not shutil.which(binary)]
    if missing:
        raise RuntimeError(f"Thiếu công cụ: {', '.join(missing)}")

    voice = job_dir / "narration.mp3"
    asyncio.run(make_voice(" ".join(scene.narration for scene in plan.scenes), voice))
    total = media_duration(voice)
    weights = [max(1, len(scene.narration)) for scene in plan.scenes]
    weight_sum = sum(weights)
    durations = [max(2.5, total * weight / weight_sum) for weight in weights]
    duration_scale = total / sum(durations)
    durations = [duration * duration_scale for duration in durations]
    images = generate_scene_visuals(plan, job_dir / "scenes")

    cursor = 0.0
    subtitles: list[str] = []
    for index, (scene, seconds) in enumerate(zip(plan.scenes, durations), 1):
        subtitles.append(f"{index}\n{srt_time(cursor)} --> {srt_time(cursor + seconds)}\n{scene.narration}\n")
        cursor += seconds
    (job_dir / "subtitles.srt").write_text("\n".join(subtitles), encoding="utf-8")

    final = job_dir / "final.mp4"
    subprocess.run(build_motion_ffmpeg_command(images, durations, voice, final), check=True, capture_output=True)
    return final, media_duration(final)


def quality_control(plan: VideoPlan, research: ResearchBrief, fact_check: FactCheck, duration: float) -> dict[str, Any]:
    score = 100
    issues = list(fact_check.issues)
    if not 35 <= duration <= 75:
        score -= 15
        issues.append(f"Thời lượng {duration:.1f}s nằm ngoài mục tiêu 35-75s")
    if len(plan.scenes) < 5:
        score -= 12
        issues.append("Storyboard có ít hơn năm cảnh")
    if research.mode == "offline":
        score -= 22
    elif len(research.sources) < 2:
        score -= 18
    if not any(scene.character_action for scene in plan.scenes):
        score -= 10
        issues.append("Storyboard chưa có hành động nhân vật")
    if not plan.caption or len(plan.hashtags) < 3:
        score -= 8
        issues.append("Caption hoặc hashtag chưa hoàn chỉnh")
    return {
        "score": max(0, score),
        "issues": issues,
        "ready_for_review": score >= 70,
        "duration_seconds": round(duration, 2),
        "scene_count": len(plan.scenes),
        "source_count": len(research.sources),
        "ai_images_enabled": bool(os.getenv("OPENAI_API_KEY")) and ENABLE_AI_IMAGES,
    }


def upload_video(video: Path, job_id: str) -> str:
    if not os.getenv("CLOUDINARY_URL", "").strip():
        raise RuntimeError("Chưa cấu hình CLOUDINARY_URL trên server.")
    import cloudinary
    import cloudinary.uploader

    cloudinary.config(secure=True)
    result = cloudinary.uploader.upload_large(
        str(video), resource_type="video", public_id=f"techflow/{job_id}", overwrite=True
    )
    original_url = str(result["secure_url"])
    delivery_transform = "c_scale,h_1920,w_1080,q_auto:eco"
    return original_url.replace("/video/upload/", f"/video/upload/{delivery_transform}/", 1)


def run(topic: str) -> dict[str, Any]:
    job_id = f"{datetime.now(timezone.utc):%Y%m%d_%H%M%S}_{slugify(topic)}"
    job_dir = OUTPUT_DIR / job_id
    job_dir.mkdir(parents=True)
    research = research_topic(topic)
    plan = generate_plan(topic, research)
    fact_check = fact_check_plan(plan, research)
    (job_dir / "research.json").write_text(
        json.dumps(research.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (job_dir / "script.json").write_text(
        json.dumps(asdict(plan), ensure_ascii=False, indent=2), encoding="utf-8"
    )
    video, duration = create_video(plan, job_dir)
    quality = quality_control(plan, research, fact_check, duration)
    (job_dir / "quality.json").write_text(
        json.dumps(quality, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    video_url = upload_video(video, job_id)
    return {
        "video_url": video_url,
        "caption": plan.caption,
        "hashtags": plan.hashtags,
        "research": research.to_dict(),
        "storyboard": asdict(plan),
        "fact_check": asdict(fact_check),
        "quality": quality,
        "status": "DRAFT_REQUIRES_REVIEW",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--topic", required=True)
    args = parser.parse_args()
    try:
        result = run(args.topic)
        metadata = json.dumps(result, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        print(f"VIDEO_METADATA_B64={base64.urlsafe_b64encode(metadata).decode('ascii')}", flush=True)
        print(f"VIDEO_READY={result['video_url']}", flush=True)
    except Exception as exc:
        LOGGER.exception("Tạo video thất bại: %s", exc)
        sys.exit(1)
