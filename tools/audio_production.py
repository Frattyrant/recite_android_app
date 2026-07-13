"""Plan deterministic, safely staged MIearn production audio."""

from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from tools.audio_profiles import PronunciationOverrides, resolve_spoken_text
from tools.generate_variant_audio import raw_variants


PRODUCTION_AUDIO = Path("app/src/main/assets/audio")


@dataclass(frozen=True)
class ProductionSegmentPlan:
    index: int
    display_text: str
    spoken_text: str
    override_key: str | None


@dataclass(frozen=True)
class ProductionEntryPlan:
    word_id: str
    english: str
    category: str
    kind: str
    segments: tuple[ProductionSegmentPlan, ...]


def content_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def assert_safe_staging_path(path: Path) -> None:
    root = Path(__file__).resolve().parents[1]
    production = (root / PRODUCTION_AUDIO).resolve()
    resolved = path.resolve()
    if resolved == production or production in resolved.parents:
        raise ValueError("production audio directory cannot be used as staging")


def plan_production(
    words: Sequence[dict],
    overrides: PronunciationOverrides,
) -> list[ProductionEntryPlan]:
    seen_ids: set[str] = set()
    seen_paths: set[str] = set()
    plans: list[ProductionEntryPlan] = []
    for word in words:
        word_id = str(word.get("id", "")).strip()
        if not word_id:
            raise ValueError("production word ID cannot be empty")
        if word_id in seen_ids:
            raise ValueError(f"duplicate word ID: {word_id}")
        seen_ids.add(word_id)

        english = str(word.get("english", "")).strip()
        kind = str(word.get("kind", "TERM")).strip().upper() or "TERM"
        variants = raw_variants(english, kind)
        if not variants:
            raise ValueError(f"word has no pronounceable segments: {word_id}")

        segments: list[ProductionSegmentPlan] = []
        for index, display_text in enumerate(variants):
            asset_path = f"audio/variants/{word_id}_{index:02d}.ogg"
            if asset_path in seen_paths:
                raise ValueError(f"duplicate output path: {asset_path}")
            seen_paths.add(asset_path)
            override_word = word
            if len(variants) > 1:
                override_word = dict(word)
                override_word["id"] = f"{word_id}#{index:02d}"
            spoken_text, override_key = resolve_spoken_text(
                override_word,
                display_text,
                overrides,
            )
            segments.append(
                ProductionSegmentPlan(
                    index=index,
                    display_text=display_text,
                    spoken_text=spoken_text,
                    override_key=override_key,
                )
            )

        complete_path = f"audio/{word_id}.ogg"
        if complete_path in seen_paths:
            raise ValueError(f"duplicate output path: {complete_path}")
        seen_paths.add(complete_path)
        plans.append(
            ProductionEntryPlan(
                word_id=word_id,
                english=english,
                category=str(word.get("category", "")),
                kind=kind,
                segments=tuple(segments),
            )
        )
    return plans
