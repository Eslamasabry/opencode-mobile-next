VERIFIED: green

# P12-BUILD-RUNTIME-C: 53 unproven Flutter runtime behaviours, ported natively

Date: 2026-11-03. Branch: `claude/native-parity-push-build-veo8vn`, base `native/p12-integrate-2026-11-03` (`71f74de8`).

## What a person can now do on the phone

- **Set up OpenCode on this phone (new, real).** This phone › *Set up* (and *Show progress*) used to open the Linux
  page with "not in this build". It now opens `PhoneSetupRoute.Setup` (`:feature:phonesetup`):
  - The start page lists what will be installed and the download size. It runs the preflight first. A 32-bit-only
    CPU is told plainly. Under 2 GB of memory is refused with both numbers, and about 2–3 GB gets a "may be slow"
    note. Low space names how much to free and offers *Open Storage settings* (B-LINUX-209..211).
  - *Set up OpenCode on this phone* plans the real job with `:engine:linuxsetup`. The setup prelude comes from
    `:engine:setupscripts`, and the OpenCode 2 2.2.5 and Node 24.21.0 pins have checksums. The job is handed to the
    same android/linux `SetupRunner` (foreground service, localised notification) the Flutter app used. The page
    then follows `setup.json`.
  - While it runs, the page shows a weighted bar that never moves backwards, the stage, and an ETA only after 10 s of
    real progress (B-LINUX-201). *Stop setup* asks first.
  - The job's *Start OpenCode* step runs the engine's `SetupFinisher` (B-LINUX-205's rules). It creates or reuses
    the phone's OpenCode 2 profile, generates its password, writes the owner key `oc.builtinServerOwner`, and starts
    the phone server through the existing `PhoneServerSource`. It then reports the step to the runner.
  - A failure shows one plain line from `phoneSetupError*`, with redacted Details and *Continue setup*
    (B-LINUX-203, B-SEC-166). Success shows *OpenCode is ready* › *Open*, which goes to This phone.
- **This phone mark (B-NAV-040).** Servers marks only the phone's own server (`http://127.0.0.1:4097`, and the Termux
  one it manages) as *This phone*. A forwarded loopback such as `adb reverse` stays a server.

How to reach them: Conversations › gear › Servers › *This phone* › *Set up* / *Show progress* (for the setup page).
The mark is on the Servers list itself.

## Rows proven: 53 / 53

The repo has no `native/tool/parity/ledger.py` (neither this branch nor `native/p12-integrate-2026-11-03` contains
one). So I counted the same way the ledger is described: a row counts when at least one test whose name starts with
the row id passes and none fails. The count comes from the JUnit XML of the final `--rerun` run.

| Module (new, owner build-runtime-c) | Rows | Tests |
|---|---|---|
| `:engine:linuxsetup` | B-LINUX-200..211, B-SEC-166, B-SEC-170 | 56 |
| `:engine:setupscripts` | B-LINUX-212..215 (real dash/bash, loopback HTTP server, apt stubs) | 16 |
| `:engine:setupassist` | B-PROTO-104..107, B-SEC-165, B-SEC-167..169, B-STATE-130..133 | 53 |
| `:engine:termuxhost` | B-LINUX-227..230, 232, 241..244, 246, B-SEC-092 | 101 |
| `:engine:linuxtools` | B-LINUX-221, 231, 239, 247, 248, B-SEC-022 | 74 |
| `:engine:lifecycle` | B-STATE-025, 027, 028, B-SEC-011, 012, B-NAV-040 | 31 |
| `:feature:phonesetup` | journey tests for B-LINUX-201, 203, 209..211, B-SEC-166 (model + Compose by visible text, EN + AR) | 17 |
| `:feature:servers` | B-NAV-040 on the real Servers screen | 2 new (73 in module) |

Every row id has at least one passing test, and none has a failing one.

**Partly ported (a passing test names the row, but not every Flutter fact is ported):**

