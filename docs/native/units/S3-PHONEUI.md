# Unit S3-PHONEUI: `:feature:phone`, first pass (This phone, Running on this phone, Keep running)

Date: 2026-11-01. Branch `native/phoneui-2026-11-01`, cut from `claude/native-phoneui-2026-11-01-d4mybm` at
`7db496b2` (the M0 tip after INTEGRATE-W1 and the S1/S2/S3 merges). Write set: `native/feature/phone/**` (not the
Linux manager) and this report. Plan: 15 row S3-PHONEUI; 07 §5 F (F01, F08, F15); 11 §6.1, §6.2, §6.7, §7.2, §8.4.

**State:** implemented and committed; 42 common tests green on desktop; the Android target compiles. Nothing is
pushed to master, no PR, no CI run, no APK, no release. **Not finished against the plan 15 finish line**: the pages
are not reachable yet (no `PhoneRoute` in `:ui:routes`, so no entry installer), nothing binds the seams to Android
(S3-RTWIRE), setup A/B/C (F02–F04), the This phone › Details › Performance rows (S2-FIELD) and E2E-17/18 are open (§7).

## 1. What was built

New module `:feature:phone` (`oc.kmp.feature`, depends on the public API of `:engine:runtime`), package
`io.github.eslamasabry.oc.feature.phone`. The module folder is picked up by `settings.gradle.kts`' layer scan; no
shared file was edited. One screen = Ui + Actions + state holder + ViewModel + Screen; presentation has no copy and
no Compose; screens arrange kit parts only and take every word from `:ui:words` (the Flutter ARB strings already
converted into `strings_phone.xml`).

| File | What it holds |
|---|---|
| `presentation/PhoneSources.kt` | The seams the pages read, shaped as narrow faces of plan 11 §5.4's `RuntimeHost` facets (which do not exist yet, S3-RTENG §7 item 5): `PhoneServerSource` (`facts: StateFlow<PhoneServerFacts>`, `refresh`, `start`, `stop`, `restart`, `updateOpenCode`, each `Outcome<Unit>`), `KeepAliveSettings` (plan 11 §8.4 / CR11-9: `info`, `open(Battery \| Autostart \| AppDetails)`), `PhoneProcessSource` (`read`, `stop(pid)`), `ChildLimitSource` (status, open Developer options, last Android kill, `queuesOften`, device-wide Hide memory `oc.childLimitCardHidden`), `PhoneNavigator` (`open(PhonePage)`, `back`). `PhoneServerFacts` carries `RuntimeStatus`, the last authenticated probe, the deliberate-stop mark, the last exit reason, start/recovery state, versions, reply/update activity and the process count. |
| `presentation/ThisPhoneAnswer.kt` | `PhoneServerState.of(facts)`: plan 11 §6.1's table (`Checking`, `NotInstalled`, `SettingUp`, `InstallFailed`, `Stopped(Person \| Idle \| Itself \| Android)`, `Starting`, `Running`, `NotAnswering`, `Restarting(attempt, nextAt)`, `RestartingNow`, `Restoring`, `GaveUp(BudgetExhausted \| Ownership \| PolicyDisabled \| StorageUnavailable)`); running needs a live authenticated answer. `ThisPhoneAnswers.of(...)`: the page's one answer (tone, headline, line, one act, countdown target), a port of `this_phone_answer.dart` `_answerNow` for the in-app host. `AboutWait` ports `thisPhoneWait` (rounded up). |
| `presentation/ThisPhoneUi.kt`, `ThisPhoneModel.kt` | F01's state and intents: server rows Stop / Restart only while the server is up (each names its object), the version row (installed, `UpToDate` / `Available(pinned)` / `Failed(pinned)` / `Updating`; a leading `v` ignored, an unknown version offers nothing), the Running row (count, cap 32/48, near-limit from 28 via `ProcessBudget`), the Keep running row. One command at a time; Restart and Update ask first; Update is refused while a reply is written; failures and a missing Android screen are said in place. While shown it re-reads the status once and moves the countdown each second. |
| `presentation/RunningModel.kt`, `ChildLimitCard.kt` | F08: reads every 10 s only while shown, keeps the last reading on a failed refresh, says a failed first read with Try again, never offers a protected row (server root, the app), stops one process after a confirm and reads again, warns from 28. `ChildLimitCards.kind` ports `childLimitCard` (Android 14+, limit not lifted, after a kill or frequent queueing, hidden until a newer kill; `OpenSettings` / `EnableDeveloperOptions` / `InstructionsOnly`); when Settings does not open the card falls back to the steps as text; the app never writes the setting. |
| `presentation/KeepRunningModel.kt` | F15: `PhoneMaker.of` and `KeepAliveAdvice.steps` (port of `keep_alive_advice.dart`, B-ANDR-007), steps ordered by what is left, done steps keep their word and open nothing, "You're set" only on stock Android with the battery exemption, the swipe warning for makers that close a swiped app, Android's daily background limit always stated, a missing screen said in place, re-read on every resume. |
| `presentation/PhoneTimers.kt` | The module's timer table (plan 13 §11.2): `phone.runningRefresh` (10 s) and `phone.answerCountdown` (1 s), foreground only. Repetition goes through core's `Pacer`, not `delay` loops (K1 keeps `delay`/`Job` out of features; K-P1). |
| `presentation/PhoneViewModels.kt` | `ThisPhoneViewModel`, `RunningViewModel`, `KeepRunningViewModel`: Metro `@ContributesIntoMap` ViewModels handing `viewModelScope` to the state holders. |
| `ui/ThisPhoneScreen.kt`, `RunningScreen.kt`, `KeepRunningScreen.kt`, `PhoneWords.kt` | Kit-only screens (`KitScreen` + `KitTopBar`, `KitNotice`, `KitErrorNotice`, `KitRowGroup`/`KitRow`, `KitActionBlock`, `KitConfirm`, `KitGroupNote`); `LifecycleStartEffect` drives `shown`, `LifecycleResumeEffect` re-reads Keep running. `PhoneWords.kt` maps every value to its `:ui:words` string and ports `thisPhoneSize` ("410 MB", "1.2 GB", isolated LTR). |

