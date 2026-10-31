#!/usr/bin/env python3
"""Build a deterministic, real-feed GTFS acceptance fixture for G-175.

The source is the unmodified Germany Full archive. The fixture retains real stop,
trip, route and service rows for four direct routing scenarios plus enough real
stops to exercise the production nationwide-index threshold without pushing the
~300 MiB source archive into an emulator.
"""
import atexit
import csv
import io
import json
import math
import os
import sys
import tempfile
import zipfile
from pathlib import Path
from collections import defaultdict
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

SOURCE, OUTPUT, REPORT = sys.argv[1:4]
MIN_STOPS = 10_001
MAX_TRIPS_PER_CASE = 4
CASES = {
    "lohne-achim": ((52.6669, 8.2387), (53.0144, 9.0356)),
    "berlin": ((52.5251, 13.3694), (52.5103, 13.4348)),
    "munich": ((48.1402, 11.5586), (48.1277, 11.6040)),
    "cross-region": ((52.5251, 13.3694), (48.1402, 11.5586)),
}

def member_map(zf):
    consumed_tables = {
        "stops.txt", "routes.txt", "trips.txt", "stop_times.txt",
        "calendar.txt", "calendar_dates.txt", "feed_info.txt",
    }
    names = {}
    table_root = None
    for member in zf.namelist():
        if member.endswith("/"):
            continue
        name = member.rsplit("/", 1)[-1].lower()
        if name in consumed_tables:
            if name in names:
                raise SystemExit(f"duplicate GTFS table {name}: {names[name]} and {member}")
            root = member.rpartition("/")[0]
            if table_root is None:
                table_root = root
            elif root != table_root:
                raise SystemExit("GTFS tables must share one ZIP directory")
        names[name] = member
    return names

def rows(zf, member):
    # The reducer is an acceptance-evidence generator. Never turn malformed
    # GTFS CSV into a plausible subset by dropping surplus/missing columns.
    try:
        with zf.open(member) as raw:
            with io.TextIOWrapper(raw, encoding="utf-8-sig", newline="") as text:
                reader = csv.DictReader(text, strict=True)
                fields = reader.fieldnames
                if (
                    not fields
                    or len(set(fields)) != len(fields)
                    or any(not field.strip() or "\x00" in field for field in fields)
                ):
                    raise SystemExit(f"invalid GTFS CSV header {member}")
                for row in reader:
                    if None in row or any(
                        value is None or "\x00" in value for value in row.values()
                    ):
                        raise SystemExit(f"invalid GTFS CSV row {member}")
                    yield row
    except UnicodeError:
        raise SystemExit(f"invalid GTFS CSV UTF-8 {member}") from None
    except csv.Error:
        raise SystemExit(f"invalid GTFS CSV syntax {member}") from None

def valid_stop_coordinates(row):
    # A malformed provider coordinate must not abort nationwide fixture selection.
    try:
        latitude = float(row["stop_lat"])
        longitude = float(row["stop_lon"])
    except (KeyError, TypeError, ValueError):
        return None
    if not (math.isfinite(latitude) and -90.0 <= latitude <= 90.0
            and math.isfinite(longitude) and -180.0 <= longitude <= 180.0):
        return None
    return latitude, longitude

def distance_m(a, b):
    lat1, lon1 = map(math.radians, a)
    lat2, lon2 = map(math.radians, b)
    dlat, dlon = lat2 - lat1, lon2 - lon1
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 12_742_000 * math.asin(math.sqrt(h))

def next_weekday():
    current = datetime.now(ZoneInfo("Europe/Berlin")) + timedelta(days=1)
    while current.weekday() >= 5:
        current += timedelta(days=1)
    return current.date()

def active_services(zf, names, day):
    active = set()
    calendar_member = names.get("calendar.txt")
    if calendar_member:
        weekday = day.strftime("%A").lower()
        compact = day.strftime("%Y%m%d")
        for row in rows(zf, calendar_member):
            if row.get("start_date", "") <= compact <= row.get("end_date", "") and row.get(weekday) == "1":
                active.add(row["service_id"])
    dates_member = names.get("calendar_dates.txt")
    if dates_member:
        compact = day.strftime("%Y%m%d")
        for row in rows(zf, dates_member):
            if row.get("date") != compact:
                continue
            if row.get("exception_type") == "1":
                active.add(row["service_id"])
            elif row.get("exception_type") == "2":
                active.discard(row["service_id"])
    return active

