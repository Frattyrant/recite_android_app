import unittest

from tools.pronunciation.commons_audio import CommonsAudioCandidate, evaluate_candidate


class CommonsAudioPolicyTest(unittest.TestCase):
    def candidate(
        self,
        *,
        title: str = "File:En-us-fixture.ogg",
        license_name: str = "CC0 1.0",
        categories: tuple[str, ...] = ("U.S. English pronunciation",),
    ) -> CommonsAudioCandidate:
        return CommonsAudioCandidate(
            title=title,
            source_url="https://upload.wikimedia.org/fixture.ogg",
            description_url="https://commons.wikimedia.org/wiki/File:En-us-fixture.ogg",
            uploader="speaker",
            license_name=license_name,
            categories=categories,
        )

    def test_accepts_only_exact_cc0_recording_with_us_accent_evidence(self):
        decision = evaluate_candidate(self.candidate(), "fixture")

        self.assertTrue(decision.accepted)
        self.assertEqual("fixture", decision.normalized_recording_text)

    def test_rejects_non_exact_title(self):
        decision = evaluate_candidate(
            self.candidate(title="File:En-us-light-fixture.ogg"),
            "fixture",
        )

        self.assertFalse(decision.accepted)
        self.assertIn("text-mismatch", decision.reasons)

    def test_rejects_non_cc0_or_unknown_accent(self):
        licensed = evaluate_candidate(
            self.candidate(license_name="CC BY-SA 4.0"),
            "fixture",
        )
        accent = evaluate_candidate(
            self.candidate(
                title="File:fixture.ogg",
                categories=("English pronunciation",),
            ),
            "fixture",
        )

        self.assertIn("license-not-cc0", licensed.reasons)
        self.assertIn("us-accent-unverified", accent.reasons)

    def test_normalizes_underscores_and_apostrophes_but_not_extra_words(self):
        exact = evaluate_candidate(
            self.candidate(title="File:En-us-operator's.ogg"),
            "operator's",
        )
        extra = evaluate_candidate(
            self.candidate(title="File:En-us-operator_station.ogg"),
            "operator",
        )

        self.assertTrue(exact.accepted)
        self.assertFalse(extra.accepted)


if __name__ == "__main__":
    unittest.main()
