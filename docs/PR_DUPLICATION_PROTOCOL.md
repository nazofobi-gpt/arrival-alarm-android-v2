# Arrival V2 — duplicate-work prevention and reconciliation

## Safety contract

Before editing code, read the current canonical \`Görevler\` row, dependencies,
owner and \`Lease Owner / Lease Until\` in the \`hafıza\` workbook. Confirm the
currently authorized execution context. Read the latest GitHub \`main\`, all open
PRs of the same parent G-task, their *actual patches*, and recently merged work.

A G-task can contain many independent acceptance slices. A shared task ID or
filename is **not** proof of duplication. A merged PR proves only that its
specific code slice was merged; it does not establish physical acceptance.

## Admission gate for newly created PRs

New PRs created from 2026-10-19 11:45 UTC onward must provide both:

    Work-Unit: G-168/provider-qualified-stop-identity
    Overlap-Review: #91 #101 #103

The first field must identify **one unique, independently verifiable change**;
the second must acknowledge every currently open PR of the same G-task. Use
\`Overlap-Review: none\` only when no same-task PR is open.

The read-only \`tools/check_pr_duplication.py\` action rejects:

- Missing or invalid work-unit metadata for new PRs.
- Missing overlap review or omitted currently open same-task PRs.
- A duplicate **exact work-unit** already open or merged within the bounded
  current repository PR history.

Same-task but independently named slices, similar titles, and overlapping file
names alone result in review warnings, **not** automatic rejection. Historical
PRs are grandfathered and may be inspected without forcing bulk closures.
Unavailable GitHub API evidence fails the check rather than claiming success.

Run \`python3 -m unittest discover -s tools/tests -p 'test_duplicate_pr_guard.py'\`
locally. After integration, GitHub Actions runs it on PR open/edit/update and
exposes an inventory via manual \`Duplicate Work-Unit Guard\` dispatch. The
workflow status becomes a merge blocker only when a repository ruleset makes
that named check required. Do not claim repository protection is active until
its ruleset has been verified.

## Existing PR cleanup — reconcile, never blindly merge

1. On the latest main, enumerate open PRs, actual code patches, changed paths,
   test outcomes, and canonical task acceptance requirements.
2. Compare old proposals against current production code and tests. Review
   unmatched semantics, not just matching text. Detect partial supersession.
3. Select one owning PR for each *distinct* acceptance slice. Preserve useful
   unique work from other PRs before any closure.
4. Close a redundant PR only with evidence: named successor/merged SHA,
   zero remaining valuable delta, and an explicit explanatory rationale.
   Never automatically close an overlapping or stale branch.
5. Enforce fresh-main review, SHA-bound CI and API36 results, reviewed file
   collision checks, and confirmed permissions before each merge. A failed
   or denied write is **NO_WRITE**; do not retry through alternate endpoints.
6. Write exact PR/head/merge SHA, verified tests, remaining device/provider
   tests, and corrected next action into canonical \`Görevler\` records.
7. Keep \`CODE_MERGED\`, \`CI_GREEN\`, \`DEVICE_VERIFIED\`, and
   \`FIELD_ACCEPTED\` as distinct evidence. Never set a terminal task status
   based only on CI or a PR merge.

### Review queue identified on 2026-10-19

- GTFS service-day: #54 / #60, both substantially present in main; inspect
  remaining exception/date differences before closing either.
- Nationwide search: #91 / #101 / #103, extensive overlapping patches; compare
  unique test/provider requirements against newer merged work.
- Nearby station IDs: #156 / #157 / #216, partially different semantics;
  coalesce only after testing parent/station identity rules.
- Connector durable journal: #218 / #222 / #250 / #253, progressive security
  changes not yet main-integrated. Preserve fingerprint/idempotency semantics.
- For all other open PRs, do not infer duplicate merely from a common G-task.

## Canonical source and limits

The \`hafıza\` Google Sheet is the source of truth for G-task status/leases.
GitHub Actions has a read-only GitHub token and **does not** have permission
to modify that Google Sheet. Post-merge Sheet reconciliation must happen in an
authorized connector-backed executor (or after separate credentialed
integration); never claim it is done from the GitHub check itself.

This control is preventive, not a replacement for Germany Full / Lohne–Achim
field acceptance, GPS/Doze/TTS/Bluetooth testing, G-136 user acceptance, or
G-138 Play packaging.
