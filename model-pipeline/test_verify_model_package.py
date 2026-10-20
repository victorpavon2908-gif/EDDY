"""Contract tests: no network access or third-party dependencies required."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location

SOURCE = Path(__file__).with_name("verify_model_package.py")
spec = spec_from_file_location("leo_package", SOURCE)
module = module_from_spec(spec)
spec.loader.exec_module(module)


class PackageVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.model = self.directory / "leo.gguf"
        self.model.write_bytes(b"not a model, only a test fixture")
        data = self.model.read_bytes()
        self.manifest = {
            "schema": 1, "model_id": "LEO-test", "version": "0.0.1",
            "license": "test-fixture-only", "format": "gguf",
            "file": "leo.gguf", "bytes": len(data),
            "sha256": hashlib.sha256(data).hexdigest()
        }
        self.manifest_path = self.directory / "manifest.json"
        self.save()

    def save(self):
        self.manifest_path.write_text(json.dumps(self.manifest), encoding="utf-8")

    def test_valid_fixture(self):
        result = module.verify_package(self.manifest_path, self.directory)
        self.assertTrue(result["verified"])
        self.assertFalse(result["signature_verified"])

    def test_tampered_model(self):
        self.model.write_bytes(b"x" * self.manifest["bytes"])
        with self.assertRaisesRegex(ValueError, "sha256"):
            module.verify_package(self.manifest_path, self.directory)

    def test_size_mismatch(self):
        self.model.write_bytes(b"short")
        with self.assertRaisesRegex(ValueError, "size mismatch"):
            module.verify_package(self.manifest_path, self.directory)

    def test_traversal_rejected(self):
        self.manifest["file"] = "../leo.gguf"
        self.save()
        with self.assertRaisesRegex(ValueError, "simple filename"):
            module.verify_package(self.manifest_path, self.directory)

    def test_missing_license_rejected(self):
        self.manifest["license"] = ""
        self.save()
        with self.assertRaisesRegex(ValueError, "license"):
            module.verify_package(self.manifest_path, self.directory)

    def test_invalid_digest_rejected(self):
        self.manifest["sha256"] = "not-a-hash"
        self.save()
        with self.assertRaisesRegex(ValueError, "sha256"):
            module.verify_package(self.manifest_path, self.directory)

    def test_wrong_format_rejected(self):
        self.manifest["format"] = "pickle"
        self.save()
        with self.assertRaisesRegex(ValueError, "format"):
            module.verify_package(self.manifest_path, self.directory)

    def test_size_cap_rejected(self):
        self.manifest["bytes"] = 2_147_483_649
        with self.assertRaisesRegex(ValueError, "maximum"):
            module.validate_manifest(self.manifest)

    def test_symlink_rejected(self):
        outside = self.directory / "source"
        outside.write_bytes(self.model.read_bytes())
        self.model.unlink()
        try:
            self.model.symlink_to(outside)
        except (OSError, NotImplementedError):
            self.skipTest("symlink unavailable")
        with self.assertRaisesRegex(ValueError, "regular"):
            module.verify_package(self.manifest_path, self.directory)


if __name__ == "__main__":
    unittest.main()
