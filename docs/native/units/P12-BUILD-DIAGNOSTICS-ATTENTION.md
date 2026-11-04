VERIFIED: green

# P12 BUILD-DIAGNOSTICS-ATTENTION

Branch `claude/native-parity-push-build-50841q`, based on `native/p12-integrate-2026-11-03` (71f74de8).

## Rows proven: 55 of 55

Every row has at least one passing test whose name starts with the row id (229 row-named tests). The modules' other
tests still pass.

`python3 -I native/tool/parity/ledger.py` **does not exist on this base**, so the count was made from the JUnit XML
results instead. The script reads `engine/attention`, `engine/diagnostics` and `feature/diagnostics` test results and
counts a row only when every test that names it passed:
`55 / 55, missing []`. Rerun the ledger at integration to confirm.

| Group | Rows | Owning module | Package |
|---|---|---|---|
| Persisted problem report | B-STATE-070, 071, 072, 073, 074, 075, 077, 079, 080, B-SEC-148 | :engine:diagnostics | `report/` |
| Crash reports, preview and share | B-SEC-042, 043, 044, 046, 047, 048, 049, 050, 051, 052, 053, 054 | :engine:diagnostics (+ :feature:diagnostics) | `crash/`, `crashreport/` |
| Device diagnostics | B-SEC-059 | :engine:diagnostics (+ :feature:diagnostics) | `device/` |
| Problem report and GitHub link | B-SEC-126, 127, 128, 129 | :engine:diagnostics | `problem/` |
| Failed setup job report | B-AGENT-108 | :engine:diagnostics | `job/` |
| Automatic activity and Undo | B-STATE-006, 007, B-SEC-015, 016, B-AGENT-030 | :engine:attention | `activity/` |
| Elsewhere attention and the attention feed | B-AGENT-077, 078, 079, B-STATE-010 | :engine:attention | `elsewhere/`, `feed/` |
| Run identity | B-PROTO-127 | :engine:attention | `identity/` |
| Notification text | B-SEC-105, 106 | :engine:attention | `notify/` |
| Widget snapshot | B-SEC-181, 182 | :engine:attention | `widget/` |
| Profile monitor, reader, check-ins | B-AGENT-138, 139 (amended text), B-ANDR-056 to 064 | :engine:attention | `monitor/` |
| Live status | B-ANDR-032, 033 | :engine:attention | `live/` |

## Journeys now working on the phone

The persisted report, the crash store and exit recovery are started once at launch by `OcApplication` through
`DiagnosticsAttentionStartup`.

1. **Report a problem keeps errors across restarts.** Errors and timings are saved to app-private files under
   `filesDir/diagnostics` as they happen. The Settings report lists the saved errors, so they still show after the app
   is killed and reopened. Clear erases memory, the saved file and any half-written file.
   - Reach it: Settings › Help › Report a problem.
2. **Send as a GitHub issue.** The report is built with secrets masked and no server address or name. It goes through
   ExternalLinkPolicy and the app's ExternalLinkOpener.
   - A short report is prefilled whole in the GitHub form link.
   - A long report has its diagnostics, or the whole report, copied to the clipboard first, and a toast says to paste.
   - Nothing is filed until the person submits GitHub's form.
   - Reach it: Settings › Help › Report a problem › Send as a GitHub issue.
3. **Everything this phone kept.** A new screen in the new module `:feature:diagnostics`, route `DiagnosticsRoute.Kept`.
   - Reach it: Settings › Help › Report a problem › Everything this phone kept.
   - **Errors kept on this phone:** the count, with Clear behind a confirmation.
   - **Crash reports:** "Save crash reports on this phone" is off by default. Turning it off deletes saved reports.
   - **Saved reports:** each shows its time and category only. Delete sits behind a confirmation.
   - **Preview crash report:** shows the exact text first. Share report then opens the share sheet with that text only.
     A stale preview is refused, a second tap while sharing does nothing, and every failure is one fixed plain line.
   - **Recent app exits:** Android's ApplicationExitInfo history (API 30+), read fresh each time. Before Android 11 the
     screen says "Not available on this phone".
