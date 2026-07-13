"""Migrate the reviewed v2.3 content pack to the cleaned V2.31 dataset."""

from __future__ import annotations

import argparse
import copy
import json
import re
import unicodedata
from collections import Counter
from pathlib import Path
from typing import Any


EXPECTED_COUNTS = {
    "mechanical": 1_227,
    "electrical": 970,
    "customer_review": 246,
    "meeting": 57,
    "business": 198,
}
CJK = re.compile(r"[\u3400-\u9fff]")
PARENTHETICAL = re.compile(r"\(([^()]*)\)")
SPELLING_REPAIRS = {
    "auxidiary": "auxiliary",
    "Heigt": "Height",
    "Transistion": "Transition",
    "dispensor": "dispenser",
    "abtuse": "obtuse",
    "Pisitioning": "Positioning",
    "Penumatic": "Pneumatic",
    "Wellding": "Welding",
    "Staionary": "Stationary",
    "Auotamatic": "Automatic",
    "parellel": "parallel",
    "varify": "verify",
    "consistancy": "consistency",
    "filedbus": "fieldbus",
    "staions": "stations",
    "sation": "station",
    "somthing": "something",
    "toooling": "tooling",
    "marriaged": "married",
    "explaning": "explaining",
    "aslo": "also",
    "egde": "edge",
    "calculatd": "calculated",
    "PFMAE": "PFMEA",
    "BAIGO": "BAIGE",
    "Balluf": "Balluff",
    "ube": "lube",
    "acuated": "actuated",
    "contatct": "contact",
    "adust": "adjust",
    "isse": "issue",
    "overcontrain": "overconstrain",
    "eveyone": "everyone",
    "on-standard": "Non-standard",
}


def _primary_english(english: str) -> str:
    protected = re.sub(
        r"(?<![A-Za-z])([A-Z])/([A-Z])(?![A-Za-z])",
        lambda match: f"{match.group(1)}\uFFF0{match.group(2)}",
        english,
    )
    for part in re.split(r"[;；/\\]", protected):
        part = part.replace("\uFFF0", "/")
        normalized = part.strip()
        if re.search(r"[A-Za-z]", normalized):
            return normalized
    raise ValueError(f"English field has no pronounceable variant: {english!r}")


def _clean_term_annotations(english: str, note: str) -> tuple[str, str, bool]:
    normalized = unicodedata.normalize("NFKC", english)
    moved: list[str] = []

    def remove_annotation(match: re.Match[str]) -> str:
        content = match.group(1).strip()
        if CJK.search(content):
            moved.append(content)
            return ""
        return match.group(0)

    cleaned = PARENTHETICAL.sub(remove_annotation, normalized)
    cleaned = re.sub(r"\s*;\s*", ";", cleaned)
    cleaned = re.sub(r";{2,}", ";", cleaned).strip(" ;")
    cleaned_note = note.strip()
    if moved:
        annotation = "；".join(moved)
        cleaned_note = "；".join(part for part in (cleaned_note, f"英文列注释：{annotation}") if part)
    return cleaned, cleaned_note, bool(moved or cleaned != english)


def _repair_obvious_spelling(english: str) -> tuple[str, bool]:
    repaired = english
    for original, replacement in SPELLING_REPAIRS.items():
        repaired = re.sub(
            rf"(?<![A-Za-z]){re.escape(original)}(?![A-Za-z])",
            replacement,
            repaired,
        )
    return repaired, repaired != english


def _validate_repairs(payload: dict[str, Any], words: list[dict[str, Any]]) -> dict[str, dict]:
    repairs = payload.get("repairs")
    if payload.get("schemaVersion") != 1 or not isinstance(repairs, list):
        raise ValueError("content V2.31 repairs require schemaVersion 1 and repairs list")
    existing = {word["id"]: word for word in words}
    result: dict[str, dict] = {}
    for repair in repairs:
        word_id = str(repair.get("id", "")).strip()
        if not word_id or word_id in result or word_id not in existing:
            raise ValueError(f"invalid or duplicate repair ID: {word_id}")
        if existing[word_id]["english"] != repair.get("originalEnglish"):
            raise ValueError(f"repair source mismatch for {word_id}")
        for field in ("english", "chinese", "note", "reason"):
            if field not in repair or (field != "note" and not str(repair[field]).strip()):
                raise ValueError(f"repair {word_id} requires {field}")
        result[word_id] = repair
    return result


