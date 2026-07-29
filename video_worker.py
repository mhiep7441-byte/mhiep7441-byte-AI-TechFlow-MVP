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
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

import urllib.request
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from typing import Any

from research_agent import ResearchBrief, research_topic
from content_guard import assess_content
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
MAX_AI_IMAGES = max(0, min(int(os.getenv("MAX_AI_IMAGES", "20")), 30))
MAX_SCENES = max(4, min(int(os.getenv("MAX_SCENES", "6")), 30))
DEFAULT_TARGET_DURATION_SECONDS = 60
MAX_TARGET_DURATION_SECONDS = 600
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
    character: str = ""
    visual_style: str = "cinematic editorial technology, lilac and cobalt, realistic light"
    provider: str = "fallback"
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS
    character_image_url: str = ""
    audio_mode: str = "narrated"
    video_provider: str = "kenburns"
    aspect_ratio: str = "9:16"
    render_quality: str = "draft"


@dataclass
class FactCheck:
    approved: bool
    issues: list[str]
    checked_at: str
    mode: str


def slugify(value: str) -> str:
    cleaned = re.sub(r"[^\w\s-]", "", value.lower(), flags=re.UNICODE)
    return re.sub(r"[-\s]+", "-", cleaned).strip("-")[:60] or "video"


def normalized_duration(value: int | str | None) -> int:
    try:
        duration = int(value or DEFAULT_TARGET_DURATION_SECONDS)
    except (TypeError, ValueError):
        duration = DEFAULT_TARGET_DURATION_SECONDS
    return min(MAX_TARGET_DURATION_SECONDS, max(30, duration))


