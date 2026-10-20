"""Behavioral HTTP contract tests; model weights and llama.cpp not required."""
import json
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from ai.inference.leo_local_server import (
    LeoServer, ProtocolError, output_from_model, validated_request,
)


def fixture_request():
    return {
        "model": "leo-local", "stream": False,
        "messages": [{"role": "system", "content": "Sos LEO."},
                     {"role": "user", "content": "¿Cómo estás?"}],
    }


class FakeModel:
    def __init__(self):
        self.calls = []

    def generate(self, messages, max_tokens):
        self.calls.append((messages, max_tokens))
        return {
            "choices": [{"message": {"role": "assistant", "content": "Estoy listo."},
                         "finish_reason": "stop"}],
            "usage": {"total_tokens": 16},
        }


class TestProtocol(unittest.TestCase):
    def test_accepts_actual_android_payload_shape(self):
        messages, max_tokens = validated_request(fixture_request(), "leo-local")
        self.assertEqual(max_tokens, 256)
        self.assertEqual(messages[-1]["role"], "user")

    def test_rejects_mismatched_model(self):
        with self.assertRaises(ProtocolError):
            validated_request(fixture_request(), "another")

    def test_rejects_tools_and_streaming(self):
        for patch in ({"tools": []}, {"stream": True}, {"tool_choice": "auto"}):
            request = {**fixture_request(), **patch}
            with self.subTest(patch=patch), self.assertRaises(ProtocolError):
                validated_request(request, "leo-local")

    def test_rejects_payload_too_large(self):
        req = fixture_request()
        req["messages"][-1]["content"] = "a" * 16001
        with self.assertRaises(ProtocolError):
            validated_request(req, "leo-local")

    def test_rejects_invalid_max_tokens(self):
        for invalid in (0, -1, 4097, True, "123"):
            req = {**fixture_request(), "max_tokens": invalid}
            with self.subTest(invalid=invalid), self.assertRaises(ProtocolError):
                validated_request(req, "leo-local")

    def test_rejects_tool_call_response(self):
        response = FakeModel().generate([], 1)
        response["choices"][0]["message"]["tool_calls"] = [{"function": {"name": "shell"}}]
        with self.assertRaises(ProtocolError):
            output_from_model(response, "leo-local")

    def test_rejects_incomplete_generation(self):
        response = FakeModel().generate([], 1)
        response["choices"][0]["finish_reason"] = "length"
        with self.assertRaises(ProtocolError):
            output_from_model(response, "leo-local")


class TestHttp(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from ai.inference.leo_local_server import LeoHandler
        cls.model = FakeModel()
        cls.server = LeoServer(("127.0.0.1", 0), cls.model, "leo-local", "test-secret")
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = f"http://127.0.0.1:{cls.server.server_port}"

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=3)

    def post(self, payload, key="test-secret", raw=None, ctype="application/json"):
        data = json.dumps(payload).encode("utf-8") if raw is None else raw
        headers = {"Content-Type": ctype}
        if key:
            headers["Authorization"] = "Bearer " + key
        request = Request(self.url + "/chat/completions", data=data,
                          headers=headers, method="POST")
        try:
            with urlopen(request, timeout=3) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, json.loads(error.read())

    def test_success(self):
        status, payload = self.post(fixture_request())
        self.assertEqual(status, 200)
        self.assertEqual(payload["choices"][0]["finish_reason"], "stop")
        self.assertEqual(payload["choices"][0]["message"]["content"], "Estoy listo.")

    def test_auth_fails_before_model_inference(self):
        count = len(self.model.calls)
        status, _ = self.post(fixture_request(), key="wrong")
        self.assertEqual(status, 401)
        self.assertEqual(len(self.model.calls), count)

    def test_invalid_json(self):
        status, _ = self.post({}, raw=b"{not json")
        self.assertEqual(status, 400)

    def test_no_tool_support(self):
        status, _ = self.post({**fixture_request(), "tools": [{"type": "function"}]})
        self.assertEqual(status, 400)

    def test_busy_immediate_fallback(self):
        self.server.inference_lock.acquire()
        try:
            status, _ = self.post(fixture_request())
            self.assertEqual(status, 503)
        finally:
            self.server.inference_lock.release()

    def test_health(self):
        with urlopen(self.url + "/health", timeout=3) as response:
            self.assertEqual(json.load(response), {"status": "ready"})


if __name__ == "__main__":
    unittest.main()
