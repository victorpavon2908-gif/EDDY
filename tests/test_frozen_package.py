"""Build a small offline package with the same SQLite, ZIP and manifest code as production."""
import argparse
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from tools import build_leo_frozen_brain as brain


class FrozenPackageTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.output = Path(folder.name)
        seed = self.output / "seed.jsonl"
        article = {
            "title": "Nicaragua",
            "url": "leo://seed/prueba",
            "text": ("inteligencia artificial, sistema solar, gallo pinto, depurar error, "
                     "promedio eficiencia y actualizar offline"),
        }
        seed.write_text(json.dumps(article), encoding="utf-8")
        self.args = argparse.Namespace(
            seed=seed,
            output_dir=self.output, target_bytes=1, min_bytes=1, max_bytes=2_000_000,
        )

    def test_searchable_short_tail_is_preserved(self):
        pieces = list(brain.chunks("x" * 11_500 + "FINAL"))
        self.assertTrue(pieces[-1].endswith("FINAL"))

    def test_package_in_directory_with_uri_characters(self):
        self.args.output_dir = self.output / "LEO #1"
        archive, manifest = brain.build(self.args)
        self.assertEqual(json.loads(manifest.read_text())["archive_sha256"], brain.sha256(archive))
        with zipfile.ZipFile(archive) as package:
            self.assertIsNone(package.testzip())
            self.assertNotIn(b'"', package.read(brain.ATTRIBUTION_NAME))

    def test_failed_compression_preserves_previous_release(self):
        archive = self.output / brain.ARCHIVE_NAME
        manifest = self.output / brain.EXTERNAL_MANIFEST
        archive.write_bytes(b"previous ZIP")
        manifest.write_bytes(b"previous manifest")
        with patch.object(zipfile.ZipFile, "write", side_effect=OSError("disk full")):
            with self.assertRaisesRegex(OSError, "disk full"):
                brain.build(self.args)
        self.assertEqual(archive.read_bytes(), b"previous ZIP")
        self.assertEqual(manifest.read_bytes(), b"previous manifest")
