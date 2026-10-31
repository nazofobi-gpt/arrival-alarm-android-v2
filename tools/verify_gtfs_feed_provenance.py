#!/usr/bin/env python3
"""Independently recheck a G-185 GTFS source-binding receipt against its inputs.

This verifies artifact integrity, not GTFS schema, operational freshness, or
physical Android/offline acceptance. The structural verifier must run first.
"""
import hashlib
import json
import re
import sys
from pathlib import Path

TABLES = ("stops.txt", "routes.txt", "trips.txt", "stop_times.txt")
EXPECTED_KEYS = {
    "schema", "acceptance", "source_zip_sha256", "source_zip_bytes",
    "verifier_report_sha256", "verified_table_rows",
}
MAX_JSON_BYTES = 2 * 1024 * 1024
CHUNK_BYTES = 1024 * 1024


def read_bounded(path: Path) -> bytes:
    before = path.stat()
    if before.st_size > MAX_JSON_BYTES:
        raise ValueError(f"evidence exceeds size limit: {path.name}")
    with path.open("rb") as stream:
        content = stream.read(MAX_JSON_BYTES + 1)
    after = path.stat()
    if len(content) > MAX_JSON_BYTES:
        raise ValueError(f"evidence exceeds size limit: {path.name}")
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) != (
        after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns
    ):
        raise ValueError(f"evidence changed during read: {path.name}")
    return content


def hash_archive(path: Path) -> tuple[str, int]:
    before = path.stat()
    digest = hashlib.sha256()
    with path.open("rb") as archive:
        for chunk in iter(lambda: archive.read(CHUNK_BYTES), b""):
            digest.update(chunk)
    after = path.stat()
    if (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns) != (
        after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns
    ):
        raise ValueError("GTFS source changed during verification")
    return digest.hexdigest(), before.st_size


def verify(archive: Path, report: Path, receipt_path: Path) -> None:
    receipt_bytes = read_bounded(receipt_path)
    report_bytes = read_bounded(report)
    receipt = json.loads(receipt_bytes.decode("utf-8"))
    report_data = json.loads(report_bytes.decode("utf-8"))
    if not isinstance(receipt, dict) or set(receipt) != EXPECTED_KEYS:
        raise ValueError("GTFS receipt fields do not match schema")
    if (receipt["schema"] != "GTFS_PREFLIGHT_SOURCE_BINDING_V1" or
            receipt["acceptance"] != "STRUCTURAL_PREFLIGHT_ONLY"):
        raise ValueError("GTFS receipt schema or acceptance mismatch")
    if not isinstance(report_data, dict):
        raise ValueError("GTFS structural report is not a JSON object")
    counts = {}
    for table in TABLES:
        entry = report_data.get(table)
        if (not isinstance(entry, dict) or type(entry.get("rows")) is not int or
                entry["rows"] <= 0 or not isinstance(entry.get("header"), list)):
            raise ValueError(f"invalid structural evidence: {table}")
        counts[table] = entry["rows"]
    if receipt["verified_table_rows"] != counts:
        raise ValueError("GTFS table counts differ from receipt")
    if (not isinstance(receipt["verifier_report_sha256"], str) or
            re.fullmatch(r"[0-9a-f]{64}", receipt["verifier_report_sha256"]) is None or
            receipt["verifier_report_sha256"] != hashlib.sha256(report_bytes).hexdigest()):
        raise ValueError("GTFS structural report checksum mismatch")
    if type(receipt["source_zip_bytes"]) is not int or receipt["source_zip_bytes"] <= 0:
        raise ValueError("GTFS source byte count is invalid")
    if (not isinstance(receipt["source_zip_sha256"], str) or
            re.fullmatch(r"[0-9a-f]{64}", receipt["source_zip_sha256"]) is None):
        raise ValueError("GTFS source checksum format invalid")
    sha256, size = hash_archive(archive)
    if sha256 != receipt["source_zip_sha256"] or size != receipt["source_zip_bytes"]:
        raise ValueError("GTFS ZIP checksum or size mismatch")
    # Guard against a report/receipt race as the large ZIP is being hashed.
    if read_bounded(report) != report_bytes or read_bounded(receipt_path) != receipt_bytes:
        raise ValueError("GTFS evidence changed during verification")


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print("usage: verify_gtfs_feed_provenance.py ZIP VERIFIED_REPORT RECEIPT", file=sys.stderr)
        return 2
    try:
        verify(*(Path(path) for path in argv))
    except (OSError, ValueError, UnicodeError) as error:
        print(f"GTFS provenance verification failed: {error}", file=sys.stderr)
        return 1
    print("GTFS provenance verification: PASS (structural evidence binding only)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
