"""Plan deterministic, safely staged MIearn production audio."""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

from tools.audio_profiles import PronunciationOverrides, resolve_audio_texts
from tools.generate_variant_audio import raw_variants
from tools.pronunciation.commons_audio import normalize_text


PRODUCTION_AUDIO = Path("app/src/main/assets/audio")


IPA_GROUP = re.compile(r"/[^/]+/")


@dataclass(frozen=True)
class HumanAudioSource:
    text: str
    path: Path
    source_url: str
    description_url: str
    speaker: str
    license_name: str
    sha256: str


@dataclass(frozen=True)
class ProductionSegmentPlan:
    index: int
    display_text: str
    spoken_text: str
    override_key: str | None
    expected_ipa: str
    source_type: str
    human_source: HumanAudioSource | None = None
    expected_transcript: str = ""


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


def speech_plan_sha256(plan: ProductionEntryPlan) -> str:
    payload = [
        {
            "index": segment.index,
            "text": segment.display_text,
            "spokenText": segment.spoken_text,
            "overrideKey": segment.override_key,
            "sourceType": segment.source_type,
            "humanSourceSha256": (
                segment.human_source.sha256 if segment.human_source is not None else None
            ),
        }
        for segment in plan.segments
    ]
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def assert_safe_staging_path(path: Path) -> None:
    root = Path(__file__).resolve().parents[1]
    production = (root / PRODUCTION_AUDIO).resolve()
    resolved = path.resolve()
    if resolved == production or production in resolved.parents:
        raise ValueError("production audio directory cannot be used as staging")


def load_human_audio_sources(
    path: Path | None,
    audio_root: Path | None = None,
) -> dict[str, HumanAudioSource]:
    if path is None:
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schemaVersion") != 1 or not isinstance(payload.get("records"), list):
        raise ValueError("human audio attribution manifest is invalid")
    result: dict[str, HumanAudioSource] = {}
    for record in payload["records"]:
        text = str(record.get("text", "")).strip()
        file_name = str(record.get("fileName", "")).strip()
        source = HumanAudioSource(
            text=text,
            path=(audio_root / file_name) if audio_root is not None else Path(file_name),
            source_url=str(record.get("sourceUrl", "")).strip(),
            description_url=str(record.get("descriptionUrl", "")).strip(),
            speaker=str(record.get("speaker", "")).strip(),
            license_name=str(record.get("license", "")).strip(),
            sha256=str(record.get("sha256", "")).strip(),
        )
        if not all((source.text, file_name, source.source_url, source.description_url, source.speaker, source.license_name, source.sha256)):
            raise ValueError(f"incomplete human audio provenance for {text!r}")
        key = normalize_text(text)
        if key in result:
            raise ValueError(f"duplicate human audio text: {text}")
        result[key] = source
    return result


def plan_production(
    words: Sequence[dict],
    overrides: PronunciationOverrides,
    human_audio: dict[str, HumanAudioSource] | None = None,
    require_ipa_alignment: bool = True,
) -> list[ProductionEntryPlan]:
    human_audio = human_audio or {}
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
        ipa_groups = IPA_GROUP.findall(str(word.get("phonetic", "")))
        if ipa_groups and len(ipa_groups) != len(variants) and require_ipa_alignment:
            raise ValueError(
                f"IPA group count does not match pronunciation variants: {word_id}"
            )
        if len(ipa_groups) != len(variants):
            ipa_groups = [""] * len(variants)

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
            spoken_text, expected_transcript, override_key = resolve_audio_texts(
                override_word,
                display_text,
                overrides,
            )
            human_source = human_audio.get(normalize_text(display_text))
            segments.append(
                ProductionSegmentPlan(
                    index=index,
                    display_text=display_text,
                    spoken_text=spoken_text,
                    override_key=override_key,
                    expected_ipa=ipa_groups[index],
                    source_type="human" if human_source is not None else "piper",
                    human_source=human_source,
                    expected_transcript=expected_transcript,
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
