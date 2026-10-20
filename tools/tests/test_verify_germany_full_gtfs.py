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
            # Default samples need valid GTFS headers; size-threshold tests
            # must exercise row counts rather than unrelated column failures.
            fixtures = {
                "stops.txt": "stop_id,stop_name,stop_lat,stop_lon\n1,Test,52.0,8.0\n",
                "routes.txt": "route_id,route_type\nR,3\n",
                "trips.txt": "route_id,service_id,trip_id\nR,S,T\n",
                "stop_times.txt": "trip_id,arrival_time,departure_time,stop_id,stop_sequence\nT,10:00:00,10:00:00,1,1\n",
            }
            with zipfile.ZipFile(archive, "w") as zf:
                for member in entries:
                    base = member.rsplit("/", 1)[-1].lower()
                    default = fixtures.get(base, "id,name\n1,Example\n")
                    zf.writestr(member, (overrides or {}).get(member, default))
            result = subprocess.run(
                [sys.executable, *(["-O"] if optimized else []), str(SCRIPT), str(archive), str(report)],
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            self.assertFalse(report.exists(), "Unverified feed must not create a success report")
            return result, report

    def test_mismatched_csv_record_columns_fail_closed_in_required_and_optional_tables(self):
        cases = (
            ("routes.txt", REQUIRED, b"route_id,route_type\nR\n", 1, 2),
            (
                "stops.txt",
                REQUIRED,
                b"stop_id,stop_name,stop_lat,stop_lon\n1,Test,52.0,8.0,EXTRA\n",
                5, 4,
            ),
            (
                "feed_info.txt",
                REQUIRED + ("feed_info.txt",),
                b"feed_publisher_name,feed_publisher_url\nAgency\n",
                1, 2,
            ),
            (
                "agency.txt",
                REQUIRED + ("agency.txt",),
                b"agency_id,agency_name\nA,Agency,EXTRA\n",
                3, 2,
            ),
        )
        for optimized in (False, True):
            for member, entries, malformed, actual, expected in cases:
                with self.subTest(member=member, optimized=optimized):
                    result, _ = self.run_archive(
                        entries, {member: malformed}, optimized=optimized,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(
                        f"GTFS table {member} has {actual} columns at CSV line 2; expected {expected}",
                        result.stderr,
                    )
                    self.assertNotIn("Traceback", result.stderr)

    def test_valid_quoted_multiline_record_does_not_trigger_column_mismatch(self):
        valid_stops = (
            b"stop_id,stop_name,stop_lat,stop_lon\n"
            b'1,"First, stop\nSecond stop",52.0,8.0\n'
        )
        for optimized in (False, True):
            with self.subTest(optimized=optimized):
                result, _ = self.run_archive(
                    REQUIRED, {"stops.txt": valid_stops}, optimized=optimized,
                )
                self.assertNotEqual(0, result.returncode)
                self.assertIn("stops.txt row count below minimum", result.stderr)
                self.assertNotIn("has 5 columns", result.stderr)
                self.assertNotIn("Traceback", result.stderr)

    def test_blank_column_inside_populated_gtfs_header_fails_closed(self):
        cases = (
            (
                "stops.txt", REQUIRED,
                b"stop_id,stop_name,stop_lat,stop_lon,\n1,Test,52.0,8.0,unused\n",
            ),
            (
                "feed_info.txt", REQUIRED + ("feed_info.txt",),
                b"feed_publisher_name, \nAgency,unused\n",
            ),
        )
        for optimized in (False, True):
            for member, entries, malformed in cases:
                with self.subTest(member=member, optimized=optimized):
                    result, _ = self.run_archive(
                        entries, {member: malformed}, optimized=optimized,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(f"blank GTFS column name in {member}", result.stderr)
                    self.assertNotIn("Traceback", result.stderr)

    def test_duplicate_columns_in_required_and_optional_tables_fail_closed(self):
        cases = (
            ("routes.txt", REQUIRED, b"route_id,route_id,route_type\nR,WRONG,3\n"),
            (
                "feed_info.txt",
                REQUIRED + ("feed_info.txt",),
                b"feed_publisher_name,feed_publisher_name\nAgency,Wrong\n",
            ),
        )
        for optimized in (False, True):
            for member, entries, content in cases:
                with self.subTest(member=member, optimized=optimized):
                    result, _ = self.run_archive(
                        entries, {member: content}, optimized=optimized,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(f"duplicate GTFS column {member}: ", result.stderr)
                    self.assertNotIn("Traceback", result.stderr)

    def test_oversized_csv_fields_fail_with_diagnostic_in_required_and_optional_tables(self):
        too_long = b"X" * (131072 + 1)
        cases = (
            ("routes.txt", REQUIRED, b"route_id,route_type\n" + too_long + b",3\n"),
            ("feed_info.txt", REQUIRED + ("feed_info.txt",), b"feed_publisher_name\n" + too_long + b"\n"),
        )
        for optimized in (False, True):
            for member, entries, content in cases:
                with self.subTest(member=member, optimized=optimized):
                    result, _ = self.run_archive(
                        entries, {member: content}, optimized=optimized,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(f"invalid CSV in GTFS table {member}", result.stderr)
                    self.assertNotIn("Traceback", result.stderr)

    def test_malformed_quoted_records_fail_closed_in_required_and_optional_tables(self):
        cases = (
            ("routes.txt", REQUIRED, b'route_id,route_type\n"R"junk,3\n'),
            (
                "feed_info.txt",
                REQUIRED + ("feed_info.txt",),
                b'feed_publisher_name\n"Unterminated agency\n',
            ),
        )
        for optimized in (False, True):
            for member, entries, malformed in cases:
                with self.subTest(member=member, optimized=optimized):
                    result, _ = self.run_archive(
                        entries, {member: malformed}, optimized=optimized,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(f"invalid CSV in GTFS table {member}", result.stderr)
                    self.assertNotIn("Traceback", result.stderr)

    def test_missing_required_columns_fail_closed_in_normal_and_optimized_python(self):
        valid = {
            "stops.txt": b"stop_id,stop_name,stop_lat,stop_lon\n1,Test,52.0,8.0\n",
            "routes.txt": b"route_id,route_type\nR,3\n",
            "trips.txt": b"route_id,service_id,trip_id\nR,S,T\n",
            "stop_times.txt": b"trip_id,arrival_time,departure_time,stop_id,stop_sequence\nT,10:00:00,10:00:00,1,1\n",
        }
        invalid = (
            ("stops.txt", "stop_id", b"stop_name,stop_lat,stop_lon\nTest,52.0,8.0\n"),
            ("routes.txt", "route_type", b"route_id\nR\n"),
            ("trips.txt", "service_id", b"route_id,trip_id\nR,T\n"),
            ("stop_times.txt", "stop_sequence", b"trip_id,arrival_time,departure_time,stop_id\nT,10:00:00,10:00:00,1\n"),
        )
        for optimized in (False, True):
            for table, column, content in invalid:
                with self.subTest(optimized=optimized, table=table, column=column):
                    entries = dict(valid)
                    entries[table] = content
                    result, _ = self.run_archive(REQUIRED, entries, optimized=optimized)
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn(f"{table} missing required GTFS column {column}", result.stderr)
                    self.assertNotIn("Traceback", result.stderr)

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


    def test_empty_required_table_fails_without_traceback(self):
        result, _ = self.run_archive(REQUIRED, {"stops.txt": b""})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("empty GTFS table stops.txt: missing CSV header", result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_empty_optional_table_fails_without_traceback(self):
        result, _ = self.run_archive(REQUIRED + ("feed_info.txt",), {"feed_info.txt": b""})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("empty GTFS table feed_info.txt: missing CSV header", result.stderr)
        self.assertNotIn("Traceback", result.stderr)


    def test_blank_line_optional_table_is_not_valid_csv_header(self):
        result, _ = self.run_archive(REQUIRED + ("feed_info.txt",), {"feed_info.txt": b"\n"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("empty GTFS table feed_info.txt: missing CSV header", result.stderr)
        self.assertNotIn("Traceback", result.stderr)

    def test_blank_column_optional_table_is_not_valid_csv_header(self):
        result, _ = self.run_archive(REQUIRED + ("feed_info.txt",), {"feed_info.txt": b" , , \n"})
        self.assertNotEqual(0, result.returncode)
        self.assertIn("empty GTFS table feed_info.txt: missing CSV header", result.stderr)
        self.assertNotIn("Traceback", result.stderr)


    def test_invalid_zip_container_fails_without_python_traceback(self):
        with tempfile.TemporaryDirectory() as dirname:
            archive = Path(dirname) / "broken.zip"
            report = Path(dirname) / "report.json"
            archive.write_bytes(b"not a GTFS ZIP archive")
            for optimized in (False, True):
                with self.subTest(optimized=optimized):
                    result = subprocess.run(
                        [sys.executable, *(["-O"] if optimized else []),
                         str(SCRIPT), str(archive), str(report)],
                        capture_output=True,
                        text=True,
                        timeout=10,
                        check=False,
                    )
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("invalid GTFS ZIP archive", result.stderr)
                    self.assertNotIn("Traceback", result.stderr)
                    self.assertFalse(report.exists())


    def test_corrupt_zip_entry_crc_fails_closed_without_traceback(self):
        fixtures = {
            "stops.txt": b"stop_id,stop_name,stop_lat,stop_lon\n1,Test,52.0,8.0\n",
            "routes.txt": b"route_id,route_type\nR,3\n",
            "trips.txt": b"route_id,service_id,trip_id\nR,S,T\n",
            "stop_times.txt": b"trip_id,arrival_time,departure_time,stop_id,stop_sequence\nT,10:00:00,10:00:00,1,1\n",
            "feed_info.txt": b"id,name\n1,Example\n",
        }
        for member, entries, original, corrupted in (
            ("stops.txt", REQUIRED, b"1,Test,52.0,8.0", b"1,Fail,52.0,8.0"),
            ("feed_info.txt", REQUIRED + ("feed_info.txt",),
             b"1,Example", b"1,Changed"),
        ):
            with self.subTest(member=member):
                with tempfile.TemporaryDirectory() as dirname:
                    archive = Path(dirname) / "corrupted-entry.zip"
                    report = Path(dirname) / "report.json"
                    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_STORED) as zf:
                        for name in entries:
                            zf.writestr(name, fixtures[name])
                    archive_bytes = archive.read_bytes()
                    self.assertEqual(archive_bytes.count(original), 1)
                    archive.write_bytes(archive_bytes.replace(original, corrupted, 1))
                    for optimized in (False, True):
                        with self.subTest(optimized=optimized):
                            result = subprocess.run(
                                [sys.executable, *(['-O'] if optimized else []),
                                 str(SCRIPT), str(archive), str(report)],
                                capture_output=True,
                                text=True,
                                timeout=10,
                                check=False,
                            )
                            self.assertNotEqual(result.returncode, 0)
                            self.assertIn(f"invalid GTFS ZIP entry {member}", result.stderr)
                            self.assertNotIn("Traceback", result.stderr)
                            self.assertFalse(report.exists())


if __name__ == "__main__":
    unittest.main()
