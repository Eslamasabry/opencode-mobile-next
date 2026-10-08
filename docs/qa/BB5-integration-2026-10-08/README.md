# BB5 integration checkpoint — 2026-10-08

State: Dart integration and focused native/JVM regression proof passed before
the latest coordinator merge. The merged native candidate and QA release APK
remain in progress; no new APK or BB device qualification exists. Idle policy
defaults off. This is not BB5 completion, a release or all-agent certification.

Finish line: stop the owned server and previously live helper after known idle
work in background, then resume their recorded intent on foreground return or
notification tap without replenishing the crash budget or overriding Stop.
Non-goal: UI, BA connection-library edits, credentials, real Claude sign-in,
releases or exact wake-up timing during Doze.

## Source and focused checks

Integration `9485f0c7c` merged without rebase at `7af5b544c`. Verified Dart
integration committed at `94001aeb9`; host fixture/tests at `61ec2c85b`.
The native draft remains uncommitted. The
[native-draft-manifest.txt](native-draft-manifest.txt) now captures the source,
notification resources and host candidate. Those hashes imply no compile pass. BB5 groundwork
`89f8937a4` and BA hooks `f561c3584` remain prerequisites.

Native verification below used source context `196703cb8`. The coordinator then
merged `feat/genui-fe` at `b62f998eb` without rebase, producing `bfbb6dac8`; this
includes normal2198 and Claude fix `064a43a36`. The one BuiltinLinux conflict
preserved both BB4 `agentWorkAdmissions` and BA `authOtherOwners.register`.
Native WIP stash `4a65...` reapplied cleanly. A merged-candidate native compile
is still pending; the unchanged pure classes/tests retain their narrower proof.

Pinned Flutter 3.47.1; Dart and Python checks below used
`OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- ...`. One process at a time;
native Gradle/JVM checks used the shared lock. No repository-wide test suite run.

| Check | Result | Evidence |
| --- | --- | --- |
| `flutter test --concurrency=1 test/builtin_server_recovery_test.dart` | 17 pass | [log](dart-recovery-final.txt) |
| `flutter test --concurrency=1 test/phone_server_healing_test.dart` | 43 pass | [log](dart-healing-final.txt) |
| `flutter test --concurrency=1 test/phone_agent_work_provider_test.dart` | 4 pass | [log](dart-provider-final.txt) |
| `flutter analyze` | clean after adding two required braces | [log](analyzer-final.txt) |
| `python3 -m unittest discover -s tool/qa -p test_bb5_runtime_acceptance.py -v` | 20 pass | [log](python-host-final.txt) |
| Updated host mocks with normal2198 | 28 pass | [log](python-host-2198.txt) |
| Focused Gradle/JVM selection before coordinator merge | 172 pass | [log](native-gradle-premerge-focused.txt), [counts](native-gradle-counts.txt) |
| Native guard removals / restored affected pure tests | 20 assertion reds / 123 pass | [red](native-idle-red.txt), [restored](native-idle-restored.txt) |

Five production guards were removed individually. Each focused file failed at
behavioral assertions; each source was then restored byte-for-byte and the final
files above passed. Compiler failures are not counted as regression evidence. Own log line-ending
whitespace is normalized for the committed diff; results and assertions are
preserved.

| Removed guard | Evidence |
| --- | --- |
| Recovery rejects idle/malformed native status before changing retry state | [red](red-recovery-idle-guard.txt) |
| Provider passes the actual BA guarded helper-restoration callback | [red](red-provider-restore-hook.txt) |
| 15-second work heartbeat continues delivering current alias/busy truth | [red](red-work-heartbeat.txt) |
| A same-owner manual claim invalidates old idle work without canceling its own launch | [red](red-same-owner-claim.txt) |
| Pause/return, deletion and transfer revoke the outstanding launch epoch | [red](red-launch-epoch-revocation.txt) |

The first recovery run lacked a required fake status field, and the first
healing run assigned a nonexistent fake setter. Those compilation failures are
retained, excluded from red proof, and fixed. The next healing run exposed five
legacy-launch regressions caused by the launch invalidating its own idle epoch;
the separate launch epoch fixes them. The final analyzer initially found two
missing braces; final analyzer is clean.

