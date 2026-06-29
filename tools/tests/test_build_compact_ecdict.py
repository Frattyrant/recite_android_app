import tempfile
import unittest
from pathlib import Path

from tools.build_compact_ecdict import build_database, select_rows


class CompactEcdictBuilderTest(unittest.TestCase):
    def test_selection_prefers_tagged_and_frequent_words(self):
        rows = [
            {
                "word": "fixture",
                "phonetic": "x",
                "translation": "夹具",
                "tag": "cet4",
                "bnc": "500",
                "frq": "600",
            },
            {
                "word": "rare-token",
                "phonetic": "",
                "translation": "稀有词",
                "tag": "",
                "bnc": "200000",
                "frq": "300000",
            },
        ]
        self.assertEqual(
            ["fixture"],
            [row["word"] for row in select_rows(rows, limit=1)],
        )

    def test_database_build_is_logically_deterministic(self):
        rows = [
            {
                "word": "fixture",
                "phonetic": "/x/",
                "translation": "夹具",
                "exchange": "s:fixtures",
                "tag": "cet4",
                "bnc": "500",
                "frq": "600",
            },
        ]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = build_database(rows, root / "a.db", limit=10)
            second = build_database(rows, root / "b.db", limit=10)
            self.assertEqual(1, first.entry_count)
            self.assertEqual(first.logical_sha256, second.logical_sha256)


if __name__ == "__main__":
    unittest.main()