- **B-LINUX-215:** "every component script parses in dash and bash" covers the scripts in `:engine:setupscripts`.
  It does not yet loop over the full component list in `:engine:linuxsetup`.
- **B-LINUX-227:** not ported:
  - the AI Team script parts (`aiteam.sh`, upstream pins, team runtime dispatch);
  - the user-namespace scenarios, which need unprivileged user namespaces.
  - Syntax and hash snapshots of 19 Termux scripts are proven.
- **B-LINUX-229:** not ported: the two widget facts (the first screen after a storage reset, This phone while Termux
  is unreachable). Termux is migrate-out only (plan 01 D7). Cause mapping, EN/AR words and the remembered "no" are
  proven.
- **B-LINUX-230:** not ported: the `TermuxHostSetup` controller. The script facts and a pure install-plan decision
  are proven.
- **B-LINUX-241:** not ported: `termux_setup_finish_test` lines 180 and 221. The 14 `switch_runtime` tests run the
  real manager.
- **B-LINUX-208:** not ported here: the Kotlin `SetupRunner` persistence half (`setup_runner_native_test`), which
  lives in `:android:linux`. The Dart-side mapping is proven.
- **B-LINUX-221:** not ported: the native manifests do not declare `MANAGE_/READ_/WRITE_EXTERNAL_STORAGE`. That is
  the integrator's call; see below.
- **B-SEC-012:** the drain, abort and retry state machine is ported. The Termux restart loop is not.

Rows that need a device or a real server for full proof: B-LINUX-201/203/205 end to end (a real `SetupRunner`
download and start on a phone), and B-STATE-025 (real health probes after an Android kill).

## Commands and results (final run, head `4bd7636b`, all green)

