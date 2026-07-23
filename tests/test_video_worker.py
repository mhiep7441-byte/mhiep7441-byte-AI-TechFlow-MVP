import unittest
from unittest.mock import patch

import video_worker


class VideoWorkerSafetyTests(unittest.TestCase):
    def test_source_allowlist_requires_https_and_trusted_host(self):
        self.assertTrue(video_worker._is_allowed_source("https://docs.python.org/3/"))
        self.assertFalse(video_worker._is_allowed_source("http://docs.python.org/3/"))
        self.assertFalse(video_worker._is_allowed_source("https://example.com/article"))

    def test_json_parser_accepts_fenced_json(self):
        parsed = video_worker._parse_json_object('```json\n{"scenes": []}\n```')
        self.assertEqual(parsed, {"scenes": []})

    def test_fallback_plan_is_review_first_and_has_visual_scenes(self):
        research = video_worker.ResearchBrief(
            "Python", [video_worker.Source("Python docs", "https://docs.python.org/3/")]
        )
        with patch.object(video_worker, "OpenAI", None):
            plan = video_worker.generate_plan("Python", research)

        self.assertGreaterEqual(len(plan.scenes), 4)
        self.assertEqual(plan.research_status, "VERIFIED_SOURCES")
        self.assertTrue(all(scene.visual_prompt for scene in plan.scenes))
        self.assertTrue(all(scene.character_action for scene in plan.scenes))
        self.assertEqual(plan.sources[0].url, "https://docs.python.org/3/")

    def test_ai_failure_falls_back_without_marking_as_published(self):
        research = video_worker.ResearchBrief("Unknown topic")
        with patch.object(video_worker, "OpenAI", None):
            plan = video_worker.generate_plan("Unknown topic", research)

        self.assertEqual(plan.research_status, "NEEDS_REVIEW")
        self.assertTrue(plan.disclaimer.strip())


if __name__ == "__main__":
    unittest.main()
