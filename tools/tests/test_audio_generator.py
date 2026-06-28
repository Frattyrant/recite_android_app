import hashlib
import tempfile
import unittest
from pathlib import Path

from tools.generate_audio import can_skip, spoken_text, spoken_text_sha256


class AudioGeneratorTest(unittest.TestCase):
    def test_non_english_source_fails_fast(self):
        word = {
            "id": "fixture",
            "audioText": "纯中文术语",
            "primaryEnglish": "纯中文术语",
        }
        with self.assertRaisesRegex(ValueError, "no pronounceable English text"):
            spoken_text(word)

    def test_single_letter_after_normalization_fails_fast(self):
        word = {
            "id": "junk",
            "audioText": "C枪",
            "primaryEnglish": "C枪",
        }
        with self.assertRaisesRegex(ValueError, "meaningful English token"):
            spoken_text(word)

    def test_stale_spoken_text_cannot_be_skipped(self):
        provenance = {"schemaVersion": 1, "modelSha256": "model"}
        word = {
            "id": "fixture",
            "audioText": "fixture",
            "primaryEnglish": "fixture",
            "audioAsset": "audio/fixture.ogg",
        }
        with tempfile.TemporaryDirectory() as directory:
            audio = Path(directory) / "fixture.ogg"
            audio.write_bytes(b"OggS" + b"a" * 300)
            manifest = {
                "provenance": provenance,
                "entries": {
                    "fixture": {
                        "id": "fixture",
                        "path": "audio/fixture.ogg",
                        "spokenTextSha256": spoken_text_sha256(word),
                        "audioSha256": hashlib.sha256(audio.read_bytes()).hexdigest(),
                        "bytes": audio.stat().st_size,
                    }
                },
            }
            self.assertTrue(can_skip(word, audio, manifest, provenance))
            word["audioText"] = "updated fixture"
            self.assertFalse(can_skip(word, audio, manifest, provenance))

    def test_swapped_audio_file_cannot_be_skipped(self):
        provenance = {"schemaVersion": 1, "modelSha256": "model"}
        words = [
            {
                "id": name,
                "audioText": name,
                "primaryEnglish": name,
                "audioAsset": f"audio/{name}.ogg",
            }
            for name in ("fixture", "clamp")
        ]
        with tempfile.TemporaryDirectory() as directory:
            paths = {
                word["id"]: Path(directory) / f"{word['id']}.ogg" for word in words
            }
            paths["fixture"].write_bytes(b"OggS" + b"a" * 300)
            paths["clamp"].write_bytes(b"OggS" + b"b" * 300)
            manifest = {
                "provenance": provenance,
                "entries": {
                    word["id"]: {
                        "id": word["id"],
                        "path": word["audioAsset"],
                        "spokenTextSha256": spoken_text_sha256(word),
                        "audioSha256": hashlib.sha256(
                            paths[word["id"]].read_bytes()
                        ).hexdigest(),
                        "bytes": paths[word["id"]].stat().st_size,
                    }
                    for word in words
                },
            }
            fixture_bytes = paths["fixture"].read_bytes()
            paths["fixture"].write_bytes(paths["clamp"].read_bytes())
            paths["clamp"].write_bytes(fixture_bytes)
            self.assertFalse(
                can_skip(words[0], paths["fixture"], manifest, provenance)
            )
            self.assertFalse(can_skip(words[1], paths["clamp"], manifest, provenance))

    def test_mismatched_model_provenance_cannot_be_skipped(self):
        word = {
            "id": "fixture",
            "audioText": "fixture",
            "primaryEnglish": "fixture",
            "audioAsset": "audio/fixture.ogg",
        }
        current = {"schemaVersion": 1, "modelSha256": "current"}
        stale = {"schemaVersion": 1, "modelSha256": "stale"}
        with tempfile.TemporaryDirectory() as directory:
            audio = Path(directory) / "fixture.ogg"
            audio.write_bytes(b"OggS" + b"a" * 300)
            manifest = {
                "provenance": stale,
                "entries": {
                    "fixture": {
                        "id": "fixture",
                        "path": word["audioAsset"],
                        "spokenTextSha256": spoken_text_sha256(word),
                        "audioSha256": hashlib.sha256(audio.read_bytes()).hexdigest(),
                        "bytes": audio.stat().st_size,
                    }
                },
            }
            self.assertFalse(can_skip(word, audio, manifest, current))


if __name__ == "__main__":
    unittest.main()
