import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'native/kotlin_jar_cache.dart';

void main() {
  final configured = Platform.environment['KOTLINC'];
  final sdkman =
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  final compiler = configured?.isNotEmpty == true
      ? configured
      : File(sdkman).existsSync()
      ? sdkman
      : null;
  String? jar;
  const scenarios = [
    'event-foreground-before-restore',
    'event-dispatch-denied',
    'event-stale-settles',
    'event-foreground-denied',
    'event-stop-cancels',
    'event-untrusted-ignored',
    'setup-main-denied',
    'daemon-main-denied',
    'setup-channel-denied',
    'daemon-timeout-denied',
    'daemon-stop-denied',
    'result-notification-denied',
    'dispatch-denied',
    'armed-null-sticky',
    'unarmed-null-rejected',
    'restoration-disarmed-not-sticky',
    'explicit-unarmed-not-sticky',
    'daemon-timeout-async-revocation',
    'daemon-stop-async-revocation',
    'daemon-request-threw-after-revocation',
    'setup-timeout-async-revocation',
    'destroy-revokes-kind',
    'localized-copy-ar',
    'localized-copy-en',
  ];

  setUpAll(() async {
    if (compiler == null) return;
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupService.kt',
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinServerService.kt',
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinRestoreReceiver.kt',
        'test/native/phone_agent_service_harness.kt',
        ...(Directory(
            'test/native/phone_agent_service_stubs',
          ).listSync().whereType<File>().map((file) => file.path).toList()
          ..sort()),
      ],
    );
  });

  for (final scenario in scenarios) {
    test(
      'native agent service callback: $scenario',
      skip: compiler == null ? 'kotlinc not available' : null,
      () async {
        final result = await Process.run(Platform.environment['JAVA'] ?? 'java', [
          '-Xmx64m',
          '-cp',
          jar!,
          'io.github.eslamasabry.opencode_mobile.Phone_agent_service_harnessKt',
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
