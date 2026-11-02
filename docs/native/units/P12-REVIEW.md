# P12-REVIEW — change review: turn files, diffs, Keep all / Undo all, Undo from here

Branch `native/p12-review-2026-11-03` (same commits on the session branch `claude/review-diffs-undo-pryfut`), based on
`native/m1-2026-11-03` `8a4eee69`. Flutter reference: `lib/ui/screens/staged_revert_screen.dart`,
`lib/ui/screens/review_workspace.dart` (Looks good / Undo all), `lib/ui/screens/chat/chat_files.dart` (turn files),
`lib/ui/screens/chat/chat_message_actions.dart` (Undo from here), `lib/domain/run_result.dart` (edited paths and
counts), `lib/state/connection/revert.dart`, `lib/api2/gateway_operations/ops_sessions.dart`.

## Status in one paragraph

**Implemented and committed, partially verified, not built.** The engine, the four pages, the routes and the chat
entry points are written, and the engine and presenter logic passes 41 tests plus 4 new OC2 protocol tests. **No
Gradle build or APK was possible in this container**: the network policy blocks `dl.google.com`, which serves both the
Android SDK and Google Maven (AGP, androidx). Compose screens, the Metro graph and `:androidApp:assemblePreviewDebug`
are therefore **unverified**: compile them first. Against OpenCode 2 the undo journeys need the protocol patch
described below. Without it the pages still open, list the turn's files and show diffs, but they say plainly that this
server can't undo.

## Journeys

| Journey | How to reach it | State |
|---|---|---|
| A turn's changed files with `+a −r` from the tools' own counts; tap a file to see its diff | Chat → tap or long-press a reply's footer → **Changes** | Logic tested; screen not compiled |
| The whole conversation's changed files | Chat top bar → **Changes** | Logic tested; screen not compiled |
| **Looks good** (keep all): goes back, sends nothing | Changes page, primary button | Tested |
| **Undo all**: asks "Undo N changed files?", stages a server undo from the changes' first prompt with files back, then opens **Review the undo** | Changes page, second button | Tested; on OC2 needs the patch |
| Per-file undo: **not offered**. The page says plainly that one-file undo isn't available yet and points to Undo all / Undo from here | Changes page, line under the files | Done (server has no per-file revert) |
| **Undo from here** on a prompt. Staged servers: quotes the prompt, "Put files back too", then "Undo and review". One-step servers: lists the files the agent edited and the message count, then "Undo now" | Chat → prompt menu → **Undo from here** (shown only when `sessionRevert` is on, never while a reply runs) | Tested; on OC2 needs the patch |
| **Review the undo**: the prompt, the exact count of hidden messages (unknown is never shown as 0), the server's file preview (each opens its diff), **Put everything back** (clear) or the destructive **Delete the hidden messages** (commit). Each asks first and names the consequences; stale / none / kept / restored states | After staging; or Changes page → "Review the undo" while one is waiting | Tested; on OC2 needs the patch |

The pages are kit parts only: KitScreen, KitTopBar, KitRowGroup/KitRow, KitSwitchRow, KitActionBlock, KitConfirm,
KitStateView, KitNotice/KitErrorNotice, and KitDiffView/DiffModel from `:ui:kit-chat`. Failures show a plain line
with Details, never raw text. Copy is in English and Arabic. Motion, ripple and predictive back come from the kit and
the shell. No new bar level was added: the top-bar Changes action sits next to Search, and the menu items are rows in
the existing message menu.

## What was built (write set)

- `native/engine/review/**` (new, core-only, ADR §6.2):
  - `TurnChanges`: edited paths (first seen, once) and per-file counts, taken only from completed, executed
    edit/write/patch tools (`metadata.filediff`, `metadata.files[]`; a new file's line count; an overwrite stays
    unknown). It also gives the turn boundaries (a synthetic user row is not a prompt), the prompt words, and the
    diff text: the first `before` and last `after`, or the reported patches in order.
  - `ConversationReview`, one per conversation:
    - `state` (the staged undo, a revision, running/working, the title).
    - `shape()` (Staged / OneStep / Unavailable, from capabilities and facets).
    - `changes()`, `history()` and `messagesAfter()` (counted across pages, the prompt excluded, unknown → null).
    - `stage()`, `undoNow()`, `putBack()` and `keep()`.

    Mutations run one at a time; a second one is refused (`Busy`), not queued. A running conversation refuses. A
    connection swap during preflight dispatches nowhere (`StaleScope`). Keep and put-back re-read the server first,
    so a stale review is blocked even if its event was missed. A lost answer to keep or put-back is reconciled by a
    read before anyone retries. `RevertChanged`, `SessionUpserted` and `Connected` events refresh the state.
- `native/feature/review/**` (new):
  - `ReviewHub` (app-scoped, follows `ConnectionRegistry.live`, keeps the 6 most recent reviews) and
    `ReviewGraphAccessors` (ConnectionScope).
  - Plain-Kotlin presenters and Ui models, thin ViewModels, `ReviewScreens.kt` and `ReviewEntries` (contributed
    `EntryInstaller`).
- `native/ui/routes/.../ReviewRoutes.kt` (new file): `ReviewRoute.Changes`, `.File`, `.UndoFromHere` and `.Staged`.
  Routes carry ids only; every page reads the server again.
- `native/ui/words/.../values{,-ar}/strings_review.xml` (new files, 10 strings). Everything else reuses the
  converted Flutter copy (`reviewRevert*`, `undoFromHere*`, `reviewUndo*`, `kitDiff*`). The drift check allows copy
  added after conversion.
- App wiring: two lines in `native/androidApp/build.gradle.kts` (`:engine:review`, `:feature:review`). **No
  `ReviewWiring.kt` was needed**: there are no Android-only bindings, and the hub and accessors are Metro
  contributions.
- Smallest reach edits in `:feature:chat` (chat lead to review):
  - `MessageAction.Changes` and `MessageAction.UndoFromHere`, with their labels and routes.
  - The `undo` and `changes` flags on `ChatCapabilitiesUi`.
  - `ChatActions.openChanges()`, with an empty default so other implementations keep compiling.
  - The top-bar Changes action.
  - Two exact-list assertions in `ChatPresenterTest` changed: one now also expects Undo from here, and the other test
    turns `sessionRevert` off. One new test each in `ChatRulesTest` and `ChatPresenterTest`.

## The protocol blocker and the patch (integrator / protocol owner)

`Oc2Gateway` currently has `sessionRevert = false`, does not implement `StagedRevertGateway`, and does not map
`session.revert` into `Session.stagedRevert`. `protocol/oc2` is outside this unit's write set, so the change is
delivered as **`docs/native/units/P12-REVIEW-oc2-staged-revert.patch`** (178 lines, `git apply --check` clean on this
branch). The patch:
- implements `StagedRevertGateway`:
  - `sessionRevertPrompt`: GET `/session/{id}/message/{mid}`, the user message's words or its file names, null for a
    reply.
  - `stageSessionRevert`: POST `.../revert/stage {messageID, files}` → `{data: Revert}`.
  - `clearSessionRevert`: POST `.../revert/clear`.
  - `commitSessionRevert`: POST `.../revert/commit`.

  Each makes one request.
- adds the stable dialect route for clear: DELETE `/session/{id}/revert` (from `dialect.dart`).
- maps `stagedRevert` (with its `FileDiff` preview) in `sessionValue`.
- sets `sessionRevert = true`.
- adds 4 tests to `Oc2LiveGatewayTest`: B-PROTO-031 ×3 and B-CHAT-104.

Apply it with `git apply docs/native/units/P12-REVIEW-oc2-staged-revert.patch`. It flips no other flag. Side
effects to check:
- the offline queue's revert preflight now runs on OC2 (`offlinePromptQueue` is still false there);
- the prompt menu now shows Undo from here on OC2.

`sessionDiff` stays false: OC2's `/vcs/diff` is a working-tree diff, not a per-conversation one. The Changes pages
use the tools' own records instead. `ConversationReview.workingTree()` is ready for when the flag is turned on.

## Catalog rows

Covered by tests:
- **B-CHAT-058**: the turn's diff from the pair or the patches; the staged preview opens as is; the diff view is
  KitDiffView with DiffModel.
