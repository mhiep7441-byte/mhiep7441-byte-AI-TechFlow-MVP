import json

from series_planner import fallback_plan, normalize_plan, plan_series


def test_offline_plan_has_requested_episode_count(monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    plan = plan_series("Chó cảnh sát dũng cảm", 8, "Trẻ em 7-11 tuổi")
    assert plan["provider"] == "offline"
    assert len(plan["episodes"]) == 8
    assert plan["episodes"][0]["factual_guardrails"]
    assert len(plan["episodes"][0]["visual_beats"]) >= 5
    json.dumps(plan, ensure_ascii=False)


def test_normalize_plan_fills_missing_safety_fields():
    plan = normalize_plan({"episodes": [{"title": "Mở đầu"}]}, "AI an toàn", 2, "Gia đình")
    assert len(plan["episodes"]) == 2
    assert plan["episodes"][0]["title"] == "Mở đầu"
    assert plan["episodes"][1]["learning_objective"]
    assert plan["safety_notes"]


def test_fallback_caps_content_to_a_structured_bible():
    plan = fallback_plan("Robot cứu hộ", 3, "Trẻ em")
    assert plan["title"] == "Robot cứu hộ"
    assert len(plan["characters"]) >= 1
    assert all(len(row["visual_beats"]) >= 5 for row in plan["episodes"])
