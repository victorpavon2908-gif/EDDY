#!/usr/bin/env python3
"""Benchmark real LEO chat-completions responses against a JSONL benchmark.

Usage: python -m training.benchmark_leo_model --dataset cases.jsonl
       --endpoint http://127.0.0.1:8765/chat/completions --output results.jsonl
Only localhost HTTP or trusted HTTPS endpoints are accepted. The benchmark
does not execute generated code or claim that accuracy = intelligence.
"""
import argparse
import json
import os
import statistics
import time
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, build_opener, HTTPRedirectHandler

from training.evaluate_leo import evaluate_jsonl, evaluate_record


class RejectRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def check_endpoint(value):
    parts = urlsplit(value)
    if (parts.scheme not in ("http", "https") or not parts.hostname
            or parts.username or parts.password or parts.query or parts.fragment
            or parts.path != "/chat/completions"):
        raise ValueError("expected complete /chat/completions endpoint")
    if parts.scheme == "http" and parts.hostname not in ("localhost", "127.0.0.1", "::1"):
        raise ValueError("remote inference requires HTTPS")
    try:
        _ = parts.port
    except ValueError as error:
        raise ValueError("invalid port") from error
    return value


def benchmark(dataset, endpoint, model, key="", timeout=45):
    check_endpoint(endpoint)
    if not model or len(model) > 160:
        raise ValueError("invalid model name")
    if not 1 <= timeout <= 120:
        raise ValueError("invalid timeout")
    results = []
    latencies = []
    errors = 0
    opener = build_opener(RejectRedirect())
    with Path(dataset).open(encoding="utf-8") as source:
        for line_number, line in enumerate(source, 1):
            if not line.strip():
                continue
            if len(results) >= 1000:
                raise ValueError("maximum of 1000 test cases")
            try:
                item = json.loads(line)
                if not isinstance(item, dict):
                    raise ValueError("expected JSON object")
                if not isinstance(item.get("prompt"), str) or not 0 < len(item["prompt"]) <= 16000:
                    raise ValueError("invalid prompt")
                record = {k: item[k] for k in ("id", "prompt", "expected", "kind")}
                record["candidate"] = ""
                evaluate_record(record)
            except (KeyError, ValueError, TypeError) as error:
                raise ValueError(f"line {line_number}: invalid benchmark record: {error}") from error
            payload = {
                "model": model, "stream": False,
                "messages": [
                    {"role": "system", "content": "Sos LEO. Responde en español de forma precisa. No inventés información."},
                    {"role": "user", "content": record["prompt"]},
                ],
            }
            headers = {"Content-Type": "application/json"}
            if key:
                headers["Authorization"] = "Bearer " + key
            request = Request(endpoint, data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
                              headers=headers, method="POST")
            start = time.monotonic()
            try:
                with opener.open(request, timeout=timeout) as response:
                    if response.status != 200:
                        raise ValueError("non-200 status")
                    raw = response.read(524289)
                if len(raw) > 524288:
                    raise ValueError("oversized response")
                data = json.loads(raw.decode("utf-8"))
                choice = data["choices"][0]
                if choice["finish_reason"] != "stop":
                    raise ValueError("incomplete reply")
                message = choice["message"]
                if "tool_calls" in message or "function_call" in message:
                    raise ValueError("unexpected tool call")
                answer = message["content"]
                if not isinstance(answer, str) or not answer.strip() or len(answer) > 48000:
                    raise ValueError("invalid answer")
                record["candidate"] = answer
            except Exception:
                # No secret credentials or response body in a report.
                errors += 1
                record["candidate"] = ""
                record["transport_error"] = True
            elapsed = round((time.monotonic() - start) * 1000)
            record["latency_ms"] = elapsed
            latencies.append(elapsed)
            results.append(record)
    if not results:
        raise ValueError("no benchmark cases")
    return results, {"requests": len(results), "errors": errors,
                     "median_latency_ms": statistics.median(latencies)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--model", default="leo-local")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--timeout", type=int, default=45)
    parser.add_argument("--api-key-env", default="LEO_LOCAL_API_KEY")
    args = parser.parse_args(argv)
    if args.output.resolve() == args.dataset.resolve():
        parser.error("output and dataset must differ")
    try:
        actual, stats = benchmark(args.dataset, args.endpoint, args.model,
                                  os.environ.get(args.api_key_env, ""), args.timeout)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            "".join(json.dumps(r, ensure_ascii=False) + "\n" for r in actual),
            encoding="utf-8",
        )
        report = evaluate_jsonl(args.output)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps({"evaluation": report, "transport": stats}, ensure_ascii=False, indent=2))
    return 0 if report["passed"] == report["total"] and stats["errors"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
