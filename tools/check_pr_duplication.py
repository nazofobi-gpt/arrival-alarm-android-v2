#!/usr/bin/env python3
"""Fail-closed duplicate work-unit gate for new Arrival V2 pull requests.

Existing pull requests remain grandfathered: a legacy PR without metadata is
reported, not automatically closed or blocked. No repository mutation occurs.
"""
from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

START_REQUIRED = datetime(2026, 10, 19, 11, 45, tzinfo=timezone.utc)
WORK_UNIT = re.compile(r"^\s*Work-Unit:\s*(G-\d+/[a-z0-9][a-z0-9.-]*)\s*$", re.I | re.M)
REVIEW = re.compile(r"^\s*Overlap-Review:\s*(.*?)\s*$", re.I | re.M)
TASK_ID = re.compile(r"\bG-\d+\b", re.I)
NUMBER = re.compile(r"#(\d+)")
API = "https://api.github.com"


def work_unit(body: str | None) -> str | None:
    match = WORK_UNIT.search(body or "")
    return match.group(1).lower() if match else None


def workstream(pr: dict[str, Any]) -> str | None:
    unit = work_unit(pr.get("body"))
    if unit:
        return unit.split("/", 1)[0].upper()
    hit = TASK_ID.search(pr.get("title") or "")
    return hit.group(0).upper() if hit else None


def overlap_refs(body: str | None) -> set[int]:
    # Review notes can be extended after concurrent PRs arrive; keep every
    # explicit Overlap-Review line without treating arbitrary prose as a review.
    return {
        int(number)
        for match in REVIEW.finditer(body or "")
        for number in NUMBER.findall(match.group(1))
    }


def normalized_title(title: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"[^\w]+", " ", title.casefold())).strip()


def is_new(pr: dict[str, Any]) -> bool:
    created = pr.get("created_at")
    # Missing provider timestamp cannot prove a legacy PR exemption.
    # Fail closed by enforcing new-PR metadata requirements.
    if not created:
        return True
    return datetime.fromisoformat(created.replace("Z", "+00:00")) >= START_REQUIRED


def analyze(
    current: dict[str, Any],
    open_prs: list[dict[str, Any]],
    closed_prs: list[dict[str, Any]],
) -> tuple[list[str], list[str]]:
    """Return (hard-block reasons, review warnings) without changing PRs."""
    errors: list[str] = []
    warnings: list[str] = []
    number = current["number"]
    unit = work_unit(current.get("body"))
    stream = workstream(current)
    peers = [p for p in open_prs if p.get("number") != number]

    if is_new(current) and not unit:
        errors.append("New PR requires 'Work-Unit: G-123/specific-acceptance-slice'.")
    if is_new(current) and not REVIEW.search(current.get("body") or ""):
        errors.append("New PR requires 'Overlap-Review: none' or reviewed PR numbers.")

    for other in peers:
        other_num = other["number"]
        other_unit = work_unit(other.get("body"))
        if unit and unit == other_unit:
            errors.append(f"Same work-unit already active in PR #{other_num}. Continue that PR.")
        if normalized_title(current.get("title", "")) == normalized_title(other.get("title", "")):
            warnings.append(f"Identical normalized title to open PR #{other_num}; inspect the patch.")

    if unit:
        for other in closed_prs:
            if other.get("number") != number and other.get("merged_at") and work_unit(other.get("body")) == unit:
                errors.append(f"Work-unit was already merged in PR #{other['number']}. Use a new acceptance slice only for genuinely new work.")

    same_stream = sorted(p["number"] for p in peers if stream and workstream(p) == stream)
    if same_stream:
        refset = overlap_refs(current.get("body"))
        missing = sorted(set(same_stream) - refset)
        if missing and is_new(current):
            errors.append(
                "Same-stream open PRs not acknowledged in Overlap-Review: "
                + ", ".join(f"#{n}" for n in missing)
            )
        elif missing:
            warnings.append(
                "Legacy PR: review open same-stream PRs before writing/merging: "
                + ", ".join(f"#{n}" for n in missing)
            )
    if not is_new(current) and not unit:
        warnings.append("Legacy PR has no work-unit; inspect against latest main before merging.")
    return sorted(set(errors)), sorted(set(warnings))


def request_json(repo: str, path: str, token: str) -> Any:
    url = f"{API}/repos/{repo}/{path}"
    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "User-Agent": "arrival-v2-duplicate-work-guard",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(req, timeout=25) as response:
        return json.load(response)


def list_prs(repo: str, token: str, state: str, max_pages: int = 6) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for page in range(1, max_pages + 1):
        rows = request_json(
            repo,
            f"pulls?state={state}&per_page=100&sort=updated&direction=desc&page={page}",
            token,
        )
        result.extend(rows)
        if len(rows) < 100:
            break
    else:
        raise RuntimeError(
            f"{state} PR scan exceeded {max_pages * 100} entries: cannot attest complete dedup scan"
        )
    return result


def publish(lines: list[str]) -> None:
    content = "\n".join(lines) + "\n"
    print(content)
    summary = os.getenv("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(content)


def main() -> int:
    repo = os.environ["GITHUB_REPOSITORY"]
    token = os.environ["GITHUB_TOKEN"]
    event_path = os.getenv("GITHUB_EVENT_PATH")
    event = json.loads(Path(event_path).read_text(encoding="utf-8")) if event_path else {}
    try:
        active = list_prs(repo, token, "open")
        current = event.get("pull_request")
        if current is None:
            grouped: dict[str, list[int]] = defaultdict(list)
            for pr in active:
                key = workstream(pr)
                if key:
                    grouped[key].append(pr["number"])
            publish(
                ["### Arrival V2 duplicate-work inventory", f"Open PRs: **{len(active)}**"]
                + [
                    f"- {key}: " + ", ".join(f"#{n}" for n in sorted(numbers))
                    for key, numbers in sorted(grouped.items())
                    if len(numbers) > 1
                ]
                + ["Manual review required: shared G-ID alone is not proof of duplication."]
            )
            return 0
        closed = list_prs(repo, token, "closed") if work_unit(current.get("body")) else []
        errors, warnings = analyze(current, active, closed)
        publish(
            [
                f"### Duplicate work-unit guard — PR #{current['number']}",
                f"Work unit: {work_unit(current.get('body')) or 'legacy/unset'}",
                f"Blocking findings: **{len(errors)}**; review warnings: **{len(warnings)}**",
            ]
            + [f"- BLOCK: {error}" for error in errors]
            + [f"- REVIEW: {warning}" for warning in warnings]
        )
        for error in errors:
            print(f"::error::{error}")
        for warning in warnings:
            print(f"::warning::{warning}")
        return 1 if errors else 0
    except (OSError, RuntimeError, ValueError, urllib.error.URLError) as error:
        print(f"::error::Duplicate-work preflight failed; do not claim pass: {error}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
