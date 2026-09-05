# PyQuest: Android Python quiz game

**Status**: 2026-09-05. All eight tiers written (231 questions), Pytor active in
the app, CI green with an emulator smoke test, APK on the rolling release.
**One line**: a native Android game that walks a player from `print("hello")` to
scoping and pricing an AI-engineering consultancy job, using multiple-choice cards
and Scratch-style drag blocks the whole way up, with Pytor the snake as an
expert tutor at every step.

Pytor is the mascot and tutor shared with the PyLearn web app, so the two read as
siblings rather than competitors. Inside PyQuest he is deliberately the expert
version: see section 8.

## 1. How this differs from PyLearn

PyLearn (`pytor.mwmai.no`) is a browser app where you *type* Python and Pyodide runs
it. PyQuest is a native Android game where you *assemble* and *choose* answers with
your thumbs, offline, in 90-second sittings. Different input device, different
session length, different failure mode. PyLearn teaches you to write code; PyQuest
teaches you to recognise correct code and correct architecture fast.

The two share a mascot and a difficulty vocabulary, nothing else. No shared code.

## 2. The central design idea: one block system, eight tiers

The drag-block metaphor is not just for beginners. The same interaction scales:

| Tier | You drag | Example |
|------|----------|---------|
| 1-2  | tokens   | `print` `(` `"hello"` `)` |
| 3-5  | statements | `if x > 5:` / `return total` / `except ValueError:` |
| 5-6  | code units | a `@dataclass` field, a pandas method chain step |
| 7-8  | pipeline components | `chunk` -> `embed` -> `upsert` -> `retrieve` -> `rerank` -> `generate` |

That is the whole reason the game holds together from hello-world to consultancy:
at every tier the player is doing the same physical action (order these pieces
correctly) against progressively larger pieces.

## 3. Level ladder (8 tiers)

| # | Tier | Covers |
|---|------|--------|
| 1 | Hello, world | print, strings, comments, variables, f-strings |
| 2 | Types & collections | int/float/str/bool, lists, dicts, sets, slicing |
| 3 | Control flow | if/elif/else, comparisons, for, while, range, comprehensions |
| 4 | Functions & modules | def, return, args and kwargs, scope, imports, exceptions |
| 5 | Data wrangling | pathlib, JSON and CSV, pandas DataFrames, groupby, joins |
| 6 | APIs, async & tests | httpx, REST, async/await, pytest, type hints, logging |
| 7 | LLM engineering | tokens, embeddings, chunking, retrieval, tool use, evals, cost control |
| 8 | AI consultancy sims | capstone: scoping, architecture, budgets, handover |

Tier 8 is a capstone rather than a lesson tier: every question is a client brief.

### What tier 7 and 8 questions actually look like

Tier 7, block-order type:
> "Order the RAG ingestion pipeline." Blocks: `load PDF`, `split into chunks`,
> `embed chunks`, `upsert to vector store`, `write metadata`. Distractor block in
> the tray: `re-rank results` (belongs to query time, not ingestion). Dragging the
> distractor in is the mistake the level is built to catch.

Tier 7, multiple choice with a number:
> "A 40k-token context, 800-token answer, 12k requests/month. At $3/M input and
> $15/M output, what is the monthly bill?" Four options, one off-by-a-decimal,
> one that forgets output tokens, one correct, one that prices output as input.

Tier 8, scenario card:
> "A Norwegian law firm has 40,000 PDFs, needs internal Q&A, data may not leave the
> EU, budget 150k NOK, wants it live in six weeks. Which do you propose?"
> Options: fine-tune an open model on the corpus / RAG over the PDFs with an
> EU-hosted model / paste documents into a long-context prompt per query /
> train from scratch. Correct answer is RAG, and the explain card says why the
> other three fail on cost, residency, or timeline specifically.

