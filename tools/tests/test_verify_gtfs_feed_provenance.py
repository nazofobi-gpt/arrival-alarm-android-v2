"""Synthetic independent verification of G-185 structural evidence receipts."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


TOOL_DIR = Path(__file__).resolve().parents[1]
VERIFY = TOOL_DIR / "verify_gtfs_feed_provenance.py"
TABLES = ("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")


class VerifyGtfsFeedProvenanceTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.folder = Path(tmp.name)
        self.archive = self.folder / "feed.zip"
        with zipfile.ZipFile(self.archive, "w") as output:
            output.writestr("stops.txt", "stop_id,stop_name\nS,Example\n")
        self.report = self.folder / "structural.json"
        self.report.write_text(json.dumps({
            table: {"rows": i + 1, "header": ["id"]}
            for i, table in enumerate(TABLES)
        }), encoding="utf-8")
        self.receipt = self.folder / "source-binding.json"
        self.write_receipt()

    def write_receipt(self):
        counts = {table: i + 1 for i, table in enumerate(TABLES)}
        receipt = {
            "schema": "GTFS_PREFLIGHT_SOURCE_BINDING_V1",
            "acceptance": "STRUCTURAL_PREFLIGHT_ONLY",
            "source_zip_sha256": hashlib.sha256(self.archive.read_bytes()).hexdigest(),
            "source_zip_bytes": self.archive.stat().st_size,
            "verifier_report_sha256": hashlib.sha256(self.report.read_bytes()).hexdigest(),
            "verified_table_rows": counts,
        }
        self.receipt.write_text(json.dumps(receipt), encoding="utf-8")

    def invoke(self, optimized=False):
        return subprocess.run(
            [sys.executable, *(["-O"] if optimized else []), str(VERIFY),
             str(self.archive), str(self.report), str(self.receipt)],
            capture_output=True, text=True, timeout=10, check=False,
        )

    def assert_rejected(self, expected):
        for optimized in (False, True):
            with self.subTest(optimized=optimized):
                outcome = self.invoke(optimized)
                self.assertEqual(1, outcome.returncode, outcome.stderr)
                self.assertIn(expected, outcome.stderr)
                self.assertNotIn("Traceback", outcome.stderr)

    def test_valid_binding_passes_under_python_and_optimized_python(self):
        for optimized in (False, True):
            with self.subTest(optimized=optimized):
                outcome = self.invoke(optimized)
                self.assertEqual(0, outcome.returncode, outcome.stderr)
                self.assertIn("structural evidence binding only", outcome.stdout)

    def test_changed_zip_same_report_is_rejected(self):
        with self.archive.open("ab") as output:
            output.write(b"tampered")
        self.assert_rejected("ZIP checksum or size mismatch")

    def test_changed_report_is_rejected_even_when_counts_still_match(self):
        self.report.write_bytes(self.report.read_bytes() + b" ")
        self.assert_rejected("structural report checksum mismatch")

    def test_invalid_receipt_schema_is_rejected(self):
        data = json.loads(self.receipt.read_text(encoding="utf-8"))
        data["acceptance"] = "FULL_ANDROID_ACCEPTANCE"
        self.receipt.write_text(json.dumps(data), encoding="utf-8")
        self.assert_rejected("schema or acceptance mismatch")

    def test_extra_receipt_field_is_rejected(self):
        data = json.loads(self.receipt.read_text(encoding="utf-8"))
        data["physical_device_pass"] = True
        self.receipt.write_text(json.dumps(data), encoding="utf-8")
        self.assert_rejected("fields do not match schema")

    def test_wrong_counts_rejected_before_source_hash(self):
        data = json.loads(self.receipt.read_text(encoding="utf-8"))
        data["verified_table_rows"]["routes.txt"] = 999
        self.receipt.write_text(json.dumps(data), encoding="utf-8")
        self.assert_rejected("table counts differ")

    def test_invalid_boolean_row_count_rejected(self):
        report = json.loads(self.report.read_text(encoding="utf-8"))
        report["stops.txt"]["rows"] = True
        self.report.write_text(json.dumps(report), encoding="utf-8")
        self.assert_rejected("invalid structural evidence")

    def test_oversized_or_malformed_receipt_is_rejected(self):
        self.receipt.write_bytes(b" " * (2 * 1024 * 1024 + 1))
        self.assert_rejected("size limit")
        self.receipt.write_text("{not-json", encoding="utf-8")
        self.assert_rejected("Expecting property name")

    def test_duplicate_receipt_schema_key_is_rejected(self):
        text = self.receipt.read_text(encoding="utf-8")
        self.assertIn('"schema":', text)
        self.receipt.write_text(
            text.replace('"schema":', '"schema": "UNTRUSTED", "schema":', 1),
            encoding="utf-8",
        )
        self.assert_rejected("duplicate JSON evidence key: schema")

    def test_duplicate_report_table_key_is_rejected(self):
        text = self.report.read_text(encoding="utf-8")
        self.assertIn('"stops.txt":', text)
        self.report.write_text(
            text.replace(
                '"stops.txt":',
                '"stops.txt": {"rows": 999, "header": ["id"]}, "stops.txt":',
                1,
            ),
            encoding="utf-8",
        )
        self.assert_rejected("duplicate JSON evidence key: stops.txt")

    def test_duplicate_nested_report_rows_key_is_rejected(self):
        text = self.report.read_text(encoding="utf-8")
        self.assertIn('"rows": 1', text)
        self.report.write_text(
            text.replace('"rows": 1', '"rows": 999, "rows": 1', 1),
            encoding="utf-8",
        )
        self.assert_rejected("duplicate JSON evidence key: rows")

    def test_duplicate_nested_receipt_table_count_key_is_rejected(self):
        text = self.receipt.read_text(encoding="utf-8")
        self.assertIn('"routes.txt": 2', text)
        self.receipt.write_text(
            text.replace(
                '"routes.txt": 2', '"routes.txt": 999, "routes.txt": 2', 1,
            ),
            encoding="utf-8",
        )
        self.assert_rejected("duplicate JSON evidence key: routes.txt")

    def test_missing_archive_is_rejected_without_traceback(self):
        self.archive.unlink()
        for optimized in (False, True):
            with self.subTest(optimized=optimized):
                outcome = self.invoke(optimized)
                self.assertEqual(1, outcome.returncode)
                self.assertNotIn("Traceback", outcome.stderr)


if __name__ == "__main__":
    unittest.main()
