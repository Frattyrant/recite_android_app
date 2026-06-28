import tempfile
import unittest
import wave
from pathlib import Path

from tools.generate_variant_audio import PAUSE_MS, combine_wavs, raw_variants


class VariantAudioTest(unittest.TestCase):
    def test_split_only_semicolons(self):
        self.assertEqual(["fixture", "jig"], raw_variants("fixture； jig"))
        self.assertEqual(["a,b/c"], raw_variants("a,b/c"))

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
