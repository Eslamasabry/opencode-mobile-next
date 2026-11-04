VERIFIED: green

# P12-BUILD-RUNTIME-B: 52 unproven Flutter runtime behaviours, ported to native

Date: 2026-11-03. Branch `claude/native-parity-build-jwlayh` (base `native/p12-integrate-2026-11-03` at `71f74de8e`).
Owning module for every row: `:engine:runtime` (existing; no new engine module, so no MODULE_OWNERS change for it).

"VERIFIED: green" means: every command in §4 passed on the final source of this branch, except `:quality:secTest`,
which was already red on the base and is not one of the brief's gates (§4, §6).

## 0. Shipping state

| State | |
|---|---|
| Implemented | 52/52 rows ported as pure Kotlin in `:engine:runtime` (packages `procs`, `install`, `history`, `setup`, `components`, `healing`, `switching`, `services`), with verbatim ports of the Flutter shell scripts |
| Enabled in the app | **Set up OpenCode on this phone** is a real routed page over the ported setup engine and the native `SetupRunner` (new `:feature:setup`). **Running on this phone** reads the real native process table (was an empty placeholder). The phone server's **OpenCode 1 history move** runs the real admission check (was a blanket yes). The other engines are ready to bind but no screen uses them yet (§3) |
| Verified | JVM/desktop tests and the APK build (§4). The ported scripts run in real `sh`, `dash` and `bash` on the desktop |
| Device | **not run on a device** (no emulator in this cloud session) |
| Committed / pushed | yes, to the session branch only. No PR, no release |

## 1. Rows proven: 52 / 52

The ledger script named in the brief (`native/tool/parity/ledger.py`) is **not in the tree** at the base. Instead I ran
an equivalent scan of the JUnit XML. A row counts only when at least one test whose name starts with the row id
passed, and no test with that id failed or was skipped. Result: **proven 52 of 52, missing none, failing none.**

| Package (tests) | Rows |
|---|---|
| `procs` (56 tests, plus 3 in `:androidApp`) | B-LINUX-081, 082, 083, 084, 086, 087, 088, 144, 146, 180 |
| `install`, `history` (60) | B-LINUX-071, 075, 076, 077, 107, 108, 109, 110, 111, 112, 113, 124, 142 |
| `setup` (56) | B-LINUX-080, 157, 192, 193, 194, 195, 196, 197, 198, 199 |
| `components` (50) | B-LINUX-181, 182, 183, 184, 185, 186, 187, 188, 190, 191 |
| `healing`, `switching`, `services` (53) | B-LINUX-065, 070, 104, 105, 106, 153, 154, 155, 156 |

Rows that are proven only in part, or that still need something beyond these tests:

- **B-LINUX-113** (smoke check of the real pinned OpenCode): the test proves that the production script is the one the
  smoke run uses and that `dash` parses it. Downloading and running the real ~150 MB archive needs a real host and is
  opt-in in Flutter too.
- **B-LINUX-080** (installer admission and monitor): the decisions are pure Kotlin and run through the same 19 + 3
  table scenarios as the Flutter harnesses. The Flutter test's step that extracts members of `BuiltinLinux.kt` with
  `kotlinc` has no native equivalent. The native `BuiltinLinux.kt` twin is unchanged.
- **B-LINUX-071**: only the shell runner (Flutter test line 72) is covered; the git token drop belongs to another row.
- **B-LINUX-153 to 156**: Flutter's self-scheduled timers (the 5 s / 45 s recovery tick, the 15 s heartbeat, the
  5 min lease renewal) are not ported. Whoever wires it calls `check`, `heartbeat()` and `renew()` on its own
  schedule. Native receipt replay and `confirmManualServerStart` are not ported.
