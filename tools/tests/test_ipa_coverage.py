import json
import tempfile
import unittest
from pathlib import Path

from tools.pronunciation.build_ipa_coverage import apply_ipa
from tools.pronunciation.ipa_lexicon import IpaLexicon, resolve_entry_phonetic


class IpaCoverageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        source = Path(self.temp.name) / "en_US.txt"
        source.write_text(
            "\n".join(
                (
                    "fixture\t/ˈfɪkstʃɝ/",
                    "jig\t/ˈdʒɪɡ/",
                    "mylar\t/ˈmaɪˌɫɑɹ/",
                    "body\t/ˈbɑdi/",
                    "in\t/ˈɪn/, /ɪn/",
                    "white\t/ˈwaɪt/",
                    "gun\t/ˈɡən/",
                    "one\t/ˈwʌn/",
                    "hundred\t/ˈhʌndɹəd/",
                    "fifty\t/ˈfɪfti/",
                    "millimeters\t/ˈmɪləˌmitɝz/",
                )
            )
            + "\n",
            encoding="utf-8",
        )
        self.lexicon = IpaLexicon.from_tsv(source)

    def test_dictionary_pronunciations_use_general_american_stress(self):
        self.assertEqual("/ˈfɪkstʃɝ/", self.lexicon.resolve_text("fixture").display)
        self.assertEqual("/ˈdʒɪɡ/", self.lexicon.resolve_text("jig").display)
        self.assertEqual("/ˈmaɪˌɫɑɹ/", self.lexicon.resolve_text("mylar").display)

    def test_acronyms_and_professional_overrides_are_explicit(self):
        overrides = {
            "PLC": "/ˌpiː ɛl ˈsiː/",
            "GD&T": "/ˌdʒiː diː ən ˈtiː/",
        }
        lexicon = self.lexicon.with_overrides(overrides)

        self.assertEqual("/ˌpiː ɛl ˈsiː/", lexicon.resolve_text("PLC").display)
        self.assertEqual("/ˌdʒiː diː ən ˈtiː/", lexicon.resolve_text("GD&T").display)

    def test_compound_pronunciation_overrides_match_audio_plan(self):
        path = Path(__file__).resolve().parents[2] / "tools/pronunciation/ipa_overrides.json"
        entries = json.loads(path.read_text(encoding="utf-8"))["entries"]

        self.assertEqual("/ˈkæt ˌpɑɹt/", entries["CATpart"])
        self.assertEqual("/ˈɛks ˌhoʊm/", entries["Xhome"])
        self.assertEqual("/ˈfiːldˌbʌs/", entries["Fieldbus"])
        self.assertEqual(entries["Fieldbus"], entries["fieldbus"])
        self.assertEqual("/ˈbʌsˌplʌɡ/", entries["busplug"])

    def test_verified_supplier_names_have_reviewed_general_american_ipa(self):
        path = Path(__file__).resolve().parents[2] / "tools/pronunciation/ipa_overrides.json"
        entries = json.loads(path.read_text(encoding="utf-8"))["entries"]

        self.assertEqual("/ˈbaɪɡə/", entries["BAIGE"])
        self.assertEqual("/ˈbɑːlʊf/", entries["Balluff"])
        self.assertEqual("/ˌhʊŋˈbaɪ/", entries["HONGBAI"])
        self.assertEqual("/ˈdoʊtræn/", entries["Dotran"])
        self.assertEqual("/ˈkɑɡnɛks/", entries["Cognex"])
        self.assertEqual("/ˈtʊŋkɚz/", entries["Tuenkers"])
        self.assertEqual("/ˈkiːɛns/", entries["Keyence"])
        self.assertEqual("/ˈblaɪhɚt/", entries["Bleichert"])
        self.assertEqual("/ˈpiːæb/", entries["PIAB"])

    def test_reviewed_compounds_have_clear_general_american_ipa(self):
        path = Path(__file__).resolve().parents[2] / "tools/pronunciation/ipa_overrides.json"
        entries = json.loads(path.read_text(encoding="utf-8"))["entries"]

        self.assertEqual("/ˈpriːˌdrɑp/", entries["predrop"])
        self.assertEqual("/ˈpriːˌpɪk/", entries["prepick"])
        self.assertEqual("/ˈriːˌspɑt/", entries["respot"])
        self.assertEqual("/ˈbændˌpæs/", entries["bandpass"])
        self.assertEqual("/ˌæntiˈrʌst/", entries["antirust"])
        self.assertEqual("/ˈdaʊnˌhoʊldɚ/", entries["downholder"])
        self.assertEqual("/ʌnˈstækɚ/", entries["Unstacker"])
        self.assertEqual("/ˈbʌzˌbɑɹ/", entries["busbar"])
        self.assertEqual("/ˈskruːˌdraɪvɚ/", entries["screwdriver"])
        self.assertEqual("/ˈwɛtɪŋ/", entries["wetting"])

    def test_entry_phonetic_has_one_group_per_audio_variant(self):
        word = {"english": "fixture；jig", "kind": "TERM"}

        result = resolve_entry_phonetic(word, self.lexicon)

        self.assertEqual("/ˈfɪkstʃɝ/； /ˈdʒɪɡ/", result.display)
        self.assertEqual(["fixture", "jig"], result.variants)
        self.assertEqual(["ipa-dict", "ipa-dict"], result.sources)

    def test_unknown_token_uses_injected_fallback_and_is_audited(self):
        lexicon = self.lexicon.with_fallback(lambda text: "tˈɛst")

        result = lexicon.resolve_text("unlisted")

        self.assertEqual("/tˈɛst/", result.display)
        self.assertEqual("espeak-fallback", result.source)
        self.assertEqual(["unlisted"], result.fallback_tokens)

    def test_measurement_numbers_are_expanded_before_ipa_lookup(self):
        result = self.lexicon.resolve_text("150 mm")

        self.assertEqual(
            "/ˈwʌn ˈhʌndɹəd ˈfɪfti ˈmɪləˌmitɝz/",
            result.display,
        )
        self.assertEqual("ipa-dict", result.source)

    def test_apply_ipa_replaces_old_values_and_writes_coverage_audit(self):
        content = {
            "contentVersion": "test",
            "words": [
                {"id": "one", "english": "fixture；jig", "kind": "TERM", "phonetic": "/wrong/"},
                {"id": "two", "english": "mylar", "kind": "TERM", "phonetic": "/wrong/"},
            ],
        }

        updated, report, frozen = apply_ipa(content, self.lexicon)

        self.assertEqual("/ˈfɪkstʃɝ/； /ˈdʒɪɡ/", updated["words"][0]["phonetic"])
        self.assertEqual("/ˈmaɪˌɫɑɹ/", updated["words"][1]["phonetic"])
        self.assertEqual(2, report["entryCount"])
        self.assertEqual(0, report["fallbackEntryCount"])
        self.assertEqual("/ˈfɪkstʃɝ/", frozen["fixture"]["display"])


if __name__ == "__main__":
    unittest.main()