def migrate_content(
    content: dict[str, Any],
    repairs_payload: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    source_words = content.get("words")
    if not isinstance(source_words, list):
        raise ValueError("content words must be a list")
    excluded_ids = [str(value).strip() for value in repairs_payload.get("excludedIds", [])]
    if len(excluded_ids) != 6 or len(set(excluded_ids)) != 6:
        raise ValueError("V2.31 requires exactly six unique audited heading exclusions")
    source_ids = {word.get("id") for word in source_words}
    missing_exclusions = set(excluded_ids) - source_ids
    if missing_exclusions:
        raise ValueError(f"excluded IDs missing from source: {sorted(missing_exclusions)}")
    repairs = _validate_repairs(repairs_payload, source_words)

    migrated_words: list[dict[str, Any]] = []
    repaired_ids: list[str] = []
    annotation_cleaned_ids: list[str] = []
    spelling_repaired_ids: list[str] = []
    for source in source_words:
        word_id = str(source["id"])
        if word_id in excluded_ids:
            continue
        word = copy.deepcopy(source)
        if repair := repairs.get(word_id):
            word.update(
                english=str(repair["english"]).strip(),
                chinese=str(repair["chinese"]).strip(),
                note=str(repair["note"]).strip(),
            )
            word["primaryEnglish"] = _primary_english(word["english"])
            word["audioText"] = word["english"]
            word["exampleEn"] = word["english"]
            word["exampleZh"] = word["chinese"]
            repaired_ids.append(word_id)
        if str(word.get("kind", "")).upper() == "TERM":
            cleaned_english, cleaned_note, changed = _clean_term_annotations(
                str(word.get("english", "")),
                str(word.get("note", "")),
            )
            if changed:
                word["english"] = cleaned_english
                word["note"] = cleaned_note
                word["primaryEnglish"] = _primary_english(cleaned_english)
                word["audioText"] = cleaned_english
                annotation_cleaned_ids.append(word_id)
        repaired_english, spelling_changed = _repair_obvious_spelling(
            str(word.get("english", "")),
        )
        if spelling_changed:
            word["english"] = repaired_english
            word["audioText"] = repaired_english
            spelling_repaired_ids.append(word_id)
        if str(word.get("kind", "")).upper() == "TERM":
            word["primaryEnglish"] = _primary_english(str(word["english"]))
        word.pop("imageAsset", None)
        migrated_words.append(word)

    counts = Counter(word["category"] for word in migrated_words)
    if dict(counts) != EXPECTED_COUNTS:
        raise ValueError(f"unexpected V2.31 category counts: {dict(counts)}")
    if len({word["id"] for word in migrated_words}) != len(migrated_words):
        raise ValueError("duplicate stable IDs after V2.31 migration")
    for word in migrated_words:
        if not str(word.get("english", "")).strip() or not str(word.get("chinese", "")).strip():
            raise ValueError(f"empty required field after migration: {word['id']}")
        if CJK.search(str(word.get("english", ""))):
            raise ValueError(f"CJK remains in English field after migration: {word['id']}")

    migrated = copy.deepcopy(content)
    migrated["contentVersion"] = str(repairs_payload.get("contentVersion", "")).strip()
    if not migrated["contentVersion"]:
        raise ValueError("V2.31 content version is required")
    migrated["words"] = migrated_words
    audit = {
        "contentVersion": migrated["contentVersion"],
        "total": len(migrated_words),
        "counts": dict(counts),
        "excludedIds": excluded_ids,
        "repairedIds": repaired_ids,
        "repairedCount": len(repaired_ids),
        "annotationCleanedIds": annotation_cleaned_ids,
        "annotationCleanedCount": len(annotation_cleaned_ids),
        "spellingRepairedIds": spelling_repaired_ids,
        "spellingRepairedCount": len(spelling_repaired_ids),
    }
    return migrated, audit


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--repairs", type=Path, default=Path("tools/data/content_v231_repairs.json"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    args = parser.parse_args()
    content = json.loads(args.input.read_text(encoding="utf-8"))
    repairs = json.loads(args.repairs.read_text(encoding="utf-8"))
    migrated, audit = migrate_content(content, repairs)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.audit.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(migrated, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.audit.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"PASS entries={audit['total']} repaired={audit['repairedCount']} excluded={len(audit['excludedIds'])}")


if __name__ == "__main__":
    main()
