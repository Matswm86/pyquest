#!/usr/bin/env python3
"""Schema check for the PyQuest curriculum.

Content ships as JSON in the APK's assets, so a malformed question is a crash on
someone's phone rather than a compile error. This script is the gate: CI runs it
on every push and the build fails before the APK is produced.

Usage:
    python3 tools/validate_curriculum.py [curriculum_dir]
"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path

DEFAULT_DIR = Path("app/src/main/assets/curriculum")
FILE_PATTERN = re.compile(r"^tier_(\d{2})\.json$")
ID_PATTERN = re.compile(r"^t(\d+)\.l(\d+)\.q(\d+)$")
VALID_TYPES = {"mcq", "blocks", "order"}
MIN_EXPLAIN_CHARS = 40


class CurriculumError(Exception):
    """A question or tier file that would break the app at runtime."""


def check_question(question: dict, tier_number: int, seen_ids: set[str]) -> list[str]:
    """Returns a list of problems with one question, empty when it is sound."""
    problems: list[str] = []
    qid = question.get("id", "<missing id>")

    match = ID_PATTERN.match(str(qid))
    if match is None:
        problems.append(f"{qid}: id must look like t1.l2.q3")
    else:
        id_tier, id_level = int(match.group(1)), int(match.group(2))
        if id_tier != tier_number:
            problems.append(f"{qid}: id says tier {id_tier}, file says tier {tier_number}")
        if id_level != question.get("level"):
            problems.append(f"{qid}: id says level {id_level}, field says {question.get('level')}")

    if qid in seen_ids:
        problems.append(f"{qid}: duplicate id")
    seen_ids.add(qid)

    qtype = question.get("type")
    if qtype not in VALID_TYPES:
        problems.append(f"{qid}: type {qtype!r} is not one of {sorted(VALID_TYPES)}")
        return problems

    if not str(question.get("prompt", "")).strip():
        problems.append(f"{qid}: prompt is empty")

    explain = str(question.get("explain", ""))
    if len(explain) < MIN_EXPLAIN_CHARS:
        problems.append(
            f"{qid}: explain is {len(explain)} chars, needs at least {MIN_EXPLAIN_CHARS}. "
            "The explanation is the teaching, not a footnote."
        )

    answer = question.get("answer") or []
    if not isinstance(answer, list) or not answer:
        problems.append(f"{qid}: answer must be a non-empty list")
        return problems

    if qtype == "mcq":
        options = question.get("options") or []
        if not 2 <= len(options) <= 6:
            problems.append(f"{qid}: mcq needs 2 to 6 options, found {len(options)}")
        if len(answer) != 1:
            problems.append(f"{qid}: mcq answer must hold exactly one option")
        elif answer[0] not in options:
            problems.append(f"{qid}: mcq answer {answer[0]!r} is not among the options")
        if len(set(options)) != len(options):
            problems.append(f"{qid}: mcq has duplicate options")
        if question.get("tray"):
            problems.append(f"{qid}: mcq must not carry a tray")
    else:
        tray = question.get("tray") or []
        if not tray:
            problems.append(f"{qid}: {qtype} needs a tray")
        problems.extend(check_against_tray(qid, tray, answer, "answer"))
        for index, alternative in enumerate(question.get("accept") or []):
            problems.extend(check_against_tray(qid, tray, alternative, f"accept[{index}]"))
        if qtype == "order" and Counter(tray) != Counter(answer):
            problems.append(f"{qid}: order must use every tray block exactly once")
        if question.get("options"):
            problems.append(f"{qid}: {qtype} must not carry options")

    return problems


def check_against_tray(qid: str, tray: list[str], sequence: list[str], label: str) -> list[str]:
    """Every block in an answer has to be available in the tray, counting repeats."""
    if not isinstance(sequence, list) or not sequence:
        return [f"{qid}: {label} must be a non-empty list"]
    shortfall = Counter(sequence) - Counter(tray)
    if shortfall:
        missing = ", ".join(f"{block!r} x{count}" for block, count in sorted(shortfall.items()))
        return [f"{qid}: {label} uses blocks the tray does not supply: {missing}"]
    return []


def check_file(path: Path) -> list[str]:
    match = FILE_PATTERN.match(path.name)
    if match is None:
        return [f"{path.name}: filename must look like tier_07.json"]
    tier_number = int(match.group(1))

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"{path.name}: invalid JSON at line {exc.lineno}, {exc.msg}"]

    problems: list[str] = []
    if data.get("tier") != tier_number:
        problems.append(
            f"{path.name}: tier field is {data.get('tier')}, filename says {tier_number}"
        )
    if not str(data.get("title", "")).strip():
        problems.append(f"{path.name}: title is empty")

    questions = data.get("questions")
    if not isinstance(questions, list) or not questions:
        return problems + [f"{path.name}: questions must be a non-empty list"]

    seen_ids: set[str] = set()
    for question in questions:
        if not isinstance(question, dict):
            problems.append(f"{path.name}: every question must be an object")
            continue
        problems.extend(check_question(question, tier_number, seen_ids))

    levels = sorted({question.get("level") for question in questions})
    if levels != list(range(1, len(levels) + 1)):
        problems.append(f"{path.name}: levels must run 1..N with no gaps, found {levels}")

    return problems


def main(argv: list[str]) -> int:
    root = Path(argv[1]) if len(argv) > 1 else DEFAULT_DIR
    if not root.is_dir():
        print(f"curriculum directory not found: {root}", file=sys.stderr)
        return 2

    files = sorted(root.glob("*.json"))
    if not files:
        print(f"no curriculum files in {root}", file=sys.stderr)
        return 2

    all_problems: list[str] = []
    total_questions = 0
    for path in files:
        problems = check_file(path)
        all_problems.extend(problems)
        if not problems:
            data = json.loads(path.read_text(encoding="utf-8"))
            count = len(data["questions"])
            total_questions += count
            print(f"OK   {path.name}: {count} questions, tier {data['tier']} {data['title']!r}")
        else:
            print(f"FAIL {path.name}")

    for problem in all_problems:
        print(f"  {problem}", file=sys.stderr)

    if all_problems:
        print(f"\n{len(all_problems)} problem(s) across {len(files)} file(s)", file=sys.stderr)
        return 1

    print(f"\nAll {len(files)} tier file(s) valid, {total_questions} questions total.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
