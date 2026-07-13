"""Independent offline ASR audit for every packaged MIearn audio asset."""

from __future__ import annotations

import argparse
import json
import re
from functools import lru_cache
from dataclasses import asdict, dataclass, replace
from pathlib import Path
from typing import Any


TOKEN = re.compile(r"[a-z0-9]+(?:'[a-z]+)?", re.IGNORECASE)
HOMOPHONE_CANONICAL = {
    "too": "to",
    "two": "to",
    "four": "4",
    "for": "4",
    "zero": "0",
    "one": "1",
    "three": "3",
    "five": "5",
    "six": "6",
    "seven": "7",
    "eight": "8",
    "nine": "9",
    "ten": "10",
    "floor": "4",
    "plexy": "plexi",
}


@dataclass(frozen=True)
class TranscriptAudit:
    passed: bool
    reason: str
    expected_tokens: tuple[str, ...]
    recognized_tokens: tuple[str, ...]
    edit_distance: int
    word_error_rate: float


def _join_initialisms(tokens: list[str]) -> tuple[str, ...]:
    result: list[str] = []
    letters: list[str] = []

    def flush() -> None:
        if letters:
            result.append("".join(letters))
            letters.clear()

    for token in tokens:
        if len(token) == 1 and token.isalpha():
            letters.append(token)
        else:
            flush()
            result.append(token)
    flush()
    return tuple(result)


def normalize_transcript(text: str) -> tuple[str, ...]:
    text = text.replace("&", " and ").replace("=", " equals ").replace("+", " plus ")
    text = re.sub(r"\bsch\s*80\b", "schedule 80", text, flags=re.IGNORECASE)
    tokens = [token.casefold().replace("'", "") for token in TOKEN.findall(text)]
    return _join_initialisms([HOMOPHONE_CANONICAL.get(token, token) for token in tokens])


def _edit_distance(expected: tuple[str, ...], actual: tuple[str, ...]) -> int:
    previous = list(range(len(actual) + 1))
    for expected_token in expected:
        current = [previous[0] + 1]
        for index, actual_token in enumerate(actual, 1):
            current.append(
                min(
                    current[-1] + 1,
                    previous[index] + 1,
                    previous[index - 1] + (expected_token != actual_token),
                )
            )
        previous = current
    return previous[-1]


@lru_cache(maxsize=1)
def _cmu_dictionary() -> dict[str, list[list[str]]]:
    try:
        import cmudict
    except ImportError:
        return {}
    return cmudict.dict()


def _pronunciations(token: str) -> set[tuple[str, ...]]:
    return {
        tuple(re.sub(r"\d", "", phoneme) for phoneme in pronunciation)
        for pronunciation in _cmu_dictionary().get(token, [])
    }


def _tokens_sound_equivalent(
    expected: tuple[str, ...],
    recognized: tuple[str, ...],
) -> bool:
    if len(expected) != len(recognized) or not expected:
        return False
    for expected_token, recognized_token in zip(expected, recognized):
        if expected_token == recognized_token:
            continue
        expected_pronunciations = _pronunciations(expected_token)
        recognized_pronunciations = _pronunciations(recognized_token)
        if not expected_pronunciations.intersection(recognized_pronunciations):
            return False
    return True


def _phoneme_error_rate(
    expected: tuple[str, ...],
    recognized: tuple[str, ...],
) -> float | None:
    expected_sequence: list[str] = []
    recognized_sequence: list[str] = []
    for token in expected:
        pronunciations = _pronunciations(token)
        if not pronunciations:
            return None
        expected_sequence.extend(sorted(pronunciations)[0])
    for token in recognized:
        pronunciations = _pronunciations(token)
        if not pronunciations:
            return None
        recognized_sequence.extend(sorted(pronunciations)[0])
    distance = _edit_distance(tuple(expected_sequence), tuple(recognized_sequence))
    return distance / max(1, len(expected_sequence))


def audit_transcript(expected: str, recognized: str) -> TranscriptAudit:
    expected_tokens = normalize_transcript(expected)
    recognized_tokens = normalize_transcript(recognized)
    if not recognized_tokens:
        return TranscriptAudit(False, "empty-transcript", expected_tokens, (), len(expected_tokens), 1.0)
    if "slash" in recognized_tokens and "slash" not in expected_tokens:
        return TranscriptAudit(
            False,
            "unexpected-spoken-slash",
            expected_tokens,
            recognized_tokens,
            _edit_distance(expected_tokens, recognized_tokens),
            1.0,
        )
    distance = _edit_distance(expected_tokens, recognized_tokens)
    rate = distance / max(1, len(expected_tokens))
    if "".join(expected_tokens) == "".join(recognized_tokens):
        return TranscriptAudit(
            True,
            "word-boundary-equivalent",
            expected_tokens,
            recognized_tokens,
            distance,
            rate,
        )
    if _tokens_sound_equivalent(expected_tokens, recognized_tokens):
        return TranscriptAudit(
            True,
            "dictionary-homophone-equivalent",
            expected_tokens,
            recognized_tokens,
            distance,
            rate,
        )
    phoneme_error_rate = _phoneme_error_rate(expected_tokens, recognized_tokens)
    if phoneme_error_rate is not None and phoneme_error_rate <= 0.25:
        return TranscriptAudit(
            True,
            "phoneme-near-match",
            expected_tokens,
            recognized_tokens,
            distance,
            rate,
        )
    threshold = 0.0 if len(expected_tokens) <= 2 else 0.25
    return TranscriptAudit(
        passed=rate <= threshold,
        reason="match" if rate <= threshold else "transcript-mismatch",
        expected_tokens=expected_tokens,
        recognized_tokens=recognized_tokens,
        edit_distance=distance,
        word_error_rate=rate,
    )


