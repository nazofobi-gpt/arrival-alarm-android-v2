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



class ReducerPublicationTest(unittest.TestCase):
    """Failed report publication must not replace the last verified GTFS ZIP."""

    def test_invalid_report_destination_preserves_previous_fixture_then_recovers(self):
        import json

        with tempfile.TemporaryDirectory() as dirname:
            root = Path(dirname)
            source = root / "source.zip"
            output = root / "accepted.zip"
            report = root / "report.json"
            tables = {
                "stops.txt": "\n".join([
                    "stop_id,stop_name,stop_lat,stop_lon",
                    "lohne,Lohne Bahnhof,52.6669,8.2387",
                    "achim,Achim Bahnhof,53.0144,9.0356",
                    "berlin_a,Berlin Hbf,52.5251,13.3694",
                    "berlin_b,Berlin Mitte,52.5103,13.4348",
                    "munich_a,Munich Hbf,48.1402,11.5586",
                    "munich_b,Munich Ost,48.1277,11.6040",
                    "",
                ]),
                "routes.txt": "route_id,route_short_name,route_long_name,route_type\nr1,R1,Test,2\n",
                "trips.txt": (
                    "route_id,service_id,trip_id,trip_headsign\n"
                    "r1,s1,t1,Achim\nr1,s1,t2,Berlin\n"
                    "r1,s1,t3,Munich\nr1,s1,t4,Munich\n"
                ),
                "stop_times.txt": (
                    "trip_id,arrival_time,departure_time,stop_id,stop_sequence\n"
                    "t1,08:00:00,08:00:00,lohne,1\nt1,09:00:00,09:00:00,achim,2\n"
                    "t2,09:00:00,09:00:00,berlin_a,1\nt2,09:20:00,09:20:00,berlin_b,2\n"
                    "t3,10:00:00,10:00:00,munich_a,1\nt3,10:25:00,10:25:00,munich_b,2\n"
                    "t4,11:00:00,11:00:00,berlin_a,1\nt4,13:00:00,13:00:00,munich_a,2\n"
                ),
                "calendar.txt": (
                    "service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n"
                    "s1,1,1,1,1,1,1,1,20250101,20291231\n"
                ),
            }
            with zipfile.ZipFile(source, "w") as archive:
                for name, contents in tables.items():
                    archive.writestr(name, contents)
            previous = b"prior verified fixture remains untouched"
            output.write_bytes(previous)
            report.mkdir()

            def invoke():
                return subprocess.run(
                    [sys.executable, str(SCRIPT), str(source), str(output), str(report)],
                    capture_output=True, text=True, timeout=15, check=False,
                )

            blocked = invoke()
            self.assertNotEqual(blocked.returncode, 0)
            self.assertIn("REPORT must be a file", blocked.stderr)
            self.assertNotIn("Traceback", blocked.stderr)
            self.assertEqual(previous, output.read_bytes())

            report.rmdir()
            success = invoke()
            self.assertEqual(success.returncode, 0, success.stderr)
            with zipfile.ZipFile(output) as archive:
                self.assertIn("g175_acceptance.json", archive.namelist())
            recorded = json.loads(report.read_text(encoding="utf-8"))
            self.assertEqual(output.stat().st_size, recorded["fixtureBytes"])
            self.assertEqual(4, recorded["selectedTripCount"])
            self.assertEqual([], list(root.glob(".g175-*")))


if __name__ == "__main__":
    unittest.main()
