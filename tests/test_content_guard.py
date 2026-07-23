from content_guard import assess_content


def test_guard_blocks_unsupported_numbers_and_offline_research():
    report = assess_content(
        [{"narration": "Hiệu suất tăng 42 phần trăm.", "source_ids": []}] * 5,
        {"mode": "offline", "sources": [], "claims": []},
        60,
    )
    assert report["passed"] is False
    assert any("số liệu" in item for item in report["blocking_issues"])
    assert report["penalty"] > 0


def test_guard_accepts_sourced_claims_and_multiple_primary_sources():
    report = assess_content(
        [
            {"narration": "Dữ kiện 2026 đã được kiểm tra.", "source_ids": ["S1"]},
            {"narration": "Giải thích khái niệm chính.", "source_ids": ["S1"]},
            {"narration": "Cho ví dụ độc lập.", "source_ids": ["S2"]},
            {"narration": "Nêu giới hạn sử dụng.", "source_ids": ["S2"]},
            {"narration": "Tóm tắt và kết luận.", "source_ids": ["S1"]},
        ],
        {
            "mode": "web_search",
            "claims": [{"claim": "A", "source_ids": ["S1"]}, {"claim": "B", "source_ids": ["S2"]}],
            "sources": [
                {"id": "S1", "url": "https://example.com/a", "reachable": True, "source_type": "official"},
                {"id": "S2", "url": "https://example.org/b", "reachable": True, "source_type": "research"},
            ],
        },
        60,
    )
    assert report["passed"] is True
    assert report["claim_coverage"] == 1
    assert report["verified_source_count"] == 2
