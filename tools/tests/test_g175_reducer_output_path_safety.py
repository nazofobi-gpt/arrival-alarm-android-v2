"""The G-175 reducer must never overwrite its source or another output."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile


SCRIPT = Path(__file__).resolve().parents[1] / "reduce_germany_full_for_g175.py"


class ReducerOutputPathSafetyTest(unittest.TestCase):
    def run_reducer(self, source, output, report):
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(source), str(output), str(report)],
            capture_output=True, text=True, timeout=10, check=False,
        )

    def test_source_cannot_be_reused_as_output_or_report(self):
        for destination in ("OUTPUT", "REPORT"):
            with self.subTest(destination=destination), tempfile.TemporaryDirectory() as dirname:
                root = Path(dirname)
                source = root / "source.zip"
                with zipfile.ZipFile(source, "w") as archive:
                    archive.writestr("stops.txt", "stop_id\n1\n")
                before = source.read_bytes()
                output = source if destination == "OUTPUT" else root / "fixture.zip"
                report = source if destination == "REPORT" else root / "report.json"
                result = self.run_reducer(source, output, report)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(
                    f"SOURCE and {destination} must be different files",
                    result.stderr.strip(),
                )
                self.assertEqual(before, source.read_bytes())
                self.assertFalse((root / ("report.json" if destination == "OUTPUT" else "fixture.zip")).exists())

    def test_output_and_report_must_not_share_an_uncreated_path(self):
        with tempfile.TemporaryDirectory() as dirname:
            root = Path(dirname)
            source = root / "source.zip"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("stops.txt", "stop_id\n1\n")
            shared = root / "shared.zip"
            result = self.run_reducer(source, shared, shared)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual("OUTPUT and REPORT must be different files", result.stderr.strip())
            self.assertFalse(shared.exists())

    def test_source_aliases_as_output_are_rejected_before_processing(self):
        for alias_kind in ("symlink", "hardlink"):
            with self.subTest(alias_kind=alias_kind), tempfile.TemporaryDirectory() as dirname:
                root = Path(dirname)
                source = root / "source.zip"
                with zipfile.ZipFile(source, "w") as archive:
                    archive.writestr("stops.txt", "stop_id\n1\n")
                before = source.read_bytes()
                alias = root / "source-alias.zip"
                if alias_kind == "symlink":
                    alias.symlink_to(source)
                else:
                    os.link(source, alias)
                result = self.run_reducer(source, alias, root / "report.json")
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("SOURCE and OUTPUT must be different files", result.stderr.strip())
                self.assertEqual(before, source.read_bytes())
                self.assertFalse((root / "report.json").exists())

    def test_distinct_paths_still_reach_original_feed_validation(self):
        with tempfile.TemporaryDirectory() as dirname:
            root = Path(dirname)
            source = root / "source.zip"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("stops.txt", "stop_id\n1\n")
            result = self.run_reducer(source, root / "fixture.zip", root / "report.json")
            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing routes.txt", result.stderr)
            self.assertNotIn("must be different files", result.stderr)


if __name__ == "__main__":
    unittest.main()
