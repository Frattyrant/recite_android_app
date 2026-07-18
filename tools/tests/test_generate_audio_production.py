import json
import shutil
import tempfile
import unittest
import wave
from pathlib import Path

from tools.audio_profiles import PronunciationOverrides
from tools.audio_production import (
    ModelAudioSource,
    plan_production,
    transitional_speech_plan_sha256,
)
from tools.generate_audio_production import _audit, generate_staged_pack


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
    def test_model_correction_is_hash_bound_and_recorded(self):
        words = [{
            "id": "robot",
            "category": "mechanical",
            "kind": "TERM",
            "english": "robot",
            "phonetic": "/ˈɹoʊbɑt/",
        }]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            correction = root / "robot.wav"
            write_fixture_wav("robot", correction)
            import hashlib
            source = ModelAudioSource(
                text="robot",
                path=correction,
                model_name="Kokoro-82M",
                model_version="1.0",
                model_source_url="https://huggingface.co/hexgrad/Kokoro-82M",
                model_license="Apache-2.0",
                voice="af_heart",
                sha256=hashlib.sha256(correction.read_bytes()).hexdigest(),
            )
            plans = plan_production(
                words,
                PronunciationOverrides.empty(),
                model_audio={"robot": source},
            )
            report = generate_staged_pack(
                plans=plans,
                words=words,
                output=root / "production",
                content_hash="content-hash",
                profile={"name": "en_US-ljspeech-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=write_fixture_ogg,
                probe=probe_fixture,
                decode_human=lambda source_path, target: shutil.copyfile(source_path, target),
            )

        entry = report["entries"]["robot"]
        self.assertEqual("model", entry["sourceType"])
        self.assertEqual("Kokoro-82M", entry["modelSource"]["modelName"])
        self.assertEqual(source.sha256, entry["modelSource"]["sourceSha256"])

    def test_audit_prefers_long_meeting_and_business_sentences(self):
        words = [
            {"id": "meet-short", "category": "meeting", "english": "Thanks."},
            {"id": "meet-long", "category": "meeting", "english": "Could you explain the inspection result again?"},
            {"id": "bus-short", "category": "business", "english": "Approved."},
            {"id": "bus-long", "category": "business", "english": "Please confirm the commercial terms before Friday."},
        ]
        entries = {
            word["id"]: {"path": f"audio/{word['id']}.ogg"}
            for word in words
        }

        audit = _audit(words, entries)
        sample_ids = {item["id"] for item in audit["samples"]}

        self.assertIn("meet-long", sample_ids)
        self.assertIn("bus-long", sample_ids)

    def test_single_expression_uses_only_complete_audio(self):
        words = [{
            "id": "mee_0001_x",
            "category": "meeting",
            "kind": "PHRASE",
            "english": "Can you repeat that?",
        }]
        plans = plan_production(words, PronunciationOverrides.empty())

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "audio-production-v2.3"
            report = generate_staged_pack(
                plans=plans,
                words=words,
                output=root,
                content_hash="content-hash",
                profile={"name": "en_US-ljspeech-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=write_fixture_ogg,
                probe=probe_fixture,
            )

            self.assertNotIn("segments", report["entries"]["mee_0001_x"])
            self.assertFalse((root / "audio/variants/mee_0001_x_00.ogg").exists())

    def test_manifest_separates_spoken_hint_from_expected_transcript(self):
        words = [{
            "id": "mec_catia",
            "category": "mechanical",
            "kind": "TERM",
            "english": "CATIA",
            "phonetic": "/k\u0259\u02c8ti\u02d0\u0259/",
        }]
        overrides = PronunciationOverrides.from_json(
            {
                "schemaVersion": 1,
                "wordId": {},
                "exactText": {
                    "CATIA": {
                        "spokenText": "kuh TEE uh",
                        "auditText": "CATIA",
                        "type": "pronunciationHint",
                    }
                },
            }
        )
        plans = plan_production(words, overrides)

        with tempfile.TemporaryDirectory() as directory:
            report = generate_staged_pack(
                plans=plans,
                words=words,
                output=Path(directory) / "audio-production-v2.31",
                content_hash="content-hash",
                profile={"name": "en_US-ljspeech-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=write_fixture_ogg,
                probe=probe_fixture,
            )

        entry = report["entries"]["mec_catia"]
        self.assertEqual("kuh TEE uh.", entry["spokenText"])
        self.assertEqual("CATIA", entry["expectedTranscript"])

    def test_writes_complete_variants_manifest_and_audit(self):
        words = [
            {
                "id": "mec_0001_x",
                "category": "mechanical",
                "kind": "TERM",
                "english": "fixture；jig",
                "chinese": "夹具",
                "phonetic": "/ˈfɪkstʃɝ/； /ˈdʒɪɡ/",
            },
            {
                "id": "mee_0001_x",
                "category": "meeting",
                "kind": "PHRASE",
                "english": "Can you repeat that?",
                "chinese": "请重复",
                "phonetic": "/kæn ju ɹɪˈpit ðæt/",
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
                profile={"name": "en_US-ljspeech-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=write_fixture_ogg,
                probe=probe_fixture,
            )

            self.assertEqual(2, report["entryCount"])
            self.assertEqual(3, report["schemaVersion"])
            entry = report["entries"]["mec_0001_x"]
            self.assertEqual(500, entry["pauseBetweenSegmentsMs"])
            self.assertEqual(["fixture", "jig"], [item["text"] for item in entry["segments"]])
            self.assertEqual(["piper", "piper"], [item["sourceType"] for item in entry["segments"]])
            self.assertEqual("/ˈfɪkstʃɝ/", entry["segments"][0]["expectedIpa"])
            self.assertTrue((root / "audio/mec_0001_x.ogg").is_file())
            self.assertTrue((root / "audio/variants/mec_0001_x_01.ogg").is_file())
            manifest = json.loads((root / "audio_manifest_v1.json").read_text(encoding="utf-8"))
            self.assertEqual("content-hash", manifest["contentSha256"])
            self.assertEqual("en_US-ljspeech-high", manifest["profile"]["name"])
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
                profile={"name": "en_US-ljspeech-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=tracked_encode,
                probe=probe_fixture,
            )
            generate_staged_pack(**arguments)
            first_run_calls = len(encode_calls)
            generate_staged_pack(**arguments)

            self.assertEqual(first_run_calls, len(encode_calls))

    def test_transitional_null_model_hash_is_migrated_without_reencoding(self):
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
            root = Path(directory) / "audio-production-v2.32"
            arguments = dict(
                plans=plans,
                words=words,
                output=root,
                content_hash="content-hash",
                profile={"name": "en_US-ljspeech-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=tracked_encode,
                probe=probe_fixture,
            )
            generate_staged_pack(**arguments)
            manifest_path = root / "audio_manifest_v1.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["entries"]["mec_0001_x"]["speechPlanSha256"] = (
                transitional_speech_plan_sha256(plans[0])
            )
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            first_run_calls = len(encode_calls)

            report = generate_staged_pack(**arguments)

            self.assertEqual(first_run_calls, len(encode_calls))
            self.assertNotEqual(
                transitional_speech_plan_sha256(plans[0]),
                report["entries"]["mec_0001_x"]["speechPlanSha256"],
            )

    def test_unchanged_speech_plan_survives_content_metadata_update(self):
        words = [{
            "id": "mec_0001_x",
            "category": "mechanical",
            "kind": "TERM",
            "english": "fixture",
            "phonetic": "/ˈfɪkstʃɝ/",
        }]
        plans = plan_production(words, PronunciationOverrides.empty())
        encode_calls = []

        def tracked_encode(source: Path, target: Path, stable_key: str) -> None:
            encode_calls.append(stable_key)
            write_fixture_ogg(source, target, stable_key)

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "audio-production-v2.31"
            common = dict(
                plans=plans,
                words=words,
                output=root,
                profile={"name": "en_US-ljspeech-high", "bitRateKbps": 40},
                synthesize=write_fixture_wav,
                encode=tracked_encode,
                probe=probe_fixture,
            )
            generate_staged_pack(content_hash="old-content", **common)
            first_run_calls = len(encode_calls)
            updated_words = [dict(words[0], phonetic="/fɪkstʃɚ/")]
            updated_plans = plan_production(updated_words, PronunciationOverrides.empty())
            report = generate_staged_pack(
                content_hash="new-content",
                plans=updated_plans,
                words=updated_words,
                **{key: value for key, value in common.items() if key not in {"plans", "words"}},
            )

            self.assertEqual(first_run_calls, len(encode_calls))
            self.assertEqual("new-content", report["contentSha256"])
            self.assertEqual("/fɪkstʃɚ/", report["entries"]["mec_0001_x"]["expectedIpa"])


if __name__ == "__main__":
    unittest.main()
