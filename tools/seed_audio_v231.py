"""Seed V2.31 staging with hash-verified V2.3 audio whose speech plan is unchanged."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.audio_profiles import PronunciationOverrides, load_pronunciation_overrides
from tools.audio_production import (
    ProductionEntryPlan,
    content_sha256,
    load_human_audio_sources,
    plan_production,
    speech_plan_sha256,
)
from tools.generate_audio import sha256, write_manifest_atomic
from tools.generate_variant_audio import segment_plan_sha256


FILE_METADATA_KEYS = (
    "path",
    "bytes",
    "audioSha256",
    "durationSeconds",
    "codec",
    "channels",
    "sampleRate",
)


def plans_are_audio_equivalent(
    previous: ProductionEntryPlan,
    current: ProductionEntryPlan,
) -> bool:
    if len(previous.segments) != len(current.segments):
        return False
    if any(segment.source_type != "piper" for segment in current.segments):
        return False
    return all(
        old.display_text == new.display_text and old.spoken_text == new.spoken_text
        for old, new in zip(previous.segments, current.segments)
    )


def _git_json(revision_path: str) -> dict:
    raw = subprocess.check_output(["git", "show", revision_path])
    return json.loads(raw.decode("utf-8"))


def _copy_verified(source_root: Path, output: Path, metadata: dict) -> dict:
    relative = str(metadata.get("path", ""))
    source = source_root / relative
    if (
        not source.is_file()
        or source.stat().st_size != metadata.get("bytes")
        or sha256(source) != metadata.get("audioSha256")
    ):
        raise RuntimeError(f"packaged V2.3 audio failed hash verification: {relative}")
    target = output / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    if sha256(target) != metadata.get("audioSha256"):
        raise RuntimeError(f"seeded audio copy hash mismatch: {relative}")
    return {key: metadata[key] for key in FILE_METADATA_KEYS}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--content", type=Path, required=True)
    parser.add_argument("--overrides", type=Path, required=True)
    parser.add_argument("--human-attributions", type=Path, required=True)
    parser.add_argument("--human-audio-root", type=Path, required=True)
    parser.add_argument("--production-assets", type=Path, required=True)
    parser.add_argument("--production-manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--old-content-revision", default="HEAD:app/src/main/assets/content/words_v1.json")
    parser.add_argument("--old-overrides-revision", default="HEAD:tools/audio/pronunciation_overrides.json")
    args = parser.parse_args()

    resolved_output = args.output.resolve()
    if "BuildCache" not in resolved_output.parts or resolved_output.name != "audio-production-v231":
        raise ValueError("V2.31 seed output must be the dedicated BuildCache staging directory")
    if resolved_output.exists():
        shutil.rmtree(resolved_output)
    resolved_output.mkdir(parents=True)

    current_content = json.loads(args.content.read_text(encoding="utf-8"))
    old_content = _git_json(args.old_content_revision)
    old_overrides = PronunciationOverrides.from_json(_git_json(args.old_overrides_revision))
    current_overrides = load_pronunciation_overrides(args.overrides)
    human_audio = load_human_audio_sources(args.human_attributions, args.human_audio_root)
    old_plans = {
        plan.word_id: plan
        for plan in plan_production(
            old_content["words"],
            old_overrides,
            require_ipa_alignment=False,
        )
    }
    current_plans = plan_production(
        current_content["words"],
        current_overrides,
        human_audio=human_audio,
    )
    old_manifest = json.loads(args.production_manifest.read_text(encoding="utf-8"))
    if old_manifest.get("schemaVersion") != 2:
        raise ValueError("seed source must be the validated V2.3 schema-2 manifest")

    entries: dict[str, dict] = {}
    reused_ids: list[str] = []
    regeneration_ids: list[str] = []
    for plan in current_plans:
        old_plan = old_plans.get(plan.word_id)
        old_entry = old_manifest.get("entries", {}).get(plan.word_id)
        if (
            old_plan is None
            or not isinstance(old_entry, dict)
            or not plans_are_audio_equivalent(old_plan, plan)
        ):
            regeneration_ids.append(plan.word_id)
            continue
        complete_meta = _copy_verified(args.production_assets, resolved_output, old_entry)
        entry = {
            "id": plan.word_id,
            **complete_meta,
            "sourceType": "piper",
            "expectedIpa": "； ".join(segment.expected_ipa for segment in plan.segments),
            "segmentPlanSha256": segment_plan_sha256(
                [segment.display_text for segment in plan.segments]
            ),
            "speechPlanSha256": speech_plan_sha256(plan),
        }
        if len(plan.segments) == 1:
            entry["spokenText"] = plan.segments[0].spoken_text
            entry["expectedTranscript"] = plan.segments[0].expected_transcript
            entry["overrideKey"] = plan.segments[0].override_key
        else:
            old_segments = old_entry.get("segments", [])
            if len(old_segments) != len(plan.segments):
                raise RuntimeError(f"seed segment count mismatch: {plan.word_id}")
            segments: list[dict] = []
            for segment, old_segment in zip(plan.segments, old_segments):
                segments.append(
                    {
                        "index": segment.index,
                        "text": segment.display_text,
                        "spokenText": segment.spoken_text,
                        "expectedTranscript": segment.expected_transcript,
                        "overrideKey": segment.override_key,
                        "expectedIpa": segment.expected_ipa,
                        "sourceType": "piper",
                        "textSha256": old_segment["textSha256"],
                        **_copy_verified(args.production_assets, resolved_output, old_segment),
                    }
                )
            entry["pauseBetweenSegmentsMs"] = 500
            entry["segments"] = segments
        entries[plan.word_id] = entry
        reused_ids.append(plan.word_id)

    manifest = {
        "schemaVersion": 3,
        "contentSha256": content_sha256(args.content),
        "entryCount": len(current_plans),
        "profile": old_manifest["profile"],
        "entries": entries,
    }
    write_manifest_atomic(resolved_output / "audio_manifest_v1.json", manifest)
    write_manifest_atomic(
        resolved_output / "seed_audit.json",
        {
            "schemaVersion": 1,
            "reusedCount": len(reused_ids),
            "regenerationCount": len(regeneration_ids),
            "reusedIds": reused_ids,
            "regenerationIds": regeneration_ids,
        },
    )
    print(f"PASS reused={len(reused_ids)} regenerate={len(regeneration_ids)}")


if __name__ == "__main__":
    main()
