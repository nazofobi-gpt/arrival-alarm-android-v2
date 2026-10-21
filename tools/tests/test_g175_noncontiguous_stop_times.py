"""G-175 regression: GTFS stop_times groups cannot be silently truncated."""

import csv
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "reduce_germany_full_for_g175.py"
STOPS = (
    ("L", "Lohne", "52.6669", "8.2387"),
    ("A", "Achim", "53.0144", "9.0356"),
    ("B1", "Berlin Hbf", "52.5251", "13.3694"),
    ("B2", "Berlin Ost", "52.5103", "13.4348"),
    ("M1", "Munich Hbf", "48.1402", "11.5586"),
    ("M2", "Munich Ost", "48.1277", "11.6040"),
)


def put_csv(archive, filename, fields, records):
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=fields, lineterminator="\n")
    writer.writeheader()
    writer.writerows(dict(zip(fields, values)) for values in records)
    archive.writestr(filename, buffer.getvalue())


class NoncontiguousStopTimesTest(unittest.TestCase):
    def run_fixture(self, split_first_trip, missing_selected_route=False,
                    partially_missing_selected_route=False):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.zip"
            output = root / "fixture.zip"
            report = root / "report.json"
            trips = {
                "T0": [("L", "09:00:00"), ("A", "10:00:00"), ("A", "10:15:00")],
                "T1": [("B1", "09:00:00"), ("B2", "10:00:00")],
                "T2": [("M1", "09:00:00"), ("M2", "10:00:00")],
                "T3": [("B1", "09:00:00"), ("M1", "10:00:00")],
            }
            times = {
                trip: [
                    (trip, clock, clock, stop, str(index))
                    for index, (stop, clock) in enumerate(stops, 1)
                ]
                for trip, stops in trips.items()
            }
            if split_first_trip:
                # A second T0 block used to pass without an error; its last stop
                # was omitted from the chosen accepted itinerary.
                ordered = (times["T0"][:2] + times["T1"] + times["T2"] +
                           times["T3"] + times["T0"][2:])
            else:
                ordered = times["T0"] + times["T1"] + times["T2"] + times["T3"]

            with zipfile.ZipFile(source, "w") as archive:
                put_csv(archive, "stops.txt",
                        ("stop_id", "stop_name", "stop_lat", "stop_lon"), STOPS)
                put_csv(archive, "routes.txt",
                        ("route_id", "route_short_name", "route_type"),
                        [("UNKNOWN_ROUTE", "Other route", "2")] if missing_selected_route
                        else [("R", "Regional", "2")])
                put_csv(archive, "trips.txt",
                        ("route_id", "service_id", "trip_id"),
                        [("R2" if partially_missing_selected_route and name == "T3"
                          else "R", "S", name) for name in trips])
                put_csv(archive, "stop_times.txt",
                        ("trip_id", "arrival_time", "departure_time",
                         "stop_id", "stop_sequence"), ordered)
                put_csv(archive, "calendar.txt",
                        ("service_id", "monday", "tuesday", "wednesday",
                         "thursday", "friday", "saturday", "sunday",
                         "start_date", "end_date"),
                        [("S", "1", "1", "1", "1", "1", "1", "1",
                          "20260101", "20271231")])
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(source), str(output), str(report)],
                capture_output=True, text=True, timeout=10, check=False,
            )
            body = json.loads(report.read_text(encoding="utf-8")) if report.exists() else None
            return result, output.exists(), body

    def test_contiguous_real_feed_groups_are_accepted(self):
        result, exists, report = self.run_fixture(split_first_trip=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(exists)
        self.assertEqual(4, report["selectedTripCount"])
        self.assertEqual(4, len(report["acceptanceCases"]))

    def test_repeated_trip_after_other_trip_fails_without_outputs(self):
        result, exists, report = self.run_fixture(split_first_trip=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("noncontiguous stop_times for trip T0", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse(exists)
        self.assertIsNone(report)

    def test_partially_missing_selected_routes_fail_without_outputs(self):
        result, exists, report = self.run_fixture(
            split_first_trip=False, partially_missing_selected_route=True
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing selected GTFS routes: [\'R2\']", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse(exists)
        self.assertIsNone(report)

    def test_missing_route_for_selected_trips_fails_without_outputs(self):
        result, exists, report = self.run_fixture(
            split_first_trip=False, missing_selected_route=True
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing selected GTFS routes: ['R']", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse(exists)
        self.assertIsNone(report)



if __name__ == "__main__":
    unittest.main()
