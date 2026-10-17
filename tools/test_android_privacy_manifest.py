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
