import json
import unittest
from pathlib import Path

from tools.generate_audio import sha256, spoken_text_sha256
from tools.generate_variant_audio import (
    legacy_term_variants,
    raw_variants,
    segment_plan_sha256,
)


PROJECT_ROOT = Path(__file__).resolve().parents[2]


class AudioManifestTest(unittest.TestCase):
    def test_every_word_has_one_nonempty_ogg(self):
        data = json.loads(
            (
                PROJECT_ROOT / "app/src/main/assets/content/words_v1.json"
            ).read_text(encoding="utf-8")
        )
        files = list((PROJECT_ROOT / "app/src/main/assets/audio").glob("*.ogg"))
        self.assertEqual(2704, len(files))
        expected = {Path(word["audioAsset"]).name for word in data["words"]}
        self.assertEqual(expected, {path.name for path in files})
        self.assertTrue(all(path.stat().st_size > 256 for path in files))

    def test_build_manifest_binds_every_word_to_its_audio_bytes(self):
        words = json.loads(
            (
                PROJECT_ROOT / "app/src/main/assets/content/words_v1.json"
            ).read_text(encoding="utf-8")
        )["words"]
        manifest_path = (
            PROJECT_ROOT
            / "app/src/main/assets/content/audio_manifest_v1.json"
        )
        self.assertTrue(manifest_path.is_file())
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        self.assertEqual(1, manifest["schemaVersion"])
        entries = manifest["entries"]
        self.assertEqual({word["id"] for word in words}, set(entries))
        for word in words:
            path = PROJECT_ROOT / "app/src/main/assets" / word["audioAsset"]
            entry = entries[word["id"]]
            self.assertEqual(word["id"], entry["id"])
            self.assertEqual(word["audioAsset"], entry["path"])
            self.assertEqual(spoken_text_sha256(word), entry["spokenTextSha256"])
            self.assertEqual(
                segment_plan_sha256(
                    raw_variants(word["english"], word.get("kind", "TERM"))
                ),
                entry["segmentPlanSha256"],
            )
            self.assertEqual(path.stat().st_size, entry["bytes"])
            self.assertEqual(sha256(path), entry["audioSha256"])


    def test_legacy_whitespace_migration_covers_all_affected_terms(self):
        words = json.loads(
            (
                PROJECT_ROOT / "app/src/main/assets/content/words_v1.json"
            ).read_text(encoding="utf-8")
        )["words"]
        affected = {
            word["id"]
            for word in words
            if word.get("kind") == "TERM"
            and len(raw_variants(word["english"], "TERM")) == 1
            and len(legacy_term_variants(word["english"])) > 1
        }

        self.assertEqual(1413, len(affected))
        self.assertIn("mec_0001_0bc593b6bd35f925", affected)
        self.assertIn("ele_0001_bbb7b92b27e75187", affected)


if __name__ == "__main__":
    unittest.main()
