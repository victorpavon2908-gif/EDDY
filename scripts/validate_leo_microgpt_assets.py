#!/usr/bin/env python3
"""Build-time integrity check for Leo's bundled MicroGPT checkpoint parts."""

from __future__ import annotations

import base64
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
EXPECTED_SHA256 = "d94590643699181d550d51bb2a621bf7ac2e6d3b928811703e78fbb879c8017f"
EXPECTED_BYTES = 109_722


def part_names() -> list[str]:
    names = [f"leo-microgpt-v1.bundle.b64.part{i:02d}" for i in range(1, 9)]
    names += [f"leo-microgpt-v1.bundle.b64.tail{i:03d}" for i in range(1, 11)]
    names += [f"leo-microgpt-v1.bundle.b64.pair{i:02d}" for i in range(6, 16)]
    names += [f"leo-microgpt-v1.bundle.b64.tail{i:03d}" for i in range(31, 37)]
    return names


def main() -> None:
    names = part_names()
    missing = [name for name in names if not (ASSETS / name).is_file()]
    if missing:
        raise SystemExit(f"Missing MicroGPT assets: {', '.join(missing)}")
    encoded = "".join((ASSETS / name).read_text(encoding="utf-8").strip() for name in names)
    payload = base64.b64decode(encoded, validate=True)
    actual = hashlib.sha256(payload).hexdigest()
    if len(payload) != EXPECTED_BYTES:
        raise SystemExit(f"MicroGPT size mismatch: {len(payload)} != {EXPECTED_BYTES}")
    if actual != EXPECTED_SHA256:
        raise SystemExit(f"MicroGPT SHA-256 mismatch: {actual} != {EXPECTED_SHA256}")
    print(f"MicroGPT OK: {len(payload)} bytes sha256={actual}")


if __name__ == "__main__":
    main()