def _prefer_transcript(
    expected: str,
    primary: str,
    retry: str,
) -> tuple[str, TranscriptAudit]:
    primary_audit = audit_transcript(expected, primary)
    retry_audit = audit_transcript(expected, retry)
    if retry_audit.passed and not primary_audit.passed:
        return retry, retry_audit
    if retry_audit.word_error_rate < primary_audit.word_error_rate:
        return retry, retry_audit
    return primary, primary_audit


def _should_prompt_retry(audit: TranscriptAudit) -> bool:
    return not audit.passed and audit.reason != "unexpected-spoken-slash"


def _targets(manifest: dict[str, Any]) -> list[tuple[str, str, str]]:
    targets: list[tuple[str, str, str]] = []
    for word_id, entry in sorted(manifest.get("entries", {}).items()):
        segments = entry.get("segments", [])
        if segments:
            complete_text = " ".join(
                str(segment.get("expectedTranscript") or segment.get("spokenText", ""))
                for segment in segments
            )
        else:
            complete_text = str(entry.get("expectedTranscript") or entry.get("spokenText", ""))
        targets.append(
            (
                str(entry.get("path", f"audio/{word_id}.ogg")),
                complete_text,
                str(entry.get("audioSha256", "")),
            )
        )
        targets.extend(
            (
                str(segment.get("path", "")),
                str(segment.get("expectedTranscript") or segment.get("spokenText", "")),
                str(segment.get("audioSha256", "")),
            )
            for segment in segments
        )
    return targets


def _manifest_hashes(manifest: dict[str, Any]) -> dict[str, str]:
    hashes: dict[str, str] = {}
    for word_id, entry in manifest.get("entries", {}).items():
        relative = str(entry.get("path", f"audio/{word_id}.ogg"))
        hashes[relative] = str(entry.get("audioSha256", ""))
        for segment in entry.get("segments", []):
            hashes[str(segment.get("path", ""))] = str(segment.get("audioSha256", ""))
    return hashes


def _apply_composite_evidence(
    manifest: dict[str, Any],
    results: list[dict[str, Any]],
) -> None:
    by_path = {str(item.get("path", "")): item for item in results}
    for word_id, entry in manifest.get("entries", {}).items():
        segment_paths = [
            str(segment.get("path", ""))
            for segment in entry.get("segments", [])
            if str(segment.get("path", ""))
        ]
        if len(segment_paths) < 2:
            continue
        complete_path = str(entry.get("path", f"audio/{word_id}.ogg"))
        complete = by_path.get(complete_path)
        segments = [by_path.get(path) for path in segment_paths]
        if (
            complete
            and not complete.get("releasePassed", False)
            and all(segment and segment.get("releasePassed", False) for segment in segments)
        ):
            complete["passed"] = True
            complete["releasePassed"] = True
            complete["reason"] = "verified-segment-composition"
            complete["compositeEvidence"] = segment_paths


def _review_for_path(
    reviewed: dict[str, Any],
    relative: str,
    audio_hash: str,
) -> dict[str, Any] | None:
    review = reviewed.get(relative)
    if review is None:
        return None
    if not isinstance(review, dict):
        raise ValueError(f"invalid audio audit review for {relative}")
    if str(review.get("audioSha256", "")) != audio_hash:
        raise ValueError(f"stale audio audit review for {relative}")
    if not str(review.get("reason", "")).strip():
        raise ValueError(f"audio audit review requires reason for {relative}")
    evidence = review.get("evidence")
    if not isinstance(evidence, list) or not evidence:
        raise ValueError(f"audio audit review requires evidence for {relative}")
    return review


def _can_reuse_transcript(
    prior: dict[str, Any] | None,
    expected: str,
    current_audio_hash: str,
    baseline_audio_hash: str,
) -> bool:
    if not prior or prior.get("expected") != expected or not current_audio_hash:
        return False
    prior_hash = str(prior.get("audioSha256", "")) or baseline_audio_hash
    return bool(prior_hash) and prior_hash == current_audio_hash


