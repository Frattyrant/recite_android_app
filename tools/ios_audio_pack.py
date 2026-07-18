"""Plan a safe, deterministic iOS audio pack from MIearn's Android manifest."""

from __future__ import annotations

import argparse
import json
import re
import hashlib
import subprocess
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any, Callable


SHA256 = re.compile(r"[0-9a-f]{64}")


@dataclass(frozen=True)
class IosAudioAsset:
    source_path: str
    output_path: str
    source_sha256: str


def ffmpeg_aac_command(ffmpeg: Path, source: Path, target: Path) -> list[str]:
    return [
        str(ffmpeg),
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-i",
        str(source),
        "-vn",
        "-ac",
        "1",
        "-ar",
        "48000",
        "-c:a",
        "aac",
        "-b:a",
        "48k",
        "-movflags",
        "+faststart",
        str(target),
    ]


def _asset(record: dict[str, Any]) -> IosAudioAsset:
    raw_path = str(record.get("path", "")).strip()
    source_sha256 = str(record.get("audioSha256", "")).strip().lower()
    path = PurePosixPath(raw_path)
    if (
        path.is_absolute()
        or ".." in path.parts
        or not path.parts
        or path.parts[0] != "audio"
        or path.suffix.casefold() != ".ogg"
    ):
        raise ValueError(f"unsafe or unsupported audio path: {raw_path!r}")
    if SHA256.fullmatch(source_sha256) is None:
        raise ValueError(f"invalid source audio hash: {raw_path!r}")
    return IosAudioAsset(
        source_path=path.as_posix(),
        output_path=path.with_suffix(".m4a").as_posix(),
        source_sha256=source_sha256,
    )


def collect_ios_audio_assets(manifest: dict[str, Any]) -> list[IosAudioAsset]:
    entries = manifest.get("entries")
    if not isinstance(entries, dict):
        raise ValueError("audio manifest entries must be an object")

    result: list[IosAudioAsset] = []
    seen_sources: set[str] = set()
    seen_outputs: set[str] = set()
    for word_id in sorted(entries):
        entry = entries[word_id]
        if not isinstance(entry, dict):
            raise ValueError(f"invalid audio entry: {word_id}")
        records = [entry, *(entry.get("segments") or [])]
        for record in records:
            if not isinstance(record, dict):
                raise ValueError(f"invalid audio segment: {word_id}")
            asset = _asset(record)
            if asset.source_path in seen_sources or asset.output_path in seen_outputs:
                raise ValueError(f"duplicate audio asset path: {asset.source_path}")
            seen_sources.add(asset.source_path)
            seen_outputs.add(asset.output_path)
            result.append(asset)
    return result


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def build_ios_audio_pack(
    manifest: dict[str, Any],
    source_root: Path,
    output_root: Path,
    transcode: Callable[[Path, Path], None],
) -> dict[str, Any]:
    source_root = source_root.resolve()
    output_root = output_root.resolve()
    if output_root == source_root or source_root in output_root.parents:
        raise ValueError("iOS output must not be inside the Android audio root")

    records: list[dict[str, Any]] = []
    for asset in collect_ios_audio_assets(manifest):
        source = source_root.joinpath(*PurePosixPath(asset.source_path).parts)
        if not source.is_file():
            raise FileNotFoundError(source)
        if _sha256(source) != asset.source_sha256:
            raise ValueError(f"source audio hash mismatch: {asset.source_path}")

        target = output_root.joinpath(*PurePosixPath(asset.output_path).parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        partial = target.with_name(f"{target.stem}.partial{target.suffix}")
        partial.unlink(missing_ok=True)
        try:
            transcode(source, partial)
            if not partial.is_file() or partial.stat().st_size <= 0:
                raise ValueError(f"transcoder produced no audio: {asset.output_path}")
            partial.replace(target)
        finally:
            partial.unlink(missing_ok=True)

        records.append(
            {
                "sourcePath": asset.source_path,
                "sourceSha256": asset.source_sha256,
                "path": asset.output_path,
                "bytes": target.stat().st_size,
                "audioSha256": _sha256(target),
            }
        )

    return {
        "schemaVersion": 1,
        "assetCount": len(records),
        "codec": "aac-lc",
        "container": "m4a",
        "assets": records,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build MIearn's hash-bound iOS M4A audio pack",
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    args = parser.parse_args()
    for required in (args.manifest, args.ffmpeg):
        if not required.is_file():
            raise FileNotFoundError(required)

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))

    def transcode(source: Path, target: Path) -> None:
        subprocess.run(
            ffmpeg_aac_command(args.ffmpeg, source, target),
            check=True,
        )

    result = build_ios_audio_pack(
        manifest,
        source_root=args.source_root,
        output_root=args.output,
        transcode=transcode,
    )
    report = args.output / "ios_audio_manifest.json"
    temporary = report.with_suffix(report.suffix + ".tmp")
    temporary.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    temporary.replace(report)
    print(f"PASS iOS audio assets={result['assetCount']} manifest={report}")


if __name__ == "__main__":
    main()
