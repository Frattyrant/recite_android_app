"""Validate every staged MIearn v2.3 production audio asset."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Callable, Sequence

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.generate_variant_audio import raw_variants, segment_plan_sha256


Probe = Callable[[Path], dict]
HIGH_MODEL_SHA256 = "4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09"


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _validate_file(
    root: Path,
    metadata: dict,
    probe: Probe,
    label: str,
    errors: list[str],
) -> dict | None:
    relative = str(metadata.get("path", ""))
    path = root / relative
    if not relative or not path.is_file():
        errors.append(f"{label}: missing audio file {relative}")
        return None
    if path.stat().st_size != metadata.get("bytes"):
        errors.append(f"{label}: byte count mismatch for {relative}")
    if _sha256(path) != metadata.get("audioSha256"):
        errors.append(f"{label}: hash mismatch for {relative}")
    try:
        metrics = probe(path)
    except Exception as error:
        errors.append(f"{label}: decode failed for {relative}: {error}")
        return None
    codec = metrics.get("codec", metadata.get("codec"))
    channels = int(metrics.get("channels", metadata.get("channels", 0)))
    sample_rate = int(metrics.get("sampleRate", metadata.get("sampleRate", 0)))
    if codec != "opus":
        errors.append(f"{label}: codec is not Opus for {relative}")
    if channels != 1:
        errors.append(f"{label}: channel count is not mono for {relative}")
    if sample_rate != 48_000:
        errors.append(f"{label}: sample rate is not 48000 for {relative}")
    duration = float(metrics.get("durationSeconds", 0.0))
    if duration <= 0.0:
        errors.append(f"{label}: zero duration for {relative}")
    if (
        float(metrics.get("maxVolumeDb", -100.0)) <= -45.0
        or float(metrics.get("meanVolumeDb", -100.0)) <= -55.0
    ):
        errors.append(f"{label}: silent or near-silent audio for {relative}")
    return metrics


def validate_production(
    root: Path,
    words: Sequence[dict],
    probe: Probe,
    expected_count: int = 2_704,
    manifest_path: Path | None = None,
) -> dict:
    manifest_path = manifest_path or root / "audio_manifest_v1.json"
    if not manifest_path.is_file():
        return {"passed": False, "validatedEntries": 0, "errors": ["manifest missing"]}
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    errors: list[str] = []
    if len(words) != expected_count:
        errors.append(f"word count is {len(words)}, expected {expected_count}")
    if payload.get("schemaVersion") != 2:
        errors.append("unsupported production manifest schema")
    profile = payload.get("profile", {})
    expected_profile = {
        "name": "en_US-lessac-high",
        "bitRateKbps": 40,
        "application": "audio",
        "channels": 1,
        "encodedSampleRate": 48_000,
    }
    if any(profile.get(key) != value for key, value in expected_profile.items()):
        errors.append("production profile does not match Lessac High 40k audio")
    if profile.get("modelSha256") != HIGH_MODEL_SHA256:
        errors.append("production model SHA-256 does not match approved Lessac High")
    entries = payload.get("entries", {})
    if not isinstance(entries, dict):
        errors.append("manifest entries must be an object")
        entries = {}
    word_ids = [str(word.get("id", "")) for word in words]
    if len(set(word_ids)) != len(word_ids) or any(not word_id for word_id in word_ids):
        errors.append("content requires unique non-empty word IDs")
    if set(entries) != set(word_ids):
        errors.append("manifest IDs do not exactly match content IDs")
    if payload.get("entryCount") != expected_count:
        errors.append("manifest entryCount does not match expected count")

    output_paths: set[str] = set()
    for word in words:
        word_id = str(word.get("id", ""))
        entry = entries.get(word_id)
        if not isinstance(entry, dict):
            continue
        complete_metrics = _validate_file(root, entry, probe, f"{word_id}:complete", errors)
        variants = raw_variants(str(word.get("english", "")), str(word.get("kind", "TERM")))
        expected_segment_texts = variants if len(variants) > 1 else []
        segments = entry.get("segments", [])
        if len(segments) != len(expected_segment_texts):
            errors.append(f"{word_id}: segment count mismatch")
            segments = segments if isinstance(segments, list) else []
        if entry.get("segmentPlanSha256") != segment_plan_sha256(variants):
            errors.append(f"{word_id}: segment plan hash mismatch")
        expected_pause_ms = 500 if len(variants) > 1 else None
        if entry.get("pauseBetweenSegmentsMs") != expected_pause_ms:
            errors.append(f"{word_id}: pause metadata mismatch")
        segment_metrics: list[dict] = []
        for index, (display_text, segment) in enumerate(zip(expected_segment_texts, segments)):
            if segment.get("index") != index or segment.get("text") != display_text:
                errors.append(f"{word_id}: segment {index} text or index mismatch")
            metrics = _validate_file(root, segment, probe, f"{word_id}:segment:{index}", errors)
            if metrics is not None:
                segment_metrics.append(metrics)
            relative = str(segment.get("path", ""))
            if relative in output_paths:
                errors.append(f"duplicate output path: {relative}")
            output_paths.add(relative)
        complete_relative = str(entry.get("path", ""))
        if complete_relative in output_paths:
            errors.append(f"duplicate output path: {complete_relative}")
        output_paths.add(complete_relative)
        if len(segment_metrics) > 1 and complete_metrics is not None:
            measured_pause = float(complete_metrics["durationSeconds"]) - sum(
                float(metrics["durationSeconds"]) for metrics in segment_metrics
            )
            expected_pause = (len(segment_metrics) - 1) * 0.5
            tolerance = 0.12 + (len(segment_metrics) - 1) * 0.005
            if abs(measured_pause - expected_pause) > tolerance:
                errors.append(
                    f"{word_id}: pause mismatch measured={measured_pause:.3f} "
                    f"expected={expected_pause:.3f}"
                )
    return {"passed": not errors, "validatedEntries": len(entries), "errors": errors}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--ffprobe", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    args = parser.parse_args()
    from tools.audio_trial import load_words
    from tools.validate_audio import probe_audio

    def probe(path: Path) -> dict:
        return {
            **probe_audio(path, args.ffprobe, args.ffmpeg),
            "codec": "opus", "channels": 1, "sampleRate": 48_000,
        }

    report = validate_production(
        args.root,
        load_words(args.content),
        probe,
        manifest_path=args.manifest,
    )
    report_path = args.root / "validation_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not report["passed"]:
        raise SystemExit("FAIL " + "; ".join(report["errors"][:20]))
    print(f"PASS entries={report['validatedEntries']}")


if __name__ == "__main__":
    main()
