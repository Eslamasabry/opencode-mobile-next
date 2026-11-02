# P12-PROJECTS — projects picker, project page, All conversations

Date: 2026-11-02/03. Branches: `native/p12-projects-2026-11-03` (as briefed) and the session branch
`claude/projects-picker-page-parity-sv16s9` (same commits). Base: `native/m1-2026-11-03` at `8a4eee69`.

**Shipping state: implemented and committed, NOT compiled, NOT tested, NOT run on a device.** This container's
network policy refuses `dl.google.com` (403 on CONNECT), which serves the Android SDK *and* Google Maven
(`maven.google.com` redirects there), and Maven Central answers 429 now and then. So the Android SDK could not be
installed and Gradle cannot even resolve its settings plugins. Every test below is written but has **never run**;
every result column says so. The integrator must build and run them first (§5).

## 1. Journeys now wired (reachable in the Android app once it compiles)

| Journey | How to reach it | What it does |
|---|---|---|
| All conversations | Conversations home → "Show all conversations" (existing row) → `ChatsRoute.All` | Every conversation on every live server, newest first, 50 a page; "Load more conversations" is a visible row and also fires on reaching the end; a failed older page keeps its cursor and shows a plain line, Details and Try again. Search over loaded titles and project names; Running, Archived (only when something is archived) and recent-project chips. Subagent sessions are left out. More than one server: each row names its server. No server: "No server connected" with Servers. |
| Rename | All conversations: long-press a row → Rename, or Select → one row → Rename | Kit input dialog starting from the readable title; blank refused in the dialog; `renameSession` on the row's own server; a refusal shows "Couldn't rename the conversation." with Details. |
| Delete with undo | Row menu → Delete conversation, or Select → rows → "Delete N conversations" | Rows hide at once with the kit Undo bar ("Deleted “X”" / "N conversations deleted"); nothing is sent until the window ends. A second delete sends the first; leaving the page (Back, opening a chat, ViewModel cleared) sends what is pending through the app scope. A refused delete brings the row back with "Couldn't delete a conversation, so it is back in the list." + Details. |
| Archive | Row menu → Archive conversation, or Select → "Archive N conversations" | Gated on `ServerCapabilities.sessionArchive` and an operations gateway. OpenCode 2 sets it false (Flutter's api2 also has no archive), so on today's servers the item/button is shown disabled with "This server can't archive conversations." |
| Multi-select | All conversations top bar → Select; or a row menu → Select | Check rows (kit `KitChoiceRow`), title "N selected", bottom actions Rename (one row), Archive N, Delete N; "Cancel selection" in the bar; Back leaves select mode first (`KitBackGuard`). |
| Projects picker | Conversations home → new tertiary row "Projects" (under "Show all conversations") → `ProjectsRoute.Picker` | Visible first rows: **New project**, **Open a folder on the server**, **Folder on this phone**; then **Opened before** (this phone's folders for this server); then **Projects on <server>** with counts ("3 conversations · 1 running · /path"). Server chips when more than one server is live (active server first). Search field from 8 projects. |
| Open a folder | Picker → Open a folder on the server | Kit path dialog prefilled with the parent of the folder used last; refuses relative paths, `..`, line breaks, the root and home folders in plain words; opens that folder's project page. |
| Project page | Any project / opened-before row / typed folder → `ProjectsRoute.Project(profile, directory)` | Title = folder name, subtitle = path. **New conversation here** first: `createSession(NewSession(location = directory))` on that server (the shared gateway location is never moved), then opens the chat with the composer focused; a refusal shows "Couldn't start the conversation." + Details + Try again. Then "Conversations in this project" (Load more visible) and **In other projects** (B-NAV-010: other projects only, running first, then recency, 5 at most). Opening the page remembers the folder under Opened before. |
| Switching project for a new conversation | Projects → a project → New conversation here | The new session carries the chosen folder as its location. |

Visibly unavailable, with the plain reason on the row (never a dead tap):

- **New project** — "This server can't make folders from the phone. Make the folder on that computer, then open it
  here." OpenCode 2 has no folder-making call; Flutter offered New project only for servers it ran itself (in-app
  Linux / Termux), which the native app does not have yet.
- **Folder on this phone** — "OpenCode on this phone isn't set up in this version yet." (The phone runtime is not
  integrated; RUNBOOK §6/§8.)

## 2. Where things are

| Piece | Files |
|---|---|
| Routes (new file) | `native/ui/routes/.../ProjectsRoutes.kt`: `ProjectsRoute.Picker(profile?)`, `ProjectsRoute.Project(profile, directory)` |
| Words (new files) | `native/ui/words/.../values/strings_projects_p12.xml`, `values-ar/strings_projects_p12.xml` (all `p12*`); the rest reuse migrated Flutter strings (`globalSessions*`, `projectFolder*`, `folderBrowser*`, `sessionMenu*`, `kit*`) |
| All conversations | `feature/chats/.../presentation/AllConversationsUi.kt` (ports, UI, actions), `AllConversationsRules.kt` (pure merge/filter/sort/paging), `ChatsAllViewModel.kt` (replaces the stub), `ui/ChatsAllScreen.kt` (replaces the stub screen) |
| Home entry | `ChatsActions.openProjects()`, `ChatsViewModel`, one tertiary row in `ChatsScreen.kt` |
| Projects module | `feature/projects/**` (new; auto-discovered): `ProjectsPorts.kt`, `ProjectRules.kt`, `ProjectsUi.kt`, `ProjectsPickerViewModel.kt`, `ProjectPageViewModel.kt`, `ui/*Screen.kt`, `ProjectsEntries.kt` |
| Android wiring | `androidApp/.../graph/ProjectsWiring.kt`: `LiveConversationServers` and `LiveProjectServers` (from `ConnectionRegistry.live` + saved profile names/active id, replacing the empty defaults), `StoredOpenedFolders` (replaces the in-memory default); `:feature:projects` added to `androidApp/build.gradle.kts` |

Storage: `oc.openedProjects.<profileId>` (string list, newest first, max 20), written through
`lanes.forProfile(profile)`. It matches `ProfileKeyRule`'s `oc.<what>.<id>` shape, so the existing profile sweep in
`PreviewRuntime.remove` → `ProfileDeletion` removes it; the profile lane is closed before the sweep, so a late write
is refused. Reads return empty for a profile that is not loaded.

## 3. Catalog rows

Tests are named after their rows (none has run — §0).

| Row | Test |
|---|---|
| B-NAV-010 | `ProjectRulesTest` › conversations elsewhere…; `ProjectsScreenTest` › the project page leads with New conversation here and lists In other projects |
| B-NAV-011 | `ProjectRulesTest` (order: update time, stable ties, unknown last; unnamed projects; temp/home never offered; servers without a project list); `ProjectsViewModelTest` (both read paths, failure keeps Try again); `ProjectsScreenTest` (counts, server name) |
| B-NAV-030 | `AllConversationsViewModelTest` › equal session ids from two servers stay two rows and each opens its own server |
| B-NAV-067 | `AllConversationsViewModelTest` (cleaned titles, subagents out); `ProjectRulesTest` (`ChatTitles`) |
| B-ANDR-078 | `ProjectRulesTest` (newest first, once each, cap, Opened before six); `ProjectsViewModelTest` (per-server, page remembers) |
| B-STATE-158 (path half) | `ProjectRulesTest` › a typed folder must be a full path… (folder-name creation half is unavailable, §1) |
| SCREENS-NAV-34 (B12) | `AllConversationsViewModelTest` (paging/dedupe/Try again, all-failed, no server, filters, delete + undo, refused delete, leaving sends, select, rename, archive gate); `AllConversationsScreenTest` (6 screen tests by visible text) |
| SCREENS-NAV-69 (D15/D16) | `ProjectsScreenTest` (visible first rows, reasons, dialog), `ProjectsViewModelTest` (New conversation here, refusal, no server) |

Still missing: B-STATE-021 (default project / "my-app" proposal: needs New project), the browse half of D16
(server folder browsing: OC2 `listFiles` is `Unavailable` in `:protocol:oc2` today), project rename (D15 "rename has a
visible menu": needs `renameProject`, unavailable on OC2), B-SEC-022 / B-LINUX-016/017 / B-ANDR-051 (phone and
built-in folder browsing), worktrees/health/services (other units).

## 4. Deviations

1. **No compile, no test run, no APK, no emulator** (network policy, §0).
2. Search is client-side over loaded pages (title + project name). The native `sessionPage` has no `search`
   parameter and `listGlobalSessions` is unimplemented in `:protocol:oc2`; Flutter searched on the server.
3. Projects come from `listProjects` only when `capabilities.projectManagement` (false on OC2 today, and the OC2
   operations do not implement it); otherwise from the folders of up to 3 pages (300) of conversations. The code
   switches by itself once `:protocol:oc2` implements `GET /project` and turns the flag on.
4. A typed folder is not probed before opening (Flutter listed `.` in it; native `listFiles` is unavailable). The
   server confirms the folder when New conversation here creates the session; a refusal is shown with Details.
5. `ChatTitles` in `:feature:projects` duplicates `:feature:chats` `SessionTitles` (features may not share code,
   K19). It should move to a lower module (`:core:model` or `:engine:agents`) by its owner.
6. The row long-press menu has no teaching tip yet (owner rule "teach what is hidden"); the visible twin is Select,
   which reaches every menu act. Flutter's chats rows had no long-press menu at all.
7. Delete uses Undo instead of Flutter's confirm dialog (the brief asked for delete with undo). The undo window is
   the kit's (8 s, stretched by the accessibility timeout, no timeout under accessible navigation).
8. Strings live in two new `:ui:words` files (`strings_projects_p12.xml`), outside the brief's literal write set,
   because features may not carry copy; new files only, no existing file edited.

## 5. For the integrator

1. Build and run, in order: `./gradlew :feature:chats:desktopTest :feature:projects:desktopTest :ui:routes:desktopTest`,
   `:quality:test`, `:androidApp:assemblePreviewDebug`, `:desktopApp:compileKotlin`. Fix whatever the compiler finds
   (a self-review against the real APIs was done, but it is no substitute).
2. Done here, review them: a `:feature:projects` row in `native/MODULE_OWNERS` (coordinator file; `ModuleOwnersTest`
   needs it), `:feature:projects` in `desktopApp/build.gradle.kts` (the home's Projects row needs a destination there),
   and two `:quality` tests that read the old stub (`GraphSmokeTest` now checks the route's running filter,
   `DesktopShellUiTest` now expects "No server connected").
3. Known small gaps: a rename/delete/archive problem notice has no dismiss of its own (it clears on the next action);
   archive capability is read when the server list changes, not on a later capability change.
4. Ask the `:protocol:oc2` owner for `listProjects` (`GET /project`), `listGlobalSessions` (server search,
   `rootsOnly`), and `listFiles` (folder probe/browse); the pages light up with no feature change.
5. Device check: Conversations → Projects → Open a folder → New conversation here → reply arrives in the chat; All
   conversations → Select two → Delete → Undo → Delete → wait → both gone on the server; profile removal clears
   `oc.openedProjects.<id>`.

## Verified on the VPS (2026-11-02)

Branch `p12v/projects`, from `origin/native/p12-projects-2026-11-03` (`b07eeead`). `native/m1-2026-11-03` was already
an ancestor, so no rebase was needed. JDK 21, real Android SDK, Gradle with `-Dorg.gradle.workers.max=3 -Xmx3g` under `nice -n 5`.

Commands and results (final run):

| Command | Result |
|---|---|
| `:feature:projects:desktopTest` | 21 tests, 0 failed |
| `:feature:chats:desktopTest` | 42 tests, 0 failed |
| `:ui:routes:desktopTest` / `:ui:words:desktopTest` | 1 / 4 tests, 0 failed |
| `:androidApp:assemblePreviewDebug` | built (`androidApp-preview-debug.apk`), so the Android side of `:feature:projects`, `:feature:chats` and `ProjectsWiring.kt` compiles |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failed |
| `:desktopApp:compileKotlin` | compiles |
| `:quality:test` | 48 tests, 2 failed, both outside this unit (below). `GraphSmokeTest` and `DesktopShellUiTest`, which this unit edited, pass |

`testDebugUnitTest` does not exist for `:feature:*` (their KMP Android targets have no host tests), so desktopTest is
the test run for those modules. Their Android code compiles as part of the APK build.

Fixes made (no assertion weakened, no test deleted):

1. `AllConversationsViewModelTest`: two test names had `;`, which Kotlin/JVM refuses in a name, so the chats test
   source set could not compile. Changed `;` to `,`. The names keep their wording.
2. `ProjectsPickerScreen` (open-folder dialog) and `ChatsAllScreen` (rename dialog): the `KitInputDialog` was placed
   after the `KitScreen` instead of inside its body, so it had no `KitSheetHost` and threw `KitRuleBroken` ("Kit dialogs
   need a KitSheetHost"). Each dialog now lives in its own private composable, which is called inside the screen body.
   This fixed `ProjectsScreenTest` › the open-folder dialog…, and it would have crashed the real app the first time
   either dialog opened.
3. `AllConversationsRules.build`: the no-server state dropped the route's filters, so `ChatsRoute.All("running")` with
   no server showed `running = false` (`ChatsViewModelTest` › route argument reaches the assisted ViewModel…). The
   no-server state now carries `running` and `archived`.
4. Shared file, outside the write set: `quality/.../ChatWorldReviewTest.kt` now passes `filePickers = emptySet()`. This
   is a pre-existing m1 break (the composer added the parameter), and `:quality:test` could not compile without the
   fix. It is the same fix the P12-SETTINGS verifier made.

Still failing in `:quality:test`, not caused by this unit (also failing on the settings branch):

- `KonsistRulesTest` K1: `feature/chat/.../composer/BasicComposerHost.kt` (m1 composer WIP). The chat owner needs to fix it.
- `p3/TranscriptStreamUiTest` 6,000-word reply: `UncompletedCoroutinesError` after its 1 min `runTest` timeout on the
  loaded shared VPS. It uses only chat/transcript code.

Remaining gaps: §5.5 device journeys were not run (there is no emulator on the VPS). §5.4 protocol asks (`listProjects`,
`listGlobalSessions`, `listFiles`) are still open.
