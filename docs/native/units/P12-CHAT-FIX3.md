VERIFIED: pending

# P12-CHAT-FIX3 — Codex second-eye findings for transcript, feel and cards

Branch: `claude/chat-parity-push-w4-tbtoib` (base `native/p12-qa-wave4-2026-11-03` @ `6b451faf`).
Reviews checked: `docs/native/reviews/p12/chat-transcript.md` (8), `chat-feel.md` (7), `chat-cards.md` (11).

Finish line: every Codex finding on the merged transcript, feel and cards units is checked against the code and
Flutter; each real one is fixed in the Android app with a test, each false one has a reason. Non-goal: the header
bar, composer, HTTP client and add-server form (CHAT-FIX-2).

## Transcript (chat-transcript.md)

| # | Finding | Verdict | Fix | Test |
|---|---|---|---|---|
| 1 | Credentials in the turn result and running label (B-SEC-094) | **fixed** | `TurnFactsRows`: command lines, file paths and the folded "now" label go through the app's redactor (`rememberKitMask`) | `TurnFactsTest` "B-SEC-094 a finished turn's command and a running step's label never show a bearer token", "B-SEC-094 the folded running work line names the command redacted" |
| 2 | Delayed history overwrites newer live tool facts (B-CHAT-112/117) | **fixed** | `TurnLedger` stamps every message and part with the version it changed at; `load(page, beforeVersion)` keeps what changed after the read started. `TurnFeedKeeper.historyVersion()` is read before `messagePage` in `GraphChatWorld.readHistory`, as the main transcript already did | `TurnLedgerTest.kt` "B-CHAT-112 B-CHAT-117 a history page that left before a live result never overwrites it" |
| 3 | Equal timestamps reorder by id (B-CHAT-100) | **fixed** | ledger sorts by creation time only; the stable sort keeps server order for ties (Flutter's `z-user, x-step, a-final` fixture) | `TurnLedgerTest.kt` "B-CHAT-100 equal timestamps keep the server's order so the work stays in its turn" |
| 4 | Produced files have no action (B-CHAT-114) | **fixed** | a step's explicit files (`ToolOutputView.artifacts`) are visible buttons named by the file ("Generated file" when nameless) in the step's body; a server path opens the file viewer (`FilesRoute.File`, which offers Open with / Share — the native "download"), a picture opens the picture viewer, a web address goes through the link policy | `TurnFactsTest` "B-CHAT-114 a file a step produced is a visible action that opens it" |
| 5 | OC2 tools never get durations (B-CHAT-109) | **fixed** | `Oc2EventAdapter.rawToolState` keeps `time.ran ?: time.created` / `time.completed` (Dart `v1ToolTimeJson`), the core projection passes them to `ToolState`; live `tool.called`/`tool.result` are stamped with the envelope time; the ledger keeps known times when a later report has none | `Oc2LiveGatewayTest` "B-CHAT-109 a history tool keeps its server times so its duration shows" |
| 6 | Every OC2 retry reads "Rate limited" (B-CHAT-016) | **fixed** | `TurnFeedKeeper` classifies from `tag ?: details` (OC2 sends no tag; its redacted reason is in details) | `TurnFactsTest` "B-CHAT-016 an OpenCode 2 retry reads its reason from the details, so an overload is not a rate limit" |
| 7 | Stall diagnosis survives a changed connection (B-CHAT-118) | **fixed** | `TurnFactsPresenter` calls `TurnStallTracker.invalidateEvidence` when the link changes, so the next probe re-diagnoses (Connection lost) | `TurnFactsTest` "B-CHAT-118 a model-slow diagnosis does not survive losing the connection" |
| 8 | Background elapsed time freezes after the turn (B-CHAT-007) | **fixed** | the facts clock ticks while background work is believed running, not only while busy/retrying | `TurnFactsTest` "B-CHAT-007 background work keeps the clock ticking after its turn ends" |

## Feel (chat-feel.md)

| # | Finding | Verdict | Fix | Test |
|---|---|---|---|---|
| 1 | Code-block Copy exposes masked credentials (B-SEC-094, B-CHAT-056) | **fixed** | the whole-fence copy (`CodeCopy`) is masked with the redactor before it reaches the clipboard; Copy reply still copies the reply as written (plan 09 §6.6) | `ChatFeelScreenTest` "B-SEC-094 B-CHAT-056 a code block's Copy copies what it shows, a key masked" |
| 2 | Send again carries unrelated draft attachments (09 §6.6) | **fixed** | new `ChatWorld.sendAgain(text)`; `GraphChatWorld` sends the words alone, never captures the draft's submission or files, and leaves the draft as it is (Flutter `_sendInsteadOfDraft`) | `ChatPresenterTest` "09-6_6 send again sends the prompt's words once without the draft's files, and fork opens the new conversation" |
| 3 | Markdown file links never enabled (B-CHAT-064) | **fixed** | each drawn reply block reports its path spans (`ReplyActions.checkPaths`); the presenter checks them through `ChatWorld.fileExists` (folder listing, Flutter `_checkPathLink`; only with `fileBrowsing`), keeps `ChatUi.confirmedPaths`, re-checks a miss only after the conversation moved on and is idle; `onPath` → `openPath`: picture → viewer, other files → `FilesRoute.File(path, line)` | `ChatPresenterTest` "B-CHAT-064 only paths the server confirms become tappable…", "B-CHAT-064 B-CHAT-114 a file opens in the file viewer at its line, a picture in the picture viewer", "B-CHAT-064 nothing is checked or opened where the server has no file browsing"; `ChatFeelScreenTest` "B-CHAT-064 a path the server has opens its file from the reply" |
| 4 | Long tables lose their structure after 16 lines (09 §9.1) | **fixed** | `LongTable.of(rows)`: a chunked Prose block that opens with a table header + delimiter is drawn whole by its first chunk; its later chunks draw nothing | `ChatFeelScreenTest` "09-9_1 a table longer than one chunk stays one table" |
| 5 | Copy/Share reply corrupts chunk boundaries (09 §6.6) | **fixed** | `ReplyText` joins chunks of one block (`continues`/`continued`) with the one newline they were cut at; separate blocks keep the blank line | `ChatRulesTest` "09-6_6 copy reply joins the chunks of one long block with its own newline, not a blank line" |
| 6 | Inline server pictures cannot open (B-CHAT-059) | **fixed** | `KitMdSource` keeps a server picture inside a sentence as the frame glyph plus its words and a new `KitSpanKind.Picture(path, label)`; tapping it calls `onPicture` (the in-chat viewer) | `KitMdSourceTest` "B-CHAT-059 a server picture inside a sentence keeps its words and opens the viewer"; `ChatFeelScreenTest` "B-CHAT-059 a server picture inside a sentence reads its words" |
| 7 | Failed replies lose message actions (09 §6.6) | **fixed** | the failed footer's first line is the same tappable "message actions" row as a normal footer; the plain reason line and Details stay under it | `ChatFeelScreenTest` "09-6_6 a failed reply still opens its message actions" |

## Cards (chat-cards.md)

CARDS_SECTION

## How to reach each fix on the phone

- Turn result / work line / step files: Conversations → a chat → under a finished reply, the "Edited … · ran …" line
  (open it) and the work line (open it, open a step; a produced file is a button named by the file).
- File links: a reply that names `path/to/file.ext[:line]` in inline code → tappable once the server has it → the
  file viewer at that line (a picture → the picture viewer).
- Inline picture: a reply sentence with `![words](/server/path.png)` → "▣ words" → the picture viewer.
- Send again: tap a prompt → Send again (the draft and its files stay in the composer).
- Code Copy: Copy at a code block's corner. Copy reply / Share: a reply's footer → Copy / Share.

## Tests and results

TESTS_SECTION

## Deviations

- Produced files open in the native file viewer (Open with / Share there) instead of Flutter's in-chat preview with a
  Download button: the native app already has that viewer, and it is the Android way to save or hand a file on.
- File-link checks re-check a miss after the conversation moved on and is idle, instead of Flutter's 20-second
  negative TTL (the presenter has no clock; this also never re-checks once per streamed word).
- A long table is drawn whole by its first chunk row (one lazy item), so a very long table costs one item's layout;
  tables are never streamed word by word anyway.
- The red step: tests for behaviour that existed (ledger order/fence, retry wording, stall, redaction, copy text,
  tables, failed footer, inline picture text) assert the exact defect the review describes; tests for new entry points
  (`sendAgain`, `fileExists`/`checkPaths`/`openPath`, `historyVersion`, `KitSpanKind.Picture`, step file buttons) could
  not compile against the old code, so their "red" is the missing API.

## For the integrator

- `ChatWorld` gained `sendAgain`, `fileExists` (defaults keep other worlds compiling); `ChatActions` gained `openPath`,
  `checkPaths` (default no-ops); `ChatUi.confirmedPaths`. `KitSpanKind.Picture` is new in `:ui:kit-chat` (kit owner:
  please review; the `when` over span kinds in `InlineAnnotated` and `KitMarkdown` handles it).
- `TurnLedger.load(page, beforeVersion)` and `historyVersion()` are new in `:engine:transcript` (owner: please review).
- `Oc2AdaptedTool` gained `startedAtMs`/`completedAtMs` (`:protocol:oc2`).
- Local only: Maven Central answered 429 to this container, so `~/.gradle/init.d/mirror.gradle.kts` (not committed)
  routed Central through Google's mirror. Nothing in the repo depends on it.
