import copy
import json
import unittest
from collections import Counter
from pathlib import Path

from tools.migrate_content_v231 import _primary_english, _repair_obvious_spelling, migrate_content


ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "app/src/main/assets/content/words_v1.json"
REPAIRS = ROOT / "tools/data/content_v231_repairs.json"


class ContentV231MigrationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        current = json.loads(CONTENT.read_text(encoding="utf-8"))
        cls.repairs = json.loads(REPAIRS.read_text(encoding="utf-8"))
        cls.v23 = copy.deepcopy(current)
        words = {word["id"]: word for word in cls.v23["words"]}
        for repair in cls.repairs["repairs"]:
            words[repair["id"]]["english"] = repair["originalEnglish"]
        heading_specs = [
            ("cus_0062_07a08c0b923561b1", "customer_review", 62, "Customer", "客户"),
            ("cus_0163_a40d31cdb0d5a0de", "customer_review", 163, "MINO USA", "美国明珞"),
            ("cus_0229_de530aaa4f6be358", "customer_review", 229, "Customer", "客户"),
            ("cus_0238_b3cacf6bdd2166c2", "customer_review", 238, "Customer", "客户"),
            ("cus_0248_ecbe12a415c1dd4f", "customer_review", 248, "Proton", "宝腾"),
            ("mee_0001_846b9822ae4b458d", "meeting", 1, "Common Meeting English Expressions", "常用会议英语口语汇总"),
        ]
        for word_id, category, source_index, english, chinese in heading_specs:
            template = next(word for word in cls.v23["words"] if word["category"] == category)
            heading = copy.deepcopy(template)
            heading.update(
                id=word_id,
                sourceIndex=source_index,
                english=english,
                primaryEnglish=english,
                chinese=chinese,
                exampleEn=english,
                exampleZh=chinese,
                audioText=english,
                audioAsset=f"audio/{word_id}.ogg",
            )
            cls.v23["words"].append(heading)
        cls.v23["contentVersion"] = "2026.06.29"

    def test_excludes_six_non_learning_headings_and_preserves_retained_ids(self):
        original = copy.deepcopy(self.v23)

        migrated, audit = migrate_content(self.v23, self.repairs)

        self.assertEqual(2_698, len(migrated["words"]))
        self.assertEqual(
            {
                "mechanical": 1_227,
                "electrical": 970,
                "customer_review": 246,
                "meeting": 57,
                "business": 198,
            },
            dict(Counter(word["category"] for word in migrated["words"])),
        )
        excluded = set(audit["excludedIds"])
        self.assertEqual(
            {
                "cus_0062_07a08c0b923561b1",
                "cus_0163_a40d31cdb0d5a0de",
                "cus_0229_de530aaa4f6be358",
                "cus_0238_b3cacf6bdd2166c2",
                "cus_0248_ecbe12a415c1dd4f",
                "mee_0001_846b9822ae4b458d",
            },
            excluded,
        )
        before_ids = {word["id"] for word in self.v23["words"]} - excluded
        after_ids = {word["id"] for word in migrated["words"]}
        self.assertEqual(before_ids, after_ids)
        self.assertEqual(original, self.v23, "migration must not mutate its input")

    def test_applies_reviewed_field_repairs_without_changing_ids(self):
        migrated, audit = migrate_content(self.v23, self.repairs)
        words = {word["id"]: word for word in migrated["words"]}

        self.assertEqual(
            "Flange;adapter;robot connection",
            words["mec_0328_22c2d1c536376c91"]["english"],
        )
        self.assertEqual("C gun", words["mec_0471_509596a758d037a3"]["english"])
        self.assertEqual("C枪", words["mec_0471_509596a758d037a3"]["chinese"])
        self.assertEqual(
            "X gun;P gun",
            words["ele_0285_cf3b7712f33d1b72"]["english"],
        )
        self.assertEqual(
            "FDS;flow drill screw",
            words["mec_1008_39cb59949b96d924"]["english"],
        )
        self.assertEqual(10, audit["repairedCount"])

    def test_updates_content_version_and_never_adds_image_fields(self):
        migrated, _ = migrate_content(self.v23, self.repairs)

        self.assertEqual("2026.08.25-v2.33-examples", migrated["contentVersion"])
        self.assertTrue(all("imageAsset" not in word for word in migrated["words"]))

    def test_repairs_clear_source_typos_without_touching_stable_ids(self):
        repaired, changed = _repair_obvious_spelling(
            "Penumatic dispensor on an on-standard Transistion station",
        )

        self.assertTrue(changed)
        self.assertEqual(
            "Pneumatic dispenser on an Non-standard Transition station",
            repaired,
        )

    def test_repairs_standard_pfmea_initialism(self):
        repaired, changed = _repair_obvious_spelling(
            "PFMAE-Process failure mode and effects analysis",
        )

        self.assertTrue(changed)
        self.assertEqual(
            "PFMEA-Process failure mode and effects analysis",
            repaired,
        )

    def test_repairs_verified_supplier_brand_spellings(self):
        repaired, changed = _repair_obvious_spelling(
            "BAIGO;Balluf;Dotran",
        )

        self.assertTrue(changed)
        self.assertEqual(
            "BAIGE;Balluff;Dotran",
            repaired,
            "Only independently verified supplier misspellings are corrected",
        )

    def test_primary_english_preserves_initialism_slash(self):
        self.assertEqual(
            "the assignment of the I/O",
            _primary_english("the assignment of the I/O"),
        )
        self.assertEqual("fixture", _primary_english("fixture/jig"))

    def test_repairs_lube_abbreviation_source_typo(self):
        repaired, changed = _repair_obvious_spelling("ube=lubrication")

        self.assertTrue(changed)
        self.assertEqual("lube=lubrication", repaired)


if __name__ == "__main__":
    unittest.main()
