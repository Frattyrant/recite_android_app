"""Select and later generate MIearn's isolated 50-entry audio trial."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


TRIAL_SEED = "miearn-audio-trial-v1"
REQUIRED_TERMS = ("fixture", "jig", "GD&T", "CMM", "PLC", "mylar")
PRODUCTION_AUDIO = Path("app/src/main/assets/audio")


@dataclass(frozen=True)
class TrialSelection:
    words: list[dict]
    missing_required_terms: list[str]


def load_words(path: Path) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    words = payload.get("words")
    if not isinstance(words, list):
        raise ValueError("content JSON must contain a words array")
    ids = [str(word.get("id", "")) for word in words]
    if any(not word_id for word_id in ids) or len(ids) != len(set(ids)):
        raise ValueError("content words require unique non-empty IDs")
    return words


def _stable_key(word: dict) -> tuple[str, str]:
    word_id = str(word["id"])
    digest = hashlib.sha256(f"{TRIAL_SEED}:{word_id}".encode("utf-8")).hexdigest()
    return digest, word_id


def _first_unselected(
    words: Iterable[dict],
    selected_ids: set[str],
) -> dict | None:
    return next(
        (word for word in sorted(words, key=_stable_key) if word["id"] not in selected_ids),
        None,
    )


def select_trial_words(
    words: Sequence[dict],
    total: int = 50,
    required_terms: Sequence[str] = REQUIRED_TERMS,
) -> TrialSelection:
    if total <= 0 or len(words) < total:
        raise ValueError("trial requires at least total source words")
    ids = [str(word.get("id", "")) for word in words]
    if any(not word_id for word_id in ids) or len(ids) != len(set(ids)):
        raise ValueError("trial source requires unique non-empty IDs")

    selected: list[dict] = []
    selected_ids: set[str] = set()
    missing: list[str] = []

    def add(word: dict | None) -> None:
        if word is not None and word["id"] not in selected_ids:
            selected.append(word)
            selected_ids.add(word["id"])

    for term in required_terms:
        matches = [
            word
            for word in words
            if term.casefold() in str(word.get("english", "")).casefold()
        ]
        if matches:
            add(_first_unselected(matches, selected_ids))
        else:
            missing.append(term)

    categories = sorted({str(word.get("category", "")) for word in words})
    for category in categories:
        add(
            _first_unselected(
                (word for word in words if str(word.get("category", "")) == category),
                selected_ids,
            )
        )

    kinds = sorted({str(word.get("kind", "")) for word in words})
    for kind in kinds:
        add(
            _first_unselected(
                (word for word in words if str(word.get("kind", "")) == kind),
                selected_ids,
            )
        )

    for word in sorted(words, key=_stable_key):
        if len(selected) == total:
            break
        add(word)

    if len(selected) != total:
        raise RuntimeError(f"expected {total} trial entries, selected {len(selected)}")
    return TrialSelection(words=selected, missing_required_terms=missing)


def _is_production_audio_path(output: Path) -> bool:
    resolved = output.resolve()
    root = Path(__file__).resolve().parents[1]
    production = (root / PRODUCTION_AUDIO).resolve()
    return resolved == production or production in resolved.parents


def write_trial_selection(output: Path, selection: TrialSelection) -> Path:
    if _is_production_audio_path(output):
        raise ValueError("trial output cannot use the production audio directory")
    output.mkdir(parents=True, exist_ok=True)
    path = output / "trial_selection.json"
    payload = {
        "schemaVersion": 1,
        "selectionSeed": TRIAL_SEED,
        "entryCount": len(selection.words),
        "missingRequiredTerms": selection.missing_required_terms,
        "entries": [
            {
                "id": word["id"],
                "category": word.get("category", ""),
                "kind": word.get("kind", ""),
                "english": word.get("english", ""),
                "phonetic": word.get("phonetic", ""),
                "chinese": word.get("chinese", ""),
            }
            for word in selection.words
        ],
    }
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return path
