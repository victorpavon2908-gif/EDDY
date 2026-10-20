#!/usr/bin/env python3
"""LEO OpenAI-compatible local Python bridge for llama.cpp GGUF inference.

Starts on loopback ONLY; place behind an authenticated HTTPS reverse proxy for
Android's existing LeoInferenceClient. No model is downloaded or executed
until the operator explicitly supplies a local GGUF file.
"""
import argparse
import hmac
import json
import logging
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

MAX_BODY = 65536
MAX_PROMPT = 30000
MAX_REPLY = 48000
MAX_TOKENS = 4096
ROLES = {"system", "user", "assistant"}
LOG = logging.getLogger("leo.inference")


class ProtocolError(ValueError):
    pass


def validated_request(body, expected_model):
    if not isinstance(body, dict):
        raise ProtocolError("invalid JSON object")
    if body.get("model") != expected_model:
        raise ProtocolError("unknown model")
    if body.get("stream") is not False:
        raise ProtocolError("streaming is not supported")
    messages = body.get("messages")
    if not isinstance(messages, list) or not 1 <= len(messages) <= 8:
        raise ProtocolError("invalid message count")
    cleaned = []
    total = 0
    for message in messages:
        if not isinstance(message, dict) or set(message) != {"role", "content"}:
            raise ProtocolError("unexpected message fields")
        role, content = message["role"], message["content"]
        if role not in ROLES or not isinstance(content, str) or not content.strip():
            raise ProtocolError("invalid role or content")
        total += len(content)
        if total > MAX_PROMPT:
            raise ProtocolError("prompt too long")
        cleaned.append({"role": role, "content": content})
    if cleaned[0]["role"] != "system" or cleaned[-1]["role"] != "user" or any(
        m["role"] == "system" for m in cleaned[1:]
    ):
        raise ProtocolError("system and final user message required")
    max_tokens = body.get("max_tokens", 256)
    if type(max_tokens) is not int or not 1 <= max_tokens <= MAX_TOKENS:
        raise ProtocolError("invalid max_tokens")
    # Caller never gets to request tool execution, custom arbitrary decoding, or files.
    if set(body) - {"model", "stream", "messages", "max_tokens"}:
        raise ProtocolError("unsupported request fields")
    return cleaned, max_tokens


def output_from_model(result, model_name):
    """Accept only complete textual model output; never echo tool calls."""
    if not isinstance(result, dict):
        raise ProtocolError("invalid model output")
    choices = result.get("choices")
    if not isinstance(choices, list) or len(choices) != 1:
        raise ProtocolError("unexpected choices")
    choice = choices[0]
    message = choice.get("message", {})
    if (choice.get("finish_reason") != "stop"
            or not isinstance(message, dict)
            or set(message) - {"role", "content", "refusal"}
            or message.get("role") != "assistant"
            or not isinstance(message.get("content"), str)
            or not message["content"].strip()
            or len(message["content"]) > MAX_REPLY):
        raise ProtocolError("incomplete or non-textual model output")
    return {
        "id": "leo-local",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": model_name,
        "choices": [{"index": 0, "message": {"role": "assistant",
                     "content": message["content"].strip()}, "finish_reason": "stop"}],
        "usage": result.get("usage", {}),
    }


class LocalGGUF:
    """Loads llama-cpp-python lazily, so HTTP tests need no ML dependency."""
    def __init__(self, model_path, context=2048, threads=4):
        from llama_cpp import Llama
        if not Path(model_path).is_file() or Path(model_path).suffix.lower() != ".gguf":
            raise ValueError("provide an existing GGUF file")
        self.engine = Llama(model_path=str(model_path), n_ctx=context,
                            n_threads=threads, n_gpu_layers=0,
                            use_mmap=True, verbose=False)

    def generate(self, messages, max_tokens):
        return self.engine.create_chat_completion(
            messages=messages, max_tokens=max_tokens, temperature=0.2,
            stream=False
        )


class LeoServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, backend, model, api_key):
        super().__init__(address, LeoHandler)
        self.backend = backend
        self.model_name = model
        self.api_key = api_key
        self.inference_lock = threading.Lock()


class LeoHandler(BaseHTTPRequestHandler):
    server_version = "LEOInference/1"
    sys_version = ""

    def log_message(self, fmt, *args):
        # Never log user prompts, authorization tokens or model outputs.
        LOG.info("HTTP %s", self.path.split("?", 1)[0])

    def reply(self, code, payload):
        output = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(output)))
        self.end_headers()
        self.wfile.write(output)

    def do_GET(self):
        if self.path != "/health":
            self.reply(404, {"error": "not found"})
        else:
            self.reply(200, {"status": "ready"})

    def do_POST(self):
        if self.path != "/chat/completions":
            self.reply(404, {"error": "not found"})
            return
        key = self.server.api_key
        if key and not hmac.compare_digest(
            self.headers.get("Authorization", ""), "Bearer " + key
        ):
            self.reply(401, {"error": "unauthorized"})
            return
        length = self.headers.get("Content-Length", "")
        if not length.isdecimal() or not 0 < int(length) <= MAX_BODY:
            self.reply(413, {"error": "invalid body size"})
            return
        if not self.headers.get("Content-Type", "").lower().startswith("application/json"):
            self.reply(415, {"error": "JSON content type required"})
            return
        try:
            body = json.loads(self.rfile.read(int(length)).decode("utf-8"))
            messages, max_tokens = validated_request(body, self.server.model_name)
        except (UnicodeError, ValueError, json.JSONDecodeError) as error:
            self.reply(400, {"error": "invalid request"})
            return
        if not self.server.inference_lock.acquire(blocking=False):
            self.reply(503, {"error": "model busy"})
            return
        try:
            result = self.server.backend.generate(messages, max_tokens)
            payload = output_from_model(result, self.server.model_name)
        except Exception:
            LOG.exception("model inference failed")
            self.reply(502, {"error": "model unavailable or incomplete"})
        else:
            self.reply(200, payload)
        finally:
            self.server.inference_lock.release()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-file", type=Path, required=True)
    parser.add_argument("--model-id", default="leo-local")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--context", type=int, default=2048)
    parser.add_argument("--threads", type=int, default=min(4, os.cpu_count() or 2))
    parser.add_argument("--api-key-env", default="LEO_LOCAL_API_KEY")
    args = parser.parse_args(argv)
    if args.host not in ("127.0.0.1", "::1", "localhost"):
        parser.error("bind only to loopback; use HTTPS reverse proxy")
    if not 1 <= args.port <= 65535 or not 512 <= args.context <= 4096 or not 1 <= args.threads <= 16:
        parser.error("invalid port, context or thread count")
    if not args.model_id or len(args.model_id) > 160:
        parser.error("invalid model id")
    if not args.model_file.is_file() or args.model_file.suffix.lower() != ".gguf":
        parser.error("model must be a local GGUF file")
    key = os.environ.get(args.api_key_env, "")
    if key and (len(key) > 4096 or any(ord(c) < 33 or ord(c) > 126 for c in key)):
        parser.error("invalid API key environment value")
    logging.basicConfig(level=logging.WARNING)
    backend = LocalGGUF(args.model_file, context=args.context, threads=args.threads)
    server = LeoServer((args.host, args.port), backend, args.model_id, key)
    try:
        server.serve_forever(poll_interval=0.3)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