4. **Framework errors keep only their category.** The default uncaught-exception handler records the error's category
   in the diagnostics ring, and from there in the saved report. It then hands Android a scrubbed error.

All copy comes from existing `:ui:words` strings, in English and Arabic. No new strings were needed.

## Not reachable yet

These are engine-proven only. The rows pass, but no screen or service uses the new engines yet.

- **Attention, all of `:engine:attention`.**
  - **What is missing:** the profile monitor, check-in reminders, live status, automatic-activity history, elsewhere
    attention, the attention feed and the widget snapshot writer are not wired into the app.
    `:engine:attention` is not in the app's module list yet.
  - **Why:** `:android:notifications` and `:android:widgets`, owned by other units, already have their own
    notification-text, alert, status-row and widget code. Moving them onto these engines means editing those modules,
    which are outside this unit's write set. The monitor also needs a gateway adapter, the automation-policy store and
    Wi-Fi checks from the connection layer.
  - **Ports to implement:** the forks' reports list them; `MonitorPorts.kt` and `AttentionKeyStore` hold the interfaces.
- **Failed setup job report (B-AGENT-108).** The runtime's setup progress still has to be mapped to `SetupJobSnapshot`
  and attached when Report is tapped from a failed Linux setup.
- **Helper deaths (part of B-SEC-059).** DeviceDiagnostics takes a `HelperServiceSource` from the built-in Linux
  performance read, which the app graph does not expose yet. The app passes `null`, so only Android's exits show.
