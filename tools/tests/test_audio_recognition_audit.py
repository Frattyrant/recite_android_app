import unittest

from tools.pronunciation.audit_audio_recognition import (
    _apply_composite_evidence,
    _can_reuse_transcript,
    _prefer_transcript,
    _reusable_checkpoint_seed,
    _review_for_path,
    _should_prompt_retry,
    _transcribe_audio,
    _targets,
    audit_transcript,
)


class AudioRecognitionAuditTest(unittest.TestCase):
    def test_transcribe_adapter_supports_openai_and_faster_whisper(self):
        class OpenAiModel:
            def transcribe(self, path, **options):
                self.call = (path, options)
                return {"text": " fixture "}

        class Segment:
            def __init__(self, text):
                self.text = text

        class FasterModel:
            def transcribe(self, path, **options):
                self.call = (path, options)
                return iter([Segment(" fixture "), Segment(" jig ")]), object()

        openai = OpenAiModel()
        faster = FasterModel()

        self.assertEqual(
            "fixture",
            _transcribe_audio(openai, "openai", "one.ogg", "technical fixture"),
        )
        self.assertEqual(
            "fixture jig",
            _transcribe_audio(faster, "faster-whisper", "two.ogg", "technical fixture"),
        )
        self.assertEqual("technical fixture", openai.call[1]["initial_prompt"])
        self.assertEqual("technical fixture", faster.call[1]["initial_prompt"])
        self.assertEqual(1, faster.call[1]["beam_size"])

    def test_targets_prefer_expected_transcript_over_synthesis_hint(self):
        manifest = {
            "entries": {
                "mec_catia": {
                    "path": "audio/mec_catia.ogg",
                    "audioSha256": "new-hash",
                    "spokenText": "kuh TEE uh",
                    "expectedTranscript": "CATIA",
                }
            }
        }

        self.assertEqual(
            [("audio/mec_catia.ogg", "CATIA", "new-hash")],
            _targets(manifest),
        )

    def test_cross_content_resume_requires_same_audio_hash(self):
        prior = {"expected": "fixture", "recognized": "fixture"}

        self.assertTrue(
            _can_reuse_transcript(prior, "fixture", "same", "same")
        )
        self.assertFalse(
            _can_reuse_transcript(prior, "fixture", "new", "old")
        )
        self.assertFalse(
            _can_reuse_transcript(prior, "jig", "same", "same")
        )

    def test_resume_checkpoint_keeps_all_future_hash_matched_results(self):
        targets = [
            ("audio/one.ogg", "one", "hash-1"),
            ("audio/two.ogg", "two", "hash-2"),
            ("audio/changed.ogg", "changed", "new-hash"),
        ]
        existing = {
            "audio/one.ogg": {
                "path": "audio/one.ogg",
                "expected": "one",
                "audioSha256": "hash-1",
            },
            "audio/two.ogg": {
                "path": "audio/two.ogg",
                "expected": "two",
                "audioSha256": "hash-2",
            },
            "audio/changed.ogg": {
                "path": "audio/changed.ogg",
                "expected": "changed",
                "audioSha256": "old-hash",
            },
        }

        seed = _reusable_checkpoint_seed(targets, existing, {})

        self.assertEqual(
            {"audio/one.ogg", "audio/two.ogg"},
            set(seed),
        )

    def test_retry_transcript_wins_when_it_matches_better(self):
        recognized, audit = _prefer_transcript(
            "contactor",
            "contact her",
            "contactor",
        )

        self.assertEqual("contactor", recognized)
        self.assertTrue(audit.passed)

    def test_prompt_retry_never_masks_a_spoken_slash(self):
        self.assertTrue(_should_prompt_retry(audit_transcript("stair", "there")))
        self.assertFalse(
            _should_prompt_retry(audit_transcript("fixture", "slash fixture"))
        )

    def test_verified_segments_can_confirm_their_deterministic_composite(self):
        manifest = {
            "entries": {
                "two": {
                    "path": "audio/two.ogg",
                    "segments": [
                        {"path": "audio/variants/two_00.ogg"},
                        {"path": "audio/variants/two_01.ogg"},
                    ],
                }
            }
        }
        results = [
            {"path": "audio/two.ogg", "passed": False, "releasePassed": False, "reason": "transcript-mismatch"},
            {"path": "audio/variants/two_00.ogg", "passed": True, "releasePassed": True},
            {"path": "audio/variants/two_01.ogg", "passed": True, "releasePassed": True},
        ]

        _apply_composite_evidence(manifest, results)

        self.assertTrue(results[0]["passed"])
        self.assertTrue(results[0]["releasePassed"])
        self.assertEqual("verified-segment-composition", results[0]["reason"])

    def test_review_exception_is_bound_to_audio_hash_and_evidence(self):
        reviews = {
            "audio/word.ogg": {
                "audioSha256": "current-hash",
                "reason": "Short isolated term was checked against the reviewed IPA plan.",
                "evidence": ["ipa-lexicon", "piper-synthesis-plan"],
            }
        }

        self.assertEqual(
            reviews["audio/word.ogg"],
            _review_for_path(reviews, "audio/word.ogg", "current-hash"),
        )
        with self.assertRaisesRegex(ValueError, "stale audio audit review"):
            _review_for_path(reviews, "audio/word.ogg", "changed-hash")

    def test_flags_spoken_slash_and_wrong_word(self):
        self.assertTrue(audit_transcript("fixture", "fixture").passed)
        self.assertFalse(audit_transcript("fixture", "slash fixture").passed)
        self.assertFalse(audit_transcript("jig", "gig").passed)

    def test_allows_letter_expansion_and_minor_long_sentence_difference(self):
        self.assertTrue(
            audit_transcript(
                "We're looking for ways to increase revenue",
                "We are looking for ways to increase revenue",
            ).passed
        )
        self.assertTrue(audit_transcript("I'm sorry", "I am sorry").passed)
        self.assertTrue(audit_transcript("P L C", "PLC").passed)
        self.assertTrue(audit_transcript("recipient's name", "recipients name").passed)
        self.assertTrue(audit_transcript("invoice", "in voice").passed)
        self.assertTrue(audit_transcript("important to", "important too").passed)
        self.assertTrue(
            audit_transcript(
                "Please confirm the fixture is aligned with the datum",
                "Please confirm fixture is aligned with the datum",
            ).passed
        )

    def test_allows_spoken_symbol_expansions(self):
        self.assertTrue(audit_transcript("Plug&play", "plug and play").passed)
        self.assertTrue(
            audit_transcript(
                "V C=visual commissioning",
                "VC equals visual commissioning",
            ).passed
        )
        self.assertTrue(
            audit_transcript(
                "schedule 80=SCH80",
                "schedule 80 equals schedule 80",
            ).passed
        )

    def test_allows_reviewed_lexical_pronunciation_equivalents(self):
        self.assertTrue(audit_transcript("Floor", "four").passed)
        self.assertTrue(
            audit_transcript(
                "plexiglass plexy glass",
                "plexiglass plexiglass",
            ).passed
        )
        self.assertTrue(
            audit_transcript(
                "Pneumatic Hoist+Rail",
                "Pneumatic hoist plus rail",
            ).passed
        )

    def test_allows_dictionary_confirmed_homophone_spellings(self):
        self.assertTrue(audit_transcript("phase", "faze").passed)
        self.assertTrue(
            audit_transcript("braking resistor", "breaking resistor").passed
        )

    def test_allows_small_phoneme_error_but_rejects_different_short_word(self):
        self.assertTrue(audit_transcript("weld", "world").passed)
        self.assertFalse(audit_transcript("jig", "gig").passed)

    def test_empty_transcript_never_passes(self):
        result = audit_transcript("fixture", "")

        self.assertFalse(result.passed)
        self.assertEqual("empty-transcript", result.reason)


if __name__ == "__main__":
    unittest.main()
