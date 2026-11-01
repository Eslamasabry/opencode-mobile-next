# Unit S2-CHAT: the chat screen (report)

Branch `native/chat-2026-11-01`, created from `claude/native-chat-2026-11-01-q7x69b` at `7db496b2` (the M0 tip after
the kita2 merge). Date: 2026-11-01. Write set: `native/feature/chat/**` except request files (S2-REQ), plus this report.

Plan sections read: `plan/01` (binding), `plan/15` row S2-CHAT, `plan/09` §0, §3.1–§3.3, §4 (headings), §6–§7, §15,
`plan/07` §2.5, §3.3–§3.4, §5 C01–C06, §10.1–§10.2, §11, `plan/08` §5.1; `native/AGENTS.md`, `docs/native/units/INTEGRATE-W1.md`,
`S2-KCB.md`, `docs/native/slice0/P3-transcript.md`. Contract used: the flat core `Failure` list of INTEGRATE-W1.

**Finish line (plan 15):** the chat screen with the list, follow, jump pill, find, status line, message actions,
`BasicComposerHost`, arrivals and the Back guard works on the preview build; J1–J3 are recorded on the emulator.
**Non-goal:** composer extras (S3), chat pages (S4), request cards (S2-REQ).

**Status, by shipping state:**

| State | What |
|---|---|
| Implemented | everything in §1, in `:feature:chat` |
| Verified | `:feature:chat:desktopTest` 37/37 green; `compileKotlinDesktop` and `compileAndroidMain` clean, no warnings in the module; a mutation pass proved four key rules are guarded (§3) |
| Committed / pushed | 7 commits on `native/chat-2026-11-01` (§3, this report included), pushed; no PR, no CI run |
| Not done | the preview build does not show this screen yet: `:androidApp` does not depend on `:feature:chat` and `:feature:chats` still installs its P2 `entry<ChatRoute>` (both are other owners' files, §6). No emulator in this container (no `/dev/kvm`), so J1–J3 are **not recorded**. The system Back gesture is not guarded yet (kit part K-2, §5). |

---

## 1. What was built

Package root `io.github.eslamasabry.oc.feature.chat`.

| File | Content |
|---|---|
| `world/ChatWorld.kt` | The seam the ViewModels code against: `transcript`, `load`, `title`, `capabilities`, `link`, `pending`, `draft`, `work` (where pure work runs; a feature may not see `AppDispatchers`), and `draftChanged`, `saveDraft`, `send`, `retrySend`, `stop`, `reload`, `fork`, `deleteMessage`, `markRead`. `ChatLoad`, `ChatLink`, `PendingSend`/`PendingState` (Sending, Unconfirmed, Failed), `DraftFacts.unsaved` (whitespace is nothing to lose), `ChatWorlds` (ref-counted sharing). Every flow is an engine's truth passed through; each binding names its replacement. |
| `world/GraphChatWorld.kt` | Production binding (`@ContributesBinding(AppScope)` `GraphChatWorlds`). Opens the P3 `TranscriptHandle` on the conversation's own graph (`ConnectionRegistry.graphFor(ref)`; no live graph → `Failed(Network(Offline))` + `Offline`), reads the newest page, marks a busy session running, feeds `GatewayEvents.all`, re-reads when the reducer asks (`refreshRequests`: reconcile by refetch), maps `StreamStatus` to `ChatLink`, follows `SessionChanged` titles. Sends: bubble first, one `prompt` per tap, `Uncertain` → Unconfirmed (never resent on its own), failure → Failed with the person's Try again; a sending bubble settles when the newest prompt row has its words; during a run it steers where `promptSteering` is on (plan 09 §4.3). `saveDraft` refuses (`Unavailable("drafts")`: slice 2 stores no drafts); `markRead` writes nothing yet. Delete re-opens the handle (P3 has no `removeMessage`). `ChatGraphAccessors` contributes the gateways to `ConnectionScope`. |
| `world/P3Bridge.kt` | **Temporary**: maps core `GatewayEvent`/`MessageWithParts` onto the P3 copies `:engine:transcript` still reads (INTEGRATE-W1 §5). Other sessions dropped; `Idle` never replaces a Failed/Interrupted of the same run (INTEGRATE-W1 §7); a session-level notice (no message) is dropped, never given an invented message; notices carry only their kind (T10); `Snapshot`/`Patch` parts read as Unknown; a compaction re-reads. Deleted when S2-TX swaps the reducer. |
| `presentation/ChatUi.kt` | `ChatUi` (revision first; rows passed through by reference; side tables: status, live, pending newest-first, find, menu, confirm, clip, arrival, capabilities, `guardBack`) and `ChatActions` (intents: `leaveRequested`, `confirmLeave`, `dismissConfirm`, `retryLoad`, `stop`, `find`, `findNext`, `findPrevious`, `openTimeline`, `openMessageMenu(rowKey)`, `closeMessageMenu`, `messageAction`, `confirmDelete`, `clipDone`, `arrivalShown`, `liveEdgeChanged`, `retrySend`, `dismissStatus`). |
| `presentation/ChatRules.kt` | Pure rules: `RowIds` (message id from the P3 row key), `StatusPick` (connection first per 07 §10.1, then send failed → send unconfirmed → reply failed → action failed; the newest bubble speaks; no reply-failed line when the newest turn's footer already says it failed, B-CHAT-071), `TranscriptFind` (literal, case-insensitive, non-overlapping, reading order over prompts, reply blocks and thoughts; the current hit kept by identity, else its number clamped; next/previous wrap — Flutter `transcript_find.dart`), `MessageActions` (Copy, Send again, Fork, Delete last; absent without the capability; no Send again during a run), `ReplyText` (the turn's prose in order, thoughts/notices skipped), `MessageOrder` (earlier-messages ordinal per row, built once per rows instance; pill only while away and past 30 messages), `ReadAck`, `Landing` (newest failed turn), `BackOrder` (menu → dialog → find → draft guard → leave, 07 §3.3). |
| `presentation/ChatPresenter.kt` | The state holder (plain class, so it is tested with `kotlin.test`). `current()` presents the world's present values; `states(scope, work, started)` is the production flow (find `flowOn(work)` + `conflate`). Read acknowledgement fires on each move into "loaded and at the live edge" and when a reply ends while that holds. Back: closes what is open first; an unsaved draft is saved, saved → pop, refused → "Discard your changes?" confirm; a second Back while saving does nothing. Delete asks first, deletes once, no Undo. Fork opens the new conversation. Stop is idempotent while stopping; a failed command shows a dismissible line. `openTimeline` opens find until S4's timeline sheet exists. |
| `presentation/ChatViewModel.kt` | One per chat entry (assisted by `ChatRoute`): opens the world in init, releases it in `onCleared`, `WhileSubscribed(5 s)`. |
| `composer/ComposerHost.kt` | The plan 09 §6.3 slot (`ComposerHost`, `ComposerTarget`, `InsertRequest`, `ComposerChrome`), here until it moves to `:ui:kit-chat` (request K-1). Chrome adds `focusRequest/focusDone` (the ready-to-type arrival) and `onStop/stopping` (Stop in the composer row, the chat owns the stop). |
| `composer/presentation/BasicComposerViewModel.kt` | Per-conversation composer VM (shares the chat's world): reports the draft text, `SendGate` (never blank, never while a dispatch is in flight, never without a conversation). |
| `composer/BasicComposerHost.kt` | `@ContributesBinding(AppScope)` binding and `BasicComposer`: `KitField` (multiline, `chat-composer-field`), Stop (`chat-stop-button`) while a reply runs, Send (`chat-send-button`, haptic Send) labelled Send / Add to this turn (steers) / Send after this reply / Sending; clears the field on accept and asks the list to follow. |
| `ui/ChatScreen.kt` | `ChatScreen(state, actions, composerHost)` from kit parts: `KitScreen` page with `KitTopBar` (Back → `leaveRequested`, Search), the one `KitStatusLine`, the find bar (`transcript-find-bar/-input/-previous/-next`, "n of m"), loading line + `KitSkeletonRows` (`chat-loading`), `KitStateView` error (`chat-load-error`, Try again), the transcript, the earlier-messages pill (`KitChip`, `earlier-messages-pill`, derivedStateOf over layout, only recomposes when the number changes), `KitMenu` message actions, `KitConfirm` for delete and leave. Find and land-on-failure scroll with `scrollToItem` (never an animated long jump). Words from `:ui:words`. |
| `ui/ChatTranscript.kt` | `ChatTranscriptList`: bottom to top the live line (`chat-live-line`, no Stop), pending bubbles (`pending:<id>`, Sending / unconfirmed / not sent + Try again), then the engine rows; newest key = live → pending → rows (the kit follows in the same frame). Prompts and reply footers are `KitTappable` (tap is the visible way into the menu; long-press the twin). The P3 `ChatTranscript(state, words, list, reveal, motion)` overload is kept unchanged for `:quality`'s P3 test. |
| `ChatEntries.kt` | `entry<ChatRoute>` with the assisted `ChatViewModel` and the injected `ComposerHost`. |

## 2. Catalog rows and tests

All tests are `commonTest`, run on the desktop JVM, named after their row (or the plan anchor where no row exists).

| Row / anchor | Test (class) | What it proves |
|---|---|---|
| B-NAV-005 | `ChatPresenterTest` | a chat opened to reply arrives ready to type exactly once; a plain open does not |
| B-STATE-102 | `ChatPresenterTest` | a draft that cannot be saved asks first; Keep editing stays; Leave without saving pops |
| 07 §3.4 (×2) | `ChatPresenterTest` | a draft that saves leaves at once; an untouched composer never guards; a second Back while saving neither saves nor pops twice |
| 07 §3.3 (×2) | `ChatRulesTest`, `ChatPresenterTest` | Back order menu → dialog → find → draft guard → leave; the guard is on only while it has something to do |
| B-CHAT-115 (×3) | `ChatRulesTest`, `ChatPresenterTest` | literal, case-insensitive hits in reading order over prompts, replies, thoughts (not app words); current hit kept across streaming, older page, deletion; n of m and stepping with wrap |
| B-CHAT-061 (×2) | `ChatRulesTest`, `ChatPresenterTest` | a prompt's menu (copy, send again, fork, delete last), each only when it can run; the menu follows capabilities, never the backend kind |
| B-CHAT-071 | `ChatRulesTest` | no second error line when the reply's footer already says it failed; a prompt with no answer gets the line |
| B-CHAT-012 (×2) | `ChatRulesTest`, `ChatPresenterTest` | read only when loaded and at the live edge; once per arrival; a reply that ends while read is read |
| B-PROTO-015 (×2) | `WorldRulesTest` | core events and history pages reach the P3 transcript field for field; other sessions never |
| B-CHAT-097 | `WorldRulesTest` | notices carry only their kind; a session-level notice is never given a message |
| INTEGRATE-W1 §7 | `WorldRulesTest` | an idle never replaces the run's failure, and ends a clean run |
| 09 §4.2 | `WorldRulesTest` | one tap is one send (`SendGate`) |
| 09 §4.4, §4.6, §6.1, §6.5 (×3), §6.6 (×6), §6.7 (×2), §7.3 (×2) | both classes | stop once with a stopping line and a reported failure; pending newest first, resent only on the person's tap; rows by reference; phases and Try again; stable status ids; delete asks first / once / no undo; refused delete is a dismissible line; copy verbatim prompt or whole reply; send again and fork; message order and pill visibility; landing on the newest failure |

Not covered here (see §5–§7): `FollowNewItemTest` and `EarlierPillCountTest` as Compose host tests (K1 forbids
`androidx.compose.ui.test` imports in feature files, tests included; the follow itself is the kit's `KitTranscriptList`),
Roborazzi goldens of the screen, J1–J3.

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`
(`commandlinetools-linux-16111833_latest.zip`; `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`; licences
accepted). Container: 4 vCPUs, 15 GB. Container-only (not committed): `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers;
`~/.gradle/init.d/central-mirror.gradle.kts` (Google's Central mirror first, then the plugin portal and Google for
plugins; `robolectric.dependency.repo.url`), `native/local.properties` (`sdk.dir`, gitignored).

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :feature:chat:compileKotlinDesktop` (baseline, before any edit) | BUILD SUCCESSFUL (6 min cold) |
| 2 | `./gradlew :feature:chat:compileKotlinDesktop` (first draft) | FAILED: a `chat/**` glob inside a `/* */` comment opened a nested comment (Kotlin nests them) → fixed |
| 3 | `./gradlew :feature:chat:desktopTest` | BUILD SUCCESSFUL; 32/32 (`ChatPresenterTest` 19, `ChatRulesTest` 13) |
| 4 | same, with `WorldRulesTest` | first a test name with `:` (illegal on the JVM) → renamed; then 37/37 |
| 5 | Mutation pass: four rules broken by hand (footer-says-failed check, find-before-draft Back order, Idle-replaces-failure, read-ack edge detection), `desktopTest --continue` | exactly the four guarding tests failed (B-CHAT-071, 07 §3.3, INTEGRATE-W1 §7, B-CHAT-012); sources restored |
| 6 | `./gradlew :feature:chat:compileAndroidMain` | BUILD SUCCESSFUL, no warnings in the module |
| 7 | **Final, on the committed tree:** `./gradlew :feature:chat:compileKotlinDesktop :feature:chat:desktopTest :feature:chat:compileAndroidMain` | **BUILD SUCCESSFUL; 37 tests, 0 failures** |
| 8 | K1/K2/K4/K5/K11/K16/K18 checked with a script that applies `quality/…/KitOnly.kt`'s allowlist to every file of the module (no `:quality` build, per brief) | 0 violations |
| 9 | `tool/check_commits.sh 7db496b2 HEAD` | OK |
| 10 | `OC_BASE=7db496b2 tool/check_write_set.sh --globs 'native/feature/chat/**,docs/native/units/S2-CHAT.md'` | OK (18 paths, all inside) |

Honesty note on "red first": the tests were written before the implementation compiled, but the first run was
already green, so no red state was observed; row 5 is the substitute evidence that the tests bite.

Commits (all `[skip ci]`): `96d37255` state, intents and rules (+ tests); `769de691` world binding; `ed55594d`
BasicComposerHost; `d85ba21e` bridge and send-gate tests; `2cae04d0` screen, transcript items, entry; `4e8e9b26` find
query and menu anchor fix; plus this report.

## 4. Decisions

| # | Decision | Why |
|---|---|---|
| D1 | A `ChatWorld` seam in the feature, bound in production by `GraphChatWorld`. | S2-TX, S2-DLV and the drafts are being built in parallel (plan 15 R15-31: start on frozen contracts with fakes). The ViewModel and screen code against the seam; swapping an engine in touches only the binding. |
| D2 | `ChatPresenter` (plain class) + a thin `ChatViewModel`. | K1 scans feature test files: no `kotlinx.coroutines.test`, no Compose UI test. The async edge is a `ChatLauncher`; tests start it in place with `kotlin.coroutines`. |
| D3 | Find runs in the feature (`TranscriptFind`, O(rows), on the world's lane via `flowOn`). | `handle.search` (§3.10, B-CHAT-115) is not in the P3 engine. A straight port of Flutter's index semantics; S2-TX replaces it. |
| D4 | Message ids are parsed from P3 row keys (`RowIds`). | P3 rows carry no `messageId`; request R-TX-2. |
| D5 | Copy reply joins blocks with one blank line. | P3 blocks drop the separators; byte-exact copy needs the part text (request R-TX-3). |
| D6 | The earlier-messages pill opens find in slice 2. | Its target, the timeline sheet, is S4; find is the in-chat way to a message. Switch `openTimeline` when C03 lands. |
| D7 | Leave confirm: "Discard your changes?" / "Leave without saving" / `draftLeaveMessage`, kind Discard (cancel = Keep editing). | `KitConfirm` requires a question title; the Flutter "Your draft isn't saved" is not one. Words already in `:ui:words`. |
| D8 | Prompts and reply footers open the menu by tap (visible) and prompts also by long-press. | The plan's "Message actions" disc and footer More need `KitMessage` (S2-KCB); the footer line is the visible path meanwhile. |
| D9 | Stop sits in the composer row and goes through the chat (`ComposerChrome.onStop`). | The chat owns the stop and its live line; the composer has no delivery engine in slice 2. |
| D10 | The P3 `ChatTranscript(state, words, …)` overload is kept. | `:quality`'s `TranscriptStreamUiTest` drives it. |

## 5. Deviations from the brief and the plan

- **System Back is not guarded yet.** The guard's logic, state and confirm are done and tested, and the top-bar Back goes
  through it; but registering a page Back handler needs `androidx.navigationevent`, which K1 bans in features and the kit
  does not expose publicly (only the internal `KitPredictiveBack`). Request K-2; the drop-in is
  `KitBackGuard(enabled = state.guardBack, onBack = actions::leaveRequested)` in `ChatScreen`.
- **Copy is absent from the menu.** `MessageAction.Copy` and `ChatUi.clip` are done and tested, but writing the clipboard
  needs `LocalClipboard`, which K1 bans in features. Request K-3; the screen filters Copy until then (absent, never broken).
- **`ComposerHost` lives in `:feature:chat`**, not `:ui:kit-chat` `…host` (write set). Request K-1.
- **No `Arrivals` type yet** in `:ui:routes`: only `ChatRoute.focusComposer` exists, so the "ready to type" arrival is
  wired end to end and `LandOnFailure` is implemented and tested in the presenter but nothing produces it.
- **No Compose host tests or goldens in the module** (K1, see §2). Plan 09 §7.3 places `FollowNewItemTest` in
  `:feature:chat`; that contradicts K1. Proposal: host them in `:quality` like P3's stream test, or let K1 allow
  `androidx.compose.ui.test` in `src/*Test`.
- **Not on the preview build, J1–J3 not recorded** (§6, no emulator).
- No drafts, read state or pending-discard record (S3 / connection engine); the bindings say so.

## 6. Requests to other owners

| # | To | Request |
|---|---|---|
| K-1 | kit-chat owner (chat-lead file) | Add `ComposerHost`, `ComposerTarget`, `InsertRequest`, `ComposerChrome` to `:ui:kit-chat` `…host` with this unit's two chrome additions (`focusRequest/focusDone`, `onStop/stopping`); `:feature:chat` then deletes `composer/ComposerHost.kt`. Must land before S3-COMP-UI. |
| K-2 | kit owner | A public page Back guard, e.g. `KitBackGuard(enabled, onBack)` over `NavigationBackHandler` (enabled only while it has something to protect, so the predictive preview stays on otherwise). |
| K-3 | kit owner | A copy part, e.g. `KitCopyEffect(text: String?, onDone)` or `rememberKitCopy()`, writing plain text and announcing "Copied". |
| K-4 | kit / kit-chat owners | `KitJumpPill.older(count = { … })` (the earlier-messages twin of the jump pill, plan 08 §5.1), `KitFindMark` (in-place find marks), `KitSkeletonTranscript`, `KitTurnLive`, `KitMessagePending`, `KitMessage` with the Message actions disc; the screen uses `KitChip`, `KitSkeletonRows`, `KitQuietLine` and `KitPromptBubble` meanwhile. |
| R-TX-1 | S2-TX | Move the reducer onto core's `GatewayEvent`/`MessageWithParts` and have the handle read history and events itself; then delete `world/P3Bridge.kt` and the feeding in `GraphChatWorld.attach`. |
| R-TX-2 | S2-TX | `messageId` on every row, and `TranscriptState.messageOrder`/`messageCount` (plan 09 §3.1): replaces `RowIds` and `MessageOrder`. |
| R-TX-3 | S2-TX | `handle.search` (B-CHAT-115), `removeMessage`, `reload(reset)`, `materialize`, and the part's own text for copy reply. |
| R-DLV-1 | S2-DLV | `PromptDelivery` + `ConversationActivity` behind `ChatWorld.send/pending/retrySend/stop` (receipts, offline queue, the full live line). |
| R-C-1 | coordinator | In the merge that ships this unit: add `":feature:chat"` to `native/androidApp/build.gradle.kts` (and `desktopApp`), delete the P2 `entry<ChatRoute>` (and `ChatPreviewViewModel`) from `:feature:chats` — Nav3 allows one `entry<ChatRoute>`. Then build the preview APK. |
| R-C-2 | coordinator | K1 vs plan 09 §7.3/§15 (Compose host tests in `:feature:chat`): decide the home (see §5). |
| R-CONN-1 | connection engine | `ReadState.markRead(ref, idle)` (B-CHAT-012) for `ChatWorld.markRead`. |
| R-COMP-1 | S3 composer | `Drafts` behind `ChatWorld.draft/saveDraft`, and the `oc.pendingDiscard.<profileId>` record (07 §2.5). |

## 7. Catalog requests

None. No library was added; the module uses only catalog entries (`kotlinx-collections-immutable`) and project modules
(`:engine:connection`, `:engine:transcript`, `:ui:kit-chat`).

## 8. Open problems

1. The screen is not reachable on the preview build until R-C-1, and until a profile's connection is live in that build
   it shows "Couldn't open this conversation" (no live graph → offline) — correct, but not the J1–J3 journey.
2. J1–J3 (send/stream/stop, long history fling, reopen) need an emulator (S2-BENCH depends on this unit); none here.
3. `GraphChatWorld` keeps its mutable state on the main thread and the graph's lane without a lock; acceptable for the
   interim binding, replaced by engines (R-TX-1, R-DLV-1).
4. A chat opened mid-run shows the live line only if `sessionStatuses` says busy; the P3 engine has no run snapshot.
5. Teaching tips for the prompt long-press (S2-TEACH registry) are not registered; the tap path is visible.
6. Accessibility order (plan 09 §7.9: traversal groups and indexes on header, list, composer) and keyboard handlers
   (§7.10) are not wired; SP-6 on the emulator is still owed.
