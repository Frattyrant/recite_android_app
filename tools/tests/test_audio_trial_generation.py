import unittest

from tools.audio_profiles import PronunciationOverrides
from tools.audio_trial import plan_trial_entry


class AudioTrialGenerationPlanTest(unittest.TestCase):
    def test_multi_expression_plan_preserves_variants_and_pause(self):
        word = {
            "id": "mec_0002",
            "kind": "TERM",
            "english": "fixture；jig",
            "audioText": "fixture, jig",
            "primaryEnglish": "fixture",
        }

        plan = plan_trial_entry(word, PronunciationOverrides.empty())

        self.assertEqual(["fixture", "jig"], [item["displayText"] for item in plan["segments"]])
        self.assertEqual(500, plan["pauseBetweenSegmentsMs"])

    def test_exact_override_changes_synthesis_text_not_display_text(self):
        rules = PronunciationOverrides.from_json(
            {
                "schemaVersion": 1,
                "wordId": {},
                "exactText": {
                    "GD&T": {"spokenText": "G D and T", "type": "symbolExpansion"}
                },
            }
        )
        word = {
            "id": "mec_0050",
            "kind": "TERM",
            "english": "GD&T",
            "audioText": "GD&T",
            "primaryEnglish": "GD&T",
        }

        plan = plan_trial_entry(word, rules)

        self.assertEqual("GD&T", plan["segments"][0]["displayText"])
        self.assertEqual("G D and T", plan["segments"][0]["spokenText"])
        self.assertEqual("exactText:GD&T", plan["segments"][0]["overrideKey"])


if __name__ == "__main__":
    unittest.main()
