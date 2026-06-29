"""Validate every packaged V2.1 variant clip and paused full sequence."""

from __future__ import annotations

import argparse
import json
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from tools.validate_audio import probe_audio


def pause_tolerance(segment_count: int) -> float:
    """Allow deterministic Opus pre-skip/padding accumulated per encoded clip."""
    return 0.12 + max(0, segment_count - 1) * 0.005


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("app/src/main/assets/content/audio_manifest_v1.json"))
    parser.add_argument("--audio-dir", type=Path, default=Path("app/src/main/assets/audio"))
    parser.add_argument("--ffprobe", type=Path, default=Path(r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffprobe.exe"))
    parser.add_argument("--ffmpeg", type=Path, default=Path(r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe"))
    parser.add_argument("--report", type=Path, default=Path("output/variant_audio_validation_v21.json"))
    args = parser.parse_args()

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    entries = [entry for entry in manifest["entries"].values() if entry.get("segments")]
    segment_paths = [
        args.audio_dir / Path(segment["path"]).relative_to("audio")
        for entry in entries
        for segment in entry["segments"]
    ]
    with ThreadPoolExecutor(max_workers=8) as executor:
        segment_metrics = list(
            executor.map(
                lambda path: probe_audio(path, args.ffprobe, args.ffmpeg),
                segment_paths,
            )
        )
    duration_by_path = {
        path.as_posix(): metric["durationSeconds"]
        for path, metric in zip(segment_paths, segment_metrics)
    }
    paused_sequences = 0
    for entry in entries:
        full_path = args.audio_dir / Path(entry["path"]).name
        full_duration = probe_audio(full_path, args.ffprobe, args.ffmpeg)["durationSeconds"]
        segment_duration = sum(
            duration_by_path[
                (args.audio_dir / Path(segment["path"]).relative_to("audio")).as_posix()
            ]
            for segment in entry["segments"]
        )
        expected_pause = (len(entry["segments"]) - 1) * 0.5
        if (
            abs(full_duration - segment_duration - expected_pause)
            > pause_tolerance(len(entry["segments"]))
        ):
            raise RuntimeError(
                f"pause duration mismatch for {entry['id']}: "
                f"full={full_duration}, segments={segment_duration}, pause={expected_pause}"
            )
        paused_sequences += 1
    report = {
        "multiExpressionWords": len(entries),
        "variantFiles": len(segment_paths),
        "pauseCount": sum(len(entry["segments"]) - 1 for entry in entries),
        "pauseBetweenSegmentsMs": manifest["pauseBetweenSegmentsMs"],
        "decodedVariantFiles": len(segment_metrics),
        "nonSilentVariantFiles": sum(
            metric["maxVolumeDb"] > -45.0 and metric["meanVolumeDb"] > -55.0
            for metric in segment_metrics
        ),
        "verifiedPausedSequences": paused_sequences,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
