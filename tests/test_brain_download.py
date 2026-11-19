"""Exercise interrupted corpus downloads without contacting a dataset host."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from tools.build_leo_frozen_brain import download


class BrainDownloadTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.file = Path(folder.name) / "shard"
        self.part = self.file.with_suffix(".part")
        self.part.write_bytes(b"abc")
        self.client = MagicMock()
        modules = patch.dict(sys.modules, requests=self.client)
        modules.start()
        self.addCleanup(modules.stop)

    def response(self, status, data=b"", **headers):
        response = MagicMock(status_code=status, headers=headers)
        response.__enter__.return_value = response
        response.iter_content.return_value = [data]
        return response

    def run_download(self, *responses):
        self.client.get.side_effect = responses
        download("https://example.invalid/shard", self.file)

    def test_server_error_preserves_progress(self):
        error = self.response(503)
        error.raise_for_status.side_effect = OSError("unavailable")
        with self.assertRaises(OSError):
            self.run_download(error)
        self.assertEqual(self.part.read_bytes(), b"abc")

    def test_resume_validates_offset(self):
        with self.assertRaises(ValueError):
            self.run_download(self.response(206, b"bad", **{"Content-Range": "bytes 0-2/6"}))
        self.assertEqual(self.part.read_bytes(), b"abc")
        self.assertFalse(self.file.exists())

    def test_short_response_can_be_resumed(self):
        with self.assertRaises(OSError):
            self.run_download(self.response(206, b"de", **{"Content-Range": "bytes 3-5/6"}))
        self.assertFalse(self.file.exists())
        self.run_download(self.response(206, b"f", **{"Content-Range": "bytes 5-5/6"}))
        self.assertEqual(self.client.get.call_args.kwargs["headers"]["Range"], "bytes=5-")
        self.assertEqual(self.file.read_bytes(), b"abcdef")

    def test_ignored_range_uses_full_response(self):
        self.run_download(self.response(200, b"new data"))
        self.assertEqual(self.file.read_bytes(), b"new data")
        self.assertEqual(self.client.get.call_count, 1)

    def test_unsatisfiable_range_retries_without_range(self):
        self.run_download(self.response(416), self.response(200, b"new data"))
        self.assertNotIn("Range", self.client.get.call_args.kwargs["headers"])
        self.assertEqual(self.file.read_bytes(), b"new data")
