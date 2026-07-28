import unittest
import os
import tempfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import video_worker
from research_agent import offline_brief


class VideoWorkerTests(unittest.TestCase):
    def test_fallback_plan_keeps_vietnamese_text(self):
        plan = video_worker.fallback_plan("AI cho lập trình viên")

        self.assertEqual("MỞ ĐẦU", plan.scenes[0].title)
        self.assertIn("AI cho lập trình viên", plan.scenes[0].narration)
        self.assertEqual(6, len(plan.scenes))
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
        self.assertIn("xfade=transition=fade", filter_graph)
        self.assertEqual("2:a:0", command[command.index("-map", command.index("-map") + 1) + 1])

    def test_bgm_command_creates_synthetic_audio_track(self):
        command = video_worker.build_bgm_command(Path("bgm.mp3"), 12.0)

        self.assertEqual("ffmpeg", command[0])
        self.assertTrue(any("anoisesrc" in part for part in command))
        self.assertIn("sine=frequency=523:sample_rate=44100", command)
        self.assertEqual("bgm.mp3", command[-1])

    def test_seedance_provider_without_endpoint_falls_back_to_kenburns(self):
        with patch.dict(os.environ, {"SEEDANCE2_IMAGE_TO_VIDEO_URL": "", "SEEDANCE2_API_KEY": ""}, clear=False):
            clips = video_worker.generate_motion_clips(
                "seedance2_fast",
                [Path("scene.jpg")],
                [video_worker.Scene("A", "", "A")],
                [5.0],
                Path("clips"),
            )

        self.assertEqual([], clips)

    def test_long_form_scene_limit_and_duration_are_bounded(self):
        self.assertEqual(6, video_worker.scene_limit(60))
        self.assertEqual(18, video_worker.scene_limit(180))
        self.assertEqual(30, video_worker.scene_limit(600))
        self.assertEqual(600, video_worker.normalized_duration(999))

    def test_render_profiles_support_vertical_and_landscape(self):
        video_worker.apply_render_profile("16:9", "hd")
        self.assertEqual((1280, 720), (video_worker.WIDTH, video_worker.HEIGHT))
        video_worker.apply_render_profile("9:16", "draft")
        self.assertEqual((540, 960), (video_worker.WIDTH, video_worker.HEIGHT))

    def test_auto_provider_prefers_gemini(self):
        class FakeResponse:
            text = '{"scenes":[]}'

        class FakeModels:
            def generate_content(self, **_kwargs):
                return FakeResponse()

        class FakeClient:
            def __init__(self, **_kwargs):
                self.models = FakeModels()

        class FakeGenai:
            Client = FakeClient

        with patch.dict(os.environ, {"AI_PROVIDER": "auto", "GEMINI_API_KEY": "test-key"}, clear=False), \
                patch.dict("sys.modules", {"google": type("Google", (), {"genai": FakeGenai})}):
            data, provider = video_worker._generate_script_json("prompt")

        self.assertEqual({"scenes": []}, data)
        self.assertEqual("gemini", provider)

    def test_offline_fact_check_requires_review(self):
        plan = video_worker.fallback_plan("AI agent")
        brief = offline_brief("AI agent")
        fact_check = video_worker.fact_check_plan(plan, brief)

        self.assertFalse(fact_check.approved)
        self.assertTrue(any("offline" in issue.lower() for issue in fact_check.issues))

    def test_required_ai_never_returns_fallback_storyboard(self):
        with patch.dict(os.environ, {"AI_REQUIRED": "true"}, clear=False), \
                patch.object(video_worker, "_generate_script_json", side_effect=RuntimeError("Gemini unavailable")):
            with self.assertRaisesRegex(RuntimeError, "Gemini unavailable"):
                video_worker.generate_plan("AI thật", offline_brief("AI thật"), 180)

    def test_gemini_image_provider_generates_each_scene(self):
        calls = []

        class FakeModels:
            def generate_content(self, **kwargs):
                calls.append(kwargs)
                return SimpleNamespace(parts=[SimpleNamespace(inline_data=SimpleNamespace(data=b"generated-image"))])

        class FakeClient:
            def __init__(self, **_kwargs):
                self.models = FakeModels()

        fake_genai = SimpleNamespace(Client=FakeClient)
        plan = video_worker.fallback_plan("AI hình ảnh")
        plan.scenes = plan.scenes[:1]
        with tempfile.TemporaryDirectory() as directory, \
                patch.dict(os.environ, {
                    "GEMINI_API_KEY": "test-key",
                    "IMAGE_PROVIDER": "gemini",
                    "GEMINI_IMAGE_MODEL": "gemini-image-test",
                }, clear=False), \
                patch.dict("sys.modules", {"google": SimpleNamespace(genai=fake_genai)}), \
                patch.object(video_worker, "compose_scene", side_effect=lambda _raw, _scene, _index, _count, output: output.write_bytes(b"jpg")):
            paths = video_worker.generate_scene_visuals(plan, Path(directory))

        self.assertEqual(1, len(calls))
        self.assertEqual("gemini-image-test", calls[0]["model"])
        self.assertEqual(1, len(paths))


if __name__ == "__main__":
    unittest.main()
