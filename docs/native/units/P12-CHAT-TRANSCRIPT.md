VERIFIED: green

# P12 CHAT-TRANSCRIPT — the turn facts on the real chat screen

Branch `claude/chat-parity-native-android-y2c08m`, based on `native/p12-chatbase-2026-11-03` (`92991b8e`).

Finish line: on the Android preview, a conversation's transcript shows each turn's work under one line with
plain-word steps, durations and output, a result line with changed files and commands and the reply's time, the
context reading with its sheet, and retry, stall and shortening words in the live line — all fed by the real OC2
gateway. Non-goal: a separate timeline sheet, copied-transcript wording, team steps, and the built-in Linux helper
probe (other units).

## What a person can do now (Android preview, real OC2 gateway)

How to reach it: Servers → a saved server → Conversations → open a conversation. Everything lives in the existing
chat screen; no route, bar or row level was added.

| Journey | What the person sees | Rows |
|---|---|---|
| Work folded under one line | Under each turn: "Read 3 files · edited 1 file · ran 2 commands" (ripple row, 48 dp, chevron). While running, the line names the step running now ("Shell · npm test") and says "Working". Ended on a failure → "Didn't finish" and it opens by itself; a stopped run reads "Stopped"; a waiting request reads "Waiting for you" (never a spinner). More than 25 steps → "Show N earlier steps". | B-CHAT-063 |
| Steps in plain words | Open the line: each step is a `KitToolRow` — kind glyph, plain title ("Read", "Shell", "Search text", "create issue · github" for MCP, the server's own title, the id as words; never a raw id), subject (mono, LTR, redacted), status words ("Passed · exit code 0", "Failed", "Stopped", "Not run"), duration ("4.2s", "1m 05s"; a running step counts up), +n −n. | B-CHAT-111, 109 |
| Step output | Tap a step: a shell step shows its command box with output; other steps show their output exactly as given (leading/trailing whitespace kept, JSON pretty-printed), 12 lines then "Show all N lines". | B-CHAT-112, 113 |
| Sub-agent conversation | A task step with a child session shows "Open its conversation"; it opens that conversation (same server/profile). | B-CHAT-110 |
| Spinners settle | A step stops spinning once later words of its own turn exist or the run ends; a late "running" report never revives a finished call; background processes keep running. | B-CHAT-117 |
| Turn result | Under a finished turn's footer: "Edited 1 file · ran 1 command", with the reply time as the supporting line ("Last reply: first words after 2.1s, finished after 12.4s"). Open it: each changed file with its "+3 −1" (omitted when no tool reported counts) and each command with "Passed · exit code 0" / "Failed · exit code 1". | B-CHAT-068, 100, 101, 092 |
| Retry | While the server retries, the live line reads "Rate limited. Retrying 2 in 0:42" (rate limit only when it is one; otherwise "Retrying 2 in 0:42"; no countdown when there is no next time). | B-CHAT-016 |
| Silent turn | After 45 s of silence (not while a request waits or a step runs) the live line reads "The model is taking longer than usual…" (stream connected) or "The connection to the agent was lost…" (stream down); any progress clears it. | B-CHAT-118, 119 |
| Shortening | A running compaction makes the live line read "Shortening conversation…"; a finished one is a notice row ("Earlier messages were summarized to save space"), never a reply. | B-CHAT-116, 069 |
| Background work | A background command shows a quiet line above the live line ("Still running in the background · build_tv.sh · 3 min", or "2 tasks still running in the background") until its notice ends it (6-hour cutoff). | B-CHAT-007, 008, 011 |
| Context | The bar's actions gain "Context 84 % used" once the catalog knows the reply's model window (no catalog/unknown model → no action). It opens the context sheet: the ring (calm/warning/full colour) with "84% used", tokens for input, output, reasoning, cache read and cache write, the context limit, model, messages, and the input makeup labelled "Estimated input makeup". Back closes it. | B-CHAT-019, 057, 102 |

## How it is built

- `:engine:transcript` (new files only): `turnfacts/` — the pure Flutter ports (ToolFacts, ToolDuration, ToolOutput,
  TranscriptSettle, RunResult, ContextUsage, ReplyTiming, TurnStall, BackgroundTasks, RetryStatus, AgentNotices);
  `ledger/` — `TurnLedger` (core `MessageWithParts` by id, turns grouped exactly like the reducer so ids match the
  rows), `turnWork` (settled steps, counts, work state) and `factTicks` (a 1 s clock only while something counts).
- `:feature:chat`: `world/TurnFeed.kt` (`TurnFeedKeeper` fed by `GraphChatWorld` from every gateway event and history
  page; also reply timing and the catalog's context windows), `presentation/TurnFactsPresenter.kt` (side tables
  `TurnFactsUi`, stall tracker), `ui/TurnFactsRows.kt`, `ui/TurnFactsWords.kt`, `ui/ChatContextSheet.kt`. Small edits:
  `GraphChatWorld` (+feed hooks), `ChatViewModel` (+`facts`, `openSession`), `ChatEntries`, `ChatScreen`
  (+`facts`, `onOpenSession`, the context action and sheet), `ChatTranscript` (work/footer/live/background rows),
  `P3Bridge` (compaction parts carry their notice kind).
- `:ui:kit-chat` (new files): `turn/KitWorkLine.kt`, `turn/KitContextRing.kt`, `tool/KitToolOutput.kt`.
- Words: all 103 strings used already existed in `:ui:words` (converted from Flutter) with Arabic; no string file
  changed.

## Tests (catalog row ids in every name)

One Gradle invocation on this branch's head (JDK 21, Linux cloud container, `--continue`), every test task executed fresh:

| Command | Result |
|---|---|
| `:engine:transcript:desktopTest` | 150 tests, 0 failures, 0 skipped (incl. 41 + 37 + 34 ported turnfacts tests and 10 ledger/work tests) |
| `:ui:kit-chat:desktopTest` | 109 tests, 0 failures |
| `:feature:chat:desktopTest` | 143 tests, 0 failures (incl. `TurnFactsTest`: 15 — 9 presenter/feed tests from real core gateway events, 5 chat-screen tests by visible text, 1 bridge test) |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures |
| `:quality:test` | 48 tests, 0 failures (K1 kit-only failed once on `kotlinx.coroutines.delay` in a feature file; the ticker moved to `ledger/FactTicks.kt`) |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL |

Tests per row (engine + feature): B-CHAT-007 ×11, 008 ×6, 011 ×3, 016 ×9, 019 ×11, 057 ×4, 063 ×10, 068 ×5,
069 ×7, 092 ×7, 100 ×6, 101 ×10, 102 ×5, 109 ×5, 110 ×3, 111 ×9, 112 ×6, 113 ×1, 114 ×4, 116 ×4, 117 ×10,
118 ×11, 119 ×1.

Screen tests (`TurnFactsTest`, real `ChatScreen`, English): the work line "Read 1 file · edited 1 file · ran 1
command" opens to "Shell" with "4.2s"; the result "Edited 1 file · ran 1 command" opens to the file and
"$ npm test"; "Context … used" opens "Conversation context" with "Cache read"; the live line reads "Rate limited.
Retrying 2 in 0:42"; "Delegated 1 task" → step → "Open its conversation" opens `child-1`.

Not run: live OC2 server, emulator or device. Red-first evidence: the ported tests were written from the Flutter
cases and run against the new code; no red receipts were recorded for them.

## Catalog rows

Covered (engine tests + presenter tests + screen tests by visible text): B-CHAT-007, 008, 011, 016, 019, 057, 063,
068, 069, 092, 100, 101, 102, 109, 110, 111, 112, 113, 114, 116, 117, 118, 119.

Partial / still missing:
- **B-CHAT-111 on other surfaces:** the transcript work line and live line use the plain words; the timeline sheet,
  copied transcript, team steps, permission requests and live notification are other units' surfaces —
  `toolFacts()` + `stepTitle()` are ready for them.
- **B-CHAT-118 helper stopped:** `TurnStallNotice.HelperStopped*` words are wired, but the native app has no
  built-in helper status yet (Linux runtime unit), so the probe only distinguishes model slow vs connection lost.
- **B-CHAT-069 sign-in errors:** `classifyAgentError`/`AgentErrorClass` are ported and tested; the Paseo agent
  mapper that would use them is not native yet. The run-failed status line is unchanged.
- **B-CHAT-016 assistant error cards / prompt not answered:** `assistantErrorState` and `unansweredTurn` are ported
  and tested; the chat's existing failed-run status line was not replaced (it belongs to the chat base).
- **B-CHAT-114 artifacts:** `ToolOutputView.artifacts` is computed; no download/open action is drawn yet.
- **B-CHAT-092 in-app server timing / persisted last value:** not applicable natively yet (no built-in server).
- **B-CHAT-057 halo:** `contextLevelCrossedUp` is ported; the ring does not pulse.

## Deviations

- The brief said `native/engine/turnfacts` already existed with TurnResult/ContextLevel/ToolFacts; it did not exist
  on any branch. A new module would need a `MODULE_OWNERS` row (coordinator-only), so the facts live as new files in
  `:engine:transcript` (`turnfacts/`, `ledger/`). Moving them to `:engine:turnfacts` later is a package move.
- `python3 -I native/tool/parity/ledger.py` and `docs/native/parity/NO-NATIVE.md` do not exist on this base; row
  states came from `catalog/behaviors.jsonl`.
- Durations follow the Flutter strings ("1m 05s"), not the catalog example "1:15" (the catalog paraphrases).
- Context opens a sheet over the chat (Back returns to the chat) instead of a routed screen, so no route file was
  added.
- `ChatViewModel` now takes the AppScope `kotlin.time.Clock` (provided by `CommonBindings`).

## Integrator notes

- No `androidApp` edit was needed: no new module, no Android-only binding.
- `GraphChatWorld`, `ChatScreen`, `ChatTranscript`, `ChatViewModel`, `ChatEntries` and `P3Bridge` have small, marked
  edits (CHAT-TRANSCRIPT); expect textual conflicts with the other chat sessions there, not logical ones.
- Local build note: Maven Central answered HTTP 429 through this environment's proxy; a user-level
  `~/.gradle/init.d` script pointed Central at Google's mirror. Nothing in the repo changed for it.
