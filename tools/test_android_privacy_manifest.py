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
