from unittest.mock import patch

import research_agent


def test_offline_brief_is_explicit_about_missing_web_research():
    brief = research_agent.offline_brief("Python 3.14")

    assert brief.mode == "offline"
    assert brief.sources == []
    assert "chưa thực hiện nghiên cứu web" in brief.summary.lower()


def test_normalise_rejects_private_and_non_https_sources():
    payload = {
        "summary": "Tóm tắt",
        "claims": [{"claim": "Một dữ kiện", "source_ids": ["S1", "S2"]}],
        "sources": [
            {"id": "S1", "title": "Local", "url": "http://127.0.0.1/secret", "publisher": "bad"},
            {"id": "S2", "title": "Metadata", "url": "https://169.254.169.254/latest", "publisher": "bad"},
        ],
    }

    brief = research_agent._normalise_brief("test", payload)

    assert brief.sources == []
    assert brief.claims[0].source_ids == []
    assert any("hai nguồn" in caveat for caveat in brief.caveats)


def test_normalise_keeps_safe_primary_source_without_fetching_network():
    payload = {
        "summary": "Tóm tắt",
        "claims": [{"claim": "Python có tài liệu chính thức", "source_ids": ["S1"]}],
        "sources": [
            {
                "id": "S1",
                "title": "Python documentation",
                "url": "https://docs.python.org/3/",
                "publisher": "Python Software Foundation",
                "source_type": "official",
            }
        ],
    }

    with patch("research_agent.socket.getaddrinfo", return_value=[(None, None, None, None, ("151.101.0.223", 443))]), patch(
        "research_agent._probe_source", return_value=True
    ):
        brief = research_agent._normalise_brief("Python", payload)

    assert len(brief.sources) == 1
    assert brief.sources[0].reachable is True
    assert brief.claims[0].source_ids == ["S1"]
