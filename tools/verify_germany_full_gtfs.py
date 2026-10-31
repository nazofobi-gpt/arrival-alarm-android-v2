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

# These identity fields must be populated in every GTFS record. Do not require
# stop_times.stop_id here: GTFS Flex may use a location_group_id or location_id.
NONEMPTY_ID_FIELDS = {
    "stops.txt": ("stop_id",),
    "routes.txt": ("route_id",),
    "trips.txt": ("route_id", "service_id", "trip_id"),
    "stop_times.txt": ("trip_id", "stop_sequence"),
    "calendar.txt": ("service_id",),
    "calendar_dates.txt": ("service_id",),
}

def inspect(zf, member, table):
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
            # Header presence is checked below for all required GTFS columns.
            # Validate present identity fields while records are already streamed.
            identity_positions = [
                (field, header.index(field))
                for field in NONEMPTY_ID_FIELDS.get(table, ())
                if field in seen
            ]
            sequence_position = (
                header.index("stop_sequence")
                if table == "stop_times.txt" and "stop_sequence" in seen
                else None
            )
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
                for field, position in identity_positions:
                    if not row[position].strip():
                        raise SystemExit(
                            f"GTFS table {member} has blank required value {field} "
                            f"at CSV line {reader.line_num}"
                        )
                if sequence_position is not None:
                    sequence = row[sequence_position]
                    if not sequence.isascii() or not sequence.isdecimal():
                        raise SystemExit(
                            f"GTFS table {member} has invalid stop_sequence "
                            f"at CSV line {reader.line_num}"
                        )
                    # A syntactically numeric value may still overflow the
                    # signed 32-bit stop ordering field used by Android.
                    # Compare decimal text to avoid Python's huge-int limit.
                    significant = sequence.lstrip("0") or "0"
                    if len(significant) > 10 or (
                        len(significant) == 10 and significant > "2147483647"
                    ):
                        raise SystemExit(
                            f"GTFS table {member} has out-of-range stop_sequence "
                            f"at CSV line {reader.line_num}"
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
    # All GTFS tables belong to one dataset. Preserve support for a single
    # enclosing folder, but reject archives that mix tables from directories.
    # Otherwise valid-looking files can be silently combined across feeds.
    table_directories = {member.rpartition("/")[0] for member in names.values()}
    if len(table_directories) != 1:
        raise SystemExit("GTFS ZIP tables span multiple directories")
    report = {name: inspect(zf, names[name], name) for name in required}
    for name in optional:
        if name in names:
            report[name] = inspect(zf, names[name], name)

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
    # When supplied, GTFS metadata must carry its required identity fields.
    "agency.txt": ("agency_name", "agency_url", "agency_timezone"),
    "feed_info.txt": ("feed_publisher_name", "feed_publisher_url", "feed_lang"),
    # Optional connection and attribution tables have required columns when present.
    "transfers.txt": ("transfer_type",),
    "attributions.txt": ("organization_name",),
    # Optional GTFS files still require their core columns when present.
    "shapes.txt": ("shape_id", "shape_pt_lat", "shape_pt_lon", "shape_pt_sequence"),
    "frequencies.txt": ("trip_id", "start_time", "end_time", "headway_secs"),
    # Station navigation tables are optional, but their identity and edge
    # columns are mandatory if those files are supplied by a GTFS producer.
    "levels.txt": ("level_id", "level_index"),
    "pathways.txt": ("pathway_id", "from_stop_id", "to_stop_id",
                     "pathway_mode", "is_bidirectional"),
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
