#!/usr/bin/env python3
"""Validate LEO model package metadata and file integrity before distribution.

This development-side tool never executes models, downloads code, or installs APKs.
SHA-256 protects against corruption only; use authenticated, signed manifests for
production distribution.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path, PurePosixPath

_REQUIRED = {"schema", "model_id", "version", "license", "format", "bytes", "sha256", "file"}
_ALLOWED_FORMATS = {"gguf", "onnx"}
_SHA256 = re.compile(r"[0-9a-f]{64}\Z")
_SAFE_TOKEN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z")


def validate_manifest(data):
    if not isinstance(data, dict):
        raise ValueError("manifest must be an object")
    missing = _REQUIRED - data.keys()
    if missing:
        raise ValueError("missing required fields: " + ", ".join(sorted(missing)))
    if data["schema"] != 1 or isinstance(data["schema"], bool):
        raise ValueError("unsupported schema")
    for key in ("model_id", "version"):
        if not isinstance(data[key], str) or not _SAFE_TOKEN.fullmatch(data[key]):
            raise ValueError("invalid " + key)
    if not isinstance(data["license"], str) or not data["license"].strip():
        raise ValueError("license must be explicitly declared")
    if data["format"] not in _ALLOWED_FORMATS:
        raise ValueError("unsupported model format")
    if type(data["bytes"]) is not int or not 0 < data["bytes"] <= 2_147_483_648:
        raise ValueError("invalid model size (maximum 2 GiB)")
    if not isinstance(data["sha256"], str) or not _SHA256.fullmatch(data["sha256"]):
        raise ValueError("invalid sha256")
    filename = data["file"]
    if not isinstance(filename, str):
        raise ValueError("file must be relative")
    path = PurePosixPath(filename)
    if (not filename or path.is_absolute() or len(path.parts) != 1
            or path.name != filename or filename in (".", "..")
            or "\\" in filename or ":" in filename):
        raise ValueError("file must be a simple filename without directories")
    extension = ".gguf" if data["format"] == "gguf" else ".onnx"
    if not filename.lower().endswith(extension):
        raise ValueError("file extension does not match model format")
    return data


def verify_package(manifest_path, model_dir):
    manifest_path = Path(manifest_path)
    model_dir = Path(model_dir)
    data = validate_manifest(json.loads(manifest_path.read_text(encoding="utf-8")))
    # Reject symlinks: do not allow model packages to read arbitrary outside files.
    if model_dir.is_symlink() or not model_dir.is_dir():
        raise ValueError("model directory must be a real directory")
    path = model_dir / data["file"]
    if path.is_symlink() or not path.is_file():
        raise ValueError("model file must be a real regular file")
    if path.stat().st_size != data["bytes"]:
        raise ValueError("model file size mismatch")
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    if digest.hexdigest() != data["sha256"]:
        raise ValueError("model file sha256 mismatch")
    return {"model_id": data["model_id"], "version": data["version"],
            "license": data["license"], "format": data["format"],
            "bytes": data["bytes"], "sha256": digest.hexdigest(), "verified": True,
            "signature_verified": False}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("model_directory", type=Path)
    args = parser.parse_args(argv)
    try:
        result = verify_package(args.manifest, args.model_directory)
    except (ValueError, OSError, json.JSONDecodeError) as error:
        parser.error(str(error))
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