Seven host guards also have behavioral removed-fix controls and a restored
20-test pass: required native flags, counter preservation, whole-UID death,
normal artifact revalidation, install-r without downgrade, full-UID coverage and
exact server executable proof. Logs are `red-host-*.txt`. No mocked adb call
executes an actual device command.

## Native draft and remaining gates

Native draft includes durable policy/generation, 45-second fresh work evidence,
logical lease admission, stop-only elapsed timer/inexact alarm, exact persisted
child drain, idle exclusion from sticky/crash recovery, foreground/token-bound
server/helper resume and preserved retry budget. A late observation from another
alias cannot replace the bound runtime owner's evidence. Idle launch authorizes
only the work generation captured before drain/launch; it cannot reopen a Stop
or timeout revision revoked during the launch. Both fixes are covered by the
completed focused native removed-fix/restored proof below.

123 affected JVM tests are authored (IdleStopPolicy16, NativeIdleState29,
NativeIdleHeartbeat17, NativeIdleTimer17, receiver dispatch8, helper admission22,
idle notification14). All 123 passed after byte-for-byte guard restoration.
The focused Gradle/JVM selection also ran NativeWorkLeaseHost49, for 172 passing
tests in total. These results precede `bfbb6dac8`; unchanged pure source/test
validity does not establish the merged JNI/Android compile.

The [native regression driver](../../../tool/qa/bb5_native_regression.py) first
passed compiler-free `--validate-only` for 20 selections, then executed all 20
guard removals. Each failed at a behavioral assertion; original bytes were
restored and all 123 affected pure tests passed. Compiler/fixture failures are
not counted as behavioral reds. Merged Android compilation, the QA release APK
and device checks remain pending.

The host refuses non-server/unknown/installer/sign-in/terminal payloads before
its initial setup mutation. The native scenario independently requires known
logical idle and only the existing server before changing its fixture. Cleanup
uses production receipt authority to drain; saved QA identities are only
independent ESRCH absence evidence, required before receipt/home deletion.
Initial authorized force-stop/replacement is test setup, not a claim that the
existing server had no CPU-capped logical chat work.

The private `bb5Idle` runner is prepared for a real one-minute background
interval, exact old PID absence, foreground resume/helper acknowledgement,
budget preservation and explicit Stop. Its helper is an isolated empty shell
stand-in; it does not establish real Paseo readiness, agent authentication,
all-agent turns or Dart manualReadyCount on device. The server uses the existing
canonical OpenCode2 runtime data/config; no server namespace isolation is
claimed. A real MainActivity and owner checks remain required.

The draft now includes a plain idle return notice: normal non-ongoing,
auto-cancel notification `4098` on the existing phone-server channel, with an
immutable MainActivity content intent. It says “Phone server paused while idle.
Tap to open OpenCode.” Posting requires current durable idle intent and it is
cancelled on foreground return or revocation; it starts no service and holds no
CPU lease. The actual-SystemUI-tap fixture is authored but unqualified. A direct
Activity launch does not establish notification-tap behavior.

Additional pending device cases: actual local-agent/chat/setup/sign-in/terminal
busy work blocks idle; late Stop/timeout during resume; notification tap; cold
idle marker persistence. The busy receiver/lease unit fixtures do not prove
those device journeys. A QA release APK build was attempted and failed as
described below. No new APK, APK installation, BB adb invocation, BB emulator
session or app/account/data change resulted.

## Resource and restoration rules

Coordinator lifted the build hold conditionally: at least 6 GB available memory
before a build, with
`GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4g -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2"`.
The earlier memory hold and readings in [memory-watch.txt](memory-watch.txt) and
[resource-gate.txt](resource-gate.txt) preceded the completed native checks.
This BB lane subsequently ran the locked focused Gradle/JVM checks and
native guard controls. No shared process was killed or memory reclaimed from
another lane.

