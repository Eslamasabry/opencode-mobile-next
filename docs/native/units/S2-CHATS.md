# S2-CHATS — bootstrap gate UI, root decision, first run, Conversations home

Date: 2026-11-01. Branch: `native/chats-2026-11-01`, created from `claude/native-chats-bootstrap-ui-j3xvyv` at
`7db496b2` (the M0 tip after the kita2 merge). Plan: `plan/15` S2-CHATS row, `plan/07` §2.6 and §5 A01/A03/B01/B15,
`plan/17` §1.6 and §1.14 (CR17-5). Write set: `native/feature/{onboarding,chats}/**`, `native/engine/agents/**/feed/**`
(the OpenCode-only `ChatFeed` and the return brief), this report.

**Shipping state:** implemented, verified locally on the desktop JVM (commands in §3), committed and pushed to the
branch. Not verified on the preview build or an emulator; the finish line (E2E-01, E2E-21 steps 1–7 on the preview
build) is **not reached**: the list has no live data until the connection owner binds `ConversationsFeed`, and the
gate is not yet hosted by the shell (§7). No PR, no CI run, no signing, no release; `master` and tags untouched.

---

## 1. What was built

### `:engine:agents` — `feed/` only (new module, see §5 D1)

| File | What |
|---|---|
| `ChatFeedItem.kt` | `ChatFeedItem` (only what a row shows, so equality drives the revision), `ChatStatus`, `ProjectSummary`, `ChatFeedSnapshot`, `ChatFeedFilter` (Dart `ChatFeedFilter.matches`). List key = `identity` (source, session, folder). |
| `FeedFolders.kt` | Ports of `isTemporaryProjectDirectory`, `isProtectedWorkspaceDirectory`, `isOtherFolderDirectory`, `otherFolderLabel`. |
| `ChatFeedSource.kt` | `ChatFeedSource` port (`current()`, `changes`, `refresh()`), `FeedSourceState` (partial failures kept per source), `OpenCodeListing` (read-only port the connection owner fills from its session, status and request maps), `OpenCodeChatFeedSource` (archived rows dropped, needs-you > running > failed, `finishedUnseen` → Done). |
| `ChatFeed.kt` | `ChatFeedRules.order` (needs you, running, newest, identity) and `projects()` (real folders only, newest first, chat and running counts); `ChatFeed` merges sources, follows them until `close()`, never closes a shared source, refuses `refresh()` after close with `Refused(ProfileClosing)`, bumps `revision` only when the visible snapshot changes. |
| `ReturnBrief.kt` | Port of Dart `ReturnBrief`/`ReturnBriefAck` over core `Session` (exact identities, bounded merge, malformed JSON = never dismissed). |
| `ReturnBriefStore.kt` | Port of `ReturnBriefStore` over a `BriefPreferences` port: `oc.returnBrief.<profileId>`, one lane per profile (merged queued writes), refused write publishes nothing (`StorageRefused`), `deleteProfile` drains then clears both copies. |
| `FeedDay.kt` | `FeedDay` (start of today on the person's clock, kotlinx-datetime) and `SystemFeedDay`. |

### `:feature:chats`

| File | What |
|---|---|
| `presentation/SessionTitles.kt` | `displaySessionTitleText` + `presentedSessionTitleText` port; `RowTitle.Text / New / Untitled`. |
| `presentation/ConversationsPorts.kt` | Ports `ConversationsFeed`, `ReturnBriefFeed`, `SettingsDoor`, with default bindings (`QuietConversationsFeed`, `NoReturnBrief`, `ClosedSettingsDoor`) so every app graph still builds; `ChatsBindings` provides `FeedDay`. |
| `presentation/ChatsState.kt` | Pure builder: phase (Opening / Empty / NoMatch / List), Today / Earlier by the person's day, needs-you and running counts, recent projects (4, the chosen one always present), query over cleaned title or project, brief block rows (feed rows of the brief's sessions, "N more"), incomplete sources. |
| `presentation/ChatsUi.kt`, `ChatsViewModel.kt` | `ChatsUi`, `ChatsActions` (intents: open, new, settings, search, running, needs-you, project, clear, dismiss brief, show all); the VM combines feed + brief + filters. The gear only acts when the door is open. |
| `ui/ChatsScreen.kt` | Kit parts only: `KitTopBar` with **New conversation** and **Settings** words (no bottom bar), search field under the bar, chips (needs you, Running, recent projects), brief rows + Continue, skeleton / calm empty / no-match states, Today / Earlier sections, rows with one status word and the grow key, "Show all conversations". |
| removed | `ConversationFixture` (the P1/P2 fake rows); `ChatPreviewViewModel` now shows the ref only. |

