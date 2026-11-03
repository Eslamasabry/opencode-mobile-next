VERIFIED: green

# P12-BUILD-RUNTIME-A — 52 runtime rows ported natively

Branch `claude/native-parity-build-gvu55s` (base `native/p12-integrate-2026-11-03` @ `71f74de8e`). Owner of the new
module `:feature:devservices` (MODULE_OWNERS: `build-runtime-a`). Everything else is new files in `:engine:runtime`
(sub-packages below) plus three small shared edits listed under "Shared-file edits".

## Rows proven: 52 of 52

The brief said 51; the list holds 52 ids, and all 52 have passing tests whose names start with the row id
(counted from the JUnit XML of the runs below; `native/tool/parity/ledger.py` does not exist on this base, so I used
an equivalent counter: a row counts only when a passing, non-skipped test names it). Test count per row in brackets.

B-ANDR-051 [4], B-ANDR-052 [4], B-ANDR-079 [2], B-ANDR-080 [3], B-ANDR-086 [6], B-ANDR-088 [3], B-ANDR-090 [3],
B-ANDR-110 [2], B-L10N-011 [1], B-L10N-019 [6], B-LINUX-007 [3], B-LINUX-008 [3], B-LINUX-009 [3], B-LINUX-010 [4],
B-LINUX-011 [5], B-LINUX-012 [2], B-LINUX-013 [2], B-LINUX-014 [6], B-LINUX-015 [2], B-LINUX-016 [5],
B-LINUX-017 [8], B-LINUX-018 [6], B-LINUX-019 [3], B-LINUX-020 [6], B-LINUX-021 [7], B-LINUX-022 [4],
B-LINUX-023 [7], B-LINUX-024 [9], B-LINUX-026 [7], B-LINUX-028 [6], B-LINUX-029 [6], B-LINUX-030 [18],
B-LINUX-031 [7], B-LINUX-032 [6], B-LINUX-033 [7], B-LINUX-034 [6], B-LINUX-043 [2], B-LINUX-049 [2],
B-LINUX-050 [3], B-LINUX-051 [5], B-LINUX-052 [3], B-LINUX-054 [5], B-LINUX-055 [2], B-LINUX-056 [3],
B-LINUX-057 [3], B-LINUX-058 [4], B-LINUX-059 [2], B-LINUX-060 [5], B-LINUX-061 [6], B-LINUX-062 [3],
B-LINUX-063 [7], B-LINUX-064 [2].

## What a person can do now (reachable in the Android app)

- **Development services** (B-LINUX-054…064): Conversations → a project (Projects page) → **Development services**
  row → the new page (`DevServicesRoute.Home`). Register a service (name, command, preview URL, with the Flutter
  checks: name required and unique, command required, http/https URL with no user name), see it listed after a
  restart (saved under the Flutter key `oc.developmentServices.<profileId>`, so migrated services read as-is and the
  profile sweep removes it), Remove it (asks first), Visit its URL (through `ExternalLinkPolicy`). Start, Stop
  (asks), Restart (asks), Logs (bounded tail, colour codes stripped), Forget last run (asks) work as soon as the
  server reports `developmentServices` — see "Integrator" below. **Also running**: on the phone's own server with
  the in-app Linux the page scans the phone (listening programs with ports, Stop asks first, Logs, Save as command);
  elsewhere it lists the server's own shells. Plain words EN+AR (existing `services*` strings), kit parts only,
  predictive back, problems as a plain line with Details.
- **Setup message words** (B-L10N-019): `engine.runtime.messages.SetupMessages` maps every app-authored setup,
  manager and bridge sentence (255, generated from Flutter's table) to its `:ui:words` key, keeps the exit code, and
  leaves foreign detail verbatim. Not yet called by a screen (the setup progress page belongs to :feature:phone):
  integrator one-liner below.
- B-L10N-011: the shipped English/Arabic reset-leftover sentences are proven by `RuntimeWordsTest` (no copy change needed).

## Engine-only (ported and proven, not yet bound in the app)

| Package (`io.github.eslamasabry.oc.engine.runtime.*`) | Rows | Entry points |
|---|---|---|
| `heat` | B-ANDR-086, 088, 090, 110 | `ThermalGuard` (start/observe/evaluate/enabled/setEnabled/dismiss; `notice`, `pauseActive`, `nextCheckAt`), `HeatPausePort`, `ThermalGuardStore` (`oc.thermalGuard`, `oc.thermalPause.<id>`, Flutter JSON) |
| `recovery` | B-LINUX-010…013 | `AppExitRecovery.runOnce(RecoveryScene)`, `RuntimeAutomation`, `AutomationGate`, `PhoneAutoStart`/`StarterAutoStart` (reuses `PhoneServerStarter`, `AppOpenStart`, `DeliberateStop`) |
| `recovery.budget` | B-LINUX-032…034 | `ServerRestartBudget` (check, setForeground, manualStartConfirmed, policyChanged, suspendProfile) |
| `bridge` | B-LINUX-018…024, 031, 049…052 | `BuiltinLinuxChannel` (Dart method names and keys), `BuiltinLinuxBridge`, `StatusReadSharing`, `RuntimeStatusMap` (parses into the existing `RuntimeStatus`), `BuiltinScripts`/`BuiltinScriptTexts` (hash-pinned) |
| `setup` | B-LINUX-007, 008 (amended), 014, 015 | `LinuxChannel`, `LinuxServices`, `SetupJobEngine` (poll on a timer), `PhoneSetupFinisher` |
| `projects` | B-LINUX-016, 017, 029, 030 | `ProjectFolders`, `ProjectRemoval` (typed confirmation), `PhoneFiles` port |
| `diagnostics` | B-LINUX-026 | `ServicePerformance` |
| `storage` | B-ANDR-052 | `PhoneStorage`, `ChannelPhoneStorageSources` |
| `paths` | B-ANDR-051, 079, 080 | `PhoneStorageFolders`, `SharedStoragePath` |
| `devservers`, `services` | B-LINUX-054…064 | `DetectedDevServers`, `PhoneScan`, `DevelopmentServices`, `DevelopmentServiceStore`, `ManagedShellWire` (the `/api/shell` contract) — **bound** via DevServicesWiring |
| `messages` | B-L10N-019 | `SetupMessages.classify/plain` |

`:android:linux` gained two JVM test files (B-LINUX-009 `AndroidGroupNamesTest`, B-LINUX-030 `BuiltinProjectStorageTest`).

## Partial / deviations (per row)

- B-LINUX-008, B-LINUX-043, B-ANDR-086: AI Team / team discovery parts deferred by D6 (amended scope ported).
  B-LINUX-043 checks `scripts/host/ubuntu-opencode.sh` with `bash -n` (+ shellcheck when present); the setup prelude
  and manager scripts are `internal` to :feature:linux and already parsed by its `LinuxScriptsShellTest`.
- B-LINUX-014: cited tests ported; app-side components (voice model), Termux hand-over, cancel and process-budget
  wait are not in the cited tests and not ported.
- B-LINUX-023/024: Termux manager script hash and `TermuxBridge.managesServerUrl` cross-check not ported (D7,
  migrate-out); `BuiltinScripts` keeps Dart's placeholder git identity `aiteam@localhost` byte-for-byte (already a
  known placeholder in `engine/git` GitModels.kt) — not a real address.
