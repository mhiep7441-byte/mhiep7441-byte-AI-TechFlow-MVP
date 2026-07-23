from __future__ import annotations

import re
from typing import Any


def _tokens(value: str) -> set[str]:
    return {
        token
        for token in re.findall(r"\w+", value.lower(), flags=re.UNICODE)
        if len(token) > 3
    }


def _similarity(left: str, right: str) -> float:
    left_tokens, right_tokens = _tokens(left), _tokens(right)
    if not left_tokens or not right_tokens:
        return 0.0
    return len(left_tokens & right_tokens) / len(left_tokens | right_tokens)


def assess_content(
    scenes: list[dict[str, Any]],
    research: dict[str, Any],
    target_duration_seconds: int,
) -> dict[str, Any]:
    issues: list[str] = []
    blocking: list[str] = []
    sources = research.get("sources") if isinstance(research.get("sources"), list) else []
    claims = research.get("claims") if isinstance(research.get("claims"), list) else []
    valid_source_ids = {
        str(source.get("id"))
        for source in sources
        if isinstance(source, dict)
        and str(source.get("url", "")).startswith("https://")
        and bool(source.get("reachable"))
    }
    sourced_claims = sum(
        1
        for claim in claims
        if isinstance(claim, dict)
        and any(str(source_id) in valid_source_ids for source_id in claim.get("source_ids", []))
    )
    claim_coverage = sourced_claims / len(claims) if claims else 0.0
    primary_sources = sum(
        1
        for source in sources
        if isinstance(source, dict)
        and str(source.get("source_type", "")).lower() in {"official", "primary", "research"}
    )
    source_quality_ratio = primary_sources / len(sources) if sources else 0.0

    required_scenes = min(30, max(5, (max(30, target_duration_seconds) + 14) // 15))
    if len(scenes) < required_scenes:
        issues.append(f"Cần ít nhất {required_scenes} cảnh cho thời lượng mục tiêu")

    unsupported_numeric_scenes = []
    for index, scene in enumerate(scenes, 1):
        narration = str(scene.get("narration", ""))
        source_ids = {str(value) for value in scene.get("source_ids", [])}
        if re.search(r"\d", narration) and not (source_ids & valid_source_ids):
            unsupported_numeric_scenes.append(index)
    if unsupported_numeric_scenes:
        blocking.append(
            "Cảnh có số liệu nhưng chưa gắn nguồn đã kiểm tra: "
            + ", ".join(map(str, unsupported_numeric_scenes))
        )

    duplicate_pairs = []
    for left in range(len(scenes)):
        for right in range(left + 1, len(scenes)):
            if _similarity(str(scenes[left].get("narration", "")), str(scenes[right].get("narration", ""))) >= 0.82:
                duplicate_pairs.append(f"{left + 1}-{right + 1}")
    if duplicate_pairs:
        issues.append("Lời đọc có cảnh trùng ý mạnh: " + ", ".join(duplicate_pairs[:5]))

    if research.get("mode") == "offline":
        blocking.append("Research đang ở chế độ offline; phải kiểm chứng thủ công trước khi xuất bản")
    elif len(sources) < 2:
        blocking.append("Chưa có tối thiểu hai nguồn độc lập")
    if claims and claim_coverage < 0.8:
        blocking.append(f"Chỉ {round(claim_coverage * 100)}% claim có nguồn truy cập được")
    if sources and source_quality_ratio < 0.6:
        issues.append("Tỷ lệ nguồn official/primary/research dưới 60%")

    penalty = min(45, len(issues) * 6 + len(blocking) * 12)
    return {
        "passed": not blocking,
        "penalty": penalty,
        "issues": issues,
        "blocking_issues": blocking,
        "claim_coverage": round(claim_coverage, 3),
        "source_quality_ratio": round(source_quality_ratio, 3),
        "verified_source_count": len(valid_source_ids),
        "required_scene_count": required_scenes,
        "actual_scene_count": len(scenes),
    }
