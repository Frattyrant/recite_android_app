import unittest
from pathlib import Path

from tools.audio_profiles import LESSAC_HIGH, LESSAC_MEDIUM
from tools.generate_audio import ffmpeg_encode_args


class AudioEncodingProfileTest(unittest.TestCase):
    def test_medium_profile_preserves_current_encoding(self):
        args = ffmpeg_encode_args(LESSAC_MEDIUM, Path("source.wav"), Path("out.ogg"))
        self.assertIn("32k", args)
        self.assertIn("voip", args)

    def test_high_profile_uses_40k_audio(self):
        args = ffmpeg_encode_args(LESSAC_HIGH, Path("source.wav"), Path("out.ogg"))
        self.assertIn("40k", args)
        self.assertIn("audio", args)
        self.assertIn("48000", args)


if __name__ == "__main__":
    unittest.main()
