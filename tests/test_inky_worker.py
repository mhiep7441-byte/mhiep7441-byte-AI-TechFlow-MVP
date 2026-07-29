import unittest
import json
from pathlib import Path
from inky_worker import parse_multi_character_storyboard, build_render_manifest, create_project_zip

class TestInkyWorker(unittest.TestCase):
    def test_parse_multi_character_storyboard(self):
        raw_json = {
            "topic": "Bobo và Elias",
            "hook": "Câu chuyện bắt đầu",
            "visual_style": "3D Cinematic",
            "characters": [
                { "id": "inky", "name": "Inky", "role": "mascot", "description": "Cute blue mascot" },
                { "id": "elias", "name": "Elias", "role": "main", "description": "Silver hair man" }
            ],
            "scenes": [
                {
                    "scene_number": 1,
                    "title": "Cảnh không nhân vật",
                    "narration": "Cánh đồng hoàng hôn im lùm.",
                    "environment": "Field at sunset",
                    "characters": [],
                    "duration_hint": 5.0
                },
                {
                    "scene_number": 2,
                    "title": "Cảnh nhiều nhân vật",
                    "narration": "Elias gặp Inky.",
                    "environment": "Door step",
                    "characters": [
                        { "character_id": "inky", "action": "vẫy tay", "expression": "vui" },
                        { "character_id": "elias", "action": "bước tới", "expression": "ngạc nhiên" }
                    ],
                    "duration_hint": 7.0
                }
            ]
        }
        plan = parse_multi_character_storyboard(raw_json, "Bobo và Elias")
        self.assertEqual(len(plan.characters), 2)
        self.assertEqual(len(plan.scenes), 2)
        self.assertEqual(len(plan.scenes[0].characters), 0)
        self.assertEqual(len(plan.scenes[1].characters), 2)

    def test_render_manifest_and_zip(self):
        raw_json = {
            "topic": "Test Project",
            "hook": "Hook",
            "visual_style": "Clean",
            "characters": [],
            "scenes": [
                { "scene_number": 1, "title": "Scene 1", "narration": "Test narration", "duration_hint": 6.0 }
            ]
        }
        plan = parse_multi_character_storyboard(raw_json, "Test Project")
        tmp_dir = Path("/tmp/test_inky_project")
        tmp_dir.mkdir(parents=True, exist_ok=True)
        
        manifest_p = build_render_manifest(plan, 1920, 1080, tmp_dir)
        self.assertTrue(manifest_p.exists())
        
        zip_p = create_project_zip(tmp_dir)
        self.assertTrue(zip_p.exists())

if __name__ == "__main__":
    unittest.main()
