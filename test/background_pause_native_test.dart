import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'native/kotlin_jar_cache.dart';

void main() {
  final compiler =
      Platform.environment['KOTLINC'] ??
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  final skip = File(compiler).existsSync() ? null : 'kotlinc unavailable';
  late String jar;
  setUpAll(() async {
    if (skip != null) return;
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BackgroundPauseReceipt.kt',
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BackgroundPauseStore.kt',
        'test/native/background_pause_harness.kt',
        'test/native/background_pause_stubs/context.kt',
        'test/native/background_pause_stubs/activity.kt',
        'test/native/background_pause_stubs/os.kt',
        'test/native/background_pause_stubs/pause.kt',
      ],
    );
  });
  for (final scenario in [
    'timeout-reopen',
    'task-removal',
    'current-restriction',
    'user-stop',
    'old-exit-interruption',
    'intentional-clear',
    'failed-commit',
    'confirmed-start',
    'reason-eleven',
  ]) {
    test('native background receipt: $scenario', skip: skip, () async {
      final result = await Process.run('java', [
        '-cp',
        jar,
        'io.github.eslamasabry.opencode_mobile.Background_pause_harnessKt',
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, contains('PASS $scenario'));
    });
  }
}
