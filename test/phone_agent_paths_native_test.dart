import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

// Compile the actual agent launch directory preparation with a fake Android
// filesDir anchor. This reproduces /data/user/0 resolving to /data/data without
// Android, root privileges, agents, network or account state.
void main() {
  final compiler = Platform.environment['KOTLINC'] ?? 'kotlinc';
  late Directory temporary;
  late String jar;

  setUpAll(() async {
    temporary = await Directory.systemTemp.createTemp('oc-agent-paths-native-');
    jar = '${temporary.path}/paths.jar';
    const sources =
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
    final linux = await File('$sources/BuiltinLinux.kt').readAsString();
    final begin = linux.indexOf('val agentRoot = if (agentUser)');
    final end = linux.indexOf('val command = listOf(', begin);
    expect(begin, greaterThanOrEqualTo(0));
    expect(end, greaterThan(begin));
    final preparation = linux.substring(begin, end);
    final fixture = File('${temporary.path}/ProductionPaths.kt');
    await fixture.writeAsString('''
package io.github.eslamasabry.opencode_mobile
import java.io.File
class FakeContext(val filesDir: File)
fun productionAgentRoot(home: File): File {
    val context = FakeContext(home.parentFile ?: error("No trusted anchor"))
    val agentUser = true
    $preparation
    return agentRoot ?: error("No agent root")
}
''');
    final helper = File('$sources/PhoneAgentPaths.kt');
    final compiled = await Process.run(compiler, [
      fixture.path,
      if (await helper.exists()) helper.path,
      'test/native/phone_agent_paths_harness.kt',
      '-include-runtime',
      '-d',
      jar,
    ]);
    expect(
      compiled.exitCode,
      0,
      reason: 'Agent launch path harness compilation: ${compiled.stderr}',
    );
  });

  tearDownAll(() async {
    await temporary.delete(recursive: true);
  });

  for (final scenario in [
    'anchor-alias',
    'managed-symlink',
    'managed-ancestor-symlink',
    'projects-symlink',
    'repeat',
  ]) {
    test('native agent launch directory: $scenario', () async {
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
