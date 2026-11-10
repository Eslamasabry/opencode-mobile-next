#!/usr/bin/env python3
"""Focused BB4 JVM regressions; coordinator owns the source freeze and checks.

Run: tool/qa/machine_lock.sh build -- python3 tool/qa/bb4_native_regression.py
Agent controls: append --agent-scope-only to that command.

Temporarily mutates only WorkLeases.kt/NativeWorkLeaseHost.kt, restores their
original bytes in finally, and runs both affected classes green. Android API37
classes are only on the classpath: tests use the injected fake host constructor.
No Gradle, ADB, APK, signing or real Android methods are invoked.
"""
from pathlib import Path
import hashlib
import os
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile'
TEST = ROOT / 'android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile'
EVIDENCE = ROOT / 'docs/qa/BB4-agent-work-2026-10-08'
PACKAGE = 'io.github.eslamasabry.opencode_mobile.'
JDK = Path('/home/eslam/Storage/tmp/codex-audit3-temurin17/jdk-17.0.20.1+1')
KOTLIN = Path('/home/eslam/.sdkman/candidates/kotlin/2.3.20')
ANDROID = Path('/home/eslam/Android/Sdk/platforms/android-37.0/android.jar')
CACHE = Path('/home/eslam/.gradle/caches/modules-2/files-2.1')
JUNIT = CACHE / 'junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar'
HAMCREST = CACHE / 'org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar'
CLASSES = ('WorkLeasesTest', 'NativeWorkLeaseHostTest', 'SetupWorkScopesTest')
EXPECTED_TESTS = 73
# Each control selects exactly one behavior test, not the whole mutant suite.
# Removing the lifetime cap requires removing its renewal and sweep enforcement
# together; deleting only one leaves the independent bound intact.
MUTATIONS = (
    ('whole_setup_child_inherits_admission', 'SetupWorkScopes.kt', (
        ('fun childNeedsLease(): Boolean = current.get() == null', 'fun childNeedsLease(): Boolean = true'),
    ), 'SetupWorkScopesTest', 'nextInstallerAfterWholeJobCapCannotCreateAReplacementHold'),
    ('setup_scope_one_entry', 'SetupWorkScopes.kt', (
        ('check(scope.entered.compareAndSet(false, true)) { "The phone setup could not start. Try again." }', 'Unit'),
    ), 'SetupWorkScopesTest', 'closedPreparedScopeDoesNotReadmitAndCannotBeEnteredTwice'),
    ('earlier_timer_preemption', 'NativeWorkLeaseHost.kt', (
        ('if (scheduled && scheduledAt <= due) return', 'if (scheduled) return'),
    ), 'NativeWorkLeaseHostTest', 'terminalObservationPreemptsPendingChatExpiryAndCancelledCallbackIsInert'),
    ('stale_timer_callback', 'NativeWorkLeaseHost.kt', (
        ('if (revision != scheduleRevision) return@task', 'Unit'),
    ), 'NativeWorkLeaseHostTest', 'terminalObservationPreemptsPendingChatExpiryAndCancelledCallbackIsInert'),
    ('unknown_terminal_retention', 'NativeWorkLeaseHost.kt', (
        ('internal fun pulse() {\n'
         '        // Read external lifecycle owners outside our guard: terminal creation holds its own monitor.\n'
         '        val running = try { terminalWork() } catch (_: Throwable) { null }',
         'internal fun pulse() {\n'
         '        // Read external lifecycle owners outside our guard: terminal creation holds its own monitor.\n'
         '        val running = try { terminalWork() } catch (_: Throwable) { emptyMap() }'),
    ), 'NativeWorkLeaseHostTest', 'unknownTerminalSnapshotCannotReviveCappedSameSessionOrRepeatProtection'),
    ('missing_wake_clear', 'NativeWorkLeaseHost.kt', (
        ('val lock = wake ?: run { leases.clear(); return }', 'val lock = wake ?: return'),
    ), 'NativeWorkLeaseHostTest', 'missingWakeCannotGrantCpuOrForegroundEligibilityForAnyWorkKind'),
    ('setup_kind_revocation', 'NativeWorkLeaseHost.kt', (
        ('fun revokeSetupWork() = synchronized(guard) { leases.releaseKind(WorkLeases.Kind.SETUP); updateWake() }',
         'fun revokeSetupWork() = synchronized(guard) { updateWake() }'),
    ), 'NativeWorkLeaseHostTest', 'setupServiceLossRevokesJobAndInstallerWhileIndependentChatRemains'),
    ('continuous_six_hour_cap', 'WorkLeases.kt', (
        ('val duration = minOf(holdMs, maxHoldMs, lease.hardDeadlineMs - now, aggregate - now)',
         'val duration = minOf(holdMs, maxHoldMs)'),
        ('if (aggregateDeadlineMs?.let { now >= it } == true)', 'if (false)'),
        ('leases.entries.removeAll { now >= it.value.expiresAtMs || now >= it.value.hardDeadlineMs }',
         'leases.entries.removeAll { now >= it.value.expiresAtMs }'),
    ), 'NativeWorkLeaseHostTest', 'automaticallyRenewedSetupAndSignInStopAtSixHourContinuousCap'),
)

