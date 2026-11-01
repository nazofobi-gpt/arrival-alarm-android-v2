#!/usr/bin/env python3
"""Varış V2 design-token parity and contrast gate (Python stdlib; no Android SDK).

Source-level gate only. Cannot certify screen layouts, TalkBack, 200% fonts or
actual phone rendering. Reads the canonical design-token JSON, not an in-test
copy of approved colors.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOKENS = ROOT / "docs/design/varis_design_tokens_v1.json"
THEME = ROOT / "app/src/main/java/com/nazofobi/arrivalalarm/ArrivalAlarmTheme.kt"

COLOR_ROLES = {
    "bgBase": "background", "surface": "surface",
    "surfaceSubtle": "surfaceVariant", "textPrimary": "onSurface",
    "textSecondary": "onSurfaceVariant", "borderSubtle": "outlineVariant",
    "brandPrimary": "primary", "brandContainer": "primaryContainer",
    "brandOnContainer": "onPrimaryContainer", "success": "secondary",
    "successContainer": "secondaryContainer", "warning": "tertiary",
    "warningContainer": "tertiaryContainer", "danger": "error",
    "dangerContainer": "errorContainer",
}
TYPE_ROLES = {
    "display": "displaySmall", "h1": "headlineLarge", "h2": "headlineSmall",
    "title": "titleMedium", "body": "bodyMedium", "meta": "bodySmall",
    "label": "labelLarge",
}
SHAPE_ROLES = {"s": "extraSmall", "m": "small", "l": "medium", "xl": "large"}
WEIGHTS = {"Normal": 400, "Medium": 500, "Bold": 700}


def luminosity(color: str) -> float:
    color = color.lstrip("#")
    assert re.fullmatch(r"[0-9a-fA-F]{6}", color), f"Bad color: {color}"
    parts = (int(color[i:i + 2], 16) / 255 for i in (0, 2, 4))
    linear = [p / 12.92 if p <= 0.04045 else ((p + .055) / 1.055) ** 2.4 for p in parts]
    return .2126 * linear[0] + .7152 * linear[1] + .0722 * linear[2]


def ratio(a: str, b: str) -> float:
    x, y = sorted((luminosity(a), luminosity(b)))
    return (y + .05) / (x + .05)


def extract_scheme(theme: str, name: str) -> dict[str, str]:
    match = re.search(r"internal val " + re.escape(name) + r" = (?:light|dark)ColorScheme\((.*?)\n\)", theme, re.S)
    if not match:
        raise ValueError(f"Missing {name} scheme")
    return dict(re.findall(r"\b(\w+)\s*=\s*Color\(0xFF([a-fA-F0-9]{6})\)", match.group(1)))


def verify(tokens: dict, theme: str) -> tuple[int, list[str]]:
    errors: list[str] = []
    total = 0

    def expect(description: str, actual: object, expected: object) -> None:
        nonlocal total
        total += 1
        if actual != expected:
            errors.append(f"{description}: expected {expected!r}, found {actual!r}")

    for mode in ("light", "dark"):
        scheme = extract_scheme(theme, "Matte" + mode.capitalize() + "Colors")
        for canonical, code in COLOR_ROLES.items():
            expect(f"{mode}.{canonical}->{code}",
                   "#" + scheme[code].upper() if code in scheme else None,
                   tokens["color"][mode][canonical].upper())
        for bg in ("primary", "primaryContainer", "secondary", "secondaryContainer",
                   "tertiary", "tertiaryContainer", "background", "surface",
                   "surfaceVariant", "error"):
            foreground = "on" + bg[:1].upper() + bg[1:]
            total += 1
            if not all(name in scheme for name in (bg, foreground)):
                errors.append(f"{mode}.{bg}/{foreground}: missing role")
            elif ratio(scheme[bg], scheme[foreground]) < 4.5:
                errors.append(f"{mode}.{bg}/{foreground}: below WCAG AA 4.5:1")
        total += 1
        if not all(name in scheme for name in ("outline", "surface")) or ratio(scheme["outline"], scheme["surface"]) < 3.0:
            errors.append(f"{mode}.outline/surface: below non-text 3:1")

    shapes_match = re.search(r"private val ArrivalShapes = Shapes\((.*?)\n\)", theme, re.S)
    shape_text = shapes_match.group(1) if shapes_match else ""
    shapes = {key: int(value) for key, value in re.findall(r"\b(\w+)\s*=\s*RoundedCornerShape\((\d+)\.dp\)", shape_text)}
    for canonical, code in SHAPE_ROLES.items():
        expect(f"radius.{canonical}->{code}", shapes.get(code), tokens["radius"][canonical])

    styles_match = re.search(r"internal val ArrivalTypography = Typography\((.*?)\n\)", theme, re.S)
    style_text = styles_match.group(1) if styles_match else ""
    styles = {}
    for style, block in re.findall(r"\b(\w+)\s*=\s*TextStyle\((.*?)\n    \)", style_text, re.S):
        styles[style] = {
            "size": int(s.group(1)) if (s := re.search(r"fontSize\s*=\s*(\d+)\.sp", block)) else None,
            "lineHeight": int(s.group(1)) if (s := re.search(r"lineHeight\s*=\s*(\d+)\.sp", block)) else None,
            "weight": WEIGHTS.get(s.group(1)) if (s := re.search(r"fontWeight\s*=\s*FontWeight\.(\w+)", block)) else None,
        }
    for canonical, style in TYPE_ROLES.items():
        for key in ("size", "lineHeight", "weight"):
            expect(f"typography.{canonical}->{style}.{key}",
                   styles.get(style, {}).get(key), tokens["typography"][canonical][key])
    return total, errors


def main() -> int:
    try:
        tokens = json.loads(TOKENS.read_text(encoding="utf-8"))
        theme = THEME.read_text(encoding="utf-8")
        total, errors = verify(tokens, theme)
    except (OSError, ValueError, KeyError, AssertionError) as error:
        print(f"Varış token gate failed to read/parse source: {error}", file=sys.stderr)
        return 2
    if errors:
        print("\n".join(errors), file=sys.stderr)
        print(f"Varış token gate FAIL: {len(errors)} issues across {total} checks", file=sys.stderr)
        return 1
    # Negative control: prove that this checker rejects a real source drift.
    changed = re.sub(r"primary = Color\(0xFF[0-9A-Fa-f]{6}\)",
                     "primary = Color(0xFFFFFFFF)", theme, count=1)
    if changed == theme or not verify(tokens, changed)[1]:
        print("Varış token gate FAIL: negative drift control not detected", file=sys.stderr)
        return 1
    print(f"Varış token source gate: {total}/{total} PASS; negative drift rejected; native/UI/device acceptance NOT tested")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
