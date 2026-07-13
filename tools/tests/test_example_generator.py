import unittest

from tools.example_generator import example_for


class ExampleGeneratorTest(unittest.TestCase):
    def word(self, index: int, category: str = "mechanical") -> dict:
        return {
            "id": f"{category}_{index:04d}_stable",
            "sourceIndex": index,
            "category": category,
            "kind": "TERM",
            "primaryEnglish": f"fixture {index}",
            "chinese": f"夹具 {index}",
            "english": f"fixture {index}",
        }

    def test_term_examples_are_deterministic_varied_and_targeted(self):
        rows = [example_for(self.word(index)) for index in range(1, 21)]

        self.assertEqual(rows, [example_for(self.word(index)) for index in range(1, 21)])
        self.assertGreaterEqual(len({row.example_en for row in rows}), 8)
        for index, row in enumerate(rows, 1):
            self.assertIn(f"fixture {index}".casefold(), row.example_en.casefold())
            self.assertIn(f"夹具 {index}", row.example_zh)

    def test_electrical_examples_use_electrical_context(self):
        row = example_for(self.word(2, category="electrical"))

        self.assertIn("fixture 2", row.example_en)
        self.assertTrue(
            any(token in row.example_en.casefold() for token in ("panel", "circuit", "signal", "power", "wiring"))
        )

    def test_phrase_keeps_reviewed_source_as_its_example(self):
        word = {
            "id": "meeting_0001_stable",
            "sourceIndex": 1,
            "category": "meeting",
            "kind": "PHRASE",
            "english": "Could you repeat that, please?",
            "primaryEnglish": "Could you repeat that, please?",
            "chinese": "请再说一遍好吗？",
        }

        row = example_for(word)

        self.assertEqual(word["english"], row.example_en)
        self.assertEqual(word["chinese"], row.example_zh)


if __name__ == "__main__":
    unittest.main()
