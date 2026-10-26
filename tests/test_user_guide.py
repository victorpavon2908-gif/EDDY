"""Prevent the shipped offline guide from diverging from the published manual."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]

class UserGuideTest(unittest.TestCase):
    def test_bundled_manual_matches_published_guide(self):
        manual = (ROOT / 'docs/MANUAL_USUARIO_LEO.md').read_text()
        self.assertEqual(manual, (ROOT / 'app/src/main/assets/manual_usuario_leo.md').read_text())
        for topic in ('Primer inicio', 'Internet', 'Enciclopedia offline', 'Herramientas', 'privacidad', 'Si algo no funciona'):
            self.assertIn(topic, manual)
