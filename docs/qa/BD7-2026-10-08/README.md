# BD7 — real device crash and ANR qualification

## Current result: 2202 saved-report flow PASS — 2026-10-09

The requested crash-only saved-report journey is **device verified on APK 2202**.
[Complete receipt](2202-saved-report/report.json),
[single-crash checkpoint](2202-saved-report/crash-trigger.json),
[cleanup](2202-saved-report/cleanup.json).

- Exact approved artifact SHA-256:
  `e63fb2e4ff32280ad4c739aee9c17db508eab2e99a42573c4e83bd66dc0babb0`;
  build 2202, local release signer `1DE5BF08...`.
- Empty/Off baseline, then real UI consent On. Exactly one `am crash --user 0`
  targeted verified main PID 3222, start ticks 15531. Android recorded exact-main
  reason 4/status 0; the fresh native report timestamp followed the trigger by
  166 ms. [Capture status](2202-saved-report/native-capture-status.json).
- After reopening, the [saved row](2202-saved-report/native-report.jpg),
  [details preview](2202-saved-report/native-preview.jpg), and
  [share preview](2202-saved-report/saved-report-share-preview.jpg) were visible.
  The Share report button was not activated; no external share opened.
- Switching [capture Off](2202-saved-report/consent-off-after.jpg) used the
  product's automatic deletion path. Persisted consent was zero, saved count
  zero, and the saved row absent. No separate Delete row remains when Off.
- Same-lock `adb install -r` of the approved normal 2202 APK passed subsequent
  artifact/signer/same-UID verification and app launch. The driver returned PASS,
  exited successfully and released its reservation; no further ADB access ran.

All six retained JPGs were visually reviewed and are under 100 KB each. They
contain only fixed diagnostics copy, error category and timestamps. All 83
focused host checks passed; current source hashes, JSON, images and links were
checked. No product edit, build, ANR, account action, external share, push or
release occurred. This qualifies the requested 2202 saved-report slice; the
historical ANR/2196/2198/2199 results below remain separate.

## Historical attempts and earlier scope

## 2198 saved-report follow-up — BC takeover, 2026-10-09

Finish line: capture one real verified-process crash on normal2198, show the
saved report and share preview through Settings, then delete that owned report,
restore capture OFF and reinstall normal2198 inside the same emulator lock.
ANR qualification, external sharing, Claude/account actions and app changes
are outside this follow-up.

The crash-only driver is [bd7_device_saved_report.py](../../../tool/qa/bd7_device_saved_report.py).
It reuses BD7's exact PID/UID/start-time and native/OS exit proof, adds the FD2
share-preview crop, and refuses to replace existing saved errors. All private
contents stay in memory. Automatic timing/exit observations may be cleared by
the explicitly authorized consent switch; existing error reports block the run.
The wrapper pins approved2198 and supports the outer reservation with
`run_locked`, avoiding a nested lock or the old driver's ANR/rollback path.

Host checks: **70** focused Python checks passed serially through machine_lock.
Failing-first controls cover the share crop, hostile exception projection,
outer-call2198 admission, stable recapture after safe geometry movement and a
new installer ticket arriving before `am crash`.
[Host proof](2198-saved-report-host-verification.json).

The first2198 wrapper passed installer admission and the empty disabled
baseline, then stopped at `app_navigation_not_ready` before consent or crash.
[Initial receipt](2198-saved-report-admission/report.json). Normal2198 restoration
passed. Its durable CHECK ticket cleared through the app's normal path and
Settings became reachable after product Start and connect on verified OC1.

The [consent-only attempt](2198-saved-report-device/report.json) rejected the
ON and cleanup screenshots as `unsafe_screenshot`; subsequent private reads
confirmed OFF and zero saved reports. A settled consent-only probe passed the
same crop guard. The camera now recaptures once only when both independently
privacy-validated rectangles differ; it discards the first PNG and accepts only
a new stable guarded capture. Unknown text, editable regions and persistent
movement still refuse. `layout_retries` records successful use. This recovery
is host-tested; it has not qualified a successful enabled-state device photo.

The [fresh attempt](2198-saved-report-device-reflow/report.json) stopped at
`setup_active_or_unknown`. A bounded120-second read-only wait kept refusing:
a durable all-component CHECK ticket was present, with its root and leader
PIDs absent. Their absence does not prove descendant quiescence. No ticket
was edited and no real crash was triggered. The wrapper now repeats installer
admission immediately before PID lookup/date/`am crash`, because UI work can
outlive the original preflight.

**2198 saved capture and share preview remain BLOCKED/unqualified.**
[Blocked receipt](2198-saved-report-blocked.json) and
[final restoration](../FQ9-2026-10-08/2198-final-restoration.json) confirm exact
normal2198 `adb install -r`, same UID10217, original background OFF, crash consent
OFF and ring0. Both owned FQ9 sessions and empty canonical fixture directories
were cleaned. The locked shell exited; no BC reservation or waiter remains.
The next device proof needs healthy product-resolved installer admission. Native
recovery changes, ANR, Claude/account actions and external sharing were not
performed. The historical2196 qualification below stays separate.

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

## 2199 saved-report follow-up — 2026-10-09

Finish line: on approved normal 2199, enable capture through Settings, crash only
the verified app PID, reopen and verify the saved row and local share preview,
then delete the owned report, restore capture Off and reinstall normal 2199
inside the same emulator reservation. Non-goals: ANR, external sharing, provider
interaction, installer-ticket repair, app-data reset, UI/native changes or builds.
Emulator order is BB, BA, then BC; the driver waits with `flock -w 3600`.

