# PyQuest

An Android game that walks you from `print("hello")` to scoping and pricing an
AI-engineering consultancy job, using multiple-choice cards and Scratch-style
drag blocks the whole way up.

The design lives in [DESIGN.md](DESIGN.md). The short version: the same physical
action, dragging pieces into the right order, carries the player from syntax
tokens at tier 1 to pipeline components at tier 10.

## 📲 Download

**[⬇ Latest APK](https://github.com/Matswm86/pyquest/releases/download/latest/pyquest-d595be8.apk)**
&nbsp;·&nbsp; [all builds](https://github.com/Matswm86/pyquest/releases)

Open the link on your phone, tap the file, and allow "install from this source"
when Android asks. Android 8.0 or newer (minSdk 26). The filename carries the
commit id on purpose, so your browser can never serve you a cached old build. If
the link 404s, a newer build has landed: grab the newest `pyquest-*.apk` off the
releases page.

Debug-signed. Reinstalling over a build with a different signature means
uninstalling the old one first.

## State

| Piece | Status |
|-------|--------|
| Gradle + Compose app, CI build, rolling APK release | shipped |
| `mcq`, `blocks`, `order`, `fill`, `pipeline` question types | shipped |
| Accordion track screen, daily goal ring, rank, streak | shipped |
| Leitner spaced repetition, first-try accuracy, XP | shipped |
| Tier 1 "Hello, world" | 32 questions, 5 levels |
| Tier 8 "AI consultancy sims" | 7-question capstone preview, level 1 |
| Tiers 2 to 7 | not written yet |

Tier 8 is deliberately in the build already so the top of the ladder is playable
on day one, not just described in a document. Its pipeline question is the one
place the game stops testing recall: you wire four stages of a ticket-routing
system while the latency and cost readouts move against the client's budget.

## Building

Every APK is built in GitHub Actions, which also publishes it to the rolling
`latest` pre-release and rewrites the download link above to match. Pushing to
`main` is the whole release process.

```bash
gh workflow run build-android.yml            # trigger a build by hand
gh run watch                                 # follow it
gh run download --name pyquest-debug-apk     # fetch the APK without a release
```

Tagging `vX.Y.Z` publishes a normal, non-rolling release instead.

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

`type` is `mcq` (tap one of `options`), `blocks` (place a subset of `tray` in
order, distractors allowed), `order` (every tray block must be used), `fill`
(blocks go into `{0}`-style gaps in `template`) or `pipeline` (blocks are stages
with `ms` and `cost`, wired against the budget in `brief`). `accept` holds
alternative orderings that are also correct.

The validator refuses a `fill` whose gaps are not numbered 0..N, a typed question
with no distractor in the tray, and a `pipeline` whose own answer breaches the
budget its brief states, because a capstone that fails its own brief teaches the
opposite of the lesson.

## Why there is no Python interpreter in the app

Grading compares the assembled sequence to `answer`, or to any entry in `accept`.
That keeps the APK small, the app fully offline and the feedback instant, at the
cost of not grading free-form typed code. Typing Python on a phone keyboard is
miserable, and [PyLearn](https://pytor.mwmai.no/) already covers that on desktop.
