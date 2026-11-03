VERIFIED: green — :engine:transcript:desktopTest 154/154, :engine:cards:desktopTest 54/54, :protocol:oc2:desktopTest 104/104 (1 skipped, pre-existing), :ui:kit-chat:desktopTest 122/122, :feature:chat:desktopTest 240/240, :quality:test 59/59, :androidApp:testPreviewDebugUnitTest 15/15, :androidApp:assemblePreviewDebug OK (head of the branch, all fixes merged).

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

| # | Finding | Verdict | Fix | Test |
|---|---|---|---|---|
| 1 | Deep tool input can crash before validation (B-CHAT-029) | **false** | already fixed by `b5c995ba`: `fromPart` copies the input with a depth cut before `CardJson.tree()` validates it; the existing test runs that production path with 100,000 levels | `B-CHAT-029 a very deep wire payload is refused as too large, never a crash` (existing, passes) |
| 2 | Card links bypass confirmation and the shared opener (B-SEC-065) | **fixed** | the direct `ACTION_VIEW` opener in `ChatCardsWiring` is deleted; card links go to `ChatViewModel.openLink` (link policy → "Open link?" naming the host → `ExternalLinkOpener`). They are checked as Markdown-origin links; both origins ask first | `B-SEC-065 a card link asks first naming its host and opens only through the chat's safe opener` |
| 3 | Reopening the chat loses the uncertain-send guard (B-CHAT-051) | **fixed** | persisted card dispatch journal `oc.genui.journal.<profileId>` in Flutter's format (card identities only, no content; swept with the profile by the `oc.<what>.<profileId>` rule); an unreadable store fails closed | `B-CHAT-051 an answer that may have left blocks a second send after the chat is reopened`, `…a corrupt saved journal fails closed`, `…a definite failure lets go of the journal`, `…the journal reads the Flutter record of a migrated install` |
| 4 | Dispatch does not freshly verify the card or idle state (B-CHAT-039/050/052) | **fixed** | after the Undo hold the board re-reads history and re-checks the card; it sends only with the stream connected, the session idle on a fresh status read and a plain prompt as the delivery (never queued, never steered) | `B-CHAT-050 a fresh read showing another client answered sends nothing`, `B-CHAT-039 a session found running at the last check refuses and sends nothing` |
| 5 | A definite async send failure blocks retry forever (B-CHAT-050) | **fixed** | the card waits for its own send's result; a definite failure lets the person try again; unconfirmed/queued/unseen stays "may have been sent" | `B-CHAT-050 a definite failure after the send id reaches the card…`, `B-CHAT-050 accepted is not a receipt, unconfirmed or unseen stays may-have-been-sent` |
| 6 | History reconciliation overwrites newer parts and chronology (B-CHAT-031/052) | **fixed** | a read overtaken by a live update is dropped and retried (at most 3); an applied read replaces the view in server order | `B-CHAT-052 a read that started before a live update never replaces the newer parts`, `B-CHAT-031 a read keeps the server's order and never appends an older cached prompt after the newest ask` |
| 7 | MCP `oc-ui` status alone qualifies OpenCode 2 (B-CHAT-034/035) | **fixed** | OpenCode 2 stays unqualified until device qualification (Flutter `cardsQualified=false`, plan 09 §13.1): cards are read-only in production, with the existing "Cards from agents" door | `B-CHAT-034 a connected oc-ui helper alone never qualifies OpenCode 2` |
| 8 | Photo asks have no photo flow (B-CHAT-004/053) | **partial** | no code change: the card says plainly that photos can't be added here and to type the answer; with #7 the answer controls are unreachable on OpenCode 2 anyway. Remaining: a picker for card asks plus Choose/Send | — |
| 9 | Waiting cards never appear in Conversations (B-CHAT-037) | **partial** | no code change: with #7 nothing can be waiting on OpenCode 2, and card models exist only for the open chat. Remaining: a capped background history pass (Flutter: 20 conversations) and the feed slot in `feature/chats` | — |
| 10 | Chart series dropped, line drawn as bars, thin summary (B-CHAT-054) | **mostly fixed** | every series is drawn (named when several); the screen-reader summary says kind, series, points and the highest value (Flutter). Remaining: line charts are drawn as bars — the kit has no line chart (kit owner) | `B-CHAT-054 the chart summary counts points and names the highest value across every series`, `B-CHAT-054 every chart series is drawn and the summary names kind, series, points and the highest value` |
| 11 | Undo restores a cleared field's default (B-CHAT-004) | **fixed** | Undo keeps the person's empty value | `B-CHAT-004 Undo after clearing an optional field with a default keeps it empty` |

## How to reach each fix on the phone

- Turn result / work line / step files: Conversations → a chat → under a finished reply, the "Edited … · ran …" line
  (open it) and the work line (open it, open a step; a produced file is a button named by the file).
- File links: a reply that names `path/to/file.ext[:line]` in inline code → tappable once the server has it → the
  file viewer at that line (a picture → the picture viewer).
- Inline picture: a reply sentence with `![words](/server/path.png)` → "▣ words" → the picture viewer.
- Send again: tap a prompt → Send again (the draft and its files stay in the composer).
- Code Copy: Copy at a code block's corner. Copy reply / Share: a reply's footer → Copy / Share.

## Tests and results

Run on the branch head with every fix merged (from `native/`, `ANDROID_HOME=/opt/android-sdk`, JDK 21):

```
./gradlew :engine:transcript:desktopTest :engine:cards:desktopTest :protocol:oc2:desktopTest :ui:kit-chat:desktopTest \
  :feature:chat:desktopTest :quality:test :androidApp:testPreviewDebugUnitTest :androidApp:assemblePreviewDebug
```

| Task | Result |
|---|---|
| `:engine:transcript:desktopTest` | 154 passed |
| `:engine:cards:desktopTest` | 54 passed |
| `:protocol:oc2:desktopTest` | 104 passed, 1 skipped (pre-existing live probe) |
| `:ui:kit-chat:desktopTest` | 122 passed |
| `:feature:chat:desktopTest` | 240 passed |
| `:quality:test` | 59 passed |
| `:androidApp:testPreviewDebugUnitTest` | 15 passed |
| `:androidApp:assemblePreviewDebug` | OK (`androidApp-preview-debug.apk`) |

No emulator in this container (no KVM): nothing was run on a device.

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

- Cards: still open are #8 (photo flow), #9 (feed slot) and #10's line chart; all are unreachable or read-only while
  OpenCode 2 stays unqualified (#7). The card journal store is bound in `ChatCardsWiring.kt` (`PreviewCardJournalStore`)
  and is not registered with ProfileScope; profile deletion removes it through the `oc.<what>.<profileId>` key sweep.
  `ChatEntries.kt` now hands card links to `vm::openLink` (one line).

- `ChatWorld` gained `sendAgain`, `fileExists` (defaults keep other worlds compiling); `ChatActions` gained `openPath`,
  `checkPaths` (default no-ops); `ChatUi.confirmedPaths`. `KitSpanKind.Picture` is new in `:ui:kit-chat` (kit owner:
  please review; the `when` over span kinds in `InlineAnnotated` and `KitMarkdown` handles it).
- `TurnLedger.load(page, beforeVersion)` and `historyVersion()` are new in `:engine:transcript` (owner: please review).
- `Oc2AdaptedTool` gained `startedAtMs`/`completedAtMs` (`:protocol:oc2`).
- Local only: Maven Central answered 429 to this container, so `~/.gradle/init.d/mirror.gradle.kts` (not committed)
  routed Central through Google's mirror. Nothing in the repo depends on it.
