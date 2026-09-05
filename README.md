# PyQuest

An Android game that walks you from `print("hello")` to scoping and pricing an
AI-engineering consultancy job, with Pytor the snake as your expert tutor the
whole way up. Multiple-choice cards and Scratch-style drag blocks; the same
placement move carries you from syntax tokens at tier 1 to pipeline components
at tier 8.

The design lives in [DESIGN.md](DESIGN.md).

## 📲 Download

**[⬇ Latest APK](https://github.com/Matswm86/pyquest/releases/download/latest/pyquest-77c2589.apk)**
&nbsp;·&nbsp; [all builds](https://github.com/Matswm86/pyquest/releases)

Open the link on your phone, tap the file, and allow "install from this source"
when Android asks. Android 8.0 or newer (minSdk 26). The filename carries the
commit id on purpose, so your browser can never serve you a cached old build. If
the link 404s, a newer build has landed: grab the newest `pyquest-*.apk` off the
releases page.

Debug-signed. Reinstalling over a build with a different signature means
uninstalling the old one first.

## Pytor

Pytor is the tutor from [PyLearn](https://pytor.mwmai.no/), and inside PyQuest he
is an expert rather than a beginner's guide: core-developer-level Python, senior
software engineering, and production-level AI and LLM engineering. He shows up
in four places:

| Where | What he does | Needs network |
|-------|--------------|---------------|
| Track screen | One line on what to do next: a streak about to lapse, the tag you keep missing, the next level | no |
| Every question | Progressive hints that never give the answer, then the expert note after you check | no |
| Pytor tab, Codex | 95 dense reference notes across Python, software engineering and AI/LLMs, searchable offline | no |
| Pytor tab, Chat | A conversation with the expert, with the game's context attached | yes |

Chat talks to the tutor service behind PyLearn in its `quest` mode; the model
key stays on the server. When there is no connection, or the service is down or
busy, Pytor answers from the Codex instead and says so. You can turn the online
half off in the You tab.

## State

| Piece | Status |
|-------|--------|
| Gradle + Compose app, CI build, emulator smoke test, rolling APK release | shipped |
| `mcq`, `blocks`, `order`, `fill`, `pipeline` question types | shipped |
| Track, Pytor and You tabs; hint sheet; expert notes; review misses | shipped |
| Leitner spaced repetition, first-try accuracy, weak-tag tracking, XP, streak | shipped |
| Tiers 1 to 7 | 30 to 32 questions each, 5 levels each |
| Tier 8 "AI consultancy sims" | 19 questions, 3 levels, two pipeline sims |
| Pytor's Codex | 95 entries: 32 Python, 30 engineering, 33 AI/LLM |

231 questions in total. Every one carries hints and an expert note, and every
multiple-choice option order and block tray is shuffled at authoring time so
nothing can be learnt by position.

## Building

Every APK is built in GitHub Actions, which also publishes it to the rolling
`latest` pre-release and rewrites the download link above to match. Pushing to
`main` is the whole release process. CI validates the curriculum and the Codex,
runs the unit tests, builds the APK, then boots it on an emulator and checks
that the track actually rendered.

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
python3 tools/validate_codex.py
```

The validator refuses an answer that uses a block the tray does not supply, an
`order` question that leaves tray blocks unused, a multiple-choice answer that is
not among the options, a duplicate id, a level gap, an explanation shorter than
40 characters, a question without hints, a hint that quotes the correct option,
and a question without a `deep` note of at least 60 characters. CI runs both
validators before the APK job starts.

### Question shape

```json
{
  "id": "t7.l3.q2",
  "tier": 7,
  "level": 3,
  "type": "order",
  "prompt": "Order the query-time RAG pipeline.",
  "tray": ["re-rank the candidates", "retrieve the top candidates", "rewrite the question", "generate with citations", "check every citation"],
  "answer": ["rewrite the question", "retrieve the top candidates", "re-rank the candidates", "generate with citations", "check every citation"],
  "accept": [],
  "explain": "Rewrite, retrieve wide, re-rank narrow, generate, verify.",
  "hints": ["Re-ranking needs candidates to rank, so it follows retrieval."],
  "deep": "Retrieve 20 to 50 candidates cheaply, then re-rank to the best 5 to 8 with a cross-encoder...",
  "xp": 25,
  "tags": ["rag", "pipelines"],
  "reviewed": "2026-09-05"
}
```

`type` is `mcq` (tap one of `options`), `blocks` (place a subset of `tray` in
order, distractors allowed), `order` (every tray block must be used), `fill`
(blocks go into `{0}`-style gaps in `template`) or `pipeline` (blocks are stages
with `ms` and `cost`, wired against the budget in `brief`). `accept` holds
alternative orderings that are also correct. `hints` are progressive, mildest
first, at most three. `deep` is Pytor's note: the mechanism, the idiom, the trap.

### Codex shape

`app/src/main/assets/codex/codex.json` holds `entries`, each with `id`, `domain`
(`python`, `engineering` or `ai`), `title`, `tags`, a one-sentence `summary`, a
`body` of at least 200 characters, optional `code`, and `related` ids that must
exist. Search is offline and weights title and tags over body text.

## Why there is no Python interpreter in the app

Grading compares the assembled sequence to `answer`, or to any entry in `accept`.
That keeps the APK small, the app fully offline and the feedback instant, at the
cost of not grading free-form typed code. Typing Python on a phone keyboard is
miserable, and [PyLearn](https://pytor.mwmai.no/) already covers that on desktop.
