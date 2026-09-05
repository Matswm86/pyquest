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
VALID_TYPES = {"mcq", "blocks", "order", "fill", "pipeline"}
GAP = re.compile(r"\{(\d+)}")
MIN_EXPLAIN_CHARS = 40
# Pytor's material. A hint that is shorter than this is a nudge nobody can use,
# and a deep note shorter than this is a restated explanation, not expertise.
MIN_HINT_CHARS = 15
MAX_HINTS = 3
MIN_DEEP_CHARS = 60


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

    hints = question.get("hints")
    if not isinstance(hints, list) or not hints:
        problems.append(f"{qid}: needs at least one hint, Pytor has nothing to say otherwise")
    else:
        if len(hints) > MAX_HINTS:
            problems.append(f"{qid}: {len(hints)} hints, at most {MAX_HINTS}")
        for index, hint in enumerate(hints):
            if not isinstance(hint, str) or len(hint.strip()) < MIN_HINT_CHARS:
                problems.append(f"{qid}: hint {index} is too short to help")

    deep = question.get("deep")
    if not isinstance(deep, str) or len(deep.strip()) < MIN_DEEP_CHARS:
        problems.append(
            f"{qid}: deep note missing or under {MIN_DEEP_CHARS} chars. "
            "This is the expert layer, the part a working engineer would want."
        )

    answer = question.get("answer") or []
    if not isinstance(answer, list) or not answer:
        problems.append(f"{qid}: answer must be a non-empty list")
        return problems

    # A hint that quotes the whole answer is the answer. Check the plain string
    # forms; block ids in the typed formats are opaque so they cannot leak.
    if isinstance(hints, list) and qtype in {"mcq", "blocks", "order"}:
        joined = " ".join(str(h) for h in hints).lower()
        if qtype == "mcq" and str(answer[0]).lower() in joined and len(str(answer[0])) > 6:
            problems.append(f"{qid}: a hint contains the correct option verbatim")

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
    elif qtype == "fill":
        problems.extend(check_fill(qid, question, answer))
    elif qtype == "pipeline":
        problems.extend(check_pipeline(qid, question, answer))
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


def block_ids(question: dict) -> list[str]:
    return [str(block.get("id")) for block in question.get("blocks") or []]


def check_blocks_pool(qid: str, question: dict, answer: list[str], slots: int) -> list[str]:
    """Shared rules for the two typed formats: real ids, no duplicates, distractors."""
    problems: list[str] = []
    ids = block_ids(question)
    if not ids:
        problems.append(f"{qid}: needs a blocks list")
        return problems
    if len(set(ids)) != len(ids):
        problems.append(f"{qid}: duplicate block ids")
    unknown = [block for block in answer if block not in ids]
    if unknown:
        problems.append(f"{qid}: answer names blocks that do not exist: {', '.join(unknown)}")
    if len(set(answer)) != len(answer):
        problems.append(f"{qid}: answer uses the same block twice")
    if len(answer) != slots:
        problems.append(f"{qid}: {slots} slot(s) but {len(answer)} answer entries")
    if len(ids) <= slots:
        problems.append(
            f"{qid}: {len(ids)} blocks for {slots} slots leaves nothing to rule out. "
            "Every typed question needs at least one distractor."
        )
    if question.get("options") or question.get("tray"):
        problems.append(f"{qid}: typed questions carry blocks, not options or tray")
    return problems


def check_fill(qid: str, question: dict, answer: list[str]) -> list[str]:
    template = question.get("template")
    if not template:
        problems = [f"{qid}: fill needs a template"]
        return problems + check_blocks_pool(qid, question, answer, len(answer))

    gaps = [int(match.group(1)) for match in GAP.finditer(template)]
    problems: list[str] = []
    if sorted(gaps) != list(range(len(gaps))):
        problems.append(
            f"{qid}: template gaps must be numbered 0..N with no repeats or holes, found {gaps}"
        )
    problems.extend(check_blocks_pool(qid, question, answer, len(gaps)))
    return problems


def check_pipeline(qid: str, question: dict, answer: list[str]) -> list[str]:
    brief = question.get("brief")
    if not isinstance(brief, dict):
        return [f"{qid}: pipeline needs a brief"]

    problems: list[str] = []
    for field in ("client", "initials", "quote"):
        if not str(brief.get(field, "")).strip():
            problems.append(f"{qid}: brief.{field} is empty")

    stages = brief.get("stages")
    if not isinstance(stages, int) or stages < 2:
        problems.append(f"{qid}: brief.stages must be an integer of at least 2")
        stages = len(answer)

    problems.extend(check_blocks_pool(qid, question, answer, stages))

    # The intended wiring has to actually fit the budget the brief states. A
    # capstone whose own answer breaches the client's ceiling teaches the
    # opposite of the lesson, and reads as a bug on the player's screen.
    by_id = {str(b.get("id")): b for b in question.get("blocks") or []}
    chosen = [by_id[b] for b in answer if b in by_id]
    total_ms = sum(int(b.get("ms", 0)) for b in chosen)
    total_cost = sum(float(b.get("cost", 0.0)) for b in chosen)
    max_ms = brief.get("maxMs")
    max_cost = brief.get("maxCost")
    if isinstance(max_ms, int) and total_ms > max_ms:
        problems.append(f"{qid}: the answer totals {total_ms} ms, over the brief's {max_ms} ms")
    if isinstance(max_cost, (int, float)) and total_cost > max_cost:
        problems.append(
            f"{qid}: the answer totals {total_cost:.4f}, over the brief's {max_cost:.4f}"
        )
    if not any(int(b.get("ms", 0)) for b in by_id.values()):
        problems.append(f"{qid}: pipeline components need ms values or the readout is dead")
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
