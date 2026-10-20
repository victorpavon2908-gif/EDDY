"""Standard-library tests for LEO's offline evaluation contract."""
import json
import tempfile
import unittest
from pathlib import Path
from training.evaluate_leo import evaluate_jsonl, evaluate_record, normalize


class LeoEvaluationTest(unittest.TestCase):
    def record(self, **overrides):
        value = {"id": "sample", "prompt": "¿Cuánto es 2+2?", "expected": "4",
                 "candidate": "4", "kind": "exact"}
        value.update(overrides)
        return value

    def test_exact_normalizes_accents_and_punctuation(self):
        self.assertTrue(evaluate_record(self.record(expected="Sí, claro.", candidate="Si claro"))["passed"])

    def test_exact_does_not_count_extra_words(self):
        self.assertFalse(evaluate_record(self.record(candidate="El resultado es 4"))["passed"])

    def test_contains_allows_longer_answer(self):
        self.assertTrue(evaluate_record(self.record(kind="contains", candidate="El resultado es 4."))["passed"])

    def test_contains_rejects_missing_answer(self):
        self.assertFalse(evaluate_record(self.record(kind="contains", candidate="No disponible"))["passed"])

    def test_refusal_requires_uncertainty_marker(self):
        self.assertTrue(evaluate_record(self.record(kind="refusal", expected="", candidate="No puedo verificar eso."))["passed"])
        self.assertFalse(evaluate_record(self.record(kind="refusal", expected="", candidate="Sin duda es cierto"))["passed"])

    def test_missing_fields_fail_closed(self):
        with self.assertRaises(ValueError):
            evaluate_record({"id": "x"})

    def test_empty_expectation_fails_closed(self):
        with self.assertRaises(ValueError):
            evaluate_record(self.record(expected=" "))

    def test_normalization(self):
        self.assertEqual(normalize("  ¿QUÉ?  "), "que")

    def test_jsonl_report_and_duplicate_ids(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.jsonl"
            path.write_text(json.dumps(self.record(), ensure_ascii=False) + "\n" +
                            json.dumps(self.record(id="second", candidate="5"), ensure_ascii=False) + "\n",
                            encoding="utf-8")
            report = evaluate_jsonl(path)
            self.assertEqual((report["passed"], report["total"]), (1, 2))
            self.assertEqual(report["accuracy"], 0.5)
            path.write_text(json.dumps(self.record()) + "\n" + json.dumps(self.record()) + "\n")
            with self.assertRaisesRegex(ValueError, "duplicate id"):
                evaluate_jsonl(path)

    def test_empty_file_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "empty.jsonl"
            path.write_text("")
            with self.assertRaisesRegex(ValueError, "empty"):
                evaluate_jsonl(path)


if __name__ == "__main__":
    unittest.main()