- **JVM crash records.** The app's uncaught handler records the category in the ring and the saved report.
  `CrashStartupEvidence.platformCrash` (android/platform's `NativeCrashStore`) is not wired because `:android:platform`
  is not in the app. ANR and native crashes still import from ApplicationExitInfo.

## Needs a device or real server

- Exit history, share sheet, clipboard and toast: checked by compile only. They are Android APIs.
- On a device: crash capture across a real kill, ANR import, and the GitHub form opening in a browser.
- Monitor and live-status rows run fully on fakes. Once wired, they need on-device checks of Wi-Fi detection, the
  notification shade and the background service.

## Deviations

- **Exit message:** the saved exit entry keeps only numbers and the kind, never Android's description text.
- **Thermal readings:** they arrive only through `ReportProblemSession.recordThermal`. Nothing feeds it yet, since the
  app has no thermal source.
- **Crash source:** it is a fixed enum, so Flutter's free-text "private-source" case cannot happen natively.
- **B-SEC-043 symlink case:** tested at engine level as "a refused write fails closed". `AndroidCrashStore` and
  `AndroidReportFiles` refuse symbolic links. android/platform's `NativeCrashStoreTest` names the real check.
- **Failed job kinds:** only the setup kind is ported. The team, gate and dev-service kinds are not.
- **B-AGENT-139:** follows the amended text in `native/catalog/scope.jsonl`. The team-link projections are not ported.
- **Live status:** composed only for needs-you, working, several-working and ready. The other states stay in
  `:android:notifications`.
- **Toast:** Send as a GitHub issue shows its "copied" notice as an Android Toast. The Settings report page has no
  notice slot, and adding one would mean a larger edit to `:feature:settings`.

## Shared-file edits, all small

| File | Change |
|---|---|
| `native/androidApp/build.gradle.kts` | One line: `":feature:diagnostics"` |
| `native/androidApp/.../OcApplication.kt` | One line: start `DiagnosticsAttentionStartup` |
| `native/androidApp/.../graph/SettingsWiring.kt` | Inject `GitHubProblemReporter`; open the two new places; the report reads the saved errors |
| `native/feature/settings/.../SettingsPages.kt` | `SettingsOutside.ReportOnGitHub` and `KeptDiagnostics` |
| `native/feature/settings/.../LanguageReportPages.kt` | Two rows under the report text |
| `native/engine/diagnostics/.../Diagnostics.kt`, `perf/PerfTrace.kt` | Internal `onRecorded` hooks |
| `native/engine/attention/build.gradle.kts` | `implementation(project(":core:security"))` |
| `native/MODULE_OWNERS` | `:feature:diagnostics` owned by `build-diagnostics-attention` |

New files:
- `graph/DiagnosticsAttentionWiring.kt` and `graph/DiagnosticsAttentionReportFiles.kt`
- `ui/routes/.../DiagnosticsRoutes.kt`
- everything under `feature/diagnostics/`
- the new packages in both engines

## Commands and results

Run on JDK 21, with the Android SDK at platform 37.1 and build-tools 37.0.0. Maven Central returned 429 errors, so a
local-only `~/.gradle/init.d/mirror.gradle` put Google's Maven Central mirror first. It is not committed.
`tools/vendor/fetch.sh` fetched sherpa-onnx.

| Command | Result |
|---|---|
| `./gradlew :engine:attention:desktopTest` | 130 tests, 0 failed |
| `./gradlew :engine:diagnostics:desktopTest` | 156 tests, 0 failed |
| `./gradlew :feature:diagnostics:desktopTest` | 15 tests (11 presenter + 4 Compose screen), 0 failed |
| `./gradlew :feature:settings:desktopTest` | 80 tests, 0 failed (after the Report page edit) |
| `./gradlew :quality:test :androidApp:testPreviewDebugUnitTest` | 114 tests, 0 failed |
| `./gradlew :androidApp:assemblePreviewDebug` | rc 0, including the native-library verify step |

## For the integrator

- Run the parity ledger when `native/tool/parity/ledger.py` lands.
- Decide who wires `:engine:attention`: the notification and widget owners, or a follow-up of this unit with write
  access to `:android:notifications` and `:android:widgets`.
- Register the per-profile keys `oc.automaticActivity.<id>`, `oc.widgetTarget.<id>` and
  `ProfileMonitor.profileScopedKeys(id)` with ProfileScope when those engines are wired.


## Integration conflict repair — 2026-11-04

Compile-only verification of unit `ecde9a395c9744c99ca20be287d740c4b74238d4` merged with integration head
`526ea644bfab1cd1c74aa4b4d6caadcd73ff25ef` on `p12x/diagnostics-attention-ecde9a3`.

- Resolved both conflicts: `native/MODULE_OWNERS` retains every ownership row from both parents;
  `SettingsWiring.kt` retains Background navigation alongside kept diagnostics and GitHub reporting.
- Checked the app project dependency union: no dependencies from either parent were lost.
- Removed the extra blank line at the end of this unit's `DiagnosticsAttentionWiring.kt`.
- Fetched the ignored sherpa-onnx AAR with `native/tools/vendor/fetch.sh`; its pinned SHA-256 passed.
- No tests were run, weakened or deleted. No other unit's behavior was changed.

Ran in `native/`, under `/home/eslam/Storage/tmp/oc-build.lock`:

```bash
export JAVA_HOME=/home/eslam/Storage2/tools/jdk21 ANDROID_HOME="$HOME/Android/Sdk"
nice -n 5 ./gradlew --console=plain --continue \
  -Dorg.gradle.workers.max=2 -Dorg.gradle.daemon.idletimeout=300000 \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  :androidApp:assemblePreviewDebug compileKotlinDesktop compileTestKotlinDesktop
```

Result: **BUILD SUCCESSFUL**, exit 0 in 2m 46s. **3/3 requested targets passed**, including preview APK native-library
verification. **1,238 actionable tasks: 911 executed, 327 from cache; 0 failed; 0 tests run.**
The log contains 0 Kotlin compiler errors and 200 Kotlin compiler warnings (warnings are not errors in this gate).
The three diagnostics/attention modules' desktop production and test sources compiled. This is compile evidence only;
the earlier test results above are historical and were not rerun during this repair.

Local build log: `/tmp/fx-diagnostics-attention-compile.log`. No push, release or device qualification performed.