## 2. Catalog rows covered (tests named `B-AREA-NNN …` or after the plan clause when no row exists)

| Row | Test | Coverage |
|---|---|---|
| B-LINUX-250 | `PhoneServerStateTest`, `PhoneScreensTest` | full for the page: a server that stopped by itself reads stopped (not running from saved state) and offers Restart now; an Android kill is named as such |
| B-LINUX-240 | `PhoneServerStateTest` | the page's half: running only with a live authenticated probe; a password challenge is not healthy; a stopped set-up server reads stopped, not absent (the probe itself is the runtime's) |
| B-LINUX-038 | `PhoneServerStateTest`, `ThisPhoneModelTest` | the UI handover S3-RTENG left here: a person-stopped server stays off with "It stays off until you start it" and only Start; Start runs once however often it is tapped |
| B-LINUX-037 | `PhoneServerStateTest` | "a server that exits is reported": Exited → "The server didn't start" + Start again (beats the stop mark) |
| B-LINUX-036 | `PhoneServerStateTest` | exhausted budget → "won't restart by itself" with Restart now kept; ownership unknown → the app-settings force-stop answer |
| B-LINUX-249 | `PhoneServerStateTest` | partial: a failed install offers setup again; the classification of the manager's failure is the runtime's |
| B-LINUX-078 | `ThisPhoneModelTest` | the row half: a leading `v` is ignored, an unknown version offers nothing (the auto-update rule is S3-RTENG's) |
| B-LINUX-079 | `ThisPhoneModelTest` | the row half: after a failed automatic update the manual row stays and asks first |
| B-LINUX-143 | `RunningModelTest`, `PhoneScreensTest` | partial: protected rows never get a Stop (the tree grouping and loop safety are `PhoneProcessWatch`, open in S3-RTENG) |
| B-LINUX-145 | `ThisPhoneModelTest`, `RunningModelTest` | "Android's process warning starts at 28" on both pages (stuck detection is `PhoneProcessWatch`) |
| B-LINUX-147 | `RunningModelTest` | partial: stop sends the pid once and reads again; a refused stop is said (the native reading is the binding's) |
| B-ANDR-007 | `KeepRunningModelTest`, `PhoneScreensTest` | full: maker from manufacturer and brand, battery first, the lock tip where swiping force-stops, per-maker words |
| plan 11 §6.1 / §6.6 / §6.7 | `PhoneServerStateTest`, `ThisPhoneModelTest` | restoring and restarting shown, countdown, idle stop is neither the person's nor a crash, Stop says stopping and a failed stop is said |
| plan 11 §7.2 guided card | `RunningModelTest`, `PhoneScreensTest` | the `childLimitCard` table, open → fall back to steps, Hide until a newer kill, a lifted limit folds the card |
| plan 07 F01 / F08 / F15 | `ThisPhoneModelTest`, `RunningModelTest`, `KeepRunningModelTest`, `PhoneScreensTest` | rows, confirms, refresh only while shown, steps ordered by what is left, "You're set", the 6 h / 24 h line |
| plan 13 §11.2 | `PhoneTimersTest` | the timer table passes `timerTableProblems`; nothing ticks while hidden |

Red first: the state/answer tests and the This phone model tests were run red (compile failure: unresolved
`ThisPhoneModel`, `PhoneOp`, … 34 errors) before their code. For Running, the card and Keep running, tests and code
were written in one step and first run together (green on the first run), so there is no recorded red run for those.

## 3. Commands and results

Environment: Android SDK from `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk/cmdline-tools/latest`
(`platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`, licenses accepted), `ANDROID_HOME=/opt/android-sdk`,
`local.properties` `sdk.dir` (gitignored), Gradle on JDK 21, JDK 17 toolchain through foojay. Container-only Gradle
setup (not committed): `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers; `~/.gradle/init.d/central-mirror.gradle.kts`
puts `google()` and Google's Maven Central mirror before Central and the plugin portal (Central and the portal's
Central redirect answer HTTP 429 through the proxy; with the mirror after `gradlePluginPortal()` the AGP plugin marker
could not resolve).

```bash
cd native
./gradlew :engine:runtime:compileKotlinDesktop :ui:kit:compileKotlinDesktop   # BUILD SUCCESSFUL (warm-up)
./gradlew :feature:phone:desktopTest     # each slice; final: 42 tests, 0 failures, no warnings in this module
#   PhoneServerStateTest 13, ThisPhoneModelTest 10, RunningModelTest 8, KeepRunningModelTest 6,
#   PhoneScreensTest 4 (Compose UI, runComposeUiTest), PhoneTimersTest 1
./gradlew :feature:phone:compileAndroidMain   # BUILD SUCCESSFUL
native/tool/check_write_set.sh --globs 'native/feature/phone/**' --also 'docs/native/units/S3-PHONEUI.md' \
  --base claude/native-phoneui-2026-11-01-d4mybm   # write-set: OK
native/tool/check_commits.sh claude/native-phoneui-2026-11-01-d4mybm HEAD   # OK (after rewording, §4 D6)
native/tool/scan_push.sh claude/native-phoneui-2026-11-01-d4mybm HEAD       # see §3.1
```

`-Poc.werror=true` was tried once: it fails in `:ui:kit` (`KitField.kt:261` deprecated `maxLength`, `KitRow.kt:162–163`
unneeded `!!`), not in this module; this module compiles with no warnings. Not run: `:quality:test` (Konsist, module
owners; other modules may not compile and it is the coordinator's gate), `./gradlew -p build-logic test`, iOS klibs,
the APK, any emulator or live-server check.

### 3.1 Push hygiene

Before the push, the branch diff (`native/feature/phone`, this report) was grepped for IPv4 addresses and e-mail
addresses: no hits. `native/tool/scan_push.sh claude/native-phoneui-2026-11-01-d4mybm HEAD`: "scan_push: clean"
(run on the code commits, then again with this report).

## 4. Deviations

- **D1 Seams in the feature, not the engine.** Plan 11 §5.4 puts `RuntimeHost` and `PhoneServerState` in
  `:engine:runtime`; S3-RTENG did not write the facets (its §7 item 5) and this unit may not edit the engine. The
  pages read feature-local interfaces (§1) shaped 1:1 on those facets. Because an engine cannot depend on a feature,
  the bindings must live in `:androidApp` / S3-RTWIRE until the runtime engine exposes the same shapes; then the
  imports swap and the feature interfaces are deleted.
- **D2 `PhoneServerState` derivation lives here.** Same cause; the table is plan 11 §6.1's verbatim and has its own
  tests, so moving it into `:engine:runtime` is a cut-and-paste.
- **D3 No entry installer.** `:ui:routes` has no `PhoneRoute` (07 §2.2.2 rows 39–54) and it is coordinator-owned, so
  `PhoneEntries` is not written; navigation goes through `PhoneNavigator.open(PhonePage)` until then.
- **D4 Repetition through `Pacer`.** Konsist K1 bans `kotlinx.coroutines.delay`/`Job` in features and K-P1 wants
  declared timers, so the 10 s refresh and the 1 s countdown are `PhoneTimers` specs collected through core's
  `Pacer` (`DefaultPacer` on virtual time in tests).
- **D5 Keep running without the AI Team heat pause and "Always full speed" groups.** The heat guard is AI Team (D6,
  deferred) and "Always full speed" is a setting owned by `:engine:settings`; both are left for their owners.
- **D6 Commit subjects reworded** (before any push) with `git filter-branch --msg-filter` to pass `check_commits.sh`
  (`type(scope): …`, ≤ 72 characters); content unchanged.
- **D7 Words reused, none added.** Every sentence is an existing ARB string. Two choices worth a look by the l10n
  owner: the notice close uses `workspaceDismissNotice` ("Dismiss"), and a failed manual update reuses
  `thisPhoneAnswerInstallFailed` ("Setup didn't finish").

## 5. Catalog and owner requests

No library is needed (the module uses only the `oc.kmp.feature` bundle and `:engine:runtime`).

- **R1 (coordinator, `:ui:routes`):** add `PhoneRoutes.kt` with `PhoneRoute.ThisPhone`, `SetupStart(addTools)`,
  `SetupProgress`, `SetupReady`, `Running`, `KeepRunning` (and the Linux-manager rows for S5) per 07 §2.2.2; then this
  unit writes `PhoneEntries` mapping `PhonePage` to them and gating on `PlatformCapabilities.builtinLinux` (07 §2.8).
- **R2 (coordinator, `MODULE_OWNERS`):** `:feature:phone	F	(slice brief names the agent)	slice-3`; `ModuleOwnersTest`
  fails until then. Add `:feature:phone` to `:androidApp`'s feature list when R1 and the bindings exist.
- **R3 (S3-RTENG / S3-RTWIRE):** expose `PhoneServerSource`, `PhoneProcessSource`, `ChildLimitSource` (incl.
  `ProcessHost.childProcessLimit()`, `lastAndroidKill`, `ProcessBudgetGuard.queuesOften`) and plan 11 §8.4's
  `KeepAliveSettings` with these shapes, and move `PhoneServerState` into `:engine:runtime` (D1, D2). Bind
  `PhoneNavigator` in the shell once R1 lands. `Pacer` must be bound in the app graph (no `@Provides` for it was found).
- **R4 (quality owner):** `KonsistRulesTest` K1 scans `scope("feature")` without the production slice, so feature
  tests importing `kotlinx.coroutines.test.*`, `androidx.compose.ui.test.*` and a test `KitPlatform` would be flagged;
  please slice K1 to production sources as the other rules are.
- **R5 (kit owner):** `:ui:kit` is not `-Werror` clean (§3).

## 6. Hand-off notes

- `ThisPhoneViewModel` expects `PhoneServerFacts.status` to be pushed on every native change (plan 11 §5.7) and the
  probe to be re-run by the runtime; the page only calls `refresh()` once per show.
- `PhoneServerSource.restart()` is a person's restart: it must not spend a crash-recovery attempt.
- `ChildLimitSource.hide(at)` writes the device-wide `oc.childLimitCardHidden` (not per profile, so no `ProfileScope`
  registration); the card reads but never writes the Android setting.

## 7. Open problems (what remains of the plan 15 finish line)

1. Reachability: `PhoneRoute`s and `PhoneEntries` (R1), the app graph bindings (R2, R3).
2. Setup A/B/C pages (F02 `SetupStart`, F03 `SetupProgress`, F04 `SetupReady`) — they need the setup engine, still open
   in S3-RTENG (its §7 item 1).
3. This phone › Details (steps, log, Report) and the Performance rows over S2-FIELD's recorder.
4. This phone's remaining rows from 07 F01: optional tools with "Remove <tool>" and measured space, Open a terminal,
   Linux, Termux migration row, the preview-flavour "another OpenCode package runs a server" line (11 §7.2), the heat
   answer (AI Team, D6), "Use this phone's server" connect act (needs the connection engine).
5. Running on this phone: the parent/child tree, stuck and heavy marks and "Stop everything left behind"
   (`PhoneProcessWatch`, S3-RTENG §7 item 3), process detail page.
6. Teaching tips: none of these pages has a hidden gesture, so no tip is owed yet.
7. E2E-17 and E2E-18, screenshots/Roborazzi, Arabic layout check, TalkBack pass.
