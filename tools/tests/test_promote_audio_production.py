import json
import tempfile
import unittest
from pathlib import Path

from tools.promote_audio_production import promote_production


class PromoteAudioProductionTest(unittest.TestCase):
    def test_rejects_unvalidated_pack_without_touching_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            staging = root / "staging"
            staging.mkdir()
            assets = root / "assets/audio"
            assets.mkdir(parents=True)
            existing = assets / "old.ogg"
            existing.write_bytes(b"old")
            manifest = root / "assets/content/audio_manifest_v1.json"
            manifest.parent.mkdir(parents=True)
            manifest.write_text("old", encoding="utf-8")

            with self.assertRaisesRegex(RuntimeError, "validation"):
                promote_production(staging, assets, manifest, expected_count=2)

            self.assertEqual(b"old", existing.read_bytes())
            self.assertEqual("old", manifest.read_text(encoding="utf-8"))

    def test_promotes_validated_audio_and_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            staging = root / "staging"
            staged_audio = staging / "audio"
            staged_audio.mkdir(parents=True)
            (staged_audio / "new.ogg").write_bytes(b"new")
            (staging / "audio_manifest_v1.json").write_text("new manifest", encoding="utf-8")
            (staging / "validation_report.json").write_text(
                json.dumps({"passed": True, "validatedEntries": 2}), encoding="utf-8"
            )
            assets = root / "assets/audio"
            assets.mkdir(parents=True)
            (assets / "old.ogg").write_bytes(b"old")
            manifest = root / "assets/content/audio_manifest_v1.json"
            manifest.parent.mkdir(parents=True)
            manifest.write_text("old manifest", encoding="utf-8")

            promote_production(staging, assets, manifest, expected_count=2)

            self.assertEqual(["new.ogg"], [path.name for path in assets.iterdir()])
            self.assertEqual("new manifest", manifest.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