The QA release APK failed because `GeneratedPluginRegistrant` referenced
`dev.flutter.plugins.integration_test.IntegrationTestPlugin`, which was absent
from the release classpath. [Build failure](release-app-registrant-failure.txt).
The owned Gradle daemon, exact PID `3212856`, was stopped and this worktree's
intermediates were deleted. No new APK or device proof resulted. This BB lane
is adding a narrow `releaseImplementation` dependency for the existing
`ocBuiltinRuntimeQa` flag; the one-runner guard remains intact. Rebuilding and
native compilation on the merged candidate remain pending.

Future whole device sessions hold `/home/eslam/Storage/tmp/oc-emulator.lock`.
Normal APK is `/home/eslam/Storage/tmp/oc-apk-share/oc-2198.apk`, verified against
its `.sha256`, same `1DE5BF08...` signer. QA target uses versionCode2198 so normal
restoration uses `adb -s emulator-5554 install -r` without downgrade/uninstall/
data clearing. Restore in the same lock. If insufficient storage prevents it,
retain the QA app/data, record the installed build, release and report.
Historical normal2197 showed Claude “Sign in needed”; that account issue was
BA-owned. Normal2198 includes Claude fix `064a43a36`; BA's emulator check is
next. This lane must not log out, move credentials or manipulate Claude
auth/configuration, and does not claim BA's pending device result.

Android may delay inexact alarms during Doze; the threshold is the earliest
eligible stop, not an exact sleeping-device deadline. Missing/stale work evidence
blocks stopping. [Android AlarmManager reference](https://developer.android.com/reference/android/app/AlarmManager#setAndAllowWhileIdle(int,long,android.app.PendingIntent)).

BB7 is skipped pending BB5 qualification. BB6 remains skipped for its account
prerequisite, and BB8 belongs to BD2.

## Resumed after FQ9 artifact diagnosis

Coordinator confirmed BC's seeder created the hidden legacy fixture outside the
product path. BB dropped its broad uncommitted project-recovery draft; strict
existing guards are unchanged. Diagnosis/evidence only are committed at
`b910abfc1` and `3cbf486a5`. No FQ9 production recovery shipped, and no FQ9 native
compile/build or app-data cleanup was performed; BC owns fixture cleanup and
rerun. The owned BB5 stash was restored cleanly.

The watch resumed at `20:34 UTC`, then exact watcher PID `3171435` was paused
before any compilation while the notification-tap gap changed the draft.
At that earlier checkpoint worker ownership had returned and the draft was
recorded in the manifest. Replacement watcher PID `3187182`, session
`42075`, started at `2026-10-08T20:59:50Z`; it polls every two minutes for at most
60 minutes. The `>=6144 MiB` gate is rechecked inside the shared build lock
before focused Gradle/JVM work. The later native/JVM results above supersede the
earlier validate-only checkpoint; merged Android/APK and device qualification
remain pending.

## Notification host verification checkpoint

The updated host fixture has 28 passing Python tests, including shade/XML/body
bounds command selection and fragmented/missing native-marker refusal.
[Focused pass](python-notification-final.txt). Removing the native readiness
marker requirement makes its named test fail by assertion even when the mocked
native result says PASS; source bytes were restored exactly and all 28 passed
again. [Behavioral red](red-host-notification-marker.txt),
[restored pass](python-notification-restored.txt). After updating current target
and normal restoration to version2198, all 28 passed again in
[python-host-2198.txt](python-host-2198.txt). These mocked checks establish no
adb/SystemUI/device behavior; the actual tap fixture remains unqualified.

## Merged-candidate resource watch

The current watcher is exact owned PID `3222150`, session `17930`, started
`2026-10-08T21:48:53Z`. It checks available memory every two minutes, for at
most 60 minutes, and rechecks the 6144 MiB threshold inside the shared build
lock. Its pending focused JVM command uses only `ocBuiltinRuntimeQa=true`,
including the narrow release plugin dependency fix. At `22:01 UTC` memory
was 3848 MiB; the merged compile had not started. BA has the next emulator
turn; this lane has not acquired the emulator lock or changed the device.
