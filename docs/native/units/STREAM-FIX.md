# STREAM-FIX — a streaming reply's per-update work no longer grows with its length

Date: 2026-11-01. Branch: `native/streamfix-2026-11-01`, created from `claude/native-stream-parsing-bijagc` at
`7db496b2` (the M0 tip). **Base: current.** There is no `origin/native/tx-2026-11-01`, so nothing of S2-TX was under
these changes. Write set: `native/engine/transcript/**`, the transcript and markdown-block files of
`native/ui/kit-chat/**`, `native/core/markdown/**`, and this report.

**Finish line:** while a 6,000-word reply streams, each publish and each frame does a bounded amount of work, whatever
the reply's length. A JVM benchmark proves it, and the device Macrobenchmark can be rerun unchanged.
**Non-goal:** moving the engine onto S2-MD's `:core:markdown` parser, and the S2-KC/S2-CHAT renderers.

Shipping state: implemented and verified on the desktop JVM. Both modules compile for Android. Committed and pushed to
the branch. There is no PR, no CI run, no device or emulator run (this container has no `/dev/kvm`), no signing and no
release. `master` and tags are untouched.

---

## 1. What was wrong

The device run (`native/proof-p3-2026-11-01`, `docs/native/slice0/device/p3/stream6000*`) measured
`frameDurationCpuMs` P50 24.1 ms. Read per frame, the three iterations show a step: frames run at about 11 ms, then
jump to 25–28 ms during the 400-line fence and mostly stay there.

The JVM benchmark below reproduces the part of that which grows, and names it. P3 published every block whole.

| Where | Work per update with P3's shape | Grows with |
|---|---|---|
| UI, every word landing (≈ every frame while a backlog drains) | `KitReplyBlock` lays out the whole live block again: `text.substring(0, shown)` gives `BasicText` a new paragraph. With the fence open, that is up to 10,593 characters per frame. Characters laid out per frame, by tenth of the reply: 117 → 1,251 → 4,559 → 7,968. | the open fence (the newest block) |
| UI, every frame while words fade | the fade's offscreen layer (`CompositingStrategy.Offscreen`) covers the whole live block, which is the full 400-line fence | the open fence |
| Engine, every publish | `StreamingBlocks.tail()` copies the whole open fence (up to ≈ 10,800 characters) into the tail row | the open fence |
| Engine, every publish | `derive()` re-adds all of the newest turn's rows (≈ 140 for this reply) to the persistent list and to the position map, and copies the prompt's text again | the whole newest turn |

The engine runs on the lane, not on the main thread, and costs microseconds. The frame cost is the UI's re-layout of
the growing block.

## 2. What was built

### 2.1 `:engine:transcript`

| File | Change |
|---|---|
| `internal/StreamingBlocks.kt` | Any block longer than `StreamingChunk.LINES = 16` lines is cut at line ends into **settled chunks** plus a short **live tail chunk**. This covers an open or closed fence, a paragraph or a table of many lines. A chunk keeps the key rule `b:<msg>/<part>:<offset>`, so each chunk is a stable lazy item. A settled chunk's instance never changes. The live chunk always holds at least one line. Blank code lines at a chunk's end are kept (one `\n` is dropped, not all of them). `Block` gains `continued` and `continues`. The new counter `tailChars` counts characters copied into tail blocks. |
| `state/Rows.kt` | `BlockRow` gains `continued: Boolean = false, continues: Boolean = false` (defaulted, so existing callers compile). |
| `internal/TranscriptModel.kt` | **Incremental newest turn.** The published list is kept as prefix + newest rows. A publish over the same prefix and turn keeps the leading rows that are the same instances, and rewrites only the suffix that changed, in the list and in the positions map. A prompt row is kept while its part's version holds, so the prompt text is not copied on each publish. Chunk flags flow into the rows. |
| `TranscriptEngine.kt` | `TranscriptCounters.tailChars` and `rowWrites` (rows removed + added in the published list). |

### 2.2 `:ui:kit-chat` (transcript files)