Tier 8, drag-block:
> "Assemble the eval harness the client's procurement team will accept."
> Blocks: `golden question set`, `human rubric`, `automated judge`,
> `regression run on every deploy`, `cost + latency logging`. Distractor:
> `vibe-check in the chat window`.

## 4. Question types

Five formats, all thumb-first, all gradeable without running Python:

1. **mcq**: four lettered options, tap one.
2. **blocks**: drag blocks from a tray into an ordered answer row, distractors allowed.
3. **order**: same interaction, but every tray block must be used.
4. **fill**: blocks drop into typed gaps inside real, rendered code. Blocks are
   coloured by kind, blue for a name, amber for an expression, violet for a call,
   so a player starts reading what a hole wants before they can explain why. The
   tray always holds more blocks than there are holes, which kills elimination.
5. **pipeline**: the capstone. Wire a client's inference pipeline into numbered
   stages while estimated p95 latency and cost per request update live against the
   ceilings the brief states, turning coral the moment either is breached.

Every format accepts both interactions: tap a block to place it in the next free
slot and tap a filled slot to empty it, or long-press to pick a block up and drop
it where the finger is. The tap path is what makes the game playable one-handed on
a bus; the drag path is what makes it feel like Scratch. Neither is optional.

## 5. The runtime decision: no Python on the device in v1

PyQuest does **not** bundle a Python interpreter. Grading compares the assembled
answer to a canonical sequence, with an `accept` list of alternative orderings where
the semantics genuinely allow more than one (e.g. two independent assignments).

The cost of this: the game cannot grade free-form typed code. That is deliberate,
because typing Python on a phone keyboard is miserable and PyLearn already covers it
on desktop. The benefits: the APK stays small (no Chaquopy, roughly 25-40 MB per ABI,
no NDK in CI), the app works fully offline, and grading is instant and deterministic.

If free-form code ever becomes necessary, the cheap route is a WebView loading
Pyodide as an optional download, not Chaquopy. That is a v2 question, not a v1 one.

## 6. Technical design

**Stack**: Kotlin + Jetpack Compose, JDK 17, compileSdk 35, minSdk 26,
kotlinx.serialization. Versions are pinned in `gradle/libs.versions.toml` and
verified by the first green CI build rather than by a local toolchain.

**Drag and drop**: Compose `pointerInput` + `detectDragGesturesAfterLongPress` on
each tray block, with drop-target bounds collected via `onGloballyPositioned`. A
block snaps to the nearest slot whose bounds contain the pointer. Tapping a block
also places it in the next empty slot, so the whole game is playable one-handed
without dragging at all. That tap fallback is an accessibility requirement, not a
nicety, and it is also what makes the game work on a bumpy bus.

**Content as data**: every question lives in
`app/src/main/assets/curriculum/tier_NN.json`, never in Kotlin. Authoring the corpus
is then a text job, and a JSON-schema check in CI catches malformed questions before
they ship.

```json
{
  "id": "t9.l3.q2",
  "tier": 9, "level": 3,
  "type": "blocks",
  "prompt": "Order the RAG ingestion pipeline.",
  "tray": ["embed chunks", "split into chunks", "re-rank results", "load PDF", "upsert to vector store"],
  "answer": ["load PDF", "split into chunks", "embed chunks", "upsert to vector store"],
  "accept": [],
  "explain": "Re-ranking happens at query time, not during ingestion.",
  "xp": 25,
  "tags": ["rag", "pipeline"]
}
```

**Progress and repetition**: one JSON blob in SharedPreferences holds a Leitner box
(0-4) per question id, plus XP, streak and last-played date. A wrong answer drops the
question to box 0 and re-queues it 3 questions later. Mastery per tier is the share of
that tier's questions in box 3 or higher, which is a more honest number than "levels
completed".

The design first called for Room here. A 300-question blob rewrites in under a
millisecond and Room would have added KSP to the very first CI run for no gain, so the
build ships SharedPreferences. Room becomes worth it when per-tag queries arrive, for
example "drill every question tagged rag that I have missed twice".

