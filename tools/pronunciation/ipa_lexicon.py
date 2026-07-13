"""General American IPA lookup with explicit technical and G2P fallbacks."""

from __future__ import annotations

import re
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Callable, Mapping

from tools.generate_variant_audio import raw_variants


TOKEN = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?|[A-Z](?:[A-Z0-9&]*[A-Z0-9])?|\d+(?:\.\d+)?")
MEASUREMENT = re.compile(r"\b(\d+(?:\.\d+)?)\s*(mm|cm|kg|m)\b", re.IGNORECASE)
NUMBER = re.compile(r"\b\d+(?:\.\d+)?\b")
SMALL_NUMBERS = (
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
    "seventeen", "eighteen", "nineteen",
)
TENS = ("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
UNITS = {
    "mm": ("millimeter", "millimeters"),
    "cm": ("centimeter", "centimeters"),
    "kg": ("kilogram", "kilograms"),
    "m": ("meter", "meters"),
}
LETTER_IPA = {
    "A": "eɪ",
    "B": "biː",
    "C": "siː",
    "D": "diː",
    "E": "iː",
    "F": "ɛf",
    "G": "dʒiː",
    "H": "eɪtʃ",
    "I": "aɪ",
    "J": "dʒeɪ",
    "K": "keɪ",
    "L": "ɛl",
    "M": "ɛm",
    "N": "ɛn",
    "O": "oʊ",
    "P": "piː",
    "Q": "kjuː",
    "R": "ɑɹ",
    "S": "ɛs",
    "T": "tiː",
    "U": "juː",
    "V": "viː",
    "W": "ˈdʌbəljuː",
    "X": "ɛks",
    "Y": "waɪ",
    "Z": "ziː",
}


def _strip_slashes(value: str) -> str:
    return value.strip().strip("/").strip()


def _integer_words(value: int) -> str:
    if value < 20:
        return SMALL_NUMBERS[value]
    if value < 100:
        return " ".join(part for part in (TENS[value // 10], SMALL_NUMBERS[value % 10] if value % 10 else "") if part)
    if value < 1_000:
        return " ".join(
            part
            for part in (
                SMALL_NUMBERS[value // 100],
                "hundred",
                _integer_words(value % 100) if value % 100 else "",
            )
            if part
        )
    if value < 10_000:
        return " ".join(
            part
            for part in (
                SMALL_NUMBERS[value // 1_000],
                "thousand",
                _integer_words(value % 1_000) if value % 1_000 else "",
            )
            if part
        )
    return " ".join(SMALL_NUMBERS[int(digit)] for digit in str(value))


def _number_words(value: str) -> str:
    if "." not in value:
        return _integer_words(int(value))
    whole, fraction = value.split(".", 1)
    return f"{_integer_words(int(whole))} point {' '.join(SMALL_NUMBERS[int(digit)] for digit in fraction)}"


def _expand_numbers_and_measurements(text: str) -> str:
    def measurement(match: re.Match[str]) -> str:
        number = match.group(1)
        singular, plural = UNITS[match.group(2).casefold()]
        unit = singular if float(number) == 1 else plural
        return f"{_number_words(number)} {unit}"

    expanded = MEASUREMENT.sub(measurement, text)
    return NUMBER.sub(lambda match: _number_words(match.group(0)), expanded)


def _default_espeak_fallback(text: str) -> str:
    from piper.espeakbridge import get_phonemes, initialize, set_voice
    from piper.phonemize_espeak import ESPEAK_DATA_DIR

    if not getattr(_default_espeak_fallback, "initialized", False):
        initialize(str(ESPEAK_DATA_DIR))
        setattr(_default_espeak_fallback, "initialized", True)
    set_voice("en-us")
    clauses = get_phonemes(text)
    return " ".join(str(phonemes).strip() for phonemes, _, _ in clauses if str(phonemes).strip())


@dataclass(frozen=True)
class IpaResolution:
    display: str
    source: str
    fallback_tokens: list[str]


@dataclass(frozen=True)
class EntryIpaResolution:
    display: str
    variants: list[str]
    sources: list[str]
    fallback_tokens: list[str]


@dataclass(frozen=True)
class IpaLexicon:
    entries: Mapping[str, tuple[str, ...]]
    overrides: Mapping[str, str]
    fallback: Callable[[str], str]

    @classmethod
    def from_tsv(cls, path: Path) -> "IpaLexicon":
        entries: dict[str, tuple[str, ...]] = {}
        for line_number, raw_line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if not raw_line.strip():
                continue
            try:
                raw_word, raw_ipa = raw_line.split("\t", 1)
            except ValueError as error:
                raise ValueError(f"invalid IPA dictionary line {line_number}") from error
            pronunciations = tuple(
                value
                for value in (_strip_slashes(part) for part in raw_ipa.split(","))
                if value
            )
            if pronunciations:
                entries[raw_word.strip().casefold()] = pronunciations
        return cls(entries=entries, overrides={}, fallback=_default_espeak_fallback)

    def with_overrides(self, overrides: Mapping[str, str]) -> "IpaLexicon":
        normalized = {str(key).strip(): _strip_slashes(str(value)) for key, value in overrides.items()}
        if any(not key or not value for key, value in normalized.items()):
            raise ValueError("IPA overrides require non-empty text and pronunciation")
        return replace(self, overrides=normalized)

    def with_fallback(self, fallback: Callable[[str], str]) -> "IpaLexicon":
        return replace(self, fallback=fallback)

    def resolve_text(self, text: str) -> IpaResolution:
        normalized = text.strip()
        if not normalized:
            raise ValueError("IPA input cannot be blank")
        if normalized in self.overrides:
            return IpaResolution(f"/{self.overrides[normalized]}/", "reviewed-override", [])
        exact = self.entries.get(normalized.casefold())
        if exact:
            return IpaResolution(f"/{exact[0]}/", "ipa-dict", [])

        normalized = _expand_numbers_and_measurements(normalized)

        tokens = TOKEN.findall(normalized)
        if not tokens:
            raise ValueError(f"IPA input has no English tokens: {text!r}")
        phonetics: list[str] = []
        sources: list[str] = []
        fallback_tokens: list[str] = []
        for token in tokens:
            if token in self.overrides:
                phonetics.append(self.overrides[token])
                sources.append("reviewed-override")
                continue
            candidates = self.entries.get(token.casefold())
            if candidates:
                phonetics.append(candidates[0])
                sources.append("ipa-dict")
                continue
            if token.isupper() and token.isalpha() and len(token) <= 6:
                phonetics.append(" ".join(LETTER_IPA[letter] for letter in token))
                sources.append("letter-name")
                continue
            fallback_ipa = _strip_slashes(self.fallback(token))
            if not fallback_ipa:
                raise ValueError(f"IPA fallback returned blank value for {token!r}")
            phonetics.append(fallback_ipa)
            sources.append("espeak-fallback")
            fallback_tokens.append(token)
        source = sources[0] if len(set(sources)) == 1 else "mixed"
        return IpaResolution(f"/{' '.join(phonetics)}/", source, fallback_tokens)


def resolve_entry_phonetic(word: Mapping[str, object], lexicon: IpaLexicon) -> EntryIpaResolution:
    english = str(word.get("english", ""))
    kind = str(word.get("kind", "TERM"))
    variants = raw_variants(english, kind)
    if not variants:
        raise ValueError(f"entry has no IPA variants: {word.get('id', '<unknown>')}")
    resolutions = [lexicon.resolve_text(variant) for variant in variants]
    return EntryIpaResolution(
        display="； ".join(result.display for result in resolutions),
        variants=variants,
        sources=[result.source for result in resolutions],
        fallback_tokens=[token for result in resolutions for token in result.fallback_tokens],
    )
