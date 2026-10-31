"""Tests for atomic, fail-closed verified report writes."""

import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from atomic_json_report import write_atomic_json_report


class AtomicJsonReportTest(unittest.TestCase):
    def test_complete_unicode_json_replaces_existing_report(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            path.write_text('{"old": true}', encoding="utf-8")
            payload = {"stops.txt": {"rows": 500000}, "name": "München"}
            write_atomic_json_report(path, payload)
            self.assertEqual(payload, json.loads(path.read_text(encoding="utf-8")))
            self.assertEqual([path.name], [p.name for p in path.parent.iterdir()])

    def test_failed_replace_retains_existing_report_and_cleans_staging(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            original = b'{"verified": "old"}\n'
            path.write_bytes(original)
            with mock.patch("atomic_json_report.os.replace", side_effect=OSError("simulated failure")):
                with self.assertRaisesRegex(OSError, "simulated failure"):
                    write_atomic_json_report(path, {"verified": "new"})
            self.assertEqual(original, path.read_bytes())
            self.assertEqual([path.name], [p.name for p in path.parent.iterdir()])

    def test_unserializable_payload_does_not_damage_prior_report(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            original = b'{"verified": "old"}\n'
            path.write_bytes(original)
            with self.assertRaises(TypeError):
                write_atomic_json_report(path, {"bad": object()})
            self.assertEqual(original, path.read_bytes())
            self.assertEqual([path.name], [p.name for p in path.parent.iterdir()])

    def test_no_existing_report_keeps_directory_clean_after_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "new.json"
            with mock.patch("atomic_json_report.os.replace", side_effect=OSError("simulated")):
                with self.assertRaises(OSError):
                    write_atomic_json_report(path, {"stops.txt": {"rows": 500000}})
            self.assertEqual([], list(Path(directory).iterdir()))


    def test_nonfinite_numbers_rejected_without_replacing_verified_report(self):
        for value in (float("nan"), float("inf"), float("-inf")):
            with self.subTest(value=str(value)):
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / "result.json"
                    original = b'{"verified": "old"}\n'
                    path.write_bytes(original)
                    with self.assertRaisesRegex(ValueError, "Out of range float values"):
                        write_atomic_json_report(path, {"measurements": {"sample": value}})
                    self.assertEqual(original, path.read_bytes())
                    self.assertEqual([path.name], [p.name for p in path.parent.iterdir()])

    def test_nonfinite_numbers_rejected_without_creating_new_report(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "result.json"
            with self.assertRaisesRegex(ValueError, "Out of range float values"):
                write_atomic_json_report(path, {"value": float("nan")})
            self.assertEqual([], list(Path(directory).iterdir()))


if __name__ == "__main__":
    unittest.main()
