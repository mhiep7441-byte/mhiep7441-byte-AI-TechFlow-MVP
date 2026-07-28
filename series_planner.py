from __future__ import annotations

import argparse
import base64
import json
import logging
import os
import re
import sys
from typing import Any

LOGGER = logging.getLogger("series_planner")
logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)s | %(message)s")


def _json_object(value: str) -> dict[str, Any]:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", value.strip(), flags=re.I)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("Series Planner did not return a JSON object")
    parsed = json.loads(cleaned[start : end + 1])
    if not isinstance(parsed, dict):
        raise ValueError("Series plan must be an object")
    return parsed


def fallback_plan(theme: str, episodes: int, audience: str) -> dict[str, Any]:
    safe_audience = audience.strip() or "Khán giả phổ thông"
    angles = [
        ("Gặp gỡ nhân vật", "Giới thiệu thế giới, mục tiêu và quy tắc an toàn."),
        ("Nhiệm vụ đầu tiên", "Đặt một thử thách nhỏ, giải quyết bằng quan sát và hợp tác."),
        ("Bài học bất ngờ", "Cho nhân vật mắc lỗi an toàn rồi tự sửa bằng bằng chứng."),
        ("Làm việc theo đội", "Nhấn mạnh giao tiếp, trách nhiệm và hỗ trợ lẫn nhau."),
        ("Thử thách lớn", "Tổng hợp kỹ năng, không mô tả hành vi nguy hiểm có thể bắt chước."),
        ("Nhìn lại hành trình", "Ôn kiến thức chính và gợi mở tập tiếp theo."),
    ]
    rows = []
    for index in range(episodes):
        title, synopsis = angles[index % len(angles)]
        rows.append(
            {
                "number": index + 1,
                "title": f"{title} — Tập {index + 1}",
                "learning_objective": "Một bài học rõ ràng, phù hợp độ tuổi và có thể kiểm chứng.",
                "hook": f"Điều gì sẽ xảy ra trong nhiệm vụ số {index + 1}?",
                "synopsis": synopsis,
                "factual_guardrails": [
                    "Không khẳng định dữ kiện chưa có nguồn.",
                    "Phân biệt chi tiết hư cấu với kiến thức thực tế.",
                    "Không hướng dẫn trẻ thực hiện hành vi nguy hiểm.",
                ],
                "visual_beats": [
                    "Mở cảnh định vị không gian",
                    "Nhân vật nhận nhiệm vụ",
                    "Hai bước giải quyết trực quan",
                    "Khoảnh khắc kiểm chứng",
                    "Kết luận và teaser",
                ],
            }
        )
    return {
        "title": theme.strip(),
        "audience": safe_audience,
        "format": "Series video dọc có nhân vật và bối cảnh nhất quán",
        "series_promise": f"Mỗi tập giúp {safe_audience.lower()} hiểu thêm một điều đúng về {theme.strip()}.",
        "world": "Một thế giới trực quan, ấm áp, có quy tắc rõ ràng và không cổ vũ hành vi nguy hiểm.",
        "characters": [
            {
                "name": "Nhân vật chính",
                "role": "Dẫn dắt câu chuyện",
                "continuity": "Giữ nguyên ngoại hình, màu trang phục, tính cách và giọng kể qua mọi tập.",
            }
        ],
        "safety_notes": [
            "Mọi video là bản nháp cần người dùng duyệt.",
            "Kiến thức thực tế phải được đối chiếu nguồn chính thức hoặc nguồn gốc.",
            "Nội dung cho trẻ em không dùng nỗi sợ, bạo lực đồ họa hoặc thử thách nguy hiểm.",
        ],
        "episodes": rows,
        "provider": "offline",
    }


def _model_plan(prompt: str) -> tuple[dict[str, Any], str]:
    provider = os.getenv("AI_PROVIDER", "auto").strip().lower()
    if provider not in {"auto", "gemini", "openai"}:
        raise ValueError("AI_PROVIDER must be auto, gemini, or openai")
    if provider in {"auto", "gemini"} and os.getenv("GEMINI_API_KEY", "").strip():
        try:
            from google import genai

            response = genai.Client(api_key=os.environ["GEMINI_API_KEY"].strip()).models.generate_content(
                model=os.getenv("GEMINI_MODEL", "gemini-2.5-flash"),
                contents=prompt,
                config={"response_mime_type": "application/json"},
            )
            return _json_object(response.text or ""), "gemini"
        except Exception:
            if provider == "gemini":
                raise
            LOGGER.exception("Gemini Series Planner failed; trying OpenAI")
    if provider in {"auto", "openai"} and os.getenv("OPENAI_API_KEY", "").strip():
        from openai import OpenAI

        response = OpenAI(api_key=os.environ["OPENAI_API_KEY"].strip()).responses.create(
            model=os.getenv("OPENAI_SCRIPT_MODEL", "gpt-5.6-terra"),
            input=prompt,
        )
        return _json_object(response.output_text), "openai"
    raise RuntimeError("No AI provider configured")


