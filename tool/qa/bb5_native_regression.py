#!/usr/bin/env python3
"""Focused BB5 pure JVM controls, supplemental to Android compile/device gates.

Run under tool/qa/machine_lock.sh build with a native source freeze. No device,
APK, auth or service process is invoked. Sources restore byte-for-byte in finally.
Every removed-fix control must fail one real JUnit test by assertion; compilation
errors never count as red proof. A fresh >=6GB available memory gate applies.
"""
from pathlib import Path
import os
import re
import subprocess
import tempfile

import bb4_native_regression as J

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile'
TEST = ROOT / 'android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile'
OUT = ROOT / 'docs/qa/BB5-integration-2026-10-08'
CLASSES = ('IdleStopPolicyTest', 'NativeIdleStateTest', 'NativeIdleHeartbeatTest',
           'NativeIdleTimerTest', 'NativeIdleReceiverDispatchTest', 'NativeAgentHostAdmissionTest',
           'NativeIdleReturnNotificationTest')
SOURCES = ('IdleStopPolicy.kt', 'NativeIdleState.kt', 'NativeIdleHeartbeat.kt', 'NativeIdleTimer.kt',
           'NativeAgentHostAdmission.kt', 'NativeIdleReturnNotification.kt')
CONTROLS = (
    ('notification_foreground', 'NativeIdleReturnNotification.kt', (('!state.foreground &&', ''),),
     'NativeIdleReturnNotificationTest', 'foregroundReturnRemovesNotificationAndAllowsLaterIdleShow'),
    ('notification_deduplicate', 'NativeIdleReturnNotification.kt', (('if (shown == key) return true', ''),),
     'NativeIdleReturnNotificationTest', 'repeatedSameOwnerGenerationDoesNotShowAgain'),
    ('notification_prior_process_clear', 'NativeIdleReturnNotification.kt',
     (('@Synchronized fun clear() {\n        shown = null',
       '@Synchronized fun clear() {\n        if (shown == null) return\n        shown = null'),),
     'NativeIdleReturnNotificationTest', 'stalePlatformNotificationIsRemovedWithoutAnInMemoryKey'),
    ('helper_ticket_epoch', 'NativeAgentHostAdmission.kt', (('current.activityEpoch != ticket.epoch ||', ''),),
     'NativeAgentHostAdmissionTest', 'pauseAndReturnDoNotReviveTheOldTicket'),
    ('helper_ticket_generation', 'NativeAgentHostAdmission.kt', (('state.generation == token &&', ''),),
     'NativeAgentHostAdmissionTest', 'idleTokenMustRemainExactlyCurrent'),
    ('helper_completed_scope', 'NativeAgentHostAdmission.kt', (('current.helperOwner == ticket.profile', 'true'),),
     'NativeAgentHostAdmissionTest', 'completedIdleTokenAdmitsOnlyItsCanonicalHelper'),
    ('idle_known_work', 'IdleStopPolicy.kt', (('workBusy != false', 'workBusy == true'),),
     'IdleStopPolicyTest', 'unknownWorkCannotStartOrKeepAnIdleDeadline'),
    ('idle_elapsed_threshold', 'IdleStopPolicy.kt', (('nowMillis >= deadline', 'true'),),
     'IdleStopPolicyTest', 'fiveMinuteDeadlineIsDueAtExactBoundaryAndRemainsDue'),
    ('idle_foreground', 'IdleStopPolicy.kt', (('!enabled || foreground || workBusy != false', '!enabled || workBusy != false'),),
     'IdleStopPolicyTest', 'foregroundCancelsDueStopAndNextBackgroundIntervalStartsFresh'),
    ('idle_clock_highwater', 'IdleStopPolicy.kt', (('(previous != null && nowMillis < previous)', 'false'),),
     'IdleStopPolicyTest', 'backwardTimeCancelsAndCannotReadmitUntilTheClockCatchesUp'),
    ('durable_state_write', 'NativeIdleState.kt', (('persist(next)', 'true'),),
     'NativeIdleStateTest', 'failedPersistFreezesOldSnapshotAndEverySubsequentAdmission'),
    ('idle_exact_generation', 'NativeIdleState.kt', (('state.generation == generation', 'true'),),
     'NativeIdleStateTest', 'wrongOwnerAndStaleGenerationNeverWriteAcknowledgments'),
    ('helper_live_ack', 'NativeIdleState.kt', (('(state.helperStopped && !helperRunning)', 'false'),),
     'NativeIdleStateTest', 'priorLiveHelperMustBeRunningBeforeCompletionClearsAllMarkers'),
    ('server_lost_helper_intent', 'NativeIdleState.kt', (('if (!matches(owner, gen) || !state.helperStopped) return false', 'if (!matches(owner, gen)) return false'),),
     'NativeIdleStateTest', 'serverCrashCannotReviveRevokedOrCompletedHelperIntent'),
    ('heartbeat_freshness', 'NativeIdleHeartbeat.kt', (('elapsed < freshForMs', 'elapsed <= freshForMs'),),
     'NativeIdleHeartbeatTest', 'defaultIdleEvidenceIsFreshOnlyBeforeFortyFiveSecondBoundary'),
    ('heartbeat_owner', 'NativeIdleHeartbeat.kt', (('if (owner != sample.owner) return null', 'if (false) return null'),),
     'NativeIdleHeartbeatTest', 'onlyExactOwnerCanReadEvidenceAndWrongOwnerDoesNotEraseIt'),
    ('timer_queued_revision', 'NativeIdleTimer.kt', (('exhausted || revision != generation || deadline != expectedDeadline', 'exhausted || deadline != expectedDeadline'),),
     'NativeIdleTimerTest', 'cancelThenRescheduleSameDeadlineKeepsOldGenerationCallbackInert'),
    ('timer_early_dispatch', 'NativeIdleTimer.kt', (('if (at < expectedDeadline)', 'if (false)'),),
     'NativeIdleTimerTest', 'earlyCallbackRearmsRemainingTimeWithoutCheckingStopPrematurely'),
    ('receiver_timeout_finish', 'NativeIdleTimer.kt', (('later(8_000) { complete(true) }', 'later(8_000) { Unit }'),),
     'NativeIdleReceiverDispatchTest', 'timeoutFinishesAndPreventsAnUnstartedLateWorkerFromChecking'),
    ('receiver_rejection_finish', 'NativeIdleTimer.kt', (('} catch (_: Throwable) { complete(true) }', '} catch (_: Throwable) { Unit }'),),
     'NativeIdleReceiverDispatchTest', 'boundedWorkerRejectionFinishesImmediatelyWithoutRunningCheck'),
)


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def log(path, message):
    with path.open('a') as stream:
        stream.write(message+'\n')
    print(message, flush=True)


