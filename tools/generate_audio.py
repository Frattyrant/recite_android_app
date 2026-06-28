"""Generate MIearn's deterministic offline Opus voice pack with Piper."""

from __future__ import annotations

import argparse
import hashlib
import importlib.metadata
import json
import os
import re
import subprocess
import unicodedata
import wave
from pathlib import Path

MODEL_NAME = "en_US-lessac-medium"
MODEL_SHA256 = "5efe09e69902187827af646e1a6e9d269dee769f9877d17b16b1b46eeaaf019f"
MANIFEST_SCHEMA_VERSION = 1
SYNTHESIS_PARAMS = {
    "noiseScale": 0.0,
    "noiseWScale": 0.0,
    "lengthScale": 1.0,
    "sourceSampleRate": 22050,
}
ENCODING_PARAMS = {
    "codec": "libopus",
    "targetBitrateKbps": 32,
    "channels": 1,
    "filter": "aresample=22050",
    "encodedSampleRate": 48000,
    "application": "voip",
    "fflags": "+bitexact",
    "audioFlags": "+bitexact",
    "metadata": "stripped",
    "container": "ogg",
    "oggSerial": "first 32 bits of SHA-256(word ID), little-endian",
}
_CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
_PARENTHETICAL = re.compile(r"\([^()]*\)")
_UNSAFE = re.compile(r"[^A-Za-z0-9\s.,;:!?&'()/+\-=]")
_WHITESPACE = re.compile(r"\s+")
_SEPARATOR_SPACING = re.compile(r"\s*([,;:/])\s*")
_MEANINGFUL_TOKEN = re.compile(
    r"(?<![A-Za-z0-9])(?=[A-Za-z0-9]*[A-Za-z])[A-Za-z0-9]{2,}"
)


def _build_ogg_crc_table() -> tuple[int, ...]:
    values = []
    for index in range(256):
        value = index << 24
        for _ in range(8):
            value = (
                ((value << 1) ^ 0x04C11DB7)
                if value & 0x80000000
                else value << 1
            ) & 0xFFFFFFFF
        values.append(value)
    return tuple(values)


_OGG_CRC_TABLE = _build_ogg_crc_table()


def spoken_text(word: dict) -> str:
    """Keep pronounceable English while removing CJK notes and stray artifacts."""
    text = unicodedata.normalize("NFKC", str(word.get("audioText", "")))
    while True:
        cleaned = _PARENTHETICAL.sub(
            lambda match: " " if _CJK.search(match.group(0)) else match.group(0),
            text,
        )
        if cleaned == text:
            break
        text = cleaned
    text = _CJK.sub(" ", text)
    text = _UNSAFE.sub(" ", text)
    text = _SEPARATOR_SPACING.sub(r"\1 ", text)
    text = re.sub(r"([!?.,;:])\1+", r"\1", text)
    text = re.sub(r"\s*[-=]{2,}\s*", ", ", text)
    text = _WHITESPACE.sub(" ", text).strip(" ,;:/-=")
    if not _MEANINGFUL_TOKEN.search(text):
        fallback = unicodedata.normalize(
            "NFKC", str(word.get("primaryEnglish", ""))
        )
        fallback = _CJK.sub(" ", fallback)
        fallback = _UNSAFE.sub(" ", fallback)
        text = _WHITESPACE.sub(" ", fallback).strip(" ,;:/-=")
    if not _MEANINGFUL_TOKEN.search(text):
        raise ValueError(
            "no pronounceable English text with a meaningful English token "
            f"for {word.get('id')}"
        )
    return text


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def spoken_text_sha256(word: dict) -> str:
    return hashlib.sha256(spoken_text(word).encode("utf-8")).hexdigest()


