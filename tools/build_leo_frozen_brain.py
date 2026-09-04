#!/usr/bin/env python3
"""Build Leo's immutable ~500 MB Spanish knowledge brain.

The brain is a read-only SQLite + FTS4 retrieval database. The source corpus is the
fixed 20231101.es Wikimedia/Wikipedia dataset on Hugging Face (CC BY-SA 3.0/GFDL),
plus a small first-party Leo seed corpus. The builder stops on actual SQLite file size,
not on synthetic padding, so every byte belongs to documents, indexes or metadata.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sqlite3
import sys
import time
import zipfile
from pathlib import Path
from typing import Iterable, Iterator

import pyarrow.parquet as pq
import requests

VERSION = "leo-brain-v1"
DATASET = "wikimedia/wikipedia"
DATASET_CONFIG = "20231101.es"
SHARD_COUNT = 13
HF_BASE = (
    "https://huggingface.co/datasets/wikimedia/wikipedia/resolve/main/"
    "20231101.es/train-{index:05d}-of-00013.parquet?download=true"
)
DEFAULT_TARGET = 496_000_000
MIN_INSTALLED = 490_000_000
MAX_INSTALLED = 499_000_000
DATABASE_NAME = "brain.sqlite"
INTERNAL_MANIFEST = "brain-manifest.json"
EXTERNAL_MANIFEST = "leo-brain-v1-manifest.json"
ARCHIVE_NAME = "leo-brain-v1.zip"
ATTRIBUTION_NAME = "ATTRIBUTION.txt"

WHITESPACE = re.compile(r"\s+")
BAD_TITLE = re.compile(r"\(desambiguaci[oó]n\)", re.IGNORECASE)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def clean_text(value: str) -> str:
    value = value.replace("\x00", " ").replace("\r", "\n")
    return WHITESPACE.sub(" ", value).strip()


def chunks(text: str, maximum: int = 11_500, overlap: int = 320) -> Iterator[str]:
    """Split long articles without storing only their opening paragraph."""
    text = clean_text(text)
    if len(text) <= maximum:
        if len(text) >= 350:
            yield text
        return
    start = 0
    while start < len(text):
        tentative = min(len(text), start + maximum)
        end = tentative
        if tentative < len(text):
            floor = start + maximum // 2
            candidates = [
                text.rfind(". ", floor, tentative),
                text.rfind("; ", floor, tentative),
                text.rfind(" ", floor, tentative),
            ]
            cut = max(candidates)
            if cut > floor:
                end = cut + 1
        piece = text[start:end].strip()
        if len(piece) >= 350:
            yield piece
        if end >= len(text):
            break
        start = max(start + 1, end - overlap)


def download(url: str, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix(destination.suffix + ".part")
    existing = partial.stat().st_size if partial.exists() else 0
    headers = {"User-Agent": "LEO-Frozen-Brain-Builder/1.0", "Accept-Encoding": "identity"}
    if existing:
        headers["Range"] = f"bytes={existing}-"
    with requests.get(url, headers=headers, stream=True, timeout=(30, 120)) as response:
        if existing and response.status_code != 206:
            partial.unlink(missing_ok=True)
            existing = 0
            return download(url, destination)
        response.raise_for_status()
        mode = "ab" if existing else "wb"
        total = response.headers.get("Content-Length")
        total_bytes = existing + int(total) if total and total.isdigit() else 0
        done = existing
        last = time.monotonic()
        with partial.open(mode) as handle:
            for block in response.iter_content(chunk_size=1024 * 1024):
                if not block:
                    continue
                handle.write(block)
                done += len(block)
                now = time.monotonic()
                if now - last >= 5:
                    if total_bytes:
                        print(f"download {destination.name}: {done * 100 // total_bytes}% ({done}/{total_bytes})", flush=True)
                    else:
                        print(f"download {destination.name}: {done} bytes", flush=True)
                    last = now
    partial.replace(destination)


def seed_records(seed_path: Path) -> Iterator[tuple[str, str, str, str]]:
    if not seed_path.is_file():
        return
    for line_number, line in enumerate(seed_path.read_text(encoding="utf-8").splitlines(), 1):
        line = line.strip()
        if not line:
            continue
        item = json.loads(line)
        title = clean_text(str(item.get("title", "")))
        text = clean_text(str(item.get("text", "")))
        url = str(item.get("url", "leo://seed")).strip() or "leo://seed"
        if title and len(text) >= 80:
            yield (f"leo-seed-{line_number}", title, url, text)


def wikipedia_records(parquet_path: Path) -> Iterator[tuple[str, str, str, str]]:
    parquet = pq.ParquetFile(parquet_path)
    for batch in parquet.iter_batches(batch_size=96, columns=["id", "url", "title", "text"]):
        data = batch.to_pydict()
        for source_id, url, title, text in zip(data["id"], data["url"], data["title"], data["text"]):
            title = clean_text(str(title or ""))
            text = str(text or "")
            if not title or BAD_TITLE.search(title) or len(text) < 500:
                continue
            for index, piece in enumerate(chunks(text)):
                suffix = "" if index == 0 else f" · parte {index + 1}"
                yield (f"wiki-{source_id}-{index}", title + suffix, str(url or ""), piece)


def create_database(path: Path) -> sqlite3.Connection:
    path.unlink(missing_ok=True)
    connection = sqlite3.connect(path)
    connection.execute("PRAGMA journal_mode=OFF")
    connection.execute("PRAGMA synchronous=OFF")
    connection.execute("PRAGMA temp_store=MEMORY")
    connection.execute("PRAGMA page_size=4096")
    connection.execute("PRAGMA locking_mode=EXCLUSIVE")
    connection.execute("PRAGMA user_version=1")
    connection.executescript(
        """
        CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE articles(
            id INTEGER PRIMARY KEY,
            source_id TEXT NOT NULL UNIQUE,
            title TEXT NOT NULL,
            url TEXT NOT NULL,
            text TEXT NOT NULL
        );
        CREATE INDEX articles_title ON articles(title);
        CREATE VIRTUAL TABLE articles_fts USING fts4(
            title,
            text,
            content='articles',
            tokenize=unicode61
        );
        """
    )
    return connection


def insert_record(connection: sqlite3.Connection, record: tuple[str, str, str, str]) -> None:
    source_id, title, url, text = record
    cursor = connection.execute(
        "INSERT OR IGNORE INTO articles(source_id,title,url,text) VALUES(?,?,?,?)",
        (source_id, title, url, text),
    )
    if cursor.rowcount != 1:
        return
    rowid = cursor.lastrowid
    connection.execute(
        "INSERT INTO articles_fts(docid,title,text) VALUES(?,?,?)",
        (rowid, title, text),
    )


def ingest_until_target(
    connection: sqlite3.Connection,
    database_path: Path,
    records: Iterable[tuple[str, str, str, str]],
    target_bytes: int,
    count: int,
) -> tuple[int, bool]:
    pending = 0
    for record in records:
        insert_record(connection, record)
        count += 1
        pending += 1
        # Smaller commits near the target limit overshoot while keeping the build fast earlier.
        current = database_path.stat().st_size if database_path.exists() else 0
        commit_every = 12 if current >= target_bytes - 20_000_000 else 64
        if pending >= commit_every:
            connection.commit()
            pending = 0
            size = database_path.stat().st_size
            if count % 512 < commit_every:
                print(f"indexed documents={count:,} brain={size / 1_000_000:.1f} MB", flush=True)
            if size >= target_bytes:
                return count, True
    connection.commit()
    return count, database_path.stat().st_size >= target_bytes


def build(args: argparse.Namespace) -> tuple[Path, Path]:
    output = Path(args.output_dir).resolve()
    cache = output / "source-cache"
    package = output / "package"
    if package.exists():
        shutil.rmtree(package)
    package.mkdir(parents=True, exist_ok=True)
    cache.mkdir(parents=True, exist_ok=True)
    database = package / DATABASE_NAME
    connection = create_database(database)

    count = 0
    reached = False
    seed = Path(args.seed)
    count, reached = ingest_until_target(connection, database, seed_records(seed), args.target_bytes, count)

    used_shards: list[str] = []
    try:
        for shard_index in range(SHARD_COUNT):
            if reached:
                break
            shard = cache / f"train-{shard_index:05d}-of-00013.parquet"
            if not shard.is_file():
                download(HF_BASE.format(index=shard_index), shard)
            if shard.stat().st_size < 20_000_000:
                raise RuntimeError(f"Wikipedia shard looks incomplete: {shard}")
            used_shards.append(shard.name)
            count, reached = ingest_until_target(
                connection,
                database,
                wikipedia_records(shard),
                args.target_bytes,
                count,
            )
        connection.execute("INSERT OR REPLACE INTO metadata(key,value) VALUES('version',?)", (VERSION,))
        connection.execute("INSERT OR REPLACE INTO metadata(key,value) VALUES('source_dataset',?)", (DATASET,))
        connection.execute("INSERT OR REPLACE INTO metadata(key,value) VALUES('source_config',?)", (DATASET_CONFIG,))
        connection.execute("INSERT OR REPLACE INTO metadata(key,value) VALUES('document_count',?)", (str(count),))
        connection.execute("INSERT OR REPLACE INTO metadata(key,value) VALUES('training_method','sqlite-fts4-retrieval-index')")
        connection.execute("ANALYZE")
        connection.commit()
    finally:
        connection.close()

    installed_bytes = database.stat().st_size
    if not reached or installed_bytes < args.min_bytes:
        raise RuntimeError(f"Brain did not reach minimum size: {installed_bytes} < {args.min_bytes}")
    if installed_bytes > args.max_bytes:
        raise RuntimeError(f"Brain exceeded hard 500 MB budget: {installed_bytes} > {args.max_bytes}")

    # Verify that the frozen index actually answers representative offline queries.
    check = sqlite3.connect(f"file:{database}?mode=ro", uri=True)
    try:
        for query in ("Nicaragua", "inteligencia artificial", "sistema solar"):
            row = check.execute(
                "SELECT count(*) FROM articles_fts WHERE articles_fts MATCH ?",
                (query,),
            ).fetchone()
            if not row or row[0] <= 0:
                raise RuntimeError(f"Frozen brain self-test failed for query: {query}")
    finally:
        check.close()

    database_sha = sha256(database)
    internal_manifest = {
        "schema": 1,
        "version": VERSION,
        "database": DATABASE_NAME,
        "installed_bytes": installed_bytes,
        "installed_sha256": database_sha,
        "document_count": count,
        "source_dataset": DATASET,
        "source_config": DATASET_CONFIG,
        "source_shards": used_shards,
        "source_license": ["CC-BY-SA-3.0", "GFDL"],
        "training_method": "clean + chunk + SQLite FTS4 full-text retrieval index",
        "immutable": True,
    }
    (package / INTERNAL_MANIFEST).write_text(
        json.dumps(internal_manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (package / ATTRIBUTION_NAME).write_text(
        """Leo Frozen Brain v1\n\n"
        "General knowledge source: Wikimedia Wikipedia, Spanish configuration 20231101.es,\n"
        "distributed through the wikimedia/wikipedia dataset on Hugging Face.\n"
        "Wikipedia text is available under CC BY-SA 3.0 and GFDL; individual article URLs\n"
        "are stored in brain.sqlite for source attribution.\n\n"
        "Dataset: https://huggingface.co/datasets/wikimedia/wikipedia\n"
        "CC BY-SA 3.0: https://creativecommons.org/licenses/by-sa/3.0/\n"
        "GFDL: https://www.gnu.org/licenses/fdl-1.3.html\n"
        "Leo-specific seed entries are first-party project material.\n""",
        encoding="utf-8",
    )

    archive = output / ARCHIVE_NAME
    archive.unlink(missing_ok=True)
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as zipped:
        for name in (DATABASE_NAME, INTERNAL_MANIFEST, ATTRIBUTION_NAME):
            zipped.write(package / name, arcname=name)

    external = {
        "schema": 1,
        "version": VERSION,
        "archive_name": ARCHIVE_NAME,
        "archive_bytes": archive.stat().st_size,
        "archive_sha256": sha256(archive),
        "installed_bytes": installed_bytes,
        "installed_sha256": database_sha,
        "document_count": count,
        "source_dataset": DATASET,
        "source_config": DATASET_CONFIG,
        "source_license": ["CC-BY-SA-3.0", "GFDL"],
    }
    manifest = output / EXTERNAL_MANIFEST
    manifest.write_text(json.dumps(external, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(json.dumps(external, indent=2), flush=True)
    return archive, manifest


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="build/leo-brain-v1")
    parser.add_argument("--seed", default="brain/leo_seed_knowledge.jsonl")
    parser.add_argument("--target-bytes", type=int, default=DEFAULT_TARGET)
    parser.add_argument("--min-bytes", type=int, default=MIN_INSTALLED)
    parser.add_argument("--max-bytes", type=int, default=MAX_INSTALLED)
    args = parser.parse_args()
    if not (MIN_INSTALLED <= args.min_bytes <= args.target_bytes <= args.max_bytes <= 500_000_000):
        parser.error("expected 490MB <= min <= target <= max <= 500MB")
    return args


if __name__ == "__main__":
    try:
        build(parse_args())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise
