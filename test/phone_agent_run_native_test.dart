import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// `KOTLINC`, then `kotlinc` on PATH, then SDKMAN's install; null when absent.
String? _kotlinc() {
  final configured = Platform.environment['KOTLINC'];
  if (configured != null && configured.isNotEmpty) return configured;
  for (final dir in (Platform.environment['PATH'] ?? '').split(':')) {
    if (dir.isNotEmpty && File('$dir/kotlinc').existsSync()) return 'kotlinc';
  }
  final sdkman =
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  return File(sdkman).existsSync() ? sdkman : null;
}

void main() {
  final found = _kotlinc();
  final compiler = found ?? 'kotlinc';
  // Without a Kotlin compiler the harness cannot run; skip, don't fail.
  final skip = found == null
      ? 'kotlinc not found (set KOTLINC); native harness not run'
      : null;
  late Directory temporary;
  late String jar;

  setUpAll(() async {
    if (skip != null) return;
    temporary = await Directory.systemTemp.createTemp('oc-agent-run-native-');
    jar = '${temporary.path}/run.jar';
    const sources =
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
    final linux = await File('$sources/BuiltinLinux.kt').readAsString();
    final begin = linux.indexOf('    fun run(script: String,');
    final end = linux.indexOf('    /**\n     * Starts [script]', begin);
    expect(begin, greaterThanOrEqualTo(0));
    expect(end, greaterThan(begin));
    final method = linux.substring(begin, end);
    final runConstants = RegExp(
      r'private const val (?:RUN_ADMISSION_WINDOW_MS|PROCESS_POLL_MS) = [^\n]+',
    ).allMatches(linux).map((match) => match.group(0)!).join('\n');
    expect(runConstants.split('\n'), hasLength(2));
    final fixture = File('${temporary.path}/ProductionRun.kt');
    await fixture.writeAsString('''
package io.github.eslamasabry.opencode_mobile
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
enum class InstallerOperation { CHECK, INSTALL }
enum class InstallerTarget { OPENCODE1, OPENCODE2, PASEO, CLAUDE, LEGACY_CLAUDE }
class BuiltinLinux(private val process: RunProcess) {
    data class Result(val exitCode: Int, val output: String)
    private var installerProcess: Process? = null
    // Admission ownership is exercised by installer_admission_native_test.
    private fun reclaimDeadInstaller(): Boolean = false
    fun start(script: String, log: File?, agentUser: Boolean): Process = process
    fun startInstaller(script: String, targets: Set<InstallerTarget>, operation: InstallerOperation,
                       agentUser: Boolean): Process {
        installerProcess = process
        return start(script, null, agentUser)
    }
    fun stopInstaller(process: Process) { stopTree(process) }
    fun finishInstaller(process: Process) { if (installerProcess === process) installerProcess = null }
    $method
    companion object {
        const val TAG = "test"
        const val OUTPUT_CAP = 16384
        $runConstants
        fun stopTree(process: Process) { process.destroyForcibly() }
    }
}
''');
    final clock = File('${temporary.path}/SystemClock.kt');
    await clock.writeAsString('''
package android.os
object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1000000L
}
''');
    final log = File('${temporary.path}/Log.kt');
    await log.writeAsString('''
package android.util
object Log {
    var denied = false
    var calls = 0
    fun i(tag: String, message: String) {
        calls++
        if (denied) throw SecurityException("log denied")
    }
}
''');
    final compiled = await Process.run(compiler, [
      fixture.path,
      clock.path,
      log.path,
      '$sources/PhoneAgentCheckOutput.kt',
      'test/native/phone_agent_run_harness.kt',
      '-include-runtime',
      '-d',
      jar,
    ]);
    expect(compiled.exitCode, 0, reason: '${compiled.stderr}');
  });

  tearDownAll(() async {
    if (skip != null) return;
    await temporary.delete(recursive: true);
  });

  for (final scenario in [
    'receipts',
    'reader-failure',
    'stdin-failure',
    'wait-failure',
    'log-failure',
    'timeout',
  ]) {
    test('native agent setup run: $scenario', skip: skip, () async {
      final result = await Process.run(Platform.environment['JAVA'] ?? 'java', [
        '-jar',
        jar,
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, 'PASS $scenario\n');
    });
  }
}
