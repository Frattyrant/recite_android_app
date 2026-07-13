"""Apply audited sentence-only cleanup without changing stable IDs or translations."""

from __future__ import annotations

import copy
from typing import Any


def apply_phrase_cleanups(
    content: dict[str, Any],
    rules_payload: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    entries = rules_payload.get("entries")
    if rules_payload.get("schemaVersion") != 1 or not isinstance(entries, list):
        raise ValueError("phrase cleanup requires schemaVersion 1 and entries")
    updated = copy.deepcopy(content)
    by_id = {str(word.get("id", "")): word for word in updated.get("words", [])}
    cleaned_ids: list[str] = []
    already_clean_ids: list[str] = []
    seen: set[str] = set()
    for rule in entries:
        word_id = str(rule.get("id", "")).strip()
        original = str(rule.get("originalEnglish", ""))
        cleaned = str(rule.get("english", "")).strip()
        reason = str(rule.get("reason", "")).strip()
        if not word_id or word_id in seen or word_id not in by_id or not cleaned or not reason:
            raise ValueError(f"invalid or duplicate phrase cleanup: {word_id}")
        seen.add(word_id)
        word = by_id[word_id]
        if str(word.get("kind", "")).upper() != "PHRASE":
            raise ValueError(f"phrase cleanup targets non-phrase entry: {word_id}")
        current_english = str(word.get("english", ""))
        if current_english == cleaned:
            cleaned_ids.append(word_id)
            already_clean_ids.append(word_id)
            continue
        if current_english != original:
            raise ValueError(f"phrase cleanup source mismatch for {word_id}")
        word["english"] = cleaned
        word["primaryEnglish"] = cleaned
        word["audioText"] = cleaned
        word["exampleEn"] = cleaned
        cleaned_ids.append(word_id)
    return updated, {
        "cleanedCount": len(cleaned_ids),
        "cleanedIds": cleaned_ids,
        "alreadyCleanCount": len(already_clean_ids),
        "alreadyCleanIds": already_clean_ids,
    }
