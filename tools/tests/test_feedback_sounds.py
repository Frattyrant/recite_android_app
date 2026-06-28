import unittest
import wave
from pathlib import Path


class FeedbackSoundAssetTest(unittest.TestCase):
    def test_feedback_sounds_are_short_and_clearly_audible(self):
        root = Path("app/src/main/res/raw")
        for name in ("answer_correct.wav", "answer_wrong.wav"):
            with wave.open(str(root / name), "rb") as source:
                frames = source.readframes(source.getnframes())
                samples = [
                    int.from_bytes(frames[index : index + 2], "little", signed=True)
                    for index in range(0, len(frames), 2)
                ]
                duration = source.getnframes() / source.getframerate()

            self.assertGreaterEqual(max(abs(sample) for sample in samples) / 32767, 0.55)
            self.assertGreaterEqual(duration, 0.12)
            self.assertLessEqual(duration, 0.35)


if __name__ == "__main__":
    unittest.main()
