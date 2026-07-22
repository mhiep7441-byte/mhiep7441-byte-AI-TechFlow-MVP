import unittest
from pathlib import Path

import video_worker


class VideoWorkerTests(unittest.TestCase):
    def test_fallback_plan_keeps_vietnamese_text(self):
        plan = video_worker.fallback_plan("AI cho lập trình viên")

        self.assertEqual("MỞ ĐẦU", plan.scenes[0].title)
        self.assertIn("Bạn đang tìm hiểu", plan.scenes[0].narration)
        self.assertEqual(5, len(plan.scenes))

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


if __name__ == "__main__":
    unittest.main()
