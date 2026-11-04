RESULT: APPROVED WITH FIXES

# Final approval — job 50-blocked-5-NAV-STATE-BUILD (second pass: NAV, STATE, BUILD)

Branch `native/p12x/job-50-blocked-5-NAV-STATE-BUILD`. Job commits reviewed: 935f6970 (chat), 43ade65e (settings),
60f1ecf3 (navigation), 0d55df5b (build). Report: `docs/native/parity/migration/50-blocked-5-NAV-STATE-BUILD.md`
(corrected in 8173f676). Approval fixes: c9742d07. Nothing was compiled and no tests, Gradle tasks or APK builds
ran here (owner: coding first, testing afterwards; Google Maven is blocked in this environment). The build VPS
compiles after this approval.

## Step 1 — merge of the P12 integration head (967e14ce)

`git merge --no-ff origin/native/p12-head`: five files conflicted. Each was resolved keeping both sides:

- `RemoteTerminalViewModel.kt`: P12's `renderers` and `platform` parameters, plus the job's `devServers` as the
  last parameter (it has a default value). `NoTerminalDevServerHost` is `@ContributesBinding(AppScope)`, and
  Android's `LiveTerminalDevServerHost` replaces it, so Metro injects the live host.
- `LocalTerminalViewModel.kt`: P12's `registry` and the server-source flow, plus the job's `devServers`.
- `PlansScreen.kt`: P12's per-card widget switch, plus the job's SignInExpired notice and list-phase gate.
- `SettingsScreen.kt` and `LocalTerminalScreen.kt`: the import lists are unions.

The `build.gradle.kts` dependency lists (androidApp, feature/agents, feature/terminal) auto-merged as unions.

The merge also created a duplicate string key. P12 added `desktopReleaseAvailable` in `strings_about.xml`, and the
job added the same key in `strings_updates.xml`. The job's key is renamed to `desktopUpdatesBuildAvailable` in
English and Arabic, together with its single use in `DesktopUpdatesScreen.kt`.

Older duplicate, not from this job: eight `linuxSvc*` keys are defined in both `strings_phone.xml` and
`strings_linux_services.xml` (in English and Arabic), and already were on `native/p12-head`. `linuxSvcTitle` even has
two different values. If Compose resource generation refuses duplicate keys, the coordinator has to fix this on the
integration head.

## Findings and fixes

### Blocker (fixed)
1. **Online sends filled the command journal for good** (`PromptDelivery.kt`, 935f6970). The job removed the
   `bubble.reviewId != null` gate, so every send to a receipt-capable server (all OC2 servers) claimed a permanent
   `send:<id>` entry in `PendingCommandJournal`. That journal is capped at 1000 entries and is never pruned, so after
   1000 messages on one server every send would fail with DispatchUncertain and nothing could recover it. Flutter
   journals only queued and held prompts and session creation (`lib/state/connection/queue.dart:843`).
   **Fix:** restored the queued-only gate and removed the now-dead direct `send:` path from Check again.
   B-STATE-047…053 are now recorded as *already there*: the journal rules already applied to queued sends.

### Major (fixed)
2. **A saved model default could stop a new project chat from opening** (`ProjectPageViewModel.kt`,
   `ProjectSignalsWiring.kt`). If the provider list failed, the server could not switch models, or the model write
   failed, the project page showed a create failure, even though the chat already existed on the server.
   **Fix:** the chat always opens and the default is best-effort. A missing catalogue or a missing switching
   capability skips the default.
3. **Running chats lost their "Running" label where pins are unsupported** (`ChatsAllScreen.kt`).
   **Fix:** Running now always goes into the supporting line. The unused pin locals were removed.
4. **Settings search and deep links stopped one level short** (`SettingsPresenter.onTarget`). A result opened the
   group page only. Flutter opens the destination itself.
   **Fix:** a page or outside row now opens its destination after the group. A switch row stays highlighted in its
   group.
5. **Tools could show the previous model's tools** (`ToolsPresentation.selectModel`) when the new model's read
   failed. **Fix:** changing the model now clears the list of tools.

