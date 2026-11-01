# Slice 0 · P3: transcript streaming

Date: 2026-11-01. Branch: `native/p3-transcript-2026-11-01`, created from `claude/native-p3-transcript-vi0221` at `6627d320`.

Inputs:
- `docs/design/native-rewrite-2026-11-01/`: `DRAFT-STATUS.md`, `plan/01-architecture-decision.md` §11 (binding; P3 row),
  `plan/09-chat-streaming-genui.md` §2–§3, §7, §8, §14–§17, `plan/08-motion-animation.md` §5.1, `plan/04-protocols-networking.md`
  §7.6 and §11.2, and both `audit/codex-*.md` files;
- `docs/native/slice0/P1-skeleton.md` and `native/AGENTS.md`;
- the Flutter fixtures `test/fixtures/api2/events.sse`, `test/perf_chat_test.dart`, `test/chat_rebuild_budget_test.dart`
  and `lib/ui/screens/chat/chat_stream.dart`.

## Result

| ADR §11 P3 acceptance | Status |
|---|---|
| A recorded 5,000-part stream runs through `TranscriptEngine` and a block `LazyColumn` with the draw-phase fade | **Done on the JVM.** The 5,004-part OC2 stream runs through the real async engine handle. A 6,000-word reply runs through the engine into the block `LazyColumn` with the fade, in a headless desktop UI test. |
| Publish counts match the Flutter fixture | **Done.** The counts are exact on all four `perf_chat_test` history shapes (spaced 10 → 10, burst 100 → 1). On the 5,004-part stream, the engine's 18,461 publishes equal the Flutter flush algorithm replayed on the same schedule (§4). |
| No frame over budget in a release-build Macrobenchmark | **Not done. It is still owed.** There is no device or emulator in this container. JVM frame times are recorded in §5 as a proxy only; they are not the gate. A 400-line open code fence already goes over an 8.3 ms budget on the JVM (§5, open problem 1). |

Shipping state: implemented, verified locally on the desktop JVM, compiled for Android, committed and pushed to the
branch. There is no PR, no CI run, no device run, no signing and no release.

---

## 1. What was built

### 1.1 `:engine:transcript` (the P1 placeholder replaced)

| File | Content |
|---|---|
| `state/Wire.kt` | P3-local copies of the transcript vocabulary: `MessageInfo`, `Part`, `PartType`, `ToolStatus` (unknown → Running, T3), `DeltaField`, `NoticeKind`, `RunOutcome`. Also the `GatewayEvent` subset the reducer consumes: `Connected`, `RunStarted`, `RunEnded`, `MessageUpserted`, `PartUpserted`, `PartDelta`, `TranscriptStale`, `Notice`. See D1. |
| `state/Rows.kt` | The immutable rows: `PromptRow`, `BlockRow` (Prose / Heading / Quote / ListItem / Code; `live`, `open`, `origin`), `ThoughtRow`, `WorkLineRow`, `NoticeRow` and `FooterRow`. Also `RowKind` (the contentType), `NewestFirst`, `RunFacts` and `TranscriptState`. `NewestFirst` is the O(1) reversed view over the oldest-first `PersistentList` (plan 09 §3.7 `ReversedImmutableList`). `TranscriptState` holds `revision`, `rows`, `positions` and `run`, plus `indexOf(key)` and `newestKey`. The package `engine.**.state.**` is already listed in `config/compose-stability.conf`. |
| `internal/StreamingBlocks.kt` | An incremental, line-based block splitter that stands in for `:core:markdown` `StreamingMarkdown` (§3.8). Settled blocks keep their instance forever, and only the tail block is rebuilt per append. An open fence is never rescanned; it is only checked for its closing line. A text that is not `old + more` resets the splitter (counted as a full parse). |
| `internal/TranscriptModel.kt` | The working model, single-threaded on the lane (§3.2, §3.3). It applies events with version stamps, keeps deferred parts and deltas within the 256-key / 1 MiB bounds (overflow → `refreshRequests`), and asks for a re-read instead of a replay on stale, reconnect and run-end-after-drop (R20). `derive()` groups turns by prompt; notices never start a turn. Rows are rebuilt only for turns whose messages changed (the TurnCache). The persistent prefix is reused when it is a prefix of the new list, so a publish never copies 5,000 rows. Equal rows reuse the previous instance. |
| `TranscriptEngine.kt` | `TranscriptEngine` (`@SingleIn(ConnectionScope)`, Metro-injected: gateway, `AppDispatchers`, `Clock`, `ConnectionCoroutineScope`) has `open(sessionId)`. `TranscriptHandle` has `state: StateFlow<TranscriptState>`, `submit`, `consume(Flow)`, `loadHistory`, `awaitIdle`, `counters`, `refreshRequests` and `close`. Its lane is `dispatchers.serial(…)`. `Publisher` is the §3.5 50 ms leading/trailing publisher (see D9). `TranscriptCounters` count publishes, derives, `displayPassMessages`, `markdownParses`, `tailReparses` and `skippedWhileHidden`. A handle with no watcher keeps applying events, skips derivation, and does one derive when the first watcher arrives (§3.5 "nobody watching"). |