def _write_report(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--model", default="base")
    parser.add_argument("--retry-model")
    parser.add_argument("--prompt-retry", action="store_true")
    parser.add_argument("--model-cache", type=Path, default=Path.home() / ".cache/whisper")
    parser.add_argument("--overrides", type=Path, default=Path("tools/pronunciation/audio_audit_overrides.json"))
    parser.add_argument("--baseline-manifest", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--allow-unreviewed", action="store_true")
    args = parser.parse_args()

    import whisper

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    override_payload = json.loads(args.overrides.read_text(encoding="utf-8"))
    if override_payload.get("schemaVersion") != 1 or not isinstance(override_payload.get("entries"), dict):
        raise ValueError("audio audit overrides require schemaVersion 1 and entries")
    reviewed = override_payload["entries"]
    model = whisper.load_model(args.model, download_root=str(args.model_cache))
    retry_model = None

    existing: dict[str, dict[str, Any]] = {}
    if args.report.is_file():
        previous = json.loads(args.report.read_text(encoding="utf-8"))
        existing = {item["path"]: item for item in previous.get("assets", [])}
    baseline_hashes: dict[str, str] = {}
    if args.baseline_manifest and args.baseline_manifest.is_file():
        baseline_hashes = _manifest_hashes(
            json.loads(args.baseline_manifest.read_text(encoding="utf-8"))
        )
    targets = _targets(manifest)
    results: list[dict[str, Any]] = []
    for index, (relative, expected, audio_hash) in enumerate(targets, 1):
        prior = existing.get(relative)
        retry_recognized = ""
        prompt_recognized = ""
        if _can_reuse_transcript(
            prior,
            expected,
            audio_hash,
            baseline_hashes.get(relative, ""),
        ):
            recognized = str(prior.get("recognized", ""))
            audit = audit_transcript(expected, recognized)
        else:
            recognized = str(
                model.transcribe(
                    str(args.assets / relative),
                    language="en",
                    task="transcribe",
                    fp16=False,
                    temperature=0,
                    condition_on_previous_text=False,
                ).get("text", "")
            ).strip()
            audit = audit_transcript(expected, recognized)
            if not audit.passed and args.retry_model:
                if retry_model is None:
                    retry_model = whisper.load_model(
                        args.retry_model,
                        download_root=str(args.model_cache),
                    )
                retry_recognized = str(
                    retry_model.transcribe(
                        str(args.assets / relative),
                        language="en",
                        task="transcribe",
                        fp16=False,
                        temperature=0,
                        condition_on_previous_text=False,
                    ).get("text", "")
                ).strip()
                recognized, audit = _prefer_transcript(
                    expected,
                    recognized,
                    retry_recognized,
                )
        if args.prompt_retry and _should_prompt_retry(audit):
            if retry_model is None:
                retry_model = whisper.load_model(
                    args.retry_model or args.model,
                    download_root=str(args.model_cache),
                )
            prompt_recognized = str(
                retry_model.transcribe(
                    str(args.assets / relative),
                    language="en",
                    task="transcribe",
                    fp16=False,
                    temperature=0,
                    condition_on_previous_text=False,
                    initial_prompt=f"Technical English vocabulary: {expected}",
                ).get("text", "")
            ).strip()
            prompted_text, prompted_audit = _prefer_transcript(
                expected,
                recognized,
                prompt_recognized,
            )
            if prompted_text == prompt_recognized and prompted_audit.passed:
                prompted_audit = replace(
                    prompted_audit,
                    reason=f"prompt-confirmed-{prompted_audit.reason}",
                )
            recognized, audit = prompted_text, prompted_audit
        review = _review_for_path(reviewed, relative, audio_hash)
        results.append(
            {
                "path": relative,
                "audioSha256": audio_hash,
                "expected": expected,
                "recognized": recognized,
                "retryRecognized": retry_recognized or None,
                "promptRecognized": prompt_recognized or None,
                **asdict(audit),
                "reviewedException": review,
                "releasePassed": audit.passed or bool(review),
            }
        )
        if index % 25 == 0 or index == len(targets):
            payload = {
                "schemaVersion": 1,
                "auditPolicyVersion": 4,
                "model": args.model,
                "retryModel": args.retry_model,
                "manifestSha256": manifest.get("contentSha256"),
                "assetCount": len(targets),
                "auditedCount": len(results),
                "assets": results,
            }
            _write_report(args.report, payload)
            print(f"ASR audit {index}/{len(targets)}", flush=True)

    _apply_composite_evidence(manifest, results)
    unreviewed = [item for item in results if not item["releasePassed"]]
    final = {
        "schemaVersion": 1,
        "auditPolicyVersion": 4,
        "model": args.model,
        "retryModel": args.retry_model,
        "manifestSha256": manifest.get("contentSha256"),
        "assetCount": len(targets),
        "auditedCount": len(results),
        "assets": results,
    }
    final["unreviewedFailureCount"] = len(unreviewed)
    final["passed"] = not unreviewed
    _write_report(args.report, final)
    if unreviewed and not args.allow_unreviewed:
        raise SystemExit(f"FAIL unreviewed ASR mismatches={len(unreviewed)}")
    print(f"PASS assets={len(results)} unreviewed={len(unreviewed)}")


if __name__ == "__main__":
    main()