def valid_existing(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size <= 256:
        return False
    with path.open("rb") as source:
        return source.read(4) == b"OggS"


def can_skip(
    word: dict,
    target: Path,
    manifest: dict,
    current_provenance: dict,
) -> bool:
    if manifest.get("provenance") != current_provenance:
        return False
    entry = manifest.get("entries", {}).get(word["id"])
    if not isinstance(entry, dict):
        return False
    if entry.get("id") != word["id"] or entry.get("path") != word["audioAsset"]:
        return False
    if entry.get("spokenTextSha256") != spoken_text_sha256(word):
        return False
    if not valid_existing(target) or entry.get("bytes") != target.stat().st_size:
        return False
    return entry.get("audioSha256") == sha256(target)


def current_provenance(model: Path, config: Path, ffmpeg: Path) -> dict:
    version = subprocess.run(
        [str(ffmpeg), "-version"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.splitlines()[0]
    return {
        "piperVersion": importlib.metadata.version("piper-tts"),
        "model": MODEL_NAME,
        "modelSha256": sha256(model),
        "modelConfigSha256": sha256(config),
        "ffmpegVersion": version,
        "synthesis": SYNTHESIS_PARAMS,
        "encoding": ENCODING_PARAMS,
    }


def manifest_entry(word: dict, target: Path) -> dict:
    return {
        "id": word["id"],
        "path": word["audioAsset"],
        "spokenTextSha256": spoken_text_sha256(word),
        "audioSha256": sha256(target),
        "bytes": target.stat().st_size,
    }


def write_manifest_atomic(path: Path, manifest: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    try:
        temporary.write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def load_manifest(path: Path) -> dict:
    if not path.is_file():
        return {}
    manifest = json.loads(path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != MANIFEST_SCHEMA_VERSION:
        return {}
    return manifest


def _ogg_crc(page: bytearray) -> int:
    checksum = 0
    for value in page:
        checksum = (
            ((checksum << 8) & 0xFFFFFFFF)
            ^ _OGG_CRC_TABLE[((checksum >> 24) & 0xFF) ^ value]
        )
    return checksum


def normalize_ogg_serial(path: Path, stable_key: str) -> None:
    """Replace ffmpeg's random Ogg serial and repair each page checksum."""
    data = bytearray(path.read_bytes())
    serial = int.from_bytes(
        hashlib.sha256(stable_key.encode("utf-8")).digest()[:4], "little"
    ) or 1
    cursor = 0
    while cursor < len(data):
        if data[cursor : cursor + 4] != b"OggS" or cursor + 27 > len(data):
            raise RuntimeError(f"malformed Ogg page in {path}")
        segment_count = data[cursor + 26]
        header_size = 27 + segment_count
        if cursor + header_size > len(data):
            raise RuntimeError(f"truncated Ogg header in {path}")
        body_size = sum(data[cursor + 27 : cursor + header_size])
        page_end = cursor + header_size + body_size
        if page_end > len(data):
            raise RuntimeError(f"truncated Ogg body in {path}")
        data[cursor + 14 : cursor + 18] = serial.to_bytes(4, "little")
        data[cursor + 22 : cursor + 26] = b"\0\0\0\0"
        checksum = _ogg_crc(data[cursor:page_end])
        data[cursor + 22 : cursor + 26] = checksum.to_bytes(4, "little")
        cursor = page_end

    normalized = path.with_suffix(path.suffix + ".normalized")
    try:
        normalized.write_bytes(data)
        os.replace(normalized, path)
    finally:
        normalized.unlink(missing_ok=True)


def generate(args: argparse.Namespace) -> None:
    from piper import PiperVoice, SynthesisConfig

    content = json.loads(args.content.read_text(encoding="utf-8"))
    words = content["words"]
    start = max(0, args.start)
    stop = len(words) if args.limit <= 0 else min(len(words), start + args.limit)
    selected = words[start:stop]

    if not args.ffmpeg.is_file():
        raise FileNotFoundError(f"ffmpeg not found: {args.ffmpeg}")
    if not args.model.is_file():
        raise FileNotFoundError(f"Piper model not found: {args.model}")
    config = Path(f"{args.model}.json")
    if not config.is_file():
        raise FileNotFoundError(f"Piper model config not found: {config}")
    actual_model_sha = sha256(args.model)
    if actual_model_sha != MODEL_SHA256:
        raise RuntimeError(
            f"unexpected {MODEL_NAME} SHA-256: {actual_model_sha}"
        )

    provenance = current_provenance(args.model, config, args.ffmpeg)
    loaded_manifest = load_manifest(args.manifest)
    entries = (
        dict(loaded_manifest.get("entries", {}))
        if loaded_manifest.get("provenance") == provenance
        else {}
    )
    build_manifest = {
        "schemaVersion": MANIFEST_SCHEMA_VERSION,
        "contentVersion": content.get("contentVersion"),
        "provenance": provenance,
        "entries": entries,
    }

    args.output.mkdir(parents=True, exist_ok=True)
    args.temp.mkdir(parents=True, exist_ok=True)
    voice = None
    synthesis = None

    generated = 0
    skipped = 0
    for offset, word in enumerate(selected, 1):
        asset_name = Path(word["audioAsset"]).name
        if asset_name != f"{word['id']}.ogg":
            raise RuntimeError(f"audioAsset/id mismatch for {word['id']}")
        target = args.output / asset_name
        if (
            not args.force
            and can_skip(word, target, loaded_manifest, provenance)
        ):
            skipped += 1
        else:
            if voice is None:
                voice = PiperVoice.load(str(args.model), config_path=str(config))
                synthesis = SynthesisConfig(
                    noise_scale=SYNTHESIS_PARAMS["noiseScale"],
                    noise_w_scale=SYNTHESIS_PARAMS["noiseWScale"],
                    length_scale=SYNTHESIS_PARAMS["lengthScale"],
                )
                if voice.config.sample_rate != SYNTHESIS_PARAMS["sourceSampleRate"]:
                    raise RuntimeError(
                        "unexpected Piper source sample rate: "
                        f"{voice.config.sample_rate}"
                    )
            wav_part = args.temp / f"{word['id']}.wav.part"
            wav_path = args.temp / f"{word['id']}.wav"
            ogg_part = args.temp / f"{word['id']}.ogg.part"
            for temporary in (wav_part, wav_path, ogg_part):
                temporary.unlink(missing_ok=True)
            try:
                with wave.open(str(wav_part), "wb") as wav_file:
                    voice.synthesize_wav(
                        spoken_text(word), wav_file, syn_config=synthesis
                    )
                os.replace(wav_part, wav_path)
                subprocess.run(
                    [
                        str(args.ffmpeg),
                        "-hide_banner",
                        "-loglevel",
                        "error",
                        "-fflags",
                        "+bitexact",
                        "-y",
                        "-i",
                        str(wav_path),
                        "-vn",
                        "-ac",
                        "1",
                        "-af",
                        "aresample=22050",
                        "-ar",
                        "48000",
                        "-c:a",
                        "libopus",
                        "-b:a",
                        "32k",
                        "-application",
                        "voip",
                        "-flags:a",
                        "+bitexact",
                        "-map_metadata",
                        "-1",
                        "-f",
                        "ogg",
                        str(ogg_part),
                    ],
                    check=True,
                )
                normalize_ogg_serial(ogg_part, word["id"])
                if not valid_existing(ogg_part):
                    raise RuntimeError(f"invalid encoded output for {word['id']}")
                os.replace(ogg_part, target)
                entries[word["id"]] = manifest_entry(word, target)
                generated += 1
                if generated % 10 == 0:
                    build_manifest["entries"] = dict(sorted(entries.items()))
                    write_manifest_atomic(args.manifest, build_manifest)
            finally:
                for temporary in (wav_part, wav_path, ogg_part):
                    temporary.unlink(missing_ok=True)

        if offset % 50 == 0 or offset == len(selected):
            print(
                f"progress {offset}/{len(selected)} "
                f"(index {start + offset}/{len(words)}, "
                f"generated={generated}, skipped={skipped})",
                flush=True,
            )

    build_manifest["entries"] = dict(sorted(entries.items()))
    write_manifest_atomic(args.manifest, build_manifest)
    packaged = sum(1 for path in args.output.glob("*.ogg") if valid_existing(path))
    print(
        f"complete selected={len(selected)} generated={generated} "
        f"skipped={skipped} packaged={packaged}/{len(words)}",
        flush=True,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--content",
        type=Path,
        default=Path("app/src/main/assets/content/words_v1.json"),
    )
    parser.add_argument(
        "--output", type=Path, default=Path("app/src/main/assets/audio")
    )
    parser.add_argument(
        "--manifest",
        type=Path,
        default=Path("app/src/main/assets/content/audio_manifest_v1.json"),
    )
    parser.add_argument(
        "--model",
        type=Path,
        default=Path(
            "tools/.piper-models/en_US-lessac-medium/"
            "en_US-lessac-medium.onnx"
        ),
    )
    parser.add_argument(
        "--ffmpeg",
        type=Path,
        default=Path(
            r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe"
        ),
    )
    parser.add_argument("--temp", type=Path, default=Path("tmp/audio-generation"))
    parser.add_argument("--start", type=int, default=0)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--force", action="store_true")
    return parser.parse_args()


if __name__ == "__main__":
    generate(parse_args())