### 1.2 `:ui:kit-chat` (new module; namespace `io.github.eslamasabry.oc.ui.kitchat`)

| File | Content |
|---|---|
| `stream/Reveal.kt` | `RevealState`: `shown` (a layout read), `runs` and `frameNanos` (draw reads), plus `frames` and `landings` counters. `RevealPace` (45 cps, 250 ms drain, 250 ms fade, 50 ms dt cap) and `wordEndAtOrAfter`. `drive()` is the `withFrameNanos` clock from plan 09 §8.2 / 08 §5.1; it parks when nothing is left to reveal or fade. `RevealRegistry` is keyed by row key and remembered outside the lazy items; a block waits for its predecessor in the same part. `KitMotionLevel` (Full/Calm/Off) is P3-local (D5). |
| `stream/FreshFade.kt` | `Modifier.kitFreshFade`: `graphicsLayer` with `CompositingStrategy.Offscreen` **only while runs fade** (the strategy is read in the layer lambda). Then `drawWithCache` / `onDrawWithContent` draws each run's `getPathForRange` with `BlendMode.DstOut` at `1 − EaseOutCubic(age/250 ms)`. Spans never change. `Modifier.growOnly`: a `LayoutModifierNode` that animates growth only with spring ζ = 1, stiffness 700, takes shrinks at once, and adds a 4 dp descender allowance (plan 08 step 6; see D4). |
| `transcript/TranscriptList.kt` | `TranscriptListState`: `following`, `atLiveEdge` (48 dp slack) and `awayFromLatest` (index > 0 or > 480 dp; read only by the pill). `KitTranscriptList`: `LazyColumn(reverseLayout = true)`. While following, `requestScrollToItem(0)` runs in a `SideEffect`, which is the same frame as an index-0 insert. A drag (`DragInteraction.Start`) stops following; reaching the edge starts it again. `KitJumpToLatest` (test tag `jump-to-latest`, the Flutter key) uses fade + rise under Full and fade only otherwise. A tap glides when the target is near; when far, it jumps to index 2 and then glides. |
| `transcript/TranscriptParts.kt` | `KitReplyBlock` (a streamed block reveals, fades and grows; a loaded block draws at full length). While revealing under Full it votes `preferredFrameRate(FrameRateCategory.High)`. It uses `TextMotion.Static` and the 700 dp column. Also `KitPromptBubble` (≤ 85 % wide), `KitQuietLine` and `KitBlockKind`. |

### 1.3 `:feature:chat` (new module)

`ui/ChatTranscript.kt`: `ChatTranscript(state, words, list, reveal, motion)` is `items(state.rows, key = { it.key },
contentType = { it.kind })` over kit-chat parts only (K1 holds). `ChatTranscriptWords` carries every non-server word;
P3 passes it in (D6). There is no ViewModel, no entries and no composer; those are S2-CHAT.

