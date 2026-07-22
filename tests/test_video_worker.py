import unittest
from pathlib import Path

import video_worker
from research_agent import offline_brief


class VideoWorkerTests(unittest.TestCase):
    def test_fallback_plan_keeps_vietnamese_text(self):
        plan = video_worker.fallback_plan("AI cho lập trình viên")

        self.assertEqual("MỞ ĐẦU", plan.scenes[0].title)
        self.assertIn("Linh sẽ tóm tắt", plan.scenes[0].narration)
        self.assertEqual(5, len(plan.scenes))
        self.assertTrue(all(scene.visual_prompt for scene in plan.scenes))
        self.assertTrue(all(scene.character_action for scene in plan.scenes))

    def test_ffmpeg_command_uses_single_pass_low_memory_profile(self):
        command = video_worker.build_ffmpeg_command(
            Path("slides.txt"),
            Path("voice.mp3"),
            Path("final.mp4"),
        )

        self.assertEqual("ffmpeg", command[0])
        self.assertIn("ultrafast", command)
        self.assertIn("stillimage", command)
        self.assertEqual("1", command[command.index("-threads") + 1])
        self.assertNotIn("silent.mp4", command)
        self.assertEqual("final.mp4", command[-1])

    def test_render_canvas_defaults_to_free_tier_profile(self):
        self.assertEqual((540, 960, 12), (video_worker.WIDTH, video_worker.HEIGHT, video_worker.FPS))

    def test_motion_command_uses_one_image_input_per_scene(self):
        command = video_worker.build_motion_ffmpeg_command(
            [Path("one.jpg"), Path("two.jpg")],
            [4.0, 5.0],
            Path("voice.mp3"),
            Path("final.mp4"),
        )

        filter_graph = command[command.index("-filter_complex") + 1]
        self.assertIn("zoompan", filter_graph)
        self.assertIn("concat=n=2", filter_graph)
        self.assertEqual("2:a:0", command[command.index("-map", command.index("-map") + 1) + 1])

    def test_offline_fact_check_requires_review(self):
        plan = video_worker.fallback_plan("AI agent")
        brief = offline_brief("AI agent")
        fact_check = video_worker.fact_check_plan(plan, brief)

        self.assertFalse(fact_check.approved)
        self.assertTrue(any("offline" in issue.lower() for issue in fact_check.issues))


if __name__ == "__main__":
    unittest.main()
