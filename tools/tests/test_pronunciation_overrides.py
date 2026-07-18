import json
import tempfile
import unittest
from pathlib import Path

from tools.audio_profiles import (
    LJSPEECH_HIGH,
    LESSAC_HIGH,
    PronunciationOverrides,
    load_pronunciation_overrides,
    resolve_audio_texts,
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

    def test_ljspeech_high_is_the_approved_40k_production_profile(self):
        self.assertEqual("en_US-ljspeech-high", LJSPEECH_HIGH.name)
        self.assertEqual(
            "5d4f08ba6a2a48c44592eed3ce56bf85e9de3dd4e20df90541ae68a8310c029a",
            LJSPEECH_HIGH.model_sha256,
        )
        self.assertEqual(40, LJSPEECH_HIGH.bit_rate_kbps)
        self.assertEqual("audio", LJSPEECH_HIGH.application)
        self.assertEqual(48_000, LJSPEECH_HIGH.encoded_sample_rate)

    def test_lessac_profile_remains_available_only_for_legacy_audits(self):
        self.assertEqual("en_US-lessac-high", LESSAC_HIGH.name)

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

    def test_token_rule_expands_initialism_inside_a_longer_expression(self):
        rules = PronunciationOverrides.from_json(
            {
                "schemaVersion": 1,
                "wordId": {},
                "exactText": {},
                "tokenText": {
                    "PLC": {"spokenText": "P L C", "type": "initialism"},
                },
            }
        )

        spoken, key = resolve_spoken_text(
            {"id": "electrical_0002", "audioText": "Security PLC"},
            "Security PLC",
            rules,
        )

        self.assertEqual("Security P L C", spoken)
        self.assertEqual("tokenText:PLC", key)

    def test_token_rule_keeps_standalone_initialism_as_spaced_letters(self):
        rules = PronunciationOverrides.from_json(
            {
                "schemaVersion": 1,
                "wordId": {},
                "exactText": {},
                "tokenText": {
                    "ABB": {"spokenText": "A B B", "type": "initialism"},
                },
            }
        )

        spoken, key = resolve_spoken_text(
            {"id": "robot-brand", "audioText": "ABB", "primaryEnglish": "ABB"},
            "ABB",
            rules,
        )

        self.assertEqual("A B B", spoken)
        self.assertEqual("tokenText:ABB", key)

    def test_audit_text_keeps_dictionary_form_when_spoken_text_is_a_hint(self):
        rules = PronunciationOverrides.from_json(
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

        spoken, audit, key = resolve_audio_texts(
            {"id": "mechanical_catia", "audioText": "CATIA"},
            "CATIA",
            rules,
        )

        self.assertEqual("kuh TEE uh", spoken)
        self.assertEqual("CATIA", audit)
        self.assertEqual("exactText:CATIA", key)

    def test_verified_supplier_names_have_explicit_pronunciation_plans(self):
        rules = load_pronunciation_overrides(
            PROJECT_ROOT / "tools/audio/pronunciation_overrides.json"
        )
        expected = {
            "BAIGE": "BY guh",
            "Balluff": "BAH loof",
            "HONGBAI": "hong bye",
            "Dotran": "DOH tran",
            "Cognex": "COG necks",
            "Tuenkers": "TUNK ers",
            "Keyence": "KEY ence",
            "Bleichert": "BLYE hert",
            "PIAB": "PEE ab",
        }

        for name, spoken_text in expected.items():
            with self.subTest(name=name):
                spoken, audit, key = resolve_audio_texts(
                    {"id": f"supplier-{name}", "audioText": name},
                    name,
                    rules,
                )
                self.assertEqual(spoken_text, spoken)
                self.assertEqual(name, audit)
                self.assertEqual(f"exactText:{name}", key)

    def test_compound_terms_are_synthesized_as_clear_words(self):
        rules = load_pronunciation_overrides(
            PROJECT_ROOT / "tools/audio/pronunciation_overrides.json"
        )
        expected = {
            "predrop": "pre drop",
            "prepick": "pre pick",
            "respot": "re spot",
            "bandpass": "band pass",
            "antirust": "anti rust",
            "downholder": "down holder",
            "Unstacker": "un stacker",
            "busbar": "bus bar",
            "screwdriver": "screw driver",
            "squeeze-out": "squeeze out",
            "Library(sys root)": "Library system root",
            "deviation": "dee vee AY shun",
        }

        for term, spoken_text in expected.items():
            with self.subTest(term=term):
                spoken, audit, key = resolve_audio_texts(
                    {"id": f"compound-{term}", "audioText": term},
                    term,
                    rules,
                )
                self.assertEqual(spoken_text, spoken)
                expected_audit = (
                    "Library system root"
                    if term == "Library(sys root)"
                    else term
                )
                self.assertEqual(expected_audit, audit)
                self.assertEqual(f"exactText:{term}", key)

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
