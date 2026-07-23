"""Safe, review-first video worker for AI TechFlow Studio.

The worker is intentionally scoped to video generation.  It never controls the
desktop, opens TikTok, or publishes content.  The Java API keeps generated
videos in ``DRAFT_REQUIRES_REVIEW`` until a human approves them.
"""

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
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from typing import Any
from urllib.parse import urlparse
from urllib.request import Request, urlopen

try:
    import cloudinary
    import cloudinary.uploader
except ImportError:  # Optional for script/plan unit tests.
    cloudinary = None

try:
    import edge_tts
except ImportError:  # Optional for script/plan unit tests.
    edge_tts = None

try:
    from openai import OpenAI
except ImportError:  # Optional when running the deterministic fallback.
    OpenAI = None

try:
    from google import genai
except ImportError:  # Optional when Gemini is not configured.
    genai = None

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # Optional for plan-only unit tests.
    Image = ImageDraw = ImageFont = None


WIDTH, HEIGHT, FPS = 1080, 1920, 24
OUTPUT_DIR = Path(os.getenv("OUTPUT_DIR", "/tmp/techflow-outputs"))
CHANNEL_NAME = os.getenv("CHANNEL_NAME", "TechFlow VN")
VOICE = os.getenv("TTS_VOICE", "vi-VN-HoaiMyNeural")
DEFAULT_TARGET_DURATION_SECONDS = 60
MAX_TARGET_DURATION_SECONDS = 600
USER_AGENT = "AI-TechFlow-Research/1.0 (+https://github.com/mhiep7441-byte/mhiep7441-byte-AI-TechFlow-MVP)"
logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")


@dataclass
class Source:
    title: str
    url: str
    excerpt: str = ""


@dataclass
class ResearchBrief:
    query: str
    sources: list[Source] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)


@dataclass
class Scene:
    title: str
    narration: str
    on_screen_text: str
    visual_prompt: str = "Minh họa công nghệ hiện đại, ánh sáng xanh tím"
    character_action: str = "nhân vật nhìn vào màn hình và giải thích"


@dataclass
class VideoPlan:
    topic: str
    scenes: list[Scene]
    caption: str
    hashtags: list[str]
    sources: list[Source] = field(default_factory=list)
    research_status: str = "NEEDS_REVIEW"
    disclaimer: str = "Kiểm tra nguồn và duyệt nội dung trước khi đăng."
    provider: str = "fallback"
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS


@dataclass
class QualityReport:
    score: int
    status: str
    checks: list[dict[str, Any]] = field(default_factory=list)
    blocking_issues: list[str] = field(default_factory=list)


def assess_quality(plan: VideoPlan) -> QualityReport:
    """Run deterministic checks before a draft can reach a human reviewer."""
    checks: list[dict[str, Any]] = []

    def check(code: str, label: str, passed: bool, blocking: bool = True) -> None:
        checks.append({"code": code, "label": label, "passed": passed, "blocking": blocking})

    scene_limit = _scene_limit(plan.target_duration_seconds)
    check("scene_count", f"Có từ 4 đến {scene_limit} cảnh", 4 <= len(plan.scenes) <= scene_limit)
    narration_length = sum(len(scene.narration.strip()) for scene in plan.scenes)
    minimum_narration = max(220, plan.target_duration_seconds * 4)
    check(
        "narration",
        "Lời thoại phù hợp thời lượng mục tiêu",
        minimum_narration <= narration_length <= 18_000,
    )
    check("visuals", "Mỗi cảnh có minh họa và hành động nhân vật", all(scene.visual_prompt.strip() and scene.character_action.strip() for scene in plan.scenes))
    check("caption", "Caption không rỗng và không vượt giới hạn", 1 <= len(plan.caption.strip()) <= 2_200)
    check("hashtags", "Có hashtag hợp lệ", 2 <= len(plan.hashtags) <= 8 and all(item.startswith("#") for item in plan.hashtags), False)
    check("sources", "Có ít nhất một nguồn để đối chiếu", bool(plan.sources), True)

    blocking_issues = [item["label"] for item in checks if item["blocking"] and not item["passed"]]
    score = round(100 * sum(1 for item in checks if item["passed"]) / len(checks)) if checks else 0
    status = "PASS" if not blocking_issues else "NEEDS_REVIEW"
    return QualityReport(score, status, checks, blocking_issues)


