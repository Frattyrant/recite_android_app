import json
import tempfile
import unittest
from pathlib import Path

from tools.audio_trial import (
    REQUIRED_TERMS,
    load_words,
    select_trial_words,
    write_trial_selection,
)


ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "app/src/main/assets/content/words_v1.json"


class AudioTrialSelectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.words = load_words(CONTENT)

    def test_selection_is_deterministic_unique_and_exactly_fifty(self):
        first = select_trial_words(self.words)
        second = select_trial_words(self.words)

        first_ids = [word["id"] for word in first.words]
        self.assertEqual(first_ids, [word["id"] for word in second.words])
        self.assertEqual(50, len(first_ids))
        self.assertEqual(50, len(set(first_ids)))

    def test_selection_covers_all_categories_and_content_kinds(self):
        selection = select_trial_words(self.words)

        self.assertEqual(
            {"mechanical", "electrical", "customer_review", "meeting", "business"},
            {word["category"] for word in selection.words},
        )
        self.assertEqual(
            {"TERM", "PHRASE"},
            {word["kind"] for word in selection.words},
        )

    def test_required_terms_are_selected_when_present(self):
        selection = select_trial_words(self.words)
        selected_text = " ".join(word["english"].casefold() for word in selection.words)

        for term in REQUIRED_TERMS:
            if any(term.casefold() in word["english"].casefold() for word in self.words):
                self.assertIn(term.casefold(), selected_text)
                self.assertNotIn(term, selection.missing_required_terms)

    def test_absent_required_term_is_reported_without_fake_word(self):
        words = [
            {
                "id": f"mec_{index:04d}",
                "category": "mechanical",
                "kind": "TERM",
                "english": f"fixture {index}",
            }
            for index in range(60)
        ]

        selection = select_trial_words(words, required_terms=("fixture", "not-present"))

        self.assertIn("not-present", selection.missing_required_terms)
        self.assertTrue(all(word["id"].startswith("mec_") for word in selection.words))

    def test_selection_writer_rejects_production_audio_directory(self):
        selection = select_trial_words(self.words)
        production = ROOT / "app/src/main/assets/audio/trial"

        with self.assertRaisesRegex(ValueError, "production audio"):
            write_trial_selection(production, selection)

    def test_selection_writer_records_auditable_fields(self):
        selection = select_trial_words(self.words)
        with tempfile.TemporaryDirectory() as directory:
            path = write_trial_selection(Path(directory), selection)
            payload = json.loads(path.read_text(encoding="utf-8"))

        self.assertEqual(1, payload["schemaVersion"])
        self.assertEqual(50, payload["entryCount"])
        self.assertEqual(selection.missing_required_terms, payload["missingRequiredTerms"])
        self.assertEqual(selection.words[0]["id"], payload["entries"][0]["id"])


if __name__ == "__main__":
    unittest.main()
