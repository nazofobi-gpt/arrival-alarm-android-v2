#!/usr/bin/env python3
import csv
import io
import json
import sys
import zipfile
from pathlib import Path

from atomic_json_report import write_atomic_json_report

archive = sys.argv[1]
report_path = sys.argv[2] if len(sys.argv) > 2 else "g174-real-feed-report.json"

# The report is an output artifact; never overwrite the input GTFS archive.
# resolve() catches relative/symlink aliases; samefile() also catches hard links.
archive_path = Path(archive)
output_path = Path(report_path)
if archive_path.resolve() == output_path.resolve() or (
    archive_path.is_file() and output_path.is_file()
    and archive_path.samefile(output_path)
):
    raise SystemExit("GTFS report path must differ from input ZIP archive")
required = ["stops.txt", "routes.txt", "trips.txt", "stop_times.txt"]
optional = [
    "agency.txt", "calendar.txt", "calendar_dates.txt", "transfers.txt",
    "shapes.txt", "levels.txt", "pathways.txt", "frequencies.txt",
    "attributions.txt", "feed_info.txt",
]

def inspect(zf, member):
    try:
        with zf.open(member) as raw:
            text = io.TextIOWrapper(raw, encoding="utf-8-sig", newline="")
            reader = csv.reader(text, strict=True)
            header = next(reader)
            if any("\x00" in column for column in header):
                raise SystemExit(f"invalid NUL in GTFS table {member} header")
            if not any(column.strip() for column in header):
                raise SystemExit(f"empty GTFS table {member}: missing CSV header")
            if any(not column.strip() for column in header):
                raise SystemExit(f"blank GTFS column name in {member}")
            seen = set()
            for column in header:
                if column in seen:
                    raise SystemExit(f"duplicate GTFS column {member}: {column}")
                seen.add(column)
            rows = 0
            for row in reader:
                if any("\x00" in value for value in row):
                    raise SystemExit(
                        f"invalid NUL in GTFS table {member} at CSV line {reader.line_num}"
                    )
                if len(row) != len(header):
                    raise SystemExit(
                        f"GTFS table {member} has {len(row)} columns at CSV line "
                        f"{reader.line_num}; expected {len(header)}"
                    )
                rows += 1
            return {"rows": rows, "header": header}
    except zipfile.BadZipFile:
        raise SystemExit(f"invalid GTFS ZIP entry {member}") from None
    except NotImplementedError:
        raise SystemExit(f"unsupported GTFS ZIP entry compression {member}") from None
    except RuntimeError:
        # Password-protected GTFS tables must fail closed without a traceback.
        raise SystemExit(f"unable to read GTFS ZIP entry {member}") from None
    except UnicodeDecodeError:
        raise SystemExit(f"invalid UTF-8 in GTFS table {member}") from None
    except csv.Error:
        raise SystemExit(f"invalid CSV in GTFS table {member}") from None
    except StopIteration:
        raise SystemExit(f"empty GTFS table {member}: missing CSV header") from None

try:
    zf = zipfile.ZipFile(archive)
except zipfile.BadZipFile:
    raise SystemExit("invalid GTFS ZIP archive") from None
except OSError:
    raise SystemExit("unable to open GTFS ZIP archive") from None

with zf:
    # A ZIP can contain both stops.txt and nested/stops.txt (or a case alias).
    # Never silently select the last table: a mixed source would be unverifiable.
    known_tables = set(required) | set(optional)
    names = {}
    for name in zf.namelist():
        if name.endswith("/"):
            continue
        basename = name.rsplit("/", 1)[-1].lower()
        if basename not in known_tables:
            continue
        if basename in names:
            raise SystemExit(
                f"duplicate GTFS table {basename}: {names[basename]} and {name}"
            )
        names[basename] = name
    missing = [name for name in required if name not in names]
    if missing:
        raise SystemExit(f"missing required GTFS files: {missing}")
    report = {name: inspect(zf, names[name]) for name in required}
    for name in optional:
        if name in names:
            report[name] = inspect(zf, names[name])

# Require structural GTFS columns before counting a feed as valid.
required_columns = {
    "stops.txt": ("stop_id", "stop_name", "stop_lat", "stop_lon"),
    "routes.txt": ("route_id", "route_type"),
    "trips.txt": ("route_id", "service_id", "trip_id"),
    "stop_times.txt": ("trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence"),
    # Optional service tables are authoritative when present: missing fields must
    # not turn a large but unroutable schedule into a verified offline pack.
    "calendar.txt": ("service_id", "monday", "tuesday", "wednesday", "thursday",
                     "friday", "saturday", "sunday", "start_date", "end_date"),
    "calendar_dates.txt": ("service_id", "date", "exception_type"),
    # Optional GTFS files still require their core columns when present.
    "shapes.txt": ("shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence"),
    "frequencies.txt": ("trip_id", "start_time", "end_time", "headway_secs"),
}
for table, columns in required_columns.items():
    if table not in report:
        continue  # calendar and calendar_dates are individually optional.
    header = set(report[table]["header"])
    for column in columns:
        if column not in header:
            raise SystemExit(f"{table} missing required GTFS column {column}")

# Fail closed even when Python is launched with -O (which removes assert statements).
for table, minimum in (
    ("stops.txt", 500_000),
    ("routes.txt", 10_000),
    ("trips.txt", 1_000_000),
):
    rows = report[table]["rows"]
    if rows < minimum:
        raise SystemExit(f"{table} row count below minimum: {rows} < {minimum}")

if report["stop_times.txt"]["rows"] <= report["trips.txt"]["rows"]:
    raise SystemExit("stop_times.txt must contain more rows than trips.txt")
if not (
    report.get("calendar.txt", {}).get("rows", 0) > 0
    or report.get("calendar_dates.txt", {}).get("rows", 0) > 0
):
    raise SystemExit("service calendar missing")

write_atomic_json_report(report_path, report)

print(json.dumps({name: data["rows"] for name, data in report.items()}, indent=2))
