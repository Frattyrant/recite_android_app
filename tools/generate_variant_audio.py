"""Generate type-aware MIearn segment clips and paused full clips."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import wave
from pathlib import Path

try:
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
except ModuleNotFoundError:
    from generate_audio import (
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
LEGACY_TERM_SEPARATOR = re.compile(r"(?:[;\uFF1B/\\]|\s)+")
TERM_SEMICOLON = re.compile(r"[;\uFF1B]+")
PHRASE_SEMICOLON = re.compile(r"[;；]+")
SENTENCE_BOUNDARY = re.compile(r"(?<=[.!?])(?:\s+(?=[\"']?[A-Z])|(?=[\"']?[A-Z]))")
ENGLISH_WORD = re.compile(r"[A-Za-z]+(?:'[A-Za-z]+)?")
ENGLISH_OR_NUMBER = re.compile(r"[A-Za-z0-9]")
SLASHES = re.compile(r"[/\\]+")
UNIT_LEFT = re.compile(r"\d+(?:\.\d+)?[A-Za-z]*$")
UNIT_RIGHT = re.compile(r"^[smh](?:\b|$)", re.IGNORECASE)


def legacy_term_variants(english: str) -> list[str]:
    return [
        part.strip()
        for part in LEGACY_TERM_SEPARATOR.split(english)
        if ENGLISH_OR_NUMBER.search(part)
    ]


def segment_plan_sha256(
    variants: list[str],
    pause_ms: int = PAUSE_MS,
) -> str:
    payload = {
        "segments": variants,
        "pauseBetweenSegmentsMs": pause_ms if len(variants) > 1 else 0,
    }
    canonical = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def requires_contiguous_regeneration(
    word: dict,
    variants: list[str],
    entry: dict,
) -> bool:
    if len(variants) != 1:
        return False
    current_plan = segment_plan_sha256(variants)
    stored_plan = entry.get("segmentPlanSha256")
    if stored_plan is not None:
        return stored_plan != current_plan
    return (
        str(word.get("kind", "TERM")).upper() == "TERM"
        and len(legacy_term_variants(str(word.get("english", "")))) > 1
    )


def raw_variants(english: str, kind: str = "TERM") -> list[str]:
    if kind.upper() != "PHRASE":
        return [
            part.strip()
            for semicolon_part in TERM_SEMICOLON.split(english)
            for part in _split_term_slashes(semicolon_part)
            if ENGLISH_OR_NUMBER.search(part)
        ]
    result: list[str] = []
    for semicolon_part in PHRASE_SEMICOLON.split(english):
        for slash_part in _split_phrase_slashes(semicolon_part):
            result.extend(
                part.strip()
                for part in SENTENCE_BOUNDARY.split(slash_part)
                if ENGLISH_WORD.search(part)
            )
    return result


def _split_term_slashes(text: str) -> list[str]:
    result: list[str] = []
    current: list[str] = []
    for index, character in enumerate(text):
        if character not in "/\\":
            current.append(character)
            continue
        unit_slash = bool(
            UNIT_LEFT.search("".join(current))
            and UNIT_RIGHT.search(text[index + 1 :])
        )
        if unit_slash:
            current.append(character)
        else:
            segment = "".join(current).strip()
            if segment:
                result.append(segment)
            current.clear()
    segment = "".join(current).strip()
    if segment:
        result.append(segment)
    return result


def _split_phrase_slashes(text: str) -> list[str]:
    result: list[str] = []
    current: list[str] = []
    for index, character in enumerate(text):
        if character not in "/\\":
            current.append(character)
            continue
        previous = text[index - 1] if index > 0 else ""
        following = text[index + 1] if index + 1 < len(text) else ""
        left_clause = re.split(r"[.!?]", "".join(current))[-1]
        right_clause = re.split(r"[/\\.!?]", text[index + 1 :], maxsplit=1)[0]
        unit_slash = bool(
            UNIT_LEFT.search(text[:index]) and UNIT_RIGHT.search(text[index + 1 :])
        )
        is_boundary = not unit_slash and (
            previous.isspace()
            or following.isspace()
            or previous in ".!?"
            or (
                len(ENGLISH_WORD.findall(left_clause)) >= 3
                and len(ENGLISH_WORD.findall(right_clause)) >= 3
            )
        )
        if is_boundary:
            segment = "".join(current).strip()
            if segment:
                result.append(segment)
            current.clear()
        else:
            current.append(character)
    segment = "".join(current).strip()
    if segment:
        result.append(segment)
    return result


def pronounceable_variant(text: str) -> str:
    try:
        return spoken_text(
            {"audioText": text, "primaryEnglish": text, "id": "variant"}
        )
    except ValueError:
        single = re.sub(r"[^A-Za-z0-9]+", " ", SLASHES.sub(" ", text)).strip()
        if re.fullmatch(r"[A-Za-z0-9]+", single):
            return single
        raise


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
    plans = [
        (word, raw_variants(word["english"], word.get("kind", "TERM")))
        for word in words
    ]
    multi = [(word, variants) for word, variants in plans if len(variants) > 1]
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
    manifest["contentVersion"] = content.get("contentVersion")
    manifest["pauseBetweenSegmentsMs"] = PAUSE_MS
    manifest["provenance"] = current_provenance(args.model, config, args.ffmpeg)
    entries = manifest.setdefault("entries", {})
    contiguous = [
        (word, variants)
        for word, variants in plans
        if requires_contiguous_regeneration(
            word,
            variants,
            entries.get(word["id"], {}),
        )
    ]

    voice = PiperVoice.load(str(args.model), config_path=str(config))
    synthesis = SynthesisConfig(
        noise_scale=SYNTHESIS_PARAMS["noiseScale"],
        noise_w_scale=SYNTHESIS_PARAMS["noiseWScale"],
        length_scale=SYNTHESIS_PARAMS["lengthScale"],
    )

    for number, (word, variants) in enumerate(contiguous, 1):
        word_temp = args.temp / word["id"]
        word_temp.mkdir(parents=True, exist_ok=True)
        wav_path = word_temp / "contiguous.wav"
        full_part = word_temp / "full.ogg.part"
        full_target = args.output / f"{word['id']}.ogg"
        try:
            with wave.open(str(wav_path), "wb") as wav_file:
                voice.synthesize_wav(spoken_text(word), wav_file, syn_config=synthesis)
            encode_ogg(args.ffmpeg, wav_path, full_part, word["id"])
            os.replace(full_part, full_target)
            entries[word["id"]] = {
                "id": word["id"],
                "path": word["audioAsset"],
                "spokenTextSha256": spoken_text_sha256(word),
                "segmentPlanSha256": segment_plan_sha256(variants),
                "audioSha256": sha256(full_target),
                "bytes": full_target.stat().st_size,
            }
        finally:
            for child in word_temp.glob("*"):
                child.unlink(missing_ok=True)
            word_temp.rmdir()
        if number % 10 == 0 or number == len(contiguous):
            manifest["entries"] = dict(sorted(entries.items()))
            write_manifest_atomic(args.manifest, manifest)
            print(
                f"contiguous progress {number}/{len(contiguous)}",
                flush=True,
            )

    for number, (word, variants) in enumerate(multi, 1):
        existing = entries.get(word["id"], {})
        plan_hash = segment_plan_sha256(variants)
        existing_segments = existing.get("segments", [])
        existing_files = [
            args.output / segment.get("path", "").removeprefix("audio/")
            for segment in existing_segments
        ]
        if (
            existing.get("segmentPlanSha256") in (None, plan_hash)
            and len(existing_segments) == len(variants)
            and valid_existing(args.output / f"{word['id']}.ogg")
            and all(valid_existing(path) for path in existing_files)
            and all(
                segment.get("text") == raw_text
                and segment.get("textSha256")
                == hashlib.sha256(raw_text.encode("utf-8")).hexdigest()
                for segment, raw_text in zip(existing_segments, variants)
            )
        ):
            existing["segmentPlanSha256"] = plan_hash
            if number % 10 == 0:
                print(
                    f"variant progress {number}/{len(multi)} (resumed)",
                    flush=True,
                )
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
                    text = spoken_text(
                        {
                            "audioText": word["primaryEnglish"],
                            "primaryEnglish": word["primaryEnglish"],
                            "id": word["id"],
                        }
                    )
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
                        "textSha256": hashlib.sha256(
                            raw_text.encode("utf-8")
                        ).hexdigest(),
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
                "segmentPlanSha256": plan_hash,
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

    for word, variants in plans:
        entry = entries.get(word["id"])
        if not isinstance(entry, dict):
            raise RuntimeError(f"missing audio manifest entry for {word['id']}")
        entry["segmentPlanSha256"] = segment_plan_sha256(variants)
        if len(variants) <= 1:
            entry.pop("segments", None)
            entry.pop("pauseBetweenSegmentsMs", None)

    expected_variant_paths = {
        variants_dir / f"{word['id']}_{index:02d}.ogg"
        for word, variants in multi
        for index in range(len(variants))
    }
    for stale_path in variants_dir.glob("*.ogg"):
        if stale_path not in expected_variant_paths:
            stale_path.unlink()
    manifest["entries"] = dict(sorted(entries.items()))
    write_manifest_atomic(args.manifest, manifest)
    print(
        f"complete contiguous={len(contiguous)} multi={len(multi)} "
        f"variants={sum(len(v) for _, v in multi)} "
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
