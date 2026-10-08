import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'native/kotlin_jar_cache.dart';

String? _compiler() {
  final configured = Platform.environment['KOTLINC'];
  if (configured != null && configured.isNotEmpty) return configured;
  for (final path in (Platform.environment['PATH'] ?? '').split(':')) {
    if (path.isNotEmpty && File('$path/kotlinc').existsSync()) {
      return '$path/kotlinc';
    }
  }
  final sdkman =
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  return File(sdkman).existsSync() ? sdkman : null;
}

void main() {
  final compiler = _compiler();
  final skip = compiler == null
      ? 'kotlinc unavailable; BB8 main-thread callback not checked'
      : null;
  late String jar;

  setUpAll(() async {
    if (compiler == null) return;
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        'android/app/src/androidTest/kotlin/io/github/eslamasabry/opencode_mobile/Bb8MainCallback.kt',
        'test/native/bb8_main_callback_harness.kt',
      ],
    );
  });

  for (final scenario in ['callback-success', 'callback-failure']) {
    test('native BB8 main callback: $scenario', skip: skip, () async {
      final result = await Process.run(Platform.environment['JAVA'] ?? 'java', [
        '-cp',
        jar,
        'io.github.eslamasabry.opencode_mobile.Bb8_main_callback_harnessKt',
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, 'PASS $scenario\n');
      expect(result.stderr, isEmpty);
    });
  }
}
