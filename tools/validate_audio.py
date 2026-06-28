"""Validate the packaged offline audio set against words_v1.json."""

from __future__ import annotations

import argparse
import json
import math
import re
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

try:
    from tools.generate_audio import (
        ENCODING_PARAMS,
        MANIFEST_SCHEMA_VERSION,
        MODEL_NAME,
        MODEL_SHA256,
        SYNTHESIS_PARAMS,
        sha256,
        spoken_text_sha256,
        valid_existing,
        write_manifest_atomic,
    )
except ModuleNotFoundError:
    from generate_audio import (  # type: ignore[no-redef]
        ENCODING_PARAMS,
        MANIFEST_SCHEMA_VERSION,
        MODEL_NAME,
        MODEL_SHA256,
        SYNTHESIS_PARAMS,
        sha256,
        spoken_text_sha256,
        valid_existing,
        write_manifest_atomic,
    )

EXPECTED_COUNT = 2704
EXPECTED_PIPER_VERSION = "1.4.2"
MODEL_CONFIG_SHA256 = (
    "efe19c417bed055f2d69908248c6ba650fa135bc868b0e6abb3da181dab690a0"
)
MIN_DURATION_SECONDS = 0.20
MIN_MEAN_VOLUME_DB = -55.0
MIN_MAX_VOLUME_DB = -45.0
MODEL_SOURCE = (
    "https://huggingface.co/rhasspy/piper-voices/tree/main/"
    "en/en_US/lessac/medium"
)


