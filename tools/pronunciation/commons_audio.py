"""Strict Wikimedia Commons pronunciation metadata policy for MIearn."""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from pathlib import PurePosixPath


EN_US_PREFIX = re.compile(r"^(?:en[-_]us)[-_ ]+", re.IGNORECASE)
US_ACCENT_MARKERS = (
    "u.s. english pronunciation",
    "us english pronunciation",
    "american english pronunciation",
    "en-us",
    "en_us",
)


@dataclass(frozen=True)
class CommonsAudioCandidate:
    title: str
    source_url: str
    description_url: str
    uploader: str
    license_name: str
    categories: tuple[str, ...]


@dataclass(frozen=True)
class CandidateDecision:
    accepted: bool
    reasons: tuple[str, ...]
    normalized_recording_text: str


def normalize_text(value: str) -> str:
    normalized = unicodedata.normalize("NFKC", value)
    normalized = normalized.replace("_", " ")
    return " ".join(normalized.casefold().split())


def recording_text_from_title(title: str) -> str:
    raw_name = title.split(":", 1)[-1]
    stem = PurePosixPath(raw_name).stem
    without_prefix = EN_US_PREFIX.sub("", stem)
    return normalize_text(without_prefix)


def evaluate_candidate(
    candidate: CommonsAudioCandidate,
    expected_text: str,
) -> CandidateDecision:
    recording_text = recording_text_from_title(candidate.title)
    reasons: list[str] = []
    if recording_text != normalize_text(expected_text):
        reasons.append("text-mismatch")

    normalized_license = normalize_text(candidate.license_name)
    if not (normalized_license.startswith("cc0") or normalized_license == "public domain"):
        reasons.append("license-not-cc0")

    accent_evidence = " ".join((candidate.title, *candidate.categories)).casefold()
    if not any(marker in accent_evidence for marker in US_ACCENT_MARKERS):
        reasons.append("us-accent-unverified")

    if not candidate.source_url or not candidate.description_url or not candidate.uploader:
        reasons.append("incomplete-provenance")
    return CandidateDecision(
        accepted=not reasons,
        reasons=tuple(reasons),
        normalized_recording_text=recording_text,
    )
