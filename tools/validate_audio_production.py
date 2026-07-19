"""Validate every staged MIearn V2.32 production audio asset."""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Callable, Sequence

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.audio_production import (
    IPA_GROUP,
    ProductionEntryPlan,
    load_human_audio_sources,
    load_model_audio_sources,
    plan_production,
    speech_plan_sha256,
)
from tools.audio_profiles import load_pronunciation_overrides
from tools.audio_profiles import (
    LJSPEECH_DATASET_LICENSE,
    LJSPEECH_DATASET_URL,
    LJSPEECH_HIGH,
    LJSPEECH_MODEL_CARD_SHA256,
    LJSPEECH_MODEL_CONFIG_SHA256,
    LJSPEECH_MODEL_SOURCE_URL,
)
from tools.generate_variant_audio import raw_variants, segment_plan_sha256


Probe = Callable[[Path], dict]
HIGH_MODEL_SHA256 = LJSPEECH_HIGH.model_sha256


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
    expected_count: int = 2_698,
    manifest_path: Path | None = None,
    plans: Sequence[ProductionEntryPlan] | None = None,
) -> dict:
    manifest_path = manifest_path or root / "audio_manifest_v1.json"
    if not manifest_path.is_file():
        return {"passed": False, "validatedEntries": 0, "errors": ["manifest missing"]}
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    errors: list[str] = []
    if len(words) != expected_count:
        errors.append(f"word count is {len(words)}, expected {expected_count}")
    if payload.get("schemaVersion") != 3:
        errors.append("unsupported production manifest schema")
    profile = payload.get("profile", {})
    expected_profile = {
        "name": LJSPEECH_HIGH.name,
        "bitRateKbps": LJSPEECH_HIGH.bit_rate_kbps,
        "application": LJSPEECH_HIGH.application,
        "channels": 1,
        "encodedSampleRate": 48_000,
        "modelConfigSha256": LJSPEECH_MODEL_CONFIG_SHA256,
        "modelCardSha256": LJSPEECH_MODEL_CARD_SHA256,
        "modelSourceUrl": LJSPEECH_MODEL_SOURCE_URL,
        "dataset": "LJSpeech",
        "datasetUrl": LJSPEECH_DATASET_URL,
        "datasetLicense": LJSPEECH_DATASET_LICENSE,
    }
    if any(profile.get(key) != value for key, value in expected_profile.items()):
        errors.append("production profile does not match LJSpeech High 40k audio")
    if profile.get("modelSha256") != HIGH_MODEL_SHA256:
        errors.append("production model SHA-256 does not match approved LJSpeech High")
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
    planned = {plan.word_id: plan for plan in plans or []}

    output_paths: set[str] = set()
    source_counts = {"piper": 0, "human": 0, "model": 0, "mixed": 0}
    for word in words:
        word_id = str(word.get("id", ""))
        entry = entries.get(word_id)
        if not isinstance(entry, dict):
            continue
        complete_metrics = _validate_file(root, entry, probe, f"{word_id}:complete", errors)
        variants = raw_variants(str(word.get("english", "")), str(word.get("kind", "TERM")))
        ipa_groups = IPA_GROUP.findall(str(word.get("phonetic", "")))
        if len(ipa_groups) != len(variants):
            errors.append(f"{word_id}: IPA group count mismatch")
        if entry.get("expectedIpa") != str(word.get("phonetic", "")):
            errors.append(f"{word_id}: complete expected IPA mismatch")
        source_type = str(entry.get("sourceType", ""))
        if source_type not in source_counts:
            errors.append(f"{word_id}: invalid complete source type")
        else:
            source_counts[source_type] += 1
        speech_hash = str(entry.get("speechPlanSha256", ""))
        if len(speech_hash) != 64 or any(character not in "0123456789abcdef" for character in speech_hash):
            errors.append(f"{word_id}: invalid speech plan hash")
        if word_id in planned and speech_hash != speech_plan_sha256(planned[word_id]):
            errors.append(f"{word_id}: speech plan hash mismatch")
        expected_segment_texts = variants if len(variants) > 1 else []
        if len(variants) == 1 and not str(entry.get("expectedTranscript", "")).strip():
            errors.append(f"{word_id}: complete expected transcript missing")
        if (
            len(variants) == 1
            and word_id in planned
            and entry.get("expectedTranscript")
            != planned[word_id].segments[0].expected_transcript
        ):
            errors.append(f"{word_id}: complete expected transcript mismatch")
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
            if index < len(ipa_groups) and segment.get("expectedIpa") != ipa_groups[index]:
                errors.append(f"{word_id}: segment {index} expected IPA mismatch")
            if not str(segment.get("expectedTranscript", "")).strip():
                errors.append(f"{word_id}: segment {index} expected transcript missing")
            if (
                word_id in planned
                and index < len(planned[word_id].segments)
                and segment.get("expectedTranscript")
                != planned[word_id].segments[index].expected_transcript
            ):
                errors.append(f"{word_id}: segment {index} expected transcript mismatch")
            segment_source = str(segment.get("sourceType", ""))
            if segment_source not in {"piper", "human", "model"}:
                errors.append(f"{word_id}: segment {index} invalid source type")
            human_source = segment.get("humanSource")
            model_source = segment.get("modelSource")
            if segment_source == "human":
                _validate_human_source(human_source, f"{word_id}:segment:{index}", errors)
            elif human_source is not None:
                errors.append(f"{word_id}: segment {index} unexpected human provenance")
            if segment_source == "model":
                _validate_model_source(model_source, f"{word_id}:segment:{index}", errors)
            elif model_source is not None:
                errors.append(f"{word_id}: segment {index} unexpected model provenance")
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
        if len(variants) == 1:
            human_source = entry.get("humanSource")
            model_source = entry.get("modelSource")
            if source_type == "human":
                _validate_human_source(human_source, f"{word_id}:complete", errors)
            elif human_source is not None:
                errors.append(f"{word_id}: unexpected complete human provenance")
            if source_type == "model":
                _validate_model_source(model_source, f"{word_id}:complete", errors)
            elif model_source is not None:
                errors.append(f"{word_id}: unexpected complete model provenance")
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
    return {
        "passed": not errors,
        "validatedEntries": len(entries),
        "sourceCounts": source_counts,
        "errors": errors,
    }


