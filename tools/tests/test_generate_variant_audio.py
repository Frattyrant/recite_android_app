import tempfile
import unittest
import wave
from pathlib import Path

from tools.generate_variant_audio import (
    PAUSE_MS,
    combine_wavs,
    legacy_term_variants,
    segment_plan_sha256,
    pronounceable_variant,
    raw_variants,
    requires_contiguous_regeneration,
)


class VariantAudioTest(unittest.TestCase):
    def test_terms_split_semicolons_slashes_backslashes_and_spaces(self):
        self.assertEqual(
            ["fixture", "jig", "support", "clamp"],
            raw_variants("fixture； jig/support\\clamp", "TERM"),
        )

    def test_terms_keep_multiword_variants_and_units_together(self):
        self.assertEqual(
            ["support and clamp block", "NC block"],
            raw_variants("support and clamp block; NC block", "TERM"),
        )
        self.assertEqual(["clamp arm"], raw_variants("clamp arm", "TERM"))
        self.assertEqual(
            ["Read at 300mm/s"],
            raw_variants("Read at 300mm/s", "TERM"),
        )
    def test_segment_plan_distinguishes_contiguous_phrase_from_legacy_word_splits(self):
        current = raw_variants("Body in White (BIW)", "TERM")
        legacy = legacy_term_variants("Body in White (BIW)")

        self.assertEqual(["Body in White (BIW)"], current)
        self.assertEqual(["Body", "in", "White", "(BIW)"], legacy)
        self.assertNotEqual(
            segment_plan_sha256(current),
            segment_plan_sha256(legacy),
        )

    def test_segment_plan_is_deterministic_and_includes_pause_policy(self):
        variants = ["fixture", "jig"]

        self.assertEqual(
            segment_plan_sha256(variants),
            segment_plan_sha256(list(variants)),
        )
        self.assertNotEqual(
            segment_plan_sha256(variants, pause_ms=500),
            segment_plan_sha256(variants, pause_ms=0),
        )

    def test_legacy_single_phrase_requires_one_contiguous_regeneration(self):
        word = {"kind": "TERM", "english": "Body in White (BIW)"}
        variants = raw_variants(word["english"], word["kind"])

        self.assertTrue(requires_contiguous_regeneration(word, variants, {}))
        self.assertFalse(
            requires_contiguous_regeneration(
                word,
                variants,
                {"segmentPlanSha256": segment_plan_sha256(variants)},
            )
        )

    def test_phrases_split_sentences_but_keep_words_and_units_together(self):
        self.assertEqual(
            ["Read at 300mm/s.", "Then inspect the result."],
            raw_variants("Read at 300mm/s. Then inspect the result.", "PHRASE"),
        )
        self.assertEqual(
            ["Sorry, come again?", "Please repeat that."],
            raw_variants("Sorry, come again?/Please repeat that.", "PHRASE"),
        )

    def test_pronounceable_text_never_contains_slash(self):
        spoken = pronounceable_variant("Read at 300mm/s.")
        self.assertNotIn("/", spoken)
        self.assertNotIn("\\", spoken)

    def test_single_letter_term_variant_remains_pronounceable(self):
        self.assertEqual("C", pronounceable_variant("C"))

    def test_term_drops_annotation_only_fragments_and_pronounces_numbers(self):
        self.assertEqual(
            ["fixture", "jig", "242"],
            raw_variants("fixture / （中文注释） / - / jig / 242", "TERM"),
        )
        self.assertEqual("242", pronounceable_variant("242"))

    def test_combined_wav_inserts_exact_half_second(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = []
            for index in range(2):
                path = root / f"{index}.wav"
                with wave.open(str(path), "wb") as target:
                    target.setnchannels(1)
                    target.setsampwidth(2)
                    target.setframerate(22_050)
                    target.writeframes(b"\1\0" * 100)
                sources.append(path)
            combined = root / "combined.wav"
            combine_wavs(sources, combined)
            with wave.open(str(combined), "rb") as result:
                self.assertEqual(200 + 22_050 * PAUSE_MS // 1000, result.getnframes())


if __name__ == "__main__":
    unittest.main()
