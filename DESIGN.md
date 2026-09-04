# PyQuest: Android Python quiz game

**Status**: 2026-09-04. Scaffold built, CI green, APK published.
Tier 1 is complete at 30 questions; tier 10 ships a 6-question preview so the top
of the ladder is playable rather than only described. Tiers 2 to 9 are unwritten.
**One line**: a native Android game that walks a player from `print("hello")` to
scoping and pricing an AI-engineering consultancy job, using multiple-choice cards
and Scratch-style drag blocks the whole way up.

Working name is PyQuest, and it shares the Pytor snake mascot with the PyLearn web
app so the two read as siblings rather than competitors.

## 1. How this differs from PyLearn

PyLearn (`pytor.mwmai.no`) is a browser app where you *type* Python and Pyodide runs
it. PyQuest is a native Android game where you *assemble* and *choose* answers with
your thumbs, offline, in 90-second sittings. Different input device, different
session length, different failure mode. PyLearn teaches you to write code; PyQuest
teaches you to recognise correct code and correct architecture fast.

The two share a mascot and a difficulty vocabulary, nothing else. No shared code.

## 2. The central design idea: one block system, ten tiers

The drag-block metaphor is not just for beginners. The same interaction scales:

| Tier | You drag | Example |
|------|----------|---------|
| 1-2  | tokens   | `print` `(` `"hello"` `)` |
| 3-5  | statements | `if x > 5:` / `return total` / `except ValueError:` |
| 6-8  | code units | a `@dataclass` field, a pandas method chain step |
| 9-10 | pipeline components | `chunk` -> `embed` -> `upsert` -> `retrieve` -> `rerank` -> `generate` |

That is the whole reason the game holds together from hello-world to consultancy:
at every tier the player is doing the same physical action (order these pieces
correctly) against progressively larger pieces.

## 3. Level ladder (10 tiers)

| # | Tier | Covers |
|---|------|--------|
| 1 | First Words | `print`, strings, comments, quotes, the REPL idea |
| 2 | Data & Variables | int/float/str/bool, naming, f-strings, casting, `type()` |
| 3 | Control Flow | `if`/`elif`/`else`, comparison + boolean ops, `for`, `while`, `range`, `break` |
| 4 | Collections | list, dict, set, tuple, indexing, slicing, comprehensions, `enumerate`, `zip` |
| 5 | Functions & Errors | `def`, args/kwargs, `return`, scope, `try`/`except`/`finally`, `raise`, custom exceptions |
| 6 | Structure | modules + imports, `pathlib`, `class`/`__init__`/methods, dunders, `@dataclass`, context managers |
| 7 | Real Python | venv + pip, type hints, `pytest`, `logging`, `argparse`, JSON/CSV, `httpx`, `asyncio` basics |
| 8 | Data & ML Foundations | numpy, pandas, train/test split, leakage, overfitting, precision/recall/F1/ROC-AUC, baselines |
| 9 | LLM Engineering | tokens, embeddings, cosine similarity, chunking, vector stores, RAG order, prompt structure, tool calling, streaming, temperature vs top-p, evals, cost-per-1k-token maths |
| 10 | Consultancy | client scoping, RAG vs fine-tune vs bigger-model, latency and cost budgets, PII/GDPR and data residency, eval harness design, hallucination mitigation, failure triage, monitoring, writing the SOW, estimating spend |

Each tier: 5 levels x 6 questions = 30 questions, plus a boss round.
Full v1 corpus is 300 questions + 10 boss rounds. Tier unlocks at >=80% on the boss.

### What tier 9 and 10 questions actually look like

Tier 9, block-order type:
> "Order the RAG ingestion pipeline." Blocks: `load PDF`, `split into chunks`,
> `embed chunks`, `upsert to vector store`, `write metadata`. Distractor block in
> the tray: `re-rank results` (belongs to query time, not ingestion). Dragging the
> distractor in is the mistake the level is built to catch.

Tier 9, multiple choice with a number:
> "A 40k-token context, 800-token answer, 12k requests/month. At $3/M input and
> $15/M output, what is the monthly bill?" Four options, one off-by-a-decimal,
> one that forgets output tokens, one correct, one that prices output as input.

Tier 10, scenario card:
> "A Norwegian law firm has 40,000 PDFs, needs internal Q&A, data may not leave the
> EU, budget 150k NOK, wants it live in six weeks. Which do you propose?"
> Options: fine-tune an open model on the corpus / RAG over the PDFs with an
> EU-hosted model / paste documents into a long-context prompt per query /
> train from scratch. Correct answer is RAG, and the explain card says why the
> other three fail on cost, residency, or timeline specifically.

Tier 10, drag-block:
> "Assemble the eval harness the client's procurement team will accept."
> Blocks: `golden question set`, `human rubric`, `automated judge`,
> `regression run on every deploy`, `cost + latency logging`. Distractor:
> `vibe-check in the chat window`.

## 4. Question types

Five types, all thumb-first, all gradeable without running Python:

1. **mcq**: 4 options, one correct, tap.
2. **blocks**: drag tokens/statements from a tray into ordered slots.
3. **fill**: code shown with `___` gaps; drag the right token into each gap.
4. **order**: reorder a shuffled list (pipeline steps, execution order).
5. **match**: pair left column to right (method to what it returns, metric to when you use it).

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
5. Tiers 1-5 corpus (150 questions).
6. Tier map, boss rounds, stats.
7. Tiers 6-10 corpus (150 questions), which is the part worth the most care.

Tiers 9 and 10 are where this app earns its existence, and they are also the part
that will date fastest, so their JSON needs a `reviewed` date field per question.