Candidate and lock-wait regression control: updated 2199 fixture against the
old 2198-only driver failed (3 assertions, 3 admission errors); the lock assertion
observed 1800 instead of 3600 seconds. Device proof is pending, not qualified by
these offline controls.

[2199 host receipt](2199-saved-report-host.json): all 71 focused Python checks
pass serially through machine_lock; formatting, F/E9 lint and diff checks clean.
The approved APK SHA-256 is `be1bf7b80a5901a4041fbe8b7a2e754631f05ed04486b70c9e33c0ac1e12061a`;
build 2199, version 1.2.0 and local signer `1DE5BF08...` are host-verified.
Run after the BB/BA handoff: `python3 tool/qa/bd7_device_saved_report.py --output
docs/qa/BD7-2026-10-08/2199-saved-report`. No APK build is required.

The [initial 2199 attempt](2199-saved-report/report.json) passed the empty/Off
baseline and installer-idle guard, then stopped at `app_navigation_not_ready`
before consent or crash. Normal 2199 reinstall/identity verification passed.
A new reservation found the exact app visible on its retained Agents route,
with Back and no root Settings tab. This is a harness navigation assumption,
not saved-capture failure. A deterministic route fixture failed before the fix;
the saved-report driver now returns only from the visible app-owned Agents/Back
page before normal Settings navigation. Unknown/foreign pages are untouched.
All 73 focused Python checks pass serially through machine_lock; lint/format and
diff checks are clean. No app-source edit or build. The first receipt remains
unchanged, and the device retry uses a separate output directory.

The [crash-trigger retry](2199-saved-report-retry/report.json) enabled capture
and called `am crash` after exact PID/UID/birth and installer-idle checks.
Reopening failed because the managed server was stopped, leaving a connection
page without Settings. The [resumed receipt](2199-saved-report-resumed/report.json)
correlates the single owned native record (1791507336215 ms) with the exact-main
Android reason-4 exit (1791507336232 ms, 17 ms later). Product Start and connect
restored navigation and the saved row was visible. The preview screenshot guard
then refused its sheet; no unsafe image was retained. Owned report deletion,
capture Off/zero ring, and exact normal 2199 reinstall plus product startup all
passed before this reservation was released for BA. These receipts remain FAIL
and do not qualify share preview. No second crash occurred in that resumption.

Offline harness corrections now cover recovery after cold activity launch,
reusing the exact visible report page, and the authored kit actions Dismiss and
Hide details. Public source: `kit_sheet_parts.dart` handle and
`kitDetailsHide` copy. Private suffixes, unknown content and editable input remain
refused. Report-page and sheet fixtures failed before their corrections; the
cold stopped-server fixture also failed before recovery moved inside bounded
launch navigation. The saved driver permits 60 seconds for real product startup
and dispatches one guarded recovery per launch; the historical crash/ANR driver
retains its 15-second default. All 78 focused host checks pass serially through
machine_lock. App source, account data and ticket files are unchanged. A complete
new crash/report/share/cleanup proof is still pending the next reservation.

The [pre-consent attempt](2199-saved-report-final/report.json) stopped at
`navigation_target_unavailable`; normal restoration passed and no second crash
was triggered. A [navigation-only trace](2199-navigation-debug.json) then passed
with capture Off and the saved ring empty. Three deterministic controls exposed
report-page priority, retained Settings scroll and inline exit-history expansion
assumptions (two assertions and one navigation error before correction). The
driver now reuses the visible report page, scrolls Settings upward, and opens
share preview directly from inline exit history. All 81 focused checks pass
serially through machine_lock; no app source changed. Complete device proof
still requires a new reservation.

## Crash recovery review — 2026-10-09

The recovered [2199 attempt](2199-saved-report-verified/report.json), despite its
directory name, is **FAIL**, not a completed qualification. Its safe screenshots
show consent, the saved native row and its details preview; the share-preview
image is absent. It stopped at `native_crash/navigation_target_unavailable`.
[Restoration](2199-saved-report-verified/restoration.json) confirms consent Off
and zero saved reports. The old host manifests remain historical and unchanged.

The reviewed QA-only correction lets this saved-report driver omit the separate
Recent app exits UI journey while retaining exact-main Android reason 4, the
post-trigger saved native record, its row and details preview. The historical
crash/ANR driver's default still requires exit UI. All 82 focused Python tests
passed serially through `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test`;
[recovery host receipt](recovery-host.json) binds that result to source hashes.
This is host verification only. Fresh 2202 device qualification follows in a
separate evidence directory; no failed 2199 result is promoted.

## 2202 saved-report qualification — 2026-10-09

Finish line: on approved normal APK 2202, enable consent, issue one real
`am crash` to the verified app PID, reopen, view the saved row and local share
preview, then switch capture Off and verify deletion before same-lock normal
2202 restoration. Non-goals: ANR, external share, provider/account interaction,
installer-ticket repair, product changes or builds.

The driver pins build 2202 and local signer, verifies the sidecar checksum, and
preserves PID/start-ticks/trigger-time intent before signaling. A durable trigger
receipt refuses another crash in the same output directory. Post-crash failures
require navigation-only recovery, never blind rerunning of the crash command.
Turning capture Off erases native and Flutter evidence in the product, so no
separate Delete button exists afterwards; cleanup checks persisted consent and
the empty ring and records the automatic deletion.

The 2202 host checkpoint passes all 83 focused Python cases serially through
`OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test`. Changed-fixture controls failed
against the preceding code: build admission, durable trigger, and Off-before-
deletion behavior. [Host receipt](2202-saved-report-host.json). The [fresh device receipt](2202-saved-report/report.json) passes; see the current
result at the top for the exact scope and cleanup.
