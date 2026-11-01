# S3-TERMMIN — the minimal sign-in terminal

Date: 2026-11-01. Branch: `native/termmin-2026-11-01`, created from `claude/native-termmin-signin-jc1eqf` at `7db496b2`
(the M0 tip after INTEGRATE-W1), with `native/kccontent-2026-11-01` merged in (S3-KCCONTENT, the `:ui:kit-content`
module this unit's write set lives in; it is not in M0 yet). Write set: terminal files of `native/ui/kit-content/**`
(new files only) and sign-in files of `native/feature/agents/**` (new), plus this report.

Inputs: `native/AGENTS.md`, `docs/native/slice0/*.md`, `docs/native/units/*.md`, plan `01`, `15` (S3-TERMMIN row,
C15-2), `03` (`PlatformViewHandle`), `06` §7.7 (terminal accessible mode), `07` G03, `11` W11-08a and §9.1, `17` §1.5
and §2.6/§2.9, `map/builtin-linux.md`, the catalog rows, and the Flutter sources they port
(`lib/ui/screens/terminal/terminal_accessible.dart`, `test/terminal_accessibility_test.dart`,
`test/local_terminal_signin_foreground_test.dart`, `test/phone_agent_sign_in_native_test.dart`,
`android/.../LocalTerminal.kt`).

## Result

**Implemented and verified on desktop (JVM) and Robolectric; compiled for Android. Not wired into an app, not
enabled, not run on a device.** The plan 15 finish line is **not** fully met: the parts this write set can hold are done
and green, but a Termux `TerminalView` over a real PTY cannot exist in this tree yet (see "Open problems" 1–3), so
E2E-31 was not run, with or without TalkBack.

| Finish-line piece | State |
|---|---|
| `TerminalAccessibleModeTest` green | **done** — 10 Robolectric tests (`:ui:kit-content:testAndroidHostTest`) |
| Accessible mode: transcript, "Send to terminal" field, grid excluded from semantics, auto-on under TalkBack | **done** in the kit (`KitTerminalView`), used by the sign-in screen |
| Live terminal behind the `PlatformViewHandle` seam | **done** in the kit: `KitTerminalLive` + `AndroidKitTerminalSlot` host any `android.view.View` the terminal host hands over |
| Sign-in screens in `feature/agents` | **done**: `SignInTerminalScreen` (G03), `SignInTerminalPresenter`, `SignInTerminalViewModel`, `SignInTerminalSession` |
| Claude's own `claude auth login --claudeai` in a Termux `TerminalView` over the copied PTY | **blocked**: no PTY and no Termux library in the tree (open problems 1–2); the session drives a `TerminalHost` port |
| E2E-31 passes, also with TalkBack | **not run** (needs the host, the route and an emulator with built-in Linux) |

## What was built

### `:ui:kit-content` (package `io.github.eslamasabry.oc.ui.kit.content.terminal`)

| File | What |
|---|---|
| `commonMain/.../TerminalTranscript.kt` | Port of Dart `_TerminalTranscriptSanitizer`: stateful across chunks; removes CSI, OSC (BEL/ST), DCS/SOS/PM/APC strings, charset designations, C0/C1 controls; CR LF and lone CR → one `\n`; drops NUL-prefixed transport chunks; keeps the newest **100,000** characters. `forDisplay`, `lines`, `sendLine` (`value + "\r"`). |
| `commonMain/.../KitTerminalSlot.kt` | `KitTerminalSlot` + `LocalKitTerminalSlot` (the platform's live-view slot, null on desktop/iOS) and `KitTerminalLive`: the grid box is `clearAndSetSemantics { contentDescription = "Terminal"; stateDescription = title }`. |
| `commonMain/.../KitTerminalView.kt` | `KitTerminalView` (`.live` + `.accessible`): visible switch tagged `terminal-accessible-mode`; `accessibleMode: Boolean?` (null follows `KitSystemSignals.accessibleNavigation` = TalkBack/Switch Access; the person can turn it off); an `above` slot shown in both modes. `KitTerminalAccessible`: selectable list, one merged node per line, newest three lines as a polite live region throttled to one announcement per second, mono `KitField` + Send that writes value + CR and clears; disabled input says why. `KitTerminalWords` (labels come from the screen). `KitTerminalRules` (pure). |
| `androidMain/.../AndroidKitTerminalSlot.kt` | Hosts the handle's `View` in `AndroidView`, detaches it from an old parent (sessions outlive screens) and sets `IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS`. The only place the native value is cast (ADR §7.3 item 3). |

### `:feature:agents` (new module; sign-in files only)

| File | What |
|---|---|
| `build.gradle.kts` | `oc.kmp.feature` + `:ui:kit-content`. |
| `signin/SignInPorts.kt` | Narrow ports: `TerminalHost`/`TerminalTarget.SignIn`/`TerminalModel`/`TerminalEvent` (plan 17 §2.6 shape + W11-08a events), `SignInCommand` (mirror of `GuestCommand`), `SignInAgents` (plan, command, confirm, output read), `SignInForeground`/`SignInForegroundLease`/`SignInAdmission` (11 §9.1 `SignIn`), `SignInLinks` (ExternalLinkOpener), `SignInClipboard` (sensitive copy). |
| `signin/SignInTerminalSession.kt` | Port of `LocalTerminalSessions.startSignIn/endSignIn`: reserve at once; start the PTY only after an active admission; fixed failure codes (`sign-in-admission/<code>`, `sign-in-lost/<reason>`, `sign-in-start`), never CLI text; `end()` drains the exact (also late) session, releases only after removal, shares concurrent drains, keeps the claim on a failed drain and retries the same session; exit holds the claim until `end()`; loss drains only the sign-in; every URL handoff rechecks protection; unknown loss reasons are never echoed. |
| `presentation/SignInTerminalUi.kt`, `SignInTerminalPresenter.kt`, `SignInTerminalViewModel.kt` | The screen state and actions; plan → command → session; transcript → `SignInAgents.read` → "Open sign-in page" / "Copy code"; `OpenUrl` events and the button go only through `SignInLinks`; on exit: account read with the claim held, then release; Done / Sign in again / Back (drain first, stay when it failed). The ViewModel is a thin `viewModelScope` wrapper. |
| `ui/SignInTerminalScreen.kt` | G03 from kit parts and `:ui:words` strings: `KitScreen`, `KitTopBar`, `KitTerminalView` with the two buttons above it, `KitStateView` per step, `KitDetailsFold` for the failure code. |

## Catalog rows → tests

All tests are named after their row (rule 11). Rows without a `B-` id use the map feature id.

| Row | Test |
|---|---|
| DESKTOP-WEB-CROSS-30 (`terminal_accessibility_test`, kit half; plan 17 §2.9) | `TerminalAccessibleModeTest` (Robolectric, 10): one "Terminal" node and no grid text in the tree, view hidden from accessibility; off without / on with a screen reader; the person can override both ways; stripped + capped transcript; empty transcript words; Send and the IME action write value + CR and clear; ended terminal disables input and says why; no live view → accessible only; newest three lines as polite live region. `TerminalTranscriptTest` (7) + `KitTerminalRulesTest` (2) in commonTest (desktop + Android host). |
| B-LINUX-094 | `SignInTerminalSessionTest`: reserves at once and waits for active foreground; notifications blocked; admission refusal code; inactive admission never launches. Presenter: blocked notifications never start a terminal. |
| B-LINUX-095 | Session: pending start drained exactly before release; disposal of a pending start never reopens the URL; cancel during admission; duplicate ends share one drain; failed drain keeps protection, exact retry. Presenter: leaving drains first, stays on failure. |
| B-LINUX-096 | Session: exit holds the claim until explicit end; loss drains only the sign-in (another terminal untouched); unknown loss reason not echoed; URL handoff rechecks protection. Presenter: account read while protected, then released; Done drains then reads. |
| B-SEC-098 | Session and presenter: a failed native start shows `sign-in-start` only; the CLI text is in neither state nor `toString`. |
| B-AGENT-129 | Session and presenter: the host receives exactly `TerminalTarget.SignIn(claude auth login --claudeai)`, once. (Plan 17 §1's row table marks B-AGENT-126…129 retired as Termux-hosted Claude Code, NG8; the "visible terminal runs only the script" clause is what is tested.) |
| B-LINUX-125 (screen half) | Presenter: Claude and Codex each open their own sign-in terminal with their own command. The allowlist and `CODEX_HOME` halves belong to the host (`:android:linux`/`:android:agenthost`). |
| AGENTS-BACKENDS-13/14 | Presenter: page and code become Open sign-in page / Copy code (link port, sensitive copy); `OpenUrl` only through the link port; exit code 0 is not proof; Start again reopens; API-key agents show the provider-key state and reserve nothing; non-phone agents are unavailable; page, code and grid are dropped once the sign-in ended. |

Not covered here: B-AGENT-026 (`SignInOutputReaderTest` is `:engine:agents`'), B-LINUX-097…101 (local shells, S5),
B-LINUX-119 (agent by name in the Terminal, host + E2E).

## Commands and results

From `native/`, `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=/opt/android-sdk`
(`commandlinetools-linux-16111833_latest.zip`; `platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`; JDK 17
toolchain via foojay). Container-only setup, not committed: `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers;
`~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Maven Central mirror first (Central answers 429 through the
proxy) and sets `robolectric.dependency.repo.url` on every `Test` task.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:agents:desktopTest …` (with `native/agentse-2026-11-01` merged) | FAILED to compile: `:engine:agents` uses `Failure.Net.*` and `StorageProblem` (pre-INTEGRATE-W1 contract). Merge dropped; ports instead (deviation 2). |
| 2 | `./gradlew :ui:kit-content:compileKotlinDesktop :ui:kit-content:compileAndroidMain` (kccontent merged) | BUILD SUCCESSFUL |
| 3 | `./gradlew :ui:kit-content:desktopTest :ui:kit-content:testAndroidHostTest --tests '*Terminal*'` | first: compile failure (`PlatformViewHandle` not visible to kit-content → deviation 3); then 1 failure (disabled reason is a state description, not text; test fixed); then green |
| 4 | `./gradlew :feature:agents:desktopTest` | session 15/15 green on the first run after the implementation |
| 5 | `./gradlew :feature:agents:desktopTest :feature:agents:compileAndroidMain` | presenter 15/15, both targets compile |
| 6 | **Final, committed tree:** `./gradlew :ui:kit-content:desktopTest :ui:kit-content:testAndroidHostTest :feature:agents:desktopTest :feature:agents:compileAndroidMain` | **BUILD SUCCESSFUL.** kit-content desktop 48/0 failures, Android host 65/0 (incl. S3-KCCONTENT's goldens and QR readability, unchanged), feature/agents desktop 30/0 |
| 7 | Konsist K1/K2/K3/K4/K5/K10/K11/K16/K18/K19 re-implemented as a script over the new files (`:quality:test` compiles the whole tree, outside this unit) | 0 violations |
| 8 | `tool/check_commits.sh 27f6a2038 HEAD` | OK, 3 commits |
| 9 | `tool/scan_push.sh` | see the end of this report |

Tests were written before the code they test; the first red state was the compile failure (no implementation yet),
not a run with failing assertions.

## Deviations

1. **`native/kccontent-2026-11-01` is merged into this branch.** `:ui:kit-content` does not exist on M0; without it the
   write set has no module. The merge brings S3-KCCONTENT's commits unchanged, so merging both branches into M0 needs
   no conflict resolution.
2. **No dependency on `:engine:agents`.** Its branch does not compile on the flat core contract (command 1), and the
   brief says to depend only on modules that compile. `signin/SignInPorts.kt` mirrors exactly what the screen needs
   (`AgentSignIn.plan/command/finish`, `SignInOutputReader.read`, `GuestCommand`); one adapter above both binds them.
3. **Kit-content takes `PlatformViewHandle.native` (`Any`), not `PlatformViewHandle`.** `:ui:kit-content` does not
   depend on `:core:common`, and its `build.gradle.kts` is not in this write set. The feature passes
   `model.view.native`; only kit `androidMain` casts it.
4. **New kit-content files in `commonMain` (and tests in `commonTest`/`androidHostTest`), not only `androidMain`.**
   Plan 06 §7.7 makes `.accessible` common, and Konsist K10 forbids platform source sets in feature modules, so the
   sign-in screen can only reach the terminal through a common part. The live grid stays Android-only through
   `LocalKitTerminalSlot` (provided by the Android app root). Every file is new and terminal-only.
5. **`feature/agents/build.gradle.kts` is created by this unit.** S3-AGENTSUI owns the module; the file only declares
   the convention plugin and `:ui:kit-content`.
6. **The sign-in lifecycle (B-LINUX-094…096) lives in `:feature:agents` (`SignInTerminalSession`)**, over ports. Plan
   11 W11-08a puts the foreground wait in `AndroidTerminalHost`; neither the host nor the claim exists here. The host
   should still refuse to start a sign-in without the claim (defence in depth).
7. **`TerminalModel.close()` returns `Outcome<Unit>`** (plan 17 §2.6 says `Unit`): a failed drain must keep the claim
   and allow an exact retry (B-LINUX-095). `TerminalModel.events` (W11-08a) is added to the 17 §2.6 shape.
8. **Feature tests use a hand-written run-to-idle loop (`TestLoop`)** instead of `runTest`: K1 scans feature test
   files and allows only `launch`, `CoroutineScope` and `flow` from kotlinx.coroutines. For the same reason the
   session uses flows rather than `Deferred`/`Mutex`, and the screen has no compose UI test in the feature (the kit
   parts it arranges are tested in kit-content).
9. **`SignInTerminalViewModel` is not contributed to Metro and there is no `*Entries` file**: `AgentsRoute` does not
   exist in `:ui:routes`, and none of the port bindings exist.

## Catalog requests

None for the parts built. For the finish line, the host needs Termux's Apache 2.0 terminal libraries, which are on
JitPack only (`com.github.termux.termux-app:terminal-emulator` and `:terminal-view`, `v0.118.3`, the Flutter app's pin):
either catalog entries plus a JitPack `exclusiveContent` repository limited to `com.github.termux.termux-app` in
`settings.gradle.kts` (the Flutter app's pattern), or S5-TERMFORK's source fork brought forward. `ExternalRules`
already allows the group in `:android:terminal` and `:ui:kit-content`.

## Words requests (`:ui:words`)

Stand-ins are used meanwhile (marked `TODO(words)` in `SignInTerminalScreen.kt`):

| Needed (plan) | Stand-in |
|---|---|
| "Accessible mode" (06 §7.7 switch) | `terminalScreenReadableMode` "Show as readable text" |
| "Send to terminal" (06 §7.7 field) | `e7SetupCommandInput` "Terminal command input" |
| "Set up a provider key on the host to use this agent." (17 §1.5) | `agentAccountApiKey` + `agentsSignInUnavailable` |
| "Allow notifications before signing in, then try again." (Dart `local_terminal.dart:776`) | `kitCapPermNotificationsWhy` |
| "Could not keep sign-in running. Try again." (Dart `local_terminal.dart:778`) | `agentsSignInFailed` |
| "Copy code" without the code in the label (17 §1.5) | `agentsSignInCopyCode` "Copy code %1$s" (Dart's) |

## Requests to other owners

- **Kit-content owner:** add `implementation(project(":core:common"))` and switch `KitTerminalLive`/`KitTerminalView`
  to take `PlatformViewHandle` (deviation 3); list the terminal parts in `KIT.md`.
- **App root (`:androidApp`):** provide `LocalKitTerminalSlot provides AndroidKitTerminalSlot` next to `KitTheme`.
- **Runtime owner (`:android:linux`, W11-08a):** `AndroidTerminalHost.open(SignIn)` over the PTY (`LocalTerminal` +
  `PtyAccess` are still a shim, S1-LINUX D1), a Termux `TerminalView` as `TerminalModel.view`, the stand-in `xdg-open`
  watcher as `TerminalEvent.OpenUrl`, the transcript through a sanitizer like `TerminalTranscript`; `ForegroundWork`
  `SignIn` claim (W11-09) as `SignInForeground`.
- **`:engine:project` / contracts:** freeze `TerminalHost`/`TerminalModel` in `contracts/s3.md` with `close():
  Outcome<Unit>` and `events` (deviation 7); this unit's ports then become imports.
- **S3-AGENTSE / INTEGRATE-W2:** once `:engine:agents` compiles, the adapter from `AgentSignIn` + `SignInOutputReader`
  + `AgentAuth.confirmSignIn` to `SignInAgents`.
- **Routes owner:** `AgentsRoute.SignInTerminal(agentId)` (07 G03); then S3-AGENTSUI adds the entry and the Metro
  contribution.
- **Coordinator:** a `MODULE_OWNERS` row for `:feature:agents` (S3-AGENTSUI; S3-TERMMIN for the sign-in files), or
  `ModuleOwnersTest` fails.

## Open problems

1. **No PTY in the tree.** `:android:linux`'s `LocalTerminal` is a shim whose `start` throws (S1-LINUX D1), so
   "over the copied PTY" has nothing to run on.
2. **No Termux library resolvable** (JitPack only; repositories are fixed in `settings.gradle.kts`; see catalog
   requests). The kit slot does not need it; the host does.
3. **No terminal host, foreground claim, route or bindings**, so the screen is not reachable in the app and E2E-31
   (also with TalkBack) was not run. No emulator was used.
4. The ViewModel cannot drain on `onCleared` (no suspend there); a screen torn down without Back relies on the host's
   `owner-closed` drain (plan 17 §1.5).
5. "Owner unbind drains a registered pending PTY" (`local_terminal_signin_foreground_test.dart:407`) is a host-registry
   behaviour and is not ported here.
6. The throttled live region is a separate 1 dp node; it was checked in Robolectric, not with TalkBack on a device.

Shipping state: implemented; verified locally (commands above); committed and pushed to
`native/termmin-2026-11-01`. Not enabled, not deployed, not released. No PR, no CI run, no signing. `master` and tags
untouched.