| Command | Result |
|---|---|
| `:engine:linuxsetup:desktopTest` | 56 tests, 0 failures |
| `:engine:setupassist:desktopTest` | 53 tests, 0 failures |
| `:engine:termuxhost:desktopTest` | 101 tests, 0 failures |
| `:engine:linuxtools:desktopTest` | 74 tests, 0 failures |
| `:engine:lifecycle:desktopTest` | 31 tests, 0 failures |
| `:engine:setupscripts:desktopTest` | 16 tests, 0 failures |
| `:feature:phonesetup:desktopTest` | 17 tests, 0 failures |
| `:feature:servers:desktopTest` | 73 tests, 0 failures |
| `:androidApp:testPreviewDebugUnitTest` | 50 tests, 0 failures |
| `:androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL (`androidApp-preview-debug.apk`, 87 MB) |
| `:quality:test` (Konsist K1/K13, ModuleOwnersTest, graph smoke) | BUILD SUCCESSFUL |
| `native/tool/scan_push.sh` on every pushed range | clean |

All tests above were run with `--rerun` in one invocation. `testDebugUnitTest` does not exist for these KMP engine
modules: they have no Android host-test source set (commonMain only, ADR §7.2). Their Android side compiles into the
APK.

Environment notes: JDK 21. Android SDK `platforms;android-37.1`, `build-tools;37.0.0`. A local, uncommitted Gradle init
script puts Google's Maven Central mirror first, because Central answered 429. `tools/vendor/fetch.sh` fetched
sherpa-onnx.

## Incident: `/etc` wiped by a test (fixed)

At about 15:24 UTC a ported test (`AppStorageFactsTest`) deleted the container's `/etc`:
- **Cause:** the Flutter fixture has a symlink `projects/web/outside -> /etc`, and the cleanup used Kotlin's
  `File.deleteRecursively()`, which follows directory links.
- **Fix:** every test in these modules now uses a delete that never follows links. Fixtures point their outside link
  at a separate temp folder holding a sentinel file, and the test asserts the sentinel survives.
- **Script runs are hermetic:** `Shell.run` clears the environment, refuses an empty or `/` HOME/PREFIX/TMPDIR, and
  puts PATH-first guard stubs in front of `rm`/`mv`/`chmod`/`apt`/`dpkg`/`pkill`/… so nothing outside the temp folder
  is touched.
- **Restore:** the worker restored `/etc` from the Ubuntu packages and rebuilt passwd/group. Four packages could not
  be downloaded (containerd.io, docker-ce, libpython3.11-minimal, libpython3.13-stdlib). Hand-edited image files
  under `/etc` are lost. This affected only this cloud container, not the repository.

## Deviations

- **New modules instead of `:engine:runtime`.** The catalog names `:engine:runtime` as the owner. I made six new
  engine modules so five parallel ports could build without breaking each other or the other runtime sessions'
  `:engine:runtime` work. They depend on `:core:*` only, so they sit outside the ordered engine list. All are in
  `MODULE_OWNERS` (owner build-runtime-c), with `:feature:phonesetup`.
- **Setup page shape:** one route, `PhoneSetupRoute.Setup`, instead of Flutter's separate start and progress
  screens. It shows the start until a job exists, then that job. Both `PhonePage.SetupStart` and
  `PhonePage.SetupProgress` lead to it.
- **Not ported (non-goals):** the customise sheet (choosing optional components) and the "name your first project"
  ready screen. *Set up* installs the required and default-on components: Linux base, Git/SSH, Python, Node.js,
  OpenCode, Start.
- **Setup assistant copy:** English only, fixed strings ported verbatim from the Dart code. They were not in
  `app_en.arb`. There is no screen for the setup assistant yet.
- **Ports instead of real HTTP:** the setup assistant's HTTP goes through `SetupHttpClient` and `RegistryHttp`. No
  real client is bound yet; see the integrator items.
- **Termux:** Termux rows are engine-level parity only. No Termux screens, because Termux is migrate-out only
  (plan 01 D7).

## What the integrator must do

1. Review the smallest-edit touches outside the write set:
   - `feature/servers` `ServersScreen.kt`: the This phone line, plus `:engine:lifecycle` in its `build.gradle.kts`;
   - `LinuxWiring.kt`: the `PhonePage.SetupStart/SetupProgress` → `PhoneSetupRoute.Setup` mapping;
   - `androidApp/build.gradle.kts`: four lines;
   - `ui/routes`: the new `PhoneSetupRoutes.kt`.
2. `LinuxVoiceWiring.LinuxLifecycle.startSetup()` and `PhoneServerSource.updateOpenCode()` still answer "not
   ported". The *Repair* and *Update OpenCode* rows can now run the setup engine (`SetupEngine.run(setOf("opencode"))`
   with the update params). That is a follow-up for the runtime owner.
3. **Setup assistant bindings:**
   - bind `SetupHttpClient` over the OC2 transport and an OkHttp `RegistryHttp` (no redirects, 512 KiB cap, 15 s
     timeouts);
   - register `SetupProfileKeys.prefixes` with ProfileScope;
   - call `SetupAuditRegistry.closeProfile(id)` and `SetupRegistryStore.close()` before the deletion sweep.
4. **Lifecycle bindings:**
   - bind `LifecycleReportController` (`:engine:lifecycle`, B-STATE-025/027) to the restart notice;
   - wire the deletion drains (B-SEC-011/012) into `deleteProfileAndLocalData`.
5. **B-LINUX-221:** decide whether the native manifest declares the shared-storage permissions the Flutter app did.
6. **Ledger:** `native/tool/parity/ledger.py` does not exist. Point the parity count at these test names when it
   lands.

## Commits

`3aee373a` setupassist + scaffolds · `bd34a2fc` checkpoint (includes the final linuxtools port) · `30843b02`
linuxsetup, lifecycle, setupscripts, Servers mark · `c7379116` termuxhost · `4bd7636b` setup journey, wiring,
scan-safe fixtures · this report.
