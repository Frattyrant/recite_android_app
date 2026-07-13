"""Audio profiles and auditable pronunciation overrides for MIearn."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class AudioProfile:
    name: str
    model_sha256: str
    bit_rate_kbps: int
    application: str
    source_sample_rate: int = 22_050
    encoded_sample_rate: int = 48_000


LESSAC_MEDIUM = AudioProfile(
    name="en_US-lessac-medium",
    model_sha256="5efe09e69902187827af646e1a6e9d269dee769f9877d17b16b1b46eeaaf019f",
    bit_rate_kbps=32,
    application="voip",
)

LESSAC_HIGH = AudioProfile(
    name="en_US-lessac-high",
    model_sha256="4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09",
    bit_rate_kbps=40,
    application="audio",
)


@dataclass(frozen=True)
class PronunciationRule:
    spoken_text: str
    rule_type: str
    note: str = ""


@dataclass(frozen=True)
class PronunciationOverrides:
    by_word_id: dict[str, PronunciationRule]
    exact_text: dict[str, PronunciationRule]

    @classmethod
    def empty(cls) -> "PronunciationOverrides":
        return cls(by_word_id={}, exact_text={})

    @classmethod
    def from_json(cls, payload: dict) -> "PronunciationOverrides":
        if payload.get("schemaVersion") != 1:
            raise ValueError("pronunciation override schemaVersion must be 1")

        def parse_group(name: str) -> dict[str, PronunciationRule]:
            raw_group = payload.get(name, {})
            if not isinstance(raw_group, dict):
                raise ValueError(f"{name} must be an object")
            parsed: dict[str, PronunciationRule] = {}
            for raw_key, raw_rule in raw_group.items():
                key = str(raw_key).strip()
                if not key or not isinstance(raw_rule, dict):
                    raise ValueError(f"{name} keys and rules must be non-empty objects")
                spoken = str(raw_rule.get("spokenText", "")).strip()
                if not spoken:
                    raise ValueError(f"{name}:{key} requires non-empty spokenText")
                rule_type = str(raw_rule.get("type", "")).strip()
                if not rule_type:
                    raise ValueError(f"{name}:{key} requires non-empty type")
                parsed[key] = PronunciationRule(
                    spoken_text=spoken,
                    rule_type=rule_type,
                    note=str(raw_rule.get("note", "")).strip(),
                )
            return parsed

        return cls(
            by_word_id=parse_group("wordId"),
            exact_text=parse_group("exactText"),
        )


def load_pronunciation_overrides(path: Path) -> PronunciationOverrides:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("pronunciation overrides root must be an object")
    return PronunciationOverrides.from_json(payload)


def resolve_spoken_text(
    word: dict,
    display_text: str,
    overrides: PronunciationOverrides,
) -> tuple[str, str | None]:
    word_id = str(word.get("id", "")).strip()
    if word_id in overrides.by_word_id:
        return overrides.by_word_id[word_id].spoken_text, f"wordId:{word_id}"

    normalized_display = display_text.strip()
    if normalized_display in overrides.exact_text:
        return (
            overrides.exact_text[normalized_display].spoken_text,
            f"exactText:{normalized_display}",
        )

    from tools.generate_audio import spoken_text

    fallback_word = dict(word)
    fallback_word["audioText"] = normalized_display
    return spoken_text(fallback_word), None
