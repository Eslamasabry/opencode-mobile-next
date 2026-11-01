# S2-TX: the transcript engine

Date: 2026-11-01. Branch: `native/tx-2026-11-01`, created from `claude/native-tx-events-2kh5h3` at `4589ba71`
(the M0 branch after the S2-ENTRY merge).

Write set: `native/engine/transcript/**` (plus this report). Nothing else was edited.

Inputs read: `native/AGENTS.md`; `docs/native/slice0/P3-transcript.md` and the other slice-0 reports;
`docs/native/units/*.md`; `native/contracts/s1.md`; the final plan (`plan/01` binding, `plan/15` unit row,
`plan/09` §2, §3, §14); `map/chat-genui.md` CHAT-GENUI-01…06; the catalog rows; and the Flutter sources being ported:
`lib/domain/transcript_settle.dart`, `lib/ui/screens/chat/transcript_turns.dart`, `chat_page_cache.dart`,
`chat_history.dart` and `chat_stream.dart`, with their tests.

## Result

Finish line (plan 15 S2-TX): *events become newest-first rows with version fences, merge and passes, published at
most every 50 ms with the first delta at once; the 09 §14.2 counters are green.*

| Part of the finish line | Status |
|---|---|
| Events → newest-first rows | **Done.** The engine consumes the core `GatewayEvent` / `MessageWithParts` vocabulary (the P3 `Wire.kt` copies are gone). Rows: prompt, Markdown blocks, thought, work line, notice, marker, assistant error, footer and "Load older". |
| Version fences | **Done.** Every message and part event stamps `++eventVersion` before anything else. A history read keeps everything that moved after it started. A read that a newer load replaced is dropped (load generation fence). |
| Merge | **Done.** `mergeHydrated` is a pure port of `_mergeHydratedMessages`, without the pending-send reconcile, which `:engine:delivery` owns. Also built: older pages by cursor, cursor reuse or HTTP 400/410 → "Reload", and the 150 ms refresh debounce. The late-edit rule, the deferred part/delta/message maps (256 keys / 1 MiB) and "never an error over rows" are kept. |
| Passes | **Done.** `settleStaleLive`, `markFoldedWork` (+ `isPassingWords`), `timelineDisplay`, `mergeTextParts` (linear), `turnActionOwners` and `deriveTurns` are pure functions. Each turn is cached; only the newest turn is re-derived. |
| ≤ 1 publish / 50 ms, first delta at once | **Done.** The counts equal the Flutter flush exactly on every `perf_chat_test` shape and on the 5,004-part recorded stream (18,462 = 18,462). |
| 09 §14.2 counters | **The engine-side counters are green:** `PublishCountTest`, `OtherConversationQuietTest` (engine half), `NewestTurnOnlyTest`, `TailUpdateTest` (engine half, still on the local block splitter) and `LinearMergeTest`. The rest belong to other modules: `BlockRelayoutCounterTest` and `RevealDrawOnlyTest` are `:ui:kit-chat`, and `TranscriptRecompositionTest` and the Compose half of the quiet test are `:feature:chat`. |

Shipping state: implemented, verified locally on the desktop JVM (56/56 tests), compiled for Android, committed and
pushed to the branch. There is no PR, no CI run, no device run and no merge.

## 1. What was built

All under `native/engine/transcript/src/`.

| File | Content |
|---|---|
| `commonMain/…/state/Rows.kt` | The public row vocabulary. `TranscriptRow` gained `messageId` and `position` (`TurnPosition`). New rows: `MarkerRow` (switch, running compaction), `AssistantErrorRow` (`recovered` when more steps followed) and `OlderRow`. `WorkLineRow` gained `thoughts`, `folded` and `callIds`. `FooterRow` gained `messageId` (the owner) and `stopped`. Also added: the full plan `RowKind` list, `TranscriptPhase` (Loading / Live / Failed), `OlderState`, and `RunFacts` (`crossedDrop`, `historyEpoch`, `retry`). `TranscriptState` gained `phase`, `older`, `messageOrder` and `messageCount`. The field names `:feature:chat` already reads are unchanged. |
| `commonMain/…/internal/passes/Passes.kt` | The display passes listed above. It also has `isPrompt` / `endsTurn` / `variantPart`, a local `ToolState.isDeclined` (trap T1; it moves to S2-MODEL) and `CopyProbe` for the linear-merge counter. |
| `commonMain/…/internal/TranscriptModel.kt` | The working model, single-threaded on the lane. It holds message and part entries with cached immutable snapshots. A snapshot is replaced on change and built at most once per publish while streaming. The model also holds the version maps, the deferred parts / deltas / late-edit messages with their bounds, the run facts and the inbox ids. `adoptHydrated` keeps an entry, with its streaming state and row caches, whenever the merge chose the live snapshot. |
| `commonMain/…/internal/History.kt` | `mergeHydrated` (pure) and the `VersionFence` it reads. |
| `commonMain/…/internal/RowBuilder.kt` | Turns become rows. Every finished turn is cached as a `TurnEntry` keyed by member snapshot identity (and by `busy` only while it still holds a running tool), and the persistent prefix is shared. The newest turn is **patched in place**: only rows after the first changed one are removed and re-added, which fixes P3 open problem 3. Unchanged rows keep their instances (`reuse`), and `messageOrder` changes only when the model's order revision does. |
| `commonMain/…/internal/StreamingBlocks.kt` | Unchanged from P3: the local stand-in for `:core:markdown` `StreamingMarkdown`. |
| `commonMain/…/TranscriptHandle.kt` | The handle has `state`, `loadOlder()`, `refreshRecent(reason)`, `reload(reset)`, `removeMessage(id)`, `submit`, `consume`, `awaitIdle`, `counters` and `close()`. The rest of the file: `RefreshReason`; the `HistorySource` seam (production is `gateway.messagePage`); `TranscriptCounters` (`publishes`, `derives`, `displayPassMessages`, `markdownParses`, `tailReparses`, `rowsReplaced`, `skippedWhileHidden`, `refreshRequests`, `historyReads`); and the `Publisher` (leading/trailing 50 ms, `lastPublishMs: Long?`, user actions at once). |
| `commonMain/…/TranscriptEngine.kt` | `open(ref: ConversationRef)` joins a warm handle. Handles are ref-counted: a handle with no holder closes after 60 s, and at most 3 unheld handles stay warm (LRU). The handle reads `gateway.events.all` (not `project`) and the newest page through the gateway. The P1 `session()` seam and the Metro `TranscriptAccessors` contribution are kept. |
| `commonTest/…` | `Events.kt` (event builders, the `Harness` = model + row builder, `ScriptedHistory`), `ApplyEventsTest`, `PassesTest.kt` (`SettleTest`, `LinearMergeTest`, `TurnPassesTest`), `HistoryMergeTest`, `PublishCountTest`, `EngineTest.kt` (`EngineTest`, `ReversedViewTest`) and `FlutterFlushModel` (unchanged). |
| `desktopTest/…` | `Oc2Sse` now emits core events. `RecordedStreamTest` is ported to the new model and row builder. `RecordedStream` is unchanged. |

### Behaviour worth knowing (from the Flutter port, now in the rows)

- **A finished turn has one work line.** It sits at the place of the first stretch of work and gathers every tool call,
  thought, passing sentence and mid-turn notice.
- **An explanation stays in view, and so does the answer.** Passing words fold; an explanation (more than one line, a
  paragraph break or a code block) does not. A turn that ended on a tool call keeps its last words in view.
- **The running turn keeps its work where it happened.** Thought rows show inline, with one work line per stretch.
  When the run ends, the thoughts fold into the turn's work line.
- **Text of consecutive steps is one reply stretch, owned by the first step** (Dart `_timelineDisplayParts`). The footer
  therefore goes to the last step that draws, which can be an earlier step than the one that ended the turn.
- **A prompt waiting in the server inbox is not drawn**, and it is not a turn boundary.
- **A running compaction is neutralised once anything follows it.** It draws nothing, and its message stays, so the
  message count is unchanged.

## 2. Rows covered

Unit row (plan 15): "CHAT-GENUI 01–06 engine rows; B-PROTO-015, 040, 088".

