import json
import unittest
from pathlib import Path

import tools.build_content as content_builder
from tools.build_content import (
    CATEGORY_COUNTS,
    build_content,
    clean_primary_english,
    make_stable_id,
)


PROJECT_ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = PROJECT_ROOT / "assets"


class ContentPipelineTest(unittest.TestCase):
    def test_primary_english_uses_first_pronounceable_variant(self):
        self.assertEqual("fixture", clean_primary_english("fixture；jig"))
        self.assertEqual("push", clean_primary_english("push/pusher"))
        self.assertEqual("Body in White", clean_primary_english("Body in White (BIW)"))

    def test_stable_id_is_repeatable_and_category_scoped(self):
        first = make_stable_id("mechanical", 1, "Body in White (BIW)")
        second = make_stable_id("mechanical", 1, "Body in White (BIW)")
        other = make_stable_id("electrical", 1, "Body in White (BIW)")
        self.assertEqual(first, second)
        self.assertNotEqual(first, other)

    def test_phrase_footer_invariant_is_category_aware_and_allows_real_numeric_phrases(self):
        self.assertTrue(
            hasattr(content_builder, "phrase_footer_issue"),
            "content builder must expose the phrase footer semantic invariant",
        )
        issue = content_builder.phrase_footer_issue

        self.assertIsNotNone(
            issue(
                "customer_review",
                27,
                "Separate the power cables from the communication cables. ( ) ( ) 90",
            )
        )
        self.assertIsNotNone(
            issue("meeting", 21, "Sorry? , , ? ? 127")
        )
        self.assertIsNotNone(
            issue("business", 1, "Please review the proposal 101")
        )
        self.assertIsNone(
            issue(
                "customer_review",
                244,
                "Any detail that touches outer surface has to be urethane minimum "
                "50 by 50mm. / , 50X50",
            )
        )
        self.assertIsNone(issue("mechanical", 1, "Loctite 242"))
        self.assertIsNone(issue("electrical", 1, "axis 1"))

    def test_repairs_footer_contaminated_phrases_without_changing_identity_or_chinese(self):
        directory = PROJECT_ROOT / "tmp" / "content-footer-repair-test"
        directory.mkdir(parents=True, exist_ok=True)
        records = build_content(
            SOURCE_DIR,
            directory / "words_v1.json",
            directory / "content_report.json",
        )
        expected = {
            ("customer_review", 27): (
                "cus_0027_3f68a8e306b3c845",
                "Separate the power cables from the communication cables.",
                "把电源线（强电）和通信线缆（弱电）分开",
            ),
            ("customer_review", 33): (
                "cus_0033_df83c3e3fc386bb3",
                "If the part is skewed and the rack isn't put in place correctly, "
                "are we able to avoid like a potential crash there, or are we thinking "
                "that the cameras will be able to catch that?",
                "? 如果工件倾斜，料架位置没放对，能避免碰撞吗？还是说相机能检测到？",
            ),
            ("customer_review", 95): (
                "cus_0095_f6b0eea397c6b6ce",
                "We need to be very gentle in the money",
                "在成本上我们要非常谨慎。",
            ),
            ("customer_review", 116): (
                "cus_0116_a78714cc3eb67643",
                "If the payload is under 90, you need to use steel, especially for the pin.",
                "90, , 如果负载在90以下，需要用铁而不是铝，特别是定位销单元。",
            ),
            ("customer_review", 129): (
                "cus_0129_8d8c4a87fbd2bb86",
                "Could we jump to 90 station?",
                "90 去90工位",
            ),
            ("customer_review", 172): (
                "cus_0172_f72012bd81ddbde5",
                "Make sure the clamp arm doesn't exceed the capacity of the clamp",
                "确保气缸的负载要满足压臂设计的负载/确保压臂的负载不超过气缸的负载。",
            ),
            ("customer_review", 188): (
                "cus_0188_6e1439266a493e29",
                "The tip dresser is 90 degrees respect to the robot.",
                "90 修磨器与机器人成90度。",
            ),
            ("customer_review", 249): (
                "cus_0249_c0da463cc76a4f5a",
                "Gripper stand needs to be under 80 degrees in SS11 project and make "
                "it to be horizontal as much as possible.",
                "80 项目的所有抓手支座都要在80度以下. 尽可能与地面平行。",
            ),
            ("meeting", 21): (
                "mee_0021_0f5c6b85e5cca166",
                "Sorry?",
                "您说什么？",
            ),
        }

        for key, (stable_id, english, chinese) in expected.items():
            item = next(
                record
                for record in records
                if (record["category"], record["sourceIndex"]) == key
            )
            self.assertEqual(stable_id, item["id"])
            self.assertEqual(f"audio/{stable_id}.ogg", item["audioAsset"])
            self.assertEqual(chinese, item["chinese"])
            self.assertEqual(chinese, item["exampleZh"])
            for field in ("english", "primaryEnglish", "exampleEn", "audioText"):
                self.assertEqual(english, item[field], (key, field))

    def test_builds_expected_2704_records_with_required_fields(self):
        directory = PROJECT_ROOT / "tmp" / "content-pipeline-test"
        directory.mkdir(parents=True, exist_ok=True)
        output = directory / "words_v1.json"
        report = directory / "content_report.json"
        records = build_content(SOURCE_DIR, output, report)

        self.assertEqual(2704, len(records))
        counts = {
            category: sum(item["category"] == category for item in records)
            for category in CATEGORY_COUNTS
        }
        self.assertEqual(CATEGORY_COUNTS, counts)

        ids = {item["id"] for item in records}
        self.assertEqual(2704, len(ids))
        for item in records:
            for field in (
                "id",
                "category",
                "sourceIndex",
                "english",
                "primaryEnglish",
                "phonetic",
                "chinese",
                "exampleEn",
                "exampleZh",
                "audioText",
                "audioAsset",
            ):
                self.assertTrue(str(item[field]).strip(), (item["id"], field))
            self.assertRegex(
                item["audioText"],
                r"(?<![A-Za-z0-9])(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{2,}",
            )

        expected_customer_repairs = {
            56: (
                "cus_0056_d8d5ae4c9c40cbfd",
                "RFQ package: the customer's initial project release standard",
            ),
            79: (
                "cus_0079_cd01df693401d0c5",
                "Laser-welding fixture shielding gas: gas flows from the cylinder or "
                "workshop pipeline through the solenoid valve and pressure switch to "
                "the manifold, then to each gas-blowing port.",
            ),
            163: ("cus_0163_a40d31cdb0d5a0de", "MINO USA"),
            248: ("cus_0248_ecbe12a415c1dd4f", "Proton"),
        }
        for source_index, (old_id, english) in expected_customer_repairs.items():
            item = next(
                record
                for record in records
                if record["category"] == "customer_review"
                and record["sourceIndex"] == source_index
            )
            self.assertEqual(old_id, item["id"])
            self.assertEqual(f"audio/{old_id}.ogg", item["audioAsset"])
            self.assertEqual(english, item["english"])
            self.assertEqual(english, item["primaryEnglish"])
            self.assertEqual(english, item["exampleEn"])
            self.assertEqual(english, item["audioText"])
            self.assertEqual("/phonetic not available/", item["phonetic"])

        expected_semantic_repairs = {
            ("mechanical", 471): {
                "id": "mec_0471_509596a758d037a3",
                "english": "C gun",
                "chinese": "C Gun",
                "phonetic": "/sˈiː/",
            },
            ("mechanical", 472): {
                "id": "mec_0472_b1fb9f4f2cdea5b4",
                "english": "X gun (P gun)",
                "chinese": "X Gun(P Gun)",
                "phonetic": "/ˈɛks pˈiː/",
            },
            ("customer_review", 220): {
                "id": "cus_0220_7b5b327869465483",
                "english": (
                    "If we have the robot riser at two meters, then we need the glue "
                    "stand much higher, then we need a maintenance stand for the glue, "
                    "maintenance stair for the robot...additional safety for maintenance "
                    "stairs and so on. If we can manage to have one meter, the glue stand "
                    "will be much lower, we have much less work to do."
                ),
                "chinese": (
                    "如果机器人底座两米高，那么涂胶支架就要更高，且涂胶设备维修也需要"
                    "加塌台，机器人维修也需要加塌台。除 此之外，我们还要给维修塌台加安全"
                    "盒子。如果机器人底座可以降到1米，就不用这么麻烦了。"
                ),
            },
            ("customer_review", 223): {
                "id": "cus_0223_cea5f7a7c99be82d",
                "english": (
                    "These are designed for like Amazon package shipment not industrial. "
                    "You got those parts on there with sharp edges and all that stuff, "
                    "I don't know how well it'll hold up, right? I don't know that we use "
                    "belts like that. It seems like the belts we use have serrated pattern "
                    "on them, so they can handle more wear and tear."
                ),
                "chinese": (
                    "? , （视频中的皮带机）是用于类似亚马逊包裹运输的，不是我们这个行业"
                    "用的。因为我们的工件边缘是很尖锐 的，我不知道这种皮带机能不能 得住。"
                    "好像我们用的皮带机一般都是带有锯齿状设计的，这样会更耐磨"
                ),
            },
        }
        for (category, source_index), expected_repair in expected_semantic_repairs.items():
            item = next(
                record
                for record in records
                if record["category"] == category
                and record["sourceIndex"] == source_index
            )
            self.assertEqual(expected_repair["id"], item["id"])
            self.assertEqual(f"audio/{item['id']}.ogg", item["audioAsset"])
            self.assertEqual(expected_repair["english"], item["english"])
            self.assertEqual(expected_repair["english"], item["primaryEnglish"])
            self.assertEqual(expected_repair["english"], item["exampleEn"])
            self.assertEqual(expected_repair["english"], item["audioText"])
            self.assertEqual(expected_repair["chinese"], item["chinese"])
            if category == "customer_review":
                self.assertEqual(expected_repair["chinese"], item["exampleZh"])
            if "phonetic" in expected_repair:
                self.assertEqual(expected_repair["phonetic"], item["phonetic"])

        on_disk = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(records, on_disk["words"])
        audit = json.loads(report.read_text(encoding="utf-8"))
        self.assertEqual(2704, audit["total"])
        self.assertEqual(CATEGORY_COUNTS, audit["counts"])
        self.assertIn("phraseFooterAudit", audit)
        self.assertEqual(0, audit["phraseFooterAudit"]["count"])
        self.assertEqual([], audit["phraseFooterAudit"]["issues"])
        self.assertEqual(
            [
                {
                    "category": "customer_review",
                    "sourceIndex": 244,
                    "legitimateEnding": "50X50",
                }
            ],
            audit["phraseFooterAudit"]["allowlisted"],
        )


if __name__ == "__main__":
    unittest.main()
