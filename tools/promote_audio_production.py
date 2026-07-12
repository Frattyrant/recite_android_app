"""Atomically promote a validated MIearn production audio staging pack."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
from pathlib import Path


def _tree_hashes(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in root.rglob("*")
        if path.is_file()
    }


def promote_production(
    staging: Path,
    assets: Path,
    manifest_target: Path,
    expected_count: int = 2_704,
) -> None:
    validation_path = staging / "validation_report.json"
    if not validation_path.is_file():
        raise RuntimeError("validation report is required before promotion")
    validation = json.loads(validation_path.read_text(encoding="utf-8"))
    if not validation.get("passed") or validation.get("validatedEntries") != expected_count:
        raise RuntimeError("validation did not pass for the expected entry count")
    staged_audio = staging / "audio"
    staged_manifest = staging / "audio_manifest_v1.json"
    if not staged_audio.is_dir() or not staged_manifest.is_file():
        raise RuntimeError("validated staging pack is incomplete")

    assets_parent = assets.parent
    assets_parent.mkdir(parents=True, exist_ok=True)
    manifest_target.parent.mkdir(parents=True, exist_ok=True)
    new_assets = assets_parent / f"{assets.name}.v2.3-new"
    backup_assets = assets_parent / f"{assets.name}.v2.2-backup"
    new_manifest = manifest_target.with_name(manifest_target.name + ".v2.3-new")
    backup_manifest = manifest_target.with_name(manifest_target.name + ".v2.2-backup")
    for path in (new_assets, backup_assets):
        if path.exists():
            shutil.rmtree(path)
    for path in (new_manifest, backup_manifest):
        path.unlink(missing_ok=True)

    shutil.copytree(staged_audio, new_assets)
    shutil.copy2(staged_manifest, new_manifest)
    if _tree_hashes(staged_audio) != _tree_hashes(new_assets):
        shutil.rmtree(new_assets)
        new_manifest.unlink(missing_ok=True)
        raise RuntimeError("copied audio hash verification failed")
    if hashlib.sha256(staged_manifest.read_bytes()).digest() != hashlib.sha256(new_manifest.read_bytes()).digest():
        shutil.rmtree(new_assets)
        new_manifest.unlink(missing_ok=True)
        raise RuntimeError("copied manifest hash verification failed")

    assets_backed_up = False
    manifest_backed_up = False
    try:
        if assets.exists():
            os.replace(assets, backup_assets)
            assets_backed_up = True
        os.replace(new_assets, assets)
        if manifest_target.exists():
            os.replace(manifest_target, backup_manifest)
            manifest_backed_up = True
        os.replace(new_manifest, manifest_target)
    except Exception:
        if assets.exists() and assets_backed_up:
            shutil.rmtree(assets)
        if assets_backed_up and backup_assets.exists():
            os.replace(backup_assets, assets)
        if manifest_target.exists() and manifest_backed_up:
            manifest_target.unlink()
        if manifest_backed_up and backup_manifest.exists():
            os.replace(backup_manifest, manifest_target)
        raise
    finally:
        if new_assets.exists():
            shutil.rmtree(new_assets)
        new_manifest.unlink(missing_ok=True)
    if backup_assets.exists():
        shutil.rmtree(backup_assets)
    backup_manifest.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()
    report = json.loads((args.root / "validation_report.json").read_text(encoding="utf-8"))
    promote_production(args.root, args.assets, args.manifest)
    print(f"promoted entries={report['validatedEntries']}")


if __name__ == "__main__":
    main()
