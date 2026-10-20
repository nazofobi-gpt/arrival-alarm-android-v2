"""Regression tests for the G-191 Android manifest policy gate."""
import unittest

from check_android_privacy_manifest import validate_manifest


VALID = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <application android:allowBackup="false" android:usesCleartextTraffic="false">
        <service android:name=".ActiveJourneyService"
                 android:exported="false"
                 android:foregroundServiceType="location" />
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""


class AndroidManifestPrivacyPolicyTest(unittest.TestCase):
    def test_valid_minimal_manifest_passes(self):
        self.assertEqual([], validate_manifest(VALID))

    def test_each_required_permission_is_checked(self):
        for permission in (
            "INTERNET", "ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION",
            "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_LOCATION",
            "POST_NOTIFICATIONS",
        ):
            with self.subTest(permission=permission):
                bad = VALID.replace(
                    f'    <uses-permission android:name="android.permission.{permission}" />\n', ''
                )
                self.assertTrue(
                    any("missing required permission" in e for e in validate_manifest(bad))
                )

    def test_required_permissions_must_remain_available_on_api36(self):
        for permission in ("INTERNET", "ACCESS_FINE_LOCATION", "FOREGROUND_SERVICE_LOCATION"):
            for max_sdk in ("35", "invalid"):
                with self.subTest(permission=permission, max_sdk=max_sdk):
                    original = f'android:name="android.permission.{permission}" />'
                    limited = original.replace(
                        " />", f' android:maxSdkVersion="{max_sdk}" />'
                    )
                    errors = validate_manifest(VALID.replace(original, limited))
                    self.assertIn(
                        f"required permission unavailable on API 36: android.permission.{permission}",
                        errors,
                    )

        original = 'android:name="android.permission.INTERNET" />'
        allowed = original.replace(" />", ' android:maxSdkVersion="36" />')
        self.assertEqual([], validate_manifest(VALID.replace(original, allowed)))
        duplicate = VALID.replace(original, original.replace(
            " />", ' android:maxSdkVersion="35" />'
        ) + "\n    <uses-permission " + original)
        self.assertEqual([], validate_manifest(duplicate))

    def test_background_location_is_rejected(self):
        bad = VALID.replace(
            "<application",
            '<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />\n<application',
        )
        self.assertTrue(
            any("unnecessary background permission" in e for e in validate_manifest(bad))
        )

    def test_exported_tracking_service_is_rejected(self):
        bad = VALID.replace('android:exported="false"', 'android:exported="true"')
        self.assertTrue(any("must not be exported" in e for e in validate_manifest(bad)))

    def test_disabled_application_cannot_pass_tracking_manifest_gate(self):
        for enabled in ("false", "@bool/tracking_enabled"):
            with self.subTest(enabled=enabled):
                bad = VALID.replace(
                    "<application ",
                    f'<application android:enabled="{enabled}" ',
                )
                self.assertIn(
                    "application must be enabled for tracking",
                    validate_manifest(bad),
                )

    def test_disabled_tracking_service_cannot_pass_manifest_gate(self):
        for enabled in ("false", "@bool/tracking_enabled"):
            with self.subTest(enabled=enabled):
                bad = VALID.replace(
                    'android:exported="false"',
                    f'android:enabled="{enabled}" android:exported="false"',
                )
                self.assertIn(
                    "ActiveJourneyService must be enabled",
                    validate_manifest(bad),
                )

    def test_explicitly_enabled_application_and_service_pass(self):
        enabled = VALID.replace(
            "<application ", '<application android:enabled="true" ',
        ).replace(
            'android:exported="false"',
            'android:enabled="true" android:exported="false"',
        )
        self.assertEqual([], validate_manifest(enabled))

    def test_disabled_main_activity_cannot_pass_manifest_gate(self):
        for enabled in ("false", "@bool/main_activity_enabled"):
            with self.subTest(enabled=enabled):
                bad = VALID.replace(
                    'android:name=".MainActivity" android:exported="true"',
                    f'android:name=".MainActivity" android:exported="true" '
                    f'android:enabled="{enabled}"',
                )
                self.assertIn("MainActivity must be enabled", validate_manifest(bad))

    def test_unexported_main_activity_cannot_pass_manifest_gate(self):
        for replacement in ('android:exported="false"', ''):
            with self.subTest(exported=replacement):
                bad = VALID.replace(
                    'android:name=".MainActivity" android:exported="true"',
                    f'android:name=".MainActivity" {replacement}',
                )
                self.assertIn(
                    "MainActivity must be exported for launcher and OAuth callbacks",
                    validate_manifest(bad),
                )

    def test_main_activity_requires_same_filter_main_and_launcher(self):
        main_action = '<action android:name="android.intent.action.MAIN" />'
        launcher_category = '<category android:name="android.intent.category.LAUNCHER" />'
        split_filters = VALID.replace(
            launcher_category,
            '</intent-filter>\n            <intent-filter>\n                ' + launcher_category,
        )
        for label, malformed in (
            ("missing_main_action", VALID.replace(main_action, "")),
            ("missing_launcher_category", VALID.replace(launcher_category, "")),
            ("split_across_filters", split_filters),
        ):
            with self.subTest(case=label):
                self.assertIn(
                    "MainActivity must have a MAIN/LAUNCHER intent filter",
                    validate_manifest(malformed),
                )
        self.assertEqual([], validate_manifest(VALID))

    def test_missing_or_duplicate_main_activity_fails_closed(self):
        missing = VALID.replace('android:name=".MainActivity"', 'android:name=".OtherActivity"')
        self.assertIn("exactly one MainActivity must be declared", validate_manifest(missing))
        activity = '<activity android:name=".MainActivity" android:exported="true" />'
        duplicate = VALID.replace("</application>", activity + "\n    </application>")
        self.assertIn("exactly one MainActivity must be declared", validate_manifest(duplicate))

    def test_fully_qualified_main_activity_is_supported(self):
        valid = VALID.replace(
            'android:name=".MainActivity"',
            'android:name="com.nazofobi.arrivalalarm.MainActivity"',
        )
        self.assertEqual([], validate_manifest(valid))

    def test_missing_or_extra_foreground_service_types_are_rejected(self):
        for value in ("dataSync", "location|dataSync", ""):
            with self.subTest(value=value):
                bad = VALID.replace('android:foregroundServiceType="location"',
                                    f'android:foregroundServiceType="{value}"')
                self.assertTrue(
                    any("only the location FGS type" in e for e in validate_manifest(bad))
                )

    def test_insecure_backup_and_cleartext_are_rejected(self):
        for original in ('android:allowBackup="false"',
                         'android:usesCleartextTraffic="false"'):
            with self.subTest(attribute=original):
                bad = VALID.replace(original, original.replace('"false"', '"true"'))
                self.assertTrue(validate_manifest(bad))

    def test_missing_tracking_service_is_rejected(self):
        bad = VALID.replace('android:name=".ActiveJourneyService"',
                            'android:name=".OtherService"')
        self.assertIn("exactly one ActiveJourneyService must be declared",
                      validate_manifest(bad))

    def test_foreign_package_service_cannot_satisfy_tracking_declaration(self):
        for name in (
            "com.other.ActiveJourneyService",
            "com.nazofobi.arrivalalarm.other.ActiveJourneyService",
        ):
            with self.subTest(name=name):
                bad = VALID.replace('android:name=".ActiveJourneyService"',
                                    f'android:name="{name}"')
                self.assertIn("exactly one ActiveJourneyService must be declared",
                              validate_manifest(bad))

    def test_valid_fully_qualified_tracking_service_remains_supported(self):
        valid = VALID.replace('android:name=".ActiveJourneyService"',
                              'android:name="com.nazofobi.arrivalalarm.ActiveJourneyService"')
        self.assertEqual([], validate_manifest(valid))

    def test_duplicate_tracking_service_is_rejected(self):
        service = """<service android:name=".ActiveJourneyService"
                 android:exported="false"
                 android:foregroundServiceType="location" />"""
        bad = VALID.replace(service, service + service)
        self.assertIn("exactly one ActiveJourneyService must be declared",
                      validate_manifest(bad))

    def test_malformed_xml_and_missing_application_fail_closed(self):
        self.assertTrue(validate_manifest("<manifest"))
        self.assertIn("missing <application>",
                      validate_manifest(VALID.replace("<application", "<notapplication")
                                                     .replace("</application>", "</notapplication>")))


if __name__ == "__main__":
    unittest.main()
