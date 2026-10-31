"""Synthetic regressions for the G-185 real-feed provenance receipt."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "gtfs_feed_provenance.py"
TABLES = ("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")


class FeedProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.archive = self.folder / "germany.zip"
        with zipfile.ZipFile(self.archive, "w") as source:
            source.writestr("stops.txt", "stop_id\nS\n")
        self.report = self.folder / "verified.json"
        self.report.write_text(json.dumps({
            table: {"rows": index + 1, "header": ["id"]}
            for index, table in enumerate(TABLES)
        }), encoding="utf-8")
        self.receipt = self.folder / "receipt.json"

    def run_script(self, output=None):
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(self.archive), str(self.report),
             str(output or self.receipt)],
            capture_output=True, text=True, timeout=10, check=False,
        )

    def test_successful_receipt_binds_exact_archive_and_verifier_report(self):
        run = self.run_script()
        self.assertEqual(0, run.returncode, run.stderr)
        result = json.loads(self.receipt.read_text(encoding="utf-8"))
        self.assertEqual("GTFS_PREFLIGHT_SOURCE_BINDING_V1", result["schema"])
        self.assertEqual("STRUCTURAL_PREFLIGHT_ONLY", result["acceptance"])
        self.assertEqual(
            hashlib.sha256(self.archive.read_bytes()).hexdigest(),
            result["source_zip_sha256"],
        )
        self.assertEqual(self.archive.stat().st_size, result["source_zip_bytes"])
        self.assertEqual(
            hashlib.sha256(self.report.read_bytes()).hexdigest(),
            result["verifier_report_sha256"],
        )
        self.assertEqual({"stops.txt": 1, "routes.txt": 2,
                          "trips.txt": 3, "stop_times.txt": 4},
                         result["verified_table_rows"])
        self.assertEqual(0, self.run_script().returncode)
        self.assertEqual(result, json.loads(self.receipt.read_text(encoding="utf-8")))

    def test_bad_json_keeps_previous_evidence_untouched(self):
        self.receipt.write_bytes(b"OLD-VALID-RECEIPT")
        self.report.write_text("{bad", encoding="utf-8")
        run = self.run_script()
        self.assertNotEqual(0, run.returncode)
        self.assertNotIn("Traceback", run.stderr)
        self.assertEqual(b"OLD-VALID-RECEIPT", self.receipt.read_bytes())

    def test_missing_or_invalid_table_counts_fail_closed(self):
        for invalid in (
            {"stops.txt": {"rows": 0, "header": ["stop_id"]}},
            {"stops.txt": {"rows": True, "header": ["stop_id"]}},
            {"stops.txt": {"rows": 1, "header": "stop_id"}},
        ):
            with self.subTest(invalid=invalid):
                report = {table: {"rows": 1, "header": ["id"]} for table in TABLES}
                report.update(invalid)
                self.report.write_text(json.dumps(report), encoding="utf-8")
                self.receipt.unlink(missing_ok=True)
                run = self.run_script()
                self.assertNotEqual(0, run.returncode)
                self.assertFalse(self.receipt.exists())
                self.assertIn("structural preflight evidence", run.stderr)

    def test_never_replace_zip_or_verified_report_even_by_symlink(self):
        before_archive = self.archive.read_bytes()
        before_report = self.report.read_bytes()
        for output in (self.archive, self.report):
            with self.subTest(output=output):
                run = self.run_script(output)
                self.assertNotEqual(0, run.returncode)
                self.assertIn("must not replace", run.stderr)
        alias = self.folder / "report-alias.json"
        alias.symlink_to(self.report)
        self.assertNotEqual(0, self.run_script(alias).returncode)
        self.assertEqual(before_archive, self.archive.read_bytes())
        self.assertEqual(before_report, self.report.read_bytes())

    def test_oversize_report_is_rejected_before_hashing_and_no_receipt(self):
        self.report.write_bytes(b" " * (2 * 1024 * 1024 + 1))
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("size limit", result.stderr)
        self.assertFalse(self.receipt.exists())


if __name__ == "__main__":
    unittest.main()
