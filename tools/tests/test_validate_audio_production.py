import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.audio_profiles import (
    LJSPEECH_DATASET_LICENSE,
    LJSPEECH_DATASET_URL,
    LJSPEECH_MODEL_CARD_SHA256,
    LJSPEECH_MODEL_CONFIG_SHA256,
    LJSPEECH_MODEL_SOURCE_URL,
)
from tools.validate_audio_production import validate_production
from tools.generate_variant_audio import segment_plan_sha256

HIGH_MODEL_SHA256 = "5d4f08ba6a2a48c44592eed3ce56bf85e9de3dd4e20df90541ae68a8310c029a"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def audio_metadata(root: Path, relative: str, duration: float) -> dict:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"OggS" + relative.encode("utf-8") + b"x" * 300)
    return {
        "path": relative,
        "bytes": path.stat().st_size,
        "audioSha256": sha256(path),
        "durationSeconds": duration,
        "codec": "opus",
        "channels": 1,
        "sampleRate": 48_000,
    }


def build_pack(root: Path) -> tuple[list[dict], dict[str, dict]]:
    words = [
        {"id": "one", "english": "fixture；jig", "kind": "TERM", "phonetic": "/ˈfɪkstʃɝ/； /ˈdʒɪɡ/"},
        {"id": "two", "english": "PLC", "kind": "TERM", "phonetic": "/ˌpiː ɛl ˈsiː/"},
    ]
    one_segments = [
        {"index": 0, "text": "fixture", "spokenText": "fixture", "expectedTranscript": "fixture", "overrideKey": None,
         "expectedIpa": "/ˈfɪkstʃɝ/", "sourceType": "piper",
         **audio_metadata(root, "audio/variants/one_00.ogg", 0.6)},
        {"index": 1, "text": "jig", "spokenText": "jig", "expectedTranscript": "jig", "overrideKey": None,
         "expectedIpa": "/ˈdʒɪɡ/", "sourceType": "piper",
         **audio_metadata(root, "audio/variants/one_01.ogg", 0.5)},
    ]
    entries = {
        "one": {"id": "one", **audio_metadata(root, "audio/one.ogg", 1.6),
                "sourceType": "piper", "expectedIpa": "/ˈfɪkstʃɝ/； /ˈdʒɪɡ/",
                "speechPlanSha256": "a" * 64,
                "pauseBetweenSegmentsMs": 500, "segments": one_segments,
                "segmentPlanSha256": segment_plan_sha256(["fixture", "jig"])},
        "two": {"id": "two", **audio_metadata(root, "audio/two.ogg", 0.7),
                "sourceType": "piper", "expectedIpa": "/ˌpiː ɛl ˈsiː/",
                "spokenText": "P L C", "expectedTranscript": "P L C", "overrideKey": "exactText:PLC",
                "speechPlanSha256": "b" * 64,
                "segmentPlanSha256": segment_plan_sha256(["PLC"])},
    }
    manifest = {
        "schemaVersion": 3,
        "contentSha256": "content",
        "entryCount": 2,
        "profile": {
            "name": "en_US-ljspeech-high", "modelSha256": HIGH_MODEL_SHA256, "bitRateKbps": 40,
            "application": "audio", "channels": 1, "encodedSampleRate": 48_000,
            "modelConfigSha256": LJSPEECH_MODEL_CONFIG_SHA256,
            "modelCardSha256": LJSPEECH_MODEL_CARD_SHA256,
            "modelSourceUrl": LJSPEECH_MODEL_SOURCE_URL,
            "dataset": "LJSpeech",
            "datasetUrl": LJSPEECH_DATASET_URL,
            "datasetLicense": LJSPEECH_DATASET_LICENSE,
        },
        "entries": entries,
    }
    (root / "audio_manifest_v1.json").write_text(
        json.dumps(manifest), encoding="utf-8"
    )
    return words, entries


def probe(path: Path) -> dict:
    name = path.name
    durations = {"one.ogg": 1.6, "one_00.ogg": 0.6, "one_01.ogg": 0.5,
                 "two.ogg": 0.7, "two_00.ogg": 0.7}
    return {
        "durationSeconds": durations[name], "codec": "opus", "channels": 1,
        "sampleRate": 48_000, "maxVolumeDb": -3.0, "meanVolumeDb": -18.0,
    }


class ValidateAudioProductionTest(unittest.TestCase):
    def test_open_model_correction_requires_complete_approved_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            words, entries = build_pack(root)
            entries["two"]["sourceType"] = "model"
            entries["two"]["modelSource"] = {
                "modelName": "Kokoro-82M",
                "modelVersion": "1.0",
                "modelSourceUrl": "https://huggingface.co/hexgrad/Kokoro-82M",
                "modelLicense": "Apache-2.0",
                "voice": "af_heart",
                "sourceSha256": "c" * 64,
            }
            manifest_path = root / "audio_manifest_v1.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["entries"] = entries
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            report = validate_production(root, words, probe, expected_count=2)
            self.assertTrue(report["passed"], report["errors"])

            entries["two"]["modelSource"]["modelLicense"] = "unknown"
            manifest["entries"] = entries
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            report = validate_production(root, words, probe, expected_count=2)
            self.assertFalse(report["passed"])
            self.assertTrue(any("model source license" in error for error in report["errors"]))

    def test_wrong_model_hash_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            words, _ = build_pack(root)
            manifest_path = root / "audio_manifest_v1.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["profile"]["modelSha256"] = "wrong"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            report = validate_production(root, words, probe, expected_count=2)

            self.assertFalse(report["passed"])
            self.assertTrue(any("model SHA-256" in error for error in report["errors"]))

    def test_valid_pack_passes_every_gate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            words, _ = build_pack(root)

            report = validate_production(root, words, probe, expected_count=2)

            self.assertTrue(report["passed"], report["errors"])
            self.assertEqual(2, report["validatedEntries"])

    def test_missing_variant_and_hash_mismatch_are_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            words, entries = build_pack(root)
            (root / entries["one"]["segments"][1]["path"]).unlink()
            (root / entries["two"]["path"]).write_bytes(b"changed")

            report = validate_production(root, words, probe, expected_count=2)

            self.assertFalse(report["passed"])
            self.assertTrue(any("missing audio file" in error for error in report["errors"]))
            self.assertTrue(any("byte count mismatch" in error for error in report["errors"]))

    def test_pause_mismatch_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            words, _ = build_pack(root)

            def short_complete(path: Path) -> dict:
                result = probe(path)
                if path.name == "one.ogg":
                    result["durationSeconds"] = 1.2
                return result

            report = validate_production(root, words, short_complete, expected_count=2)

            self.assertFalse(report["passed"])
            self.assertTrue(any("pause mismatch" in error for error in report["errors"]))

    def test_missing_expected_transcript_is_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            words, entries = build_pack(root)
            del entries["two"]["expectedTranscript"]
            manifest_path = root / "audio_manifest_v1.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["entries"] = entries
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

            report = validate_production(root, words, probe, expected_count=2)

            self.assertFalse(report["passed"])
            self.assertTrue(any("expected transcript" in error for error in report["errors"]))


if __name__ == "__main__":
    unittest.main()
