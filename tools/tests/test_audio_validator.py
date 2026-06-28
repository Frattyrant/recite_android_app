import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.validate_audio import probe_audio


FFMPEG = Path(r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe")
FFPROBE = FFMPEG.with_name("ffprobe.exe")


class AudioValidatorTest(unittest.TestCase):
    @unittest.skipUnless(FFMPEG.is_file() and FFPROBE.is_file(), "ffmpeg required")
    def test_silent_valid_opus_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            silent = Path(directory) / "silent.ogg"
            subprocess.run(
                [
                    str(FFMPEG),
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-f",
                    "lavfi",
                    "-i",
                    "anullsrc=r=22050:cl=mono",
                    "-t",
                    "0.5",
                    "-c:a",
                    "libopus",
                    "-ar",
                    "48000",
                    str(silent),
                ],
                check=True,
            )
            with self.assertRaisesRegex(RuntimeError, "silent or near-silent"):
                probe_audio(silent, FFPROBE, FFMPEG)


if __name__ == "__main__":
    unittest.main()
