import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_scripts.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';

void main() {
  test('captured installed agents have final explicit account states', () {
    for (final entry in {
      'claude-signedIn': AgentAuthProbeState.signedIn,
      'fx-signedOut': AgentAuthProbeState.signedOut,
    }.entries) {
      final fixture =
          jsonDecode(
                File(
                  'test/fixtures/agent_auth/${entry.key}-device.json',
                ).readAsStringSync(),
              )
              as Map<String, dynamic>;
      final result = AgentAuthProbeResult.fromJson(
        fixture['probe'] as Map<String, dynamic>,
      );
      expect(result.state, entry.value);
      expect((fixture['capture'] as Map)['elapsedMs'], lessThan(10000));
    }
  });

  test('malformed and contradictory account replies remain errors', () {
    for (final data in <Map<String, dynamic>>[
      {},
      {'state': 'loading'},
      {'state': 'signedOut', 'accountDisplayName': 'account'},
      {'state': 'signedIn', 'accountDisplayName': 'bad\naccount'},
      {'state': 'signedIn', 'error': 'synthetic-secret'},
      {'state': 'error', 'error': 'synthetic-secret'},
    ]) {
      final result = AgentAuthProbeResult.fromJson(data);
      expect(result.state, AgentAuthProbeState.error);
      expect(result.error, AgentAuthProbeError.invalidResponse);
      expect(result.accountDisplayName, isNull);
    }
  });

  group('bounded private status scripts', () {
    late Directory root;
    late String base;
    late String home;
    setUp(() async {
      root = await Directory.systemTemp.createTemp('oc-auth-probe-');
      base = '${root.path}/oc';
      home = '$base/.oc-profiles/fixture';
      await Directory('$base/.local/bin').create(recursive: true);
      await Directory(home).create(recursive: true);
    });
    tearDown(() async => root.delete(recursive: true));

    Future<void> cli(String id, String body) async {
      final file = File('$base/.local/bin/$id');
      await file.writeAsString('#!/bin/sh\n$body\n');
      await Process.run('chmod', ['700', file.path]);
    }

    Future<AgentAuthProbeResult> run(String id, {bool logout = false}) async {
      final agent = AgentCatalog.builtIn.byId(id)!;
      final script = logout
          ? AgentPhoneScripts.signOut(agent)
          : AgentPhoneScripts.authProbe(agent);
      final result = await Process.run(
        'bash',
        ['-c', script.replaceAll('/home/oc', base)],
        environment: {
          'HOME': home,
          'CLAUDE_CONFIG_DIR': '$home/claude',
          'CODEX_HOME': '$home/codex',
          'PATH': '/usr/bin:/bin',
        },
        workingDirectory: home,
      );
      expect(result.exitCode, 0);
      expect(result.stderr, isEmpty);
      expect(result.stdout.toString(), isNot(contains('synthetic-secret')));
      return AgentAuthProbeResult.fromJson(
        jsonDecode(result.stdout as String) as Map<String, dynamic>,
      );
    }

    test(
      'Claude explicit signed-out status overrides successful exit',
      () async {
        await cli('claude', r'''
printf '%s\n' '{"loggedIn":false,"authMethod":"none","apiProvider":"firstParty"}'
exit 0
''');
        expect((await run('claude')).state, AgentAuthProbeState.signedOut);
      },
    );

    test('Claude status exposes only its dedicated account field', () async {
      await cli('claude', r'''
printf '%s\n' '{"loggedIn":true,"authMethod":"claude.ai","apiProvider":"firstParty","email":"test@example.invalid","accessToken":"synthetic-secret"}'
printf '%s\n' 'synthetic-secret' >&2
''');
      final result = await run('claude');
      expect(result.state, AgentAuthProbeState.signedIn);
      expect(result.accountDisplayName, 'test@example.invalid');
    });

    test('CLI failures never become a signed-out account', () async {
      await cli('claude', r'''
printf '%s\n' 'Run claude login synthetic-secret'
exit 1
''');
      expect((await run('claude')).error, AgentAuthProbeError.invalidResponse);
    });

    test('fx explicit source is independent of provider error text', () async {
      await cli('fx', r'''
printf '%s\n' '{"kind":"status","auth":"fx login","error":"Run fx login synthetic-secret"}'
''');
      expect((await run('fx')).state, AgentAuthProbeState.signedIn);
      await cli('fx', r'''
printf '%s\n' '{"kind":"status","auth":"missing"}'
''');
      expect((await run('fx')).state, AgentAuthProbeState.signedOut);
    });

    test(
      'fx expired and future unknown credential sources are errors',
      () async {
        await cli('fx', r'''
printf '%s\n' '{"kind":"status","auth":"fx login","auth_expired":true}'
''');
        expect((await run('fx')).error, AgentAuthProbeError.signInExpired);
        await cli('fx', r'''
printf '%s\n' '{"kind":"status","auth":"future source"}'
''');
        expect((await run('fx')).error, AgentAuthProbeError.invalidResponse);
      },
    );

    test(
      'unsupported agents reach a typed final error without launching',
      () async {
        for (final id in ['codex', 'gemini', 'qwen', 'goose', 'omp-acp']) {
          expect((await run(id)).error, AgentAuthProbeError.probeUnsupported);
        }
      },
    );

    test('missing installation is distinct from signed out', () async {
      expect((await run('fx')).error, AgentAuthProbeError.notInstalled);
    });

    test('a CLI that hangs reaches timedOut within ten seconds', () async {
      await cli('fx', 'sleep 60');
      final watch = Stopwatch()..start();
      expect((await run('fx')).error, AgentAuthProbeError.timedOut);
      expect(watch.elapsed, lessThan(const Duration(seconds: 10)));
    });

    test('successful logout exit requires a signed-out probe', () async {
      await cli('claude', r'''
if [ "$2" = logout ]; then printf '%s\n' 'synthetic-secret'; exit 0; fi
printf '%s\n' '{"loggedIn":true,"authMethod":"claude.ai","apiProvider":"firstParty"}'
''');
      expect(
        (await run('claude', logout: true)).error,
        AgentAuthProbeError.signOutFailed,
      );
    });

    test(
      'logout runs the agent command and confirms credentials are gone',
      () async {
        await cli('fx', r'''
if [ "$1" = logout ]; then touch "$HOME/logged-out"; exit 0; fi
if [ -f "$HOME/logged-out" ]; then
  printf '%s\n' '{"kind":"status","auth":"missing"}'
else
  printf '%s\n' '{"kind":"status","auth":"fx login"}'
fi
''');
        expect(
          (await run('fx', logout: true)).state,
          AgentAuthProbeState.signedOut,
        );
        expect(await File('$home/logged-out').exists(), isTrue);
      },
    );
  });
}
