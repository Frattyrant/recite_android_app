import unittest
import hashlib
import tempfile
from pathlib import Path

from tools.ios_audio_pack import (
    build_ios_audio_pack,
    collect_ios_audio_assets,
    ffmpeg_aac_command,
)


class IosAudioPackTest(unittest.TestCase):
    def test_ffmpeg_command_uses_ios_native_mono_aac_profile(self):
        command = ffmpeg_aac_command(
            Path("ffmpeg"),
            Path("input.ogg"),
            Path("output.m4a"),
        )

        self.assertEqual("ffmpeg", str(command[0]))
        self.assertIn("aac", command)
        self.assertIn("48k", command)
        self.assertIn("48000", command)
        self.assertIn("+faststart", command)
        self.assertEqual("output.m4a", str(command[-1]))

    def test_collects_complete_and_variant_assets_with_m4a_paths(self):
        manifest = {
            "entries": {
                "word_1": {
                    "path": "audio/word_1.ogg",
                    "audioSha256": "a" * 64,
                    "segments": [
                        {
                            "path": "audio/variants/word_1_00.ogg",
                            "audioSha256": "b" * 64,
                        },
                        {
                            "path": "audio/variants/word_1_01.ogg",
                            "audioSha256": "c" * 64,
                        },
                    ],
                },
            },
        }

        assets = collect_ios_audio_assets(manifest)

        self.assertEqual(
            [
                "audio/word_1.m4a",
                "audio/variants/word_1_00.m4a",
                "audio/variants/word_1_01.m4a",
            ],
            [asset.output_path for asset in assets],
        )
        self.assertEqual("a" * 64, assets[0].source_sha256)

    def test_rejects_unsafe_or_incomplete_source_records(self):
        cases = [
            {"path": "../secret.ogg", "audioSha256": "a" * 64},
            {"path": "audio/word.mp3", "audioSha256": "a" * 64},
            {"path": "audio/word.ogg", "audioSha256": ""},
        ]
        for entry in cases:
            with self.subTest(entry=entry):
                with self.assertRaises(ValueError):
                    collect_ios_audio_assets({"entries": {"word": entry}})

    def test_rejects_duplicate_asset_paths(self):
        manifest = {
            "entries": {
                "word_1": {
                    "path": "audio/shared.ogg",
                    "audioSha256": "a" * 64,
                },
                "word_2": {
                    "path": "audio/shared.ogg",
                    "audioSha256": "b" * 64,
                },
            },
        }

        with self.assertRaisesRegex(ValueError, "duplicate"):
            collect_ios_audio_assets(manifest)

    def test_build_verifies_source_hash_and_writes_targets_atomically(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source_root = root / "android"
            output_root = root / "ios"
            source = source_root / "audio/word.ogg"
            source.parent.mkdir(parents=True)
            source.write_bytes(b"verified ogg bytes")
            source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
            manifest = {
                "entries": {
                    "word": {
                        "path": "audio/word.ogg",
                        "audioSha256": source_hash,
                    },
                },
            }

            def transcode(input_path: Path, output_path: Path) -> None:
                output_path.write_bytes(b"m4a:" + input_path.read_bytes())

            result = build_ios_audio_pack(
                manifest,
                source_root=source_root,
                output_root=output_root,
                transcode=transcode,
            )

            target = output_root / "audio/word.m4a"
            self.assertEqual(b"m4a:verified ogg bytes", target.read_bytes())
            self.assertEqual(1, result["assetCount"])
            self.assertEqual(source_hash, result["assets"][0]["sourceSha256"])
            self.assertFalse(any(output_root.rglob("*.partial.m4a")))

    def test_build_rejects_changed_source_bytes_before_transcoding(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source_root = root / "android"
            source = source_root / "audio/word.ogg"
            source.parent.mkdir(parents=True)
            source.write_bytes(b"changed")
            manifest = {
                "entries": {
                    "word": {
                        "path": "audio/word.ogg",
                        "audioSha256": "a" * 64,
                    },
                },
            }

            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                build_ios_audio_pack(
                    manifest,
                    source_root=source_root,
                    output_root=root / "ios",
                    transcode=lambda _source, _target: None,
                )


if __name__ == "__main__":
    unittest.main()
