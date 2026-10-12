"""Regression checks for the Android translation/format safety gate."""

from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_locale_resources import placeholders, validate  # noqa: E402


class LocaleResourceContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write("values", '<string name="arrival">Stop %1$s in %2$d min</string>')
        self.write("values-de", '<string name="arrival">In %2$d Min. zu %1$s</string>')
        self.write("values-en", '<string name="arrival">Stop %1$s in %2$d min</string>')

    def write(self, directory: str, body: str, filename="strings.xml"):
        dest = self.root / directory
        dest.mkdir(parents=True, exist_ok=True)
        (dest / filename).write_text(
            '<?xml version="1.0" encoding="utf-8"?><resources>'
            + body + "</resources>",
            encoding="utf-8",
        )

    def test_reordered_numbered_placeholders_pass(self):
        self.assertEqual([], validate(self.root))

    def test_missing_locale_key_is_rejected(self):
        self.write("values-de", '<string name="other">Nur andere</string>')
        errors = validate(self.root)
        self.assertTrue(any("missing translation for arrival" in e for e in errors))

    def test_wrong_format_type_is_rejected(self):
        self.write("values-de", '<string name="arrival">In %2$s Min. zu %1$s</string>')
        self.assertTrue(any("format arguments differ" in e for e in validate(self.root)))

    def test_nontranslatable_source_may_be_omitted(self):
        self.write(
            "values",
            '<string name="arrival">Stop %1$s in %2$d min</string>'
            '<string name="machine_code" translatable="false">X-123</string>',
        )
        self.assertEqual([], validate(self.root))

    def test_formatted_false_does_not_validate_literal_percent_text(self):
        self.write(
            "values",
            '<string name="arrival" formatted="false">100% safe</string>',
        )
        self.write(
            "values-de",
            '<string name="arrival" formatted="false">100% sicher</string>',
        )
        self.write(
            "values-en",
            '<string name="arrival" formatted="false">100% safe</string>',
        )
        self.assertEqual([], validate(self.root))

    def test_unrecognized_locale_key_is_rejected(self):
        self.write(
            "values-en",
            '<string name="arrival">Stop %1$s in %2$d min</string>'
            '<string name="obsolete">Old</string>',
        )
        self.assertTrue(any("unrecognized translation key obsolete" in e for e in validate(self.root)))

    def test_duplicate_default_key_across_files_is_rejected(self):
        self.write("values", '<string name="arrival">Duplicate</string>', filename="strings_more.xml")
        self.assertTrue(any("duplicate default key" in e for e in validate(self.root)))

    def test_extra_locale_file_with_unknown_key_is_rejected(self):
        # A new localized XML file must not silently evade the parity gate.
        self.write("values-de", '<string name="orphan">Zusatz</string>', "strings_extra.xml")
        self.assertTrue(
            any("unrecognized translation key orphan" in e for e in validate(self.root))
        )

    def test_extra_locale_file_with_duplicate_key_is_rejected(self):
        self.write(
            "values-en",
            '<string name="arrival">Duplicate %1$s %2$d</string>',
            "strings_extra.xml",
        )
        self.assertTrue(
            any("duplicate en key across files: arrival" in e for e in validate(self.root))
        )

    def test_missing_locale_resource_file_is_reported_by_key(self):
        (self.root / "values-de" / "strings.xml").unlink()
        self.assertTrue(
            any("missing translation for arrival" in e for e in validate(self.root))
        )

    def test_escaped_percent_has_no_format_argument(self):
        self.assertEqual({}, placeholders("100%% complete"))

    def test_whitespace_only_translation_is_rejected(self):
        self.write("values-de", '<string name="arrival">   </string>')
        self.assertTrue(any("empty translation" in e for e in validate(self.root)))


    def test_java_date_time_suffix_mismatch_is_rejected(self):
        self.write("values", '<string name="arrival">Varış %1$tH:%1$tM</string>')
        self.write("values-de", '<string name="arrival">Ankunft %1$tH:%1$tY</string>')
        self.write("values-en", '<string name="arrival">Arrival %1$tH:%1$tM</string>')
        self.assertTrue(any("format arguments differ" in e for e in validate(self.root)))

    def test_reordered_java_date_time_suffixes_pass(self):
        self.write("values", '<string name="arrival">%1$tY-%1$tm-%1$td</string>')
        self.write("values-de", '<string name="arrival">%1$td.%1$tm.%1$tY</string>')
        self.write("values-en", '<string name="arrival">%1$tY/%1$tm/%1$td</string>')
        self.assertEqual([], validate(self.root))


if __name__ == "__main__":
    unittest.main()
