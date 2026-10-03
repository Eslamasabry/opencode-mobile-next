import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final compiler = Platform.environment['KOTLINC'] ?? 'kotlinc';
  late Directory temporary;
  late String jar;

  setUpAll(() async {
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
    final fixture = File('${temporary.path}/ProductionRun.kt');
    await fixture.writeAsString('''
package io.github.eslamasabry.opencode_mobile
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
class BuiltinLinux(private val process: RunProcess) {
    data class Result(val exitCode: Int, val output: String)
    fun start(script: String, log: File?, agentUser: Boolean): Process = process
    $method
    companion object {
        const val TAG = "test"
        const val OUTPUT_CAP = 16384
        fun stopTree(process: Process) { process.destroyForcibly() }
    }
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
    test('native agent setup run: $scenario', () async {
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
