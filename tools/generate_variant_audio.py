"""Generate semicolon-delimited MIearn variant clips and paused full clips."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import wave
from pathlib import Path

from tools.generate_audio import (
    MODEL_SHA256,
    SYNTHESIS_PARAMS,
    current_provenance,
    normalize_ogg_serial,
    sha256,
    spoken_text,
    spoken_text_sha256,
    valid_existing,
    write_manifest_atomic,
)

PAUSE_MS = 500
VARIANT_SEPARATOR = re.compile(r"[;；]")


def raw_variants(english: str) -> list[str]:
    return [part.strip() for part in VARIANT_SEPARATOR.split(english) if part.strip()]


def pronounceable_variant(text: str) -> str:
    return spoken_text({"audioText": text, "primaryEnglish": text, "id": "variant"})


def encode_ogg(ffmpeg: Path, source: Path, target_part: Path, stable_key: str) -> None:
    subprocess.run(
        [
            str(ffmpeg),
            "-hide_banner", "-loglevel", "error", "-fflags", "+bitexact", "-y",
            "-i", str(source), "-vn", "-ac", "1", "-af", "aresample=22050",
            "-ar", "48000", "-c:a", "libopus", "-b:a", "32k",
            "-application", "voip", "-flags:a", "+bitexact",
            "-map_metadata", "-1", "-f", "ogg", str(target_part),
        ],
        check=True,
    )
    normalize_ogg_serial(target_part, stable_key)
    if not valid_existing(target_part):
        raise RuntimeError(f"invalid encoded output: {target_part}")


def combine_wavs(parts: list[Path], output: Path) -> None:
    if len(parts) < 2:
        raise ValueError("combined variant audio needs at least two parts")
    with wave.open(str(parts[0]), "rb") as first:
        params = first.getparams()
        frames = [first.readframes(first.getnframes())]
    silence = b"\0" * int(params.framerate * PAUSE_MS / 1000) * params.sampwidth * params.nchannels
    for part in parts[1:]:
        with wave.open(str(part), "rb") as source:
            if (
                source.getnchannels() != params.nchannels
                or source.getsampwidth() != params.sampwidth
                or source.getframerate() != params.framerate
            ):
                raise RuntimeError(f"incompatible Piper WAV: {part}")
            frames.extend((silence, source.readframes(source.getnframes())))
    with wave.open(str(output), "wb") as target:
        target.setnchannels(params.nchannels)
        target.setsampwidth(params.sampwidth)
        target.setframerate(params.framerate)
        target.writeframes(b"".join(frames))


def generate(args: argparse.Namespace) -> None:
    from piper import PiperVoice, SynthesisConfig

    content = json.loads(args.content.read_text(encoding="utf-8"))
    words = content["words"]
    multi = [(word, raw_variants(word["english"])) for word in words]
    multi = [(word, variants) for word, variants in multi if len(variants) > 1]
    config = Path(f"{args.model}.json")
    if not args.model.is_file() or not config.is_file() or not args.ffmpeg.is_file():
        raise FileNotFoundError("Piper model/config or ffmpeg is missing")
    if sha256(args.model) != MODEL_SHA256:
        raise RuntimeError("unexpected Piper model hash")

    args.output.mkdir(parents=True, exist_ok=True)
    variants_dir = args.output / "variants"
    variants_dir.mkdir(parents=True, exist_ok=True)
    args.temp.mkdir(parents=True, exist_ok=True)
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    manifest["pauseBetweenSegmentsMs"] = PAUSE_MS
    manifest["provenance"] = current_provenance(args.model, config, args.ffmpeg)
    entries = manifest.setdefault("entries", {})

    voice = PiperVoice.load(str(args.model), config_path=str(config))
    synthesis = SynthesisConfig(
        noise_scale=SYNTHESIS_PARAMS["noiseScale"],
        noise_w_scale=SYNTHESIS_PARAMS["noiseWScale"],
        length_scale=SYNTHESIS_PARAMS["lengthScale"],
    )
    for number, (word, variants) in enumerate(multi, 1):
        existing = entries.get(word["id"], {})
        existing_segments = existing.get("segments", [])
        existing_files = [args.output / segment.get("path", "").removeprefix("audio/") for segment in existing_segments]
        if (
            len(existing_segments) == len(variants)
            and valid_existing(args.output / f"{word['id']}.ogg")
            and all(valid_existing(path) for path in existing_files)
        ):
            if number % 10 == 0:
                print(f"variant progress {number}/{len(multi)} (resumed)", flush=True)
            continue
        word_temp = args.temp / word["id"]
        word_temp.mkdir(parents=True, exist_ok=True)
        wavs: list[Path] = []
        segment_entries: list[dict] = []
        try:
            for index, raw_text in enumerate(variants):
                try:
                    text = pronounceable_variant(raw_text)
                except ValueError:
                    text = spoken_text({"audioText": word["primaryEnglish"], "primaryEnglish": word["primaryEnglish"], "id": word["id"]})
                wav_path = word_temp / f"{index:02d}.wav"
                with wave.open(str(wav_path), "wb") as wav_file:
                    voice.synthesize_wav(text, wav_file, syn_config=synthesis)
                wavs.append(wav_path)
                relative_path = f"audio/variants/{word['id']}_{index:02d}.ogg"
                target = variants_dir / f"{word['id']}_{index:02d}.ogg"
                part = word_temp / f"{index:02d}.ogg.part"
                encode_ogg(args.ffmpeg, wav_path, part, f"{word['id']}:{index}")
                os.replace(part, target)
                segment_entries.append(
                    {
                        "index": index,
                        "text": raw_text,
                        "spokenText": text,
                        "textSha256": hashlib.sha256(raw_text.encode("utf-8")).hexdigest(),
                        "path": relative_path,
                        "bytes": target.stat().st_size,
                        "audioSha256": sha256(target),
                    }
                )
            combined = word_temp / "combined.wav"
            combine_wavs(wavs, combined)
            full_part = word_temp / "full.ogg.part"
            full_target = args.output / f"{word['id']}.ogg"
            encode_ogg(args.ffmpeg, combined, full_part, word["id"])
            os.replace(full_part, full_target)
            entries[word["id"]] = {
                "id": word["id"],
                "path": word["audioAsset"],
                "spokenTextSha256": spoken_text_sha256(word),
                "audioSha256": sha256(full_target),
                "bytes": full_target.stat().st_size,
                "pauseBetweenSegmentsMs": PAUSE_MS,
                "segments": segment_entries,
            }
        finally:
            for child in word_temp.glob("*"):
                child.unlink(missing_ok=True)
            word_temp.rmdir()
        if number % 10 == 0 or number == len(multi):
            manifest["entries"] = dict(sorted(entries.items()))
            write_manifest_atomic(args.manifest, manifest)
            print(f"variant progress {number}/{len(multi)}", flush=True)
    print(
        f"complete multi={len(multi)} variants={sum(len(v) for _, v in multi)} "
        f"pauses={sum(len(v) - 1 for _, v in multi)}",
        flush=True,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", type=Path, default=Path("app/src/main/assets/content/words_v1.json"))
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/audio"))
    parser.add_argument("--manifest", type=Path, default=Path("app/src/main/assets/content/audio_manifest_v1.json"))
    parser.add_argument("--model", type=Path, default=Path("tools/.piper-models/en_US-lessac-medium/en_US-lessac-medium.onnx"))
    parser.add_argument("--ffmpeg", type=Path, default=Path(r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe"))
    parser.add_argument("--temp", type=Path, default=Path("tmp/audio-variants"))
    return parser.parse_args()


if __name__ == "__main__":
    generate(parse_args())