AGENT_MUTATIONS = (
    ('agent_helper_exact_profile_isolation', 'NativeWorkLeaseHost.kt', (
        ('agentChats.keys.filter { it.profile == profile }.forEach { key ->',
         'agentChats.keys.filter { true }.forEach { key ->'),
    ), 'NativeWorkLeaseHostTest', 'helperLossClosesOnlyMatchingProfileWithoutRevivingItsRememberedRun'),
    ('agent_helper_running_admission', 'NativeWorkLeaseHost.kt', (
        ('setChat(agentChats, AgentChat(profile, name), on, holdMs, helperRunning)',
         'setChat(agentChats, AgentChat(profile, name), on, holdMs, true)'),
    ), 'NativeWorkLeaseHostTest', 'agentScopeRequiresValidOpaqueIdentityAndAnAdmittedHelper'),
    ('agent_server_loss_scope_isolation', 'NativeWorkLeaseHost.kt', (
        ('fun serverGone() = synchronized(guard) {',
         'fun serverGone() = synchronized(guard) {\n'
         '        agentChats.values.filterNotNull().forEach { leases.release(it) }'),
    ), 'NativeWorkLeaseHostTest', 'serverLossRevokesOnlyOpenCodeScopeAndDoesNotCloseAgentReplies'),
    ('logical_busy_independent_cpu_cap', 'NativeWorkLeaseHost.kt', (
        ('fun logicalWorkBusy(): Boolean? {', 'fun logicalWorkBusy(): Boolean? {\n        return held'),
    ), 'NativeWorkLeaseHostTest', 'cappedAndRevokedSetupRemainsLogicallyBusyUntilActualCompletion'),
    ('logical_busy_unknown_snapshot_refusal', 'NativeWorkLeaseHost.kt', (
        ('if (!unchanged || running == null || liveness.values.any { it == null }) null else false',
         'if (!unchanged || liveness.values.any { it == null }) null else false'),
    ), 'NativeWorkLeaseHostTest', 'unknownOwnerOrTerminalSnapshotCannotProveIdle'),
    ('agent_fresh_id_revocation_latch', 'NativeWorkLeaseHost.kt', (
        ('chatAdmissionOpen = false', 'chatAdmissionOpen = true'),
    ), 'NativeWorkLeaseHostTest', 'foregroundRevocationDeniesFreshChatIdsInBothScopesEvenWithRunningHelpers'),
    ('agent_stale_generation_rearm', 'NativeWorkLeaseHost.kt', (
        ('if (expectedGeneration != foregroundRevision) return@synchronized false', 'Unit'),
    ), 'NativeWorkLeaseHostTest', 'launchCapturedBeforeStopCannotAuthorizeAfterRevocation'),
    ('agent_zero_hold_retains_logical_key', 'NativeWorkLeaseHost.kt', (
        ('if (chats.containsKey(key)) chats[key] = null', 'chats.remove(key)'),
    ), 'NativeWorkLeaseHostTest', 'zeroHoldClosureRetainsBothExistingLogicalKeysWithoutCpuOrAdmission'),
)

RUNNER = """import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.Test
fun main(args: Array<String>) {
    val result = when (args.firstOrNull()) {
        "method" -> {
            check(args.size == 3)
            val type = Class.forName(args[1])
            check(type.getDeclaredMethod(args[2]).getAnnotation(Test::class.java) != null)
            JUnitCore().run(Request.method(type, args[2]))
        }
        "classes" -> {
            check(args.size >= 2)
            JUnitCore().run(Request.classes(*args.drop(1).map { Class.forName(it) }.toTypedArray()))
        }
        else -> error("runner_mode_invalid")
    }
    val assertions = result.failures.count { it.exception is AssertionError }
    println("RESULT run=${result.runCount} failures=${result.failureCount} assertions=$assertions successful=${result.wasSuccessful()}")
    if (!result.wasSuccessful()) kotlin.system.exitProcess(1)
}
"""


