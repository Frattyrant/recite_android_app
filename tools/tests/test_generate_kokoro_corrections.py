import unittest

from tools.generate_kokoro_corrections import SYNTHESIS_OVERRIDES, correction_targets


class GenerateKokoroCorrectionsTest(unittest.TestCase):
    def test_failed_complete_selects_all_variants_once(self):
        content = {
            "words": [{
                "id": "mec_1",
                "english": "fixture；jig",
                "kind": "TERM",
            }]
        }
        manifest = {
            "entries": {
                "mec_1": {
                    "path": "audio/mec_1.ogg",
                    "segments": [
                        {"path": "audio/variants/mec_1_00.ogg"},
                        {"path": "audio/variants/mec_1_01.ogg"},
                    ],
                }
            }
        }
        report = {
            "assets": [
                {"path": "audio/mec_1.ogg", "passed": False},
                {"path": "audio/variants/mec_1_01.ogg", "passed": False},
            ]
        }

        self.assertEqual(
            ["fixture", "jig"],
            correction_targets(content, manifest, report),
        )

    def test_unknown_failure_path_fails_closed(self):
        content = {"words": [{"id": "one", "english": "robot", "kind": "TERM"}]}
        manifest = {"entries": {"one": {"path": "audio/one.ogg"}}}
        report = {"assets": [{"path": "audio/unknown.ogg", "passed": False}]}
        with self.assertRaises(ValueError):
            correction_targets(content, manifest, report)

    def test_reviewed_short_token_hints_do_not_change_manifest_key(self):
        self.assertEqual("pre drop", SYNTHESIS_OVERRIDES["predrop"])
        self.assertEqual("hong bye", SYNTHESIS_OVERRIDES["hongbai"])
        self.assertEqual("crank!", SYNTHESIS_OVERRIDES["crank"])
        self.assertEqual("notch!", SYNTHESIS_OVERRIDES["notch"])


if __name__ == "__main__":
    unittest.main()