### `:feature:onboarding` (new module)

| File | What |
|---|---|
| `presentation/RootDecision.kt` | 07 §2.6 table: no active profile → Servers (welcome only when nothing is saved), move → ManagedMove, re-entry → Servers + Edit, else Conversations; `sameKind` for the RootViewModel's replace rule. |
| `presentation/FirstRun.kt` | Port of Dart `FirstRun` with the same keys and values (`oc.firstRun.state`, `oc.firstRun.notifyAsk`) over a `FirstRunPreferences` port. |
| `presentation/ServerAddress.kt` | Port of `normalizeServerProfileUrl` (+ `isLoopbackHost`), IPv6 bracketing included. |
| `presentation/OpeningGate.kt` | `OpeningWorld` port, `OpeningGateUi` (Waiting with a `TimeMark` for the kit's 8 s escalation, Failed with the confirm question, Resetting, ResetFailed, Open), `OpeningGateViewModel` (one clock per waiting phase; Start fresh only after the question). |
| `ui/OpeningGateScreen.kt` | `KitStateView` states with fixed words (Opening…, Can't read saved servers, Try again, Report a problem, Start fresh) and a destructive `KitConfirm` ("Remove saved sign-ins?"). |

## 2. Catalog rows covered (tests named after them)

| Row | Test |
|---|---|
| B-CHAT-013 | agents `ChatFeedTest` › temporary folders are never projects; chats `ChatsStateTest` › recent projects |
| B-CHAT-014 | agents `ChatFeedTest` › revision changes only on visible change; sub-agent and archived rows |
| B-STATE-029 | agents `MergedChatFeedTest` › a closed feed rejects late reads without closing shared sources |
| B-NAV-030 | agents `MergedChatFeedTest` › urgency order, equal ids from two sources stay apart |
| B-NAV-031 | agents `MergedChatFeedTest` › failed source isolated, no loading on refresh, recovers; chats `ChatsStateTest` › named |
| B-CHAT-093 | agents `ReturnBriefTest`; chats `ChatsStateTest` › brief block |
| B-CHAT-094 | agents `ReturnBriefTest`; chats `ChatsScreenTest` › Continue dismisses |
| B-CHAT-095 | agents `ReturnBriefTest` › unknown is not empty |
| B-STATE-082 | agents `ReturnBriefTest` › per profile and location, refused write, queued merge, corrupt entries |
| B-STATE-083 | agents `ReturnBriefTest` › profile deletion drains and clears |
| B-NAV-066, B-NAV-067 | chats `SessionTitlesTest` |
| B-NAV-037 | chats `ChatsScreenTest` › no bottom bar, Settings word; no dead gear without a door |
| B-NAV-024 | onboarding `RootAndFirstRunTest` › cold start with an active profile → Conversations |
| B-NAV-017 | onboarding `RootAndFirstRunTest` › only a device that started on the welcome is new |
| B-NAV-018 | onboarding `RootAndFirstRunTest` › bare addresses gain the right scheme |
| SCREENS-NAV-01 (A01) | onboarding `OpeningGateTest` (VM and screen by visible text) |
| SCREENS-NAV-02 (A03) | onboarding `RootAndFirstRunTest` › root rows in order |
| SCREENS-NAV-27 (B01) | chats `ChatsStateTest`, `ChatsViewModelTest`, `ChatsScreenTest` |

Not covered: B-NAV-032 and B-NAV-053 (new conversation: start source, `my-app` proposal — `ChatsRoute.New` page is not
built), B-NAV-065 (pins, S4), B-NAV-010 (conversations elsewhere), E2E-01 / E2E-21 (device flows).

## 3. Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk` (cmdline-tools
`commandlinetools-linux-16111833_latest.zip`; `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`).
Container-only (not committed): `~/.gradle/init.d/central-mirror.gradle.kts` puts `google()`, Google's Maven Central
mirror and the plugin portal in `pluginManagement`, the mirror in `dependencyResolutionManagement` (Central answers 429
through the proxy), and sets `robolectric.dependency.repo.url`; `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :feature:chats:desktopTest` (baseline, before any edit) | BUILD SUCCESSFUL, 1 test |
| 2 | `./gradlew :engine:agents:desktopTest :feature:chats:desktopTest :feature:onboarding:desktopTest` (final tree) | **BUILD SUCCESSFUL. 41 tests, 0 failures**: agents 15 (ChatFeed 3, MergedChatFeed 4, ReturnBrief 8), chats 17 (Titles 2, State 6, ViewModel 3, Screen 6), onboarding 9 (RootAndFirstRun 4, OpeningGate 5) |
| 3 | `./gradlew :engine:agents:compileAndroidMain :feature:chats:compileAndroidMain :feature:onboarding:compileAndroidMain` | BUILD SUCCESSFUL |
| 4 | `./gradlew :quality:test` (compiles `:desktopApp`'s Metro graph with my modules) | graph compiles after D3; **GraphSmokeTest 2/2 pass**, KitOnlyPlanted, ScopeFile, P3 pass. Red, see §8: K1 (my test files), K4 (`KitChatLocals.kt`, not mine), ModuleOwnersTest (rows), DesktopShellUiTest at its "Back" step (pre-existing) |
| 5 | `native/tool/check_commits.sh 7db496b2 HEAD` | OK, 5 commits |
| 6 | `native/tool/check_write_set.sh --globs '<write set>' --also docs/native/units/S2-CHATS.md` | 1 path outside: `native/engine/agents/build.gradle.kts` (D1) |
| 7 | `native/tool/scan_push.sh 7db496b2 HEAD` | clean (no private addresses, e-mails or keys) |

Tests were written before the code for each slice; the first red state was the compile failure. Fixes after the first
run: two screen tests matched a word twice (a chip and a row both say "Running" / the project name); the search field
reported its own empty start (now dropped); the brief VM test needed a subscriber for `WhileSubscribed`.

## 4. Decisions

| # | Decision | Why |
|---|---|---|
| A1 | The return brief model and store live in `engine/agents/**/feed/` | Plan 15 grants that folder "for the OpenCode-only ChatFeed and the return brief view model"; the store needs a per-profile `Mutex` and the day split needs kotlinx-datetime, both outside the K1 feature allowlist. |
| A2 | Features read the feed through ports with default bindings (`QuietConversationsFeed`, `NoReturnBrief`, `ClosedSettingsDoor`) | Every app graph must keep building; the connection owner replaces them with `replaces = [...]`. A closed Settings door hides the word: absent, never dead. |
| A3 | Settings and New conversation are top-bar words, not icons or a floating button | Kit has no gear glyph a feature may use; words keep the path visible. B-NAV-037 holds (no bottom bar). |
| A4 | The 8 s "Try again" uses the kit's `KitStateView(since, onSlow)` | One shared escalation rule (B-KIT-034); the VM only keeps one `TimeMark` per waiting phase. |
| A5 | The opening gate VM is a plain class (no Metro map entry) | The shell hosts it before the graph opens; an unbound `OpeningWorld` must not break graphs that include the module. |
| A6 | `ChatFeedItem.title` stays raw; cleaning happens in the list builder | One display rule (`SessionTitles`), stored title untouched (B-NAV-067). |

## 5. Deviations from the brief

- **D1 `native/engine/agents/build.gradle.kts` is outside the write set.** The `feed/` code needs a module; plan 17
  CR17-5 puts it in `:engine:agents`, which did not exist. The file is the minimal `oc.kmp.library` module (core-only,
  plus kotlinx-datetime). The agents engine owner takes it over in slice 3.
- **D2 Copy.** `:ui:words` is shared, so no keys were added. Existing keys are reused (`chatsHome*`, `bootstrap*`,
  `e7LocaleUiSettings` for "Settings", `kitTopBarSearch`, `notifStatusNeedsYou`, `returnBriefContinue`,
  `workspaceNewSession`, `chatsHomeShowAll`). "Updating your app…" is stood in by `openCode2MoveProgress`; the brief
  heading, "and N more" and the "Can't tell what's new" line are **not shown** until their keys exist (§6).
- **D3 `FeedDay` is bound from `:feature:chats`** (`ChatsBindings`), not by `@ContributesBinding` in the engine:
  `:desktopApp` sees only its direct modules, and the first `:quality:test` run failed with Metro `MissingBinding`.
- **D4 B-NAV-018 test data** uses `box.example.net` instead of Flutter's `*.ts.net` example (the push scan blocks
  tailnet names).
- Not built from the finish line: Inbox (B05), All conversations (B12), New conversation page (B07), project and
  connection sheets (B03/B04), the welcome screen itself (it is `ServersRoute.Servers`, S2-SERVERS), ManagedMove (A04).

## 6. Catalog and words requests

- **Catalog:** none (kotlinx-datetime already has an alias).
- **`:ui:words` (l10n owner), en + ar:** `chatsReturnBriefTitle` (plural: "N conversations finished while you were
  away"), `chatsReturnBriefMore` ("and %1$d more"), `chatsReturnBriefUnknown` ("Can't tell what's new on this
  server"), `bootstrapUpdatingTitle` ("Updating your app…"), `chatsHomeSettings` ("Settings"),
  `chatsHomeRecentProjects` (heading, if the design wants one).

## 7. Requests to other owners

- **Coordinator, `MODULE_OWNERS`:** rows `:engine:agents` (agents owner; S2-CHATS holds `feed/` until slice 3) and
  `:feature:onboarding` (S2-CHATS). The file already lacks other units' rows too (entry, composer, profiles, runtime,
  settings, diagnostics, `:android:platform`).
- **Coordinator, `:quality` K1:** the rule scans `commonTest` files, so any feature screen test (Compose UI test,
  coroutines test) fails it. Suggest filtering `scope("feature")` with the `production` slice for K1.
- **Coordinator, `DesktopShellUiTest`:** the kit's top-bar Back is now a labelled glyph; find it with
  `hasContentDescription("Back")` instead of text (the step fails without my change too, after "Show all
  conversations").
- **Routes owner (`:ui:routes`):** a Settings key (`SettingsRoute.Home`) so a `SettingsDoor` can be bound; `ChatsRoute`
  gains `Inbox`, `AllConversations(profileId, archived)` and `NewConversation(profileId?, projectDir?)` per 07 §2.2;
  `OnboardingRoute.ManagedMove`; `ConversationRef.backend` (07 §2.2.1).
- **Shell (coordinator):** host `OpeningGateScreen` before the graph with an `OpeningWorld` over `AppBootstrap.phase`
  + `GateSignal.open` (Opening while Starting, Updating while the MigrationGate is closed, Open on Ready, SafeMode →
  A02), and the `RootViewModel` over `RootDecision` (`replaceRoot` only when `sameKind` is false).
  `:ui:shell` may not depend on features, so the app module (or a slot interface in `:ui:routes`) has to pass it in.
- **Connection owner:** bind `ConversationsFeed` with one `OpenCodeChatFeedSource` per live graph (`OpenCodeListing`
  from the session map, `sessionStatuses`, pending requests and read watermarks), and `ReturnBriefFeed` over
  `ReturnBriefStore` + `BriefPreferences` (prefs store) with the location key `[baseUrl, directory, workspace]`.
- **Profiles owner:** register `oc.returnBrief.<profileId>` with `ProfileScope`, and call
  `ReturnBriefStore.deleteProfile` from profile deletion (B-STATE-083); `FirstRunPreferences` over the prefs store.

## 8. Open problems

1. `:quality:test` red: K1 on my test files (§7), K4 on `KitChatLocals.kt` (kit-chat), ModuleOwnersTest (rows),
   DesktopShellUiTest "Back" (kit change). GraphSmokeTest passes with the new `ChatsViewModel` graph.
2. The list shows "No conversations yet" in the app until the connection owner binds `ConversationsFeed`.
3. The gate is not hosted; the Settings word is hidden until a Settings route exists.
4. Brief words missing (D2): the unknown state currently draws nothing, which looks like an empty brief.
5. Late-row entrance motion (fade + rise 8 dp), "Opening…" on reopening agent rows and the request cards inside rows
   (B02, slice 3) are not done.
6. No preview-build or emulator run; E2E-01 and E2E-21 are unverified.
