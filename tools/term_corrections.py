"""Load, validate, and apply reviewed technical-term corrections."""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from urllib.parse import urlparse


APPROVED_CATEGORIES = {"mechanical", "electrical"}
STANDARDS_ORGANIZATIONS = {"IEC", "ISO", "NIST"}


@dataclass(frozen=True)
class Evidence:
    organization: str
    url: str
    accessed_on: str


@dataclass(frozen=True)
class TermCorrection:
    category: str
    source_index: int
    original_english: str
    corrected_english: str
    reason: str
    evidence: tuple[Evidence, ...]


def _required_text(item: dict, key: str) -> str:
    value = item.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"missing non-empty {key}")
    return value.strip()


def _parse_correction(item: dict) -> TermCorrection:
    evidence_items = item.get("evidence")
    if not isinstance(evidence_items, list):
        raise ValueError("evidence must be a list")
    evidence = tuple(
        Evidence(
            organization=_required_text(entry, "organization"),
            url=_required_text(entry, "url"),
            accessed_on=_required_text(entry, "accessedOn"),
        )
        for entry in evidence_items
    )
    source_index = item.get("sourceIndex")
    if not isinstance(source_index, int):
        raise ValueError("sourceIndex must be an integer")
    return TermCorrection(
        category=_required_text(item, "category"),
        source_index=source_index,
        original_english=_required_text(item, "originalEnglish"),
        corrected_english=_required_text(item, "correctedEnglish"),
        reason=_required_text(item, "reason"),
        evidence=evidence,
    )


def validate_correction(correction: TermCorrection) -> None:
    if correction.category not in APPROVED_CATEGORIES:
        raise ValueError(f"unknown correction category: {correction.category}")
    if correction.source_index <= 0:
        raise ValueError("sourceIndex must be positive")
    if correction.original_english == correction.corrected_english:
        raise ValueError("corrected English must differ from original English")
    if not correction.evidence:
        raise ValueError("correction evidence is required")

    organizations: set[str] = set()
    for evidence in correction.evidence:
        if urlparse(evidence.url).scheme != "https":
            raise ValueError("evidence URL must use HTTPS")
        try:
            date.fromisoformat(evidence.accessed_on)
        except ValueError as error:
            raise ValueError("evidence accessedOn must be an ISO date") from error
        organizations.add(evidence.organization)

    has_standard = any(
        organization.upper() in STANDARDS_ORGANIZATIONS
        for organization in organizations
    )
    if not has_standard and len(organizations) < 2:
        raise ValueError(
            "correction needs two independent organizations or a standards source"
        )


def load_corrections(path: Path) -> dict[tuple[str, int], TermCorrection]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("schemaVersion") != 1:
        raise ValueError("unsupported correction schemaVersion")
    items = payload.get("corrections")
    if not isinstance(items, list):
        raise ValueError("corrections must be a list")

    corrections: dict[tuple[str, int], TermCorrection] = {}
    for item in items:
        if not isinstance(item, dict):
            raise ValueError("each correction must be an object")
        correction = _parse_correction(item)
        validate_correction(correction)
        identity = (correction.category, correction.source_index)
        if identity in corrections:
            raise ValueError(f"duplicate correction identity: {identity}")
        corrections[identity] = correction
    return corrections


def apply_correction(record: dict, correction: TermCorrection) -> dict:
    identity = (record.get("category"), record.get("sourceIndex"))
    expected = (correction.category, correction.source_index)
    if identity != expected:
        raise ValueError(
            f"correction identity {expected} does not match record {identity}"
        )
    if record.get("english") != correction.original_english:
        raise ValueError(
            f"record original English does not match correction for {expected}"
        )

    updated = dict(record)
    for field in ("english", "primaryEnglish", "exampleEn", "audioText"):
        updated[field] = correction.corrected_english
    return updated