### 1.4 Tests and fixtures

| Where | What |
|---|---|
| `engine/transcript/src/commonTest` | `TranscriptReducerTest` (13 tests), `PublishCountTest` (4), `FlutterFlushModel` (a line-for-line model of `_scheduleStreamFlush` / `_flushStreamDeltas`), `Events` (the Flutter `_history(count, fragmented)` shapes). |
| `engine/transcript/src/desktopTest` | `Oc2Sse`: a test-only OC2 SSE → `GatewayEvent` adapter for the plan 04 §7.6 transcript rows. `RecordedStream`: the 5,004-part stream. `RecordedStreamTest` (2 tests). `resources/oc2/events.sse` is a copy of the Flutter fixture. |
| `ui/kit-chat/src/desktopTest` | `RevealTest` (4): word snapping; re-layouts only on word landings and the clock parks; Off motion shows words at once; a loaded block never animates. |
| `quality/src/test/…/p3` | `TranscriptStreamUiTest` and `Stream6000` (in `:quality`; see D7). |

**The 5,000-part fixture.** The fixture is the real OC2 recording `test/fixtures/api2/events.sse` (OC2 beta 18600). That
turn has a queued prompt, an instructions notice, two steps, reasoning, text and a `read` tool call. It is replayed 834
times with seeded content, keeping every frame's wire shape and changing only ids, times and text.

| Measure | Value |
|---|---|
| Parts per turn | 6 (prompt text, 2 reasoning, 2 text, 1 tool) |
| Parts in total | 5,004 |
| SSE frames | 61,448 |
| Text deltas | 40,596, of 1–12 characters at a jittered 40–90 per second |
| Text | 258,230 characters |
| Virtual length | 1,285 s |

The SSE body is about 12 MB, so it is built in memory by the seeded generator instead of being committed. It runs
through the same adapter as the real recording, so the reducer only ever sees wire-shaped input.

**The 6,000-word stream** (`perf:stream-6000`, plan 09 §14.3):
- 6,413 words, 35,379 characters;
- 3 code fences (one of them 400 lines), a table and two lists;
- 5,435 text deltas of 1–12 characters at a jittered 40–90 per second (seeded).

---

## 2. Environment set-up (done first, as asked)

| Step | Command / result |
|---|---|
| SDK tools | `curl -O https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip`, unzipped to `/opt/android-sdk/cmdline-tools/latest`; `ANDROID_HOME=/opt/android-sdk`. |
| Licences | `yes \| sdkmanager --licenses` |
| Packages | `sdkmanager "platform-tools" "platforms;android-36" "platforms;android-37.0" "platforms;android-37.1" "build-tools;37.0.0" "build-tools;36.1.0"`. That is what `Sdk.kt` (compileSdk 37 minor 1) and P1 §2 need. Installed: `android-36`, `android-37.0`, `android-37.1`, `build-tools` 36.1.0 and 37.0.0, `platform-tools`. |
| JDK 17 | `apt-get install openjdk-17-jdk-headless`: 17.0.20.1. Gradle runs on JDK 21.0.12.1 (P1 D1). `~/.gradle/gradle.properties` turns off toolchain auto-download. |
| Long builds | `~/.claude/settings.json` sets `env.BASH_DEFAULT_TIMEOUT_MS=1800000` and `BASH_MAX_TIMEOUT_MS=3600000`. They apply from the next session; this session used explicit timeouts. |
| Maven Central | The proxy answered **HTTP 429** to Gradle's HEAD requests on `repo.maven.apache.org`, as it did in P1. A container-only `~/.gradle/init.d/central-mirror.gradle.kts` (not in the repo) does two things: it puts Google's Central mirror `maven-central.storage-download.googleapis.com/maven2`, Google and the plugin portal first in `pluginManagement` (`beforeSettings`), and it rewrites every Central entry to that mirror. The checked-in settings are unchanged. `native/local.properties` has `sdk.dir` and is gitignored. |

---

## 3. Commands and results

All commands run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`,
on 4 vCPUs and 15 GB RAM, with the repo's caps (`-Xmx2g`, 2 workers).

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:transcript:compileAndroidMain :ui:kit-chat:compileAndroidMain :feature:chat:compileAndroidMain :engine:transcript:desktopTest :ui:kit-chat:desktopTest :quality:test -Poc.werror=true --rerun-tasks --continue` | Every compile passes with warnings as errors, for Android and desktop. `:engine:transcript:desktopTest` 19/19, `:ui:kit-chat:desktopTest` 4/4. `:quality:test` 18 tests, 17 passed: `DesktopShellUiTest` 1, `GraphSmokeTest` 2, `KitOnlyPlantedTest` 2, `KonsistRulesTest` 11 (K1 sees `:feature:chat`), and `TranscriptStreamUiTest` 1 (30.0 s). **`ModuleOwnersTest` fails, and it fails on the base commit `6627d320` too:** `:tools:arb2xml` and `:tools:flutter-migration-readers` (merged from P5/P6) have no `MODULE_OWNERS` rows. Those rows belong to the coordinator and the P5/P6 owners, so they were left alone; the two new P3 modules have rows. |
| 2 | `./gradlew :quality:test --tests '*TranscriptStreamUiTest*' --rerun` (repeated) | Passes every time. The numbers per run are in §5. |
| 3 | `./gradlew :engine:transcript:dependencies …`, `:ui:kit-chat:dependencies …` | Versions in §6. |

`GraphSmokeTest` still proves the P1 S4 accessor with the new constructor: the connection graph resolves `AppDispatchers`,
`Clock` and `ConnectionCoroutineScope` for `TranscriptEngine`.

Tests in `:engine:transcript`:

| Test | Proves |
|---|---|
| `B-CHAT-121 turns group by prompt, a notice never starts a turn, rows come newest first` | turn grouping (§3.6), notice placement, newest-first order, `indexOf` |
| `B-CHAT-096 settled blocks keep their instances through ten tail updates` | the perf_chat_test "ten tail updates" case: 10 tail re-parses, 0 full parses; the heading and code `BlockRow`s are the same instances (`===`) |
| `an open fence is one live code block and closes into a settled one` | the open-fence rule (§3.8); the key is kept when the block settles |
| `a whole-part upsert equal to the streamed text keeps the blocks, a rewrite re-parses once` | "whole part wins" (§3.3); `session.text.ended` with equal text costs nothing |
| `a delta before its part…` / `parts for an unknown message wait…` | deferred deltas and parts |
| `events of another conversation change nothing` | no apply and no publish for another session |
| `deferred text is bounded and overflow asks for a refresh` | the 256-key / 1 MiB bounds [new] |
| `B-CHAT-005 a stale transcript and a reconnect ask for a re-read instead of a replay` | R20 |
| `the end of a run settles live blocks and adds one footer` | one footer per turn, live → settled, a stopped work line |
| `newest turn only - ten deltas into a 100-turn chat pass at most 20 messages` | the `chat_rebuild_budget_test` bound (≤ 2 × updates) |
| `the settled prefix is shared, never copied, between publishes` | across a 1,000-message history, exactly one row instance changes per tail update |
| `B-CHAT-096 a fragmented 5,000-part reply builds one row per part` | the Flutter fragmented shape |
| `B-CHAT-096 publish counts match the Flutter perf fixture for every history shape` | §4 |
| `the first delta after quiet is published with no delay` | the leading edge |
| `another conversation streaming at 20 per second publishes nothing here` | `OtherConversationQuietTest`, engine half |
| `a hidden handle keeps applying but skips derivation until watched` | §3.5 "nobody watching" |
| `the Flutter OC2 recording reduces to …` | the real `events.sse` gives: prompt, notice, "pong.", work line (1 call, Done), "pong.", footer, all in one turn |
| `P3 a recorded 5,000-part stream publishes like the Flutter flush and ends settled` | §4 and §5.1; the end state is 834 prompts, 834 work lines, 834 footers and 5,933 rows, with unique keys, nothing live, and `indexOf` consistent |

