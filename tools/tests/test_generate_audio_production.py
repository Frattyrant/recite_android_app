import json
import tempfile
import unittest
import wave
from pathlib import Path

from tools.audio_profiles import PronunciationOverrides
from tools.audio_production import plan_production
from tools.generate_audio_production import generate_staged_pack


def write_fixture_wav(text: str, target: Path) -> None:
    frames = (b"\x01\x00" * 2205) if text else b""
    with wave.open(str(target), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(22050)
        output.writeframes(frames)


def write_fixture_ogg(source: Path, target: Path, stable_key: str) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(b"OggS" + stable_key.encode("utf-8") + source.read_bytes())


def probe_fixture(path: Path) -> dict:
    return {
        "durationSeconds": 0.6,
        "codec": "opus",
        "channels": 1,
        "sampleRate": 48000,
        "maxVolumeDb": -3.0,
        "meanVolumeDb": -18.0,
    }


class GenerateAudioProductionTest(unittest.TestCase):
    def test_writes_complete_variants_manifest_and_audit(self):
        words = [
            {
                "id": "mec_0001_x",
                "category": "mechanical",
                "kind": "TERM",
                "english": "fixture；jig",
                "chinese": "夹具",
            },
            {
                "id": "mee_0001_x",
                "category": "meeting",
                "kind": "PHRASE",
                "english": "Can you repeat that?",
                "chinese": "请重复",
            },
        ]
        plans = plan_production(words, PronunciationOverrides.empty())

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "audio-production-v2.3"
            report = generate_staged_pack(
                plans=plans,
                words=words,
                output=root,
                content_hash="content-hash",
                profile={"name": "en_US-lessac-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=write_fixture_ogg,
                probe=probe_fixture,
            )

            self.assertEqual(2, report["entryCount"])
            entry = report["entries"]["mec_0001_x"]
            self.assertEqual(500, entry["pauseBetweenSegmentsMs"])
            self.assertEqual(["fixture", "jig"], [item["displayText"] for item in entry["segments"]])
            self.assertTrue((root / "audio/mec_0001_x.ogg").is_file())
            self.assertTrue((root / "audio/variants/mec_0001_x_01.ogg").is_file())
            manifest = json.loads((root / "audio_manifest_v1.json").read_text(encoding="utf-8"))
            self.assertEqual("content-hash", manifest["contentSha256"])
            self.assertEqual("en_US-lessac-high", manifest["profile"]["name"])
            audit = json.loads((root / "release_audit.json").read_text(encoding="utf-8"))
            self.assertIn("mec_0001_x", {item["id"] for item in audit["samples"]})

    def test_second_run_reuses_hash_verified_staged_entries(self):
        words = [{
            "id": "mec_0001_x",
            "category": "mechanical",
            "kind": "TERM",
            "english": "fixture",
        }]
        plans = plan_production(words, PronunciationOverrides.empty())
        encode_calls = []

        def tracked_encode(source: Path, target: Path, stable_key: str) -> None:
            encode_calls.append(stable_key)
            write_fixture_ogg(source, target, stable_key)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "audio-production-v2.3"
            arguments = dict(
                plans=plans,
                words=words,
                output=root,
                content_hash="content-hash",
                profile={"name": "en_US-lessac-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=tracked_encode,
                probe=probe_fixture,
            )
            generate_staged_pack(**arguments)
            first_run_calls = len(encode_calls)
            generate_staged_pack(**arguments)

            self.assertEqual(first_run_calls, len(encode_calls))


if __name__ == "__main__":
    unittest.main()
