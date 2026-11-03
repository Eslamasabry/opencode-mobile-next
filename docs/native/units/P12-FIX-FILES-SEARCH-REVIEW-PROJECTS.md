VERIFIED: green

# P12-FIX-FILES-SEARCH-REVIEW-PROJECTS — Codex second-eye findings for FILES, SEARCH-USAGE, REVIEW, PROJECTS

Date 2026-11-03. Branch `claude/native-parity-p12-j3x7mc` (the session's fixed name), base
`native/p12-qa-wave4-2026-11-03` at `e61b9d22`. Sources: `docs/native/reviews/p12/{files,search,review,projects}.md`.

Shipping states, kept apart: **implemented + committed + pushed** on this branch; **verified** by the commands under
"Tests and results"; **not** signed, delivered, released or run on a device/emulator.

## Environment (not committed)

- Android SDK installed from `commandlinetools-linux-16111833` (`platform-tools`, `platforms;android-37.1`,
  `platforms;android-36`, `build-tools;37.0.0`, `build-tools;36.0.0`), JDK 21, `local.properties` (gitignored).
- Maven Central answered **429** to this machine. An uncommitted `~/.gradle/init.d/central-mirror.gradle.kts` puts
  Google's mirror of Central (`maven-central.storage-download.googleapis.com/maven2`) ahead of the declared repos.
- Two helper agents worked in separate worktrees (PROJECTS, SEARCH-USAGE); their commits were cherry-picked here.
  Parallel Gradle daemons once hit the container's memory limit (one daemon was OOM-killed); the affected runs were
  repeated, and only completed runs are reported.

## Findings

### FILES (`reviews/p12/files.md`)

| # | Finding | Verdict | Fix and test |
|---|---|---|---|
| 1 | MAJOR Files reads the gateway's location, not the conversation's | **fixed** | Nobody calls `setLocation`, so every conversation read the server's default folder. New facet `LocatedFileGateway.filesAt(location)` (core/domain `Facets.kt`, additive); `Oc2Gateway` sends `location[directory]` per request. `ConversationFiles` reads the conversation's own `directory`/workspace and fails (never falls back to another tree) when it can't. The mention directory now comes from the conversation too. Tests: `ConversationFilesTest` (B-NAV-026 ×2), `Oc2LocatedFilesTest` (B-NAV-026). |
| 2 | MAJOR Stop sharing leaves active downloads running | **fixed** | `SocketShareServer` tracks accepted sockets; `close()` closes the listener **and every open connection** (Flutter `close(force: true)`). Test: `FilesWiringTest` "B-MEDIA-034 Stop sharing also ends a download that is already running" (64 MiB body, Stop after the first bytes). |
| 3 | MAJOR Untrusted PDFs parsed in the app process | **fixed** | Ported the Flutter app's isolated `LocalPdfService` (frozen FQCN `io.github.eslamasabry.opencode_mobile.LocalPdfService`, `isolatedProcess`, `:local_pdf`, not exported, 10 s watchdog, kill on unbind). `AndroidPdfPages` now only prepares two unlinked descriptors, `bindIsolatedService`s a fresh instance per page, one request app-wide, 12 s deadline, and checks the PNG signature + IHDR size before anything decodes it. API < 29 → `PdfNeedsAndroid10`. Tests: `FilesPdfIsolationTest` (B-MEDIA-004: no `PdfRenderer(` in the app wiring, manifest declaration; PNG/size acceptance). The instrumented `LocalPdfServiceTest` (plan 10 N6, T3) is **not** written/run here (no emulator). |
| 4 | MAJOR PDF pages accumulate without bound | **fixed** | `FileViewModel.keepNear`: rendered pages stay only within 3 pages of the one just shown and under 24 MiB of PNGs; a dropped page renders again when reached (its `LaunchedEffect` re-requests it). Test: "B-MEDIA-001 PDF pages far from the one shown are released and render again when reached". |
| 5 | MAJOR One viewer adopts and stops another's share | **fixed** | `WifiShareState.id` names the start that made it; `WifiShare.stop(id)`/`stopLater(id)` stop only that share; the viewer adopts only its own id and shows Ended when replaced. A sheet closed while starting stops its own share once it starts. Test: "B-MEDIA-034 a second viewer's share is never adopted or stopped by the first". |
| 6 | MAJOR Save journey unreachable | **fixed** | New port `FileSaver`; viewer shows a visible **Save** button; Android `AndroidFileSaver` opens the system create-document picker on the resumed activity and writes the **exact original bytes** (`openOutputStream(uri, "wt")`). Cancel = nothing; failure = "Couldn't save the file. Try again."; success = "<name> saved to your device." (Flutter copy). Tests: "B-MEDIA-001 Save hands over the exact original bytes and says where they went" (VM), "B-MEDIA-001 Save is a visible action and a saved file says so" (screen). |
| 7 | MINOR Truncated text shown as complete | **fixed** | Text/Markdown/Code bodies with `truncated` show the kit notice "Showing part of this file" above the body. Test: "B-MEDIA-001 a file longer than the viewer shows says only part of it is shown". |

### SEARCH-USAGE (`reviews/p12/search.md`)

Five real and fixed (helper agent, red first), one real but outside this unit's write set.

| # | Finding | Verdict | Fix and test |
|---|---|---|---|
| 1 | MAJOR results lose project context | **real, not fixed here** | Root cause is app-wide: nothing calls `setLocation`, and the chat/composer never pins an opened conversation's folder (the same happens when opening from the Conversations home). That is chat/composer code ("do not touch chat, composer"). Integrator: when the chat opens a conversation, read its directory/workspace and use it for composer file search and mentions (Files now does this through `ConversationFiles` + `LocatedFileGateway`, which the composer can reuse). |
| 2 | MAJOR archived-only first page strands results | **fixed** | No match shows Load more (and Try again after a failed page) while the server has more; the ViewModel's Load more leaves No match once matches arrive. Tests: "B-PROTO-020 an empty first page that keeps a cursor offers load more and reaches later matches" (VM), "B-PROTO-020 no match on this page still offers Load more while the server has more, and Try again after a failure" (screen). |
| 3 | MAJOR short pages drop the cursor | **fixed** | `Oc2SearchUsage` keeps the returned `cursor.next` whatever the page length. Tests: "B-PROTO-020 a short page keeps the cursor the server returned", "B-PROTO-020 an archived-only page arrives empty and still keeps its cursor". |
| 4 | MAJOR cached usage relabelled with a new time zone | **fixed** | The time zone is part of a reading's identity; a reading for another zone is dropped, never relabelled (Flutter `usage_overview.dart:195`). Test: "B-NAV-078 a failed refresh after the time zone changed never relabels the old totals with the new zone". |
| 5 | MINOR older projects missing from usage | **fixed** | `Oc2SearchUsage.projectChoices`: the current project first (`GET /location`, falling back to `GET /project/current` like Flutter), then the recent conversations' projects. Tests: B-PROTO-122 ×3. |
| 6 | MINOR restoration races a recent-search write | **fixed** | New common `RecentSearchHistory` (feature/search): restoration and every write run one at a time and a write restores first; `AndroidRecentSearches` delegates to it. Test: "SCREENS-NAV-34 a search remembered before start-up restoration runs keeps the saved history" (the review names no row). |

### REVIEW (`reviews/p12/review.md`)

| # | Finding | Verdict | Fix and test |
|---|---|---|---|
| 1 | MAJOR OC2 undo journeys unreachable | **fixed** | Applied the unit's own `P12-REVIEW-oc2-staged-revert.patch` (clean): `Oc2Gateway` implements `StagedRevertGateway`, maps `session.revert` to `stagedRevert`, `sessionRevert = true`, stable clears with `DELETE .../revert`. Tests: the patch's 4 `Oc2LiveGatewayTest` cases (B-PROTO-031 ×3, B-CHAT-104), all green. Side effects the unit named: Undo from here now shows on OC2 prompts, and the offline queue's revert preflight now runs on OC2. |
| 2 | MAJOR `RevertChanged` leaves the transcript unchanged | **real, not fixed here** | The fix is in `:feature:chat` (`P3Bridge`) / the transcript engine, both outside this unit's write set ("do not touch chat"). Integrator: on `RevertChanged` for the open conversation (and after this app's own stage/clear/commit), reset or refetch the chat history (Flutter invalidates structural history). |
| 3 | MAJOR Changes misses working-tree changes | **fixed** | New facet `LocatedVcsGateway.workingTreeAt(location)`; `Oc2Gateway` reads `GET /vcs/diff?mode=working` for the conversation's own folder. When the tools reported no changes for the whole conversation, Changes shows the folder's uncommitted files with Flutter's notice "Showing Uncommitted: it is the view with changes."; a file opens its working-tree diff (`ReviewRoute.File(workingTree = true)`, a defaulted field). Tests: "B-NAV-053 a conversation whose agent changed files by command shows the folder's uncommitted changes", "B-NAV-053 tool changes stay the view when there are any". |
| 4 | MAJOR Review comments cannot reach the composer | **fixed** | Changes offers **Send back with a note** (Flutter's `review-send-back`) as a visible action; the note field ("Your comment", "Send back") formats a review-comment reference with `ReviewReference.format` and appends it to **this conversation's draft** (`ReviewDrafts` → `ConversationDrafts`), then returns to the chat. Nothing is sent to the server from Review. Tests: presenter ×2 and screen ×2 (B-CHAT-098). Not done: the composer's staged-reference chips (`ReviewReferences` store) — that is composer code. |
| 5 | MAJOR Stage ignores a changed remote boundary | **fixed** | `ConversationReview.basis()` + `stage(..., reviewed)`: the server is read before dispatch and a moved boundary is refused as `StaleScope`, nothing sent. Undo from here and Undo all pass the boundary their page read; Undo from here turns to `UndoBlock.Stale` on a remote change or a refused stage. Tests: engine "B-CHAT-105 a remote stage after the page read refuses Undo from here before anything is sent"; presenter "B-CHAT-105 another client staging while Undo from here is open…", "B-CHAT-105 a missed remote stage is caught by the read before dispatch". |
| 6 | MAJOR Review authorisation survives connection replacement | **fixed** | `StagedReview.connection` records the connection it was read on; `isCurrent` requires the same one, and a gateway swap bumps the revision. Test: "B-CHAT-104 a review read on one connection never commits through its replacement". |
| 7 | MINOR Equal timestamps miscount | **fixed** | Equal times now count rows **after** the prompt in page order. Test: "B-CHAT-103 equal times keep page order, so a reply at the prompt's time still follows it". |

### PROJECTS (`reviews/p12/projects.md`)

All seven are real and fixed (helper agent, red first: 11 new tests failed before the fix).

| # | Finding | Fix and test |
|---|---|---|
| 1 | MAJOR filtered pagination unreachable | Load more and its Try again also show in No match while a cursor remains; auto-load keyed on pages read. Tests: "SCREENS-NAV-34 a search with no loaded match still offers Load more…", "…a failed older page under a filter with no match keeps its Try again", "…reaching the end keeps asking for older pages…". |
| 2 | MAJOR Running never follows live activity | ViewModel follows RunStarted/RunRetrying/RunEnded per live server, rereads statuses after reconnect. Test: "SCREENS-NAV-34 Running follows live run events…". |
| 3 | MAJOR AI Team conversations in ordinary lists | Mirrors Dart `isAiTeamDirectory` (`/root/aiteam`, `.gc`, `.oc/aiteam`, `.`/`..` resolved) in chats and projects (one copy per module: feature modules can't share code). Tests: B-AGENT-264 ×3. |
| 4 | MAJOR reload during Undo abandons deletion | A pending delete keeps its own session copy; on commit it goes to that profile's current server; only sent deletes stay hidden. Test: "SCREENS-NAV-34 a reload inside the undo window still sends the delete…". |
| 5 | MINOR project titles keep markup | Project titles use the chats module's rules (half-cut markers, ISO stamps). Test: B-NAV-067. |
| 6 | MINOR Opened before stale | Picker rereads Opened before whenever it is on top again. Test: B-ANDR-078. |
| 7 | MINOR unknown timestamps reorder | Projects dated ≤ 0 keep the server's order. Test: B-NAV-011. |

## How to reach each screen

- Files: Conversations → a conversation → **Files** (top bar) → folders → a file. Viewer: Ask about this file / Share on
  Wi-Fi / Open with / **Save**.
- Review: chat top bar **Changes** (whole conversation; working-tree fallback; **Send back with a note**); a prompt's ⋯ →
  **Undo from here** (now on OC2); **Review the undo** from Changes when one waits.
- All conversations: Conversations home → Show all conversations. Projects: Conversations home → Projects.
- Search/Usage: unchanged entry points (see P12-SEARCH-USAGE.md).

## Tests and results

Final candidate `0b51e0a9` (plus this report). Each line is a fresh Gradle execution on this machine (JDK 21,
Android SDK above), counts from the JUnit XML:

| Command | Result |
|---|---|
| `:engine:files:desktopTest` | 24 tests, 0 failed |
| `:feature:files:desktopTest` | 33 tests, 0 failed |
| `:engine:review:desktopTest` | 29 tests, 0 failed |
| `:feature:review:desktopTest` | 33 tests, 0 failed |
| `:feature:chats:desktopTest` | 49 tests, 0 failed |
| `:feature:projects:desktopTest` | 26 tests, 0 failed |
| `:feature:search:desktopTest` | 25 tests, 0 failed |
| `:feature:usage:desktopTest` | 21 tests, 0 failed |
| `:protocol:oc2:desktopTest` | 114 tests, 0 failed, 1 skipped (the opt-in live-server test) |
| `:core:domain:desktopTest` | 12 tests, 0 failed |
| `:feature:chat:desktopTest` (OC2 `sessionRevert` flip side effects) | 216 tests, 0 failed |
| `:engine:delivery:desktopTest` (revert preflight now on OC2) | 98 tests, 0 failed |
| `:androidApp:testPreviewDebugUnitTest` (incl. `FilesWiringTest`, `FilesPdfIsolationTest`) | 18 tests, 0 failed |
| `:quality:test` (Konsist, module rules, graph smoke) | 59 tests, 0 failed |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL (`androidApp-preview-debug.apk`) |
| `native/tool/check_commits.sh origin/native/p12-qa-wave4-2026-11-03 HEAD`, `scan_push.sh` | OK / clean |

The first `:quality:test` run failed K1 ("features arrange kit parts only") on three new feature files importing
`kotlinx.coroutines.sync.Mutex` / `Job`; fixed in `0b51e0a9`, then 59/59. The chat/delivery suites ran after the last
code change; the FILES/REVIEW/PROJECTS/SEARCH suites ran before the K1 commit and the three touched suites
(chats, search, files) ran again after it.

Not run: the instrumented `LocalPdfServiceTest` (T3, emulator), any emulator/device journey, `build-logic` tests,
the full repository suite.

## Deviations and integrator notes

- **Outside the four units' modules** (needed for the fixes): `core/domain/Facets.kt` (two additive facet interfaces),
  `protocol/oc2/.../Oc2Gateway.kt` + `Oc2Dialect.kt` (the review unit's patch, the two facets), `ui/routes/ReviewRoutes.kt`
  (one defaulted field), `androidApp` manifest (the frozen `LocalPdfService` declaration) and the new
  `androidApp/.../LocalPdfService.kt` (frozen FQCN per plan 10 §11.6; it cannot live in `graph/FilesWiring.kt`).
- Search finding 1 and Review finding 2 are real but belong to chat/composer (location pinning of an opened
  conversation; transcript reset on revert). Both are listed for the chat owner.
- `ConversationFiles`/`workingTree` read the session once per screen to learn its folder (one extra GET).
- No red-run receipt exists for the FILES/REVIEW tests written in this session: they were written with the new
  APIs (`filesAt`, `FileSaver`, `stop(id)`, `basis()`), so against the old code they fail to compile (API-red only).
  The PROJECTS tests and SEARCH tests have behavioural red runs (helper reports).
