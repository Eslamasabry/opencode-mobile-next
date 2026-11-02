# Unit P12-LINUX: the phone's Linux manager and the This phone pages

Date: 2026-11-02 (parity push for 2026-11-03). Branch pushed: `claude/native-parity-linux-runtime-nkg30c`. The session
was told to push only to that branch; the brief named `native/p12-linux-2026-11-03`. Base: `native/m1-2026-11-03` at
`8a4eee69`.

## 0. Status, stated plainly

| State | What |
|---|---|
| **Implemented** | `:feature:linux` (new): the Linux manager engine, its models, four routed screens, ViewModels, entries and bindings. `:feature:phone` wired: entries, routes and a "Linux" row. A "This phone" row on Servers. `LinuxWiring.kt` in androidApp. |
| **Verified** | Only the pure Kotlin: **80 JVM tests, 0 failures** (42 Linux, 38 phone), run in a scratch JVM build that compiles `:core:common`, `:engine:runtime`, the Linux host/manager/presentation code and the phone presentation code from this tree (§4). |
| **Not verified** | **Nothing Android or Compose was compiled.** This session's network policy refuses `dl.google.com`, so neither the Android SDK nor Google Maven (AGP 9.3.3, `androidx.*`) can be downloaded, and no module of `native/` configures. Screens, ViewModels, entries, Metro wiring and `LinuxWiring.kt` were written against the source signatures but never compiled. `:androidApp:assemblePreviewDebug` was **not** run. |
| **Enabled on the phone** | Reachable: Conversations gear → Servers → **This phone** → **Linux** → Ports / Storage / Packages / Running. **Not working end to end**: the built-in Linux cannot be in the APK yet (§3), so the Linux pages say "Linux is not set up or is not answering" and This phone says it is not set up. Keep running and the child-process-limit card are real. |
| Committed / pushed | Yes, 3 commits plus this report. Not deployed, not released. |

## 1. Journeys

| Journey (Flutter) | Native now |
|---|---|
| Open This phone (settings) | Servers page → "This phone" row (only when `PlatformCapabilities.builtinLinux`) → `PhoneRoute.ThisPhone`. |
| This phone → Running on this phone / Keep running | `PhoneRoute.Running`, `PhoneRoute.KeepRunning` (S3-PHONEUI screens, now routed). Keep running opens the real Android battery and app pages. |
| This phone → Linux manager | New "Linux" row (`linuxMgrRow`) → `LinuxRoute.Home`. |
| Linux manager home: the job panel, Running, Ports, Storage, Packages, Repair, Reset | Done (`LinuxHomeScreen`). Repair and Reset check first that setup or a package task is not running, and say so plainly if one is. Repair asks first. Reset asks with the consequences and the typed word "Reset", then hands off to This phone. |
| Ports with preview | Done. A port is listed only when a loopback connection to it worked. The row says who uses it and whether only this phone can reach it. "Open preview" opens `http://127.0.0.1:<port>/` through `ExternalLinkPolicy` (AppAuthored). The server and Paseo show "Not a web page". |
| Storage | Done: total, six categories, four safe cleanups with the size each would give back, a confirm, then "Freed …". |
| Packages: search, install by name, remove, update system, one long job with stop | Done. Typed names are validated before anything runs. Install checks what apt knows first ("No package named …", "… is already installed"). Remove first reads apt's plan and refuses one that would take something Linux needs. The job shows its percent and a Stop that names it, keeps going when you leave the page, and says how it ended. |
| Reset interrupted by a kill | `LinuxResetFlow.resumeIfPending()` is ported and tested. **Not yet called at app start** (§5 item 4). |
| Services, Backups with restore, Vitals strip, start/stop of the phone server, install/update of OpenCode and agents, phone setup A/B/C | **Not done in this unit.** The rows are left out rather than shown dead. The server and update rows on This phone are S3-PHONEUI's and work once a real `PhoneServerSource` is bound. Setup, backups and services need the runtime engine and the setup engine, which are not in the tree (§3). |

## 2. Catalog rows

Covered by tests named after the row:

