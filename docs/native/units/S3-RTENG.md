# Unit S3-RTENG: `:engine:runtime`, first pass (heat, budget, server lifecycle)

Branch `native/rteng-2026-11-01`, cut from `native/m0-2026-11-01` at `6b9a2b21`. Write set: `native/engine/runtime/**`
(new module) and this report. Plan: 15 row S3-RTENG; 11 §5.2, §5.4, §5.5, §6, §7, §8; map `builtin-linux.md`.

**State:** implemented and committed; 86 common tests green on desktop (also with `-Poc.werror=true`); the Android
and common-metadata targets compile. **Not finished against the plan 15 finish line**: this pass covers the heat and
battery tiers, the process budget and the phone server's lifecycle rules (the task brief). The setup engine, the
recovery consumer, healing beyond the app-open start, idle, the manager parsers and the `.sh.tmpl` scripts (S11-3)
are still open (§7). Nothing is bound to Android (that is S3-RTWIRE).

## 1. What was built

Pure Kotlin, `commonMain`, package `io.github.eslamasabry.oc.engine.runtime`, depending only on `:core:common`
(so it builds while the rest of the core contract is being reconciled). No `Dispatchers.*`, no `Clock.System`, no
logging, no copy: clocks and scopes are injected, and every user-facing word stays with `:ui:words`.