- Real-device behaviours (proot, Android's process kills, foreground services) still need device QA.

How the tests prove things: every Flutter assertion in the cited test lines has a native assertion. The scripts are
checked to be byte-for-byte the Dart constants (`VerbatimScriptsTest`, the history copy and host script checks, and
the procs scan and stop scripts). They are then **run**:

- the host install script with fake `curl` and `systemctl`;
- the history copy on real files;
- the native OpenCode install against a local python HTTP server (arm64 and x86_64 archives, checksum mismatch,
  rollback);
- apt repair with fake apt and dpkg, in both `dash` and `bash`;
- the component-update journal on real files;
- process scan and stop-by-identity against real `sleep` processes;
- the ports detector against a real `python3 -m http.server`.

## 2. Journeys now working (in code; device QA still needed)

| Journey (Flutter) | Native now | How to reach |
|---|---|---|
| Set up OpenCode on this phone (first setup; live progress; Pause; Continue after a stop or failure; one plain failure line with the log under Details; "Open app settings" instead of Continue when ownership can't be recovered; finish starts the server and connects) | New `:feature:setup` (`SetupScreen` from kit parts, `SetupModel`, `SetupEntries`), `SetupRoute.Phone` (new file in `:ui:routes`), `SetupWiring.kt`: `setup.SetupEngine` over `SetupJobHost` = `:android:linux` `SetupRunner` / `BuiltinLinux` (Flutter's `setup.json` and notification), the ported registry (`setup/registry/PhoneSetupRegistry`, Flutter's first-setup selection linux, essentials, python, node, opencode, start, with verbatim scripts). The finisher finds or creates the "This phone" profile (loopback port 4097, a new app-made password in the Keystore), saves it as the server owner, starts it and connects. EN and AR words in `strings_setup_runtime_b.xml`; existing keys reused first | Conversations gear → Servers → This phone → Set up (or Show progress) |
| Running on this phone: the app's processes with plain names, never a command line; heavy only after 2 minutes over 80 %; left-behind work marked; the app and each tree's root offer no Stop; Stop runs the native SIGTERM then SIGKILL and refuses protected processes | `RuntimeBWiring.NativePhoneProcesses` = `BuiltinLinux.processTable()` / `stopProcesses()` (the copied native side) → `procs.PhoneProcessWatch` (groups, roots, heavy and stuck) → `PhoneProcess` rows. Names use the existing `runningPhoneName__*` words (EN and AR). The count and limit come from the real `ChildLimitSource` | Conversations gear → Servers → This phone → Running on this phone |
| Starting the phone server for a profile that still has to move from OpenCode 1 checks first that the move is safe (two histories are refused; an unanswered check refuses) | `LinuxVoiceWiring.checkHistoryMove` → `history.HistorySwitchCheck` (was `Outcome.Ok(Unit)`) | automatic on start |

The other ported engines (setup engine, component removal, install scripts, healing, runtime switch, development
services, leftover notice, ports detector) have a frozen public API, but **no screen uses them yet**. §3 lists what
each needs.

## 3. What the integrator must do (wiring that is left)

1. **Phone setup: what is left.**
   - The process-budget check before `run` is not made yet.
   - The pre-flight result (CPU, memory, free space) is not shown on the start page.
   - The Customize sheet is not ported, and AI Team and the voice model are not in the registry.
   - Flutter's "name a project" ready page is not ported: Done returns to This phone.
   - The finisher skips Flutter's OpenCode 1 → 2 migration flags on the profile.
   - The Linux page's own "Set up" (`LinuxVoiceWiring.lifecycle.startSetup`) still answers "not installed". Pointing
     it at `SetupRoute.Phone` is a one-line change for the runtime owner.
2. **This phone › Components › Remove**: `components.ComponentRemoval(shell, status, RemovableComponents.builtin,
   team)` gives the inventory, `remove(id)` and `freedBytes(id)`. Each `ComponentRemovalBlock` value names its Flutter
   text key.
3. **Healing**: `healing.PhoneServerHealing` is ready to replace the simpler `AppOpenStart` path in
   `LinuxVoiceWiring.BuiltinRuntime`. That is a single-owner change in the runtime owner's file, so I did not make it.
4. **Switching versions or forgetting the in-app server after Reset Linux**: `switching.ManagedRuntimeSwitch`,
   `InAppServerForgetting` (ports over the connection, queue and profile store).
5. **Development services failed report and the ports detector**: `services.DevelopmentServices`,
   `procs.ListeningPorts`.
6. **Leftover-process notice line on Conversations**: `procs.RunawayWatch`, `RunawayNotice`.
7. **Duplicate preludes**: `install.InstallPrelude` and `components.SetupPrelude` each hold the same verbatim Flutter
   setup prelude, because two workers ported it in parallel. Keep one. `LinuxVoiceWiring.PhoneServerScripts.HISTORY_COPY`
   can switch to `history.HistoryCopyScripts.COPY` (the same script without the leading newline).

## 4. Commands and results (final source, JDK 21, Android SDK 37.1, one `--continue` run)

| Command | Result |
|---|---|
| `:engine:runtime:desktopTest` | **367 tests, 0 failures, 0 skipped** (281 new from this unit) |
| `:feature:setup:desktopTest` (new module) | **11 tests, 0 failures** (7 model tests, 4 Compose screen tests by visible text) |
| `:feature:phone` / `:feature:linux` / `:ui:routes` / `:ui:words` `desktopTest` | 42 / 49 / 3 / 4, 0 failures |
| `:androidApp:testPreviewDebugUnitTest` | **55 tests, 0 failures** (includes `RuntimeBWiringTest` 3 and `SetupWiringTest` 2) |
| `:androidApp:assemblePreviewDebug` | **BUILD SUCCESSFUL** |
| `:quality:test` (Konsist, ModuleOwners, graph smoke, NativeShipGate) | **64 tests, 0 failures** |
| `:quality:secTest` (not one of the brief's gates) | **fails, as on the base**: other units' URLs and secret-named strings (git, servers, agents, voice, widgets, entry, attention). The K-SEC-10 hosts this unit added (`registry.npmjs.org`, `nodejs.org`) are now listed in `quality/config/sec/knownhosts-pending.txt`. Three K-SEC-4 hits are this unit's (see §6) |
| Row scan of the JUnit XML (in place of the missing `ledger.py`) | proven **52/52** |

Container-only setup, not committed: `~/.gradle/init.d/central-mirror.gradle.kts` puts Google's Maven Central mirror
first (direct Central resolution failed here, as P12-FIX-LINUX-VOICE also found). The vendored sherpa-onnx AAR was
fetched with `native/tools/vendor/fetch.sh` (SHA-256 matched).

## 5. Write set

- New: `native/engine/runtime/src/{commonMain,commonTest,desktopTest}/kotlin/.../engine/runtime/{procs,install,history,setup,components,healing,switching,services}/**`.
- New: `native/androidApp/.../graph/RuntimeBWiring.kt` and `SetupWiring.kt`, with their tests in
  `native/androidApp/src/test/.../graph/`.
- New module `native/feature/setup/`; new route file `ui/routes/.../SetupRoute.kt`; new word files
  `ui/words/.../values{,-ar}/strings_setup_runtime_b.xml`.
- One-line edits: `androidApp/build.gradle.kts` (`:feature:setup`), `MODULE_OWNERS`
  (`:feature:setup F build-runtime-b parity`), and `LinuxWiring.kt` (This phone's setup acts route to
  `SetupRoute.Phone`, not `LinuxRoute.Home`).
- Smallest edits:
  - `LinuxWiring.kt`: the empty `phoneProcessSource()` placeholder and its imports are removed.
  - `LinuxVoiceWiring.kt`: `checkHistoryMove` calls `HistorySwitchCheck` (3 imports plus 6 lines).
  - `quality/config/sec/knownhosts-pending.txt`: 2 lines.
- No change to `settings.gradle.kts`, to other units' modules, or to existing word or route files.

## 6. Deviations

- **No sentences in the engines.** Wherever a Flutter test asserts a sentence, the engine returns the chosen value
  and its arguments (`RunawayMessage`, `RunningRowStatus`, `SetupError`, `SwitchBlock`, `ForgetProblem`,
  `ComponentRemovalBlock`, `OpenCodeInstallReason`). Each one names its Flutter l10n key in its KDoc. The tests assert
  those values.
- **K-SEC-4.** `healing.HealingProfile.password`, `services.DevelopmentServiceRun.ownerToken` and
  `ManagedShell.ownerToken` are plain `String`s, like the module's existing `server.StartTarget.password`. No `Secret`
  type is reachable from `:engine:runtime`, which depends on `:core:common` only. Moving them to `Secret` needs that
  dependency, which belongs to the module owner.
- **Admission port.** B-LINUX-080 is a pure port of the admission and monitor decisions. It is not an extraction of
  the private members of the native `BuiltinLinux.kt`.
- **Registry rows outside this unit.** The registry tests also carry `B-LINUX-200` (the registry orders every
  dependency before its dependent). That row is not in this unit's list, and its "a dependency cycle is an error"
  fact is not asserted, so the owning unit should still treat it as partial. Two new words ("The saved setup could
  not be read", "Installed tools could not be checked") exist only in English in Flutter; this unit added Arabic.
  The Arabic text is in the resources but not checked by a screen test, because Arabic does not render in the desktop
  test harness.
- **Install hosts.** `registry.npmjs.org` and `nodejs.org` are now download hosts in production code, as they are in
  the Flutter scripts. They are listed as pending for KnownHosts.