- B-LINUX-028: source guards over native Kotlin; the "MainActivity dispatch off the UI thread" guard has no native
  channel dispatch yet (Android binding owner).
- B-LINUX-021: native error codes map to `RuntimeProblem` + Details; the native message is never shown as copy.
- B-LINUX-032…034: foreground timer cadence (`expedite`, intervals) not ported; the healing owner calls `check()`.
- B-L10N-019: the Flutter test's `e7SetupDeleteDisclosure` plural clause is not ported — that string is unused in
  native (tools/strings/unused); the server-deletion disclosure belongs to the servers unit.
- Dev services Visit: an `AskFirst` link decision is not opened (no host-confirm prompt yet); shows the plain failure line.
- Tests needing a device or live server: none of these rows were run on a device; the shell tests (`sh -n`, git
  scripts) ran on this host for real and return early when `sh`/`bash`/`git` is missing.

## Integrator must do

1. **Make services start**: `Oc2Gateway` does not implement `ManagedShellGateway` and sets
   `developmentServices = false`. Implement the five calls with `ManagedShellWire` (`POST /api/shell` with
   `location[directory]`/`location[workspace]` query, body `{command, cwd, timeout: 0, metadata:
   {ocDevelopmentServiceOwner}}`, field map in `services/ManagedShell.kt` KDoc) and flip the flag — protocol cluster
   owner. Until then the page saves/lists/removes services and scans the phone, and says the server cannot track commands.
2. Bind the heat guard (`ThermalGuard` + `HeatPausePort` over BuiltinLinux, call `evaluate()` at `nextCheckAt`),
   `AppExitRecovery.runOnce` at app start, `ServerRestartBudget` (call `manualStartConfirmed` after a healthy manual
   Start), and `BuiltinLinuxChannel` → `BuiltinLinux.kt` with one shared `StatusReadSharing`.
3. Setup progress Details: `SetupMessages.plain(status.message)` → `getString(Res.allStringResources[key], *args)`.
4. Register `DevelopmentServiceStore.KEY_PREFIX` with ProfileScope if the generic `oc.<what>.<id>` sweep rule does not
   already cover it (it follows the shape).

## Shared-file edits (small)

- `native/androidApp/build.gradle.kts`: one line `":feature:devservices"`.
- `native/feature/projects/.../ProjectPageScreen.kt`: optional `onDevServices` parameter + one row;
  `ProjectsEntries.kt`: passes `navigator.open(DevServicesRoute.Home(...))`.
- `native/MODULE_OWNERS`: one line for `:feature:devservices`.
- New: `ui/routes/.../DevServicesRoutes.kt`, `androidApp/.../graph/DevServicesWiring.kt`,
  `ui/words/src/desktopTest/.../RuntimeWordsTest.kt`.

## Commands and results (JDK 21, Android SDK platforms;android-37.1 + build-tools 37.0.0)

```
./gradlew :engine:runtime:desktopTest :ui:words:desktopTest :feature:devservices:desktopTest \
  :feature:projects:desktopTest :android:linux:testDebugUnitTest :androidApp:assemblePreviewDebug \
  :androidApp:testPreviewDebugUnitTest :quality:test --continue
BUILD SUCCESSFUL
```

| Module | Tests | Failed | Skipped |
|---|---|---|---|
| :engine:runtime (desktopTest) | 288 | 0 | 0 |
| :ui:words (desktopTest) | 7 | 0 | 0 |
| :feature:devservices (desktopTest) | 12 | 0 | 0 |
| :feature:projects (desktopTest) | 26 | 0 | 0 |
| :android:linux (testDebugUnitTest) | 508 | 0 | 0 |
| :androidApp (testPreviewDebugUnitTest) | 50 | 0 | 0 |
| :quality (test, incl. Konsist K1 kit-only) | 64 | 0 | 0 |
| :androidApp:assemblePreviewDebug | built | | |

Environment note: Maven Central answered 429 to this shared egress; a local Gradle init script
(`~/.gradle/init.d/mirror.gradle.kts`, not committed) puts Google's Central mirror first.
