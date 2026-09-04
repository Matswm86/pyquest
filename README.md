# PyQuest

An Android game that walks you from `print("hello")` to scoping and pricing an
AI-engineering consultancy job, using multiple-choice cards and Scratch-style
drag blocks the whole way up.

The design lives in [DESIGN.md](DESIGN.md). The short version: the same physical
action, dragging pieces into the right order, carries the player from syntax
tokens at tier 1 to pipeline components at tier 10.

## State

| Piece | Status |
|-------|--------|
| Gradle + Compose app, CI build | scaffolded |
| `mcq` and `blocks`/`order` question types | implemented |
| Tier map with per-tier mastery | implemented |
| Leitner spaced repetition, XP, streak | implemented |
| Tier 1 "First Words" | 30 questions, 5 levels |
| Tier 10 "Consultancy" | 6-question preview, level 1 only |
| Tiers 2 to 9 | not written yet |

Tier 10 is deliberately in the build already so the top of the ladder is playable
on day one, not just described in a document.

## Building

Every APK is built in GitHub Actions. Do not run `./gradlew` on the workstation;
the 8 GB box runs out of memory.

```bash
gh workflow run build-android.yml            # trigger a build
gh run watch                                 # follow it
gh run download --name pyquest-debug-apk     # fetch the APK to sideload
```

## Adding questions

Questions are data, never Kotlin. Edit
`app/src/main/assets/curriculum/tier_NN.json` and run the gate:

```bash
python3 tools/validate_curriculum.py
```

The validator refuses an answer that uses a block the tray does not supply, an
`order` question that leaves tray blocks unused, a multiple-choice answer that is
not among the options, a duplicate id, a level gap, and an explanation shorter
than 40 characters. CI runs it before the APK job starts.

### Question shape

```json
{
  "id": "t10.l1.q4",
  "tier": 10,
  "level": 1,
  "type": "blocks",
  "prompt": "Order the ingestion pipeline. One block belongs to query time.",
  "tray": ["embed chunks", "split into chunks", "re-rank results", "load the PDF", "upsert to the vector store"],
  "answer": ["load the PDF", "split into chunks", "embed chunks", "upsert to the vector store"],
  "accept": [],
  "explain": "Re-ranking scores candidates that came back from a search, so it needs a query.",
  "xp": 45,
  "tags": ["rag", "pipeline"],
  "reviewed": "2026-09-04"
}
```

`type` is `mcq` (tap one of `options`), `blocks` (drag a subset of `tray` into
order, distractors allowed) or `order` (every tray block must be used). `accept`
holds alternative orderings that are also correct.

## Why there is no Python interpreter in the app

Grading compares the assembled sequence to `answer`, or to any entry in `accept`.
That keeps the APK small, the app fully offline and the feedback instant, at the
cost of not grading free-form typed code. Typing Python on a phone keyboard is
miserable, and [PyLearn](https://pytor.mwmai.no/) already covers that on desktop.
