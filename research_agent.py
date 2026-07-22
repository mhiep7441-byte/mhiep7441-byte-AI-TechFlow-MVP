from __future__ import annotations

import ipaddress
import json
import logging
import os
import re
import socket
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass
from datetime import datetime, timezone

LOGGER = logging.getLogger(__name__)
RESEARCH_MODEL = os.getenv("OPENAI_RESEARCH_MODEL", "gpt-5.6-terra")
MAX_SOURCES = max(2, min(int(os.getenv("RESEARCH_MAX_SOURCES", "6")), 10))


@dataclass
class ResearchSource:
    id: str
    title: str
    url: str
    publisher: str
    published_at: str = ""
    source_type: str = "primary"
    reachable: bool = False


@dataclass
class ResearchClaim:
    claim: str
    source_ids: list[str]
    confidence: str = "medium"


@dataclass
class ResearchBrief:
    topic: str
    summary: str
    claims: list[ResearchClaim]
    sources: list[ResearchSource]
    caveats: list[str]
    researched_at: str
    mode: str = "web_search"

    def to_dict(self) -> dict:
        return asdict(self)


def offline_brief(topic: str) -> ResearchBrief:
    return ResearchBrief(
        topic=topic,
        summary="Bản mẫu offline; chưa thực hiện nghiên cứu web.",
        claims=[],
        sources=[],
        caveats=["Cần API key và bước kiểm chứng nguồn chính thức trước khi xuất bản."],
        researched_at=datetime.now(timezone.utc).isoformat(),
        mode="offline",
    )


def _extract_json(value: str) -> dict:
    cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", value.strip(), flags=re.I)
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("Research Agent không trả về JSON hợp lệ")
    return json.loads(cleaned[start : end + 1])


def _is_public_https_url(value: str) -> bool:
    try:
        parsed = urllib.parse.urlparse(value)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password:
            return False
        addresses = socket.getaddrinfo(parsed.hostname, 443, type=socket.SOCK_STREAM)
        for item in addresses:
            address = ipaddress.ip_address(item[4][0])
            if any(
                (
                    address.is_private,
                    address.is_loopback,
                    address.is_link_local,
                    address.is_multicast,
                    address.is_reserved,
                    address.is_unspecified,
                )
            ):
                return False
        return True
    except (OSError, ValueError):
        return False


def _probe_source(url: str) -> bool:
    if not _is_public_https_url(url):
        return False
    request = urllib.request.Request(
        url,
        method="HEAD",
        headers={"User-Agent": "AI-TechFlow-Research/1.0"},
    )
    try:
        with urllib.request.urlopen(request, timeout=6) as response:
            return 200 <= response.status < 400
    except (urllib.error.URLError, TimeoutError, ValueError):
        return False


def _normalise_brief(topic: str, data: dict) -> ResearchBrief:
    raw_sources = data.get("sources") if isinstance(data.get("sources"), list) else []
    sources: list[ResearchSource] = []
    seen_urls: set[str] = set()
    for index, item in enumerate(raw_sources[:MAX_SOURCES], 1):
        if not isinstance(item, dict):
            continue
        url = str(item.get("url", "")).strip()
        if url in seen_urls or not _is_public_https_url(url):
            continue
        seen_urls.add(url)
        sources.append(
            ResearchSource(
                id=str(item.get("id") or f"S{index}")[:20],
                title=str(item.get("title") or "Nguồn chính thức")[:300],
                url=url[:1500],
                publisher=str(item.get("publisher") or urllib.parse.urlparse(url).hostname or "")[:160],
                published_at=str(item.get("published_at") or "")[:40],
                source_type=str(item.get("source_type") or "primary")[:40],
                reachable=_probe_source(url),
            )
        )

    valid_ids = {source.id for source in sources}
    claims: list[ResearchClaim] = []
    raw_claims = data.get("claims") if isinstance(data.get("claims"), list) else []
    for item in raw_claims[:12]:
        if not isinstance(item, dict):
            continue
        claim = str(item.get("claim", "")).strip()
        if not claim:
            continue
        source_ids = [str(value) for value in item.get("source_ids", []) if str(value) in valid_ids]
        claims.append(
            ResearchClaim(
                claim=claim[:900],
                source_ids=source_ids,
                confidence=str(item.get("confidence") or "medium")[:20],
            )
        )

    caveats = [str(value)[:500] for value in data.get("caveats", []) if str(value).strip()][:8]
    if sources and not any(source.reachable for source in sources):
        caveats.append("Máy chủ nguồn từ chối kiểm tra tự động; cần mở liên kết khi duyệt.")
    if len(sources) < 2:
        caveats.append("Chưa đủ hai nguồn độc lập; không nên xuất bản như một tin đã xác nhận.")

    return ResearchBrief(
        topic=topic,
        summary=str(data.get("summary") or "")[:1800],
        claims=claims,
        sources=sources,
        caveats=caveats,
        researched_at=datetime.now(timezone.utc).isoformat(),
    )


def research_topic(topic: str) -> ResearchBrief:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key:
        LOGGER.warning("Không có OPENAI_API_KEY; Research Agent chạy chế độ offline.")
        return offline_brief(topic)

    try:
        from openai import OpenAI
    except ImportError:
        LOGGER.warning("Thiếu OpenAI SDK; Research Agent chạy chế độ offline.")
        return offline_brief(topic)

    prompt = f"""
Bạn là Research Agent cho kênh công nghệ tiếng Việt. Nghiên cứu chủ đề: {topic}

Quy tắc bắt buộc:
- Ưu tiên tài liệu chính thức, bài nghiên cứu gốc, trang sản phẩm/changelog chính thức và cơ quan có thẩm quyền.
- Với tin mới, xác nhận ngày xảy ra sự kiện và ngày xuất bản; không dựa vào một bài tổng hợp duy nhất.
- Không sao chép nguyên văn dài. Không suy đoán số liệu hoặc tính năng.
- Mỗi khẳng định quan trọng phải tham chiếu source_ids.
- Chỉ trả JSON thuần theo schema bên dưới; tối đa {MAX_SOURCES} nguồn.

{{
  "summary": "tóm tắt trung lập bằng tiếng Việt",
  "claims": [{{"claim":"...","source_ids":["S1"],"confidence":"high|medium|low"}}],
  "sources": [{{
    "id":"S1", "title":"...", "url":"https://...", "publisher":"...",
    "published_at":"YYYY-MM-DD hoặc rỗng", "source_type":"official|primary|research"
  }}],
  "caveats": ["điều chưa chắc chắn hoặc cần nói rõ"]
}}
""".strip()

    client = OpenAI(api_key=api_key)
    try:
        response = client.responses.create(
            model=RESEARCH_MODEL,
            tools=[{"type": "web_search"}],
            input=prompt,
        )
        return _normalise_brief(topic, _extract_json(response.output_text))
    except Exception as exc:  # OpenAI/network errors must keep the offline pipeline usable.
        LOGGER.exception("Research Agent thất bại, chuyển sang offline: %s", exc)
        brief = offline_brief(topic)
        brief.caveats.append(f"Research Agent lỗi: {type(exc).__name__}")
        return brief
