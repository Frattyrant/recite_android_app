import json
import tempfile
import unittest
from pathlib import Path

from tools.audio_profiles import (
    LESSAC_HIGH,
    PronunciationOverrides,
    load_pronunciation_overrides,
    resolve_spoken_text,
)

PROJECT_ROOT = Path(__file__).resolve().parents[2]


class PronunciationOverridesTest(unittest.TestCase):
    def test_single_letter_i_uses_dictionary_pronunciation(self):
        overrides = load_pronunciation_overrides(
            PROJECT_ROOT / "tools/audio/pronunciation_overrides.json"
        )

        spoken, key = resolve_spoken_text(
            {"id": "mec_io#00", "audioText": "I", "primaryEnglish": "I"},
            "I",
            overrides,
        )

        self.assertEqual("eye", spoken)
        self.assertEqual("exactText:I", key)

    def test_lessac_high_profile_uses_40k_audio(self):
        self.assertEqual("en_US-lessac-high", LESSAC_HIGH.name)
        self.assertEqual(40, LESSAC_HIGH.bit_rate_kbps)
        self.assertEqual("audio", LESSAC_HIGH.application)
        self.assertEqual(48_000, LESSAC_HIGH.encoded_sample_rate)

    def test_word_id_rule_wins_over_exact_text_rule(self):
        rules = PronunciationOverrides.from_json(
            {
                "schemaVersion": 1,
                "wordId": {
                    "mechanical_0050": {
                        "spokenText": "G D and T",
                        "type": "initialism",
                    }
                },
                "exactText": {
                    "GD&T": {
                        "spokenText": "geometric dimensioning and tolerancing",
                        "type": "symbolExpansion",
                    }
                },
            }
        )

        spoken, key = resolve_spoken_text(
            {"id": "mechanical_0050", "audioText": "GD&T"},
            "GD&T",
            rules,
        )

        self.assertEqual("G D and T", spoken)
        self.assertEqual("wordId:mechanical_0050", key)

    def test_exact_text_rule_applies_to_variant(self):
        rules = PronunciationOverrides.from_json(
            {
                "schemaVersion": 1,
                "wordId": {},
                "exactText": {
                    "PLC": {"spokenText": "P L C", "type": "initialism"}
                },
            }
        )

        spoken, key = resolve_spoken_text(
            {"id": "electrical_0001", "audioText": "PLC"},
            "PLC",
            rules,
        )

        self.assertEqual("P L C", spoken)
        self.assertEqual("exactText:PLC", key)

    def test_empty_spoken_text_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "non-empty spokenText"):
            PronunciationOverrides.from_json(
                {
                    "schemaVersion": 1,
                    "wordId": {},
                    "exactText": {
                        "PLC": {"spokenText": "", "type": "initialism"}
                    },
                }
            )

    def test_loader_reads_utf8_json(self):
        payload = {
            "schemaVersion": 1,
            "wordId": {},
            "exactText": {
                "CMM": {
                    "spokenText": "C M M",
                    "type": "initialism",
                    "note": "坐标测量机",
                }
            },
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "overrides.json"
            path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            rules = load_pronunciation_overrides(path)

        self.assertEqual("C M M", rules.exact_text["CMM"].spoken_text)


if __name__ == "__main__":
    unittest.main()