**Scoring**: XP and a daily streak. No hearts and no energy timer, because gating
practice behind a wait is the mechanic that makes people quit.

**Build and ship**: GitHub Actions only. The workflow runs JDK 17 plus
`android-actions/setup-android@v3`, installs `platforms;android-35`, runs
`assembleDebug`, attaches the APK to the rolling `latest` pre-release under a
SHA-suffixed filename, and rewrites the README download link to match. No APK is
ever produced on a workstation.

**Screens** (7 total): Home with the tier map, Tier detail with 5 level nodes,
Question screen (the one screen that matters), Explain card after each answer,
Boss round with a timer, Stats with per-tier mastery bars, Settings.

## 7. Build order

1. Repo scaffold with a pinned version catalog, CI green on an empty app.
2. Question screen with the `mcq` and `blocks` types, hardcoded sample questions.
3. JSON loader + schema check in CI.
4. Room progress + Leitner scheduling.
5. Tiers 1-4 corpus.
6. Tier map, boss rounds, stats.
7. Tiers 5-8 corpus, which is the part worth the most care.

Tiers 7 and 8 are where this app earns its existence, and they are also the part
that will date fastest, so their JSON needs a `reviewed` date field per question.

All seven steps are done as of 2026-09-05; the Room step was replaced by the
SharedPreferences blob described above.

## 8. Pytor inside the game

Pytor is not decoration. He is the reason a player who is stuck does not quit,
and the reason the game can claim to teach rather than test. Four surfaces, in
the order a player meets them:

1. **Track screen.** One coaching line chosen from the player's state, in strict
   priority: a streak that lapses at midnight, then the tag they keep missing
   (seen 3+ times, missed over 25%), then the daily goal, then the next
   unmastered level. Tapping the line opens the tier it points at.
2. **Every question.** An "Ask Pytor" chip opens a bottom sheet with the
   question's `hints`, one at a time, mildest first, never the answer. After the
   check the same sheet shows the `deep` note: the mechanism, the idiom, the
   trap. Hints used are counted and shown on the level-cleared screen, not
   penalised; a penalty would teach players not to ask.
3. **The Codex.** 95 entries across Python, software engineering and AI/LLM
   engineering, bundled in the APK and searched offline with a small weighted
   scorer (title 8, tag 5, prefix 3/2, body up to 3, all-terms bonus 6). Every
   entry is reachable by its own title; the unit test pins that.
4. **Chat.** The tutor service behind PyLearn, called in its `quest` mode with
   the game's context (tier, level, the current question, weak tags). The
   persona prompt makes Pytor a world-class expert who leads with the answer,
   cites the mechanism, and never invents a name or a number. On any failure,
   network, 429, timeout, the chat falls back to the Codex and says so.

**Why both offline and online.** The app has to work on a bus. The Codex and the
hints are what Pytor knows without a signal; the chat is what he knows with one.
Neither replaces the other, and the settings toggle lets a player keep him
offline entirely.

**Why hints are authored, not generated.** A generated hint can leak the answer
or be wrong; an authored one is reviewed with the question. The validator
refuses a hint that quotes the correct multiple-choice option verbatim.

**Shuffling.** The first draft of the corpus had the correct option at position
A in 151 of 152 multiple-choice questions and every `order` tray already in
answer order. The authoring step now shuffles options, trays and block lists
with a seed per question, and the session shuffles multiple-choice options again
at display time so a requeued miss cannot be answered by position.

**Bugs fixed in the same pass.** The header counter jumped back to 1 after a
requeued miss (it now counts distinct solved questions); the system back button
quit the app from a question (BackHandler); the daily ring and streak read
stored fields with no date attached and showed stale values on a new day (both
are now functions of today's date, with unit tests); and the level-done screen
said "Tier cleared" after every level.