| Row | Where | Status |
|---|---|---|
| CHAT-GENUI-01 transcript model | consumed from `:core:model` | Used as is. The traps (T1–T10 helpers) belong to S2-MODEL; T1 `declined` is local until then. |
| CHAT-GENUI-02 live event pipeline | `ApplyEventsTest`, `HistoryMergeTest` (late edit), `PublishCountTest` | Done |
| CHAT-GENUI-03 history, older pages, merge | `HistoryMergeTest` | Done. **Not built:** the staged-revert boundary read, `prefetch` joining, and the withdrawn-prompt store. The engine keeps an in-memory withdrawn set. |
| CHAT-GENUI-04 opening excerpt | — | **Not built.** It needs `:engine:connection`'s first-paint cache, and that module does not compile on the M0 branch. See open problems. |
| CHAT-GENUI-05 display passes | `SettleTest`, `TurnPassesTest`, `LinearMergeTest`, `NewestTurnOnlyTest` | Done |
| CHAT-GENUI-06 turn rows | `ApplyEventsTest`, `RecordedStreamTest` | Done for prompt, block, thought, work line, notice, marker, error and footer. **Not built:** timeline step rows, background results, card calls and the excerpt row. |
| B-PROTO-015 (engine half) | `ApplyEventsTest` | Interrupted reasons give Stopped; a blank failure still fails the footer; tool input deltas are stored and never drawn. The adapter half is S1-OC2's. |
| B-PROTO-040 | `ApplyEventsTest` | Done: a finished step keeps the run busy and adds no footer; only `RunEnded` ends the run. |
| B-PROTO-088 (engine half) | `HistoryMergeTest` | Done: a drop mid-stream re-reads instead of replaying, no word arrives twice, and a run that crossed the drop re-reads at its end. The send half is S2-DLV's; the device flow is E2E. |
| Also exercised | B-CHAT-005, 096, 097, 116, 117, 121, B-PROTO-061 | See the test list in §3. |

## 3. Commands and results

All commands run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and `ANDROID_HOME=/opt/android-sdk`.

### Environment set-up (done first, as asked)

| Step | Command / result |
|---|---|
| SDK tools | `commandlinetools-linux-16111833_latest.zip` from `dl.google.com`, unzipped to `/opt/android-sdk/cmdline-tools/latest`. |
| Licences and packages | `yes \| sdkmanager --licenses`, then `sdkmanager "platform-tools" "platforms;android-37.1" "build-tools;37.0.0"` (the levels in `build-logic` `Sdk.kt`). Installed: `android-37.1`. |
| JDK | Gradle runs on JDK 21.0.12; `apt-get install openjdk-17-jdk-headless` provides the module toolchain (17.0.20). |
| Container-only Gradle files (not in the repo) | `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Maven Central mirror first, as P3 needed for the proxy's 429s. `~/.gradle/gradle.properties` turns off toolchain auto-download. `native/local.properties` (gitignored) holds `sdk.dir`. |
| Timeout | `~/.claude/settings.json` sets `BASH_DEFAULT_TIMEOUT_MS=1800000`. |

### Builds and tests

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:transcript:desktopTest` (baseline, before any change) | **Fails outside this unit:** `:engine:connection:compileKotlinDesktop` has 10 errors in `ConnectionRegistry.kt` / `ConnectionSession.kt` (`GatewayFactory.backend`, `StreamStatus.Disconnected`, …). This is the parallel core reconciliation the brief warned about. |
| 2 | `./gradlew :engine:transcript:compileKotlinDesktop` after dropping the `:engine:connection` dependency (D1) | BUILD SUCCESSFUL |
| 3 | `./gradlew :engine:transcript:desktopTest -Poc.werror=true` | **56 tests, 0 failures.** BUILD SUCCESSFUL. |
| 4 | `./gradlew :engine:transcript:compileAndroidMain :engine:transcript:compileTestKotlinDesktop -Poc.werror=true --rerun-tasks` | BUILD SUCCESSFUL. Android and desktop compile with warnings as errors. |
| 5 | `./gradlew :feature:chat:compileKotlinDesktop` (to see the downstream effect) | **Fails before reaching this unit's API:** `:ui:kit-chat` has an unresolved `MotionTokens` in `TranscriptList.kt` on the M0 branch. §6 lists the break this unit adds. |
| 6 | `git diff 4589ba71..HEAD \| grep -E '<private IPv4 ranges>\|<e-mail pattern>'` | No matches. |

Tests (`:engine:transcript:desktopTest`, 56):