def scene_limit(target_duration_seconds: int) -> int:
    duration = normalized_duration(target_duration_seconds)
    return min(30, max(MAX_SCENES, (duration + 9) // 10))


def apply_render_profile(aspect_ratio: str, render_quality: str) -> None:
    global WIDTH, HEIGHT
    profiles = {
        ("9:16", "draft"): (540, 960),
        ("9:16", "hd"): (720, 1280),
        ("9:16", "2k"): (1440, 2560),
        ("16:9", "draft"): (960, 540),
        ("16:9", "hd"): (1280, 720),
        ("16:9", "2k"): (2560, 1440),
    }
    WIDTH, HEIGHT = profiles.get((aspect_ratio, render_quality), profiles[("9:16", "draft")])


def fallback_plan(
    topic: str,
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
) -> VideoPlan:
    duration = normalized_duration(target_duration_seconds)
    max_s = scene_limit(duration)
    base_scenes = [
        Scene(
            "MỞ ĐẦU",
            f"{topic}. Hãy cùng khám phá câu chuyện này từ đầu.",
            topic,
            f"Cinematic opening scene introducing the world of {topic}, vibrant colors, warm lighting",
            "xuất hiện và giới thiệu chủ đề",
            "fast dolly in",
        ),
        Scene(
            "BỐI CẢNH",
            f"Để hiểu {topic}, ta cần biết bối cảnh và những yếu tố quan trọng nhất.",
            "Bối cảnh và yếu tố chính",
            f"Wide establishing shot showing the environment and context of {topic}",
            "quan sát khung cảnh xung quanh",
            "slow orbit",
        ),
        Scene(
            "DIỄN BIẾN",
            "Đây là phần thú vị nhất — khi mọi thứ bắt đầu diễn ra và thay đổi.",
            "Hành động và thay đổi",
            f"Dynamic action scene showing the key development in {topic}",
            "tham gia vào hành động chính",
            "left to right pan",
        ),
        Scene(
            "KHÁM PHÁ",
            "Mỗi chi tiết đều có ý nghĩa. Hãy nhìn kỹ hơn vào những điều ẩn giấu.",
            "Chi tiết quan trọng",
            f"Close-up detail shot revealing hidden aspects of {topic}",
            "phát hiện điều bất ngờ",
            "subtle push in",
        ),
        Scene(
            "KẾT LUẬN",
            f"Đó là câu chuyện về {topic}. Theo dõi kênh để xem tập tiếp theo.",
            "Tóm tắt • Theo dõi kênh",
            f"Hero shot with warm lighting, closing the story of {topic}",
            "mỉm cười và vẫy tay",
            "hero pull back",
        ),
    ]
    # Duplicate middle scenes to fill longer durations
    scenes = list(base_scenes)
    fillers = base_scenes[1:-1]
    while len(scenes) < max_s and fillers:
        for filler in fillers:
            if len(scenes) >= max_s:
                break
            copy = Scene(
                title=f"{filler.title} {len(scenes)}",
                narration=filler.narration,
                on_screen_text=filler.on_screen_text,
                visual_prompt=filler.visual_prompt,
                character_action=filler.character_action,
                camera_motion=filler.camera_motion,
            )
            scenes.insert(-1, copy)
    return VideoPlan(
        topic=topic,
        hook=f"{topic} — câu chuyện bạn chưa biết.",
        character="",
        visual_style="cinematic editorial illustration, vibrant colors, warm lighting, expressive composition",
        scenes=scenes,
        caption=f"{topic} — khám phá cùng {CHANNEL_NAME}.",
        hashtags=["#video", f"#{slugify(topic)}", f"#{CHANNEL_NAME.replace(' ', '')}"],
        provider="fallback",
        target_duration_seconds=duration,
    )


def _extract_json(value: str) -> dict[str, Any]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", value.strip(), flags=re.I)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("Model không trả về JSON hợp lệ")
    return json.loads(cleaned[start : end + 1])


def _generate_script_json(prompt: str) -> tuple[dict[str, Any], str]:
    provider = os.getenv("AI_PROVIDER", "auto").strip().lower()
    if provider not in {"auto", "gemini", "openai"}:
        raise ValueError("AI_PROVIDER chỉ nhận auto, gemini hoặc openai")
    if provider in {"auto", "gemini"} and os.getenv("GEMINI_API_KEY", "").strip():
        try:
            from google import genai

            client = genai.Client(api_key=os.environ["GEMINI_API_KEY"].strip())
            response = client.models.generate_content(
                model=os.getenv("GEMINI_MODEL", "gemini-2.5-flash"),
                contents=prompt,
                config={"response_mime_type": "application/json"},
            )
            return _extract_json(response.text or ""), "gemini"
        except Exception:
            if provider == "gemini":
                raise
            LOGGER.exception("Gemini Script Agent thất bại, thử OpenAI.")
    if provider in {"auto", "openai"} and os.getenv("OPENAI_API_KEY", "").strip():
        from openai import OpenAI

        response = OpenAI(api_key=os.environ["OPENAI_API_KEY"].strip()).responses.create(
            model=SCRIPT_MODEL,
            input=prompt,
        )
        return _extract_json(response.output_text), "openai"
    raise RuntimeError("Chưa cấu hình Gemini hoặc OpenAI cho Script Agent")


def generate_plan(
    topic: str,
    research: ResearchBrief | None = None,
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
    visual_style: str = "",
    character_description: str = "",
    audio_mode: str = "narrated",
    aspect_ratio: str = "9:16",
) -> VideoPlan:
    duration = normalized_duration(target_duration_seconds)
    maximum_scenes = scene_limit(duration)
    brief = research or research_topic(topic)
    evidence = json.dumps(brief.to_dict(), ensure_ascii=False)
    prompt = f"""
Bạn là Creative Director và biên tập viên video dọc cho kênh {CHANNEL_NAME}.
Tạo storyboard video tiếng Việt khoảng {duration} giây về: {topic}

Research brief (chỉ dùng dữ kiện có trong đây):
{evidence}

Định hướng hình ảnh do người dùng chọn: {visual_style or "(AI tự đề xuất)"}
Nhân vật/host do người dùng chọn: {character_description or "(AI tự đề xuất)"}
Chế độ âm thanh: {audio_mode}
Tỷ lệ khung hình: {aspect_ratio}

Yêu cầu:
- Tạo đúng {maximum_scenes} cảnh, hook mạnh trong 2 giây đầu; nhịp tự nhiên, không giật tít sai.
- Với silent_animation, narration để trống; diễn biến nằm trong character_action, visual_prompt và on_screen_text.
- Nội dung phải đủ sâu cho thời lượng mục tiêu, có mở bài, diễn tiến và kết luận; không lặp ý để kéo dài.
- Tạo một nhân vật nhất quán xuyên suốt phù hợp với chủ đề; mô tả ngoại hình cụ thể trong trường character.
- Nếu chủ đề là câu chuyện phiêu lưu, trẻ em, giáo dục, v.v., hãy sáng tạo nhân vật phù hợp (ví dụ: chú chó, robot, bé gái, siêu anh hùng...).
- Với video dài (trên 120 giây), tạo đủ cảnh để mỗi cảnh khoảng 8-12 giây, có cốt truyện rõ ràng: mở đầu, thắt nút, cao trào, kết thúc.
- Mỗi cảnh có bối cảnh, hành động nhân vật, chuyển động camera và visual_prompt đủ chi tiết cho khung {aspect_ratio}.
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
        data, provider_used = _generate_script_json(prompt)
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
            for index, item in enumerate(data.get("scenes", [])[:maximum_scenes])
            if isinstance(item, dict)
            and (audio_mode == "silent_animation" or str(item.get("narration", "")).strip())
        ]
        if len(scenes) < 4:
            raise ValueError("Storyboard cần ít nhất bốn cảnh")
        return VideoPlan(
            topic=str(data.get("topic") or topic)[:500],
            hook=str(data.get("hook") or scenes[0].narration)[:500],
            character=str(character_description or data.get("character") or fallback_plan(topic, duration).character)[:700],
            visual_style=str(visual_style or data.get("visual_style") or fallback_plan(topic, duration).visual_style)[:700],
            scenes=scenes,
            caption=str(data.get("caption") or topic).strip()[:350],
            hashtags=[str(value)[:60] for value in data.get("hashtags", [])][:7],
            provider=provider_used,
            target_duration_seconds=duration,
        )
    except Exception as exc:
        if os.getenv("AI_REQUIRED", "false").strip().lower() in {"1", "true", "yes"}:
            LOGGER.exception("Script Agent failed while AI_REQUIRED is enabled: %s", exc)
            raise
        LOGGER.exception("Script Agent thất bại, dùng storyboard offline: %s", exc)
        return fallback_plan(topic, duration)


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


def _download_reference_image(url: str, output: Path) -> Path | None:
    """Download a character reference image from Cloudinary or any HTTPS URL."""
    if not url or not url.startswith("https://"):
        return None
    try:
        request = urllib.request.Request(url, headers={"User-Agent": "AI-TechFlow/1.0"})
        with urllib.request.urlopen(request, timeout=30) as response:
            output.write_bytes(response.read(15_000_000))
        LOGGER.info("Downloaded character reference image: %s", url)
        return output
    except Exception as exc:
        LOGGER.warning("Could not download character reference: %s", exc)
        return None


def generate_character_image(
    character_description: str,
    visual_style: str = "",
    theme: str = "",
) -> str | None:
    """Generate a character reference sheet image and upload to Cloudinary.
    Returns the Cloudinary URL or None on failure."""
    openai_key = os.getenv("OPENAI_API_KEY", "").strip()
    gemini_key = os.getenv("GEMINI_API_KEY", "").strip()
    
    if not openai_key and not gemini_key:
        LOGGER.warning("No OPENAI_API_KEY or GEMINI_API_KEY; cannot generate character image")
        return None

    prompt = (
        f"Character reference sheet for animation/video production. "
        f"Character: {character_description}. "
        f"{'Theme: ' + theme + '. ' if theme else ''}"
        f"{'Visual style: ' + visual_style + '. ' if visual_style else ''}"
        f"Show the character from front view and 3/4 view side by side. "
        f"Consistent design, expressive face, clear details. "
        f"Clean white background, professional character design sheet. "
        f"No text, no watermark, high quality illustration."
    )
    try:
        tmp_dir = OUTPUT_DIR / "_character_refs"
        tmp_dir.mkdir(parents=True, exist_ok=True)
        ref_file = tmp_dir / f"char_{slugify(character_description)}.png"
        
        url = None
        if gemini_key:
            from google import genai
            from google.genai import types
            gemini_client = genai.Client(api_key=gemini_key)
            result = gemini_client.models.generate_images(
                model=os.getenv("GEMINI_IMAGE_MODEL", "gemini-2.5-flash-image"),
                prompt=prompt,
                config=types.GenerateImagesConfig(
                    number_of_images=1,
                    output_mime_type="image/png",
                    aspect_ratio="16:9"
                )
            )
            for img in result.generated_images:
                ref_file.write_bytes(img.image.image_bytes)
                url = upload_character_image(ref_file, slugify(character_description))
                ref_file.unlink(missing_ok=True)
                break
        elif openai_key:
            from openai import OpenAI
            client = OpenAI(api_key=openai_key)
            result = client.images.generate(
                model=IMAGE_MODEL,
                prompt=prompt,
                size="1536x1024",
                quality=IMAGE_QUALITY,
            )
            if _save_generated_image(result, ref_file):
                url = upload_character_image(ref_file, slugify(character_description))
                ref_file.unlink(missing_ok=True)
                
        return url
    except Exception as exc:
        LOGGER.warning("Character image generation failed: %s", exc)
    return None


def upload_character_image(image: Path, name: str) -> str:
    """Upload a character reference image to Cloudinary."""
    if not os.getenv("CLOUDINARY_URL", "").strip():
        raise RuntimeError("CLOUDINARY_URL not configured")
    import cloudinary
    import cloudinary.uploader
    cloudinary.config(secure=True)
    result = cloudinary.uploader.upload(
        str(image), resource_type="image", public_id=f"techflow/characters/{name}", overwrite=True
    )
    return str(result["secure_url"])


def generate_scene_visuals(plan: VideoPlan, output_dir: Path, character_ref: Path | None = None) -> list[Path]:
    output_dir.mkdir(parents=True, exist_ok=True)
    image_provider = os.getenv("IMAGE_PROVIDER", "auto").strip().lower()
    gemini_client = None
    openai_client = None
    if image_provider in {"auto", "gemini"} and os.getenv("GEMINI_API_KEY", "").strip() and ENABLE_AI_IMAGES:
        try:
            from google import genai

            gemini_client = genai.Client(api_key=os.environ["GEMINI_API_KEY"].strip())
        except ImportError:
            LOGGER.warning("Thiếu Google GenAI SDK; không thể tạo ảnh Gemini.")
    if gemini_client is None and image_provider in {"auto", "openai"} and os.getenv("OPENAI_API_KEY", "").strip() and ENABLE_AI_IMAGES:
        try:
            from openai import OpenAI

            openai_client = OpenAI(api_key=os.environ["OPENAI_API_KEY"].strip())
        except ImportError:
            LOGGER.warning("Thiếu OpenAI SDK; dùng minh họa vector.")

    is_16_9 = plan.aspect_ratio == "16:9"
    img_size = "1536x1024" if is_16_9 else "1024x1536"
    orientation = "Horizontal 16:9 widescreen cinematic scene" if is_16_9 else "Vertical 9:16 cinematic scene"

    paths: list[Path] = []
    for index, scene in enumerate(plan.scenes):
        raw = output_dir / f"raw_{index:02}.png"
        generated = False
        if (gemini_client is not None or openai_client is not None) and index < MAX_AI_IMAGES:
            prompt = (
                f"{orientation} for a video. "
                f"{'Recurring character: ' + plan.character + '. Consistent appearance in every scene. ' if plan.character else ''}"
                f"Visual direction: {plan.visual_style}. Scene: {scene.visual_prompt}. "
                f"Character action: {scene.character_action}. Professional lighting, expressive composition, "
                "realistic hands, no text, no logos, no watermark, leave negative space for captions."
            )
            try:
                if gemini_client is not None:
                    response = gemini_client.models.generate_content(
                        model=os.getenv("GEMINI_IMAGE_MODEL", "gemini-2.5-flash-image"),
                        contents=[prompt],
                    )
                    for part in getattr(response, "parts", []) or []:
                        inline_data = getattr(part, "inline_data", None)
                        data = getattr(inline_data, "data", None)
                        if data:
                            raw.write_bytes(data)
                            generated = True
                            break
                elif openai_client is not None:
                    generated = _save_generated_image(
                        openai_client.images.generate(
                            model=IMAGE_MODEL,
                            prompt=prompt,
                            size=img_size,
                            quality=IMAGE_QUALITY,
                        ) if not (character_ref and character_ref.exists()) else openai_client.images.edit(
                            model=IMAGE_MODEL,
                            image=open(character_ref, "rb"),
                            prompt=prompt,
                            size=img_size,
                        ),
                        raw,
                    )
            except Exception as exc:
                LOGGER.warning("Không tạo được ảnh %s cho cảnh %s: %s", image_provider, index + 1, exc)
                if os.getenv("AI_IMAGES_REQUIRED", "false").strip().lower() in {"1", "true", "yes"}:
                    raise
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
    try:
        import edge_tts
        await edge_tts.Communicate(text, VOICE, rate="+6%", pitch="+1Hz").save(str(output))
    except Exception as exc:
        LOGGER.warning("TTS failed or edge_tts not available (%s); generating silent audio track.", exc)
        subprocess.run(
            [
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
                "-t", "30", "-c:a", "libmp3lame", str(output)
            ],
            check=True
        )



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


def is_silent_animation(plan: VideoPlan) -> bool:
    return plan.audio_mode == "silent_animation" or not " ".join(scene.narration for scene in plan.scenes).strip()


def build_bgm_command(output: Path, duration: float) -> list[str]:
    return [
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "anoisesrc=color=pink:amplitude=0.018",
        "-f", "lavfi", "-i", "sine=frequency=523:sample_rate=44100",
        "-filter_complex",
        "[0:a]volume=0.32[bed];[1:a]volume=0.035,afade=t=in:st=0:d=0.6,afade=t=out:st="
        + f"{max(0.0, duration - 1.0):.3f}:d=1.0[tone];[bed][tone]amix=inputs=2:duration=first",
        "-t", f"{duration:.3f}", "-c:a", "libmp3lame", "-q:a", "6", str(output),
    ]


def make_bgm(output: Path, duration: float) -> None:
    subprocess.run(build_bgm_command(output, duration), check=True, capture_output=True)


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
    transition_duration = min(0.4, min(durations) / 4)
    for index, duration in enumerate(durations):
        clip_duration = duration + (transition_duration if index < len(durations) - 1 else 0)
        frames = max(1, round(clip_duration * FPS))
        direction_x = "iw/2-(iw/zoom/2)" if index % 2 == 0 else "iw-iw/zoom"
        filters.append(
            f"[{index}:v]zoompan=z='min(zoom+0.0009,1.075)':x='{direction_x}':"
            f"y='ih/2-(ih/zoom/2)':d={frames}:s={WIDTH}x{HEIGHT}:fps={FPS},"
            f"trim=duration={clip_duration:.3f},setpts=PTS-STARTPTS,setsar=1[v{index}]"
        )
    transitions = ("fade", "slideleft", "smoothup", "circleopen")
    previous = "[v0]"
    offset = durations[0]
    for index in range(1, len(images)):
        output = "[vout]" if index == len(images) - 1 else f"[vx{index}]"
        filters.append(
            f"{previous}[v{index}]xfade=transition={transitions[(index - 1) % len(transitions)]}:"
            f"duration={transition_duration:.3f}:offset={offset:.3f}{output}"
        )
        previous = output
        offset += durations[index]
    if len(images) == 1:
        filters.append("[v0]format=yuv420p[vout]")
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


def build_clip_concat_command(clips: list[Path], audio: Path, final: Path) -> list[str]:
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    for clip in clips:
        command.extend(["-i", str(clip)])
    audio_index = len(clips)
    command.extend(["-i", str(audio)])
    parts = "".join(f"[{index}:v:0]" for index in range(len(clips)))
    filters = f"{parts}concat=n={len(clips)}:v=1:a=0[vout]"
    command.extend([
        "-filter_complex", filters, "-map", "[vout]", "-map", f"{audio_index}:a:0",
        "-c:v", "libx264", "-preset", "ultrafast", "-crf", "27",
        "-threads", str(FFMPEG_THREADS),
        "-c:a", "aac", "-b:a", "112k", "-shortest", "-movflags", "+faststart", str(final),
    ])
    return command


def image_to_video_clip(provider: str, image: Path, scene: Scene, duration: float, output: Path) -> bool:
    endpoint_var = "SEEDANCE2_IMAGE_TO_VIDEO_URL" if provider == "seedance2_fast" else "VEO_IMAGE_TO_VIDEO_URL"
    key_var = "SEEDANCE2_API_KEY" if provider == "seedance2_fast" else "VEO_API_KEY"
    endpoint = os.getenv(endpoint_var, "").strip()
    api_key = os.getenv(key_var, "").strip()
    if not endpoint or not api_key:
        LOGGER.info("Video provider %s chưa cấu hình; dùng Ken Burns.", provider)
        return False
    payload = json.dumps({
        "prompt": f"{scene.visual_prompt}. {scene.character_action}. {scene.camera_motion}. Smooth 9:16 animation.",
        "duration_seconds": max(5, min(10, round(duration))),
        "aspect_ratio": "16:9" if WIDTH > HEIGHT else "9:16",
        "image_base64": base64.b64encode(image.read_bytes()).decode("ascii"),
    }).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=payload,
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        data = json.loads(response.read().decode("utf-8"))
    video_url = str(data.get("video_url") or data.get("url") or "").strip()
    if not video_url:
        job_id = str(data.get("id") or data.get("job_id") or "").strip()
        status_url = str(data.get("status_url") or "").strip()
        for _ in range(36):
            time.sleep(5)
            poll_url = status_url or f"{endpoint.rstrip('/')}/{job_id}"
            poll = urllib.request.Request(poll_url, headers={"Authorization": f"Bearer {api_key}"})
            with urllib.request.urlopen(poll, timeout=30) as response:
                data = json.loads(response.read().decode("utf-8"))
            video_url = str(data.get("video_url") or data.get("url") or "").strip()
            if video_url:
                break
    if not video_url:
        raise RuntimeError(f"{provider} không trả về video_url")
    urllib.request.urlretrieve(video_url, output)
    return output.exists() and output.stat().st_size > 0


def generate_motion_clips(provider: str, images: list[Path], scenes: list[Scene], durations: list[float], output_dir: Path) -> list[Path]:
    if provider not in {"seedance2_fast", "veo"}:
        return []
    output_dir.mkdir(parents=True, exist_ok=True)
    clips: list[Path] = []
    for index, (image, scene, duration) in enumerate(zip(images, scenes, durations), 1):
        clip = output_dir / f"scene_{index:02}.mp4"
        if not image_to_video_clip(provider, image, scene, duration, clip):
            return []
        clips.append(clip)
    return clips


def create_video(plan: VideoPlan, job_dir: Path) -> tuple[Path, float]:
    missing = [binary for binary in ("ffmpeg", "ffprobe") if not shutil.which(binary)]
    if missing:
        raise RuntimeError(f"Thiếu công cụ: {', '.join(missing)}")

    voice = job_dir / ("bgm.mp3" if is_silent_animation(plan) else "narration.mp3")
    if is_silent_animation(plan):
        total = float(plan.target_duration_seconds)
        make_bgm(voice, total)
        weights = [max(1.0, scene.duration_hint) for scene in plan.scenes]
    else:
        asyncio.run(make_voice(" ".join(scene.narration for scene in plan.scenes), voice))
        total = media_duration(voice)
        weights = [max(1, len(scene.narration)) for scene in plan.scenes]
    weight_sum = sum(weights)
    durations = [max(2.5, total * weight / weight_sum) for weight in weights]
    duration_scale = total / sum(durations)
    durations = [duration * duration_scale for duration in durations]
    images = generate_scene_visuals(plan, job_dir / "scenes", character_ref=getattr(plan, '_character_ref', None))

    cursor = 0.0
    subtitles: list[str] = []
    for index, (scene, seconds) in enumerate(zip(plan.scenes, durations), 1):
        subtitle = scene.narration if scene.narration.strip() else scene.on_screen_text
        subtitles.append(f"{index}\n{srt_time(cursor)} --> {srt_time(cursor + seconds)}\n{subtitle}\n")
        cursor += seconds
    (job_dir / "subtitles.srt").write_text("\n".join(subtitles), encoding="utf-8")

    final = job_dir / "final.mp4"
    clips = generate_motion_clips(plan.video_provider, images, plan.scenes, durations, job_dir / "motion_clips")
    command = build_clip_concat_command(clips, voice, final) if clips else build_motion_ffmpeg_command(images, durations, voice, final)
    subprocess.run(command, check=True, capture_output=True)
    return final, media_duration(final)


def quality_control(plan: VideoPlan, research: ResearchBrief, fact_check: FactCheck, duration: float) -> dict[str, Any]:
    score = 100
    issues = list(fact_check.issues)
    guard = assess_content(
        [asdict(scene) for scene in plan.scenes],
        research.to_dict(),
        plan.target_duration_seconds,
        plan.audio_mode,
    )
    score -= guard["penalty"]
    issues.extend(guard["issues"])
    issues.extend(guard["blocking_issues"])
    tolerance = max(12, plan.target_duration_seconds * 0.35)
    if abs(duration - plan.target_duration_seconds) > tolerance:
        score -= 15
        issues.append(
            f"Thời lượng {duration:.1f}s lệch đáng kể so với mục tiêu {plan.target_duration_seconds}s"
        )
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
        "ready_for_review": score >= 70 and guard["passed"],
        "duration_seconds": round(duration, 2),
        "scene_count": len(plan.scenes),
        "source_count": len(research.sources),
        "ai_images_enabled": bool(os.getenv("OPENAI_API_KEY")) and ENABLE_AI_IMAGES,
        "script_provider": plan.provider,
        "audio_mode": plan.audio_mode,
        "video_provider": plan.video_provider,
        "target_duration_seconds": plan.target_duration_seconds,
        "content_guard": guard,
    }


def run_script_only(
    topic: str,
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
    visual_style: str = "",
    character_description: str = "",
    character_image_url: str = "",
    audio_mode: str = "",
    video_provider: str = "",
    aspect_ratio: str = "",
    render_quality: str = "",
) -> dict[str, Any]:
    research = research_topic(topic)
    requested_audio_mode = (audio_mode or os.getenv("VIDEO_AUDIO_MODE", "narrated")).strip()
    requested_aspect_ratio = (aspect_ratio or os.getenv("VIDEO_ASPECT_RATIO", "9:16")).strip()
    plan = generate_plan(
        topic,
        research,
        target_duration_seconds,
        visual_style,
        character_description,
        requested_audio_mode,
        requested_aspect_ratio,
    )
    plan.character_image_url = character_image_url
    requested_video_provider = (video_provider or os.getenv("VIDEO_PROVIDER", "kenburns")).strip()
    requested_render_quality = (render_quality or os.getenv("VIDEO_RENDER_QUALITY", "draft")).strip()
    plan.audio_mode = requested_audio_mode if requested_audio_mode in {"narrated", "silent_animation"} else "narrated"
    plan.video_provider = requested_video_provider if requested_video_provider in {"seedance2_fast", "veo", "kenburns"} else "kenburns"
    plan.aspect_ratio = requested_aspect_ratio if requested_aspect_ratio in {"9:16", "16:9"} else "9:16"
    plan.render_quality = requested_render_quality if requested_render_quality in {"draft", "hd", "2k"} else "draft"
    if plan.audio_mode == "silent_animation":
        for scene in plan.scenes:
            scene.narration = ""
            
    fact_check = fact_check_plan(plan, research)
    
    return {
        "caption": plan.caption,
        "hashtags": plan.hashtags,
        "research": research.to_dict(),
        "storyboard": asdict(plan),
        "fact_check": asdict(fact_check),
        "status": "DRAFT_REQUIRES_REVIEW",
    }


def upload_video(video: Path, job_id: str) -> str:
    if not os.getenv("CLOUDINARY_URL", "").strip():
        LOGGER.warning("Chưa cấu hình CLOUDINARY_URL; trả về file URL cục bộ.")
        return video.resolve().as_uri()
    import cloudinary
    import cloudinary.uploader

    cloudinary.config(secure=True)
    result = cloudinary.uploader.upload_large(
        str(video), resource_type="video", public_id=f"techflow/{job_id}", overwrite=True
    )
    original_url = str(result["secure_url"])
    delivery_transform = f"c_limit,h_{HEIGHT},w_{WIDTH},q_auto:eco"
    return original_url.replace("/video/upload/", f"/video/upload/{delivery_transform}/", 1)


def run(
    topic: str,
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
    visual_style: str = "",
    character_description: str = "",
    character_image_url: str = "",
    audio_mode: str = "",
    video_provider: str = "",
    aspect_ratio: str = "",
    render_quality: str = "",
) -> dict[str, Any]:
    job_id = f"{datetime.now(timezone.utc):%Y%m%d_%H%M%S}_{slugify(topic)}"
    job_dir = OUTPUT_DIR / job_id
    job_dir.mkdir(parents=True)
    research = research_topic(topic)
    requested_audio_mode = (audio_mode or os.getenv("VIDEO_AUDIO_MODE", "narrated")).strip()
    requested_aspect_ratio = (aspect_ratio or os.getenv("VIDEO_ASPECT_RATIO", "9:16")).strip()
    plan = generate_plan(
        topic,
        research,
        target_duration_seconds,
        visual_style,
        character_description,
        requested_audio_mode,
        requested_aspect_ratio,
    )
    plan.character_image_url = character_image_url
    requested_video_provider = (video_provider or os.getenv("VIDEO_PROVIDER", "kenburns")).strip()
    requested_render_quality = (render_quality or os.getenv("VIDEO_RENDER_QUALITY", "draft")).strip()
    plan.audio_mode = requested_audio_mode if requested_audio_mode in {"narrated", "silent_animation"} else "narrated"
    plan.video_provider = requested_video_provider if requested_video_provider in {"seedance2_fast", "veo", "kenburns"} else "kenburns"
    plan.aspect_ratio = requested_aspect_ratio if requested_aspect_ratio in {"9:16", "16:9"} else "9:16"
    plan.render_quality = requested_render_quality if requested_render_quality in {"draft", "hd", "2k"} else "draft"
    if plan.audio_mode == "silent_animation":
        for scene in plan.scenes:
            scene.narration = ""
    apply_render_profile(plan.aspect_ratio, plan.render_quality)
    # Download character reference for visual consistency
    character_ref = None
    if character_image_url:
        ref_path = job_dir / "character_ref.png"
        character_ref = _download_reference_image(character_image_url, ref_path)
    plan._character_ref = character_ref
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
    parser.add_argument(
        "--duration",
        type=int,
        default=DEFAULT_TARGET_DURATION_SECONDS,
        help="Thời lượng mục tiêu 30-600 giây",
    )
    parser.add_argument("--visual-style", default="", help="Định hướng hình ảnh nhất quán")
    parser.add_argument("--character", default="", help="Mô tả nhân vật/host nhất quán")
    parser.add_argument("--character-image", default="", help="URL ảnh reference nhân vật để giữ nhất quán")
    parser.add_argument("--generate-character", action="store_true", help="Chỉ tạo ảnh character reference rồi thoát")
    parser.add_argument("--generate-script", action="store_true", help="Chỉ tạo kịch bản và storyboard rồi thoát")
    parser.add_argument("--audio-mode", choices=["narrated", "silent_animation"], default=os.getenv("VIDEO_AUDIO_MODE", "narrated"))
    parser.add_argument("--video-provider", choices=["seedance2_fast", "veo", "kenburns"], default=os.getenv("VIDEO_PROVIDER", "kenburns"))
    parser.add_argument("--aspect-ratio", choices=["9:16", "16:9"], default=os.getenv("VIDEO_ASPECT_RATIO", "9:16"))
    parser.add_argument("--render-quality", choices=["draft", "hd", "2k"], default=os.getenv("VIDEO_RENDER_QUALITY", "draft"))
    args = parser.parse_args()
    try:
        if args.generate_character:
            url = generate_character_image(args.character, args.visual_style, args.topic)
            if url:
                print(f"CHARACTER_IMAGE_URL={url}", flush=True)
                sys.exit(0)
            else:
                print("CHARACTER_IMAGE_FAILED", flush=True)
                sys.exit(1)
                
        if args.generate_script:
            result = run_script_only(
                args.topic,
                args.duration,
                args.visual_style,
                args.character,
                args.character_image,
                args.audio_mode,
                args.video_provider,
                args.aspect_ratio,
                args.render_quality,
            )
            # Output in the same format expected by worker
            print("===WORKER_OUTPUT===")
            print(json.dumps(result, ensure_ascii=False))
            sys.exit(0)
            
        result = run(
            args.topic,
            args.duration,
            args.visual_style,
            args.character,
            args.character_image,
            args.audio_mode,
            args.video_provider,
            args.aspect_ratio,
            args.render_quality,
        )
        metadata = json.dumps(result, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        print(f"VIDEO_METADATA_B64={base64.urlsafe_b64encode(metadata).decode('ascii')}", flush=True)
        print(f"VIDEO_READY={result['video_url']}", flush=True)
    except Exception as exc:
        LOGGER.exception("Tạo video thất bại: %s", exc)
        sys.exit(1)
