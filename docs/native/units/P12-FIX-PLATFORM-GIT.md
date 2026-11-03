VERIFIED: green

# P12-FIX-PLATFORM-GIT — Codex second-eye findings for PLATFORM and GIT

Branch `claude/native-parity-p12-prdv7d`, base `native/p12-qa-wave4-2026-11-03` (`cbad87e1`). Sources:
`docs/native/reviews/p12/platform.md`, `docs/native/reviews/p12/git.md`. Each finding was checked against the native
code and the Flutter source (`lib/`). A test was written for each fix; the tests marked **red-checked** were run
against the old code and failed there.

## GIT (review: 0 BLOCKER, 5 MAJOR, 4 MINOR)

| # | Finding | Verdict | Fix and test |
|---|---|---|---|
| G1 | Rename commits leave out the old path | **Real.** Worse than the review said: just passing the old path to `git add` fails (`pathspec 'old.kt' did not match`) because the old path is already gone from the index and the disk. Flutter has the same latent bug. | `GitRepository.commit(paths, message, renamedFrom)` stages only the ticked paths and commits `paths + renamedFrom`. `GitPage.commit/commitAndPush` pass it through; `GitPresenter` sends the `originalPath` of every ticked rename. Tests: `RealGitTest` `B-BUILD-017 a rename saved with its old path leaves nothing staged and only the new file` (real `git mv`, real git), `GitPresenterTest` `B-BUILD-019 a ticked rename saves its old path too`. |
| G2 | A failed save clears the unticked files | **Real.** | Ticks are cleared only when that save worked (`generation` moved and `result.ok`, or saved-not-sent). Test: `B-BUILD-019 a failed save keeps the files left out` (hook failure, then Save and send: `notes.md` never reaches a commit). |
| G3 | GitHub approval after profile deletion writes a token | **Real.** | `GitHubAccount.forget()` bumps the attempt and `cancelAndJoin`s the sign-in job; `GitServices.forgetRemovedProfiles` forgets removed accounts **before** the sweep. Test: `GitHubTest` `B-SEC-085 deleting a profile while its GitHub sign-in waits stores no token afterwards` (**red-checked**). |
| G4 | Leaving Git mid-command skips the server DELETE | **Real.** | The `finally` DELETE runs in `withContext(NonCancellable)`. It is bounded by the client's connect/inactivity timeouts. A `withTimeoutOrNull` was tried and dropped: under virtual time it fired before the real request finished. Test: `ServerGitShellTest` `B-BUILD-013 leaving the page mid-command still drops the server's copy` (**red-checked**). |
| G5 | No Add remote or Clone | **Real.** | Engine: `isValidGitRemoteName`, `isValidGitFolderName`, `gitRemoteAddressProblem` (refuses an address with a password before anything runs), `suggestGitCloneFolder`, `gitParentFolder`, `gitCloneTarget`, `GitRepository.addRemote/clone/fetch`, `GitPage.addRemote/clone` (`GitBusy.AddRemote/Clone`). UI: visible **Remotes** and **Clone a repository** rows under Branch/Get latest/Send. The Remotes sheet lists the remotes and has name and address fields plus Add remote, with field errors. The Clone sheet has address and folder fields (the folder name defaults to one taken from the address) plus "Clones into …". All words already existed in `:ui:words` (EN and AR). Tests: `RealGitTest` `B-BUILD-014 add remote, clone beside the project and a quiet fetch` (real git: password refused, bad name refused, remote added, `RemoteExists`, clone, `FolderExists`, fetch makes "behind" true, `NoUpstream`); `GitPresenterTest` `B-BUILD-014 Remotes adds a remote and Clone copies a repository beside the project`; `GitScreenTest` `B-BUILD-014 a project without a remote reaches Remotes and Clone from visible rows` and `… refuses an address with a password before anything runs`. |
| G6 | Wiring turns `remoteShell = false` into "unknown" | **Real, not fixed here (outside the write set).** `Oc2Gateway.kt:920` hardcodes `remoteShell = false` for **every** OC2 server; Flutter's `gateway_mappers.dart:58` says `true`. If the wiring honoured `false`, Git would be unavailable everywhere. Today an OC2 server that really has no shell still ends at "This server can't run Git" through the 404 → `Unsupported` path. | **Integrator:** the protocol cluster sets `remoteShell = true` in `Oc2Gateway` (Paseo keeps `false`). Then `GitWiring.kt:144` passes `advertised` straight through (`remoteShell = advertised`). |
| G7 | "Up to date" never fetches | **Real.** | `GitPage.checkForUpdates()` (Flutter `checkForUpdates`): a quiet `git fetch --quiet` after the first read, then a re-read. It holds no button, has a `GitCheck` state, and the Get latest row reads "Checking…" or "Couldn't check, tap to try". `GitPresenter.start` runs it after `refresh`. Test: `B-BUILD-014 opening looks at the remote quietly …`. |
| G8 | GitHub sign-in offered on server workspaces | **Real.** | `GitUi.offersGitHubSignIn` is false for every `GitHost.Server` workspace (`GitWorkspace.usesOwnGitSignIn(host)`), so the section is hidden. The "uses the Git sign-in set up on …" line stays. Tests: `GitScreenTest` `B-SEC-082 a server workspace pushes with its own sign-in, so no GitHub sign-in is offered`; the presenter test asserts `offersGitHubSignIn == false`. The existing B-SEC-084 screen tests now pass `offersGitHubSignIn = true`. |
| G9 | "Runs nothing" tests inspect disconnected fakes | **Real.** | Both tests now hand the fake shell to the real `resolveGitWorkspace` (not connected, and no project). A command run on an unavailable workspace would now show up in the fake's scripts. |

## PLATFORM (review: 0 BLOCKER, 10 MAJOR, 2 MINOR)

All app-side decisions now live in a pure `PlatformRules` / `ShareSeed` in `PlatformWiring.kt`. They are tested in
`androidApp/src/test/.../PlatformRulesTest.kt` (JVM, no Android).

| # | Finding | Verdict | Fix and test |
|---|---|---|---|
| P1 | Widget text not redacted or bounded | **Real.** | `PlatformRules.widgetText` = `NotificationText.redact(…, 80)` (Flutter `safeWidgetTitle`), applied to the title, project, row server and server list. Test: `B-SEC-181 widget titles, projects and server names are masked and bounded`. |
| P2 | Widget tap opens (active profile, other server's session) | **Real.** | `SessionWidgetIntents.rowProfile` uses the row's own `profileID` for the URI and the extras. Another server's row also gets no `requestID`, so no inline Allow/Reject can answer on the wrong connection (Flutter: "no request identity crosses from another connection"). It still shows needs-you. Tests: `TwoWidgetsRenderTest` `B-ANDR-116 a row of another server opens that server's conversation` (Robolectric); `PlatformRulesTest` `B-ANDR-116 each widget row names its own server …`. |
| P3 | Share text lands in an unrelated chat or is lost | **Real.** | `ShareSeed`: the words go only to a chat opened straight from **New conversation** that did not exist when the share arrived. They are cleared only after `flush()` returns Ok. A timeout or failed save keeps them for the next new conversation. An existing draft is kept and the share is added below it. Test: `B-ANDR-075 shared words go only to the new conversation made for them and wait until saved`. |
| P4 | Notification switches ignored | **Real.** | `oc.notify.requests` gates permission and question alerts; `oc.notify.finishedRuns` gates finished and stopped alerts. Both are read live from prefs. These are the keys Settings writes and migration keeps. Test: `B-MIGR-005 the Settings switches decide each kind of alert`. |
| P5 | Two Update taps download and install twice | **Real.** | `AppUpdates.admit(phase)`: a single CAS on the state takes the lane, used by `updateNow` and `check`. Test: `AppUpdatesTest` `B-MIGR-012 two quick Update taps download and install once` (**red-checked**). |
| P6 | Failed run posts "finished" | **Real.** | `PlatformRules.endedAlertKind`: Running → Failed gives `AlertKind.Error`, which the shade shows as the stopped notification with its reason sentence. Running → Idle gives `Complete`. Test: `B-ANDR-011 a failed run gets the stopped alert …`. |
| P7 | Alerts stay after returning to the app | **Real.** | When the app comes to the foreground (`cameToForeground`, checked in the feed pass, so on one coroutine), every request alert and finished/stopped alert this process posted is dismissed. Request ids stay remembered, so the same request is not posted again. Test: `B-ANDR-040 coming back to the app clears outstanding alerts once`. Limit: alerts posted by an earlier process are not tracked, and `AlertShade` has no clear-all. |
| P8 | Pinned-session shortcuts never published | **Real.** Native has no pin feature yet; pins exist only as migrated Flutter `oc.sessionPins.<profile>`. | `publishPins`: the active profile's pins come from `oc.sessionPins.<id>` (every folder scope, in order), titles from the feed (redacted), and go through `PinnedShortcuts.publish`. It runs only when the list changes. Test: `B-ANDR-030 saved pins are read in order from every folder scope`. **Remaining:** pin/unpin in the native Conversations list belongs to the feed/chats owner. |
| P9 | Updates unreachable without a waiting release | **Real.** | `AppUpdateSettings` (in PlatformWiring) puts the real `AppUpdates` behind Settings' `UpdateSettings` port: state, Check now, the automatic-check switch, and Update/Cancel/Install. `SettingsWiring.kt` gets a 3-line edit: inject it, `inAppUpdater = true`, `updates = updateSettings`. **Settings › Updates** is now always there. Test: `B-MIGR-012 Settings Updates shows the real updater, with nothing waiting too`. |
| P10 | Release URL bypasses `ExternalLinkOpener` | **Half real.** Bypassing the opener: **real, fixed**. `ReleasePageLinks` now opens through the app's `ExternalLinkOpener` (browsable-only, SecurityException handled). "Bypasses confirmation": **not a fix needed**. The URL must start with this project's release prefix and pass `ExternalLinkPolicy`; `AskFirst` is accepted only when the shown host is `github.com`, and the tapped button says "Open on GitHub" (the same rule as Git's "Open GitHub"). | Test: `B-SEC-065 only this project's GitHub release pages open, through the policy`. |
| P11 | Finished-reply excerpt always empty | **Real.** `OpenCodeChatFeedSource.row()` (engine:agents, not this unit) never fills `preview`. | Fixed on the platform side: a Complete alert with no preview reads the last 6 messages (`messagePage`, 5 s cap) and posts `firstReplyLine` of the assistant text without synthetic parts. Test: `B-ANDR-048 the finished alert reads the last reply's text …`. |
| P12 | Unchanged widget content rewritten | **Real.** | The widget content is a `WidgetModel` with no clock stamps. An equal model is not published. `updatedAt`/`lastSeenAt` move only with content, and a server's `since` only when its online state changes. Test: `B-ANDR-117 the same content gives an equal model …`. |

## How to reach each screen

- Git: chat ⋯ → Git (as before). The new **Remotes** and **Clone a repository** rows sit under Send and open their sheets.
- Updates: Settings → **Updates** (now always shown); the Conversations Update button still opens the full Updates page while a release waits.
- Widgets, shortcuts, notifications and share have no new screens; their behaviour changed as described above.

## Tests and results

One Gradle run (`--continue`, JDK 21, Android SDK platforms 37.1/36, build-tools 37.0.0), every task passed:

```bash
./gradlew :engine:git:desktopTest :feature:git:desktopTest :engine:updates:desktopTest :feature:updates:desktopTest \
  :feature:settings:desktopTest :android:widgets:testDebugUnitTest :android:tiles:testDebugUnitTest \
  :android:notifications:testDebugUnitTest :androidApp:testPreviewDebugUnitTest :quality:test :androidApp:assemblePreviewDebug
```

| Task | Result |
|---|---|
| `:engine:git:desktopTest` (incl. real-git `RealGitTest`) | 53/53 |
| `:feature:git:desktopTest` (presenter + Compose screen) | 20/20 |
| `:engine:updates:desktopTest` | 22/22 |
| `:feature:updates:desktopTest` | 28/28 |
| `:feature:settings:desktopTest` | 66/66 |
| `:android:widgets:testDebugUnitTest` (Robolectric) | 173/173 |
| `:android:tiles:testDebugUnitTest` | 27/27 |
| `:android:notifications:testDebugUnitTest` | 82/82 |
| `:androidApp:testPreviewDebugUnitTest` (incl. `PlatformRulesTest` 11) | 26/26 |
| `:quality:test` | 59/59 |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL (`androidApp-preview-debug.apk`, 37 MB) |

Red checks: with the old `ServerGitShell.kt`/`GitServices.kt`, `:engine:git:desktopTest` failed exactly the two new tests (G3, G4).
With the old `AppUpdates.kt`, `:engine:updates:desktopTest` failed the new double-tap test (P5). No device or emulator run.

## Deviations and integrator notes

- G6 needs the protocol-cluster change described above.
- P8: native pin/unpin UI does not exist yet (feed/chats owner); shortcuts follow migrated pins until then.
- P7: only alerts posted by the current process are cleared.
- Wi-Fi-only alerts are still not applied: no network fact is wired into `AlertFacts`. The review did not raise this.
- `SettingsWiring.kt` (settings unit) has a 3-line edit for P9. `SessionWidgetIntents.kt` and
  `TwoWidgetsRenderTest.kt` (widgets) are touched for P2.
- Build environment only (outside the repo): `~/.gradle/init.d/mirror.gradle.kts` puts Google's Maven Central mirror and Google first, because Maven Central returned HTTP 429.
