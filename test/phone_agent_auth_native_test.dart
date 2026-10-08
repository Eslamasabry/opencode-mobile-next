import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_scripts.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

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
      ? 'kotlinc unavailable; BB8 native authentication not checked'
      : null;
  late Directory temporary;
  late String jar;
  late String jsonJar;
  const native =
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
  const scenarios = [
    'request-admission',
    'request-rejection',
    'projection-valid',
    'projection-rejection',
    'private-success',
    'invalid-no-launch',
    'prepare-denied',
    'start-failure',
    'timeout',
    'logout-timeout',
    'wait-failure',
    'reader-failure',
    'output-overflow',
    'output-overflow-timeout',
    'blocked-profile',
    'retained-dead-root',
    'deletion-during-run',
    'tree-dead-root',
    'tree-pid-reuse',
    'tree-zombie',
    'tree-unconfirmed-drain',
    'tree-missing-identity',
    'tree-inventory-failure',
    'tree-root-only-then-orphan',
    'tree-signal-reuse',
    'tree-failed-start-unknown',
    'outside-helper-after-baseline',
    'outside-unknown-orphan',
    'outside-wrong-root-cookie',
    'outside-reused-root-cookie',
    'outside-private-identities-win',
    'outside-retained-helper-child',
    'outside-reused-helper-child',
    'outside-registry-failure',
    'private-success-helper-concurrent',
    'cold-registered-owner',
    'cold-unknown-owner',
    'deadline-absolute-probe',
    'deadline-absolute-logout',
    'inventory-valid',
    'inventory-unreadable',
    'inventory-malformed',
    'inventory-vanished',
    'inventory-cross-uid',
    'lock-empty-exact',
    'lock-nonempty-retained',
    'lock-foreign-symlink',
  ];

  setUpAll(() async {
    if (compiler == null) return;
    temporary = await Directory.systemTemp.createTemp('oc-bb8-auth-native-');
    final configured =
        Platform.environment['OC_AUTH_JSON_JAR'] ??
        Platform.environment['JSON_JAR'];
    if (configured != null) {
      jsonJar = configured;
    } else {
      final cache = Directory(
        '${Platform.environment['GRADLE_USER_HOME'] ?? '${Platform.environment['HOME']}/.gradle'}/caches/modules-2/files-2.1/org.json/json',
      );
      final candidates = cache.existsSync()
          ? (cache
                .listSync(recursive: true)
                .whereType<File>()
                .where((file) => file.path.endsWith('.jar'))
                .map((file) => file.path)
                .toList()
              ..sort())
          : <String>[];
      expect(
        candidates,
        isNotEmpty,
        reason: 'Existing org.json jar required; set OC_AUTH_JSON_JAR',
      );
      jsonJar = candidates.first;
    }
    const home = '/home/oc/.oc-profiles/synthetic-profile';
    for (final id in [
      'claude',
      'codex',
      'gemini',
      'qwen',
      'goose',
      'omp-acp',
      'fx',
    ]) {
      final agent = AgentCatalog.builtIn.byId(id)!;
      for (final logout in [false, if (id == 'claude' || id == 'fx') true]) {
        final action = logout ? 'logout' : 'probe';
        final command = logout
            ? AgentPhoneScripts.signOut(agent)
            : AgentPhoneScripts.authProbe(agent);
        final script =
            "export HOME='$home' CLAUDE_CONFIG_DIR='$home/claude' CODEX_HOME='$home/codex' DISABLE_AUTOUPDATER=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1\n$command";
        await File('${temporary.path}/$id-$action.txt').writeAsString(script);
      }
    }
    for (final name in [
      'PhoneAgentAuthRequest',
      'PhoneAgentAuthProjection',
      'PhoneAgentAuthJson',
      'PhoneAgentAuthProbe',
      'PhoneAgentAuthCapture',
      'PhoneAgentAuthProcessTree',
      'PhoneAgentAuthColdOwner',
      'PhoneAgentAuthDeadline',
      'PhoneAgentAuthInventory',
      'PhoneAgentPaths',
      'PhoneAgentAuthLock',
    ]) {
      expect(
        File('$native/$name.kt').existsSync(),
        isTrue,
        reason: 'Missing production private native auth bridge: $name',
      );
    }
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        '$native/PhoneAgentAuthRequest.kt',
        '$native/PhoneAgentAuthProjection.kt',
        '$native/PhoneAgentAuthJson.kt',
        '$native/PhoneAgentAuthProbe.kt',
        '$native/PhoneAgentAuthCapture.kt',
        '$native/PhoneAgentAuthProcessTree.kt',
        '$native/PhoneAgentAuthColdOwner.kt',
        '$native/PhoneAgentAuthDeadline.kt',
        '$native/PhoneAgentAuthInventory.kt',
        '$native/PhoneAgentPaths.kt',
        '$native/PhoneAgentAuthLock.kt',
        'test/native/phone_agent_auth_harness.kt',
      ],
      extraArgs: ['-cp', jsonJar],
    );
  });

  tearDownAll(() async {
    if (compiler != null) await temporary.delete(recursive: true);
  });

  for (final scenario in scenarios) {
    test('native private agent auth: $scenario', skip: skip, () async {
      final result = await Process.run(Platform.environment['JAVA'] ?? 'java', [
        '-cp',
        '$jar:$jsonJar',
        'io.github.eslamasabry.opencode_mobile.Phone_agent_auth_harnessKt',
        scenario,
        temporary.path,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, 'PASS $scenario\n');
      expect(result.stderr, isEmpty);
    });
  }
}