class _VisibleTextParser(HTMLParser):
    """Small dependency-free HTML-to-text parser used for source excerpts."""

    _ignored = {"script", "style", "noscript", "svg"}

    def __init__(self) -> None:
        super().__init__()
        self._ignored_depth = 0
        self.parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag.lower() in self._ignored:
            self._ignored_depth += 1

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() in self._ignored and self._ignored_depth:
            self._ignored_depth -= 1

    def handle_data(self, data: str) -> None:
        if not self._ignored_depth:
            text = re.sub(r"\s+", " ", data).strip()
            if text:
                self.parts.append(text)


def slugify(value: str) -> str:
    value = re.sub(r"[^\w\s-]", "", value.lower(), flags=re.UNICODE)
    return re.sub(r"[-\s]+", "-", value).strip("-")[:60] or "video"


def _normalized_duration(value: int | str | None) -> int:
    try:
        duration = int(value or DEFAULT_TARGET_DURATION_SECONDS)
    except (TypeError, ValueError):
        duration = DEFAULT_TARGET_DURATION_SECONDS
    return min(MAX_TARGET_DURATION_SECONDS, max(30, duration))


def _scene_limit(target_duration_seconds: int) -> int:
    """Give long-form scripts enough scene changes without creating huge jobs."""
    duration = _normalized_duration(target_duration_seconds)
    return min(30, max(6, (duration + 11) // 12))


def _allowed_domains() -> set[str]:
    configured = os.getenv("RESEARCH_ALLOWED_DOMAINS", "").strip()
    if configured:
        return {item.strip().lower().lstrip(".") for item in configured.split(",") if item.strip()}
    return {
        "ai.google.dev",
        "developers.openai.com",
        "platform.openai.com",
        "docs.python.org",
        "docs.docker.com",
        "developer.mozilla.org",
        "docs.github.com",
        "developers.tiktok.com",
    }


def _is_allowed_source(url: str) -> bool:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname:
        return False
    host = parsed.hostname.lower().rstrip(".")
    return any(host == domain or host.endswith("." + domain) for domain in _allowed_domains())


def _source_catalog(topic: str) -> list[Source]:
    lowered = topic.lower()
    catalog: list[Source] = []
    if any(word in lowered for word in ("openai", "gpt", "codex", "ai")):
        catalog.append(Source("OpenAI API documentation", "https://developers.openai.com/api/docs/overview"))
    if any(word in lowered for word in ("python", "django", "fastapi")):
        catalog.append(Source("Python documentation", "https://docs.python.org/3/"))
    if any(word in lowered for word in ("docker", "container")):
        catalog.append(Source("Docker documentation", "https://docs.docker.com/get-started/"))
    if any(word in lowered for word in ("web", "http", "javascript", "react")):
        catalog.append(Source("MDN Web Docs", "https://developer.mozilla.org/en-US/docs/Web"))
    if any(word in lowered for word in ("tiktok", "video", "social")):
        catalog.append(Source("TikTok for Developers", "https://developers.tiktok.com/products/content-posting-api"))
    return catalog[:3]


def _fetch_source(source: Source, timeout: float = 8.0) -> Source:
    if not _is_allowed_source(source.url):
        raise ValueError(f"Nguồn không nằm trong allowlist HTTPS: {source.url}")
    request = Request(source.url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/xhtml+xml"})
    with urlopen(request, timeout=timeout) as response:
        raw = response.read(180_000)
    parser = _VisibleTextParser()
    parser.feed(raw.decode("utf-8", errors="replace"))
    excerpt = re.sub(r"\s+", " ", " ".join(parser.parts)).strip()[:4000]
    if not excerpt:
        raise ValueError("Nguồn không có văn bản đọc được")
    return Source(source.title, source.url, excerpt)


def research_topic(topic: str, source_urls: list[str] | None = None) -> ResearchBrief:
    """Fetch a small, auditable set of trusted sources.

    URLs can be supplied through ``RESEARCH_URLS`` (comma-separated), but only
    HTTPS hosts in ``RESEARCH_ALLOWED_DOMAINS`` are accepted to avoid SSRF and
    accidental use of untrusted material.
    """

    configured = source_urls or [item.strip() for item in os.getenv("RESEARCH_URLS", "").split(",") if item.strip()]
    candidates = [Source(urlparse(url).netloc or url, url) for url in configured] or _source_catalog(topic)
    brief = ResearchBrief(topic)
    for candidate in candidates[:3]:
        try:
            brief.sources.append(_fetch_source(candidate))
        except Exception as exc:  # Network failure must not silently become a citation.
            brief.notes.append(f"Không đọc được {candidate.url}: {exc}")
    if not brief.sources:
        brief.notes.append("Chưa lấy được nguồn chính thức; nội dung phải được kiểm chứng thủ công.")
    return brief


def _fallback_plan(
    topic: str,
    research: ResearchBrief,
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
) -> VideoPlan:
    source_line = "\n".join(source.title for source in research.sources) or "Chưa có nguồn tự động"
    return VideoPlan(
        topic,
        [
            Scene("MỞ ĐẦU", f"Bạn đang tìm hiểu về {topic}? Trong video này, chúng ta sẽ tách vấn đề thành những ý dễ hiểu.", topic, "hologram chủ đề công nghệ, ánh sáng neon", "nhân vật bước vào khung hình và chỉ vào tiêu đề"),
            Scene("VÌ SAO QUAN TRỌNG", "Điểm quan trọng là hiểu cách công nghệ hoạt động và giới hạn của nó, thay vì chỉ nhìn vào lời quảng cáo.", "Hiểu cơ chế\nBiết giới hạn", "nhân vật đứng cạnh bảng sơ đồ và biểu tượng cảnh báo", "nhân vật khoanh vùng hai ý chính trên bảng"),
            Scene("CÁCH TIẾP CẬN", "Hãy bắt đầu bằng yêu cầu rõ ràng, xử lý dữ liệu có kiểm soát, rồi kiểm tra kết quả bằng nguồn đáng tin cậy.", "Yêu cầu → Xử lý → Kiểm tra", "bảng quy trình ba bước, các nút phát sáng", "nhân vật nối ba bước bằng tay"),
            Scene("KIỂM CHỨNG", f"Các tài liệu tham khảo của video gồm: {source_line}. Nếu chưa có nguồn, hãy coi đây là bản nháp và kiểm chứng trước khi đăng.", "Nguồn rõ ràng\nCon người duyệt", "kính lúp, tài liệu và huy hiệu kiểm chứng", "nhân vật cầm kính lúp kiểm tra tài liệu"),
            Scene("KẾT LUẬN", f"Đó là cách tiếp cận an toàn với {topic}. Lưu video, xem lại phụ đề và chỉ chia sẻ sau khi bạn duyệt.", "Bản nháp an toàn\nDuyệt trước khi đăng", "nhân vật vẫy tay cạnh màn hình hoàn tất", "nhân vật bấm nút xem lại, không phải nút đăng"),
        ],
        f"{topic} — giải thích ngắn gọn, có kiểm chứng trước khi đăng.",
        ["#congnghe", "#laptrinh", "#AI", "#TechFlowVN"],
        research.sources,
        "VERIFIED_SOURCES" if research.sources else "NEEDS_REVIEW",
        "Bản nháp AI: hãy kiểm tra nguồn, bản quyền và phụ đề trước khi đăng.",
        "fallback",
        _normalized_duration(target_duration_seconds),
    )


def _parse_json_object(value: str) -> dict[str, Any]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", value.strip(), flags=re.IGNORECASE)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("AI không trả về JSON hợp lệ")
    return json.loads(cleaned[start : end + 1])


def _plan_prompt(topic: str, research: ResearchBrief, target_duration_seconds: int) -> str:
    duration = _normalized_duration(target_duration_seconds)
    scene_limit = _scene_limit(duration)
    source_context = "\n\n".join(
        f"[{item.title}] {item.url}\n{item.excerpt}" for item in research.sources
    )
    return f"""Bạn là biên tập viên công nghệ Việt Nam. Tạo kịch bản video dọc khoảng {duration} giây cho chủ đề: {topic}.
Chỉ dùng dữ kiện có trong nguồn bên dưới; không bịa số liệu, không khẳng định tuyệt đối. Nếu nguồn không đủ, nói rõ cần kiểm chứng.
Viết phần mở đầu có hook, thân bài có diễn tiến rõ ràng và kết thúc có lời kêu gọi phù hợp. Nội dung phải đủ sâu cho thời lượng mục tiêu, không lặp ý để kéo dài.
Mỗi cảnh phải có nhân vật minh họa, hành động và một visual_prompt ngắn cho họa sĩ 2D/motion designer.
Trả JSON thuần theo schema: {{"topic":"...","scenes":[{{"title":"...","narration":"...","on_screen_text":"...","visual_prompt":"...","character_action":"..."}}],"caption":"...","hashtags":["#..."]}}.
Tối đa {scene_limit} cảnh, tối thiểu 4 cảnh. Nguồn tham khảo (không tự thêm URL):
{source_context or "(không có nguồn, phải nêu rõ nội dung cần kiểm chứng)"}"""


def _plan_from_data(
    data: dict[str, Any],
    topic: str,
    research: ResearchBrief,
    target_duration_seconds: int,
    provider: str,
) -> VideoPlan:
    scene_limit = _scene_limit(target_duration_seconds)
    scenes: list[Scene] = []
    for raw in data.get("scenes", [])[:scene_limit]:
        if not isinstance(raw, dict):
            continue
        scenes.append(Scene(
            str(raw.get("title", "CẢNH"))[:80],
            str(raw.get("narration", "")).strip()[:1400],
            str(raw.get("on_screen_text", "")).strip()[:180],
            str(raw.get("visual_prompt", "Minh họa công nghệ hiện đại"))[:240],
            str(raw.get("character_action", "nhân vật giải thích"))[:180],
        ))
    if len(scenes) < 4 or any(not scene.narration for scene in scenes):
        raise ValueError("Kịch bản AI thiếu cảnh hoặc lời thoại")
    hashtags = [str(item) for item in data.get("hashtags", []) if str(item).startswith("#")][:8]
    return VideoPlan(
        str(data.get("topic", topic))[:500],
        scenes,
        str(data.get("caption", f"{topic} — xem bản nháp và kiểm chứng trước khi đăng."))[:2200],
        hashtags or ["#congnghe", "#TechFlowVN"],
        research.sources,
        "VERIFIED_SOURCES" if research.sources else "NEEDS_REVIEW",
        "Bản nháp AI: hãy kiểm tra nguồn, bản quyền và phụ đề trước khi đăng.",
        provider,
        _normalized_duration(target_duration_seconds),
    )


def _openai_plan(
    topic: str,
    research: ResearchBrief,
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
) -> VideoPlan:
    key = os.getenv("OPENAI_API_KEY", "").strip()
    if not key or OpenAI is None:
        raise RuntimeError("OpenAI chưa được cấu hình")
    response = OpenAI(api_key=key).responses.create(
        model=os.getenv("OPENAI_MODEL", "gpt-5-mini"),
        input=_plan_prompt(topic, research, target_duration_seconds),
    )
    return _plan_from_data(
        _parse_json_object(response.output_text),
        topic,
        research,
        target_duration_seconds,
        "openai",
    )


def _gemini_plan(
    topic: str,
    research: ResearchBrief,
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
) -> VideoPlan:
    key = os.getenv("GEMINI_API_KEY", "").strip()
    if not key or genai is None:
        raise RuntimeError("Gemini chưa được cấu hình")
    client = genai.Client(api_key=key)
    response = client.models.generate_content(
        model=os.getenv("GEMINI_MODEL", "gemini-2.5-flash"),
        contents=_plan_prompt(topic, research, target_duration_seconds),
        config={"response_mime_type": "application/json"},
    )
    return _plan_from_data(
        _parse_json_object(response.text or ""),
        topic,
        research,
        target_duration_seconds,
        "gemini",
    )


def generate_plan(
    topic: str,
    research: ResearchBrief | None = None,
    visual_style: str = "",
    character_description: str = "",
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
) -> VideoPlan:
    research = research or research_topic(topic)
    duration = _normalized_duration(target_duration_seconds)
    provider = os.getenv("AI_PROVIDER", "auto").strip().lower()
    if provider not in {"auto", "gemini", "openai"}:
        raise ValueError("AI_PROVIDER chỉ nhận auto, gemini hoặc openai")
    candidates = (
        ["gemini", "openai"] if provider == "auto"
        else [provider]
    )
    plan: VideoPlan | None = None
    for candidate in candidates:
        try:
            plan = (
                _gemini_plan(topic, research, duration)
                if candidate == "gemini"
                else _openai_plan(topic, research, duration)
            )
            break
        except Exception as exc:
            logging.warning("%s plan failed: %s", candidate, exc)
    if plan is None:
        logging.warning("No AI provider available; using deterministic fallback")
        plan = _fallback_plan(topic, research, duration)
    if visual_style or character_description:
        for scene in plan.scenes:
            if visual_style:
                scene.visual_prompt = f"{visual_style}; {scene.visual_prompt}"[:240]
            if character_description:
                scene.character_action = f"{character_description}: {scene.character_action}"[:180]
    return plan


def selected_font(size: int, bold: bool = False):
    if ImageFont is None:
        raise RuntimeError("Thiếu Pillow. Cài worker-requirements.txt trước khi tạo hình.")
    names = [
        ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        ("C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"),
        ("/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"),
    ]
    for name in names:
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


def wrap(draw, value: str, font, max_width: int) -> list[str]:
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
    return lines


def _draw_character(draw, action: str, accent: str) -> None:
    """Draw a consistent illustrated presenter without external copyrighted assets."""
    cx, cy = 540, 640
    draw.ellipse((cx - 105, cy - 170, cx + 105, cy + 40), fill="#ffd6b3", outline="#ffb27e", width=5)
    draw.arc((cx - 55, cy - 45, cx + 55, cy + 32), 15, 165, fill="#29344d", width=6)
    draw.ellipse((cx - 63, cy - 75, cx - 42, cy - 54), fill="#1a2235")
    draw.ellipse((cx + 42, cy - 75, cx + 63, cy - 54), fill="#1a2235")
    draw.rounded_rectangle((cx - 170, cy + 25, cx + 170, cy + 460), radius=65, fill=accent, outline="#93eaff", width=4)
    draw.line((cx - 130, cy + 130, cx - 350, cy + 40), fill="#ffd6b3", width=28)
    draw.line((cx + 130, cy + 130, cx + 350, cy + 40), fill="#ffd6b3", width=28)
    draw.ellipse((cx - 365, cy + 18, cx - 335, cy + 48), fill="#ffd6b3")
    draw.ellipse((cx + 335, cy + 18, cx + 365, cy + 48), fill="#ffd6b3")
    draw.text((cx - 275, cy + 505), "AI TECHFLOW", font=selected_font(28, True), fill="#e9fbff")
    draw.text((cx - 410, cy + 580), action[:58], font=selected_font(25), fill="#a9bdd5")


def _draw_visual(draw, prompt: str, character_action: str = "nhân vật minh họa") -> None:
    prompt_lower = prompt.lower()
    accent = "#2c8ce6" if any(key in prompt_lower for key in ("bảng", "quy trình", "screen")) else "#7c4dff"
    for x in range(95, 1000, 150):
        draw.line((x, 270, x, 1530), fill="#152846", width=2)
    for y in range(300, 1530, 150):
        draw.line((80, y, 1000, y), fill="#152846", width=2)
    draw.rounded_rectangle((100, 340, 980, 510), radius=26, fill="#101e35", outline="#2b466c", width=3)
    draw.text((140, 390), "VISUAL BRIEF", font=selected_font(30, True), fill="#70ddff")
    for index, label in enumerate(("CẢNH", "NHÂN VẬT", "Ý CHÍNH")):
        left = 150 + index * 275
        draw.rounded_rectangle((left, 445, left + 220, 480), radius=9, fill="#1c3557")
        draw.text((left + 15, 451), label, font=selected_font(20, True), fill="#b8c9df")
    _draw_character(draw, character_action, accent)


def draw_scene(scene: Scene, index: int, count: int, output: Path, source_hint: str = "") -> None:
    if Image is None or ImageDraw is None:
        raise RuntimeError("Thiếu Pillow. Cài worker-requirements.txt trước khi tạo video.")
    image = Image.new("RGB", (WIDTH, HEIGHT), "#070b14")
    draw = ImageDraw.Draw(image)
    for y in range(HEIGHT):
        ratio = y / HEIGHT
        color = (7 + int(10 * ratio), 11 + int(15 * ratio), 25 + int(28 * ratio))
        draw.line((0, y, WIDTH, y), fill=color)
    draw.rounded_rectangle((65, 80, 1015, 235), radius=38, fill="#14233a", outline="#2e83b7", width=3)
    draw.text((105, 128), scene.title.upper(), font=selected_font(54, True), fill="#67d7ff")
    _draw_visual(draw, scene.visual_prompt, scene.character_action)
    body = selected_font(60, True)
    lines = wrap(draw, scene.on_screen_text, body, 850)
    y = 1190 - len(lines) * 45
    for line in lines:
        box = draw.textbbox((0, 0), line, font=body)
        draw.text(((WIDTH - box[2]) / 2, y), line, font=body, fill="#f5faff")
        y += 92
    progress = int(860 * (index + 1) / count)
    draw.rounded_rectangle((110, 1640, 970, 1660), radius=10, fill="#253451")
    draw.rounded_rectangle((110, 1640, 110 + progress, 1660), radius=10, fill="#4bcbff")
    draw.text((110, 1745), CHANNEL_NAME, font=selected_font(36, True), fill="#67d7ff")
    draw.text((890, 1745), f"{index + 1}/{count}", font=selected_font(34), fill="#a4b1c7")
    if source_hint:
        draw.text((110, 1810), f"Nguồn: {source_hint[:82]}", font=selected_font(22), fill="#7f91aa")
    image.save(output, quality=94)


async def make_voice(text: str, output: Path) -> None:
    if edge_tts is None:
        raise RuntimeError("Thiếu edge-tts. Cài worker-requirements.txt trước khi tạo giọng đọc.")
    await edge_tts.Communicate(text, VOICE, rate="+4%").save(str(output))


def media_duration(path: Path) -> float:
    result = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", str(path)],
        check=True, capture_output=True, text=True,
    )
    return float(result.stdout.strip())


def srt_time(seconds: float) -> str:
    milliseconds = int(max(0, seconds) * 1000)
    hours, milliseconds = divmod(milliseconds, 3_600_000)
    minutes, milliseconds = divmod(milliseconds, 60_000)
    secs, milliseconds = divmod(milliseconds, 1000)
    return f"{hours:02}:{minutes:02}:{secs:02},{milliseconds:03}"


def create_video(plan: VideoPlan, job_dir: Path) -> Path:
    missing = [tool for tool in ("ffmpeg", "ffprobe") if shutil.which(tool) is None]
    if missing:
        raise RuntimeError(f"Thiếu công cụ: {', '.join(missing)}")
    if len(plan.scenes) < 4:
        raise ValueError("Video cần ít nhất 4 cảnh")
    scenes_dir = job_dir / "scenes"
    scenes_dir.mkdir(parents=True, exist_ok=True)
    source_hint = plan.sources[0].title if plan.sources else "chưa có nguồn tự động"
    for index, scene in enumerate(plan.scenes):
        draw_scene(scene, index, len(plan.scenes), scenes_dir / f"scene_{index:02}.png", source_hint)
    voice = job_dir / "narration.mp3"
    asyncio.run(make_voice(" ".join(scene.narration for scene in plan.scenes), voice))
    total = media_duration(voice)
    weights = [max(1, len(scene.narration)) for scene in plan.scenes]
    weight_sum = sum(weights)
    cursor = 0.0
    durations: list[float] = []
    subtitles: list[str] = []
    for index, (scene, weight) in enumerate(zip(plan.scenes, weights), start=1):
        seconds = total * weight / weight_sum
        durations.append(seconds)
        subtitles.append(f"{index}\n{srt_time(cursor)} --> {srt_time(cursor + seconds)}\n{scene.narration}\n")
        cursor += seconds
    (job_dir / "subtitles.srt").write_text("\n".join(subtitles), encoding="utf-8")
    transition_duration = min(0.45, min(durations) / 4)
    clips: list[Path] = []
    for index, seconds in enumerate(durations):
        clip = scenes_dir / f"clip_{index:02}.mp4"
        clip_duration = seconds + (transition_duration if index < len(durations) - 1 else 0)
        zoom_direction = "zoom+0.0007" if index % 2 == 0 else "zoom+0.0005"
        subprocess.run([
            "ffmpeg", "-y", "-loop", "1", "-t", f"{clip_duration:.3f}",
            "-i", str(scenes_dir / f"scene_{index:02}.png"),
            "-vf",
            (
                f"scale={WIDTH}:{HEIGHT}:force_original_aspect_ratio=increase,"
                f"crop={WIDTH}:{HEIGHT},"
                f"zoompan=z='min({zoom_direction},1.06)':"
                f"x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:"
                f"s={WIDTH}x{HEIGHT}:fps={FPS},format=yuv420p"
            ),
            "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", str(clip),
        ], check=True)
        clips.append(clip)
    silent, final = job_dir / "silent.mp4", job_dir / "final.mp4"
    transitions = ("fade", "slideleft", "smoothup", "circleopen")
    inputs: list[str] = []
    for clip in clips:
        inputs.extend(["-i", str(clip)])
    filters: list[str] = []
    previous = "[0:v]"
    offset = durations[0]
    for index in range(1, len(clips)):
        output_label = f"[v{index}]"
        filters.append(
            f"{previous}[{index}:v]xfade=transition={transitions[(index - 1) % len(transitions)]}:"
            f"duration={transition_duration:.3f}:offset={offset:.3f}{output_label}"
        )
        previous = output_label
        offset += durations[index]
    subprocess.run([
        "ffmpeg", "-y", *inputs, "-filter_complex", ";".join(filters),
        "-map", previous, "-an", "-c:v", "libx264", "-preset", "veryfast",
        "-crf", "24", "-pix_fmt", "yuv420p", str(silent),
    ], check=True)
    subprocess.run(["ffmpeg", "-y", "-i", str(silent), "-i", str(voice), "-c:v", "copy", "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", str(final)], check=True)
    return final


def run(
    topic: str,
    upload: bool = True,
    source_urls: list[str] | None = None,
    visual_style: str = "",
    character_description: str = "",
    target_duration_seconds: int = DEFAULT_TARGET_DURATION_SECONDS,
) -> str:
    if not topic or not topic.strip():
        raise ValueError("Chủ đề video không được để trống")
    job_id = f"{datetime.now(timezone.utc):%Y%m%d_%H%M%S}_{slugify(topic)}"
    job_dir = OUTPUT_DIR / job_id
    job_dir.mkdir(parents=True, exist_ok=True)
    research = research_topic(topic, source_urls)
    plan = generate_plan(
        topic,
        research,
        visual_style,
        character_description,
        target_duration_seconds,
    )
    quality = assess_quality(plan)
    (job_dir / "script.json").write_text(json.dumps(asdict(plan), ensure_ascii=False, indent=2), encoding="utf-8")
    (job_dir / "research.json").write_text(json.dumps(asdict(research), ensure_ascii=False, indent=2), encoding="utf-8")
    (job_dir / "quality.json").write_text(json.dumps(asdict(quality), ensure_ascii=False, indent=2), encoding="utf-8")
    (job_dir / "metadata.json").write_text(json.dumps({"status": "DRAFT_REQUIRES_REVIEW", "topic": topic, "caption": plan.caption, "hashtags": plan.hashtags, "sources": [asdict(source) for source in plan.sources], "quality": asdict(quality)}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"QUALITY_SCORE={quality.score}", flush=True)
    print(f"QUALITY_STATUS={quality.status}", flush=True)
    print(f"QUALITY_REPORT={json.dumps(asdict(quality), ensure_ascii=True, separators=(',', ':'))}", flush=True)
    print(f"AI_PROVIDER_USED={plan.provider}", flush=True)
    video = create_video(plan, job_dir)
    if not upload:
        return str(video)
    if cloudinary is None or not os.getenv("CLOUDINARY_URL", "").strip():
        raise RuntimeError("Chưa cấu hình CLOUDINARY_URL trên server.")
    cloudinary.config(secure=True)
    result = cloudinary.uploader.upload_large(
        str(video), resource_type="video", public_id=f"techflow/{job_id}", overwrite=True,
        tags=["techflow", "draft_requires_review"],
        context={"topic": topic, "status": "DRAFT_REQUIRES_REVIEW"},
    )
    return str(result["secure_url"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Generate a research-backed vertical draft video")
    parser.add_argument("--topic", required=True)
    parser.add_argument("--no-upload", action="store_true", help="Keep the MP4 local; useful for local QA")
    parser.add_argument("--sources", default="", help="Comma-separated HTTPS source URLs")
    parser.add_argument("--visual-style", default="", help="Optional visual direction for every scene")
    parser.add_argument("--character", default="", help="Optional host/character description")
    parser.add_argument("--duration", type=int, default=DEFAULT_TARGET_DURATION_SECONDS, help="Target duration in seconds (30-600)")
    args = parser.parse_args()
    try:
        sources = [item.strip() for item in args.sources.split(",") if item.strip()]
        print(f"VIDEO_READY={run(args.topic, upload=not args.no_upload, source_urls=sources or None, visual_style=args.visual_style, character_description=args.character, target_duration_seconds=args.duration)}", flush=True)
    except Exception as exc:
        logging.exception("Tạo video thất bại: %s", exc)
        sys.exit(1)
