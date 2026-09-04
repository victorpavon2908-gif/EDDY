#!/usr/bin/env python3
"""Build-time integrity check for Leo's bundled MicroGPT checkpoint parts."""

from __future__ import annotations

import base64
import hashlib
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
EXPECTED_SHA256 = "eb97f32cdde73e62c19ab0159de10503b687bbf0dce67a18d756e34fc075a851"
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

    # Match java.util.Base64.getDecoder(): no whitespace normalization is allowed here.
    encoded = "".join((ASSETS / name).read_text(encoding="utf-8") for name in names)
    payload = base64.b64decode(encoded, validate=True)
    actual = hashlib.sha256(payload).hexdigest()
    if len(payload) != EXPECTED_BYTES:
        raise SystemExit(f"MicroGPT size mismatch: {len(payload)} != {EXPECTED_BYTES}")
    if actual != EXPECTED_SHA256:
        raise SystemExit(f"MicroGPT SHA-256 mismatch: {actual} != {EXPECTED_SHA256}")

    if payload[:8] != b"LEOMGQ81":
        raise SystemExit(f"MicroGPT magic mismatch: {payload[:8]!r}")
    version, vocab, context, dim, heads, ff, layers = struct.unpack_from("<7i", payload, 8)
    if version != 1:
        raise SystemExit(f"Unexpected MicroGPT version: {version}")
    if not (32 <= vocab <= 4096 and 16 <= context <= 128 and 16 <= dim <= 256):
        raise SystemExit(f"Invalid MicroGPT dimensions: vocab={vocab} context={context} dim={dim}")
    if not (1 <= heads <= 16 and dim % heads == 0 and 1 <= layers <= 8 and ff >= dim):
        raise SystemExit(f"Invalid MicroGPT architecture: heads={heads} ff={ff} layers={layers}")

    print(
        f"MicroGPT OK: {len(payload)} bytes sha256={actual} "
        f"vocab={vocab} context={context} dim={dim} heads={heads} ff={ff} layers={layers}"
    )


if __name__ == "__main__":
    main()
