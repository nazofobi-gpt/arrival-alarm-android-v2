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
PLACEHOLDER = re.compile(r"%(?:([1-9][0-9]*)\$)?([-#+ 0,(<]*)[0-9]*(?:\.[0-9]+)?([tT][A-Za-z]|[A-Za-z%])")


@dataclass(frozen=True)
class StringValue:
    text: str
    formatted: bool
    translatable: bool


VALID_CONVERSIONS = frozenset("bBhHsScCdoxXeEfgGaA%n")
# Java Formatter's documented date/time suffixes (case-sensitive).
VALID_DATETIME_SUFFIXES = frozenset("HIk" + "lMSLNpzZsQBbhAaCYyjmdeRTrDFc")


def placeholders(value: str) -> Counter[tuple[int, str]]:
    """Validate each percent token and compare argument types and positions."""
    found: Counter[tuple[int, str]] = Counter()
    next_implicit = 1
    previous_position: int | None = None
    cursor = 0
    while True:
        start = value.find("%", cursor)
        if start < 0:
            break
        # finditer() silently skips malformed tokens such as %1$ or a bare %.
        # Runtime String.format does not, so fail the locale gate instead.
        match = PLACEHOLDER.match(value, start)
        if match is None:
            raise ValueError(f"malformed format argument at offset {start}")
        conversion = match.group(3)
        if len(conversion) == 2:
            if conversion[1] not in VALID_DATETIME_SUFFIXES:
                raise ValueError(f"unsupported date/time conversion %{conversion} at offset {start}")
        elif conversion not in VALID_CONVERSIONS:
            raise ValueError(f"unsupported format conversion %{conversion} at offset {start}")
        if conversion.lower() == "s" and "+" in match.group(2):
            raise ValueError(f"invalid format flag '+' for %{conversion} at offset {start}")
        cursor = match.end()
        if conversion in ("%", "n"):
            # Java Formatter accepts no reused argument for %% or %n.
            # Even after an ordinary argument, %<% and %<n are invalid.
            if "<" in match.group(2):
                raise ValueError(
                    f"relative flag invalid for no-argument conversion %{conversion} at offset {start}"
                )
            if conversion == "n":
                # Java Formatter permits an argument index but no width,
                # precision or flags on the line-separator conversion.
                token = match.group(0)[1:]
                if match.group(1):
                    token = token[len(match.group(1)) + 1:]
                if token != "n":
                    raise ValueError(f"invalid width, precision or flags for %n at offset {start}")
            continue
        # The '<' flag reuses the preceding argument, without advancing implicit numbering.
        if "<" in match.group(2):
            if previous_position is None:
                raise ValueError("relative format argument has no previous argument")
            position = previous_position
        else:
            position = int(match.group(1)) if match.group(1) else next_implicit
            if not match.group(1):
                next_implicit += 1
        previous_position = position
        # Date/time suffix case is meaningful (e.g. %tH versus %tM).
        signature = conversion if len(conversion) == 2 else conversion.lower()
        found[position, signature] += 1
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
        # Android marks translatable="false" values as locale-independent.
        # A localized override (including endpoints or machine-readable IDs)
        # must not silently bypass that contract.
        nontranslatable_overrides = set(localized) & (set(defaults) - expected)
        for key in sorted(missing):
            errors.append(f"values-{locale}: missing translation for {key}")
        for key in sorted(unexpected):
            errors.append(f"values-{locale}: unrecognized translation key {key}")
        for key in sorted(nontranslatable_overrides):
            errors.append(f"values-{locale}: nontranslatable key must not be localized: {key}")

        for key in sorted(expected & set(localized)):
            source = defaults[key]
            target = localized[key]
            if source.formatted != target.formatted:
                errors.append(f"values-{locale}: formatted flag differs for {key}")
            if source.translatable != target.translatable:
                errors.append(f"values-{locale}: translatable flag differs for {key}")
            if source.text.strip() and not target.text.strip():
                errors.append(f"values-{locale}: empty translation for {key}")
            if source.formatted and target.formatted:
                try:
                    base_arguments = placeholders(source.text)
                    locale_arguments = placeholders(target.text)
                except ValueError as exc:
                    errors.append(f"values-{locale}: invalid format for {key}: {exc}")
                    continue
                if base_arguments != locale_arguments:
                    errors.append(
                        f"values-{locale}: format arguments differ for {key}: "
                        f"base={dict(base_arguments)}, locale={dict(locale_arguments)}"
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