- B-LINUX-085: ports parsing, owners, reach, preview only for pages, a failed open said. Engine, model and screen tests.
- B-LINUX-089: storage categories, freed bytes never negative, safe paths, cleanup scripts stay in their paths, measure → confirm → freed. Engine, model and screen tests.
- B-LINUX-090: name validation, installed list with locked packages, candidates, removal plans, the job frame and its budget, unknown/install/blocked-remove flows. Engine, model and screen tests.
- B-LINUX-091: job log states (running/percent, done, failed, timed out, busy, interrupted), one job at a time, stop asks first. Engine and model tests.
- B-LINUX-092: passing ownership/slot failures retried twice, then "busy"; not installed said at once; the page says when Linux is not here. Engine, model and screen tests.
- B-LINUX-093: the reset marker is saved before erasing; a refusal changes nothing; partial erase still sets up; a setup that cannot start keeps the marker; resume from both marker phases; installed again ends quietly; no marker means nothing to do. Repair and reset are guarded and ask first.
- B-SEC-096 (Linux parts): the probe script holds only numbers, package scripts refuse non-names, cleanups name only safe paths. "Every script parses as shell" (`sh -n`) is a desktop test.
- PB-42 for `LinuxTimers`.

Still missing: B-LINUX-082/083/084 (the Flutter "Running in Linux" process list with grouping and stop-by-identity: the native Running page is S3-PHONEUI's `PhoneProcessSource`, which needs the runtime), B-LINUX-086/087/088 (the shared Development services port detector), B-LINUX-060…065 (services), the backup rows, B-L10N-011 (reset leftover sentence: the leftover cleanup is not ported).

## 3. Why the Linux pages cannot work on the phone yet (blockers, not workarounds)

1. **`:android:linux` cannot join `:androidApp`.** Its S1 shims declare `io.github.eslamasabry.opencode_mobile.MainActivity`, `R` and `BuildConfig`, the same FQCNs as the app. S1-LINUX P1 says not to add it until S3-RTSPLIT replaces them. That module is read-only for this unit.
2. **No proot/loader `jniLibs`** are in the module (S1-LINUX P3), so even a joined module could not start Ubuntu.
3. **No setup engine or runtime host** (`RuntimeHost`/`AndroidRuntimeHost`, S3-RTENG/S3-RTWIRE) exists yet for start/stop, setup, repair or backups.

So `LinuxWiring` binds `AbsentLinuxHost(NotOnThisBuild)`, a lifecycle that answers `Failure.Runtime(NotInstalled)`, a `PhoneServerSource` whose status is `installed = false`, and an empty process reading. That is the honest state, not a fake: nothing pretends to run.

## 4. Tests and results

The scratch JVM build is in the session scratchpad and is not committed. It compiles these directories straight from the tree with Kotlin 2.4.21, coroutines 1.11.0 and Metro runtime 1.5.1:
`core/common/src/{commonMain,desktopMain}`, `engine/runtime/src/commonMain`, `feature/linux/src/commonMain/.../{host,manager,presentation}` (minus `LinuxViewModels.kt`), `feature/phone/src/commonMain/.../presentation` (minus `PhoneViewModels.kt`), plus `feature/linux/src/{commonTest,desktopTest}` and `feature/phone/src/commonTest` (minus the two Compose screen tests).

| Suite | Tests | Result |
|---|---:|---|
| LinuxManagerParseTest | 17 | pass |
| LinuxManagerTest | 6 | pass |
| LinuxScriptsShellTest (desktop, `sh -n`) | 1 | pass |
| LinuxResetFlowTest | 4 | pass |
| LinuxHomeModelTest | 5 | pass |
| LinuxPagesModelsTest | 7 | pass |
| LinuxSizeTest, LinuxTimersTest | 2 | pass |
| Phone: ThisPhoneModelTest (now also asserts "open Linux"), RunningModelTest, KeepRunningModelTest, PhoneServerStateTest, PhoneTimersTest | 38 | pass |
| **Total** | **80** | **0 failures** |

Written but **not run**: `feature/linux/.../LinuxScreensTest.kt` (5 Compose tests by visible text, same harness as `PhoneScreensTest`). Also not run: `:quality:test` / Konsist, `ModuleOwnersTest`, and every Android task.

## 5. What the integrator must do

1. **Build on a machine with Google Maven**: `:feature:linux:desktopTest :feature:phone:desktopTest :feature:servers:desktopTest :ui:routes:desktopTest :androidApp:assemblePreviewDebug :quality:test`. Fix any compile slip in the Compose, Metro or wiring files; their logic is covered by the JVM tests above.
2. **MODULE_OWNERS** (coordinator file): add `:feature:linux	F	P12-LINUX	parity` and `:feature:phone	F	S3-PHONEUI	slice-3`. `ModuleOwnersTest` fails until then.
3. **Pacer**: `LinuxWiring.pacer()` binds `DefaultPacer(Foreground)` because nothing else bound one (S3-PHONEUI R3). If another unit or the shell binds `Pacer`, delete this one, or Metro reports a duplicate binding.
4. **Reset resume at start**: call `LinuxResetFlow.resumeIfPending()` once after launch (app-start owner) when a real lifecycle is bound.
5. **Swap in BuiltinLinux** once S3-RTSPLIT removes the FQCN shims: add `:android:linux` to the app list and replace `linuxHost()` and `linuxLifecycle()` in `LinuxWiring` with an adapter along these lines (blocking calls on `AppDispatchers.io`; exception text only classifies, it is never shown):

```kotlin
class BuiltinLinuxHost(private val linux: BuiltinLinux, private val io: CoroutineDispatcher, private val files: File) : LinuxHost {
    override suspend fun presence() = if (linux.installed) LinuxPresence.Ready else LinuxPresence.NotInstalled
    override suspend fun run(script: String, timeoutSeconds: Long) = withContext(io) {
        attempt(::classify) { linux.run(script, timeoutSeconds).let { ShellRun(it.exitCode, it.output) } }
    }
    override suspend fun setupRunning() = withContext(io) {   // files/linux/setup.json, as setupStatus reads it
        File(files, "linux/setup.json").takeIf { it.isFile }?.readText()?.contains("\"state\":\"running\"") == true
    }
    override suspend fun serviceRunning(name: String) = linux.serviceRunning(name)
    override suspend fun startService(name: String, script: String, notice: String) =
        withContext(io) { attempt(::classify) { linux.startService(name, script, null, notice) } }
    override suspend fun stopService(name: String) = withContext(io) { attempt(::classify) { linux.stopService(name) } }
    override suspend fun serviceLog(name: String, tailBytes: Int) = withContext(io) { linux.serviceLogTail(name, tailBytes) }
    private fun classify(e: Throwable): Failure = Failure.Runtime(
        when {
            e.message == "ownershipUnknown" || e.message == NativeRuntimeOwnership.UNKNOWN_CODE -> RuntimeProblem.OwnershipUnknown
            e.message?.startsWith("Another phone task") == true -> RuntimeProblem.SlotBusy
            e.message?.startsWith("Ubuntu is not installed") == true -> RuntimeProblem.NotInstalled
            else -> RuntimeProblem.Generic
        }, Redacted.shape(e))
}
// LinuxLifecycle: installed() = linux.installed; erase() = attempt { linux.uninstall() } (projects stay);
// startSetup()/repair() = the setup engine's run(firstSetupSelection) / check-and-fix once it exists.
```

   The `PhoneServerSource`, `PhoneProcessSource` and `ChildLimitSource.lastAndroidKill/queuesOften` bindings move to the runtime host (S3-PHONEUI R3) in the same change.
6. **Strings**: every sentence is an existing `:ui:words` key (en and ar were both checked for every key used). Proposed new words for `:ui:words`, to replace today's nearest key (`linuxMgrProblemUnavailable`):
   - "Linux on this phone isn't in this preview yet." (for `LinuxPresence.NotOnThisBuild`)
   - "Setting Linux up again couldn't start. It tries again the next time the app opens." (for `ResetSetupPending`)

## 6. Deviations

- **Port owners**: the port script also prints `::c <pid> <cmdline>` for the programs that hold sockets, and the owner is the highest pid that names the port. Flutter matches against its full process scan by start time. The process scan itself was not ported (see §2).
- **Package list**: shows the first 60 rows with "Show all N packages", so a long list in one column stays light.
- **This phone's setup acts** (`SetupStart`/`SetupProgress`) route to the Linux page, which says plainly that Linux is not set up, because the setup pages do not exist yet.
- **Small edits outside the unit folder** (each the smallest that works): `ui/routes` (two new route files), `feature/phone` (new `PhoneEntries.kt`, `PhonePage.Linux`, `openLinux()` with a default body, one row and one test line), `feature/servers` (an optional `onThisPhone` parameter and one row; `ServersEntries` now injects `AppNavigator` and `PlatformCapabilities`), and `androidApp/build.gradle.kts` (3 lines: `:engine:runtime`, `:feature:phone`, `:feature:linux`).
- The preview app keeps the reset marker and the child-limit Hide time in the `oc_native_linux` SharedPreferences file, with the Flutter key names. Migration from the Flutter store is the migration unit's job.
- `127.0.0.1` appears in code only as the app-authored loopback target of the port probe and the preview.
