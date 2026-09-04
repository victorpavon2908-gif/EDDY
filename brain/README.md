# Leo Frozen Brain

Leo uses two separate local knowledge tiers:

- **Frozen brain:** up to 500,000,000 bytes, immutable after SHA-256 validation.
- **Adaptive brain:** up to 4,500,000,000 bytes for owner-specific memory, corrections, indexes and adaptive checkpoints.
- **Absolute Leo brain ceiling:** 5,000,000,000 bytes, while also preserving a device free-space reserve.

## Frozen brain v1

`tools/build_leo_frozen_brain.py` builds `brain.sqlite`, an Android-compatible SQLite FTS4 retrieval database. It starts with the first-party curriculum in `leo_seed_knowledge.jsonl`, then ingests cleaned Spanish Wikipedia articles from the fixed `wikimedia/wikipedia` `20231101.es` dataset until the real SQLite file reaches roughly 496 MB. No synthetic padding is used.

The builder stores article URLs for attribution, writes an internal manifest, runs representative retrieval checks, computes SHA-256 hashes, and packages the database as `leo-brain-v1.zip`.

The GitHub Actions workflow publishes two GitHub Release assets under tag `leo-brain-v1`:

- `leo-brain-v1.zip`
- `leo-brain-v1-manifest.json`

The Android app downloads the manifest first, resumes the archive download when interrupted, verifies both archive and installed database hashes, rejects packages outside the 490–500 MB installed range, extracts through a zip-slip-safe installer, and opens the database read-only.

## Source and license

General knowledge comes from Wikimedia Wikipedia in Spanish, via the `wikimedia/wikipedia` dataset on Hugging Face. Wikipedia text is distributed under CC BY-SA 3.0 and GFDL. The release package includes `ATTRIBUTION.txt`, and every Wikipedia document keeps its source article URL in the database.

Leo-specific seed entries are first-party project material.
