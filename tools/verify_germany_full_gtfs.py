#!/usr/bin/env python3
import csv
import io
import json
import sys
import zipfile

archive = sys.argv[1]
report_path = sys.argv[2] if len(sys.argv) > 2 else "g174-real-feed-report.json"
required = ["stops.txt", "routes.txt", "trips.txt", "stop_times.txt"]
optional = [
    "agency.txt", "calendar.txt", "calendar_dates.txt", "transfers.txt",
    "shapes.txt", "levels.txt", "pathways.txt", "frequencies.txt",
    "attributions.txt", "feed_info.txt",
]

def inspect(zf, member):
    with zf.open(member) as raw:
        text = io.TextIOWrapper(raw, encoding="utf-8-sig", newline="")
        reader = csv.reader(text)
        header = next(reader)
        return {"rows": sum(1 for _ in reader), "header": header}

with zipfile.ZipFile(archive) as zf:
    names = {
        name.rsplit("/", 1)[-1].lower(): name
        for name in zf.namelist()
        if not name.endswith("/")
    }
    missing = [name for name in required if name not in names]
    if missing:
        raise SystemExit(f"missing required GTFS files: {missing}")
    report = {name: inspect(zf, names[name]) for name in required}
    for name in optional:
        if name in names:
            report[name] = inspect(zf, names[name])

assert report["stops.txt"]["rows"] >= 500_000
assert report["routes.txt"]["rows"] >= 10_000
assert report["trips.txt"]["rows"] >= 1_000_000
assert report["stop_times.txt"]["rows"] > report["trips.txt"]["rows"]
assert (
    report.get("calendar.txt", {}).get("rows", 0) > 0
    or report.get("calendar_dates.txt", {}).get("rows", 0) > 0
), "service calendar missing"

stop_header = set(report["stops.txt"]["header"])
for column in ("stop_id", "stop_name", "stop_lat", "stop_lon"):
    assert column in stop_header, f"stops.txt missing {column}"

with open(report_path, "w", encoding="utf-8") as output:
    json.dump(report, output, ensure_ascii=False, indent=2)

print(json.dumps({name: data["rows"] for name, data in report.items()}, indent=2))