def expected_provenance(ffmpeg: Path) -> dict:
    version = subprocess.run(
        [str(ffmpeg), "-version"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.splitlines()[0]
    return {
        "piperVersion": EXPECTED_PIPER_VERSION,
        "model": MODEL_NAME,
        "modelSha256": MODEL_SHA256,
        "modelConfigSha256": MODEL_CONFIG_SHA256,
        "ffmpegVersion": version,
        "synthesis": SYNTHESIS_PARAMS,
        "encoding": ENCODING_PARAMS,
    }


def probe_audio(path: Path, ffprobe: Path, ffmpeg: Path) -> dict:
    result = subprocess.run(
        [
            str(ffprobe),
            "-v",
            "error",
            "-select_streams",
            "a:0",
            "-count_frames",
            "-show_entries",
            "stream=codec_name,sample_rate,channels,nb_read_frames:format=duration",
            "-of",
            "json",
            str(path),
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    streams = json.loads(result.stdout).get("streams", [])
    if len(streams) != 1:
        raise RuntimeError(f"expected one audio stream in {path.name}: {streams}")
    stream = streams[0]
    properties = {
        key: stream.get(key) for key in ("codec_name", "sample_rate", "channels")
    }
    expected = {"codec_name": "opus", "sample_rate": "48000", "channels": 1}
    if properties != expected:
        raise RuntimeError(f"unexpected codec properties for {path.name}: {stream}")
    try:
        frames = int(stream["nb_read_frames"])
    except (KeyError, TypeError, ValueError) as error:
        raise RuntimeError(f"could not read frames from {path.name}: {stream}") from error
    if frames <= 0:
        raise RuntimeError(f"no readable Opus frames in {path.name}")
    try:
        duration = float(json.loads(result.stdout)["format"]["duration"])
    except (KeyError, TypeError, ValueError) as error:
        raise RuntimeError(f"could not read duration from {path.name}") from error
    if not math.isfinite(duration) or duration < MIN_DURATION_SECONDS:
        raise RuntimeError(
            f"audio duration too short for {path.name}: {duration:.3f}s"
        )

    decoded = subprocess.run(
        [
            str(ffmpeg),
            "-hide_banner",
            "-nostats",
            "-i",
            str(path),
            "-map",
            "0:a:0",
            "-af",
            "volumedetect",
            "-f",
            "null",
            "-",
        ],
        check=True,
        capture_output=True,
        text=True,
    )
    volume = decoded.stderr
    mean_match = re.search(r"mean_volume:\s*(-?inf|[-+]?\d+(?:\.\d+)?) dB", volume)
    max_match = re.search(r"max_volume:\s*(-?inf|[-+]?\d+(?:\.\d+)?) dB", volume)
    if not mean_match or not max_match:
        raise RuntimeError(f"volume analysis missing for {path.name}")
    mean_volume = float(mean_match.group(1))
    max_volume = float(max_match.group(1))
    if (
        not math.isfinite(mean_volume)
        or mean_volume <= MIN_MEAN_VOLUME_DB
        or not math.isfinite(max_volume)
        or max_volume <= MIN_MAX_VOLUME_DB
    ):
        raise RuntimeError(
            f"silent or near-silent audio in {path.name}: "
            f"mean={mean_volume} dB, max={max_volume} dB"
        )
    return {
        "durationSeconds": duration,
        "meanVolumeDb": mean_volume,
        "maxVolumeDb": max_volume,
    }


def validate(
    content_path: Path,
    audio_dir: Path,
    manifest_path: Path,
    ffprobe: Path,
    ffmpeg: Path,
    probe_all: bool,
) -> dict:
    content = json.loads(content_path.read_text(encoding="utf-8"))
    words = content["words"]
    expected = {Path(word["audioAsset"]).name for word in words}
    actual = {path.name for path in audio_dir.glob("*.ogg")}
    if len(words) != EXPECTED_COUNT or len(expected) != EXPECTED_COUNT:
        raise RuntimeError(
            f"content manifest count is not {EXPECTED_COUNT}: "
            f"words={len(words)}, uniqueAssets={len(expected)}"
        )

    missing = sorted(expected - actual)
    unexpected = sorted(actual - expected)
    empty = sorted(path.name for path in audio_dir.glob("*.ogg") if path.stat().st_size <= 256)
    foreign = sorted(
        str(path.relative_to(audio_dir))
        for path in audio_dir.rglob("*")
        if path.is_file() and path.suffix.lower() != ".ogg"
    )
    if len(actual) != EXPECTED_COUNT or missing or unexpected or empty or foreign:
        raise RuntimeError(
            f"audio mismatch: expected={EXPECTED_COUNT}, actual={len(actual)}, "
            f"missing={len(missing)}, unexpected={len(unexpected)}, "
            f"empty={len(empty)}, foreign={len(foreign)}"
        )

    if not ffprobe.is_file():
        raise FileNotFoundError(f"ffprobe not found: {ffprobe}")
    if not ffmpeg.is_file():
        raise FileNotFoundError(f"ffmpeg not found: {ffmpeg}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != MANIFEST_SCHEMA_VERSION:
        raise RuntimeError(
            f"unexpected audio manifest schema: {manifest.get('schemaVersion')}"
        )
    provenance = expected_provenance(ffmpeg)
    if manifest.get("provenance") != provenance:
        raise RuntimeError("audio manifest global provenance mismatch")
    entries = manifest.get("entries", {})
    expected_ids = {word["id"] for word in words}
    if set(entries) != expected_ids:
        raise RuntimeError(
            "audio manifest entry mismatch: "
            f"missing={len(expected_ids - set(entries))}, "
            f"unexpected={len(set(entries) - expected_ids)}"
        )
    for word in words:
        entry = entries[word["id"]]
        path = audio_dir / Path(word["audioAsset"]).name
        expected_entry = {
            "id": word["id"],
            "path": word["audioAsset"],
            "spokenTextSha256": spoken_text_sha256(word),
            "audioSha256": sha256(path),
            "bytes": path.stat().st_size,
        }
        if any(entry.get(key) != value for key, value in expected_entry.items()):
            raise RuntimeError(f"audio manifest binding mismatch for {word['id']}")

        variants = [
            part.strip()
            for part in re.split(r"[;；]", word["english"])
            if part.strip()
        ]
        if len(variants) > 1:
            if entry.get("pauseBetweenSegmentsMs") != 500:
                raise RuntimeError(f"missing 500 ms pause for {word['id']}")
            segments = entry.get("segments", [])
            if len(segments) != len(variants):
                raise RuntimeError(f"variant count mismatch for {word['id']}")
            for index, (variant, segment) in enumerate(zip(variants, segments)):
                expected_path = f"audio/variants/{word['id']}_{index:02d}.ogg"
                variant_path = audio_dir / "variants" / Path(expected_path).name
                if (
                    segment.get("index") != index
                    or segment.get("text") != variant
                    or segment.get("path") != expected_path
                    or not valid_existing(variant_path)
                    or segment.get("bytes") != variant_path.stat().st_size
                    or segment.get("audioSha256") != sha256(variant_path)
                ):
                    raise RuntimeError(
                        f"variant manifest binding mismatch for {word['id']}:{index}"
                    )

    ordered = [audio_dir / name for name in sorted(expected)]
    if probe_all:
        probe_paths = ordered
    else:
        # A deterministic, stratified 5% sample for codec-level validation.
        stride = max(1, len(ordered) // max(1, round(len(ordered) * 0.05)))
        probe_paths = ordered[::stride]

    with ThreadPoolExecutor(max_workers=8) as executor:
        metrics = list(
            executor.map(
                lambda path: probe_audio(path, ffprobe, ffmpeg),
                probe_paths,
            )
        )

    return {
        "count": len(actual),
        "expectedCount": len(expected),
        "actualCount": len(actual),
        "probed": len(probe_paths),
        "probedCount": len(probe_paths),
        "decodedCount": len(probe_paths),
        "totalBytes": sum(path.stat().st_size for path in ordered),
        "minimumDurationSeconds": min(
            metric["durationSeconds"] for metric in metrics
        ),
        "minimumMaxVolumeDb": min(metric["maxVolumeDb"] for metric in metrics),
        "minimumMeanVolumeDb": min(
            metric["meanVolumeDb"] for metric in metrics
        ),
        "silenceThresholdMeanVolumeDb": MIN_MEAN_VOLUME_DB,
        "silenceThresholdMaxVolumeDb": MIN_MAX_VOLUME_DB,
        "codec": "Opus",
        "channels": 1,
        "sourceSampleRate": 22050,
        "decodedSampleRate": 48000,
        "targetBitrateKbps": 32,
        "generator": f"Piper piper-tts {EXPECTED_PIPER_VERSION}",
        "model": "en_US-lessac-medium",
        "modelSha256": (
            "5efe09e69902187827af646e1a6e9d269dee769f9877d17b16b1b46eeaaf019f"
        ),
        "modelConfigSha256": (
            "efe19c417bed055f2d69908248c6ba650fa135bc868b0e6abb3da181dab690a0"
        ),
        "synthesis": SYNTHESIS_PARAMS,
        "encoding": ENCODING_PARAMS,
        "audioManifest": str(manifest_path),
        "manifestSchemaVersion": MANIFEST_SCHEMA_VERSION,
        "ffmpegVersion": provenance["ffmpegVersion"],
        "source": MODEL_SOURCE,
        "license": {
            "generator": "GPL-3.0-or-later (generation tool; not packaged)",
            "voiceRepository": "MIT",
            "trainingDataset": "Blizzard 2013 Research Licence Agreement",
        },
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--content",
        type=Path,
        default=Path("app/src/main/assets/content/words_v1.json"),
    )
    parser.add_argument("--audio-dir", type=Path, default=Path("app/src/main/assets/audio"))
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("app/src/main/assets/content/audio_manifest_v1.json"),
    )
    parser.add_argument(
        "--ffprobe",
        type=Path,
        default=Path(r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffprobe.exe"),
    )
    parser.add_argument(
        "--ffmpeg",
        type=Path,
        default=Path(r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe"),
    )
    parser.add_argument("--all", action="store_true", help="decode-check every audio file")
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("output/reports/audio_report.json"),
    )
    args = parser.parse_args()

    report = validate(
        args.content,
        args.audio_dir,
        args.manifest,
        args.ffprobe,
        args.ffmpeg,
        args.all,
    )
    write_manifest_atomic(args.report, report)
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
