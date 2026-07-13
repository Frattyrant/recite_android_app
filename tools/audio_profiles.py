"""Audio profiles and auditable pronunciation overrides for MIearn."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from dataclasses import field
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
    audit_text: str = ""


@dataclass(frozen=True)
class PronunciationOverrides:
    by_word_id: dict[str, PronunciationRule]
    exact_text: dict[str, PronunciationRule]
    token_text: dict[str, PronunciationRule] = field(default_factory=dict)

    @classmethod
    def empty(cls) -> "PronunciationOverrides":
        return cls(by_word_id={}, exact_text={}, token_text={})

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
                    audit_text=str(raw_rule.get("auditText", "")).strip(),
                )
            return parsed

        return cls(
            by_word_id=parse_group("wordId"),
            exact_text=parse_group("exactText"),
            token_text=parse_group("tokenText"),
        )


def load_pronunciation_overrides(path: Path) -> PronunciationOverrides:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("pronunciation overrides root must be an object")
    return PronunciationOverrides.from_json(payload)


def resolve_audio_texts(
    word: dict,
    display_text: str,
    overrides: PronunciationOverrides,
) -> tuple[str, str, str | None]:
    word_id = str(word.get("id", "")).strip()
    if word_id in overrides.by_word_id:
        rule = overrides.by_word_id[word_id]
        return rule.spoken_text, rule.audit_text or rule.spoken_text, f"wordId:{word_id}"

    normalized_display = display_text.strip()
    if normalized_display in overrides.exact_text:
        rule = overrides.exact_text[normalized_display]
        return (
            rule.spoken_text,
            rule.audit_text or rule.spoken_text,
            f"exactText:{normalized_display}",
        )

    spoken_display = normalized_display
    audit_display = normalized_display
    applied_tokens: list[str] = []
    for token in sorted(overrides.token_text, key=len, reverse=True):
        rule = overrides.token_text[token]
        pattern = re.compile(
            rf"(?<![A-Za-z0-9]){re.escape(token)}(?![A-Za-z0-9])",
        )
        spoken_display, count = pattern.subn(rule.spoken_text, spoken_display)
        if count:
            audit_display = pattern.sub(rule.audit_text or rule.spoken_text, audit_display)
            applied_tokens.append(token)

    from tools.generate_audio import spoken_text

    fallback_word = dict(word)
    fallback_word["audioText"] = spoken_display
    audit_word = dict(word)
    audit_word["audioText"] = audit_display
    return (
        spoken_text(fallback_word),
        spoken_text(audit_word),
        "+".join(f"tokenText:{token}" for token in applied_tokens) or None,
    )


def resolve_spoken_text(
    word: dict,
    display_text: str,
    overrides: PronunciationOverrides,
) -> tuple[str, str | None]:
    spoken, _, key = resolve_audio_texts(word, display_text, overrides)
    return spoken, key