def validate(originals, tests):
    for label, source, replacements, klass, method in CONTROLS:
        require(klass in CLASSES, 'bb5_control_class_unknown')
        require(len(re.findall(r'@Test\s+fun\s+'+re.escape(method)+r'\s*\(',
                               tests[TEST/(klass+'.kt')].decode())) == 1,
                'bb5_control_test_not_unique')
        for before, after in replacements:
            require(before != after and originals[source].decode().count(before) == 1,
                    'bb5_control_guard_not_unique_'+label)


def main():
    originals = {name: (MAIN/name).read_bytes() for name in SOURCES}
    tests = {TEST/(name+'.kt'): (TEST/(name+'.kt')).read_bytes() for name in CLASSES}
    validate(originals, tests)
    if __import__('sys').argv[1:] == ['--validate-only']:
        print('PASS unique guards and named behavior tests; no JVM/compiler invoked')
        return 0
    require(not __import__('sys').argv[1:], 'bb5_unsupported_arguments')
    rows = subprocess.check_output(['free', '-m'], text=True)
    available = int(next(line for line in rows.splitlines() if line.startswith('Mem:')).split()[-1])
    require(available >= 6144, 'bb5_native_memory_gate_unmet')
    OUT.mkdir(parents=True, exist_ok=True)
    red = OUT/'native-idle-red.txt'; restored = OUT/'native-idle-restored.txt'
    red.write_text('Focused pure JVM BB5 controls; Android compilation and device proof are separate.\n')
    restored.write_text('Restored affected BB5 classes; no Android methods or real service process invoked.\n')
    expected = sum(len(re.findall(r'@Test\s+fun\s+', data.decode())) for data in tests.values())
    J.CLASSES = CLASSES
    with tempfile.TemporaryDirectory(prefix='bb5-idle-jvm-') as directory:
        checks = J.Checks(Path(directory))
        checks.files = [MAIN/name for name in SOURCES]+list(tests)+[
            ROOT/'test/native/idle_timer_stubs/runtime.kt', checks.runner]
        failure = None
        try:
            checks.compile()
            for label, _, _, klass, method in CONTROLS:
                result = checks.run(klass, method)
                log(red, 'baseline='+label+' result='+str(result))
                require(result == (0, 1, 0, 0, True), 'bb5_original_not_green_'+label)
            for label, source, replacements, klass, method in CONTROLS:
                require(all((MAIN/name).read_bytes() == data for name,data in originals.items()),
                        'bb5_source_freeze_changed')
                require(all(path.read_bytes() == data for path,data in tests.items()),
                        'bb5_test_freeze_changed')
                mutated = originals[source]
                for before, after in replacements:
                    mutated = mutated.replace(before.encode(), after.encode(), 1)
                (MAIN/source).write_bytes(mutated)
                checks.compile()
                result = checks.run(klass, method)
                log(red, 'red='+label+' result='+str(result))
                require(result == (1, 1, 1, 1, False), 'bb5_not_behavioral_red_'+label)
                (MAIN/source).write_bytes(originals[source])
        except Exception as error:
            failure = error
        finally:
            for name,data in originals.items():
                (MAIN/name).write_bytes(data)
        require(all((MAIN/name).read_bytes() == data for name,data in originals.items()),
                'bb5_exact_restoration_failed')
        checks.compile()
        result = checks.run()
        log(restored, 'restored='+str(result)+' expectedTests='+str(expected))
        require(result == (0, expected, 0, 0, True), 'bb5_restored_gate_failed')
        if failure is not None:
            raise failure
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
