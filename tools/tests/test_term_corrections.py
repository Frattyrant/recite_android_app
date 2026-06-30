import json
import tempfile
import unittest
from pathlib import Path

try:
    from tools.term_corrections import apply_correction, load_corrections
except ModuleNotFoundError:
    apply_correction = None
    load_corrections = None


ROOT = Path(__file__).resolve().parents[2]
LEDGER = ROOT / "tools" / "data" / "term_corrections.json"


class TermCorrectionTest(unittest.TestCase):
    def test_correction_module_exists(self):
        self.assertIsNotNone(load_corrections)
        self.assertIsNotNone(apply_correction)

    def test_reviewed_ledger_has_three_evidence_backed_corrections(self):
        corrections = load_corrections(LEDGER)

        self.assertEqual(
            {
                ("mechanical", 13),
                ("mechanical", 14),
                ("electrical", 151),
            },
            set(corrections),
        )
        self.assertEqual(
            "inductive proximity switch",
            corrections[("mechanical", 13)].corrected_english,
        )
        self.assertEqual(
            "capacitive proximity switch",
            corrections[("mechanical", 14)].corrected_english,
        )
        self.assertIn(
            "consist of",
            corrections[("electrical", 151)].corrected_english,
        )

    def test_rejects_duplicate_identity_and_single_nonstandard_source(self):
        entry = {
            "category": "mechanical",
            "sourceIndex": 13,
            "originalEnglish": "inductance proximity switch",
            "correctedEnglish": "inductive proximity switch",
            "reason": "Use the standard adjective for the sensing principle.",
            "evidence": [
                {
                    "organization": "OMRON",
                    "url": "https://example.com/official",
                    "accessedOn": "2026-06-29",
                }
            ],
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "corrections.json"
            path.write_text(
                json.dumps(
                    {"schemaVersion": 1, "corrections": [entry, entry]},
                ),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(ValueError, "independent|duplicate"):
                load_corrections(path)

    def test_apply_preserves_stable_id_and_rejects_source_mismatch(self):
        correction = load_corrections(LEDGER)[("mechanical", 13)]
        record = {
            "id": "mec_0013_stable",
            "category": "mechanical",
            "sourceIndex": 13,
            "english": "inductance proximity switch",
            "primaryEnglish": "inductance proximity switch",
            "exampleEn": "old",
            "audioText": "old",
        }

        updated = apply_correction(record, correction)

        self.assertEqual("mec_0013_stable", updated["id"])
        self.assertEqual("inductive proximity switch", updated["english"])
        self.assertEqual("inductive proximity switch", updated["primaryEnglish"])
        self.assertEqual("inductive proximity switch", updated["exampleEn"])
        self.assertEqual("inductive proximity switch", updated["audioText"])
        with self.assertRaisesRegex(ValueError, "original English"):
            apply_correction({**record, "english": "different"}, correction)


if __name__ == "__main__":
    unittest.main()
