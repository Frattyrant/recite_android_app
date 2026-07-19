"""Generate hash-bound Kokoro corrections for ASR-rejected production audio."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Iterable

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.generate_variant_audio import raw_variants
from tools.pronunciation.commons_audio import normalize_text


KOKORO_NAME = "Kokoro-82M"
KOKORO_VERSION = "1.0"
KOKORO_SOURCE_URL = "https://huggingface.co/hexgrad/Kokoro-82M"
KOKORO_LICENSE = "Apache-2.0"
SAFE_FILE = re.compile(r"[0-9a-f]{24}\.wav\Z")
SYNTHESIS_OVERRIDES = {
    # Short technical/brand tokens need a small prosody or boundary hint. The
    # displayed/audited text stays unchanged; only build-time synthesis input
    # is adjusted, and the exact input is recorded in the manifest.
    "predrop": "pre drop",
    "hongbai": "hong bye",
    "crank": "crank!",
    "notch": "notch!",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def correction_targets(
    content: dict[str, Any],
    production_manifest: dict[str, Any],
    asr_report: dict[str, Any],
) -> list[str]:
    entries = production_manifest.get("entries", {})
    if not isinstance(entries, dict):
        raise ValueError("production manifest entries must be an object")
    words = content.get("words", [])
    by_id = {str(word.get("id", "")): word for word in words}
    if len(by_id) != len(words) or not by_id:
        raise ValueError("content requires unique word IDs")

    path_targets: dict[str, list[str]] = {}
    for word_id, entry in entries.items():
        word = by_id.get(word_id)
        if word is None:
            raise ValueError(f"manifest word missing from content: {word_id}")
        variants = raw_variants(
            str(word.get("english", "")),
            str(word.get("kind", "TERM")),
        )
        complete_path = str(entry.get("path", ""))
        if complete_path:
            path_targets[complete_path] = variants
        segments = entry.get("segments", [])
        if segments and len(segments) != len(variants):
            raise ValueError(f"segment count mismatch: {word_id}")
        for variant, segment in zip(variants, segments):
            path = str(segment.get("path", ""))
            if path:
                path_targets[path] = [variant]

    selected: dict[str, str] = {}
    failures = [item for item in asr_report.get("assets", []) if not item.get("passed")]
    if not failures:
        return []
    for failure in failures:
        path = str(failure.get("path", ""))
        targets = path_targets.get(path)
        if not targets:
            raise ValueError(f"ASR failure path is not in production manifest: {path}")
        for text in targets:
            normalized = normalize_text(text)
            if normalized:
                selected.setdefault(normalized, text.strip())
    return [selected[key] for key in sorted(selected)]


def _write_atomic_wav(path: Path, audio: Any, sample_rate: int) -> None:
    import numpy as np
    import soundfile as sf

    samples = np.asarray(audio, dtype=np.float32).reshape(-1)
    if samples.size < sample_rate // 10 or not np.isfinite(samples).all():
        raise RuntimeError(f"invalid Kokoro output for {path.name}")
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".partial.wav")
    try:
        sf.write(temporary, samples, sample_rate, subtype="PCM_16", format="WAV")
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def generate(
    texts: Iterable[str],
    output: Path,
    voice: str,
    repo_id: str,
) -> dict[str, Any]:
    import numpy as np
    from kokoro import KPipeline

    pipeline = KPipeline(lang_code="a", repo_id=repo_id)
    manifest_path = output / "kokoro_corrections_v1.json"
    existing_records: dict[str, dict[str, Any]] = {}
    if manifest_path.is_file():
        existing_payload = json.loads(manifest_path.read_text(encoding="utf-8"))
        if existing_payload.get("schemaVersion") != 1:
            raise ValueError("unsupported existing Kokoro correction manifest")
        existing_records = {
            normalize_text(str(record.get("text", ""))): record
            for record in existing_payload.get("records", [])
        }
    records_by_key: dict[str, dict[str, Any]] = {}
    values = list(texts)
    for index, text in enumerate(values, 1):
        key = normalize_text(text)
        synthesis_text = SYNTHESIS_OVERRIDES.get(normalize_text(text), text)
        filename = hashlib.sha256(text.encode("utf-8")).hexdigest()[:24] + ".wav"
        if not SAFE_FILE.fullmatch(filename):
            raise AssertionError(filename)
        target = output / filename
        previous = existing_records.get(key, {})
        previous_synthesis = str(previous.get("synthesisText", previous.get("text", "")))
        if not target.is_file() or previous_synthesis != synthesis_text:
            chunks = [
                audio
                for _, _, audio in pipeline(synthesis_text, voice=voice, speed=1.0)
            ]
            if not chunks:
                raise RuntimeError(f"Kokoro returned no audio for {text!r}")
            _write_atomic_wav(target, np.concatenate(chunks), 24_000)
        records_by_key[key] = (
            {
                "text": text,
                "synthesisText": synthesis_text,
                "fileName": filename,
                "modelName": KOKORO_NAME,
                "modelVersion": KOKORO_VERSION,
                "modelSourceUrl": KOKORO_SOURCE_URL,
                "modelLicense": KOKORO_LICENSE,
                "voice": voice,
                "bytes": target.stat().st_size,
                "sha256": sha256(target),
            }
        )
        if index % 10 == 0 or index == len(values):
            print(f"Kokoro corrections {index}/{len(values)}", flush=True)
    for key, record in existing_records.items():
        if key in records_by_key:
            continue
        file_name = str(record.get("fileName", ""))
        target = output / file_name
        if (
            SAFE_FILE.fullmatch(file_name)
            and target.is_file()
            and sha256(target) == record.get("sha256")
        ):
            preserved = dict(record)
            preserved.setdefault("synthesisText", str(record.get("text", "")))
            records_by_key[key] = preserved
    records = [records_by_key[key] for key in sorted(records_by_key)]
    manifest = {
        "schemaVersion": 1,
        "modelName": KOKORO_NAME,
        "modelVersion": KOKORO_VERSION,
        "modelSourceUrl": KOKORO_SOURCE_URL,
        "modelLicense": KOKORO_LICENSE,
        "voice": voice,
        "recordCount": len(records),
        "records": records,
    }
    temporary = manifest_path.with_suffix(".partial.json")
    temporary.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.replace(temporary, manifest_path)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--production-manifest", type=Path, required=True)
    parser.add_argument("--asr-report", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--voice", default="af_heart")
    parser.add_argument("--repo-id", default="hexgrad/Kokoro-82M")
    args = parser.parse_args()
    targets = correction_targets(
        json.loads(args.content.read_text(encoding="utf-8")),
        json.loads(args.production_manifest.read_text(encoding="utf-8")),
        json.loads(args.asr_report.read_text(encoding="utf-8")),
    )
    manifest = generate(targets, args.output, args.voice, args.repo_id)
    print(f"PASS corrections={manifest['recordCount']}")


if __name__ == "__main__":
    main()
