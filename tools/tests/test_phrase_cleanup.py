import copy
import unittest

from tools.phrase_cleanup import apply_phrase_cleanups


class PhraseCleanupTest(unittest.TestCase):
    def test_removes_source_annotation_without_changing_id_or_translation(self):
        content = {
            "words": [{
                "id": "cus_0001_stable",
                "kind": "PHRASE",
                "english": "Keep 100mm clearance. 100mm",
                "primaryEnglish": "Keep 100mm clearance. 100mm",
                "audioText": "Keep 100mm clearance. 100mm",
                "exampleEn": "Keep 100mm clearance. 100mm",
                "chinese": "保持100毫米间隙。",
            }],
        }
        original = copy.deepcopy(content)
        rules = {
            "schemaVersion": 1,
            "entries": [{
                "id": "cus_0001_stable",
                "originalEnglish": "Keep 100mm clearance. 100mm",
                "english": "Keep 100 mm clearance.",
                "reason": "Remove duplicated source-side unit note.",
            }],
        }

        updated, audit = apply_phrase_cleanups(content, rules)

        self.assertEqual(original, content)
        self.assertEqual("cus_0001_stable", updated["words"][0]["id"])
        self.assertEqual("保持100毫米间隙。", updated["words"][0]["chinese"])
        self.assertEqual("Keep 100 mm clearance.", updated["words"][0]["english"])
        self.assertEqual("Keep 100 mm clearance.", updated["words"][0]["audioText"])
        self.assertEqual(1, audit["cleanedCount"])

    def test_rejects_rule_when_reviewed_source_text_no_longer_matches(self):
        with self.assertRaisesRegex(ValueError, "source mismatch"):
            apply_phrase_cleanups(
                {"words": [{"id": "x", "kind": "PHRASE", "english": "new"}]},
                {
                    "schemaVersion": 1,
                    "entries": [{
                        "id": "x",
                        "originalEnglish": "old",
                        "english": "clean",
                        "reason": "reviewed",
                    }],
                },
            )


if __name__ == "__main__":
    unittest.main()