- **B-CHAT-068**: per-file sums in order, omitted when not reported; the new-file count.
- **B-CHAT-101**: only completed, executed edit tools.
- **B-CHAT-103**: the count across pages and kinds, the prompt excluded, unknown → null.
- **B-CHAT-104**: synthetic rows are not prompts; closed connections do no reads; the attachment-only prompt; the OC2
  prompt read.
- **B-CHAT-105**: the preview comes back at once with its metadata; a remote stage makes the review stale and blocks
  the commit; fresh boundaries block the commit without the event; the stale question closes.
- **B-CHAT-106**: an ambiguous commit is reconciled; concurrent mutations are refused; a busy conversation can't stage;
  a swap mid-preflight dispatches nowhere; a waiting undo replaces Undo all.
- **B-CHAT-120**: edited paths first seen, once; the staged and one-step shapes; menu gating.
- **B-PROTO-031**: distinct stage, clear and commit endpoints; stable DELETE; the session carries the revert.
- **B-L10N-010** (Arabic review copy, screen test).

Still missing:
- **B-NAV-039** (the "review after a run that changed files" tip: needs the teaching registry, owned by S2-TEACH).
- **B-CHAT-098** (review comments staged into the prompt: "Send back with a note", a composer handoff).
- **B-STATE-121** (the staged tail preview in the cache).
- **B-PROTO-043** is protocol-side and already covered by the existing tests.

## Tests and results

These ran in this container with a standalone Kotlin 2.4.0 compiler (from the Gradle distribution) and JUnit 4,
against the module sources, because Gradle could not resolve AGP:

| Suite | Result |
|---|---|
| `:engine:review` commonTest (`TurnChangesTest`, `ConversationReviewTest`) | **25 passed, 0 failed** |
| `:feature:review` commonTest (`ReviewPresenterTest`) | **16 passed, 0 failed** |
| `:protocol:oc2` `Oc2LiveGatewayTest` with the patch applied | **23 passed** (19 existing + 4 new), 0 failed |
| `:feature:review` desktopTest `ReviewScreensTest` (9 Compose tests by visible text, including Arabic) | **written, not run** (needs Compose and the generated `Res`) |
| `:feature:chat` changed tests (`ChatRulesTest`, `ChatPresenterTest`) | **edited, not run** (needs Gradle) |
| `:androidApp:assemblePreviewDebug`, `:quality:test`, Metro graph | **not run** (blocked) |

These are not a red-first record: most tests were written alongside the code. Test names follow the catalog rows.

## Integrator checklist

1. Make sure Gradle can reach `dl.google.com` (Android SDK and Google Maven), then run
   `./gradlew :engine:review:desktopTest :feature:review:desktopTest :feature:chat:desktopTest :androidApp:assemblePreviewDebug`.
   Fix any compile slips in `ReviewScreens.kt`, `ReviewEntries.kt`, `ReviewViewModels.kt` or `ReviewHub.kt`. Those
   files were never compiled.
2. Add the `MODULE_OWNERS` rows for `:engine:review` and `:feature:review` (`ModuleOwnersTest`).
3. Decide on `engineOrder` in `build-logic/.../ModuleRules.kt`. `review` is not listed, so the engine may use core
   only; that is how it is written.
4. Apply `P12-REVIEW-oc2-staged-revert.patch` (protocol owner) and run `:protocol:oc2:desktopTest`.
5. Run on the emulator: chat → prompt menu → Undo from here → Undo and review → Review the undo → Put everything back,
   then the same with Delete the hidden messages. Then chat → Changes → a file's diff → Undo all.

## Deviations

- Undo from here is a page (SlideUp route), not a bottom sheet. It behaves like Flutter's sheet, keeps the choice
  through a failure, and is a routed screen as the brief asks.
- The Flutter review workspace's view picker, comments and "Send back with a note" are not built. Neither is a
  working-tree view: OC2 has no per-conversation diff. Turn and conversation scopes come from tool records, labelled
  "From what the agent's tools reported".
- "Changes" on a reply always appears in its menu. A turn with no edits opens the page's empty state ("No changes")
  rather than hiding the item, since the chat presenter doesn't see tool parts.

