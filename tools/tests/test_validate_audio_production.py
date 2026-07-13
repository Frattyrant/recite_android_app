import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.validate_audio_production import validate_production
from tools.generate_variant_audio import segment_plan_sha256

HIGH_MODEL_SHA256 = "4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09"


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
        {"id": "one", "english": "fixture；jig", "kind": "TERM"},
        {"id": "two", "english": "PLC", "kind": "TERM"},
    ]
    one_segments = [
        {"index": 0, "text": "fixture", "spokenText": "fixture", "overrideKey": None,
         **audio_metadata(root, "audio/variants/one_00.ogg", 0.6)},
        {"index": 1, "text": "jig", "spokenText": "jig", "overrideKey": None,
         **audio_metadata(root, "audio/variants/one_01.ogg", 0.5)},
    ]
    entries = {
        "one": {"id": "one", **audio_metadata(root, "audio/one.ogg", 1.6),
                "pauseBetweenSegmentsMs": 500, "segments": one_segments,
                "segmentPlanSha256": segment_plan_sha256(["fixture", "jig"])},
        "two": {"id": "two", **audio_metadata(root, "audio/two.ogg", 0.7),
                "segmentPlanSha256": segment_plan_sha256(["PLC"])},
    }
    manifest = {
        "schemaVersion": 2,
        "contentSha256": "content",
        "entryCount": 2,
        "profile": {
            "name": "en_US-lessac-high", "modelSha256": HIGH_MODEL_SHA256, "bitRateKbps": 40,
            "application": "audio", "channels": 1, "encodedSampleRate": 48_000,
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


if __name__ == "__main__":
    unittest.main()
