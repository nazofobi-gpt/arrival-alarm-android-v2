"""Regression tests for duplicate work-unit admission and legacy safety."""
import importlib.util
import unittest
from pathlib import Path

SOURCE = Path(__file__).resolve().parents[1] / "check_pr_duplication.py"
spec = importlib.util.spec_from_file_location("check_pr_duplication", SOURCE)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


def pr(number, body="", title="fix(G-168): search", created="2026-10-19T12:00:00Z", merged=None):
    return {
        "number": number,
        "body": body,
        "title": title,
        "created_at": created,
        "merged_at": merged,
    }


class DuplicateGuardTest(unittest.TestCase):
    def test_same_exact_work_unit_is_blocked(self):
        current = pr(402, "Work-Unit: G-168/provider-identities\nOverlap-Review: #101")
        existing = pr(101, "Work-Unit: G-168/provider-identities", "fix(G-168): other")
        errors, _ = guard.analyze(current, [existing], [])
        self.assertTrue(any("already active" in item for item in errors))

    def test_distinct_slice_same_parent_requires_review_not_duplicate(self):
        current = pr(402, "Work-Unit: G-168/provider-identities\nOverlap-Review: #101")
        existing = pr(101, "Work-Unit: G-168/search-ui", "feat(G-168): screen")
        errors, _ = guard.analyze(current, [existing], [])
        self.assertFalse(errors)

    def test_same_parent_without_acknowledgement_is_blocked(self):
        current = pr(402, "Work-Unit: G-168/provider-identities\nOverlap-Review: none")
        old = pr(101, "Work-Unit: G-168/search-ui", "feat(G-168): screen")
        errors, _ = guard.analyze(current, [old], [])
        self.assertTrue(any("not acknowledged" in item for item in errors))

    def test_multiple_overlap_review_lines_acknowledge_distinct_peers(self):
        body = (
            "Work-Unit: G-173/multiline-review\\n"
            "Overlap-Review: #101\\n"
            "Overlap-Review: #102\\n"
        )
        body = body.replace("\\n", "\n")
        current = pr(402, body, "fix(G-173): gate audit")
        first = pr(101, "Work-Unit: G-173/search-ui", "feat(G-173): readiness UI")
        second = pr(102, "Work-Unit: G-173/index-guard", "fix(G-173): index guard")
        self.assertEqual(guard.overlap_refs(body), {101, 102})
        errors, _ = guard.analyze(current, [first, second], [])
        self.assertEqual(errors, [])

    def test_unprefixed_prose_is_not_overlap_acknowledgement(self):
        body = (
            "Work-Unit: G-173/multiline-review\\n"
            "Overlap-Review: #101\\n"
            "Discussion: #102 was mentioned without an overlap review\\n"
        )
        body = body.replace("\\n", "\n")
        current = pr(402, body, "fix(G-173): gate audit")
        first = pr(101, "Work-Unit: G-173/search-ui", "feat(G-173): readiness UI")
        second = pr(102, "Work-Unit: G-173/index-guard", "fix(G-173): index guard")
        self.assertEqual(guard.overlap_refs(body), {101})
        errors, _ = guard.analyze(current, [first, second], [])
        self.assertTrue(any("#102" in error for error in errors))

    def test_missing_metadata_is_required_only_for_new_pr(self):
        new = pr(402, "", "fix(G-168): route")
        old = pr(90, "", "fix(G-168): route", created="2026-10-11T12:00:00Z")
        self.assertTrue(guard.analyze(new, [], [])[0])
        errors, warnings = guard.analyze(old, [], [])
        self.assertEqual(errors, [])
        self.assertTrue(warnings)

    def test_merged_same_unit_blocks_reimplementation(self):
        current = pr(402, "Work-Unit: G-168/provider-identities\nOverlap-Review: none")
        merged = pr(300, "Work-Unit: G-168/provider-identities", merged="2026-10-18T13:00:00Z")
        errors, _ = guard.analyze(current, [], [merged])
        self.assertTrue(any("already merged" in item for item in errors))

    def test_closed_unmerged_same_unit_does_not_block(self):
        current = pr(402, "Work-Unit: G-168/provider-identities\nOverlap-Review: none")
        closed = pr(300, "Work-Unit: G-168/provider-identities")
        errors, _ = guard.analyze(current, [], [closed])
        self.assertEqual(errors, [])

    def test_title_overlap_only_warns_not_blocks(self):
        current = pr(402, "Work-Unit: G-185/cache-epoch\nOverlap-Review: #403",
                     "fix(G-185): preserve offline cache")
        existing = pr(403, "Work-Unit: G-185/cache-metadata",
                      "fix(G-185): preserve offline cache")
        errors, warnings = guard.analyze(current, [existing], [])
        self.assertFalse(errors)
        self.assertTrue(any("Identical normalized title" in item for item in warnings))

    def test_self_is_not_a_duplicate(self):
        current = pr(402, "Work-Unit: G-185/cache-epoch\nOverlap-Review: none")
        errors, _ = guard.analyze(current, [current], [])
        self.assertEqual(errors, [])

    def test_work_unit_is_not_only_parent_id(self):
        self.assertIsNone(guard.work_unit("Work-Unit: G-168"))
        self.assertEqual(guard.work_unit("Work-Unit: G-168/provider-identities"),
                         "g-168/provider-identities")

    def test_legacy_same_stream_returns_review_warning(self):
        a = pr(91, title="G-168 search", created="2026-10-04T00:00:00Z")
        b = pr(101, title="G-168 search UI", created="2026-10-05T00:00:00Z")
        errors, warnings = guard.analyze(a, [b], [])
        self.assertFalse(errors)
        self.assertTrue(any("same-stream" in item for item in warnings))


if __name__ == "__main__":
    unittest.main()
