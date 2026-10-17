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
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="com.nazofobi.arrivalalarm"
                      android:host="oauth" android:path="/callback" />
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

    def test_launcher_requires_main_and_launcher_in_same_filter(self):
        for original in (
            "android.intent.action.MAIN",
            "android.intent.category.LAUNCHER",
        ):
            with self.subTest(removed=original):
                bad = VALID.replace(original, original + "_REMOVED")
                self.assertIn(
                    "MainActivity must declare MAIN/LAUNCHER filter",
                    validate_manifest(bad),
                )
        split = VALID.replace(
            '<category android:name="android.intent.category.LAUNCHER" />',
            '</intent-filter><intent-filter>'
            '<category android:name="android.intent.category.LAUNCHER" />',
        )
        self.assertIn(
            "MainActivity must declare MAIN/LAUNCHER filter",
            validate_manifest(split),
        )

    def test_launcher_filter_cannot_require_uri_data(self):
        bad = VALID.replace(
            '<action android:name="android.intent.action.MAIN" />',
            '<action android:name="android.intent.action.MAIN" />'
            '<data android:scheme="unintended" />',
        )
        self.assertIn("MainActivity must declare MAIN/LAUNCHER filter", validate_manifest(bad))

    def test_oauth_filter_requires_action_and_categories_together(self):
        for original in (
            "android.intent.action.VIEW",
            "android.intent.category.DEFAULT",
            "android.intent.category.BROWSABLE",
        ):
            with self.subTest(removed=original):
                bad = VALID.replace(original, original + "_REMOVED")
                self.assertIn(
                    "MainActivity must accept the OAuth redirect URI",
                    validate_manifest(bad),
                )
        split = VALID.replace(
            '<category android:name="android.intent.category.BROWSABLE" />',
            '</intent-filter><intent-filter>'
            '<category android:name="android.intent.category.BROWSABLE" />',
        )
        self.assertIn("MainActivity must accept the OAuth redirect URI", validate_manifest(split))

    def test_oauth_filter_requires_exact_redirect_coordinates(self):
        for original, replacement in (
            ('android:scheme="com.nazofobi.arrivalalarm"', 'android:scheme="other.app"'),
            ('android:host="oauth"', 'android:host="other"'),
            ('android:path="/callback"', 'android:path="/unexpected"'),
        ):
            with self.subTest(modified=original):
                bad = VALID.replace(original, replacement)
                self.assertIn(
                    "MainActivity must accept the OAuth redirect URI",
                    validate_manifest(bad),
                )

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
