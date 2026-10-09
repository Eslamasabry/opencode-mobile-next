# BB5 device checkpoint — 2026-10-09

State: **failed QA acceptance; BB5 is not device-qualified.** Normal2199 is
restored, actual OpenCode2 Connected verified, Claude Code visibly signed in;
the emulator lock has been released. No Gradle/APK/JVM build ran in this turn.

Whole session used `flock -w 3600 /home/eslam/Storage/tmp/oc-emulator.lock` on
emulator5554 only, held across inspection, QA installation, scenario and restore
(1011seconds). `adb root` enabled access to the private snapshots after the first
read was refused; it remains enabled for following leads. Private preference and
credential contents were retained only in memory, never output or saved to QA.

Normal2199 SHA256be1bf7b80a5901a4041fbe8b7a2e754631f05ed04486b70c9e33c0ac1e12061a
and full authorized signer1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C
were checked before mutation. Existing signed QA2198 target/runner from the build
window were hash/cert checked. The private driver now admits that explicit QA
replacement only when the installed newer app exactly matches the independently
validated normal2199 artifact. QA install uses `adb install -r -d`; normal restore
uses `adb install -r`. No uninstall, data clearing, APK copying or credential work.

## Observed result

The initial baseline included an additional live owned helper. Server-only QA
admission refused without mutation. Authorized product Stop-all action through
BuiltinServerService then actual Start established a full-UID server-only baseline.
The normal product switch from OpenCode1 to OpenCode2 also completed successfully.
All changes stayed under the same lock.

[Session](device-session.txt) shows candidate/preflight/actual Connected checks
passed, then bb5Idle failed before any idle notification. The host originally
hid this refusal behind a missing-notification-tap message. Subsequent native
cleanup returned the concrete fixed reason **bb5_cleanup_fixture_invalid**
([receipt](cleanup-result.json)). The saved fixture had exactly version1, fixed
qa_bb5_idle owner, prior enabled=false/minutes5 and no captured members. Its
schema looked correct from host JSON; Android path/type validation and the primary
exception require further native fixture diagnosis. The native finally cleanup
can replace an earlier failure, so **an owner-change primary cause is unproven**.
No actual minute idle stop, helper pause, notification tap or return passed.

## Restoration

The general native-person restore passed before fixture cleanup was completed.
While the entire app UID was stopped, only three new QA-scoped native keys were
removed (budget/recipe/ownership), each proven absent in the original snapshot.
The test's two new Flutter marker/policy keys and its exact validated fixture
were removed; the helper home never existed and no members were captured.
Default-off/minutes5 idle state was verified. This cleanup did not alter real
profile counters, recipes, accounts or credentials.
[Scope receipt](qa-fixture-recovery.json), [final fixture receipt](fixture-final-restore.json).

Normal2199 was installed-r and its installed SHA/version checked. The first
post-install identity guard reported process replacement during startup; a
subsequent stable product/UI check proved actual Connected OpenCode2 and health.
[Final restoration](final-restoration.json),
[Claude signed-in screenshot](normal2199-agents.jpg) (small JPG).
The active runtime changed OC1→OC2 for qualification and remains connected OC2;
Claude Code is signed in, not newly authenticated. No logout occurred.

## Offline host repair and remaining gate

34 focused Python idle-host tests and120 affected BB9 host tests pass. Four independently removed fixes fail at
assertions, then exact bytes are restored and34 pass: known normal2199 support,
preserving native refusal before a missing tap, preserving its fixed native code,
and exact installed normal bytes before QA downgrade. Logs are host-tests*.txt
and red-*.txt. Generic BB9 callers retain downgrade refusal unless they carry
this explicit validated-normal guard; normal restoration never downgrades.

BB5 native fixture diagnosis/rebuild and a new whole-session rerun remain required.
Available memory after release was4231MiB, below the ordinary6144MiB Gradle gate;
the temporary5120MiB allowance applied only to the previous exclusive window.
No build was started. BB7 remains skipped for this unmet qualification dependency.

## Offline fixture diagnosis after releasing the emulator

Finish line: preserve the first admission failure and prove whether runtime setup
and the background idle wait were entered. Non-goal: changing production owner,
work admission or timer policy to make a private QA run pass.

The four-field saved fixture precedes helper creation, policy configuration,
runtime inventory capture and the actual background transition. No helper home
existed. Therefore the observed run never reached its background idle wait; it
does not establish a defective idle timer. Start/health, owner validation and
helper admission remain candidates. Later owner changes during restoration do
not identify the first failure.

The original `finally` could replace any of those failures with cleanup's error.
Its path check also rejects a valid trusted app files-directory alias because
literal and canonical paths differ. The host now reproduces both problems using
the **actual private Java guard** loaded by the Android fixture. Trusted parent
aliases work; fixture symlinks, outside paths and directories refuse. Restoring
the old path predicate produces an assertion failure; making cleanup replace
the primary error produces a separate assertion failure. These establish fixture
bugs, **not the unrecorded device startup cause or its exact Android path**.

The fixture retains the original fixed refusal, reports a secondary cleanup code
separately, maps otherwise unknown errors to fixed startup/owner/helper/policy/
background/return phases, and emits `bb5RuntimePrepared`/`bb5IdleWaitEntered` only
at their actual boundaries. Cleanup path/size/keys/value checks now have distinct
fixed codes. The host prints only these allowed diagnostics. No raw exception,
guest output, script, password, provider response or account data is added.

37 focused host tests pass after byte-exact restoration; three removed fixes fail
at behavioral assertions (alias, primary preservation, diagnostic fields).
120 affected BB9 host tests pass. Evidence: [restored host run](host-guard-restored.txt),
[affected hosts](host-guard-bb9-affected.txt), [alias red](red-guard-alias.txt),
[primary red](red-guard-primary.txt), [field red](red-guard-phase-fields.txt).
The host compiler/JVM are capped at96/64MiB, under the shared test lock; no
Gradle/APK build or emulator session was started. Android Kotlin integration and
the private runner rebuild are pending the memory/build gate. The actual device
primary cause remains unproven until a corrected runner is exercised after BC's
turn. BB5 remains default-off and device-unqualified; BB7 remains deferred.

## Additional offline narrowing

[Original primary analysis](original-primary-analysis.md) traces the retained
recipe to the native gate's durable commit and demonstrates a foreground owner
collision: the actual Dart recovery controller rebinds the saved phone profile
after the private fixture switches native ownership.15 affected Dart tests pass,
the removed real bind fails at an assertion, exact source restoration is checked,
and focused analysis is clean ([analyzer](owner-rebind-analyze.txt)). This remains
a candidate mechanism, not the uniquely recovered device primary. No new
emulator session, native build or product behavior change occurred. The next
build awaits the coordinator's window after BC and BA.
