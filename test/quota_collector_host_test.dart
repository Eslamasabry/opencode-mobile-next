import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/quota_collector_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// In-app Ubuntu as the collector host sees it: a call log and canned answers.
class _FakeLinux extends BuiltinLinux {
  bool installed = true;
  String nodeAnswer = 'qc node=22';
  List<String> services = [];
  final calls = <String>[];
  final scripts = <String>[];
  String? serviceScript;
  int? servicePort;

  @override
  Future<BuiltinLinuxStatus> status() async {
    calls.add('status');
    return BuiltinLinuxStatus(
      installed: installed,
      phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
      services: services,
    );
  }

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async {
    scripts.add(script);
    if (script.contains('process.versions.node')) {
      calls.add('node');
      return BuiltinLinuxRunResult(exitCode: 0, output: nodeAnswer);
    }
    if (script.contains('qc files=ok')) {
      calls.add('lock');
      return const BuiltinLinuxRunResult(exitCode: 0, output: 'qc files=ok');
    }
    if (script.contains('keys.mjs')) {
      calls.add('keys');
      return const BuiltinLinuxRunResult(
        exitCode: 0,
        output: 'qc keys=ok codex=opencode glm=1 minimax=0',
      );
    }
    if (script.contains('qc removed')) {
      calls.add('rm');
      return const BuiltinLinuxRunResult(exitCode: 0, output: 'qc removed');
    }
    calls.add('run');
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async {
    calls.add('start:$name');
    scripts.add(script);
    serviceScript = script;
    servicePort = port;
    services = [...services, name];
  }

  @override
  Future<void> stopService(String name) async {
    calls.add('stop:$name');
    services = [...services]..remove(name);
  }

  @override
  Future<String> serviceLog(String name, {int tailBytes = 16384}) async =>
      'QUOTA_FIXTURE_LOG';
}

ServerProfile _profile({String id = 'builtin', ServerFlavor? flavor}) =>
    ServerProfile(
        id: id,
        name: 'In-app Ubuntu',
        baseUrl: BuiltinLinux.serverUrl,
        flavor: flavor ?? ServerFlavor.v2,
      )
      ..username = BuiltinLinux.serverUsername
      ..password = 'basic-fixture-password';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late Map<String, String> secureValues;
  late Directory root;
  late _FakeLinux linux;
  late SharedPreferences prefs;
  var collectorText = '// collector v1\n';

  InAppQuotaCollector host({bool probeOk = true}) => InAppQuotaCollector(
    prefs: prefs,
    linux: linux,
    secure: const FlutterSecureStorage(),
    loadCollector: () async => collectorText,
    rootfs: () async => root,
    probe: (_) async => probeOk,
    supported: true,
    probeAttempts: 2,
    probeDelay: Duration.zero,
  );

