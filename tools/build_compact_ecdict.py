#!/usr/bin/env python3
"""Build MIearn's deterministic reduced ECDICT SQLite asset."""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
import os
import sqlite3
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

EXAM_TAGS = {"zk", "gk", "cet4", "cet6", "ky", "toefl", "ielts", "gre"}
SOURCE_URL = "https://github.com/skywind3000/ECDICT"


@dataclass(frozen=True)
class BuildResult:
    entry_count: int
    logical_sha256: str
    database_sha256: str
    database_bytes: int


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _positive_rank(value: object) -> int | None:
    text = str(value or "").strip()
    return int(text) if text.isdigit() and int(text) > 0 else None


def score(row: dict[str, str]) -> tuple[int, int, str]:
    tags = set(row.get("tag", "").split())
    exam = int(bool(tags & EXAM_TAGS))
    ranks = [
        rank
        for rank in (
            _positive_rank(row.get("bnc")),
            _positive_rank(row.get("frq")),
        )
        if rank is not None
    ]
    return -exam, min(ranks, default=1_000_000_000), row["word"].casefold()


def select_rows(
    rows: Iterable[dict[str, object]],
    limit: int = 120_000,
) -> list[dict[str, str]]:
    usable: dict[str, dict[str, str]] = {}
    for raw in rows:
        word = str(raw.get("word") or "").strip()
        translation = str(raw.get("translation") or "").strip()
        if not word or not translation:
            continue
        normalized = word.casefold()
        if normalized not in usable:
            usable[normalized] = {
                "word": normalized,
                "phonetic": str(raw.get("phonetic") or "").strip(),
                "translation": translation,
                "exchange": str(raw.get("exchange") or "").strip(),
                "tag": str(raw.get("tag") or "").strip(),
                "bnc": str(raw.get("bnc") or "").strip(),
                "frq": str(raw.get("frq") or "").strip(),
            }
    return sorted(usable.values(), key=score)[:limit]


def logical_sha256(rows: list[dict[str, str]]) -> str:
    payload = "\n".join(
        "\t".join(
            (
                row["word"],
                row["phonetic"],
                row["translation"],
                row["exchange"],
            )
        )
        for row in sorted(rows, key=lambda item: item["word"])
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def build_database(
    rows: Iterable[dict[str, object]],
    output: Path,
    limit: int = 120_000,
) -> BuildResult:
    selected = select_rows(rows, limit)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)
    database = sqlite3.connect(output)
    try:
        database.execute("PRAGMA page_size=4096")
        database.execute("PRAGMA journal_mode=OFF")
        database.execute("PRAGMA synchronous=OFF")
        database.execute(
            """
            CREATE TABLE entry (
                word TEXT PRIMARY KEY COLLATE NOCASE,
                phonetic TEXT NOT NULL,
                translation TEXT NOT NULL,
                exchange TEXT NOT NULL
            ) WITHOUT ROWID
            """
        )
        database.executemany(
            "INSERT INTO entry(word, phonetic, translation, exchange) VALUES (?, ?, ?, ?)",
            [
                (
                    row["word"],
                    row["phonetic"],
                    row["translation"],
                    row["exchange"],
                )
                for row in sorted(selected, key=lambda item: item["word"])
            ],
        )
        database.commit()
        database.execute("VACUUM")
    finally:
        database.close()
    return BuildResult(
        entry_count=len(selected),
        logical_sha256=logical_sha256(selected),
        database_sha256=sha256(output),
        database_bytes=output.stat().st_size,
    )


def read_csv(path: Path) -> Iterable[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as source:
        yield from csv.DictReader(source)


def write_gzip(source: Path, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix(output.suffix + ".tmp")
    try:
        with source.open("rb") as raw, temporary.open("wb") as destination:
            with gzip.GzipFile(
                filename="",
                mode="wb",
                fileobj=destination,
                compresslevel=9,
                mtime=0,
            ) as compressed:
                for block in iter(lambda: raw.read(1024 * 1024), b""):
                    compressed.write(block)
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--revision", type=Path)
    parser.add_argument("--limit", type=int, default=120_000)
    args = parser.parse_args()

    revision_path = args.revision or args.source.with_name("ecdict.revision.txt")
    revision = revision_path.read_text(encoding="utf-8").strip()
    database_path = args.output.with_suffix("")
    result = build_database(read_csv(args.source), database_path, args.limit)
    write_gzip(database_path, args.output)
    manifest = {
        "schemaVersion": 1,
        "source": SOURCE_URL,
        "sourceRevision": revision,
        "sourceCsvSha256": sha256(args.source),
        "license": "MIT",
        "entryCount": result.entry_count,
        "logicalSha256": result.logical_sha256,
        "databaseBytes": result.database_bytes,
        "databaseSha256": result.database_sha256,
        "gzipBytes": args.output.stat().st_size,
        "gzipSha256": sha256(args.output),
    }
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    database_path.unlink(missing_ok=True)
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()