| Class | Tests |
|---|---|
| `ApplyEventsTest` (20) | B-CHAT-121 turns group by prompt, a notice never starts a turn, rows come newest first · B-CHAT-096 settled blocks keep their instances through ten tail updates (TailUpdateTest, engine half) · CHAT-GENUI-02 an open fence is one live code block and closes into a settled one · CHAT-GENUI-02 a whole-part upsert equal to the streamed text keeps the blocks, a rewrite re-parses once · B-CHAT-121 a delta before its part is kept… · B-CHAT-121 parts for an unknown message wait for it · OtherConversationQuietTest events of another conversation change nothing · DeferredBoundsTest … · B-CHAT-005 a stale transcript and a reconnect ask for a re-read instead of a replay · B-PROTO-040 a finished step is not a finished run, the execution end is · B-PROTO-015 adapted run endings, failures and tool input deltas land as the engine expects · CHAT-GENUI-06 the end of a run settles live blocks and adds one footer · B-CHAT-121 thoughts show while the turn runs and fold into the work line when it ends · B-CHAT-121 a recovered failure stays quiet, a failure the turn ended on reads as failed (incl. T1 declined) · B-CHAT-121 an assistant error is its own row, quiet when more steps followed · B-CHAT-121 markers and compaction rows take their own form, a passed compaction draws nothing (incl. T9) · B-CHAT-097 notices keep only their kind and never create text rows · B-CHAT-121 a prompt waiting in the server inbox is not a prompt row… · B-CHAT-096 a fragmented 5,000-part reply builds one row per part · MessageDeletedTest … |
| `SettleTest` (9) | B-CHAT-116 ×3 and B-CHAT-117 ×6, ported one for one from `transcript_settle_test.dart` |
| `LinearMergeTest` (3) | B-CHAT-096 edge cases against the quadratic oracle (`slice_p4_4_test.dart`) · single part as is · 1,000 and 5,000 fragments, fragmented or not, copy ≤ 2 × text |
| `TurnPassesTest` (4) | B-CHAT-121 passing words fold, an explanation stays · one work stretch at the first place · the footer owner, none while running · notices and inbox prompts never start turns |
| `HistoryMergeTest` (8) | B-PROTO-061 newest page then cursor-only older pages at the far end · B-PROTO-061 a reused or expired cursor → Reload · CHAT-GENUI-03 what moved during a read wins · CHAT-GENUI-03 a replaced read is dropped · CHAT-GENUI-03 a burst shares one read after 150 ms · CHAT-GENUI-54 no error over rows · B-PROTO-088 drop → re-read, no word twice · CHAT-GENUI-02 late edit |
| `PublishCountTest` (5) | B-CHAT-096 publish counts match the Flutter perf fixture for every history shape (1,000/5,000 × fragmented or not; spaced = 10, burst = 1) · B-CHAT-096 the first delta publishes without advancing time; a trailing one lands at +50 ms · OtherConversationQuietTest 20/s for 10 s → 0 publishes, 0 derives · NewestTurnOnlyTest 1,000 turns, 10 deltas → ≤ 20 messages · HiddenHandleTest |
| `EngineTest` (2) | OtherDirectoryHandleTest (events of the open chat arrive from the `all` view after the graph moved directory) · HandleLruTest (ref count, 60 s close, LRU of 3) |
| `ReversedViewTest` (3) | ReversedViewTest · IndexStableOnAppendTest · B-CHAT-096 the settled prefix is shared, never copied |
| `RecordedStreamTest` (2) | B-CHAT-121 the Flutter OC2 recording reduces to prompt, notice, one work line, the answer and one footer · P3 the 5,004-part stream publishes like the Flutter flush and ends settled |

The `PublishCountTest` clock runs at `1_760_000_000_000 + virtual time`, never 0 (plan 09 §14.2).

### Recorded 5,004-part stream (`build/p3/engine.json`, JVM, this container)

| Measure | P3 (slice 0) | S2-TX |
|---|---|---|
| Engine publishes / Flutter-flush reference | 18,461 / 18,461 | **18,462 / 18,462**. A reconnect now publishes its run facts, which adds one drawing change. |
| Final rows | 5,933 | 5,852. Passing words and thoughts now fold into the work line, as Flutter does. |
| Full Markdown parses | 0 | 0 |
| `derive()` p50 / p95 / p99 | 46 / 150 / 240 µs | 62–65 / 520–560 / 830–1,060 µs |
| Apply, all 59,779 events | 87–108 ms | 281–311 ms |

The derive got slower because each publish now does real work. It builds the snapshot list, groups turns, runs the
four passes over the newest turn and rebuilds the per-turn display. P3 did only turn grouping. Derivation runs on the
lane, never on the main thread, so these times are not frame time. They are still well under the 50 ms window. The
apply cost rose because a text part's immutable snapshot is materialised once per change instead of never. Treat all
JVM numbers as relative: this is a shared 4-vCPU container.

## 4. Deviations from the plan, and why

