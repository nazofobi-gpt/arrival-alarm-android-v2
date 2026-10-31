#!/usr/bin/env python3
"""Bind a successful GTFS structural preflight report to its exact source ZIP.

This is a deterministic provenance receipt, not an independent GTFS validator.
Call only after verify_germany_full_gtfs.py succeeds.
"""
import hashlib
import json
import sys
from pathlib import Path

from atomic_json_report import write_atomic_json_report


TABLES = ("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")
MAX_REPORT_BYTES = 2 * 1024 * 1024
CHUNK_BYTES = 1024 * 1024


def aliases(left: Path, right: Path) -> bool:
    return left.resolve() == right.resolve() or (
        left.is_file() and right.is_file() and left.samefile(right)
    )


def digest_archive(path: Path) -> tuple[str, int]:
    before = path.stat()
    checksum = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(CHUNK_BYTES), b""):
            checksum.update(block)
    after = path.stat()
    if (before.st_size, before.st_mtime_ns, before.st_ino) != (
        after.st_size, after.st_mtime_ns, after.st_ino
    ):
        raise ValueError("GTFS source ZIP changed while hashing")
    return checksum.hexdigest(), before.st_size


def make_receipt(archive: Path, report: Path) -> dict:
    if not archive.is_file() or not report.is_file():
        raise ValueError("GTFS ZIP and verified report must be existing files")
    if report.stat().st_size > MAX_REPORT_BYTES:
        raise ValueError("verified GTFS report exceeds evidence size limit")
    report_bytes = report.read_bytes()
    parsed = json.loads(report_bytes.decode("utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError("verified GTFS report must be a JSON object")
    counts = {}
    for table in TABLES:
        entry = parsed.get(table)
        if (not isinstance(entry, dict)
                or type(entry.get("rows")) is not int
                or entry["rows"] <= 0
                or not isinstance(entry.get("header"), list)):
            raise ValueError(f"missing structural preflight evidence for {table}")
        counts[table] = entry["rows"]
    zip_sha256, zip_bytes = digest_archive(archive)
    return {
        "schema": "GTFS_PREFLIGHT_SOURCE_BINDING_V1",
        "acceptance": "STRUCTURAL_PREFLIGHT_ONLY",
        "source_zip_sha256": zip_sha256,
        "source_zip_bytes": zip_bytes,
        "verifier_report_sha256": hashlib.sha256(report_bytes).hexdigest(),
        "verified_table_rows": counts,
    }


def main(arguments: list[str]) -> int:
    if len(arguments) != 3:
        print("usage: gtfs_feed_provenance.py ZIP VERIFIED_REPORT RECEIPT", file=sys.stderr)
        return 2
    archive, report, output = map(Path, arguments)
    try:
        if any(aliases(output, item) for item in (archive, report)):
            raise ValueError("receipt must not replace the source ZIP or verified report")
        receipt = make_receipt(archive, report)
        write_atomic_json_report(output, receipt)
    except (OSError, ValueError, UnicodeError) as error:
        print(f"GTFS provenance error: {error}", file=sys.stderr)
        return 1
    print(json.dumps(receipt, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
