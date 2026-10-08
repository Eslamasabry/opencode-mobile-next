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
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentAuthOtherOwners.kt',
        'test/native/phone_agent_auth_other_owners_harness.kt',
      ],
    );
  });
  for (final scenario in [
    'registered-helper',
    'private-excluded',
    'unknown-at-launch',
    'dead-pruned',
    'reused-pruned',
    'unreadable-excluded',
    'unreadable-launch',
    'invalid-birth',
  ]) {
    test('native other-agent ownership: $scenario', () async {
      final result = await Process.run('java', [
        '-Xmx64m',
        '-jar',
        jar,
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
    }, skip: skip);
  }
}