def require(condition, code):
    if not condition:
        raise RuntimeError(code)


def record(path, line):
    with path.open('a') as output:
        output.write(line + '\n')
    print(line, flush=True)


class Checks:
    def __init__(self, directory):
        self.jar = directory / 'bb4-lease-tests.jar'
        self.runner = directory / 'Bb4LeaseRunner.kt'
        self.runner.write_text(RUNNER)
        self.environment = os.environ.copy()
        for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'JAVA_OPTS', 'KOTLIN_RUNNER'):
            self.environment.pop(key, None)
        self.environment['JAVA_HOME'] = str(JDK)
        self.files = [MAIN / 'WorkLeases.kt', MAIN / 'NativeWorkLeaseHost.kt', MAIN / 'SetupWorkScopes.kt']
        self.files += [TEST / (name + '.kt') for name in CLASSES] + [self.runner]

    def invoke(self, command, timeout):
        # Keep all compiler/assertion text private; emit only fixed result fields.
        try:
            result = subprocess.run(command, cwd=ROOT, env=self.environment,
                                    capture_output=True, text=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            raise RuntimeError('bb4_jvm_command_timeout') from None
        require(len(result.stdout.encode()) + len(result.stderr.encode()) <= 65536, 'bb4_jvm_output_overflow')
        return result

    def compile(self):
        classpath = os.pathsep.join(map(str, (ANDROID, JUNIT)))
        result = self.invoke([str(KOTLIN / 'bin/kotlinc'), '-J-Xmx256m', '-J-XX:MaxMetaspaceSize=192m',
                              '-jvm-target', '17', '-cp', classpath, *map(str, self.files),
                              '-d', str(self.jar)], 120)
        require(result.returncode == 0, 'bb4_pure_kotlin_compile_failed')

    def run(self, klass=None, method=None):
        arguments = (['method', PACKAGE + klass, method] if klass else
                     ['classes', *[PACKAGE + name for name in CLASSES]])
        classpath = os.pathsep.join(map(str, (self.jar, ANDROID, JUNIT, HAMCREST,
                                             KOTLIN / 'lib/kotlin-stdlib.jar')))
        result = self.invoke([str(JDK / 'bin/java'), '-Xmx128m', '-XX:MaxMetaspaceSize=96m',
                              '-cp', classpath, 'Bb4LeaseRunnerKt', *arguments], 30)
        match = re.fullmatch(r'RESULT run=([0-9]+) failures=([0-9]+) assertions=([0-9]+) successful=(true|false)\n?', result.stdout)
        require(match is not None, 'bb4_junit_result_invalid')
        return result.returncode, int(match[1]), int(match[2]), int(match[3]), match[4] == 'true'


def main():
    selections = {
        (): MUTATIONS,
        ('--setup-scope-only',): MUTATIONS[:2],
        ('--agent-scope-only',): AGENT_MUTATIONS,
        ('--restored-only',): (),
    }
    arguments = tuple(sys.argv[1:])
    require(arguments in selections, 'bb4_unsupported_arguments')
    selected = selections[arguments]
    for artifact in (JDK / 'bin/java', KOTLIN / 'bin/kotlinc', ANDROID, JUNIT, HAMCREST,
                     KOTLIN / 'lib/kotlin-stdlib.jar'):
        require(artifact.is_file(), 'bb4_pinned_jvm_artifact_missing')
    sources = {name: MAIN / name for name in ('WorkLeases.kt', 'NativeWorkLeaseHost.kt', 'SetupWorkScopes.kt')}
    originals = {name: path.read_bytes() for name, path in sources.items()}
    tests = {TEST / (name + '.kt'): (TEST / (name + '.kt')).read_bytes() for name in CLASSES}
    for _, name, replacements, klass, method in selected:
        require(klass in CLASSES, 'bb4_mutation_test_class_invalid')
        test_text = tests[TEST / (klass + '.kt')].decode()
        methods = list(re.finditer(r'@Test\s+fun\s+' + re.escape(method) + r'\s*\(', test_text))
        require(len(methods) == 1, 'bb4_mutation_test_not_unique')
        following = re.search(r'@Test\s+fun\s+', test_text[methods[0].end():])
        end = methods[0].end() + following.start() if following else len(test_text)
        require(re.search(r'\bassert(?:Equals|True|False|Null|NotNull|Same|NotSame|Throws)\s*\(',
                          test_text[methods[0].end():end]) is not None,
                'bb4_mutation_test_assertion_missing')
        for before, after in replacements:
            require(originals[name].decode().count(before) == 1, 'bb4_mutation_guard_not_unique')
            require(before != after, 'bb4_mutation_no_change')
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    red = EVIDENCE / 'native-lease-jvm-red.txt'
    restored = EVIDENCE / 'native-lease-jvm-restored.txt'
    red.write_text('BB4 focused removed-fix controls; pure JVM; caller-held machine build lock and source freeze required.\n')
    restored.write_text('BB4 restored affected JVM classes; supplemental to actual Android Gradle checks.\n')
    record(red, 'toolchain=Temurin17.0.20.1+1 Kotlin2.3.20 AndroidAPI37.0 JUnit4.13.2 compilerHeapMiB=256 testHeapMiB=128')
    record(red, 'NoDeviceNoGradleNoApk=true assertionFailuresRequired=true')
    with tempfile.TemporaryDirectory(prefix='bb4-leases-jvm-') as directory:
        checks = Checks(Path(directory))
        failure = None
        controls = 0
        try:
            if selected:
                print('RUN original selected behavior tests', flush=True)
                checks.compile()
                for label, _, _, klass, method in selected:
                    code, count, failures, assertions, successful = checks.run(klass, method)
                    record(red, f'baseline={label} test={klass}.{method} exit={code} run={count} failures={failures} assertions={assertions}')
                    require(code == 0 and count == 1 and failures == assertions == 0 and successful,
                            'bb4_original_behavior_not_green')
            for label, name, replacements, klass, method in selected:
                for source, original in originals.items():
                    require(sources[source].read_bytes() == original, 'bb4_source_freeze_changed')
                for test, original in tests.items():
                    require(test.read_bytes() == original, 'bb4_test_freeze_changed')
                print('RUN red=' + label, flush=True)
                mutated = originals[name]
                for before, after in replacements:
                    require(mutated.count(before.encode()) == 1, 'bb4_mutation_guard_not_unique')
                    mutated = mutated.replace(before.encode(), after.encode(), 1)
                require(mutated != originals[name], 'bb4_mutation_no_change')
                try:
                    sources[name].write_bytes(mutated)
                    checks.compile()
                    code, count, failures, assertions, successful = checks.run(klass, method)
                    record(red, f'control={label} test={klass}.{method} exit={code} run={count} failures={failures} assertions={assertions}')
                    require(code == 1 and count == failures == assertions == 1 and not successful,
                            'bb4_removed_fix_not_observed')
                    controls += 1
                finally:
                    sources[name].write_bytes(originals[name])
        except BaseException as error:
            failure = error
            record(red, 'controls_complete=false')
        finally:
            for name, source in sources.items():
                source.write_bytes(originals[name])
                require(source.read_bytes() == originals[name], 'bb4_source_restoration_failed')
                record(restored, f'source={name} originalBytesRestored=true sha256={hashlib.sha256(originals[name]).hexdigest()}')
            for test, original in tests.items():
                require(test.read_bytes() == original, 'bb4_test_freeze_changed')
                record(restored, f'test={test.name} unchanged=true sha256={hashlib.sha256(original).hexdigest()}')
            print('RUN restored affected classes', flush=True)
            checks.compile()
            code, count, failures, assertions, successful = checks.run()
            record(restored, f'classes={",".join(CLASSES)} exit={code} run={count} failures={failures} assertions={assertions}')
            require(code == 0 and count == EXPECTED_TESTS and failures == assertions == 0 and successful,
                    'bb4_restored_focused_gate_failed')
        if failure is not None:
            raise failure
    record(red, f'PASS controls={controls} expectedSingleAssertionFailures={len(selected)} restoredGatePassed=true')
    record(restored, f'PASS originalSourceBytes=true focusedClasses={len(CLASSES)} tests={EXPECTED_TESTS}')


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Files/arguments and compiler output never enter this fixed failure line.
        code = str(error) if isinstance(error, RuntimeError) and re.fullmatch(r'bb4_[a-z_]{1,80}', str(error)) else 'bb4_regression_unavailable'
        print('FAIL ' + code, flush=True)
        raise SystemExit(1) from None