| File | Port of | What it holds |
|---|---|---|
| `heat/Thermal.kt` | `lib/platform/thermal.dart` | `ThermalStatus` (native words, `atLeast`), `ThermalReading.of` (NaN/negative headroom → null), `BatteryReading.of`/`fromSysfs` |
| `heat/HeatTier.kt` | `lib/builtin/heat_tier.dart` | `HeatTier` Normal/Slow/Hold, `HeatTierPolicy.decide` (0.7/0.9 headroom, 20/10 % unplugged battery, power save, hidden, Always full speed; calm 2 min) |
| `heat/AgentThrottle.kt` | `heat_tier.dart` `AgentThrottle` | `AgentPriorityLevel` (nice 0/10/15), up at once, down after 2 calm minutes, full speed restores at once, re-applied every 5 min while slowed, a failed `setPriority` retried at the next reading; tier as `StateFlow` |
| `heat/ThermalPolicy.kt` | `thermal_guard.dart` `ThermalPolicy` | pause at SEVERE or headroom ≥ 0.95, stop at CRITICAL+, resume after 2 cool minutes, off still gives back |
| `heat/ServicesHeatHold.kt` | `linux_services/linux_services_heat.dart` | the Linux services heat hold over a hold-file port (CRITICAL holds, cool for 2 min releases, a hold left by an earlier process is given back) |
| `budget/ProcessBudget.kt` | `process_budget.dart` | `HeavyJob` (4/6/3/2), `BudgetWait` Room/Warm/Battery, `ProcessBudget.decide`: CAP 32, LIFTED_CAP 48, RESERVE 4, LOW_BATTERY 20, WARN_FROM 28; heat first, then battery, then room |
| `budget/ProcessBudgetGuard.kt` | `process_budget.dart` `ProcessBudgetGuard` | waits, never kills; oldest first; 45 s reservations; polls every 5 s while anything waits; cancellation of the caller withdraws the wait, `close()` ends every wait as `Cancelled`; `queuesOften()`; a phone it cannot read never blocks; `BudgetState` as `StateFlow` |
| `budget/AndroidKills.kt` | `lastAndroidKill`, `BuiltinServiceExitReason` | the last unrequested exit-137 of a Linux service in 24 h |
| `host/RuntimeStatus.kt` | 11 §5.7 | the frozen status contract as a data class, plus `idleBlocks` / `idleBlocksLaunch` (`phone_server_healing.dart`) |
| `host/RuntimeCall.kt` | 11 §5.5 | `RuntimeProblem` (the plan's 17 values) and `RuntimeCall<T>` (Done / Refused) — see deviation D1 |
| `server/PhoneServerStarter.kt` | `builtin_server.dart` `startBuiltinServer` | the one start path: OC2-move refusal, lost password renewed (B-LINUX-025), password write, `LaunchMode` Manual / ForAppOpen / Recovery / AfterUpdate, probe every 2 s up to 90 s, exited vs timed out, recovery confirm with the same generation, `StartProblem` (the 8 values), `retryable`; clears the deliberate-stop mark on success |
| `server/AppOpenStart.kt` | `phone_server_healing.dart` `startForLaunch`/`_launchStart`/`_launchStartWanted` | `LaunchStartRules` (pure table) + `AppOpenStart`: once per open, one more try after a fast failure, no retry after a timeout, a failed crash restart is the first try, a running server that rejects our password is replaced, a young booting one is waited for until 90 s of age (30 s when the age is unknown), the native recovery and the idle owner are never superseded, **the person's Stop always wins** (re-checked before every step and enforced natively by the generation) |
| `server/DeliberateStop.kt` | `deliberate_stop.dart` | `oc.phoneServerStopped.<profileId>` over a `RuntimeFlags` port |
| `server/OpenCodeUpdate.kt` | `in_app_auto_update.dart` + plan 11 R7 | `OpenCodeAutoUpdateRule`, `OpenCodeAutoUpdater` (one try per run and pin), `OpenCodeUpdateRestart`: only when idle; activate the journal; restart **once** with the same generation (no recovery budget spent); a failed restart rolls back to the last good binary and starts it; a stopped or not-running server is never started by an update (it gets the new binary at its next start); a Stop during the restart wins and keeps the new version |

Test support: `commonTest/.../FakeLinuxHost.kt`, an in-memory phone Linux (process count, thermal and battery readings,
one OpenCode server with the native intent — wanted, user-stopped, generation — a guarded launch that the native side
refuses after a Stop, probe behaviours, and an OpenCode component journal) implementing `BudgetReadings` and
`PhoneServerPort`; `FakeJournal`. Tests use virtual time (`runTest`; a clock bound to the test scheduler).

## 2. Catalog rows covered (tests named `B-AREA-NNN …`)

| Row | Where | Coverage |
|---|---|---|
| B-ANDR-128 | `HeatTierPolicyTest` | full (the policy table) |
| B-ANDR-129 | `ProcessBudgetTest`, `ThermalPolicyTest` | full |
| B-ANDR-130 | `AgentThrottleTest` | full except the nice script itself (B-LINUX-254, scripts) |
| B-ANDR-083, 084, 085 | `ThermalPolicyTest` | the policy rules only; the AI Team guard around them is `:engine:team` (D6, later) |
| B-ANDR-093 | `HeatTierPolicyTest` | the status-word and headroom parsing; the channel half is gone natively |
| B-LINUX-261 | `ServicesHeatHoldTest` | the hold controller; the hold file and supervisor are the binding's / scripts |
| B-LINUX-281, 282, 283 | `ProcessBudgetTest`, `ProcessBudgetGuardTest` | full |
| B-LINUX-145 | `ProcessBudgetTest` | only "the warning starts at 28" (the rest is `PhoneProcessWatch`, open) |
| B-LINUX-025 | `PhoneServerStarterTest` | full |
| B-LINUX-035 | `PhoneServerStarterTest` | partial: a Stop during the password write prevents the launch; a recovery start confirms the same generation |
| B-LINUX-037 | `PhoneServerStarterTest` | the start-path parts (not "recognised only with its address", which needs the profile model) |
| B-LINUX-038 | `PhoneServerStarterTest`, `DeliberateStopTest`, `OpenCodeUpdateTest` | the never-restart-what-you-stopped parts; the starter's UI handover is S3-PHONEUI's |
| B-LINUX-078, 079 | `OpenCodeUpdateTest` | full |
| B-LINUX-151, 152 | `AppOpenStartTest` | the app-open start; the reconnect half (connection) is not here |
| B-LINUX-245 | `OpenCodeUpdateTest` | "a new OpenCode restarts even a connected server" + R7's rollback and Stop rules; the setup-finish rest is the setup engine's |

Not covered from the plan 15 list: B-LINUX-227…232, 239…244, 246, B-SEC-170 (all setup / scripts / process manager /
folders work, §7).

Tests were written from the rows and the Flutter tests before the code of each file, but the first Gradle run only
happened once the whole slice compiled (Maven Central rate-limited this container for ~40 min), so there is no
recorded red run per row.

## 3. Commands and results

Environment: Android SDK from `commandlinetools-linux-16111833_latest.zip` in `/opt/android-sdk`
(`platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`), `local.properties` `sdk.dir` (gitignored),
JDK 21 (Gradle), Kotlin toolchain 17 through foojay.

```bash
cd native
./gradlew :core:common:compileKotlinDesktop :core:testing:compileKotlinDesktop   # BUILD SUCCESSFUL (after HTTP 429 retries)
./gradlew :engine:runtime:desktopTest                                            # 86 tests, 0 failures
./gradlew :engine:runtime:desktopTest -Poc.werror=true --rerun-tasks             # BUILD SUCCESSFUL, 86 tests, 0 failures
./gradlew :engine:runtime:compileAndroidMain :engine:runtime:compileCommonMainKotlinMetadata -Poc.werror=true  # BUILD SUCCESSFUL
```

The module-rule check (`enforceModuleRules`) runs at configuration and passed (`:engine:runtime` → `:core:common`).
Not run: `:quality:test` (other modules may not compile right now; see C2), `./gradlew -p build-logic test` (not my
module), iOS klibs.

## 4. Deviations from the plan

- **D1 `RuntimeCall` instead of `Outcome` + `Failure.Runtime`.** CR11-1 puts `RuntimeProblem` in `:core:common` as
  `Failure.Runtime`; the core contract has no such leaf yet and I may not edit core. The ports answer
  `RuntimeCall<T>` (Done / Refused(problem, details: Redacted?)); swap to `Outcome` mechanically once CR11-1 lands.
  `RuntimeProblem` here is the plan's enum verbatim.
- **D2 Narrow ports, not the full `RuntimeHost`.** §5.4's `RuntimeHost` needs `GuestScript`, `RestoreRecipe`,
  `SecretText`, `StopTicket` and the rendered scripts (S11-3), none of which exist yet. This pass defines what its
  rules call: `PhoneServerPort` (history-move check, password write, launch by `LaunchMode`, probe, refreshStatus,
  confirmRecovery), `BudgetReadings`, `RuntimeFlags`, `ComponentJournal`, `OpenCodeHostFacts`/`OpenCodeUpdateJobs`.
  S3-RTWIRE implements them over `AndroidRuntimeHost`; the full facet interfaces still need to be written here.
- **D3 Password as `String`.** `SecretText` lives in `:core:security`, which this module does not depend on yet; the
  password only crosses `writePassword` and `probe`, `StartTarget.toString()` hides it, and nothing logs.
- **D4 Restart-after-update result instead of a "While you were away" entry.** R7 says one "Updated OpenCode to …"
  entry; 02 S02-8 says the native app stops recording "While you were away" at the switch. `OpenCodeUpdateRestart`
  returns `UpdateRestartResult` and records nothing; the caller (`:engine:attention`) decides. Needs a coordinator
  ruling.
- **D5 R7's Stop rule (my reading).** A failed update restart whose cause is a Stop (or a withdrawn start) keeps the new
  version and leaves the server stopped; only other failures roll back. A not-running or person-stopped server is
  never started by an update. R6 supports this; R7 does not say it explicitly.
- **D6 Event restore (R1–R5, R8, R9) stays native.** `NativeServerEventPolicy`, `NativeRecoveryBudget` and the
  recipe rules are already copied unchanged into `:android:linux` (S1-LINUX); plan 11 §6.3 says crash recovery is
  "native, unchanged". I did not duplicate them in common code.
- **D7 `ProcessBudgetGuard.admit` returns `BudgetAdmission`** (Admitted / Cancelled on `close()`); a caller cancels its
  own wait by cancelling its coroutine (Flutter: a `cancelled` future and a thrown `BudgetCancelled`).
- **D8 No formatter run.** `tool/format-changed.sh` (rule 13) does not exist in the tree yet; files follow the
  surrounding style by hand.
- **D9 Commit 2 alone does not compile**: `FakeLinuxHost` (committed with the budget) references the server ports
  committed in commit 3. The branch tip compiles and is green.

## 5. Catalog requests and requests to other owners

- **C1 (core owner):** CR11-1 — `Failure.Runtime(problem: RuntimeProblem)` in `:core:common`; then D1 goes away.
- **C2 (coordinator):** add `:engine:runtime	S	runtime-owner	slice-3` to `native/MODULE_OWNERS`;
  `ModuleOwnersTest` in `:quality` fails until then (as it does for `:engine:analytics` today).
- **C3 (core owner):** `:engine:runtime` will need `:core:security` (`SecretText`) and `:core:data` (preferences for
  `RuntimeFlags`, `ProfileScope` registration of `oc.phoneServerStopped.<p>`) once those compile on the branch.
- **C4 (build-logic owner):** `OcScriptsPlugin` / `generateRuntimeScripts` (S11-3 step 2) is needed before the scripts
  half can land; no new library is needed for this pass.
- **C5 (coordinator):** the D4 ruling (R7's entry vs S02-8).

## 6. Hand-off notes for S3-RTWIRE

- Bind `PhoneServerPort.launch` to `server.start` / `startForLaunch` / `restart` by `LaunchMode`; `AfterUpdate` uses
  `restart(…, expectedGeneration)` and must not call `recovery.confirm`.
- `AppOpenStart.allowAgain()` belongs on the shell's first resume after the app left (the once-per-open rule, R9 c).
- `AgentThrottle.evaluate()` on every visibility change; `observe()` on every thermal reading from the one listener.
- `ProcessBudgetGuard` needs the app scope and `enabled = PlatformCapabilities.builtinLinux`.
- `ServicesHeatHold.readHold` answers null when the hold file cannot be read (that reading is skipped).

## 7. Open problems (what remains of the S3-RTENG finish line)

1. `SetupEngine`, `ComponentRegistry`, `Preflight`, `ComponentRemoval` (B-LINUX-001, 007, 014, 157, 181…220, 227,
   230, 242…246, B-SEC-170).
2. `ServerRecoveryConsumer` (states, 5 s / 45 s cadence; B-LINUX-031…036 beyond this pass, 232), `PhoneServerHealing`
   beyond the app-open start (B-LINUX-153…156), `PhoneServerIdleResume` (B-LINUX-018, 019), `AppExitRecovery` /
   `LifecycleReport` (B-LINUX-010…013), `PhoneServerState` derivation (11 §6.1, B-LINUX-250).
3. `PhoneProcessWatch` (B-LINUX-143…147, 180), process manager parsers (B-LINUX-239), `LinuxManager` / `LinuxServices`
   / `LinuxVitals` parsers, `RootfsFolders` (B-LINUX-231), the phone note / thread caps (B-LINUX-280).
4. The scripts as checked `.sh.tmpl` files with `ScriptTemplate`, builders and the golden / checksum / shell tests
   (S11-3, incl. the agent nice script, B-LINUX-254) — blocked on C4.
5. The full §5.4 `RuntimeHost` facet interfaces and `UnavailableRuntimeHost`.
6. The status-map parser (strict, as `BuiltinLinuxStatus.fromMap`) if it is to live in common code rather than the
   binding.
