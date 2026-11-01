#!/usr/bin/env python3
"""Preserve the full API 36 connected-test evidence before viewport-only reruns."""

from __future__ import annotations

import json
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def snapshot_reports(junit_source: Path, html_source: Path, target: Path) -> dict[str, int]:
    if not junit_source.is_dir() or not html_source.is_dir():
        raise ValueError("API36 connected JUnit or HTML source directory is missing")
    if target.exists():
        raise ValueError("Refusing to overwrite an existing API36 full-suite snapshot")

    xml_paths = sorted(junit_source.rglob("TEST-*.xml"))
    counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    for xml_path in xml_paths:
        root = ET.parse(xml_path).getroot()
        suites = [root] if root.tag == "testsuite" else list(root.findall("./testsuite"))
        if not suites:
            raise ValueError(f"No JUnit testsuite in {xml_path}")
        for suite in suites:
            for key in counts:
                counts[key] += int(suite.get(key, "0"))
            if suite.findall(".//failure") or suite.findall(".//error"):
                raise ValueError(f"JUnit contains failing testcase in {xml_path}")

    has_html = any(html_source.rglob("*.html"))
    if counts["tests"] < 100 or counts["failures"] or counts["errors"] or not has_html:
        raise ValueError(f"Incomplete API36 full regression snapshot: {counts}; html={has_html}")

    target.mkdir(parents=True)
    try:
        shutil.copytree(junit_source, target / "junit")
        shutil.copytree(html_source, target / "html")
        summary = {"xml_files": len(xml_paths), **counts}
        (target / "report-summary.json").write_text(
            json.dumps(summary, sort_keys=True) + "\n", encoding="utf-8"
        )
    except Exception:
        shutil.rmtree(target)
        raise
    return summary


def main() -> int:
    if len(sys.argv) != 4:
        print("Usage: snapshot_api36_instrumentation_reports.py JUNIT_DIR HTML_DIR SNAPSHOT_DIR", file=sys.stderr)
        return 2
    try:
        summary = snapshot_reports(*(Path(arg) for arg in sys.argv[1:]))
    except (ValueError, OSError, ET.ParseError) as exc:
        print(f"API36 snapshot rejected: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(summary, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
