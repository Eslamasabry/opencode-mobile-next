# BB5 integration checkpoint — 2026-10-08

State: Dart lifecycle integration verified; native implementation and private
acceptance runner are drafted, uncompiled and not device qualified. Idle policy
defaults off. This is not BB5 completion, a release or all-agent certification.

Finish line: stop the owned server and previously live helper after known idle
work in background, then resume their recorded intent on foreground return or
notification tap without replenishing the crash budget or overriding Stop.
Non-goal: UI, BA connection-library edits, credentials, real Claude sign-in,
releases or exact wake-up timing during Doze.

## Source and focused checks

Integration `9485f0c7c` merged without rebase at `7af5b544c`. Verified Dart
integration committed at `94001aeb9`; host fixture/tests at `61ec2c85b`.
The native draft remains uncommitted; hashes are in
[native-draft-manifest.txt](native-draft-manifest.txt). BB5 groundwork
`89f8937a4` and BA hooks `f561c3584` remain prerequisites.

Pinned Flutter 3.47.1; every check below used
`OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test -- ...`. One process at a time;
no repository-wide test suite run.

| Check | Result | Evidence |
| --- | --- | --- |
| `flutter test --concurrency=1 test/builtin_server_recovery_test.dart` | 17 pass | [log](dart-recovery-final.txt) |
| `flutter test --concurrency=1 test/phone_server_healing_test.dart` | 43 pass | [log](dart-healing-final.txt) |
| `flutter test --concurrency=1 test/phone_agent_work_provider_test.dart` | 4 pass | [log](dart-provider-final.txt) |
| `flutter analyze` | clean after adding two required braces | [log](analyzer-final.txt) |
| `python3 -m unittest discover -s tool/qa -p test_bb5_runtime_acceptance.py -v` | 20 pass | [log](python-host-final.txt) |

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
or timeout revision revoked during the launch. Both fixes still need native
removed-fix and restored tests.

87 affected JVM tests are authored (IdleStopPolicy16, NativeIdleState29,
NativeIdleHeartbeat17, NativeIdleTimer17, receiver dispatch8). These have not
been run on this integration candidate. The historical pre-hold IdleStopPolicy16
pass is not a substitute for the new candidate's native gate.

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

Additional pending device cases: actual local-agent/chat/setup/sign-in/terminal
busy work blocks idle; late Stop/timeout during resume; notification tap; cold
idle marker persistence. The busy receiver/lease unit fixtures do not prove
those device journeys. No APK compilation, APK installation, adb invocation,
emulator session or app/account/data change occurred at this checkpoint.

## Resource and restoration rules

Coordinator lifted the build hold conditionally: at least 6 GB available memory
before a build, with
`GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx4g -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2"`.
Available memory during this checkpoint remained below the threshold
(approximately 3.8–5.1 GB), so no native compiler/Gradle/APK job was started.
Final [resource reading](resource-gate.txt) remains below the gate. No shared
process was killed or memory reclaimed from another lane. No new BB APK exists
for this draft, so the new candidate cannot be device qualified yet.

Future whole device sessions hold `/home/eslam/Storage/tmp/oc-emulator.lock`.
Normal APK is `/home/eslam/Storage/tmp/oc-apk-share/oc-2197.apk`, verified against
its `.sha256`, same `1DE5BF08...` signer. QA target uses versionCode2197 so normal
restoration uses `adb -s emulator-5554 install -r` without downgrade/uninstall/
data clearing. Restore in the same lock. If insufficient storage prevents it,
retain the QA app/data, record the installed build, release and report.
Claude's known normal2197 “Sign in needed” is BA-owned; this lane must not log
out, move credentials or manipulate its auth/configuration.

Android may delay inexact alarms during Doze; the threshold is the earliest
eligible stop, not an exact sleeping-device deadline. Missing/stale work evidence
blocks stopping. [Android AlarmManager reference](https://developer.android.com/reference/android/app/AlarmManager#setAndAllowWhileIdle(int,long,android.app.PendingIntent)).

BB7 is skipped pending BB5 qualification. BB6 remains skipped for its account
prerequisite, and BB8 belongs to BD2.
