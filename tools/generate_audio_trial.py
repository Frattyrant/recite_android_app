"""Generate the isolated MIearn Piper Lessac High A/B trial bundle."""

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

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.audio_profiles import LESSAC_HIGH, load_pronunciation_overrides
from tools.audio_trial import (
    _is_production_audio_path,
    load_words,
    plan_trial_entry,
    select_trial_words,
    write_trial_selection,
)
from tools.generate_audio import (
    SYNTHESIS_PARAMS,
    ffmpeg_encode_args,
    normalize_ogg_serial,
    valid_existing,
)
from tools.generate_variant_audio import combine_wavs


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def file_metadata(path: Path, root: Path) -> dict:
    return {
        "path": path.relative_to(root).as_posix(),
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
    }


def encode_candidate(ffmpeg: Path, source: Path, target: Path, stable_key: str) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_suffix(target.suffix + ".part")
    temporary.unlink(missing_ok=True)
    try:
        subprocess.run(
            [str(ffmpeg), *ffmpeg_encode_args(LESSAC_HIGH, source, temporary)],
            check=True,
        )
        normalize_ogg_serial(temporary, stable_key)
        if not valid_existing(temporary):
            raise RuntimeError(f"invalid encoded candidate audio: {target}")
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)


def write_trial_readme(root: Path, report: dict) -> None:
    lines = [
        "# MIearn Piper Lessac High A/B Trial",
        "",
        "- A: current Lessac Medium 32 kbps assets",
        "- B: candidate Lessac High 40 kbps Opus (`application=audio`)",
        "- Production Android assets were not modified.",
        "",
        "| # | English | Chinese | Category | Override |",
        "|---:|---|---|---|---|",
    ]
    for index, entry in enumerate(report["entries"], 1):
        override_keys = ", ".join(
            segment["overrideKey"]
            for segment in entry["segments"]
            if segment.get("overrideKey")
        ) or "—"
        lines.append(
            f"| {index} | {entry['english'].replace('|', '/')} | "
            f"{entry['chinese'].replace('|', '/')} | {entry['category']} | "
            f"{override_keys} |"
        )
    (root / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def generate_trial(
    content_path: Path,
    overrides_path: Path,
    current_audio: Path,
    current_manifest_path: Path,
    model: Path,
    ffmpeg: Path,
    output: Path,
) -> dict:
    from piper import PiperVoice, SynthesisConfig

    if _is_production_audio_path(output):
        raise ValueError("trial output cannot use the production audio directory")
    config = Path(f"{model}.json")
    for required in (content_path, overrides_path, current_manifest_path, model, config, ffmpeg):
        if not required.is_file():
            raise FileNotFoundError(required)
    if sha256(model) != LESSAC_HIGH.model_sha256:
        raise RuntimeError("unexpected Lessac High model SHA-256")

    words = load_words(content_path)
    selection = select_trial_words(words)
    write_trial_selection(output, selection)
    overrides = load_pronunciation_overrides(overrides_path)
    current_manifest = json.loads(current_manifest_path.read_text(encoding="utf-8"))
    current_entries = current_manifest.get("entries", {})

    a_audio = output / "a-current/audio"
    b_audio = output / "b-high/audio"
    b_variants = b_audio / "variants"
    temporary_root = output / ".tmp"
    for directory in (a_audio, b_audio, b_variants, temporary_root):
        directory.mkdir(parents=True, exist_ok=True)

    voice = PiperVoice.load(str(model), config_path=str(config))
    if voice.config.sample_rate != LESSAC_HIGH.source_sample_rate:
        raise RuntimeError(f"unexpected Piper sample rate: {voice.config.sample_rate}")
    synthesis = SynthesisConfig(
        noise_scale=SYNTHESIS_PARAMS["noiseScale"],
        noise_w_scale=SYNTHESIS_PARAMS["noiseWScale"],
        length_scale=SYNTHESIS_PARAMS["lengthScale"],
    )

    entries = []
    try:
        for number, word in enumerate(selection.words, 1):
            word_id = word["id"]
            current_source = current_audio / f"{word_id}.ogg"
            if not current_source.is_file():
                raise FileNotFoundError(f"current audio missing: {current_source}")
            current_target = a_audio / current_source.name
            shutil.copy2(current_source, current_target)

            current_entry = current_entries.get(word_id, {})
            for segment in current_entry.get("segments", []):
                segment_source = current_audio / Path(segment["path"]).relative_to("audio")
                segment_target = output / "a-current" / segment["path"]
                segment_target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(segment_source, segment_target)

            plan = plan_trial_entry(word, overrides)
            word_temp = temporary_root / word_id
            word_temp.mkdir(parents=True, exist_ok=True)
            wavs = []
            candidate_segments = []
            for index, segment in enumerate(plan["segments"]):
                wav_path = word_temp / f"{index:02d}.wav"
                with wave.open(str(wav_path), "wb") as wav_file:
                    voice.synthesize_wav(
                        segment["spokenText"],
                        wav_file,
                        syn_config=synthesis,
                    )
                wavs.append(wav_path)
                if len(plan["segments"]) > 1:
                    segment_target = b_variants / f"{word_id}_{index:02d}.ogg"
                    encode_candidate(
                        ffmpeg,
                        wav_path,
                        segment_target,
                        f"trial:{word_id}:{index}",
                    )
                    candidate_segments.append(
                        {**segment, **file_metadata(segment_target, output)}
                    )

            full_wav = wavs[0]
            if len(wavs) > 1:
                full_wav = word_temp / "combined.wav"
                combine_wavs(wavs, full_wav)
            candidate_target = b_audio / f"{word_id}.ogg"
            encode_candidate(ffmpeg, full_wav, candidate_target, f"trial:{word_id}")
            entries.append(
                {
                    **plan,
                    "current": file_metadata(current_target, output),
                    "candidate": file_metadata(candidate_target, output),
                    "candidateSegments": candidate_segments,
                }
            )
            print(f"trial progress {number}/{len(selection.words)}", flush=True)
    finally:
        if temporary_root.exists():
            shutil.rmtree(temporary_root)

    ffmpeg_version = subprocess.run(
        [str(ffmpeg), "-version"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.splitlines()[0]
    report = {
        "schemaVersion": 1,
        "entryCount": len(entries),
        "missingRequiredTerms": selection.missing_required_terms,
        "candidateProfile": {
            "name": LESSAC_HIGH.name,
            "modelSha256": sha256(model),
            "modelConfigSha256": sha256(config),
            "piperVersion": importlib.metadata.version("piper-tts"),
            "ffmpegVersion": ffmpeg_version,
            "bitRateKbps": LESSAC_HIGH.bit_rate_kbps,
            "application": LESSAC_HIGH.application,
            "channels": 1,
            "encodedSampleRate": LESSAC_HIGH.encoded_sample_rate,
        },
        "entries": entries,
    }
    report_path = output / "trial_report.json"
    temporary_report = report_path.with_suffix(".json.tmp")
    temporary_report.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary_report, report_path)
    write_trial_readme(output, report)
    return report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--content",
        type=Path,
        default=Path("app/src/main/assets/content/words_v1.json"),
    )
    parser.add_argument(
        "--overrides",
        type=Path,
        default=Path("tools/audio/pronunciation_overrides.json"),
    )
    parser.add_argument(
        "--current-audio",
        type=Path,
        default=Path("app/src/main/assets/audio"),
    )
    parser.add_argument(
        "--current-manifest",
        type=Path,
        default=Path("app/src/main/assets/content/audio_manifest_v1.json"),
    )
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument(
        "--ffmpeg",
        type=Path,
        default=Path(r"D:\ffmpeg\ffmpeg-master-latest-win64-gpl\bin\ffmpeg.exe"),
    )
    parser.add_argument("--output", type=Path, default=Path("tmp/audio-trial"))
    args = parser.parse_args()
    report = generate_trial(
        content_path=args.content,
        overrides_path=args.overrides,
        current_audio=args.current_audio,
        current_manifest_path=args.current_manifest,
        model=args.model,
        ffmpeg=args.ffmpeg,
        output=args.output,
    )
    print(f"complete entries={report['entryCount']}")


if __name__ == "__main__":
    main()
