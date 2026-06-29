import tempfile
import unittest
import wave
from pathlib import Path

from tools.generate_variant_audio import (
    PAUSE_MS,
    combine_wavs,
    pronounceable_variant,
    raw_variants,
)


class VariantAudioTest(unittest.TestCase):
    def test_terms_split_semicolons_slashes_backslashes_and_spaces(self):
        self.assertEqual(
            ["fixture", "jig", "support", "clamp"],
            raw_variants("fixture； jig/support\\clamp", "TERM"),
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
