import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from tools.validate_audio_trial import validate_trial


def write_audio(path: Path, marker: bytes) -> dict:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"OggS" + marker * 300)
    return {
        "path": path.relative_to(path.parents[2]).as_posix(),
        "bytes": path.stat().st_size,
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }


class AudioTrialValidatorTest(unittest.TestCase):
    def build_fixture(self, root: Path, application: str = "audio") -> None:
        current = root / "a-current/audio/fixture.ogg"
        candidate = root / "b-high/audio/fixture.ogg"
        current_meta = write_audio(current, b"a")
        candidate_meta = write_audio(candidate, b"b")
        manifest = {
            "schemaVersion": 1,
            "entryCount": 1,
            "candidateProfile": {
                "name": "en_US-lessac-high",
                "bitRateKbps": 40,
                "application": application,
                "channels": 1,
                "encodedSampleRate": 48000,
            },
            "entries": [
                {
                    "id": "fixture",
                    "segments": [{"displayText": "fixture", "spokenText": "fixture"}],
                    "pauseBetweenSegmentsMs": 0,
                    "current": current_meta,
                    "candidate": candidate_meta,
                }
            ],
        }
        (root / "trial_report.json").write_text(
            json.dumps(manifest), encoding="utf-8"
        )

    @staticmethod
    def probe(_: Path) -> dict:
        return {
            "codec": "opus",
            "channels": 1,
            "sampleRate": 48000,
            "durationSeconds": 0.8,
            "maxVolumeDb": -4.0,
            "meanVolumeDb": -20.0,
        }

    def test_valid_bundle_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.build_fixture(root)
            report = validate_trial(root, self.probe)

        self.assertTrue(report["passed"])
        self.assertEqual(1, report["validatedEntries"])

    def test_wrong_candidate_application_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.build_fixture(root, application="voip")
            report = validate_trial(root, self.probe)

        self.assertFalse(report["passed"])
        self.assertIn("candidate profile", " ".join(report["errors"]))

    def test_changed_audio_hash_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.build_fixture(root)
            (root / "b-high/audio/fixture.ogg").write_bytes(b"OggS" + b"x" * 300)
            report = validate_trial(root, self.probe)

        self.assertFalse(report["passed"])
        self.assertIn("hash mismatch", " ".join(report["errors"]))

    def test_silent_audio_fails(self):
        def silent_probe(path: Path) -> dict:
            metrics = self.probe(path)
            metrics["maxVolumeDb"] = -90.0
            metrics["meanVolumeDb"] = -90.0
            return metrics

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.build_fixture(root)
            report = validate_trial(root, silent_probe)

        self.assertFalse(report["passed"])
        self.assertIn("silent", " ".join(report["errors"]))


if __name__ == "__main__":
    unittest.main()