`TranscriptStreamUiTest` checks all of the following:
- the newest row stays at the bottom in all 5,472 frames of the stream;
- every character arrives, in order;
- no block is left live or revealing;
- after two swipes, following stops and "Jump to latest" shows;
- a second reply does **not** pull the reader down;
- tapping the pill returns to index 0, turns following back on and hides the pill.

---

## 4. Publish counts vs the Flutter fixture

Flutter's `perf_chat_test` asserts `stream_flushes ≤ tokens`. Its flush (`_scheduleStreamFlush`) works like this:
1. the first delta in a task flushes in a microtask;
2. deltas inside the 50 ms timer window set a dirty flag;
3. the timer flushes once if the flag is set.

`FlutterFlushModel` is that algorithm, run in virtual time. The native `Publisher` defers its leading publish to the
lane's next turn, after the events already queued; that is the microtask equivalent (D9). The engine counts below come
from the real async handle on a `StandardTestDispatcher`.

| Flutter fixture shape (`_history`) | Spaced: 10 tokens, 60 ms apart | Burst: 100 tokens in one task |
|---|---|---|
| 1,000 parts, not fragmented | Flutter 10 · native **10** | Flutter 1 · native **1** |
| 5,000 parts, not fragmented | 10 · **10** | 1 · **1** |
| 1,000 parts, fragmented | 10 · **10** | 1 · **1** |
| 5,000 parts, fragmented | 10 · **10** | 1 · **1** |

Plan 09 §14.2 writes "burst → ≤ 2". Flutter's own algorithm gives exactly 1 for a burst in one synchronous task, and the
native engine gives the same 1.

**The recorded 5,004-part stream**, replayed at its frame times. The match is exact:

| Count | Value |
|---|---|
| Adapted events | 59,779 (58,110 of them change what is drawn) |
| Native engine publishes | **18,461** |
| The same algorithm fed every drawing change (the ADR design, ≤ 1 per 50 ms) | **18,461**, equal |
| Flutter today: delta flushes | 14,804 |
| Flutter today: direct `setState` for non-delta events | 17,514 (Flutter coalesces these per vsync, so they are triggers, not frames) |
| Stream length | 1,285 s of virtual time: about 14 publishes per second, under the 20 per second cap |

---

## 5. Time per frame on the JVM (a proxy, not the gate)

**The release-build Macrobenchmark on a device (J2 stream, J3 fling; plan 09 §14.3, S-9/S-10 on NX721J at 120 Hz) is
still owed.** So are the emulator relative runs for SP-1 and the 240 fps recording for SP-7. Nothing below substitutes
for them.

### 5.1 Engine derive cost (`RecordedStreamTest`, 5,004-part stream)

The model is replayed synchronously, deriving at each of the 18,461 publish points. JIT warm-up (the first 10 %) is
dropped. The time is the wall time of `derive()` with `System.nanoTime`.

| Run | p50 | p95 | p99 | max | apply, total for 59,779 events |
|---|---|---|---|---|---|
| 1 | 46 µs | 150 µs | 240 µs | 7.1 ms | 96 ms |
| 2 | 51 µs | 139 µs | 224 µs | 7.8 ms | 87 ms |
| 3 | 58 µs | 162 µs | 295 µs | 18.0 ms | 108 ms |

Derivation runs on the lane, never on the main thread, so these times are not frame time. The max values are single
GC or JIT outliers.

### 5.2 Frames (`TranscriptStreamUiTest`, the 6,000-word stream)

How the frames were measured:
- headless `runComposeUiTest` (v2), Skia raster, on a 412 × 860 dp box;
- `mainClock.advanceTimeByFrame()`, so 16 ms frames (60 Hz test clock), 5,472 frames in all;
- each frame's wall time covers recomposition, measure, layout and draw of the scene;
- the engine lane runs between frames and is timed on its own;
- the first 60 frames are dropped.

