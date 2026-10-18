"""Regression tests for duplicate GTFS table identities in the G-175 reducer."""

from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "reduce_germany_full_for_g175.py"
REQUIRED = ("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")


class ReducerMemberIdentityTest(unittest.TestCase):
    def run_fixture(self, members: tuple[str, ...]) -> tuple[subprocess.CompletedProcess[str], bool]:
        with tempfile.TemporaryDirectory() as dirname:
            root = Path(dirname)
            source = root / "source.zip"
            output = root / "fixture.zip"
            report = root / "report.json"
            with zipfile.ZipFile(source, "w") as archive:
                for member in members:
                    archive.writestr(member, "id\n1\n")
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(source), str(output), str(report)],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            return result, output.exists() or report.exists()

    def test_duplicate_required_table_case_alias_fails_closed(self):
        result, created_output = self.run_fixture(REQUIRED + ("nested/STOPS.TXT",))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicate GTFS table stops.txt", result.stderr)
        self.assertIn("nested/STOPS.TXT", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse(created_output)

    def test_duplicate_consumed_optional_calendar_fails_closed(self):
        result, created_output = self.run_fixture(
            REQUIRED + ("calendar.txt", "other/CALENDAR.TXT")
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicate GTFS table calendar.txt", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse(created_output)

    def test_distinct_members_do_not_trigger_duplicate_guard(self):
        result, created_output = self.run_fixture(REQUIRED + ("calendar.txt",))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("no active service", result.stderr)
        self.assertNotIn("duplicate GTFS table", result.stderr)
        self.assertFalse(created_output)


if __name__ == "__main__":
    unittest.main()
