"""Regression checks for ambiguous GTFS archive member selection."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "verify_germany_full_gtfs.py"
REQUIRED = ("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")


class GtfsArchiveMemberTest(unittest.TestCase):
    def run_archive(
        self, entries: tuple[str, ...], overrides: dict[str, bytes] | None = None,
        optimized: bool = False,
    ) -> tuple[subprocess.CompletedProcess[str], Path]:
        with tempfile.TemporaryDirectory() as dirname:
            archive = Path(dirname) / "feed.zip"
            report = Path(dirname) / "report.json"
            with zipfile.ZipFile(archive, "w") as zf:
                for member in entries:
                    zf.writestr(member, (overrides or {}).get(member, "id,name\n1,Example\n"))
            result = subprocess.run(
                [sys.executable, *(["-O"] if optimized else []), str(SCRIPT), str(archive), str(report)],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            self.assertFalse(report.exists(), "Unverified feed must not create a success report")
            return result, report

    def test_duplicate_required_table_in_nested_directory_fails_closed(self):
        result, _ = self.run_archive(REQUIRED + ("feed/STOPS.TXT",))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("duplicate GTFS table stops.txt", result.stderr)
        self.assertIn("feed/STOPS.TXT", result.stderr)

    def test_duplicate_optional_table_case_alias_fails_closed(self):
        result, _ = self.run_archive(REQUIRED + ("feed_info.txt", "nested/FEED_INFO.TXT"))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("duplicate GTFS table feed_info.txt", result.stderr)

    def test_missing_required_table_remains_a_specific_failure(self):
        result, _ = self.run_archive(("stops.txt", "routes.txt", "trips.txt"))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing required GTFS files", result.stderr)
        self.assertIn("stop_times.txt", result.stderr)

    def test_invalid_utf8_in_required_table_reports_table_without_traceback(self):
        malformed = b"stop_id,stop_name,stop_lat,stop_lon\n1,\xff,52.0,8.0\n"
        result, _ = self.run_archive(REQUIRED, {"stops.txt": malformed})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid UTF-8 in GTFS table stops.txt", result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_invalid_utf8_in_optional_table_reports_table_without_traceback(self):
        malformed = b"feed_publisher_name,feed_publisher_url\n\xff,https://example.org\n"
        result, _ = self.run_archive(REQUIRED + ("feed_info.txt",), {"feed_info.txt": malformed})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("invalid UTF-8 in GTFS table feed_info.txt", result.stderr)
        self.assertNotIn("Traceback", result.stderr)


    def test_optimized_interpreter_rejects_undersized_feed_before_reporting_success(self):
        result, _ = self.run_archive(REQUIRED + ("calendar.txt",), optimized=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stops.txt row count below minimum", result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_normal_interpreter_rejects_undersized_feed_with_same_diagnostic(self):
        result, _ = self.run_archive(REQUIRED + ("calendar.txt",))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("stops.txt row count below minimum", result.stderr)


if __name__ == "__main__":
    unittest.main()
