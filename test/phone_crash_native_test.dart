import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'native/kotlin_jar_cache.dart';

void main() {
  final compiler =
      Platform.environment['KOTLINC'] ??
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  final available = File(compiler).existsSync();
  late String jar;
  const native =
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
  setUpAll(() async {
    if (!available) return;
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        '$native/NativeChannelReplies.kt',
        '$native/NativeCrashStore.kt',
        'test/native/channel_reply_stubs/channel.kt',
        'test/native/phone_crash_harness.kt',
      ],
    );
  });
  for (final scenario in [
    'reply-once',
    'reply-detached',
    'crash-redaction',
    'crash-chain-and-storage-failure',
    'crash-symlinks',
  ]) {
    test(
      'native channel/crash: $scenario',
      skip: available ? null : 'Kotlin compiler unavailable',
      () async {
        final result =
            await Process.run(Platform.environment['JAVA'] ?? 'java', [
              '-cp',
              jar,
              'io.github.eslamasabry.opencode_mobile.Phone_crash_harnessKt',
              scenario,
            ]);
        expect(
          result.exitCode,
          0,
          reason: '${result.stdout}\n${result.stderr}',
        );
        expect(result.stdout, contains('PASS $scenario'));
      },
    );
  }
}
