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

    def test_string_plus_flag_is_invalid_even_when_types_match(self):
        # Java Formatter throws FormatFlagsConversionMismatchException for %+s.
        # The translation gate must reject it instead of ignoring the flag.
        self.write("values-de", '<string name="arrival">Stop %+s in %2$d min</string>')
        self.assertTrue(any("invalid format" in e for e in validate(self.root)))


    def test_invalid_java_string_format_flags_are_rejected(self):
        # Confirmed against Java 21 String.format: these raise
        # FormatFlagsConversionMismatchException for a String argument.
        for token in ("%0s", "%05s", "% s", "%,s", "%(s", "%+s", "%#s", "%#S", "%1$#10s"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid string format flags"):
                    placeholders(token)

    def test_java_character_conversion_rejects_invalid_flags_precision_and_width(self):
        # Independently reproduced with Java 21 String.format(Locale.ROOT, ...).
        for token in ("%+c", "%#C", "%0c", "%,c", "%(c", "% c", "%1$05c"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid character format flags"):
                    placeholders(token)
        for token in ("%.2c", "%5.2c", "%1$.2C"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid character format precision"):
                    placeholders(token)
        for token in ("%-c", "%1$-C"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "missing character format width"):
                    placeholders(token)
        for token in ("%c", "%1$C", "%5c", "%1$-5c"):
            with self.subTest(token=token):
                self.assertEqual({(1, "c"): 1}, dict(placeholders(token)))

    def test_invalid_character_conversion_fails_even_when_all_locales_agree(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %1$#c</string>')
        self.assertTrue(any("invalid format for arrival" in e for e in validate(self.root)))

    def test_boolean_and_hash_java_formatter_flags_are_checked(self):
        # Reproduced with Java 21: unsupported flags throw
        # FormatFlagsConversionMismatchException and bare '-' needs width.
        for token in ("%#b", "%+b", "%0h", "%,h", "%(b"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid boolean/hash format flags"):
                    placeholders(token)
        for token in ("%-b", "%1$-h"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "missing boolean/hash format width"):
                    placeholders(token)
        for token in ("%b", "%1$-5b", "%1$-8h", "%.2h", "%5.2b"):
            with self.subTest(token=token):
                self.assertEqual({(1, token[-1].lower()): 1}, dict(placeholders(token)))

    def test_boolean_hash_invalid_flags_fail_when_all_locales_agree(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Ready %#b</string>')
        self.assertTrue(any("invalid format for arrival" in e for e in validate(self.root)))

    def test_duplicate_java_formatter_flags_are_rejected(self):
        # Verified on Java 21: these raise DuplicateFormatFlagsException.
        for token in ("%--5s", "%++d", "%1$--10s"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "duplicate format flags"):
                    placeholders(token)

    def test_matching_invalid_sharp_string_flags_fail_locale_gate(self):
        # A string with the # flag throws FormatFlagsConversionMismatchException
        # even when each translation contains the same placeholder.
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %1$#10s</string>')
        self.assertTrue(
            any("invalid format for arrival" in error for error in validate(self.root))
        )

    def test_integral_conversions_reject_precision_but_keep_valid_width(self):
        # Independently confirmed with Java 21 String.format(Locale.ROOT).
        for token in ("%.2d", "%1$.2d", "%5.2d", "%1$.2o", "%1$5.2x", "%1$5.2X"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid integer format precision"):
                    placeholders(token)
        for token in ("%05d", "%1$-5d", "%#8x", "%1$08X"):
            with self.subTest(token=token):
                self.assertEqual(1, sum(placeholders(token).values()))

    def test_matching_invalid_integer_precision_fails_locale_gate(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %1$.2d</string>')
        self.assertTrue(any("invalid format for arrival" in e for e in validate(self.root)))

    def test_java_formatter_rejects_invalid_decimal_flags(self):
        # Reproduced with Java 21 String.format(Locale.ROOT, token, 123).
        for token in ("%#d", "%1$#8d", "%+ d", "%-05d"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid decimal format flags"):
                    placeholders(token)

    def test_java_formatter_decimal_padding_requires_width(self):
        for token in ("%0d", "%1$0d", "%-d", "%0.2d"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "missing decimal format width"):
                    placeholders(token)

    def test_java_formatter_valid_decimal_flags_remain_supported(self):
        for token in ("%d", "%1$05d", "%-5d", "%+d", "% d", "%,d", "%(d"):
            with self.subTest(token=token):
                self.assertEqual({(1, "d"): 1}, dict(placeholders(token)))
        self.assertEqual({(1, "x"): 1}, dict(placeholders("%#x")))

    def test_java_formatter_rejects_invalid_octal_hex_flags_and_width(self):
        for token in ("%+o", "% o", "%,o", "%(o", "%+x", "% x", "%,x", "%(x", "%0o", "%-o", "%0x", "%-x", "%-05o", "%-05x", "%#-05x"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(
                    ValueError,
                    r"(invalid octal/hex format flags|missing octal/hex format width)",
                ):
                    placeholders(token)
        for token in ("%#o", "%#x", "%05x", "%-5x", "%1$-5o", "%#08X"):
            with self.subTest(token=token):
                self.assertEqual(1, sum(placeholders(token).values()))

    def test_matching_invalid_octal_hex_flags_fail_locale_gate(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">%+x</string>')
        self.assertTrue(any("invalid format for arrival" in error for error in validate(self.root)))

    def test_invalid_decimal_flags_fail_even_when_all_locales_agree(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Arrives in %1$#8d minutes</string>')
        self.assertTrue(any("invalid format for arrival" in error for error in validate(self.root)))

        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %1$--10s</string>')
        self.assertTrue(
            any("duplicate format flags" in error for error in validate(self.root))
        )

    def test_valid_java_string_padding_remains_allowed(self):
        self.assertEqual({(1, "s"): 1}, dict(placeholders("%1$-10s")))
        self.assertEqual({(1, "s"): 1}, dict(placeholders("%10s")))

    def test_invalid_string_flags_in_matching_locales_are_still_rejected(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %0s</string>')
        self.assertTrue(
            any("invalid format for arrival" in error for error in validate(self.root))
        )

    def test_datetime_conversion_suffix_mismatch_is_rejected(self):
        # Both strings have a date/time argument, but H (hour) and M (minute)
        # cannot be substituted for one another by a translator.
        self.write("values", '<string name="arrival">At %1$tH:%1$tM</string>')
        self.write("values-de", '<string name="arrival">Um %1$tH:%1$tS</string>')
        self.write("values-en", '<string name="arrival">At %1$tH:%1$tM</string>')
        self.assertTrue(
            any("format arguments differ" in e for e in validate(self.root))
        )

    def test_datetime_arguments_can_be_reordered_between_locales(self):
        self.write("values", '<string name="arrival">At %1$tH:%1$tM on %2$td</string>')
        self.write("values-de", '<string name="arrival">Am %2$td um %1$tH:%1$tM</string>')
        self.write("values-en", '<string name="arrival">At %1$tH:%1$tM on %2$td</string>')
        self.assertEqual([], validate(self.root))

    def test_datetime_conversion_case_must_not_change_silently(self):
        self.write("values", '<string name="arrival">At %1$tB</string>')
        self.write("values-de", '<string name="arrival">Am %1$TB</string>')
        self.write("values-en", '<string name="arrival">At %1$tB</string>')
        self.assertTrue(
            any("format arguments differ" in e for e in validate(self.root))
        )

    def test_localized_key_cannot_disable_required_translation(self):
        # A localized override cannot downgrade a required translation.
        self.write(
            "values-de",
            '<string name="arrival" translatable="false">In %2$d Min. zu %1$s</string>',
        )
        self.assertTrue(
            any("translatable flag differs for arrival" in error for error in validate(self.root))
        )

    def test_nontranslatable_source_may_be_omitted(self):
        self.write(
            "values",
            '<string name="arrival">Stop %1$s in %2$d min</string>'
            '<string name="machine_code" translatable="false">X-123</string>',
        )
        self.assertEqual([], validate(self.root))

    def test_nontranslatable_source_cannot_be_overridden_in_locale(self):
        self.write(
            "values",
            '<string name="arrival">Stop %1$s in %2$d min</string>'
            '<string name="provider_endpoint" translatable="false">https://example.org</string>',
        )
        self.write(
            "values-de",
            '<string name="arrival">In %2$d Min. zu %1$s</string>'
            '<string name="provider_endpoint">https://untrusted.example</string>',
        )
        errors = validate(self.root)
        self.assertTrue(
            any(
                "nontranslatable key must not be localized: provider_endpoint" in e
                for e in errors
            )
        )

    def test_nontranslatable_source_still_may_be_omitted(self):
        self.write(
            "values",
            '<string name="arrival">Stop %1$s in %2$d min</string>'
            '<string name="provider_endpoint" translatable="false">https://example.org</string>',
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

    def test_relative_argument_reuse_is_valid_when_arguments_match(self):
        self.write("values", '<string name="arrival">Stop %1$s / %&lt;s</string>')
        self.write("values-de", '<string name="arrival">Halt %1$s / %&lt;s</string>')
        self.write("values-en", '<string name="arrival">Stop %1$s / %&lt;s</string>')
        self.assertEqual([], validate(self.root))

    def test_extra_relative_argument_cannot_evade_parity_check(self):
        self.write("values-de", '<string name="arrival">Halt %1$s / %2$d / %&lt;d</string>')
        self.assertTrue(any("format arguments differ" in e for e in validate(self.root)))

    def test_relative_datetime_conversion_mismatch_is_rejected(self):
        self.write("values", '<string name="arrival">At %1$tH:%&lt;tM</string>')
        self.write("values-de", '<string name="arrival">Um %1$tH:%&lt;tH</string>')
        self.write("values-en", '<string name="arrival">At %1$tH:%&lt;tM</string>')
        self.assertTrue(any("format arguments differ" in e for e in validate(self.root)))

    def test_relative_without_preceding_argument_is_invalid(self):
        self.write("values-de", '<string name="arrival">Halt %&lt;s / %2$d</string>')
        self.assertTrue(any("invalid format" in e for e in validate(self.root)))

    def test_escaped_percent_has_no_format_argument(self):
        self.assertEqual({}, placeholders("100%% complete"))

    def test_whitespace_only_translation_is_rejected(self):
        self.write("values-de", '<string name="arrival">   </string>')
        self.assertTrue(any("empty translation" in e for e in validate(self.root)))


    def test_unsupported_java_conversion_fails_closed(self):
        self.write("values-de", '<string name="arrival">Halt %1$q in %2$d Min.</string>')
        self.assertTrue(
            any("unsupported format conversion %q" in e for e in validate(self.root))
        )

    def test_invalid_date_suffix_fails_closed(self):
        self.write("values", '<string name="arrival">Time: %1$tq</string>')
        self.write("values-de", '<string name="arrival">Zeit: %1$tq</string>')
        self.write("values-en", '<string name="arrival">Time: %1$tq</string>')
        self.assertTrue(
            any("unsupported date/time conversion %tq" in e for e in validate(self.root))
        )

    def test_dangling_index_fails_even_if_all_locales_match(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %1$</string>')
        self.assertTrue(
            any("malformed format argument" in e for e in validate(self.root))
        )

    def test_lone_percent_in_formatted_translation_fails(self):
        self.write(
            "values-de",
            '<string name="arrival">Halt %1$s in %2$d Min. 100%</string>',
        )
        self.assertTrue(
            any("malformed format argument" in e for e in validate(self.root))
        )

    def test_escaped_percent_and_line_break_remain_valid(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(
                locale,
                '<string name="arrival">Stop %1$s in %2$d min (100%%)%n</string>',
            )
        self.assertEqual([], validate(self.root))


    def test_relative_flag_on_newline_is_rejected_even_when_locales_match(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %1$s%&lt;n</string>')
        self.assertTrue(
            any("relative flag invalid for no-argument conversion %n" in e
                for e in validate(self.root))
        )

    def test_relative_flag_on_escaped_percent_is_rejected_even_when_locales_match(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Stop %1$s 100%&lt;%</string>')
        self.assertTrue(
            any("relative flag invalid for no-argument conversion %%" in e
                for e in validate(self.root))
        )


    def test_java_formatter_percent_literal_rejects_invalid_flags_and_precision(self):
        # Confirmed with Java 21 String.format; these throw before rendering.
        for token in ("%#%", "%+%", "% %", "%0%", "%(%", "%,%", "%-%", "%5.2%", "%.2%"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid width, precision or flags for %"):
                    placeholders(token)

    def test_invalid_percent_literal_fails_even_when_all_locales_match(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Progress %#%</string>')
        self.assertTrue(any("invalid format for arrival" in error for error in validate(self.root)))

    def test_valid_percent_literal_width_alignment_and_index_remain_allowed(self):
        for token in ("%%", "%5%", "%-5%", "%1$%", "%1$5%"):
            with self.subTest(token=token):
                self.assertEqual({}, dict(placeholders(token)))


    def test_java_formatter_line_separator_rejects_width_precision_and_flags(self):
        for token in ("%5n", "%.2n", "%-5n", "%1$5n"):
            with self.subTest(token=token):
                with self.assertRaisesRegex(ValueError, "invalid width, precision or flags for %n"):
                    placeholders(token)

    def test_invalid_newline_width_is_rejected_even_when_locales_match(self):
        for locale in ("values", "values-de", "values-en"):
            self.write(locale, '<string name="arrival">Next stop%5n</string>')
        self.assertTrue(any("invalid format for arrival" in error for error in validate(self.root)))

    def test_indexed_newline_without_width_remains_valid(self):
        self.assertEqual({}, placeholders("%1$n%n"))


if __name__ == "__main__":
    unittest.main()
