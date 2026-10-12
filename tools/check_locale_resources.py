#!/usr/bin/env python3
"""Fail CI when Android string translations drift or format arguments become unsafe."""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

LOCALES = ("de", "en")
PLACEHOLDER = re.compile(r"%(?:([1-9][0-9]*)\$)?[-#+0,(]*[0-9]*(?:\.[0-9]+)?([A-Za-z%])")


@dataclass(frozen=True)
class StringValue:
    text: str
    formatted: bool
    translatable: bool


def placeholders(value: str) -> Counter[tuple[int, str]]:
    """Compare argument positions and conversion types, not language word order."""
    found: Counter[tuple[int, str]] = Counter()
    next_implicit = 1
    for match in PLACEHOLDER.finditer(value):
        conversion = match.group(2)
        if conversion in ("%", "n"):
            continue
        position = int(match.group(1)) if match.group(1) else next_implicit
        if not match.group(1):
            next_implicit += 1
        found[position, conversion.lower()] += 1
    return found


def read_strings(path: Path) -> tuple[dict[str, StringValue], list[str]]:
    errors: list[str] = []
    entries: dict[str, StringValue] = {}
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        return {}, [f"{path}: cannot read XML: {exc}"]
    if root.tag != "resources":
        return {}, [f"{path}: expected <resources> root"]

    for node in root.findall("string"):
        name = node.get("name")
        if not name:
            errors.append(f"{path}: unnamed <string>")
            continue
        if name in entries:
            errors.append(f"{path}: duplicate key {name}")
            continue
        entries[name] = StringValue(
            text="".join(node.itertext()),
            formatted=node.get("formatted", "true").lower() != "false",
            translatable=node.get("translatable", "true").lower() != "false",
        )
    return entries, errors


def validate(root: Path) -> list[str]:
    """Compare all matching strings*.xml files in values, values-de and values-en."""
    errors: list[str] = []
    defaults: dict[str, StringValue] = {}
    default_files = sorted((root / "values").glob("strings*.xml"))
    if not default_files:
        return [f"{root / 'values'}: no strings*.xml resources found"]

    for file in default_files:
        entries, parse_errors = read_strings(file)
        errors.extend(parse_errors)
        for name, entry in entries.items():
            if name in defaults:
                errors.append(f"{file}: duplicate default key across resource files: {name}")
            else:
                defaults[name] = entry

    for locale in LOCALES:
        directory = root / f"values-{locale}"
        localized: dict[str, StringValue] = {}
        # Scan all localized files, not only filenames present in the default
        # locale. Otherwise an extra strings_*.xml can bypass parity checks.
        for file in sorted(directory.glob("strings*.xml")):
            entries, parse_errors = read_strings(file)
            errors.extend(parse_errors)
            for key, entry in entries.items():
                if key in localized:
                    errors.append(f"{file}: duplicate {locale} key across files: {key}")
                else:
                    localized[key] = entry

        expected = {key for key, value in defaults.items() if value.translatable}
        missing = expected - set(localized)
        unexpected = set(localized) - set(defaults)
        for key in sorted(missing):
            errors.append(f"values-{locale}: missing translation for {key}")
        for key in sorted(unexpected):
            errors.append(f"values-{locale}: unrecognized translation key {key}")

        for key in sorted(expected & set(localized)):
            source = defaults[key]
            target = localized[key]
            if source.formatted != target.formatted:
                errors.append(f"values-{locale}: formatted flag differs for {key}")
            if source.text.strip() and not target.text.strip():
                errors.append(f"values-{locale}: empty translation for {key}")
            if source.formatted and target.formatted:
                if placeholders(source.text) != placeholders(target.text):
                    errors.append(
                        f"values-{locale}: format arguments differ for {key}: "
                        f"base={dict(placeholders(source.text))}, "
                        f"locale={dict(placeholders(target.text))}"
                    )
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--res-dir",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "app/src/main/res",
        help="Android app/src/main/res directory",
    )
    args = parser.parse_args()
    errors = validate(args.res_dir)
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        print(f"Android locale contract FAILED: {len(errors)} errors", file=sys.stderr)
        return 1
    print("Android locale contract PASS: default, German and English strings are consistent")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
