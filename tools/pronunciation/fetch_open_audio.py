"""Discover and stage strictly reviewed CC0 en-US pronunciation recordings."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from tools.generate_variant_audio import raw_variants
from tools.pronunciation.commons_audio import (
    CommonsAudioCandidate,
    evaluate_candidate,
    normalize_text,
    recording_text_from_title,
)


API_URL = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "MIearn-audio-audit/2.32 (https://github.com/Frattyrant/recite_android_app)"
SINGLE_WORD = re.compile(r"[A-Za-z]+(?:[-'][A-Za-z]+)*\Z")


def batched(values: list[str], size: int) -> Iterable[list[str]]:
    for start in range(0, len(values), size):
        yield values[start : start + size]


def discoverable_texts(content: dict[str, Any]) -> list[str]:
    unique: dict[str, str] = {}
    for word in content.get("words", []):
        for variant in raw_variants(
            str(word.get("english", "")),
            str(word.get("kind", "TERM")),
        ):
            stripped = variant.strip()
            if SINGLE_WORD.fullmatch(stripped):
                unique.setdefault(normalize_text(stripped), stripped)
    return [unique[key] for key in sorted(unique)]


def _metadata_value(metadata: dict[str, Any], key: str) -> str:
    value = metadata.get(key, {})
    return str(value.get("value", "")).strip() if isinstance(value, dict) else ""


def query_candidates(
    texts: list[str],
    request_delay_seconds: float,
    cache_dir: Path,
) -> list[CommonsAudioCandidate]:
    titles = [f"File:En-us-{text}.ogg" for text in texts]
    candidates: list[CommonsAudioCandidate] = []
    for number, title_batch in enumerate(batched(titles, 40), 1):
        params = {
            "action": "query",
            "format": "json",
            "formatversion": "2",
            "redirects": "1",
            "titles": "|".join(title_batch),
            "prop": "imageinfo",
            "iiprop": "url|user|extmetadata",
        }
        cache_key = hashlib.sha256("\n".join(title_batch).encode("utf-8")).hexdigest()
        cache_path = cache_dir / f"{cache_key}.json"
        if cache_path.is_file():
            payload = json.loads(cache_path.read_text(encoding="utf-8"))
        else:
            last_error: Exception | None = None
            payload = {}
            for attempt in range(4):
                request = urllib.request.Request(
                    API_URL + "?" + urllib.parse.urlencode(params),
                    headers={"User-Agent": USER_AGENT},
                )
                try:
                    with urllib.request.urlopen(request, timeout=45) as response:
                        payload = json.load(response)
                    last_error = None
                    break
                except Exception as error:
                    last_error = error
                    if attempt < 3:
                        time.sleep(2**attempt)
            if last_error is not None:
                raise last_error
            cache_dir.mkdir(parents=True, exist_ok=True)
            cache_path.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
        for page in payload.get("query", {}).get("pages", []):
            if page.get("missing") or not page.get("imageinfo"):
                continue
            info = page["imageinfo"][0]
            metadata = info.get("extmetadata", {})
            categories = tuple(
                part.strip()
                for part in re.split(r"[|\n]", _metadata_value(metadata, "Categories"))
                if part.strip()
            )
            candidates.append(
                CommonsAudioCandidate(
                    title=str(page.get("title", "")),
                    source_url=str(info.get("url", "")),
                    description_url=str(info.get("descriptionurl", "")),
                    uploader=str(info.get("user", "")) or _metadata_value(metadata, "Artist"),
                    license_name=_metadata_value(metadata, "LicenseShortName"),
                    categories=categories,
                )
            )
        print(f"Commons metadata batch {number}/{(len(titles) + 39) // 40}", flush=True)
        if request_delay_seconds > 0 and number * 40 < len(titles):
            time.sleep(request_delay_seconds)
    return candidates


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _download(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.is_file() and target.stat().st_size > 0:
        return
    temporary = target.with_suffix(target.suffix + ".part")
    try:
        last_error: Exception | None = None
        for attempt in range(4):
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            try:
                with urllib.request.urlopen(request, timeout=90) as response:
                    temporary.write_bytes(response.read())
                last_error = None
                break
            except Exception as error:
                last_error = error
                temporary.unlink(missing_ok=True)
                if attempt < 3:
                    time.sleep(2**attempt)
        if last_error is not None:
            raise last_error
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--staging", type=Path, required=True)
    parser.add_argument("--allowlist", type=Path, required=True)
    parser.add_argument("--attributions", type=Path, required=True)
    parser.add_argument("--audit", type=Path, required=True)
    parser.add_argument("--request-delay-seconds", type=float, default=0.15)
    parser.add_argument("--metadata-cache", type=Path, default=Path("tmp/commons-metadata-v231"))
    args = parser.parse_args()

    content = json.loads(args.content.read_text(encoding="utf-8"))
    allowlist = json.loads(args.allowlist.read_text(encoding="utf-8"))
    if allowlist.get("schemaVersion") != 1 or not isinstance(allowlist.get("acceptedTitles"), list):
        raise ValueError("human audio allowlist requires schemaVersion 1 and acceptedTitles")
    accepted_titles = {str(title).casefold() for title in allowlist["acceptedTitles"]}

    texts = discoverable_texts(content)
    candidates = query_candidates(texts, args.request_delay_seconds, args.metadata_cache)
    candidate_audit: list[dict[str, Any]] = []
    records: list[dict[str, Any]] = []
    for candidate in sorted(candidates, key=lambda item: item.title.casefold()):
        expected_text = recording_text_from_title(candidate.title)
        decision = evaluate_candidate(candidate, expected_text)
        reviewed = candidate.title.casefold() in accepted_titles
        accepted = decision.accepted and reviewed
        reasons = list(decision.reasons)
        if decision.accepted and not reviewed:
            reasons.append("not-in-reviewed-allowlist")
        candidate_audit.append(
            {
                "title": candidate.title,
                "text": expected_text,
                "license": candidate.license_name,
                "uploader": candidate.uploader,
                "sourceUrl": candidate.source_url,
                "descriptionUrl": candidate.description_url,
                "categories": list(candidate.categories),
                "accepted": accepted,
                "reasons": reasons,
            }
        )
        if not accepted:
            continue
        extension = Path(urllib.parse.urlparse(candidate.source_url).path).suffix or ".ogg"
        filename = hashlib.sha256(expected_text.encode("utf-8")).hexdigest()[:20] + extension
        target = args.staging / filename
        _download(candidate.source_url, target)
        records.append(
            {
                "text": expected_text,
                "fileName": target.name,
                "sourceUrl": candidate.source_url,
                "descriptionUrl": candidate.description_url,
                "speaker": candidate.uploader,
                "license": candidate.license_name,
                "accentEvidence": [candidate.title, *candidate.categories],
                "bytes": target.stat().st_size,
                "sha256": sha256(target),
            }
        )

    audit = {
        "schemaVersion": 1,
        "contentVersion": content.get("contentVersion"),
        "queriedSingleWordCount": len(texts),
        "candidateCount": len(candidates),
        "policyEligibleCount": sum(not item["reasons"] or item["reasons"] == ["not-in-reviewed-allowlist"] for item in candidate_audit),
        "acceptedCount": len(records),
        "candidates": candidate_audit,
    }
    attributions = {
        "schemaVersion": 1,
        "contentVersion": content.get("contentVersion"),
        "records": records,
    }
    args.audit.parent.mkdir(parents=True, exist_ok=True)
    args.attributions.parent.mkdir(parents=True, exist_ok=True)
    args.audit.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.attributions.write_text(
        json.dumps(attributions, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(
        f"PASS queried={len(texts)} candidates={len(candidates)} accepted={len(records)}",
        flush=True,
    )


if __name__ == "__main__":
    main()