Budgets: 8.3 ms (120 Hz) and 16.7 ms (60 Hz).

| Run | All frames p50 / p95 / p99 / max | > 8.3 ms | > 16.7 ms | Live tail is prose: p50 / p95 / p99 | Live tail is the open 400-line fence: p50 / p95 / p99 |
|---|---|---|---|---|---|
| A | 3.64 / 14.42 / 30.84 / 98.96 ms | 1,069 | 227 | (not split) | (not split) |
| B | 3.70 / 10.46 / 16.46 / 34.21 | 477 | 54 | 3.31 / 5.75 / 8.22 | 6.89 / 14.20 / 23.50 |
| C | 3.53 / 13.60 / 19.74 / 34.68 | 832 | 126 | 3.08 / 5.68 / 8.06 | 8.40 / 18.46 / 23.19 |
| D | 3.65 / 11.64 / 18.00 / 60.39 | 731 | 74 | 3.18 / 6.24 / 9.26 | 7.82 / 16.13 / 21.62 |

Other counts from the same runs:
- 1,712 publishes and 139 rows;
- 5,136 word landings over 7,547 reveal-clock frames;
- 1,575 frames with the 400-line fence open and live;
- engine lane per frame: p50 0.03–0.04 ms, p95 0.22–0.32 ms.

Readings:
- **With a prose tail, the JVM stays inside a 60 Hz budget and mostly inside 120 Hz**: p95 5.7–6.2 ms, p99 8–9 ms.
  `RevealTest` confirms that fade frames are draw-only: re-layouts ≤ word landings + 2, and the clock parks when idle.
- **The open 400-line fence is the cost.** Each word landing re-lays out the whole open code block (up to about
  10,000 characters), which gives p95 14–18 ms. This is S-9's "a tail update re-lays out 1 block", but that one block
  is huge. See open problem 1.
- Run-to-run spread is ±25 % on a shared 4-vCPU container with software raster. Treat these as relative numbers only.

---

## 6. Versions resolved (from Gradle)

There are no new catalog entries and no version changes; everything matches P1 §5.

| Item | Version |
|---|---|
| Kotlin / stdlib | 2.4.21 |
| CMP `ui`, `foundation`, `material3` | 1.13.0-beta01 |
| `kotlinx-collections-immutable` | 0.5.2 (`PersistentList.addAll` is deprecated in favour of `addingAll`, which is now used) |
| `kotlinx-coroutines(-test)` | 1.11.0 |
| `kotlinx-serialization-json` | 1.12.0 (newly used, in `:engine:transcript` desktopTest only) |

Verified in the CMP 1.13.0-beta01 jars (SP-3):
- `androidx.compose.ui.preferredFrameRate(Modifier, FrameRateCategory)` **is exported in common UI** (`FrameRateKt`,
  `FrameRateCategory.High`). No `KitPlatform.frameRateHint` seam is needed. Whether the vote shows in Perfetto is still
  owed on a device.
- `ui-text` 1.13.0-beta01 has **no `DrawAnnotation`**, as plan 09 §0.2 found for beta02. The offscreen `DstOut` mask
  stays.

The `runComposeUiTest` v1 API is deprecated in CMP 1.13 and fails `-Poc.werror`, so the P3 UI tests use
`androidx.compose.ui.test.v2.runComposeUiTest`. P1's `DesktopShellUiTest` still uses v1. It compiled in the same
`-Poc.werror=true` run, so `oc.jvm.library` evidently does not turn that deprecation into an error there. That was not
investigated; align it when `:quality` is next touched.

---

## 7. Deviations from the plan, and why

