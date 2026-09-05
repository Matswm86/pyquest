#!/usr/bin/env python3
"""Schema check for Pytor's Codex.

The Codex is the expert reference that ships inside the APK and answers the
player when there is no network. A malformed entry is a crash or a silent
empty search on a phone, so this runs in CI before the APK job, next to the
curriculum validator.

Usage:
    python3 tools/validate_codex.py [codex_json]
"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path

DEFAULT_PATH = Path("app/src/main/assets/codex/codex.json")
DOMAINS = {"python", "engineering", "ai"}
ID_PATTERN = re.compile(r"^[a-z0-9][a-z0-9-]{2,60}$")
MIN_BODY_CHARS = 200
MIN_SUMMARY_CHARS = 30
MIN_TAGS = 2
MIN_ENTRIES_PER_DOMAIN = 15


def check_entry(entry: dict, seen: set[str]) -> list[str]:
    problems: list[str] = []
    eid = str(entry.get("id", "<missing id>"))
    if ID_PATTERN.match(eid) is None:
        problems.append(f"{eid}: id must be lowercase kebab-case, 3 to 61 chars")
    if eid in seen:
        problems.append(f"{eid}: duplicate id")
    seen.add(eid)

    if entry.get("domain") not in DOMAINS:
        problems.append(f"{eid}: domain {entry.get('domain')!r} is not one of {sorted(DOMAINS)}")
    if not str(entry.get("title", "")).strip():
        problems.append(f"{eid}: title is empty")

    tags = entry.get("tags") or []
    if not isinstance(tags, list) or len(tags) < MIN_TAGS:
        problems.append(f"{eid}: needs at least {MIN_TAGS} tags, search leans on them")
    elif any(not isinstance(tag, str) or tag != tag.lower().strip() for tag in tags):
        problems.append(f"{eid}: tags must be lowercase and trimmed")

    summary = str(entry.get("summary", ""))
    if len(summary.strip()) < MIN_SUMMARY_CHARS:
        problems.append(f"{eid}: summary is {len(summary)} chars, needs {MIN_SUMMARY_CHARS}")

    body = str(entry.get("body", ""))
    if len(body.strip()) < MIN_BODY_CHARS:
        problems.append(
            f"{eid}: body is {len(body)} chars, needs at least {MIN_BODY_CHARS}. "
            "A Codex note is an expert answer, not a glossary line."
        )

    code = entry.get("code")
    if code is not None and (not isinstance(code, str) or not code.strip()):
        problems.append(f"{eid}: code must be a non-empty string when present")

    related = entry.get("related") or []
    if not isinstance(related, list) or any(not isinstance(r, str) for r in related):
        problems.append(f"{eid}: related must be a list of ids")
    return problems


def check_file(path: Path) -> list[str]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"{path.name}: invalid JSON at line {exc.lineno}, {exc.msg}"]

    entries = data.get("entries")
    if not isinstance(entries, list) or not entries:
        return [f"{path.name}: entries must be a non-empty list"]

    problems: list[str] = []
    seen: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            problems.append(f"{path.name}: every entry must be an object")
            continue
        problems.extend(check_entry(entry, seen))

    # Dangling related links are a dead tap in the app, so they fail the build.
    ids = {str(e.get("id")) for e in entries if isinstance(e, dict)}
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        for rel in entry.get("related") or []:
            if rel not in ids:
                problems.append(f"{entry.get('id')}: related id {rel!r} does not exist")

    per_domain = Counter(str(e.get("domain")) for e in entries if isinstance(e, dict))
    for domain in sorted(DOMAINS):
        if per_domain[domain] < MIN_ENTRIES_PER_DOMAIN:
            problems.append(
                f"{path.name}: domain {domain!r} has {per_domain[domain]} entries, "
                f"needs {MIN_ENTRIES_PER_DOMAIN} to count as expertise"
            )
    return problems


def main(argv: list[str]) -> int:
    path = Path(argv[1]) if len(argv) > 1 else DEFAULT_PATH
    if not path.is_file():
        print(f"codex file not found: {path}", file=sys.stderr)
        return 2

    problems = check_file(path)
    if problems:
        print(f"FAIL {path.name}")
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        print(f"\n{len(problems)} problem(s)", file=sys.stderr)
        return 1

    data = json.loads(path.read_text(encoding="utf-8"))
    per_domain = Counter(e["domain"] for e in data["entries"])
    summary = ", ".join(f"{d} {per_domain[d]}" for d in sorted(DOMAINS))
    print(f"OK   {path.name}: {len(data['entries'])} entries ({summary})")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