def normalize_plan(data: dict[str, Any], theme: str, episodes: int, audience: str) -> dict[str, Any]:
    fallback = fallback_plan(theme, episodes, audience)
    source_episodes = data.get("episodes") if isinstance(data.get("episodes"), list) else []
    normalized_episodes = []
    for index in range(episodes):
        base = fallback["episodes"][index]
        item = source_episodes[index] if index < len(source_episodes) and isinstance(source_episodes[index], dict) else {}
        normalized_episodes.append(
            {
                "number": index + 1,
                "title": str(item.get("title") or base["title"])[:160],
                "learning_objective": str(item.get("learning_objective") or base["learning_objective"])[:500],
                "hook": str(item.get("hook") or base["hook"])[:500],
                "synopsis": str(item.get("synopsis") or base["synopsis"])[:1200],
                "factual_guardrails": [
                    str(value)[:400] for value in item.get("factual_guardrails", base["factual_guardrails"])[:6]
                ],
                "visual_beats": [str(value)[:300] for value in item.get("visual_beats", base["visual_beats"])[:8]],
            }
        )
    return {
        "title": str(data.get("title") or fallback["title"])[:160],
        "audience": str(data.get("audience") or fallback["audience"])[:160],
        "format": str(data.get("format") or fallback["format"])[:300],
        "series_promise": str(data.get("series_promise") or fallback["series_promise"])[:800],
        "world": str(data.get("world") or fallback["world"])[:1200],
        "characters": data.get("characters")[:8] if isinstance(data.get("characters"), list) else fallback["characters"],
        "safety_notes": [
            str(value)[:500]
            for value in (data.get("safety_notes") if isinstance(data.get("safety_notes"), list) else fallback["safety_notes"])[:8]
        ],
        "episodes": normalized_episodes,
        "provider": str(data.get("provider") or "ai")[:30],
    }


def plan_series(theme: str, episodes: int, audience: str) -> dict[str, Any]:
    safe_count = min(30, max(1, int(episodes)))
    prompt = f"""
Bạn là showrunner, biên tập viên giáo dục và chuyên gia an toàn nội dung.
Thiết kế series tiếng Việt gồm đúng {safe_count} tập.
Chủ đề: {theme}
Khán giả: {audience or "Khán giả phổ thông"}

Yêu cầu:
- Mỗi tập có ý tưởng riêng, nối tiếp hợp lý và không lặp.
- Nếu là series cho trẻ em (ví dụ chó cảnh sát), câu chuyện hấp dẫn nhưng phải phân biệt hư cấu với sự thật.
- Không bịa dữ kiện. factual_guardrails nêu chính xác điều worker phải kiểm chứng bằng nguồn chính thức/nguồn gốc.
- Mỗi tập có ít nhất 5 visual_beats để tạo nhiều cảnh và chuyển cảnh.
- Giữ nhân vật, thế giới, màu sắc và tính cách nhất quán.
- Không tự xuất bản; nội dung luôn là bản nháp cần duyệt.
- Trả JSON thuần với các khóa: title, audience, format, series_promise, world,
  characters, safety_notes, episodes.
- Mỗi episode có: number, title, learning_objective, hook, synopsis,
  factual_guardrails (mảng), visual_beats (mảng).
""".strip()
    try:
        data, provider = _model_plan(prompt)
        data["provider"] = provider
        return normalize_plan(data, theme, safe_count, audience)
    except Exception:
        if os.getenv("AI_REQUIRED", "false").strip().lower() in {"1", "true", "yes"}:
            LOGGER.exception("AI Series Planner failed while AI_REQUIRED is enabled")
            raise
        LOGGER.exception("AI Series Planner unavailable; using deterministic offline plan")
        return fallback_plan(theme, safe_count, audience)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--theme", required=True)
    parser.add_argument("--episodes", type=int, default=5)
    parser.add_argument("--audience", default="")
    args = parser.parse_args()
    plan = plan_series(args.theme, args.episodes, args.audience)
    payload = json.dumps(plan, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    print(f"SERIES_PLAN_B64={base64.urlsafe_b64encode(payload).decode('ascii')}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