def clock_seconds(raw):
    if not isinstance(raw, str):
        return -1
    parts = raw.split(":")
    # GTFS allows H:MM:SS and extended service-day hours, but not
    # signs, whitespace, non-ASCII digits or abbreviated minute/second fields.
    if (
        len(parts) != 3
        or not parts[0]
        or len(parts[1]) != 2
        or len(parts[2]) != 2
        or any(not part.isascii() or not part.isdecimal() for part in parts)
    ):
        return -1
    try:
        h, m, s = map(int, parts)
    except ValueError:
        return -1
    if m >= 60 or s >= 60:
        return -1
    return h * 3600 + m * 60 + s

def write_csv(zf, filename, fieldnames, records):
    buffer = io.StringIO(newline="")
    writer = csv.DictWriter(buffer, fieldnames=fieldnames, extrasaction="ignore", lineterminator="\n")
    writer.writeheader()
    writer.writerows(records)
    zf.writestr(filename, buffer.getvalue().encode("utf-8"))

def paths_alias(left, right):
    first, second = Path(left), Path(right)
    return first.resolve() == second.resolve() or (
        first.is_file() and second.is_file() and first.samefile(second)
    )


for left_name, left, right_name, right in (
    ("SOURCE", SOURCE, "OUTPUT", OUTPUT),
    ("SOURCE", SOURCE, "REPORT", REPORT),
    ("OUTPUT", OUTPUT, "REPORT", REPORT),
):
    if paths_alias(left, right):
        raise SystemExit(f"{left_name} and {right_name} must be different files")

# Fail before overwriting a known-good fixture if either output is invalid.
for label, filename in (("OUTPUT", OUTPUT), ("REPORT", REPORT)):
    destination = Path(filename)
    if destination.is_dir() or not destination.parent.is_dir():
        raise SystemExit(f"{label} must be a file in an existing directory")


target_day = next_weekday()
try:
    source_archive = zipfile.ZipFile(SOURCE)
except (OSError, zipfile.BadZipFile):
    raise SystemExit("unable to open Germany Full GTFS ZIP source") from None

