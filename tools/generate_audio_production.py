"""Generate MIearn's complete Piper High production pack in staging."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import os
import shutil
import subprocess
import sys
import wave
from pathlib import Path
from typing import Callable, Sequence

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.audio_profiles import LESSAC_HIGH, load_pronunciation_overrides
from tools.audio_production import (
    ProductionEntryPlan,
    assert_safe_staging_path,
    content_sha256,
    plan_production,
)
from tools.audio_trial import load_words
from tools.generate_audio import (
    SYNTHESIS_PARAMS,
    ffmpeg_encode_args,
    normalize_ogg_serial,
    sha256,
    valid_existing,
    write_manifest_atomic,
)
from tools.generate_variant_audio import combine_wavs, segment_plan_sha256


Synthesizer = Callable[[str, Path], None]
Encoder = Callable[[Path, Path, str], None]
Probe = Callable[[Path], dict]


def _file_metadata(path: Path, output: Path, probe: Probe) -> dict:
    metrics = probe(path)
    return {
        "path": path.relative_to(output).as_posix(),
        "bytes": path.stat().st_size,
        "audioSha256": sha256(path),
        "durationSeconds": float(metrics["durationSeconds"]),
        "codec": metrics.get("codec", "opus"),
        "channels": int(metrics.get("channels", 1)),
        "sampleRate": int(metrics.get("sampleRate", 48_000)),
    }


def _metadata_matches(output: Path, metadata: dict, probe: Probe) -> bool:
    relative = str(metadata.get("path", ""))
    path = output / relative
    if not relative or not path.is_file():
        return False
    if path.stat().st_size != metadata.get("bytes"):
        return False
    if sha256(path) != metadata.get("audioSha256"):
        return False
    try:
        metrics = probe(path)
    except Exception:
        return False
    return (
        metrics.get("codec", "opus") == metadata.get("codec")
        and int(metrics.get("channels", 1)) == metadata.get("channels")
        and int(metrics.get("sampleRate", 48_000)) == metadata.get("sampleRate")
        and abs(float(metrics["durationSeconds"]) - float(metadata["durationSeconds"]))
        < 0.001
    )


def _can_resume(
    plan: ProductionEntryPlan,
    entry: dict,
    output: Path,
    probe: Probe,
) -> bool:
    if entry.get("id") != plan.word_id or not _metadata_matches(output, entry, probe):
        return False
    segments = entry.get("segments", [])
    expected_segments = plan.segments if len(plan.segments) > 1 else ()
    if len(segments) != len(expected_segments):
        return False
    for expected, actual in zip(expected_segments, segments):
        if (
            actual.get("index") != expected.index
            or actual.get("text") != expected.display_text
            or actual.get("spokenText") != expected.spoken_text
            or actual.get("overrideKey") != expected.override_key
            or not _metadata_matches(output, actual, probe)
        ):
            return False
    return entry.get("segmentPlanSha256") == segment_plan_sha256(
        [segment.display_text for segment in plan.segments]
    )


def _audit(words: Sequence[dict], entries: dict) -> dict:
    requested = ("fixture", "jig", "GD&T", "PLC", "mylar")
    selected: list[dict] = []
    selected_ids: set[str] = set()
    missing: list[str] = []

    def add(word: dict) -> None:
        if word["id"] not in selected_ids:
            selected_ids.add(word["id"])
            selected.append(word)

    for term in requested:
        match = next(
            (word for word in words if term.casefold() in str(word.get("english", "")).casefold()),
            None,
        )
        if match is None:
            missing.append(term)
        else:
            add(match)
    for category in sorted({str(word.get("category", "")) for word in words}):
        match = next(word for word in words if str(word.get("category", "")) == category)
        add(match)
    return {
        "schemaVersion": 1,
        "samples": [
            {
                "id": word["id"],
                "english": word.get("english", ""),
                "chinese": word.get("chinese", ""),
                "category": word.get("category", ""),
                "completePath": entries[word["id"]]["path"],
                "variantPaths": [
                    item["path"]
                    for item in entries[word["id"]].get("segments", [])
                ],
            }
            for word in selected
        ],
        "missingSuggestedSamples": missing,
    }


def generate_staged_pack(
    plans: Sequence[ProductionEntryPlan],
    words: Sequence[dict],
    output: Path,
    content_hash: str,
    profile: dict,
    synthesize: Synthesizer,
    encode: Encoder,
    probe: Probe,
) -> dict:
    assert_safe_staging_path(output)
    audio = output / "audio"
    variants = audio / "variants"
    temporary = output / ".tmp"
    for directory in (audio, variants, temporary):
        directory.mkdir(parents=True, exist_ok=True)

    manifest_path = output / "audio_manifest_v1.json"
    entries: dict[str, dict] = {}
    if manifest_path.is_file():
        existing = json.loads(manifest_path.read_text(encoding="utf-8"))
        if (
            existing.get("schemaVersion") == 2
            and existing.get("contentSha256") == content_hash
            and existing.get("profile") == profile
        ):
            entries = dict(existing.get("entries", {}))
    try:
        for number, plan in enumerate(plans, 1):
            if _can_resume(plan, entries.get(plan.word_id, {}), output, probe):
                if number % 25 == 0 or number == len(plans):
                    print(
                        f"production progress {number}/{len(plans)} (resumed)",
                        flush=True,
                    )
                continue
            word_temp = temporary / plan.word_id
            word_temp.mkdir(parents=True, exist_ok=True)
            wavs: list[Path] = []
            segment_entries: list[dict] = []
            for segment in plan.segments:
                wav = word_temp / f"{segment.index:02d}.wav"
                synthesize(segment.spoken_text, wav)
                wavs.append(wav)
                if len(plan.segments) > 1:
                    target = variants / f"{plan.word_id}_{segment.index:02d}.ogg"
                    part = word_temp / f"{segment.index:02d}.ogg.part"
                    encode(wav, part, f"{plan.word_id}:{segment.index}")
                    os.replace(part, target)
                    segment_entries.append(
                        {
                            "index": segment.index,
                            "text": segment.display_text,
                            "spokenText": segment.spoken_text,
                            "overrideKey": segment.override_key,
                            "textSha256": hashlib.sha256(segment.display_text.encode("utf-8")).hexdigest(),
                            **_file_metadata(target, output, probe),
                        }
                    )
            complete_wav = wavs[0]
            if len(wavs) > 1:
                complete_wav = word_temp / "combined.wav"
                combine_wavs(wavs, complete_wav)
            complete = audio / f"{plan.word_id}.ogg"
            complete_part = word_temp / "complete.ogg.part"
            encode(complete_wav, complete_part, plan.word_id)
            os.replace(complete_part, complete)
            complete_meta = _file_metadata(complete, output, probe)
            entry = {
                "id": plan.word_id,
                **complete_meta,
                "segmentPlanSha256": segment_plan_sha256(
                    [segment.display_text for segment in plan.segments]
                ),
            }
            if len(plan.segments) > 1:
                entry["pauseBetweenSegmentsMs"] = 500
                entry["segments"] = segment_entries
            entries[plan.word_id] = entry
            manifest = {
                "schemaVersion": 2,
                "contentSha256": content_hash,
                "entryCount": len(plans),
                "profile": profile,
                "entries": entries,
            }
            write_manifest_atomic(manifest_path, manifest)
            shutil.rmtree(word_temp)
            if number % 25 == 0 or number == len(plans):
                print(f"production progress {number}/{len(plans)}", flush=True)
    finally:
        if temporary.exists():
            shutil.rmtree(temporary)

    expected_variant_paths = {
        variants / f"{plan.word_id}_{segment.index:02d}.ogg"
        for plan in plans
        if len(plan.segments) > 1
        for segment in plan.segments
    }
    for stale_path in variants.glob("*.ogg"):
        if stale_path not in expected_variant_paths:
            stale_path.unlink()

    audit = _audit(words, entries)
    write_manifest_atomic(output / "release_audit.json", audit)
    return json.loads(manifest_path.read_text(encoding="utf-8"))


def _real_generation(args: argparse.Namespace) -> dict:
    from piper import PiperVoice, SynthesisConfig
    from tools.validate_audio import probe_audio

    config = Path(f"{args.model}.json")
    for required in (args.content, args.overrides, args.model, config, args.ffmpeg):
        if not required.is_file():
            raise FileNotFoundError(required)
    if sha256(args.model) != LESSAC_HIGH.model_sha256:
        raise RuntimeError("unexpected Lessac High model SHA-256")
    words = load_words(args.content)
    plans = plan_production(words, load_pronunciation_overrides(args.overrides))
    voice = PiperVoice.load(str(args.model), config_path=str(config))
    synthesis = SynthesisConfig(
        noise_scale=SYNTHESIS_PARAMS["noiseScale"],
        noise_w_scale=SYNTHESIS_PARAMS["noiseWScale"],
        length_scale=SYNTHESIS_PARAMS["lengthScale"],
    )

    def synthesize(text: str, target: Path) -> None:
        with wave.open(str(target), "wb") as wav_file:
            voice.synthesize_wav(text, wav_file, syn_config=synthesis)

    def encode(source: Path, target: Path, stable_key: str) -> None:
        subprocess.run([str(args.ffmpeg), *ffmpeg_encode_args(LESSAC_HIGH, source, target)], check=True)
        normalize_ogg_serial(target, stable_key)
        if not valid_existing(target):
            raise RuntimeError(f"invalid encoded audio: {target}")

    ffprobe = args.ffmpeg.with_name("ffprobe.exe")
    profile = {
        "name": LESSAC_HIGH.name,
        "modelSha256": sha256(args.model),
        "modelConfigSha256": sha256(config),
        "piperVersion": importlib.metadata.version("piper-tts"),
        "ffmpegVersion": subprocess.run([str(args.ffmpeg), "-version"], check=True, capture_output=True, text=True).stdout.splitlines()[0],
        "bitRateKbps": 40,
        "application": "audio",
        "channels": 1,
        "encodedSampleRate": 48_000,
    }
    return generate_staged_pack(
        plans, words, args.output, content_sha256(args.content), profile,
        synthesize, encode, lambda path: probe_audio(path, ffprobe, args.ffmpeg),
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", type=Path, default=Path("app/src/main/assets/content/words_v1.json"))
    parser.add_argument("--overrides", type=Path, default=Path("tools/audio/pronunciation_overrides.json"))
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=Path("tmp/audio-production-v2.3"))
    args = parser.parse_args()
    report = _real_generation(args)
    print(f"complete entries={report['entryCount']} profile={report['profile']['name']}")


if __name__ == "__main__":
    main()
