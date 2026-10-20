"""Regression tests for duplicate GTFS table identities in the G-175 reducer."""

import ast
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


class ReducerClockTimeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # The reducer is an executable script with top-level ZIP processing.
        # Load only the pure clock parser to keep regression tests feed-independent.
        tree = ast.parse(SCRIPT.read_text(encoding="utf-8"))
        fn = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "clock_seconds")
        namespace = {}
        exec(compile(ast.Module(body=[fn], type_ignores=[]), str(SCRIPT), "exec"), namespace)
        cls.clock = staticmethod(namespace["clock_seconds"])

    def test_valid_gtfs_times_include_overnight_service_hours(self):
        self.assertEqual(8 * 3600 + 15 * 60, self.clock("08:15:00"))
        self.assertEqual(25 * 3600 + 2 * 60 + 3, self.clock("25:02:03"))
        self.assertEqual(0, self.clock("00:00:00"))

    def test_invalid_minutes_and_seconds_are_not_normalized_into_other_times(self):
        for value in ("08:60:00", "08:99:00", "09:00:60", "09:15:99", "10:-01:00"):
            with self.subTest(value=value):
                self.assertEqual(-1, self.clock(value))

    def test_gtfs_hour_single_digit_is_valid(self):
        # The GTFS Time format explicitly accepts H:MM:SS.
        self.assertEqual(8 * 3600 + 15 * 60, self.clock("8:15:00"))

    def test_noncanonical_clock_tokens_fail_closed(self):
        for value in (
            "08:+15:00", "08:5:00", "08:15:0", "08:15:00 ",
            " 08:15:00", "+08:15:00", "08:１５:00",
            "08:15:00:00", None,
        ):
            with self.subTest(value=value):
                self.assertEqual(-1, self.clock(value))

    def test_negative_hours_and_malformed_time_are_rejected(self):
        for value in ("-1:50:00", "08:30", "abc", ""):
            with self.subTest(value=value):
                self.assertEqual(-1, self.clock(value))


if __name__ == "__main__":
    unittest.main()