| # | Plan says | Built | Why |
|---|---|---|---|
| D1 | `MessageInfo` / `Part` in `:core:model` (09 §2.2); `GatewayEvent` in `:core:domain` (04 §11.2) | Trimmed copies in `…engine.transcript.state` | The brief limits P3 to `:engine:transcript` plus a chat module; core modules have other owners. The field names follow the plan, so S2-MODEL swaps the imports. |
| D2 | Block key `b:<partId>:<startOffset>`; thought key `thought:<partId>` (09 §3.7) | `b:<messageId>/<partId>:<offset>` and `thought:<messageId>/<partId>` | **A plan bug the fixture found.** OC2 part ids are per-message ordinals (`text-0`, `reasoning-0`; 04 §7.6), so the plan's keys repeat in every step and turn. The 5,004-part stream had 3,540 unique keys for 5,933 rows. Tool (call id) and notice keys are already unique. Plan 09 §3.7 and §7.2 should be corrected. |
| D3 | `StreamingMarkdown` over the JetBrains parser in `:core:markdown` (S2-MD, SP-2) | A line-based block splitter inside the engine | `:core:markdown` does not exist yet, and P3 needs only block boundaries and the stable-prefix rule. There are no inline spans and no tables; a table is drawn as prose. |
| D4 | 09 §8.4: `animateContentSize` on the live block, with a `LayoutModifierNode` as fallback | 08 §5.1 step 6: the `growOnly` `LayoutModifierNode` | 08 is the later, more exact rule (growth only, shrinks taken at once, velocity carries). `animateContentSize` also animates shrinks. |
| D5 | `MotionLevel` / `resolveMotion` from `:ui:design` (06 §4.8) | `KitMotionLevel` in kit-chat | It does not exist in `:ui:design` yet, and that module has another owner. |
| D6 | Copy from `:ui:words` | `ChatTranscriptWords` passed in by the caller; no copy in P3 modules | Avoids editing `:ui:words` (another owner). S2-CHAT maps it to string resources. |
| D7 | `TranscriptRecompositionTest` and the stream test in `:feature:chat` | `TranscriptStreamUiTest` lives in `:quality`; `quality/build.gradle.kts` gained `testImplementation` of `:ui:kit-chat`, `:feature:chat` and `compose.foundation` | Konsist K1 checks **every** file under `feature/`, test sources included, so a headless UI test there fails the rule. P1 put its headless UI test in `:quality` for the same kind of reason. **Shared files touched, minimally:** `native/MODULE_OWNERS` (two rows for the new modules) and `native/quality/build.gradle.kts`. Not touched: `settings.gradle.kts` (module discovery picked the new modules up), the catalog and `build-logic`. |
| D8 | PublishCountTest "burst → ≤ 2" | Asserts exactly 1 | That is what Flutter's algorithm gives for a single-task burst (§4). |
| D9 | `Publisher.changed` publishes synchronously on the leading edge (09 §3.5 code) | The leading publish is launched on the lane and runs after the events already queued | This is Flutter's microtask flush. A burst of N events delivered together publishes once with all N, not once with the first. The counts match Flutter exactly. |
| D10 | `TranscriptState.index: ImmutableMap` (newest-first) | `positions: PersistentMap` (oldest-first, updated incrementally) and `indexOf(key)` | Oldest-first positions never move when the tail changes, so a publish adds only the changed turn's keys instead of rebuilding 5,000 entries. |
| D11 | — | `revision` is the first field of `TranscriptState`, and `NewestFirst.equals` short-circuits on the same source list | `MutableStateFlow` compares with `equals`. With rows first, every publish would compare up to 5,000 rows (audit #12: measure comparison cost, not only recompositions). |
| D12 | `open(ref: ConversationRef)` reads history through the gateway; LRU of 3 handles; 60 s close | `open(sessionId)`; `loadHistory(page)`, `submit` and `consume` are fed by the caller; no LRU | History reads, merge fences, older pages, search, stall tracking and the remaining passes (settle, fold, `mergeTextParts`, action owners) are S2-TX. `refreshRequests` counts the re-reads the model asks for. |
| D13 | Item animation (`animateItem`), `cacheWindow`, `SelectionContainer` / `DisableSelection`, caret, accessibility traversal | Not built | Outside P3's proof: list, fade and follow. They are listed for S2-KC. |
| D14 | Real `Oc2EventAdapter` (`:protocol:oc2`) | A test-only `Oc2Sse` adapter for the transcript rows of 04 §7.6 | P3 needs wire-shaped input, not the production adapter. |
| D15 | Reveal measured "at 120 Hz test clock" (`RevealDrawOnlyTest`) | 60 Hz (the CMP test clock's 16 ms frame) | The test clock's frame length is fixed. The property (re-layout only on word landings) does not depend on the rate. |

## 8. Audit fixes applied

| Audit item | Applied |
|---|---|
| 01+03 MAJOR 12 (stability can hide cost; measure frame time and comparison cost, prefer reference reuse) | Rows reuse instances (settled block rows are cached per block, and equal rows reuse the previous instance). The prefix list is shared. `StateFlow` equality is short-circuited (D11). Frame time and derive time are measured, not only counts. No stability-config change. |
| 01+03 MAJOR 6 (a serial dispatcher is not a mutex) | Every model mutation and every publish runs on the handle's lane. There are no suspensions inside apply or derive, so the serial lane is enough here. |
| 04+05 (GatewayEvents exposes `StreamStatus` from `:core:net`) | Not touched: P3 does not use `GatewayEvents`. Recorded for whoever freezes 04 §11.2. |
| The others (WriterLane, storage contract, profile DTO, credential seam, release policy) | Not applicable to P3. |

## 9. Open problems

1. **The open 400-line fence goes over budget on the JVM** (p95 14–18 ms vs 5.7–6.2 ms for a prose tail). A word
   landing in an open fence re-lays out the whole fence. Proposed fix, to decide before S2-KC:
   - the splitter emits an open fence as settled chunks of about 32 lines plus a short live tail chunk;
   - the keys are `b:<msg>/<part>:<chunkStart>`;
   - kit-chat draws consecutive chunks of one fence as one continuous code surface, with no seams between them;
   - the cost per landing then stays O(chunk).

   This also suits SP-2's "tail re-parse with the open-fence wrapper ≤ 1 ms per append". The fallback is
   `KitCodeBlock` with a `LayoutModifierNode` that lays out only appended lines.
2. **The device gate is owed:**
   - the release-build Macrobenchmark J2/J3 (S-9, S-10, S-12) on NX721J;
   - the emulator runs relative to Flutter R (SP-1);
   - the 240 fps recording for the insert-at-0 hop (SP-7);
   - the frame-rate vote in Perfetto (SP-3);
   - Roborazzi descender goldens.
3. **Newest-turn rebuild is O(parts in the turn).** A single turn of 5,000 fragments (the Flutter fragmented shape)
   rebuilds its 5,000-row list on every publish. Settled rows are reused, but the list and the position map are rebuilt.
   S2-TX should cache rows per message inside the newest turn and port `mergeTextParts`.
4. **`ModuleOwnersTest` fails on the base branch:** `:tools:arb2xml` and `:tools:flutter-migration-readers` have no
   rows. That is for the coordinator.
5. **Konsist K1 checks feature test sources.** Either that is intended (and UI tests of features live in `:quality` or
   a future `:ui:testing`), or K1 should be limited to main source sets. That decision belongs to the `:quality` owner.
6. **`RevealRegistry` never forgets finished states** (plan 09 §14.4 says to forget them). This is bounded per
   transcript in P3 and should be fixed in S2-KC with the "draw full once done" set.
7. **Hidden-handle detail:** the first publish happens even with no watcher. `WhileSubscribed(5_000)` wiring through a
   ViewModel is S2-CHAT.
8. **The Android side was compiled, not run.** There is no Robolectric or emulator check of kit-chat; `TextMotion`,
   `preferredFrameRate` and the offscreen layer on Android are unverified.
9. **The JVM numbers come from a 4-vCPU cloud container** with software raster and a 60 Hz test clock. They are good
   for relative comparisons only (§5).
