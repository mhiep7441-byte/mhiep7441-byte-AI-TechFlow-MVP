from main import mock_plan, slugify


def test_slugify():
    assert slugify("Docker là gì?") == "docker-là-gì"


def test_mock_plan_has_scenes():
    plan = mock_plan("Test topic")
    assert len(plan.scenes) >= 3
    assert plan.topic == "Test topic"
