import unittest

from tools.audio_production import ProductionEntryPlan, ProductionSegmentPlan
from tools.seed_audio_v231 import plans_are_audio_equivalent


def plan(spoken_text: str, source_type: str = "piper") -> ProductionEntryPlan:
    return ProductionEntryPlan(
        word_id="word",
        english="fixture",
        category="mechanical",
        kind="TERM",
        segments=(
            ProductionSegmentPlan(
                index=0,
                display_text="fixture",
                spoken_text=spoken_text,
                override_key=None,
                expected_ipa="/ˈfɪkstʃɝ/",
                source_type=source_type,
            ),
        ),
    )


class SeedAudioV231Test(unittest.TestCase):
    def test_reuses_only_identical_piper_speech_plans(self):
        self.assertTrue(plans_are_audio_equivalent(plan("fixture"), plan("fixture")))
        self.assertFalse(plans_are_audio_equivalent(plan("fixture"), plan("fixed sure")))
        self.assertFalse(plans_are_audio_equivalent(plan("fixture"), plan("fixture", "human")))


if __name__ == "__main__":
    unittest.main()
