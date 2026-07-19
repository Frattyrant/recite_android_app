import hashlib
import tempfile
import unittest
from pathlib import Path

from tools.audio_profiles import (
    PronunciationOverrides,
    PronunciationRule,
)
from tools.audio_production import (
    HumanAudioSource,
    ModelAudioSource,
    assert_safe_staging_path,
    content_sha256,
    load_model_audio_sources,
    plan_production,
)


class AudioProductionPlanTest(unittest.TestCase):
    def test_plan_preserves_word_order_variants_and_override(self):
        words = [
            {
                "id": "mec_0001_x",
                "english": "fixture；jig",
                "audioText": "fixture；jig",
                "kind": "TERM",
                "phonetic": "/ˈfɪkstʃɝ/； /ˈdʒɪɡ/",
            },
            {
                "id": "mee_0001_x",
                "english": "PLC",
                "audioText": "PLC",
                "kind": "TERM",
                "phonetic": "/ˌpiː ɛl ˈsiː/",
            },
        ]
        overrides = PronunciationOverrides(
            by_word_id={},
            exact_text={
                "PLC": PronunciationRule("P L C", "abbreviation"),
            },
        )

        plan = plan_production(words, overrides)

        self.assertEqual(
            ["mec_0001_x", "mee_0001_x"],
            [entry.word_id for entry in plan],
        )
        self.assertEqual(
            ["fixture", "jig"],
            [segment.display_text for segment in plan[0].segments],
        )
        self.assertEqual("P L C.", plan[1].segments[0].spoken_text)
        self.assertEqual("exactText:PLC", plan[1].segments[0].override_key)

    def test_plan_prefers_verified_human_source_and_never_sends_slashes_to_piper(self):
        human = HumanAudioSource(
            text="fixture",
            path=Path("staging/fixture.ogg"),
            source_url="https://upload.wikimedia.org/fixture.ogg",
            description_url="https://commons.wikimedia.org/wiki/File:En-us-fixture.ogg",
            speaker="speaker",
            license_name="Public domain",
            sha256="a" * 64,
        )
        words = [{
            "id": "term",
            "english": "fixture;push/pusher",
            "kind": "TERM",
            "phonetic": "/ˈfɪkstʃɝ/； /pʊʃ/； /ˈpʊʃɝ/",
        }]

        plan = plan_production(
            words,
            PronunciationOverrides.empty(),
            human_audio={"fixture": human},
        )[0]

        self.assertEqual("human", plan.segments[0].source_type)
        self.assertEqual("fixture", plan.segments[0].spoken_text)
        self.assertEqual("piper", plan.segments[1].source_type)
        self.assertTrue(plan.segments[1].spoken_text.endswith("."))
        self.assertNotIn("/", plan.segments[1].spoken_text)
        self.assertEqual("/ˈfɪkstʃɝ/", plan.segments[0].expected_ipa)

    def test_plan_rejects_duplicate_ids(self):
        words = [
            {"id": "same", "english": "fixture", "kind": "TERM"},
            {"id": "same", "english": "jig", "kind": "TERM"},
        ]

        with self.assertRaisesRegex(ValueError, "duplicate word ID"):
            plan_production(words, PronunciationOverrides.empty())

    def test_plan_prefers_hash_bound_open_model_correction(self):
        source = ModelAudioSource(
            text="robot",
            path=Path("corrections/robot.wav"),
            model_name="Kokoro-82M",
            model_version="1.0",
            model_source_url="https://huggingface.co/hexgrad/Kokoro-82M",
            model_license="Apache-2.0",
            voice="af_heart",
            sha256="b" * 64,
        )
        word = {
            "id": "robot",
            "english": "robot",
            "kind": "TERM",
            "phonetic": "/ˈɹoʊbɑt/",
        }

        segment = plan_production(
            [word],
            PronunciationOverrides.empty(),
            model_audio={"robot": source},
        )[0].segments[0]

        self.assertEqual("model", segment.source_type)
        self.assertEqual(source, segment.model_source)
        self.assertIsNone(segment.human_source)
        self.assertEqual("robot", segment.spoken_text)

    def test_model_correction_manifest_is_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "corrections.json"
            manifest.write_text(
                '{"schemaVersion":1,"records":[{'
                '"text":"robot","fileName":"robot.wav",'
                '"modelName":"Kokoro-82M","modelVersion":"1.0",'
                '"modelSourceUrl":"https://huggingface.co/hexgrad/Kokoro-82M",'
                '"modelLicense":"Apache-2.0","voice":"af_heart",'
                '"sha256":"' + ("b" * 64) + '"}]}',
                encoding="utf-8",
            )
            loaded = load_model_audio_sources(manifest, root)
            self.assertEqual(root / "robot.wav", loaded["robot"].path)

            manifest.write_text(
                '{"schemaVersion":1,"records":[{"text":"robot",'
                '"fileName":"../robot.wav"}]}',
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                load_model_audio_sources(manifest, root)

    def test_staging_path_rejects_production_assets(self):
        with self.assertRaisesRegex(ValueError, "production audio"):
            assert_safe_staging_path(Path("app/src/main/assets/audio"))

        with tempfile.TemporaryDirectory() as directory:
            assert_safe_staging_path(Path(directory) / "audio-production-v2.3")

    def test_content_hash_uses_file_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "words.json"
            path.write_bytes(b'{"words": []}\n')

            self.assertEqual(
                hashlib.sha256(path.read_bytes()).hexdigest(),
                content_sha256(path),
            )


if __name__ == "__main__":
    unittest.main()
