import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'native/kotlin_jar_cache.dart';

void main() {
  final compiler =
      Platform.environment['KOTLINC'] ??
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  final skip = !File(compiler).existsSync()
      ? 'kotlinc unavailable; native host not checked'
      : null;
  late Directory temporary;
  late String jar;
  setUpAll(() async {
    if (skip != null) return;
    temporary = Directory.systemTemp.createTempSync('host-native-test-');
    final runner = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt',
    ).readAsStringSync();
    final guard = runner
        .substring(
          runner.indexOf('object SetupDiskSpace {'),
          runner.indexOf('/**\n * Runs a phone setup job'),
        )
        .trim();
    final helper = File('${temporary.path}/disk.kt')
      ..writeAsStringSync(
        'package io.github.eslamasabry.opencode_mobile\nimport java.io.File\n$guard\n',
      );
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentHost.kt',
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentInventory.kt',
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentChecks.kt',
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PaseoStaleLock.kt',
        helper.path,
        'test/native/phone_agent_host_harness.kt',
        'test/native/phone_agent_host_stubs/os.kt',
        'test/native/phone_agent_host_stubs/linux.kt',
      ],
    );
  });
  tearDownAll(() {
    if (skip == null) temporary.deleteSync(recursive: true);
  });
  for (final scenario in [
    'low-space-launch',
    'healthy-launch',
    'exited-at-once',
    'stale-paseo-lock',
    'answering-paseo-lock',
    'concurrent-start',
    'partial-inventory',
    'helper-version-restart',
    'helper-invalid-update',
    'helper-busy-update',
    'helper-unowned-update',
    'helper-other-profile',
    'helper-restart-failure',
    'helper-guarded-stop',
  ]) {
    test('native phone host: $scenario', skip: skip, () async {
      final result = await Process.run('java', [
        '-cp',
        jar,
        'io.github.eslamasabry.opencode_mobile.Phone_agent_host_harnessKt',
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, contains('PASS $scenario'));
    });
  }
}