with source_archive as source:
    names = member_map(source)
    for required in ("stops.txt", "routes.txt", "trips.txt", "stop_times.txt"):
        if required not in names:
            raise SystemExit(f"missing {required}")

    active = active_services(source, names, target_day)
    if not active:
        raise SystemExit(f"no active service for {target_day}")

    points = {point for pair in CASES.values() for point in pair}
    near = {point: set() for point in points}
    filler = []
    stop_fields = None
    for row in rows(source, names["stops.txt"]):
        if stop_fields is None:
            stop_fields = list(row)
        if len(filler) < MIN_STOPS:
            filler.append(row)
        location = valid_stop_coordinates(row)
        if location is None:
            continue
        for point in points:
            if distance_m(location, point) <= 1_750:
                near[point].add(row["stop_id"])
    for point, ids in near.items():
        if not ids:
            raise SystemExit(f"no stops near {point}")

    active_trips = {}
    trip_fields = None
    for row in rows(source, names["trips.txt"]):
        if trip_fields is None:
            trip_fields = list(row)
        if row.get("service_id") in active:
            active_trips[row["trip_id"]] = row

    selected = {name: [] for name in CASES}
    selected_stop_times = {}
    selected_endpoints = {}
    lohne_outbound = []
    achim_inbound = []
    current_trip = None
    current_rows = []
    stop_time_fields = None

    def consume(trip_id, trip_rows):
        if not trip_id or trip_id not in active_trips or not trip_rows:
            return
        ids = [row.get("stop_id", "") for row in trip_rows]
        lohne_ids = near[CASES["lohne-achim"][0]]
        achim_ids = near[CASES["lohne-achim"][1]]
        lohne_indexes = [i for i, stop_id in enumerate(ids) if stop_id in lohne_ids]
        achim_indexes = [i for i, stop_id in enumerate(ids) if stop_id in achim_ids]
        if lohne_indexes:
            origin_index = lohne_indexes[0]
            origin_departure = clock_seconds(
                trip_rows[origin_index].get("departure_time")
                or trip_rows[origin_index].get("arrival_time", "")
            )
            if 8 * 3600 <= origin_departure <= 14 * 3600:
                lohne_outbound.append((trip_id, origin_index, list(trip_rows)))
        if achim_indexes:
            achim_inbound.append((trip_id, achim_indexes[-1], list(trip_rows)))
        for case, (origin_point, destination_point) in CASES.items():
            if len(selected[case]) >= MAX_TRIPS_PER_CASE:
                continue
            origin_indexes = [i for i, stop_id in enumerate(ids) if stop_id in near[origin_point]]
            destination_indexes = [i for i, stop_id in enumerate(ids) if stop_id in near[destination_point]]
            pair = next(((a, b) for a in origin_indexes for b in destination_indexes if b > a), None)
            if pair is None:
                continue
            departure = trip_rows[pair[0]].get("departure_time") or trip_rows[pair[0]].get("arrival_time", "")
            seconds = clock_seconds(departure)
            if not (8 * 3600 <= seconds <= 14 * 3600):
                continue
            selected[case].append(trip_id)
            selected_stop_times[trip_id] = list(trip_rows)
            selected_endpoints.setdefault(
                case,
                (trip_rows[pair[0]]["stop_id"], trip_rows[pair[1]]["stop_id"]),
            )

    # Streaming assumes each trip's stop_times occupy one contiguous block.
    # Reject a reappearing trip rather than silently selecting an incomplete
    # stop sequence and publishing misleading real-feed acceptance evidence.
    completed_trip_ids = set()
    for row in rows(source, names["stop_times.txt"]):
        if stop_time_fields is None:
            stop_time_fields = list(row)
        trip_id = row.get("trip_id")
        if current_trip is None:
            current_trip = trip_id
        if trip_id != current_trip:
            completed_trip_ids.add(current_trip)
            if trip_id in completed_trip_ids:
                raise SystemExit(f"noncontiguous stop_times for trip {trip_id}")
            consume(current_trip, current_rows)
            current_trip, current_rows = trip_id, []
        current_rows.append(row)
    consume(current_trip, current_rows)

    if not selected["lohne-achim"]:
        connection = None
        for first_trip, origin_index, first_rows in lohne_outbound:
            first_by_stop = {
                row.get("stop_id"): (i, clock_seconds(row.get("arrival_time") or row.get("departure_time", "")))
                for i, row in enumerate(first_rows)
                if i > origin_index
            }
            for second_trip, destination_index, second_rows in achim_inbound:
                if first_trip == second_trip:
                    continue
                for transfer_index, row in enumerate(second_rows[:destination_index]):
                    stop_id = row.get("stop_id")
                    first_match = first_by_stop.get(stop_id)
                    if first_match is None:
                        continue
                    first_arrival = first_match[1]
                    second_departure = clock_seconds(row.get("departure_time") or row.get("arrival_time", ""))
                    if first_arrival >= 0 and second_departure >= first_arrival + 120:
                        connection = (
                            first_trip,
                            first_rows,
                            origin_index,
                            second_trip,
                            second_rows,
                            destination_index,
                            stop_id,
                        )
                        break
                if connection:
                    break
            if connection:
                break
        if connection:
            (
                first_trip,
                first_rows,
                origin_index,
                second_trip,
                second_rows,
                destination_index,
                _,
            ) = connection
            selected["lohne-achim"] = [first_trip, second_trip]
            selected_stop_times[first_trip] = first_rows
            selected_stop_times[second_trip] = second_rows
            selected_endpoints["lohne-achim"] = (
                first_rows[origin_index]["stop_id"],
                second_rows[destination_index]["stop_id"],
            )

    missing = [case for case, trips in selected.items() if not trips]
    if missing:
        raise SystemExit(f"no active 08:00-14:00 direct trips for {missing} on {target_day}")

    selected_trip_ids = {trip for trips in selected.values() for trip in trips}
    selected_trip_rows = [active_trips[trip] for trip in sorted(selected_trip_ids)]
    route_ids = {row["route_id"] for row in selected_trip_rows}
    service_ids = {row["service_id"] for row in selected_trip_rows}
    required_stop_ids = {
        row["stop_id"]
        for trip in selected_trip_ids
        for row in selected_stop_times[trip]
    }
    required_stop_ids.update(stop_id for ids in near.values() for stop_id in ids)

    kept_stops = {row["stop_id"]: row for row in filler}
    for row in rows(source, names["stops.txt"]):
        if row.get("stop_id") in required_stop_ids:
            kept_stops[row["stop_id"]] = row

    def acceptance_point(stop_id):
        row = kept_stops[stop_id]
        return {
            "stopId": stop_id,
            "name": row.get("stop_name") or stop_id,
            "lat": float(row["stop_lat"]),
            "lon": float(row["stop_lon"]),
        }

    acceptance_cases = {
        case: {
            "origin": acceptance_point(endpoints[0]),
            "destination": acceptance_point(endpoints[1]),
        }
        for case, endpoints in selected_endpoints.items()
    }
    if set(acceptance_cases) != set(CASES):
        raise SystemExit(
            f"missing selected acceptance endpoints for {sorted(set(CASES) - set(acceptance_cases))}"
        )

    route_rows = [row for row in rows(source, names["routes.txt"]) if row.get("route_id") in route_ids]
    missing_route_ids = route_ids - {row.get("route_id") for row in route_rows}
    if missing_route_ids:
        raise SystemExit(f"missing selected GTFS routes: {sorted(missing_route_ids)}")
    route_fields = list(route_rows[0])
    schedule_rows = []
    for trip in sorted(selected_trip_ids):
        schedule_rows.extend(selected_stop_times[trip])

    calendar_rows = []
    calendar_fields = None
    if names.get("calendar.txt"):
        for row in rows(source, names["calendar.txt"]):
            calendar_fields = calendar_fields or list(row)
            if row.get("service_id") in service_ids:
                calendar_rows.append(row)
    date_rows = []
    date_fields = None
    if names.get("calendar_dates.txt"):
        for row in rows(source, names["calendar_dates.txt"]):
            date_fields = date_fields or list(row)
            if row.get("service_id") in service_ids:
                date_rows.append(row)

    # Stage ZIP contents; an unsuccessful export must not truncate the old fixture.
    fd, staged_output = tempfile.mkstemp(prefix=".g175-fixture-", suffix=".zip", dir=Path(OUTPUT).parent)
    os.close(fd)
    atexit.register(lambda: Path(staged_output).unlink(missing_ok=True))
    with zipfile.ZipFile(staged_output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        write_csv(target, "stops.txt", stop_fields, kept_stops.values())
        write_csv(target, "routes.txt", route_fields, route_rows)
        write_csv(target, "trips.txt", trip_fields, selected_trip_rows)
        write_csv(target, "stop_times.txt", stop_time_fields, schedule_rows)
        if calendar_fields:
            write_csv(target, "calendar.txt", calendar_fields, calendar_rows)
        if date_fields:
            write_csv(target, "calendar_dates.txt", date_fields, date_rows)
        if names.get("feed_info.txt"):
            feed_rows = list(rows(source, names["feed_info.txt"]))
            if feed_rows:
                write_csv(target, "feed_info.txt", list(feed_rows[0]), feed_rows)
        target.writestr(
            "g175_acceptance.json",
            json.dumps(
                {
                    "taskId": "G-175-REAL-FEED-VERIFY-001",
                    "serviceDate": target_day.isoformat(),
                    "cases": acceptance_cases,
                },
                ensure_ascii=False,
                indent=2,
            ).encode("utf-8"),
        )

report = {
    "taskId": "G-175-REAL-FEED-VERIFY-001",
    "source": SOURCE,
    "sourceBytes": os.path.getsize(SOURCE),
    "fixture": OUTPUT,
    "fixtureBytes": os.path.getsize(staged_output),
    "serviceDate": target_day.isoformat(),
    "selectedTrips": selected,
    "acceptanceCases": acceptance_cases,
    "selectedTripCount": len(selected_trip_ids),
    "selectedStopCount": len(kept_stops),
    "providerMode": "OFF_LOCAL_STATIC_ONLY",
}
fd, staged_report = tempfile.mkstemp(prefix=".g175-report-", suffix=".json", dir=Path(REPORT).parent)
os.close(fd)
atexit.register(lambda: Path(staged_report).unlink(missing_ok=True))
with open(staged_report, "w", encoding="utf-8") as output:
    json.dump(report, output, ensure_ascii=False, indent=2)
os.replace(staged_output, OUTPUT)
os.replace(staged_report, REPORT)
print(json.dumps(report, ensure_ascii=False))
