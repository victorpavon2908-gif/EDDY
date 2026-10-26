"""Ensure the retired companion cannot accidentally be packaged or deployed."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class AndroidOnlyTest(unittest.TestCase):
    def test_web_entrypoints_and_deployment_are_removed(self):
        for path in ('src', 'server', 'public', 'index.html', 'package.json', 'package-lock.json',
                     'server.ts', 'vite.config.ts', 'render.yaml', '.github/workflows/web-build.yml'):
            with self.subTest(path=path):
                self.assertFalse((ROOT / path).exists())

    def test_native_search_and_mobile_guide_remain(self):
        self.assertTrue((ROOT / 'app/src/main/java/com/niko/assistant/ai/LeoNativeWebSearch.kt').is_file())
        self.assertTrue((ROOT / 'app/src/main/assets/manual_usuario_leo.md').is_file())
