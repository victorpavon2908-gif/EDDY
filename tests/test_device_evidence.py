import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('device_evidence', Path(__file__).resolve().parents[1] / 'scripts/collect_leo_device_evidence.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EvidenceCollectorTest(unittest.TestCase):
    # These fixtures test the collector, never Android hardware.
    def test_capture_does_not_certify_physical_success(self):
        with tempfile.TemporaryDirectory() as root:
            out = Path(root) / 'evidence'
            def run(cmd, **kwargs):
                return subprocess.CompletedProcess(cmd, 0, b'observed fixture', b'')
            result = module.collect('adb', 'test', 'barge-in', out, 0, 1, runner=run)
            self.assertTrue(result['capture_complete'])
            self.assertEqual('PENDIENTE DE VALIDACIÓN FÍSICA', result['status'])
            self.assertEqual(10, len(result['records']))
            self.assertEqual(result['status'], json.loads((out / 'manifest.json').read_text())['status'])
            self.assertTrue(all(len(r['sha256']) == 64 for r in result['records']))

    def test_timeout_preserves_error_evidence(self):
        with tempfile.TemporaryDirectory() as root:
            def timeout(cmd, **kwargs):
                raise subprocess.TimeoutExpired(cmd, 30)
            result = module.capture('adb', 'device', ['shell', 'dumpsys', 'battery'], Path(root) / 'error.txt', timeout)
            self.assertIsNone(result['exit_code'])
            self.assertEqual('TimeoutExpired', result['problem'])

    def test_never_overwrites_evidence(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(FileExistsError):
                module.collect('adb', 'test', 'idle', Path(root), 0, 1)

    def test_permission_denial_is_not_success(self):
        with tempfile.TemporaryDirectory() as root:
            def denied(cmd, **kwargs):
                return subprocess.CompletedProcess(cmd, 1, b'', b'permission denied')
            result = module.collect('adb', 'test', 'idle', Path(root) / 'new', 0, 1, denied)
            self.assertFalse(result['all_commands_succeeded'])
            self.assertEqual('PENDIENTE DE VALIDACIÓN FÍSICA', result['status'])