### Major (recorded in the report, not code-fixed)
6. **B-STATE-108/109/110 are groundwork.** The binding store and its deletion sweep are real, use the right
   `oc.sessionLinkBinding.<id>` key and match Flutter, but nothing in the app reads or writes a binding yet,
   because address handoff is unavailable. They are re-labelled *groundwork (storage built, no producer)*.
7. **B-NAV-046:** the queued-prompt move is offered only from the source chat. Flutter's destination-side
   "move N here" entry (status line and Servers row) is not ported. Rejected sub-finding: moving a prompt stores
   its text redacted, and this matches Flutter (`lib/state/queued_prompt_move.dart:112`). It is not a regression.
8. **B-NAV-043:** the project-page dev-server row goes beyond Flutter, which shows the signal only in the
   Terminal header. That row reads once per load and opens Development services. The two terminal headers match
   Flutter.
9. **B-BUILD-003:** the desktop half is not built. There is no `CapabilityFlows` binding on desktop and no
   `:feature:capabilities` module in desktopApp. **B-BUILD-011:** the desktop update check is manual only. Flutter's
   check on start and on resume, and its status-bar notice, are not ported.

### Minor
- Fixed: a desktop update check that fails now clears the earlier release, so Open on GitHub no longer appears
  next to the failure.
- Fixed: the "A2A external agents" capability copy (rule 15, plain words) was rewritten in English and Arabic.
- Fixed: the project page's initial workspace opens once, instead of reopening on every server re-adoption.
- Recorded in the report:
  - B-NAV-029 became reachable only in 0d55df5b.
  - The keyboard-shortcuts search result is not on desktop yet, because desktopApp does not include
    `:feature:search`.
  - The Tools screen lists every model as a row, where Flutter shows one picker header.
- Open, for a follow-up:
  - Settings `focusedRow` and `focusedAnchor` are never cleared.
  - Two new phrase-chip keys (`nativePhraseChipsTitle`/`Description`) duplicate the existing
    `settingsPhraseChips*` keys. Flutter uses an inline switch with "Show hidden phrases"; native uses a page.
  - The Notices page renders raw Markdown, and its text still lists the Flutter dependencies.
  - Tool parameter types show untranslated JSON Schema words.
  - On desktop, the home Allow and Reject buttons are shown without an answer port.
  - The desktop release body is buffered in full before the 2 MB cap is checked. The URL is pinned to
    api.github.com, so the impact is low.
  - Project-scoped search results use the latest chat's folder when the server has no current location.
  - The `@Provides KitChatEnvironment` on the desktop graph is redundant.

## Checks that passed
- **Security:**
  - Preview egress still goes through DevPreviewTarget/PreviewEgress, and is revoked when its profile changes or is
    deleted.
  - The desktop release page opens only a revalidated https URL on github.com for this repository, after a kit
    confirmation and through ExternalLinkPolicy → ExternalLinkOpener.
  - No credentials are logged.
  - New per-profile keys follow `oc.<what>.<profileId>` and are swept on profile deletion.
- **Kit-only UI:** the new screens (Capabilities, Desktop updates, Model defaults, Queue move, Managed move,
  Tools) use only kit parts. `KitRunningFeedLine` and `KitDesktopSettingsMenu` were added to the kit.
- **Copy:** every new key is in both `values/` and `values-ar/`. There are no hardcoded user strings.
- **Reachability and DI:**
  - Capabilities is reachable from Settings › Help.
  - Model defaults is reachable from Settings › Model and from search.
  - Desktop updates is reachable from the desktop OS menu.
  - Entries, ViewModels and wiring are contributed into the real graphs.
  - The `when`s over SettingsPage, SettingsOutside, SearchPlace and the capability flows are exhaustive after the
    merge.
- **Rows verified OK:**
  - **Built:** B-NAV-001, 016, 019, 023, 027, 033, 035, 036, 042, 050, 065, 070, 076, 079; B-STATE-001;
    B-BUILD-012.
  - **Already there:** B-NAV-074, 080.
  - **Blockers that are genuine:**
    - B-NAV-015 and B-NAV-052: plan/01 D6.
    - B-BUILD-003, Android half: D6/D7.
    - B-BUILD-004/005/006: owner-deferred test-ledger rows, the same as batch 31.