## Verified on the VPS (2026-11-02, branch `p12v/review`)

Base: `origin/native/p12-review-2026-11-03` @ `03e35f49a` (already on `origin/native/m1-2026-11-03` @ `8a4eee69c`; no
rebase). JDK 21, Android SDK, real Gradle (`-Dorg.gradle.workers.max=3 -Xmx3g`, `nice -n 5`).

| Command | Result |
|---|---|
| `:engine:review:desktopTest` | 26 tests, 0 failures (25 original + `ReviewLifetimeTest`) |
| `:feature:review:desktopTest` | 25 tests, 0 failures, after fixes 1 and 2 (`ReviewScreensTest` was 4/9 red on its first run) |
| `:feature:chat:desktopTest` | test sources did not compile (base composer WIP); after base fix 4: 104 run, the unit's `ChatPresenterTest` 20/20 and `ChatRulesTest` 14/14 pass; 8 composer WIP tests fail (below) |
| `:ui:routes:desktopTest`, `:ui:words:desktopTest` | 1 / 4 tests, 0 failures |
| `:androidApp:assemblePreviewDebug` | green on the first compile (Metro graph with `ReviewHub`, `ReviewGraphAccessors`, `ReviewEntries`) |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures |
| `:quality:test` | did not compile (base, fix 5); then 48 run, 2 failures, both base (below) |
| `:protocol:oc2:desktopTest` with `P12-REVIEW-oc2-staged-revert.patch` applied temporarily | 96 tests, 0 failures; the patch was reverted afterwards and is **not** committed (protocol owner's call, checklist 4) |

Fixes in the unit's write set:

1. `ui/words/.../values/strings_review.xml`: `\'` → `'`. Compose resources do not unescape `\'` (every other words file
   writes a plain apostrophe), so the screens showed a backslash: "This server can\'t undo from a prompt." The Arabic
   file had none.
2. `ReviewScreensTest`: file-name rows are found with `onNodeWithContentDescription(name)` instead of
   `onNodeWithText(name)`. `KitRow(titleIsFileName = true)` draws `Main​.kt` with a zero-width break before the dot and
   sets the plain name as the row's spoken description. Every assertion and click is the same; only the lookup changed.
3. K1 (features import kit parts only): `ReviewHub` imported `kotlinx.coroutines.Job`, `SupervisorJob` and `cancel`.
   New `engine/review/ReviewLifetime` (a child supervisor scope with `close()`, plus `ReviewLifetimeTest`). The hub uses it,
   so its behaviour is unchanged: each review has its own child of the app scope on the main context, and evicting the
   oldest of 6 stops only that review.

Shared files outside the write set (needed to compile or pass the gates):

4. `feature/chat` tests: cherry-picked `45708691c` from `p12v/files` (the same base composer-WIP compile fix: a
   `filePickers` argument, two selection-fake members, `open class FakeChatWorld`, no assertion changed).
5. `quality/.../ChatWorldReviewTest.kt`: `filePickers = emptySet()` (same base fix as `p12v/settings`/`p12v/files`).
6. `MODULE_OWNERS`: rows for `:engine:review` (S, chat-lead) and `:feature:review` (F), so `ModuleOwnersTest` passes (checklist 2).
   The coordinator should confirm the owners.

Still red, not this unit's (the files verifier reproduced them on the bare base):

- `:feature:chat:desktopTest`, 8 composer model/file WIP tests: `ComposerFilesViewModelTest` ×4,
  `ComposerModelsFilesTest` ×3, `WorldComposerFilesModelsTest` B-CHAT-107 ×1.
- `:quality:test` K1 on `feature/chat/.../composer/BasicComposerHost.kt` (base file).
- `:quality:test` `p3/TranscriptStreamUiTest` 6,000-word stream: 1 min `runTest` timeout on the loaded shared VPS (load
  average ~20 on 8 CPUs). It also failed on `p12v/settings`. It touches no review code.

Not done: checklist 3 (`engineOrder` decision, left as core-only), checklist 4 (apply the OC2 patch), and checklist 5
(emulator journeys; there is no emulator on the VPS).
