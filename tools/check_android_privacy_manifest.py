#!/usr/bin/env python3
"""Fail-closed static Android location/privacy manifest checks for G-191.

The check is intentionally limited to manifest properties we can prove locally.
It does not substitute for runtime permission, background-start, device or Play QA.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
REQUIRED_PERMISSIONS = {
    "android.permission.INTERNET",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_LOCATION",
    "android.permission.POST_NOTIFICATIONS",
}
FORBIDDEN_PERMISSIONS = {
    "android.permission.ACCESS_BACKGROUND_LOCATION",
}


def validate_manifest(source: str) -> list[str]:
    try:
        root = ET.fromstring(source)
    except ET.ParseError as exc:
        return [f"manifest XML cannot be parsed: {exc}"]

    errors: list[str] = []
    if root.tag != "manifest":
        return ["root element must be <manifest>"]

    permissions = [
        element.get(ANDROID + "name", "")
        for element in root
        if element.tag in {"uses-permission", "uses-permission-sdk-23"}
    ]
    missing = sorted(REQUIRED_PERMISSIONS - set(permissions))
    for name in missing:
        errors.append(f"missing required permission: {name}")

    # A declared permission may be disabled on the target API by maxSdkVersion.
    def available_on_api_36(element: ET.Element) -> bool:
        max_sdk = element.get(ANDROID + "maxSdkVersion")
        return max_sdk is None or (max_sdk.isdecimal() and int(max_sdk) >= 36)

    for name in sorted(REQUIRED_PERMISSIONS.intersection(permissions)):
        declarations = [
            element for element in root
            if element.tag in {"uses-permission", "uses-permission-sdk-23"}
            and element.get(ANDROID + "name") == name
        ]
        if not any(available_on_api_36(element) for element in declarations):
            errors.append(f"required permission unavailable on API 36: {name}")
    for name in sorted(FORBIDDEN_PERMISSIONS.intersection(permissions)):
        errors.append(f"unnecessary background permission: {name}")

    app = root.find("application")
    if app is None:
        return errors + ["missing <application>"]
    if app.get(ANDROID + "allowBackup") != "false":
        errors.append("android:allowBackup must explicitly be false")
    if app.get(ANDROID + "usesCleartextTraffic") != "false":
        errors.append("android:usesCleartextTraffic must explicitly be false")
    # Disabled application components cannot run the location foreground service.
    if app.get(ANDROID + "enabled") not in (None, "true"):
        errors.append("application must be enabled for tracking")

    # A foreign package with the same class basename is not this app's service.
    expected_service_names = {
        ".ActiveJourneyService",
        "ActiveJourneyService",
        "com.nazofobi.arrivalalarm.ActiveJourneyService",
    }
    services = [
        component
        for component in app.findall("service")
        if component.get(ANDROID + "name") in expected_service_names
    ]
    if len(services) != 1:
        errors.append("exactly one ActiveJourneyService must be declared")
    else:
        service = services[0]
        if service.get(ANDROID + "exported") != "false":
            errors.append("ActiveJourneyService must not be exported")
        if service.get(ANDROID + "enabled") not in (None, "true"):
            errors.append("ActiveJourneyService must be enabled")
        types = {
            part.strip()
            for part in (service.get(ANDROID + "foregroundServiceType") or "").split("|")
            if part.strip()
        }
        if types != {"location"}:
            errors.append("ActiveJourneyService must declare only the location FGS type")

    # A disabled/non-exported launcher prevents users from opening the app and
    # completing the OAuth callback even though tracking service checks pass.
    expected_activity_names = {
        ".MainActivity",
        "MainActivity",
        "com.nazofobi.arrivalalarm.MainActivity",
    }
    activities = [
        component for component in app.findall("activity")
        if component.get(ANDROID + "name") in expected_activity_names
    ]
    if len(activities) != 1:
        errors.append("exactly one MainActivity must be declared")
    else:
        activity = activities[0]
        if activity.get(ANDROID + "enabled") not in (None, "true"):
            errors.append("MainActivity must be enabled")
        if activity.get(ANDROID + "exported") != "true":
            errors.append("MainActivity must be exported for launcher and OAuth callbacks")
        # MAIN and LAUNCHER must belong to the same intent-filter. Merely
        # declaring an exported MainActivity does not make it launchable.
        has_launcher_filter = any(
            any(action.get(ANDROID + "name") == "android.intent.action.MAIN"
                for action in intent_filter.findall("action"))
            and any(category.get(ANDROID + "name") == "android.intent.category.LAUNCHER"
                for category in intent_filter.findall("category"))
            for intent_filter in activity.findall("intent-filter")
        )
        if not has_launcher_filter:
            errors.append("MainActivity must have a MAIN/LAUNCHER intent filter")

    return errors


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="AndroidManifest.xml to validate")
    args = parser.parse_args(argv)
    try:
        errors = validate_manifest(args.manifest.read_text(encoding="utf-8"))
    except OSError as exc:
        print(f"Manifest could not be read: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(f"G-191 manifest policy FAIL: {error}", file=sys.stderr)
        return 1
    print("G-191 manifest static policy PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
