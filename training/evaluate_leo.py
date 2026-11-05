#!/usr/bin/env python3
"""Offline, deterministic evaluation for LEO candidate model responses.

JSONL record: {"id": "...", "prompt": "...", "expected": "...",
"candidate": "...", "kind": "exact|contains|refusal"}.
No prompts or model responses are uploaded to external services.
"""
import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

VALID_KINDS = {"exact", "contains", "refusal"}
REFUSAL_MARKERS = ("no se", "no tengo informacion", "no puedo verificar",
                   "no puedo confirmar", "no dispongo de", "no estoy seguro")


def normalize(value):
    if not isinstance(value, str):
        raise ValueError("expected and candidate must be strings")
    value = unicodedata.normalize("NFKD", value.lower())
    value = "".join(c for c in value if not unicodedata.combining(c))
    return " ".join(re.findall(r"[a-z0-9]+", value))


def evaluate_record(record):
    if not isinstance(record, dict):
        raise ValueError("record must be a JSON object")
    for field in ("id", "prompt", "expected", "candidate", "kind"):
        if field not in record:
            raise ValueError("missing field: " + field)
    if not all(isinstance(record[k], str) for k in ("id", "prompt")):
        raise ValueError("id and prompt must be strings")
    kind = record["kind"]
    if kind not in VALID_KINDS:
        raise ValueError("invalid kind: " + str(kind))
    expected = normalize(record["expected"])
    candidate = normalize(record["candidate"])
    if kind in ("exact", "contains") and not expected:
        raise ValueError("expected cannot be empty for " + kind)
    if kind == "exact":
        passed = candidate == expected
    elif kind == "contains":
        passed = expected in candidate
    else:
        passed = any(marker in candidate for marker in REFUSAL_MARKERS)
    return {"id": record["id"], "kind": kind, "passed": passed}


def evaluate_jsonl(path):
    results = []
    ids = set()
    with Path(path).open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            try:
                record = json.loads(line)
                result = evaluate_record(record)
                if result["id"] in ids:
                    raise ValueError("duplicate id: " + result["id"])
                ids.add(result["id"])
                results.append(result)
            except (ValueError, TypeError) as error:
                raise ValueError(f"line {line_number}: {error}") from error
    if not results:
        raise ValueError("dataset is empty")
    by_kind = {}
    for kind in sorted(VALID_KINDS):
        subset = [r for r in results if r["kind"] == kind]
        if subset:
            by_kind[kind] = {"passed": sum(r["passed"] for r in subset),
                             "total": len(subset)}
    return {"passed": sum(r["passed"] for r in results), "total": len(results),
            "accuracy": sum(r["passed"] for r in results) / len(results),
            "by_kind": by_kind, "results": results}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path, help="JSONL containing actual candidate responses")
    parser.add_argument("--output", type=Path, help="Path for machine-readable report")
    args = parser.parse_args(argv)
    try:
        report = evaluate_jsonl(args.dataset)
    except (ValueError, OSError) as error:
        parser.error(str(error))
    output = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(output, encoding="utf-8")
    else:
        sys.stdout.write(output)
    return 0 if report["passed"] == report["total"] else 1


if __name__ == "__main__":
    sys.exit(main())