| File | Change |
|---|---|
| `transcript/TranscriptParts.kt` | `KitReplyBlock(…, continued = false, continues = false)`. On a side where a chunk meets the next chunk, it drops the row gap, the code box's rounding and its inner padding. The chunks of one fence therefore draw as one code surface, and a paragraph's chunks as one paragraph. The code box now fills the 700 dp column, so every chunk has the same width. `onTextLayout` reports to the registry. |
| `stream/Reveal.kt` | `RevealRegistry.layouts` / `layoutChars`: text layouts of reply blocks and the characters they laid out. The benchmark gates on these, and a PB-17 check can use them. |

Unchanged: `transcript/TranscriptList.kt`. The stale `MotionTokens` import is **already fixed on the base**
(`ui.design.motion.MotionTokens`, `QUICK`/`STANDARD`, the same change as proof-p3's), so nothing was left to do.
`markdown/*` and `core/markdown` were not changed (see D2 and open problem 3).

### 2.3 Benchmarks and tests (kept for the coordinator)

| Where | What |
|---|---|
| `engine/transcript/src/commonTest/…/Reply6000.kt` | The `perf:stream-6000` generator. It is identical to `:quality` `Stream6000`, to proof-p3's `Stream6000` and to kit-chat's `P3Fixtures`: the same seed gives the same text and delta cuts. |
| `engine/transcript/src/commonTest/…/StreamCostTest.kt` (5 tests) | Deterministic counters, red first (see §3). |
| `engine/transcript/src/desktopTest/…/StreamCostBenchmark.kt` | **JVM benchmark, engine half.** Measures the lane's time for apply + derive per publish, by tenth of the reply, for the reply 1×, 2× and 4× long. Output: `engine/transcript/build/streamfix/engine.json`. |
| `ui/kit-chat/src/desktopTest/…/transcript/ReplyStreamFrameBenchmark.kt` | **JVM benchmark, UI half.** Headless `runComposeUiTest`, 412 × 860 dp, at a 16 ms test clock. The reply streams at its recorded pace (publishing at most once per 50 ms, like the engine) into `KitTranscriptList` + `KitReplyBlock`, with reveal and fade at Full. It runs twice: `whole` (P3's shape) and `chunked` (16-line rows). Per frame it records the wall time of recomposition, layout and draw, the text layouts, the characters laid out and the item compositions. Output: `ui/kit-chat/build/streamfix/ui.json`. |
| `ui/kit-chat/src/desktopTest/…/transcript/StreamFeed.kt` | A test stand-in for the engine's rows. Kit modules may not depend on engines, so it reproduces the same block and chunk rules for this fixture. |
| `ui/kit-chat/src/desktopTest/…/transcript/ChunkSeamTest.kt` (2 tests) | A 40-line fence or paragraph as three chunk rows takes the room of the whole block, within 1 px per seam. Without the seam handling it would be 16–48 dp taller. |

## 3. Catalog rows covered

| Test | Row | Proves |
|---|---|---|
| `B-CHAT-096 PB-11 per-publish engine work stays flat while a 6,000-word reply grows` | B-CHAT-096 (perf of the streamed reply), plan 13 PB-11 | per publish: tail characters ≤ 1,024 and row writes ≤ 6 over the whole reply. The last tenth writes no more rows than the first. The blocks cover the reply exactly, at their offsets. The fence ends as 26 settled chunks of ≤ 16 lines. |
| `an open 400-line fence streams as chunks of 16 lines that join into one fence` | (09 §3.8 open-fence rule) | the live chunk is always ≤ 16 lines, open, code. Settled chunks keep their instances (`===`). The flags are right. On close, the keys are the same and the chunks join into exactly the fence text. |
| `a paragraph of many lines splits at line ends into continued prose chunks` | — | prose chunks 16 + 16 + 8 lines, with the right flags, and the joined text is exact |
| `a fence chunk that ends in blank code lines keeps them` | — | no lost blank line at a seam |
| `a whole-part rewrite of a chunked reply re-parses once and settles` | (09 §3.3 "whole part wins") | one full parse, exact text, nothing live |
| `B-CHAT-096 PB-18 the lane's cost per publish does not grow with the reply` | B-CHAT-096, PB-18 (`chat.derive`) | timing report. It fails if the 4× reply's late p50 is over 2× the 1× reply's. |
| `B-CHAT-096 PB-11 a 6,000-word reply lays out a bounded chunk per frame, not the growing block` | B-CHAT-096, PB-11 | **chunked:** ≤ 2,000 characters laid out in any frame, and the last tenth lays out no more per frame than the first tenths. **whole:** more than 5× that, which shows the gate detects the old shape. A loose wall-clock check: the open fence's p50 is within 2× + 2 ms of prose. Both runs stay at the live edge, finish every reveal and draw every character in order. |
| `ChunkSeamTest` (2) | — | chunk rows are seamless |

**Red first.** The deterministic tests were written before the engine change. Against the P3 engine they do not
compile, because the counters and flags they assert did not exist. The benchmark was run on the unchanged engine first;
§4.1 has the "before" numbers. The UI benchmark carries its own before/after: `whole` is P3's shape.

## 4. Commands and results

All commands run from `native/`, with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`,
JDK 17 installed for the toolchain, and Gradle 9.7.1. The container has 4 vCPUs and software raster.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:transcript:desktopTest` (on the base, before any change) | 19/19 pass |
| 2 | `./gradlew :ui:kit-chat:desktopTest` (on the base) | 67/67 pass |
| 3 | `./gradlew :engine:transcript:desktopTest --tests '*StreamCostBenchmark*'` on the unchanged engine (StreamCostTest moved aside) | §4.1 "before" |
| 4 | `./gradlew :engine:transcript:desktopTest` | **25/25 pass**: PublishCountTest 4, RecordedStreamTest 2 (the 5,004-part stream still publishes exactly like the Flutter flush), TranscriptReducerTest 13, StreamCostTest 5, StreamCostBenchmark 1 |
| 5 | `./gradlew :engine:transcript:compileAndroidMain :ui:kit-chat:compileAndroidMain :ui:kit-chat:desktopTest --rerun-tasks --continue` | BUILD SUCCESSFUL. There are no compiler warnings in either module. kit-chat desktopTest 68/68 (the 67 existing + ReplyStreamFrameBenchmark; ChunkSeamTest was added after this run) |
| 6 | `./gradlew :feature:chat:compileKotlinDesktop` (the one consumer, unchanged) | compiles, because the new parameters are defaulted |
| 6b | `./gradlew :ui:kit-chat:desktopTest :engine:transcript:desktopTest --rerun` on the committed tree | **kit-chat 70/70, engine 25/25** (whole 482 frames > 8.3 ms, chunked 11; chunked p95 4.07 ms) |
| 7 | `tool/check_commits.sh claude/native-stream-parsing-bijagc HEAD` | OK, all commits |
| 8 | `-Poc.werror=true` on the same tasks | fails in **`:ui:kit`** (another unit's files: `KitField.kt:261` deprecated `maxLength`, `KitRow.kt:162–163` unnecessary `!!`). Not in this write set. My modules have no warnings (run 5). |

### 4.1 Engine: lane time per publish (`StreamCostBenchmark`, p50 in µs per tenth of the reply, median of 3 runs)

| Reply | Before (P3) | After |
|---|---|---|
| 1× (35,379 chars) | 4.8, 6.5, 6.6, 6.9, 7.0, 9.3, 10.8, 12.3, 14.3, **16.6** | 2.3, 1.8, 1.8, 1.7, 2.3, 2.4, 2.6, 2.5, 2.5, **2.7** |
| 2× (70,760) | 2.8 … **23.6** | 1.7 … **2.8** |
| 4× (141,522) | 2.9, 6.7, 12.7, 15.2, 21.8, 26.3, 31.0, 37.4, 40.8, **48.2** | 1.4, 1.9, 2.0, 2.0, 2.5, 2.3, 2.7, 2.9, 2.8, **3.3** |
| Late p50, 4× / 1× | 2.9 | 1.2 |

What is left of the slope (1.4 → 3.3 µs over 560 rows) is `buildTurn` collecting the newest turn's row references
before the identity scan. That is O(rows) pointer copies with no text and no list rebuild; see open problem 4.

### 4.2 UI: frames (`ReplyStreamFrameBenchmark`, 5,439 test frames per run, first 60 dropped)

| Measure | `whole` (P3's shape) | `chunked` (STREAM-FIX) |
|---|---|---|
| Rows at the end | 137 | 162 |
| Frame p50 / p95 / p99 | 3.42 / 10.58 / 16.51 ms | **2.79 / 4.10 / 5.26 ms** |
| Frames > 8.3 ms / > 16.7 ms | 593 / 52 | **9 / 1** |
| Open-fence frames p50 / p95 | 7.68 / 15.57 ms | **2.94 / 4.41 ms** |
| Frame p50 by tenth of the reply | 3.69, 4.63, **7.59, 8.27**, 2.94, 2.87, 3.10, 3.29, 3.22, 2.90 | 2.92, 2.97, 3.01, 2.87, 2.75, 2.70, 2.84, 2.67, 2.69, 2.57 |
| Frame p95 by tenth | 6.40, 8.62, **14.00, 17.33**, 9.10, 4.49, 4.73, 4.84, 4.84, 4.33 | 4.25, 4.32, 4.67, 4.19, 4.00, 4.04, 4.02, 3.93, 3.73, 3.43 |
| Characters laid out per frame, mean by tenth | 117, 1,251, **4,559, 7,968**, 1,344, 105, 123, 102, 104, 102 | 117, 179, 213, 215, 130, 105, 123, 102, 104, 102 |
| Max characters laid out in one frame | 10,593 | **448** |
| Max text layouts in one frame | 3 | 3 |

The other two runs gave the same picture: whole 487 and 482 frames > 8.3 ms, chunked 11 and 11. The wall times are relative numbers from a
shared container with software raster and a 60 Hz test clock. **The release-build device numbers are owed (open
problem 1).**

## 5. Deviations

| # | Brief / plan says | Done | Why |
|---|---|---|---|
| D1 | Base on `origin/native/tx-2026-11-01` if it exists | Base = current (`7db496b2`) | That branch does not exist (`git ls-remote` on 2026-11-01). |
| D2 | "Incremental markdown parsing of the tail only" in `:core:markdown` | The fix is in the engine's P3 splitter (`StreamingBlocks`, plan 09 §3.8's stand-in). `:core:markdown` is untouched. | `:core:markdown` is not on the base. S2-MD's lives only on `native/md-2026-11-01` (unmerged), and the engine does not use it yet (S2-TX wires it). The splitter already re-parsed only the tail. What grew was the tail itself (an open fence is one block); chunking fixes that. Merging S2-MD's module here would only create a conflict for the coordinator. |
| D3 | Open problem 1 of P3 proposed ~32-line chunks | 16 lines | With 16 lines the live chunk's re-layout and its offscreen fade layer stay under half a phone screen (16 × 20 sp). A 400-line fence becomes 26 rows. |
| D4 | Plan 09 §3.7: one `BlockRow` per Markdown block | One row per **chunk** of ≤ 16 lines, with `continued` / `continues` | This is what makes the per-update work bounded. Plan 09 §3.7/§3.8 should record the chunk rule and the two flags (amendment request below). |
| D5 | The code box wrapped its text width | The code box fills the 700 dp column | Every chunk of a fence must be the same width to read as one surface. It also matches `KitCodeBlock`, which fills its width. |
| D7 | Write set: "the transcript and markdown-block files of kit-chat" | `stream/Reveal.kt` was edited (two counters on `RevealRegistry`) | It is the transcript's reveal clock (P3 built it with the transcript list). The change only adds counters. |
| D6 | Test names from catalog rows | `B-CHAT-096` plus plan 13's PB ids where one applies; four structural engine tests and the seam tests have plain names | The catalog has no row for chunking or seams. P3's structural tests followed the same rule. |

## 6. Requests

**Library catalog requests:** none. No new dependency.

**For `:feature:chat` (chat-lead; outside this write set).** `ChatTranscript.ChatRow` must pass the two flags, or
chunk rows draw with a gap and rounded corners at every 16th line:

```kotlin
is BlockRow ->
    KitReplyBlock(
        …,
        motion = motion,
        continued = row.continued,
        continues = row.continues,
    )
```

Without this change everything still compiles and the performance fix still applies. Only the seams show.

**Plan amendment (plan 09 §3.7, §3.8; plan 13 §5.2):** "a block over 16 lines is published as chunk rows of ≤ 16 lines
(`continued` / `continues`); the live block is the last chunk". This turns 13 §5.2 "one block re-measures per tail
update" into a bounded cost.

**For the coordinator, to rerun the emulator Macrobenchmark:**
1. Merge `native/streamfix-2026-11-01` and `native/proof-p3-2026-11-01`. `git merge-tree` shows they merge cleanly.
2. Apply the two-line `ChatTranscript` change above.
3. Run the proof's `:benchmark` `StreamBenchmark.stream6000` and `history5000` exactly as recorded in
   `docs/native/slice0/device/p3/`.

The proof module and its activity are untouched by this branch. `StreamProofActivity` calls only `ChatTranscript`, so
it picks up the chunk rows without changes. The JVM benchmarks rerun with:

```
./gradlew :engine:transcript:desktopTest --tests '*StreamCostBenchmark*'
./gradlew :ui:kit-chat:desktopTest --tests '*ReplyStreamFrameBenchmark*'
```

## 7. Open problems

1. **The device gate is still owed.** It needs an emulator rerun of `stream6000` and `history5000` (with the
   `ChatTranscript` change) and the NX721J phone run. The JVM reproduces the growth during the open fence and removes
   it. It does **not** reproduce the device's plateau *after* the fence closes: on the JVM, the `whole` frames drop back
   to ≈ 3 ms once the fence settles. My hypothesis is that it is tied to the one 21,000-px-tall fence row on Android:
   - its offscreen fade layer was over the GPU's texture limit while it was live;
   - the whole fence display list stayed on screen after it closed.

   Chunking removes both, because no row is taller than 16 lines. In the new traces, check that:
   - `frameDurationCpuMs` stays flat by position;
   - no RenderThread frame shows a layer larger than the screen.

   If the plateau remains, the next suspect is the fade layer (`kitFreshFade` `Offscreen`) on the emulator's software
   GL.
2. **`KitCodeBlock` (code/, S2-KCB's) does O(source) work on every update of an open fence:** `Redactor.redact`,
   `split('\n')` and the line list. Its 12-line transcript cap (plan 09 §9.2) bounds layout but not that work. When
   S2-CHAT renders engine rows through `KitMarkdownBlock` → `KitCodeBlock`, there are two options:
   - each chunk row is its own short source (then the cap and the Copy button must apply to the whole fence, not to
     each chunk);
   - the renderer draws only the first chunk up to the cap and hides the continued chunks.

   Either way, `KitCodeBlock` should skip redaction and the split for lines past the cap. This needs a decision by
   S2-KC/S2-CHAT and the kit-chat owner.
3. **`:core:markdown` (S2-MD) and the chunk rule.** When S2-TX moves the engine onto `StreamingMarkdown`, either:
   - its `BlockDelta.tail` must cut long blocks the same way (a 16-line chunk rule on `Block.Code`, `Prose` and
     `Table`), or
   - the engine must chunk the blocks it gets from it.

   `StreamCostTest` is the contract either way.
4. **The remaining O(rows) on the lane.**
   - `buildTurn` still collects the newest turn's row references on every publish (with ≈ 560 rows this is the
     1.4 → 3.3 µs slope).
   - `groupTurns` and the prefix-match loop are O(messages) and O(turns) of the whole transcript on every derive.

   Both are microseconds, off the main thread. S2-TX's per-message row cache (P3 open problem 3) removes them.
5. **A paragraph with no line breaks is not chunked.** Only line ends are cut points, so a 6,000-word single-line
   paragraph would still re-lay out whole. LLM paragraphs are far shorter. A fix would cut at sentence ends past about
   2,000 characters, at the cost of a visible line break.
6. **`-Poc.werror=true` fails in `:ui:kit`** (another unit's files; §4 run 8).
7. **Item compositions.** `ReplyStreamFrameBenchmark` reports up to 13 item compositions in one frame. That count
   includes items entering the viewport and the final settle. It is reported, not gated; PB-17's `≤ 3` per publish
   needs a dedicated `TranscriptRecompositionTest` (S2-KC).
