# BD7 — real device crash and ANR qualification

Status: BLOCKED — real exits qualified; saved-report capture and preview remain
unqualified on APK2196. Do not mark this sequence complete.

Finish line: a real Android app-process fatal crash and OS-classified ANR appear
in Recent app exits and the enabled private crash-report flow after restart,
with the normal app restored inside the emulator lock. Product UI/native/Dart
changes, synthetic records, account access and public release are outside this
qualification slice.

Branch `sol/bd-bd7` starts at `feat/genui-fe` `f0e33d96d`. The coordinator
explicitly superseded the original2197 dependency with2196. The tested APK is
`/home/eslam/Storage/tmp/oc-apk-share/oc-2196.apk`, source `d777082c`, SHA256
`78074bc8c8f3244b252ae11f23cb3dc4d748b989c95c33e25111f6c16716af56`, signer
`1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.
It includes capture `dcbd1771`, consent UI `c09fd449` and exit rows `8644d15d`.
**Exit-list ordering change `d55ed022` is not in2196 and is not qualified here.**

## Device results

All sessions used only `emulator-5554`, held
`/home/eslam/Storage/tmp/oc-emulator.lock` through restoration, and checked
`df -k /data` before app operations. Proof-session storage was79% used with
1,750,548KiB available. Every retained session receipt reports a successful
same-lock2196 reinstall with `adb install -r -d`, live process, resumed
MainActivity and first frame. Diagnostic rollback also passed.

| Check | Evidence | Result |
| --- | --- | --- |
| Real Android JVM fatal crash | exact main PID8544, OS reason4, status0; [row](device/exits-native-exit.jpg) | PASS |
| Real input-dispatch ANR exit | exact main PID9244, OS reason6, status0; [Android dialog](device/exits-anr-dialog.jpg), [row](device/exits-anr-exit.jpg) | PASS |
| Consent switch OFF → ON | production switch tap, persisted positive consent epoch; [before](device/attempt5-consent-before.jpg), [on](device/attempt5-consent-on.jpg) | PASS |
| Saved fresh native/ANR report and preview after restart | no complete qualified report/preview receipts | BLOCKED |
| Original diagnostics and normal2196 restoration | [proof-session restore](device/exits-normal-restore.json), [last restore](device/attempt6-normal-restore.json) | PASS |

The [exits receipt](device/exits-report.json) is explicitly `PARTIAL`, with
`saved_report_qualified:false`. Android's actual exit history matched each
verified main-process PID and numeric reason. Both UI rows displayed the
matching reason under Details. Screenshots were inspected and contain only
fixed diagnostics or the system ANR dialog. The Java/Kotlin fatal case uses
`am crash --user 0 <verified-pid>` and Android's real fatal handling; it is not
C/SIGABRT/NDK qualification. The ANR case suspends only the verified main PID,
injects a harmless diagnostics tap, waits for Android's dialog and selects
Close app. A force-stop or SIGKILL is never counted as an ANR.

The [executed exit-only recipe](device/exit-only-driver.py) intentionally bypassed
saved-report qualification to complete the OS/UI portion after unavailable
capture was observed. It uses the common exact-PID/start/UID guards, private
rollback and same-lock normal restore. It does not enable consent or manufacture
crash records. Its fixed output directory and worktree path document this run.

## Qualification gap and next step

On multiple launches, the production consent row was disabled and displayed
“Crash reports aren't available right now. Restart the app and try again.”
A [fresh-restart check](device/restart-report.json) confirmed both conditions.
One exit-only launch did not show that notice, so availability is intermittent.
Consent OFF itself is not the unavailable state.

[Attempt5](device/attempt5-report.json) enabled consent through the real UI,
then failed with a fixed `device_command_failed` during native-crash verification.
That stage alone does **not** locate the failing command or prove a missing
import. [Attempt6](device/attempt6-report.json) stopped at bounded cold-navigation
readiness. Neither qualifies saved capture/preview. Earlier receipts preserve
private-backup quoting, navigation and screenshot-guard failures; those are
harness failures, not proof of a product crash.

The relevant product path is
[null-controller UI](../../../lib/ui/screens/crash_reports_section.dart):
readiness resolves once, and a null controller selects the unavailable row.
[CrashDiagnosticsStartup](../../../lib/diagnostics/crash_diagnostics.dart)
starts after the first frame, permits300ms, rejects late results and returns null
on timeout or exception; `start` memoizes that result for the process. The
receipts contain no channel/isolate timings establishing which null path was
responsible. A300ms startup timeout is a hypothesis, not a measured root cause.

The next owner should measure fixed startup stage/outcome/timing, qualify a
bounded recovery after unavailable startup, then rerun the complete
[driver](../../../tool/qa/bd7_device_crash_smoke.py). It now preserves validated
OS exit and category-file-existence status before reading the saved ring and
projects a missing-ring read as `saved_crash_ring_unavailable`; this refinement
is host-tested and did not get exercised past navigation in attempt6. Recovery
must preserve consent, avoid concurrent writable snapshot readers and prevent
expired attempts from attaching or committing data. Claude owns any UI retry
hook. No product fix was made or a replacement APK built during this historical
2196 device-qualification slice; the later backend correction is recorded below.

## Privacy and host checks

The [consent contract](../../design/bd7-contract.md) and
[privacy policy](../../privacy/bd7-crash-diagnostics.md) govern collection.
Original diagnostic files/absence and consent were snapshotted privately on the
emulator, then copied back and compared while this app was stopped. No profile,
account, provider or conversation files entered the snapshot. Small private
rescue snapshots were retained on failed sessions; no private paths or contents
enter public receipts. Consent was restored to its original state. The real OS
exit records naturally remain in Android's history.

Raw UI/XML, exception values, OS descriptions, logs and traces remain unsaved.
Projection and screenshot guards accept fixed copy, numeric metadata and dates;
unknown or editable content is refused. Each retained JPG is under100KB.

[Historical2196 host manifest](host-verification.json):52 deterministic offline Python tests
passed for parsing, UI privacy/navigation, process ownership, rollback and
failure projection. Behavioral controls failed before fixes or with guards
reverted, then passed with exact sources restored. Controls cover shell quoting,
disabled consent, exact degraded hints, transient hung accessibility dumps and
missing-ring evidence. Mistyped selectors and one mock-fixture error were
corrected and are not counted as failing-first evidence. Python compilation,
diff and documentation-link checks passed. Pinned `flutter pub get` ran once.
There were no Dart/native product edits in that historical snapshot: analyzer,
Kotlin gate and APK build were not applicable to it. Its source-hash manifest
describes the historical driver, which has since changed. The full suite remains
coordinator-owned. Commits are local only;
no amend, push, PR, signing change or publication occurred.

## Late-open correction checkpoint

The coordinator subsequently authorized a backend correction on the same
branch for crash-store opening that finishes after the startup budget. This
checkpoint extends the historical2196 qualification above; it does not replace
its findings or qualify saved-report capture and preview.

The intended behavior is that startup still returns within its bounded300ms
budget, while crash-store readiness remains pending until the actual controller
is available. A late successful open must publish that real controller rather
than permanently resolve readiness to null. Resetting the startup generation
must cancel stale publication, and restored diagnostics must replay once after
the current generation becomes ready.

The correction is **implemented and host-verified**. The
[late-open verification receipt](late-open-verification.json) records39 passing
focused Flutter tests across `crash_diagnostics_startup_test.dart` (13),
`crash_diagnostics_test.dart` and `crash_reports_section_test.dart`. Pinned full
`flutter analyze` was clean in11.3s on the final Dart snapshot. The slow-open
regression failed as expected against the old source and with the fix reverted;
removing the generation guard also failed as expected. The reset-during-replay
regression initially failed, then passed after the per-record generation guard
was added. All53 offline Python QA checks passed; the2197 candidate preflight
test failed as expected before the candidate metadata was switched. The
[late-open frontend contract](../../design/bd7-late-open-contract.md) records the
readiness behavior. The local correction commit is forthcoming and will be
appended by the lead.

The current driver accepts and defaults to2197; normal restoration remains2196.
The2197 APK file is absent, and no build or device session ran in this correction
turn. The historical2196 device/source-hash manifest does not qualify the changed
driver or the late-open correction. The complete saved-report flow still
requires a rerun against the coordinator's2197 candidate, including real
native-crash and ANR capture, saved rows and previews, private diagnostics
rollback, and normal-app restoration inside the same emulator lock. Until those
receipts exist, saved-report qualification remains **BLOCKED**.
