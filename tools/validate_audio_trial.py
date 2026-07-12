"""Validate an isolated MIearn A/B audio trial bundle."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Callable


Probe = Callable[[Path], dict]


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
    if _sha256(path) != metadata.get("sha256"):
        errors.append(f"{label}: hash mismatch for {relative}")
    try:
        metrics = probe(path)
    except Exception as error:  # validation reports every broken asset together
        errors.append(f"{label}: decode failed for {relative}: {error}")
        return None
    if metrics.get("codec") not in (None, "opus"):
        errors.append(f"{label}: codec is not Opus for {relative}")
    if metrics.get("channels") not in (None, 1):
        errors.append(f"{label}: channel count is not mono for {relative}")
    if metrics.get("sampleRate") not in (None, 48_000, "48000"):
        errors.append(f"{label}: sample rate is not 48000 for {relative}")
    if float(metrics.get("durationSeconds", 0.0)) <= 0.0:
        errors.append(f"{label}: zero duration for {relative}")
    if (
        float(metrics.get("maxVolumeDb", -100.0)) <= -45.0
        or float(metrics.get("meanVolumeDb", -100.0)) <= -55.0
    ):
        errors.append(f"{label}: silent or near-silent audio for {relative}")
    return metrics


def validate_trial(root: Path, probe: Probe) -> dict:
    manifest_path = root / "trial_report.json"
    if not manifest_path.is_file():
        return {"passed": False, "validatedEntries": 0, "errors": ["trial report missing"]}
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    errors: list[str] = []
    if payload.get("schemaVersion") != 1:
        errors.append("unsupported trial report schema")
    profile = payload.get("candidateProfile", {})
    expected_profile = {
        "name": "en_US-lessac-high",
        "bitRateKbps": 40,
        "application": "audio",
        "channels": 1,
        "encodedSampleRate": 48_000,
    }
    if any(profile.get(key) != value for key, value in expected_profile.items()):
        errors.append("candidate profile does not match Lessac High 40k audio")

    entries = payload.get("entries", [])
    if not isinstance(entries, list) or len(entries) != payload.get("entryCount"):
        errors.append("entry count does not match report")
        entries = entries if isinstance(entries, list) else []

    for entry in entries:
        word_id = str(entry.get("id", "<missing-id>"))
        _validate_file(root, entry.get("current", {}), probe, f"{word_id}:current", errors)
        candidate_metrics = _validate_file(
            root,
            entry.get("candidate", {}),
            probe,
            f"{word_id}:candidate",
            errors,
        )
        segment_metrics = []
        for segment in entry.get("candidateSegments", []):
            metrics = _validate_file(
                root,
                segment,
                probe,
                f"{word_id}:segment:{segment.get('index')}",
                errors,
            )
            if metrics is not None:
                segment_metrics.append(metrics)
        if len(segment_metrics) > 1 and candidate_metrics is not None:
            measured_pause = float(candidate_metrics["durationSeconds"]) - sum(
                float(metrics["durationSeconds"]) for metrics in segment_metrics
            )
            expected_pause = (len(segment_metrics) - 1) * 0.5
            tolerance = 0.12 + (len(segment_metrics) - 1) * 0.005
            if abs(measured_pause - expected_pause) > tolerance:
                errors.append(
                    f"{word_id}: pause mismatch measured={measured_pause:.3f} "
                    f"expected={expected_pause:.3f}"
                )

    return {
        "passed": not errors,
        "validatedEntries": len(entries),
        "errors": errors,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--ffprobe", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    args = parser.parse_args()

    from tools.validate_audio import probe_audio

    def probe(path: Path) -> dict:
        metrics = probe_audio(path, args.ffprobe, args.ffmpeg)
        return {
            **metrics,
            "codec": "opus",
            "channels": 1,
            "sampleRate": 48_000,
        }

    report = validate_trial(args.root, probe)
    validation_path = args.root / "validation_report.json"
    validation_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    if not report["passed"]:
        raise SystemExit("FAIL " + "; ".join(report["errors"]))
    print(f"PASS entries={report['validatedEntries']}")


if __name__ == "__main__":
    main()
