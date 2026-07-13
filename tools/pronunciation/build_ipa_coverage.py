"""Replace legacy PDF phonetics with auditable General American IPA."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Any

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.example_generator import apply_examples
from tools.migrate_content_v231 import _primary_english, _repair_obvious_spelling
from tools.phrase_cleanup import apply_phrase_cleanups
from tools.pronunciation.ipa_lexicon import IpaLexicon, resolve_entry_phonetic


def load_overrides(path: Path) -> dict[str, str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    entries = payload.get("entries")
    if payload.get("schemaVersion") != 1 or payload.get("dialect") != "en-US":
        raise ValueError("IPA overrides require schemaVersion 1 and en-US dialect")
    if not isinstance(entries, dict):
        raise ValueError("IPA override entries must be an object")
    return {str(key).strip(): str(value).strip() for key, value in entries.items()}


def apply_ipa(
    content: dict[str, Any],
    lexicon: IpaLexicon,
    phrase_cleanup_payload: dict[str, Any] | None = None,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, dict[str, Any]]]:
    updated = copy.deepcopy(content)
    spelling_repaired_ids: list[str] = []
    for word in updated.get("words", []):
        repaired_english, spelling_changed = _repair_obvious_spelling(
            str(word.get("english", "")),
        )
        if spelling_changed:
            word["english"] = repaired_english
            word["audioText"] = repaired_english
            spelling_repaired_ids.append(str(word["id"]))
        if str(word.get("kind", "TERM")).upper() == "TERM":
            word["primaryEnglish"] = _primary_english(str(word["english"]))
    phrase_cleanup_audit = {"cleanedCount": 0, "cleanedIds": []}
    if phrase_cleanup_payload is not None:
        updated, phrase_cleanup_audit = apply_phrase_cleanups(
            updated,
            phrase_cleanup_payload,
        )
    updated = apply_examples(updated)
    source_counts: Counter[str] = Counter()
    fallback_entries: list[dict[str, Any]] = []
    frozen: dict[str, dict[str, Any]] = {}
    for word in updated.get("words", []):
        result = resolve_entry_phonetic(word, lexicon)
        word["phonetic"] = result.display
        for variant, source in zip(result.variants, result.sources):
            resolution = lexicon.resolve_text(variant)
            frozen[variant] = {
                "display": resolution.display,
                "source": resolution.source,
                "fallbackTokens": resolution.fallback_tokens,
            }
            source_counts[source] += 1
        if result.fallback_tokens:
            fallback_entries.append(
                {
                    "id": word["id"],
                    "english": word["english"],
                    "tokens": sorted(set(result.fallback_tokens)),
                }
            )
    malformed = [
        word["id"]
        for word in updated.get("words", [])
        if not str(word.get("phonetic", "")).startswith("/")
        or "phonetic not available" in str(word.get("phonetic", "")).casefold()
    ]
    if malformed:
        raise ValueError(f"malformed IPA entries: {malformed[:20]}")
    report = {
        "contentVersion": updated.get("contentVersion"),
        "entryCount": len(updated.get("words", [])),
        "variantCount": sum(source_counts.values()),
        "sourceCounts": dict(sorted(source_counts.items())),
        "fallbackEntryCount": len(fallback_entries),
        "fallbackEntries": fallback_entries,
        "spellingRepairedCount": len(spelling_repaired_ids),
        "spellingRepairedIds": spelling_repaired_ids,
        "phraseCleanup": phrase_cleanup_audit,
    }
    return updated, report, dict(sorted(frozen.items(), key=lambda item: item[0].casefold()))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--ipa-dict", type=Path, required=True)
    parser.add_argument("--overrides", type=Path, default=Path("tools/pronunciation/ipa_overrides.json"))
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--frozen", type=Path, required=True)
    parser.add_argument(
        "--phrase-cleanups",
        type=Path,
        default=Path("tools/data/phrase_cleanups_v231.json"),
    )
    args = parser.parse_args()
    content = json.loads(args.content.read_text(encoding="utf-8"))
    lexicon = IpaLexicon.from_tsv(args.ipa_dict).with_overrides(load_overrides(args.overrides))
    phrase_cleanup_payload = json.loads(args.phrase_cleanups.read_text(encoding="utf-8"))
    updated, report, frozen = apply_ipa(
        content,
        lexicon,
        phrase_cleanup_payload=phrase_cleanup_payload,
    )
    report["provenance"] = {
        "name": "open-dict-data/ipa-dict en_US",
        "url": "https://github.com/open-dict-data/ipa-dict",
        "revision": "43c3570eb3553bdd19fccd2bd0091534889af023",
        "sha256": sha256(args.ipa_dict),
        "license": "MIT (English US data derived from cmudict-ipa; see upstream credits)",
        "fallback": "piper-tts bundled eSpeak-ng en-us phonemizer",
    }
    args.content.write_text(json.dumps(updated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.frozen.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.frozen.write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "dialect": "en-US",
                "contentVersion": updated.get("contentVersion"),
                "variants": frozen,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    print(
        f"PASS entries={report['entryCount']} variants={report['variantCount']} "
        f"fallbackEntries={report['fallbackEntryCount']}"
    )


if __name__ == "__main__":
    main()
