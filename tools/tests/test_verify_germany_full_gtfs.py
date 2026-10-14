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
    def run_archive(self, entries: tuple[str, ...]) -> tuple[subprocess.CompletedProcess[str], Path]:
        with tempfile.TemporaryDirectory() as dirname:
            archive = Path(dirname) / "feed.zip"
            report = Path(dirname) / "report.json"
            with zipfile.ZipFile(archive, "w") as zf:
                for member in entries:
                    zf.writestr(member, "id,name\n1,Example\n")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(archive), str(report)],
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


if __name__ == "__main__":
    unittest.main()