def _validate_human_source(value: object, label: str, errors: list[str]) -> None:
    if not isinstance(value, dict):
        errors.append(f"{label}: human provenance missing")
        return
    required = ("sourceUrl", "descriptionUrl", "speaker", "license", "sourceSha256")
    if any(not str(value.get(key, "")).strip() for key in required):
        errors.append(f"{label}: incomplete human provenance")
    license_name = str(value.get("license", "")).casefold()
    if not (license_name.startswith("cc0") or license_name == "public domain"):
        errors.append(f"{label}: human source license is not approved")


def _validate_model_source(value: object, label: str, errors: list[str]) -> None:
    if not isinstance(value, dict):
        errors.append(f"{label}: model provenance missing")
        return
    required = (
        "modelName",
        "modelVersion",
        "modelSourceUrl",
        "modelLicense",
        "voice",
        "sourceSha256",
    )
    if any(not str(value.get(key, "")).strip() for key in required):
        errors.append(f"{label}: incomplete model provenance")
    if str(value.get("modelLicense", "")).casefold() not in {"apache-2.0", "apache 2.0"}:
        errors.append(f"{label}: model source license is not approved")
    source_hash = str(value.get("sourceSha256", ""))
    if len(source_hash) != 64 or any(character not in "0123456789abcdef" for character in source_hash):
        errors.append(f"{label}: invalid model source hash")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--ffprobe", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    parser.add_argument("--overrides", type=Path, default=Path("tools/audio/pronunciation_overrides.json"))
    parser.add_argument("--human-attributions", type=Path)
    parser.add_argument("--human-audio-root", type=Path)
    parser.add_argument("--model-audio-attributions", type=Path)
    parser.add_argument("--model-audio-root", type=Path)
    args = parser.parse_args()
    from tools.audio_trial import load_words
    from tools.validate_audio import probe_audio

    def real_probe(path: Path) -> dict:
        return {
            **probe_audio(path, args.ffprobe, args.ffmpeg),
            "codec": "opus", "channels": 1, "sampleRate": 48_000,
        }

    words = load_words(args.content)
    plans = plan_production(
        words,
        load_pronunciation_overrides(args.overrides),
        human_audio=load_human_audio_sources(
            args.human_attributions,
            args.human_audio_root,
        ),
        model_audio=load_model_audio_sources(
            args.model_audio_attributions,
            args.model_audio_root,
        ),
    )
    manifest_payload = json.loads(
        (args.manifest or (args.root / "audio_manifest_v1.json")).read_text(encoding="utf-8")
    )
    relative_paths = sorted(
        {
            str(metadata.get("path", ""))
            for entry in manifest_payload.get("entries", {}).values()
            for metadata in (entry, *entry.get("segments", []))
            if str(metadata.get("path", ""))
        }
    )
    probed: dict[Path, dict | Exception] = {}
    workers = min(8, max(2, os.cpu_count() or 2))
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        futures = {
            executor.submit(real_probe, args.root / relative): args.root / relative
            for relative in relative_paths
        }
        for completed, future in enumerate(concurrent.futures.as_completed(futures), 1):
            path = futures[future]
            try:
                probed[path] = future.result()
            except Exception as error:
                probed[path] = error
            if completed % 100 == 0 or completed == len(futures):
                print(f"audio decode audit {completed}/{len(futures)}", flush=True)

    def probe(path: Path) -> dict:
        result = probed[path]
        if isinstance(result, Exception):
            raise result
        return result

    report = validate_production(
        args.root,
        words,
        probe,
        manifest_path=args.manifest,
        plans=plans,
    )
    report_path = args.root / "validation_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if not report["passed"]:
        raise SystemExit("FAIL " + "; ".join(report["errors"][:20]))
    print(f"PASS entries={report['validatedEntries']}")


if __name__ == "__main__":
    main()