| # | Plan says | Built | Why |
|---|---|---|---|
| D1 | `:engine:transcript` depends on `:engine:connection` (ADR §6.2); `ConnectionGraph.transcript` accessor | Dependency removed; the `ConnectionGraph.transcript` extension is gone; `@ContributesTo(ConnectionScope) TranscriptAccessors` stays | `:engine:connection` does not compile on the M0 branch (§3 #1), and the brief says to depend only on modules that compile. Only the accessor sugar needed it. **Restore once it compiles:** add `implementation(project(":engine:connection"))` and `public val ConnectionGraph.transcript: TranscriptEngine get() = (this as TranscriptAccessors).transcriptEngine`. |
| D2 | `open(ref: ConversationRef)` | Done. The P3 `open(sessionId: String)` is gone. | It is the plan's signature. Callers in `:quality` need the change (§6). |
| D3 | `notice:<partId>`, `marker:<partId>` (09 §3.7) | `notice:<messageId>/<partId>`, `marker:<messageId>/<partId>` | This is the slice-0 D2 reason (OC2 part ids repeat per message). Also, core `GatewayEvent.Notice` carries no part id, so a notice is stored as the fixed part `notice` of its message. |
| D4 | One `WorkLineRow` per turn, `work:<turnId>` | One per finished turn. The running turn has one per stretch of work, keyed `work:<turnId>` and then `work:<turnId>#<n>`. | Dart `_timelineDisplayParts` keeps the running turn's work where it happened, so the reader can follow along. The stretches merge into `work:<turnId>` when the turn ends. |
| D5 | `ThoughtRow` per reasoning part | Shown only in the running turn; a finished turn counts thoughts in its work line (`thoughts`). Placeholder thoughts are dropped. | Dart treats reasoning as work: it folds into the finished turn's work line (CHAT-GENUI-05/06). |
| D6 | Merged text is parsed as one Markdown text (`mergeTextParts` → blocks) | Rows keep one streaming block list per text part and concatenate them in order. `mergeTextParts` is ported (pure and linear) for copy and for `LinearMergeTest`. | Re-parsing the merged text would lose per-part streaming and keys. The rows match the merge except in one edge: a fragment that ends with a single `\n`, followed by one that continues the paragraph, renders as two blocks instead of one paragraph. |
| D7 | `turnActionOwners` counts the model-changed meta label (`metaAlways`, `_messageMeta().modelLabel`) | Not ported (treated as false) | It needs the catalog and the timestamps setting. That belongs with S2-CHAT's footer meta. |
| D8 | `foldableNotice` excludes background agent results | Not excluded | It needs `BackgroundAgentResult` (BackgroundTasks, plan 09 §3.10). |
| D9 | `TranscriptState.index`, `inbox`, `revert`, `unanswered`, `stall` | `positions` is kept (P3 D10 name; the meaning is the same: oldest-first). `inbox` / `revert` / `unanswered` / `stall` are not there yet. | They need the delivery, revert and stall parts, which are not built. |
| D10 | Publisher uses monotonic elapsed time | Injected `kotlin.time.Clock` (wall time) | There is no monotonic time source in the graph (contract request C3). The wait is clamped to `[0, 50]`, so a wall-clock step can only publish early, never late or never. |
| D11 | The handle reads `ServerGateway.messagePage` directly | Through `HistorySource` (a `fun interface`); production passes `gateway.messagePage` | Tests script reads and their timing (the version-fence tests need a read held open). |
| D12 | `RevertChanged`: update `revert`; history reset removes messages ≥ `removedFrom` at once | `RevertChanged` → `historyEpoch++` and a reset reload (debounced); deferred maps are cleared on the reset | Core `RevertChanged` carries only the kind. `MessageRemoved` / `PartRemoved` / `HistoryReset(removedFrom)` are not in the core vocabulary (GE-09-1; contract request C1). |
| D13 | "Whole part wins" | An empty-text `PartUpserted` after streamed words keeps the words | OC2 sends empty start frames. A real rewrite still wins (one full re-parse). |
| D14 | `TranscriptCounters` is an `internal object` reset per test | A per-handle class (as in P3) | Tests never share counters, and nothing needs a global. |

## 5. Contract and catalog requests

Catalog requests: **none** (no new libraries; `kotlinx-serialization-json` comes through `:core:model`'s `api`).

Contract requests (to the core owner / S2-MODEL; nothing edited):

| # | Request | Why |
|---|---|---|
| C1 | `GatewayEvent.MessageRemoved(sessionId, messageId)`, `PartRemoved(sessionId, messageId, partId)` and `HistoryReset(sessionId, kind, removedFrom: MessageId?)` (plan 09 §3.3 GE-09-1) | Removal and history-reset rows of §3.3. The engine has `removeMessage` and a reset reload ready to wire. |
| C2 | `GatewayEvent.Notice` carries the notice's own id (or part id) | More than one notice per message would otherwise share the fixed part `notice` (D3). |
| C3 | A monotonic time source in `:core:common` (for example `TimeSource.Monotonic` behind an injectable `ElapsedClock`) with a test double | Publisher, debounce and stall timings (D10). |
| C4 | S2-MODEL: `ToolState.declined`, `ToolStatus.parse`, `MessageErrorKind.fromName` / `refineFromText` | The engine has a local `isDeclined` until then. |
| C5 | Document the `ServerPage<MessageWithParts>` item order (oldest first within a page) on `SessionGateway.messagePage` | The merge and the "far end" prepend assume it. The S1-OC2 adapter must produce it. |

## 6. Effects outside the write set (owners must apply)

This unit's API change breaks these callers. Each one is mechanical:

| File (owner) | Change needed |
|---|---|
| `quality/src/test/…/p3/Stream6000.kt` (coordinator) | Import `GatewayEvent`, `DeltaField` and `RunOutcome` from `io.github.eslamasabry.oc.core.domain.events`, and `MessageInfo`, `Part`, `PartType` and `Role` from `io.github.eslamasabry.oc.core.model.transcript`. Ids are value classes (`SessionId(SESSION)`, `MessageId(step)`, `PartId("text-0")`). `Part(…)` needs `rawType = "text"`. `MessageInfo(…, completedMs = t)` becomes `time = MsgTime(null, t)`. |
| `quality/src/test/…/p3/TranscriptStreamUiTest.kt` (coordinator) | `engine.open(ConversationRef(ProfileId("p"), SessionId(Stream6000.SESSION)))`. `NoticeKind` stays `engine.transcript.state.NoticeKind` (the row enum) and `GatewayEvent` is the core one. The engine reads the gateway's history and events now; the fake gateway answers an empty page. |
| `quality/src/test/…/GraphSmokeTest.kt` (coordinator) | Until D1 is restored, use `(connection as TranscriptAccessors).transcriptEngine` instead of `connection.transcript`. |
| `feature/chat/src/commonMain/…/ui/ChatTranscript.kt` (S2-CHAT) | The exhaustive `when (row)` in `ChatRow` needs branches for `MarkerRow`, `AssistantErrorRow` and `OlderRow`. The existing branches compile unchanged. |

## 7. Open problems

1. **Opening excerpt (CHAT-GENUI-04, B-STATE-121/122) and `prefetch(ref)` are not built.** They need
   `:engine:connection`'s first-paint cache (`oc.chatTail.<profileId>`), and that module does not compile on the M0
   branch. The excerpt writer rules (12 × 1,000 scalars, redacted, never while a revert is staged …) are plan 09 §3.4
   step 7.
2. **Still to build in this module (plan 09 §3.1, §3.9, §3.10):**
   - folds (`ExpansionStore`, `setExpanded`, `collapseAll`), `materialize` and `pathLinks`;
   - `TranscriptSearch`, `TurnStallTracker`, `StuckCommand`, `BackgroundTasks`, `RunResult`, `Unanswered` and `ContextShare`;
   - the finished-reply block cache (LRU of 512 parts);
   - the staged-revert boundary read;
   - trace sections (`chat.apply`, `chat.derive`, `chat.publish`).
3. **The open-fence cost (P3 open problem 1) is unchanged.** Chunked fences belong with S2-MD's `StreamingMarkdown`
   (and the S2-KC renderer). This unit keeps the P3 splitter.
4. **Where the per-publish O(n) still is.** Each publish builds the snapshot list, groups the turns (scanning every
   message's parts for variant tags) and checks the per-turn cache by identity. These are pointer walks, but on a
   5,000-message history they set the derive floor. Turn boundaries could be cached between publishes and invalidated
   by the model's order revision.
5. **`busy` and settle.** A finished turn whose tool is still `running` is re-derived when `busy` flips, as in Flutter.
   Every other finished turn is not.
6. **The handle's `close()` releases; it does not end work.** The engine closes the handle 60 s later or on LRU
   eviction. A caller that builds a handle directly (tests) cancels its parent scope.
7. **Ref counting is main-thread only.** `open` and `close` are expected from ViewModels on the main thread, and the
   60 s timer runs on `dispatchers.main`. A call from another thread would need a lock.
8. **Not verified:** a device or emulator run, the Android host tests, and the Compose-level counters (other modules).
   The JVM numbers come from a shared 4-vCPU container.