  setUp(() async {
    secureValues = {};
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            final args = (call.arguments as Map).cast<String, Object?>();
            switch (call.method) {
              case 'read':
                return secureValues[args['key']];
              case 'write':
                secureValues[args['key']! as String] = args['value']! as String;
                return null;
              case 'delete':
                secureValues.remove(args['key']);
                return null;
              case 'readAll':
                return secureValues;
            }
            return null;
          },
        );
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    root = await Directory.systemTemp.createTemp('oc-quota-host-');
    linux = _FakeLinux();
    collectorText = '// collector v1\n';
  });

  tearDown(() async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
    await root.delete(recursive: true);
  });

  String stagedToken() => File(
    '${root.path}${InAppQuotaCollector.tokenPath}',
  ).readAsStringSync().trim();

  group('set-up order', () {
    test('checks Node, copies, locks, finds sign-ins, then starts', () async {
      final steps = <QuotaSetupStep>[];
      await host().setUp(_profile(), onStep: steps.add);
      expect(linux.calls, ['status', 'node', 'lock', 'keys', 'start:quota']);
      expect(steps, [
        QuotaSetupStep.checking,
        QuotaSetupStep.copying,
        QuotaSetupStep.signIns,
        QuotaSetupStep.starting,
      ]);
      expect(linux.servicePort, 4195);
      expect(
        File(
          '${root.path}${InAppQuotaCollector.collectorPath}',
        ).readAsStringSync(),
        collectorText,
      );
      expect(prefs.getBool(InAppQuotaCollector.enabledKey('builtin')), isTrue);
    });

    test('running it again keeps the token and the unchanged files', () async {
      await host().setUp(_profile());
      final token = stagedToken();
      final file = File('${root.path}${InAppQuotaCollector.collectorPath}');
      final old = DateTime.utc(2001);
      file.setLastModifiedSync(old);
      await host().setUp(_profile());
      expect(stagedToken(), token);
      expect(secureValues['oc.quotaToken.builtin'], token);
      expect(file.lastModifiedSync().toUtc(), old);
    });

    test('a changed collector is copied again', () async {
      await host().setUp(_profile());
      collectorText = '// collector v2\n';
      await host().setUp(_profile());
      expect(
        File(
          '${root.path}${InAppQuotaCollector.collectorPath}',
        ).readAsStringSync(),
        collectorText,
      );
    });

    test('Node 19 and a missing Node stop before anything is copied', () async {
      linux.nodeAnswer = 'qc node=19';
      await expectLater(
        host().setUp(_profile()),
        throwsA(
          isA<QuotaSetupException>().having(
            (e) => e.failure,
            'failure',
            QuotaSetupFailure.nodeTooOld,
          ),
        ),
      );
      linux.nodeAnswer = 'qc node=';
      await expectLater(
        host().setUp(_profile()),
        throwsA(
          isA<QuotaSetupException>().having(
            (e) => e.failure,
            'failure',
            QuotaSetupFailure.noNode,
          ),
        ),
      );
      expect(linux.calls.where((c) => c.startsWith('start')), isEmpty);
      expect(secureValues, isEmpty);
      expect(prefs.getBool(InAppQuotaCollector.enabledKey('builtin')), isNull);
    });

    test('Ubuntu not ready is its own failure', () async {
      linux.installed = false;
      await expectLater(
        host().setUp(_profile()),
        throwsA(
          isA<QuotaSetupException>().having(
            (e) => e.failure,
            'failure',
            QuotaSetupFailure.linuxNotReady,
          ),
        ),
      );
    });

    test('a helper that never answers fails with the log in Details', () async {
      await expectLater(
        host(probeOk: false).setUp(_profile()),
        throwsA(
          isA<QuotaSetupException>()
              .having((e) => e.failure, 'failure', QuotaSetupFailure.start)
              .having((e) => e.detail, 'detail', 'QUOTA_FIXTURE_LOG'),
        ),
      );
      expect(prefs.getBool(InAppQuotaCollector.enabledKey('builtin')), isNull);
    });
  });

  group('secrets', () {
    test('the token is long, random, in secure storage, 0600-staged', () async {
      await host().setUp(_profile());
      final token = secureValues['oc.quotaToken.builtin']!;
      expect(token.length, greaterThanOrEqualTo(48));
      expect(RegExp(r'^[A-Za-z0-9_-]+$').hasMatch(token), isTrue);
      expect(stagedToken(), token);
      expect(
        linux.scripts.any(
          (s) =>
              s.contains('chmod 600') &&
              s.contains(InAppQuotaCollector.tokenPath),
        ),
        isTrue,
      );
    });

    test('no command or service script holds the token or a key', () async {
      await host().setUp(_profile());
      final token = secureValues['oc.quotaToken.builtin']!;
      for (final script in linux.scripts) {
        expect(script, isNot(contains(token)));
        expect(script, isNot(contains('Bearer')));
      }
      final service = linux.serviceScript!;
      // Files only: paths in, values never.
      expect(
        service,
        contains(
          'OCMN_QUOTA_READ_TOKEN_FILE=/root/.oc-builtin/quota/read.token',
        ),
      );
      expect(service, contains('OCMN_GLM_KEY_FILE='));
      expect(service, contains('/root/.local/share/opencode/auth.json'));
      expect(service, isNot(contains('KEY=sk')));
    });

    test('OpenCode 2 looks in its own data folder first', () {
      final script = host().serviceScript(_profile(flavor: ServerFlavor.v2));
      expect(
        script.indexOf('/root/.oc-opencode2/data/opencode/auth.json'),
        lessThan(script.indexOf('/root/.local/share/opencode/auth.json')),
      );
    });

    test('the secure slot and the switch are swept with the profile', () async {
      await host().setUp(_profile());
      final store = ProfileStore(prefs: prefs);
      expect(
        store.profileScopedPreferenceKeys('builtin'),
        contains(InAppQuotaCollector.enabledKey('builtin')),
      );
      await store.upsert(_profile());
      expect(secureValues.keys, contains('oc.quotaToken.builtin'));
      await store.remove('builtin');
      expect(secureValues.keys, isNot(contains('oc.quotaToken.builtin')));
      await store.removeScopedPreferences('builtin');
      expect(prefs.getBool(InAppQuotaCollector.enabledKey('builtin')), isNull);
    });
  });

  group('running and turning off', () {
    test('a set-up helper that is not running comes back', () async {
      await host().setUp(_profile());
      linux.services = [];
      linux.calls.clear();
      expect(await host().ensureRunning(_profile()), isTrue);
      expect(linux.calls, contains('start:quota'));
    });

    test('a helper that runs is left alone', () async {
      await host().setUp(_profile());
      linux.calls.clear();
      await host().ensureRunning(_profile());
      expect(linux.calls.where((c) => c.startsWith('start')), isEmpty);
    });

    test('nothing starts when it was never set up or was turned off', () async {
      expect(await host().ensureRunning(_profile()), isFalse);
      await host().setUp(_profile());
      await host().turnOff('builtin');
      linux.calls.clear();
      expect(await host().ensureRunning(_profile()), isFalse);
      expect(linux.calls.where((c) => c.startsWith('start')), isEmpty);
    });

    test('turning off stops it and removes files, token and switch', () async {
      await host().setUp(_profile());
      await host().turnOff('builtin');
      expect(linux.calls, containsAllInOrder(['stop:quota', 'rm']));
      expect(secureValues, isEmpty);
      expect(prefs.getBool(InAppQuotaCollector.enabledKey('builtin')), isNull);
      expect(await InAppQuotaCollector.readToken('builtin'), isNull);
    });
  });

  group('the sign-in helper inside Ubuntu (real Node)', () {
    late Directory work;
    late bool nodeOk;

    setUpAll(() async {
      try {
        final result = await Process.run('node', ['--version']);
        final major = int.tryParse(
          (result.stdout as String)
              .trim()
              .replaceFirst('v', '')
              .split('.')
              .first,
        );
        nodeOk = major != null && major >= 20;
      } on Object {
        nodeOk = false;
      }
    });

    setUp(() async {
      work = await Directory.systemTemp.createTemp('oc-quota-keys-');
      File(
        '${work.path}/keys.mjs',
      ).writeAsStringSync(InAppQuotaCollector.keysHelperSource);
    });
    tearDown(() => work.delete(recursive: true));

    Future<ProcessResult> runHelper(List<String> candidates) =>
        Process.run('node', [
          '${work.path}/keys.mjs',
          work.path,
          '${work.path}/agents',
          ...candidates,
        ]);

    test(
      'writes 0600 key files, prints counts only, prefers OpenCode',
      () async {
        if (!nodeOk) return;
        final auth = File('${work.path}/auth.json')
          ..writeAsStringSync(
            jsonEncode({
              'zai-coding-plan': {
                'type': 'api',
                'key': 'FIXTURE-GLM-KEY-VALUE',
              },
              'openai': {
                'type': 'oauth',
                'access': 'FIXTURE-ACCESS',
                'accountId': 'acct',
                'expires': 1,
              },
            }),
          );
        File('${work.path}/agents/p1/codex/auth.json')
          ..createSync(recursive: true)
          ..writeAsStringSync(
            jsonEncode({
              'tokens': {'access_token': 'A', 'account_id': 'B'},
            }),
          );
        final result = await runHelper([auth.path]);
        expect(result.exitCode, 0);
        final out = '${result.stdout}${result.stderr}';
        expect(out, contains('codex=opencode glm=1 minimax=0'));
        expect(out, isNot(contains('FIXTURE')));
        final glm = File('${work.path}/glm.key');
        expect(glm.readAsStringSync().trim(), 'FIXTURE-GLM-KEY-VALUE');
        expect(glm.statSync().mode & 0x1ff, 384); // 0600
        expect(File('${work.path}/minimax.key').existsSync(), isFalse);
        final env = File('${work.path}/auth.env').readAsStringSync();
        expect(env, contains("OCMN_QUOTA_AUTH_FORMAT=opencode"));
        expect(env, contains(auth.path));
        expect(env, isNot(contains('FIXTURE')));
      },
    );

    test('uses an agent Codex sign-in only when OpenCode has none', () async {
      if (!nodeOk) return;
      final auth = File('${work.path}/auth.json')
        ..writeAsStringSync(jsonEncode({}));
      final agent = File('${work.path}/agents/p1/codex/auth.json')
        ..createSync(recursive: true)
        ..writeAsStringSync(
          jsonEncode({
            'tokens': {'access_token': 'A', 'account_id': 'B'},
          }),
        );
      final result = await runHelper([auth.path]);
      expect('${result.stdout}', contains('codex=agent'));
      final env = File('${work.path}/auth.env').readAsStringSync();
      expect(env, contains('OCMN_QUOTA_AUTH_FORMAT=codex'));
      expect(env, contains(agent.path));
    });

    test('a key that was removed in OpenCode is removed here', () async {
      if (!nodeOk) return;
      final auth = File('${work.path}/auth.json')
        ..writeAsStringSync(
          jsonEncode({
            'minimax-coding-plan': {'type': 'api', 'key': 'FIXTURE-MM'},
          }),
        );
      await runHelper([auth.path]);
      expect(File('${work.path}/minimax.key').existsSync(), isTrue);
      auth.writeAsStringSync(jsonEncode({}));
      await runHelper([auth.path]);
      expect(File('${work.path}/minimax.key').existsSync(), isFalse);
    });
  });
}
