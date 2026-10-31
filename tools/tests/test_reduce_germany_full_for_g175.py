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


    def test_mixed_required_table_roots_fail_before_any_fixture_output(self):
        result, created_output = self.run_fixture((
            "feed/stops.txt", "feed/routes.txt", "feed/trips.txt",
            "elsewhere/stop_times.txt", "feed/calendar.txt",
        ))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("GTFS tables must share one ZIP directory", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse(created_output)

    def test_mixed_optional_table_root_is_also_rejected(self):
        result, created_output = self.run_fixture((
            "feed/stops.txt", "feed/routes.txt", "feed/trips.txt",
            "feed/stop_times.txt", "calendar_dates.txt",
        ))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("GTFS tables must share one ZIP directory", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse(created_output)

    def test_consistent_nested_root_remains_supported(self):
        result, created_output = self.run_fixture((
            "feed/stops.txt", "feed/routes.txt", "feed/trips.txt",
            "feed/stop_times.txt", "feed/calendar.txt",
        ))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("no active service", result.stderr)
        self.assertNotIn("GTFS tables must share one ZIP directory", result.stderr)
        self.assertFalse(created_output)

    def test_unrelated_zip_metadata_in_another_directory_is_ignored(self):
        result, created_output = self.run_fixture((
            "README.txt", "feed/stops.txt", "feed/routes.txt",
            "feed/trips.txt", "feed/stop_times.txt", "feed/calendar.txt",
        ))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("no active service", result.stderr)
        self.assertNotIn("GTFS tables must share one ZIP directory", result.stderr)
        self.assertFalse(created_output)



class ReducerCsvIntegrityTest(unittest.TestCase):
    """Malformed GTFS rows must not produce credible real-feed fixtures."""

    @classmethod
    def setUpClass(cls):
        import csv
        import io
        tree = ast.parse(SCRIPT.read_text(encoding="utf-8"))
        fn = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "rows")
        namespace = {"csv": csv, "io": io}
        exec(compile(ast.Module(body=[fn], type_ignores=[]), str(SCRIPT), "exec"), namespace)
        cls.iter_rows = staticmethod(namespace["rows"])

    def parse_csv(self, content: bytes):
        import io
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, "w") as archive:
            archive.writestr("stops.txt", content)
        buffer.seek(0)
        with zipfile.ZipFile(buffer) as archive:
            return list(self.iter_rows(archive, "stops.txt"))

    def test_valid_rows_and_empty_optional_field_are_preserved(self):
        self.assertEqual(
            [{"stop_id": "1", "stop_name": "Stop"}, {"stop_id": "2", "stop_name": ""}],
            self.parse_csv(b"stop_id,stop_name\n1,Stop\n2,\n"),
        )

    def test_invalid_header_is_rejected(self):
        invalid = (
            b"", b"stop_id,stop_id\n1,2\n",
            b"stop_id,\n1,2\n", b"stop_id,na\x00me\n1,Stop\n",
        )
        for content in invalid:
            with self.subTest(content=content), self.assertRaises(SystemExit) as caught:
                self.parse_csv(content)
            self.assertIn("invalid GTFS CSV header", str(caught.exception))

    def test_short_long_and_nul_rows_are_rejected(self):
        invalid = (
            b"stop_id,stop_name\n1\n",
            b"stop_id,stop_name\n1,Stop,extra\n",
            b"stop_id,stop_name\n1,St\x00op\n",
        )
        for content in invalid:
            with self.subTest(content=content), self.assertRaises(SystemExit) as caught:
                self.parse_csv(content)
            self.assertIn("invalid GTFS CSV row", str(caught.exception))

    def test_unclosed_quote_and_invalid_utf8_fail_closed(self):
        for content, message in (
            (b'stop_id,stop_name\n1,"unterminated\n', "syntax"),
            (b"stop_id,stop_name\n1,\xff\n", "UTF-8"),
        ):
            with self.subTest(message=message), self.assertRaises(SystemExit) as caught:
                self.parse_csv(content)
            self.assertIn(message, str(caught.exception))


class ReducerSourceZipOpenTest(unittest.TestCase):
    """Source ZIP failures are controlled, without traceback or partial output."""

    def test_missing_directory_and_corrupt_sources_fail_closed(self):
        for source_kind in ("missing", "directory", "corrupt", "empty"):
            with self.subTest(source_kind=source_kind):
                with tempfile.TemporaryDirectory() as dirname:
                    root = Path(dirname)
                    source = root / "source.zip"
                    output = root / "fixture.zip"
                    report = root / "report.json"
                    if source_kind == "directory":
                        source.mkdir()
                    elif source_kind == "corrupt":
                        source.write_bytes(b"not a GTFS ZIP archive")
                    elif source_kind == "empty":
                        source.write_bytes(b"")

                    result = subprocess.run(
                        [sys.executable, str(SCRIPT), str(source), str(output), str(report)],
                        capture_output=True,
                        text=True,
                        timeout=10,
                        check=False,
                    )
                    self.assertNotEqual(result.returncode, 0)
                    self.assertEqual(
                        result.stderr.strip(),
                        "unable to open Germany Full GTFS ZIP source",
                    )
                    self.assertNotIn("Traceback", result.stderr)
                    self.assertFalse(output.exists())
                    self.assertFalse(report.exists())


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


class ReducerStopCoordinateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Parse the feed-independent helper without running the ZIP reduction.
        tree = ast.parse(SCRIPT.read_text(encoding="utf-8"))
        fn = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == "valid_stop_coordinates")
        namespace = {"math": __import__("math")}
        exec(compile(ast.Module(body=[fn], type_ignores=[]), str(SCRIPT), "exec"), namespace)
        cls.point = staticmethod(namespace["valid_stop_coordinates"])

    def test_finite_valid_coordinates_remain_usable(self):
        self.assertEqual((52.6669, 8.2387), self.point({"stop_lat": "52.6669", "stop_lon": "8.2387"}))
        self.assertEqual((-90.0, 180.0), self.point({"stop_lat": "-90", "stop_lon": "180"}))

    def test_nonfinite_out_of_range_and_missing_coordinates_are_ignored(self):
        for row in (
            {"stop_lat": "NaN", "stop_lon": "8.2"},
            {"stop_lat": "52.6", "stop_lon": "Infinity"},
            {"stop_lat": "-Infinity", "stop_lon": "8.2"},
            {"stop_lat": "1e309", "stop_lon": "8.2"},
            {"stop_lat": "90.001", "stop_lon": "8.2"},
            {"stop_lat": "52.6", "stop_lon": "-180.001"},
            {"stop_lat": "52.6", "stop_lon": None},
            {"stop_lat": "52.6"},
            {"stop_lat": "wrong", "stop_lon": "8.2"},
        ):
            with self.subTest(row=row):
                self.assertIsNone(self.point(row))


if __name__ == "__main__":
    unittest.main()
