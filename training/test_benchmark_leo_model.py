"""Real HTTP benchmark orchestration with a fake model (not model quality tests)."""
import json
import tempfile
import threading
import unittest
from pathlib import Path

from ai.inference.leo_local_server import LeoServer
from training.benchmark_leo_model import benchmark, check_endpoint
from training.evaluate_leo import evaluate_jsonl


class FakeModel:
    def __init__(self):
        self.finished = "stop"

    def generate(self, messages, max_tokens):
        return {"choices": [{"finish_reason": self.finished,
                             "message": {"role": "assistant", "content": "Estoy listo."}}]}


class BenchmarkContractTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.dataset = Path(self.directory.name) / "input.jsonl"
        record = {"id": "greeting", "prompt": "¿Cómo estás?", "expected": "Estoy listo.",
                  "kind": "exact"}
        self.dataset.write_text(json.dumps(record, ensure_ascii=False) + "\n", encoding="utf-8")
        self.backend = FakeModel()
        self.server = LeoServer(("127.0.0.1", 0), self.backend, "leo-local", "test-secret")
        thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(thread.join, 3)
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.server.shutdown)
        self.endpoint = f"http://127.0.0.1:{self.server.server_port}/chat/completions"

    def test_uses_real_http_and_scores_actual_returned_answer(self):
        records, stats = benchmark(self.dataset, self.endpoint, "leo-local", "test-secret")
        self.assertEqual(stats["requests"], 1)
        self.assertEqual(stats["errors"], 0)
        self.assertEqual(records[0]["candidate"], "Estoy listo.")
        output = Path(self.directory.name) / "output.jsonl"
        output.write_text(json.dumps(records[0], ensure_ascii=False) + "\n", encoding="utf-8")
        self.assertEqual(evaluate_jsonl(output)["passed"], 1)

    def test_truncated_reply_is_failure_not_fake_success(self):
        self.backend.finished = "length"
        records, stats = benchmark(self.dataset, self.endpoint, "leo-local", "test-secret")
        self.assertEqual(stats["errors"], 1)
        self.assertEqual(records[0]["candidate"], "")
        self.assertTrue(records[0]["transport_error"])

    def test_rejects_plaintext_remote_host(self):
        with self.assertRaisesRegex(ValueError, "HTTPS"):
            check_endpoint("http://example.com/chat/completions")

    def test_rejects_query_credentials(self):
        with self.assertRaises(ValueError):
            check_endpoint("https://x:y@example.com/chat/completions?key=x")

    def test_invalid_case_fails_closed(self):
        self.dataset.write_text('{"id":"missing"}\n', encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "invalid benchmark record"):
            benchmark(self.dataset, self.endpoint, "leo-local", "test-secret")


if __name__ == "__main__":
    unittest.main()
