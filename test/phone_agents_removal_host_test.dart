import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_install_guard.dart';
import 'package:opencode_mobile/builtin/agents/paseo_scripts.dart';
import 'package:opencode_mobile/builtin/agents/phone_agents_host.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/fake_setup_engine.dart';
import 'paseo_gateway_test.dart' show FakeDaemon;

const channel = MethodChannel(
  'io.github.eslamasabry.opencode_mobile/builtin_linux',
);
const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late SharedPreferences prefs;
  late BuiltinPhoneAgents host;
  FakeDaemon? daemon;
  final calls = <MethodCall>[];
  String? nativeJob;
  var removalExit = 0;
  Completer<void>? held;
  Completer<void>? holdArchitecture;
  Map? capturedParams;
  final specs = <Map>[];
  setUp(() async {
    SharedPreferences.setMockInitialValues({
      '${phoneAgentGatePrefix}phone': jsonEncode({
        'claude': {'fingerprint': 'keep'},
        'fx': {'fingerprint': 'keep'},
      }),
      '${phoneAgentInstallPrefix}phone': 'fx',
      'unrelated': 'keep',
    });
    prefs = await SharedPreferences.getInstance();
    daemon = null;
    calls.clear();
    specs.clear();
    nativeJob = null;
    removalExit = 0;
    held = null;
    holdArchitecture = null;
    capturedParams = null;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(
      storage,
      (call) async => call.method == 'read' ? 'a' * 64 : null,
    );
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      switch (call.method) {
        case 'agentAuthProbe':
          return {'state': 'signedIn'};
        case 'setupStatus':
          return nativeJob;
        case 'agentHostStatus':
          if (holdArchitecture != null) await holdArchitecture!.future;
          return {'abi': 'x86_64', 'running': true};
        case 'status':
          return {'installed': true, 'phase': 'ready'};
        case 'run':
          if (held != null) await held!.future;
          return {
            'exitCode': removalExit,
            'output': 'private output must not escape',
          };
        case 'startSetup':
          final args = call.arguments as Map;
          capturedParams = args['params'] as Map;
          specs.addAll((args['components'] as List).cast<Map>());
          nativeJob = jsonEncode({
            'jobId': args['jobId'],
            'state': 'done',
            'order': [for (final spec in specs) spec['id']],
            'components': {
              for (final spec in specs) spec['id']: {'state': 'done'},
            },
          });
          return null;
      }
      return null;
    });
    host = BuiltinPhoneAgents(
      profileId: 'phone',
      prefs: prefs,
      loadLock: () async =>
          File(PaseoPhoneScripts.packageLockAsset).readAsStringSync(),
      socketFactory: (_, _) async => daemon ??= FakeDaemon(),
    );
  });
  tearDown(() async {
    if (held != null && !held!.isCompleted) held!.complete();
    if (holdArchitecture != null && !holdArchitecture!.isCompleted) {
      holdArchitecture!.complete();
    }
    await host.dispose();
    await daemon?.close();
  });

  test(
    'removal uses fixed agent view and preserves account and gate metadata',
    () async {
      final before = {for (final key in prefs.getKeys()) key: prefs.get(key)};
      await host.removeAgent('fx');
      final run =
          calls.singleWhere((call) => call.method == 'run').arguments as Map;
      expect(run['agentUser'], true);
      expect(run['timeoutSeconds'], 30);
      expect(run['script'], contains('agent-fx'));
      expect(
        calls.map((call) => call.method),
        isNot(contains('stopAgentHost')),
      );
      expect({for (final key in prefs.getKeys()) key: prefs.get(key)}, before);
    },
  );
  test('Claude removal is never dispatched', () async {
    await expectLater(
      host.removeAgent('claude'),
      throwsA(isA<AgentHostException>()),
    );
    expect(calls, isEmpty);
  });
  test('running foreign native setup prevents removal', () async {
    nativeJob = jsonEncode({
      'state': 'running',
      'params': {
        'phoneAgentOwner': {'profileId': 'other', 'agentId': 'codex'},
      },
    });
    await expectLater(
      host.removeAgent('fx'),
      throwsA(
        isA<AgentHostException>().having(
          (e) => e.reason,
          'reason',
          AgentHostFailure.busy,
        ),
      ),
    );
    expect(calls.map((c) => c.method), isNot(contains('run')));
  });
  test('unknown native setup prevents removal', () async {
    nativeJob = '{bad';
    await expectLater(
      host.removeAgent('fx'),
      throwsA(isA<AgentHostException>()),
    );
    expect(calls.map((c) => c.method), isNot(contains('run')));
  });
  test('live target refusal is busy without leaking child output', () async {
    removalExit = 16;
    await expectLater(
      host.removeAgent('fx'),
      throwsA(
        isA<AgentHostException>().having(
          (e) => e.reason,
          'reason',
          AgentHostFailure.busy,
        ),
      ),
    );
  });
  test(
    'removal serializes installs and releases exclusion after completion',
    () async {
      held = Completer<void>();
      final removal = host.removeAgent('fx');
      await Future<void>.delayed(Duration.zero);
      await expectLater(
        host.install('codex'),
        throwsA(
          isA<AgentHostException>().having(
            (e) => e.reason,
            'reason',
            AgentHostFailure.busy,
          ),
        ),
      );
      held!.complete();
      await removal;
      held = null;
      await host.install('codex');
      expect(
        specs.singleWhere((spec) => spec['id'] == 'agent-codex'),
        isNotNull,
      );
    },
  );
  test('QA requirement reaches the native target only', () async {
    await host.dispose();
    host = BuiltinPhoneAgents(
      profileId: 'phone',
      prefs: prefs,
      loadLock: () async =>
          File(PaseoPhoneScripts.packageLockAsset).readAsStringSync(),
      installGuard: const AgentInstallGuard(minimumFreeBytes: 8589934592),
    );
    await host.install('fx');
    expect(capturedParams?['agentInstallGuard'], {
      'agentId': 'fx',
      'minimumFreeBytes': '8589934592',
    });
    final target = specs.singleWhere((spec) => spec['id'] == 'agent-fx');
    expect((target['data'] as Map)['requiredFreeBytes'], '8589934592');
    for (final spec in specs.where((spec) => spec['id'] != 'agent-fx')) {
      expect(
        int.parse((spec['data'] as Map)['requiredFreeBytes'] as String),
        lessThan(8589934592),
      );
    }
  });
  test('normal build dispatch has no QA declaration', () async {
    await host.install('fx');
    expect(capturedParams?.containsKey('agentInstallGuard'), false);
  });
  test('removing fx retains unrelated Claude auth probes', () async {
    held = Completer<void>();
    final removal = host.removeAgent('fx');
    await Future<void>.delayed(Duration.zero);
    try {
      expect(
        (await host.probeSignIn('claude')).state,
        AgentAuthProbeState.signedIn,
      );
      expect((await host.probeSignIn('fx')).state, AgentAuthProbeState.error);
      expect(
        calls.where((call) => call.method == 'agentAuthProbe'),
        hasLength(1),
      );
    } finally {
      held!.complete();
      await removal;
    }
  });
  test(
    'existing gateway draft cannot dispatch while payload is removed',
    () async {
      await host.install('fx');
      final gateway = host.newGatewaySync('/root/projects/app');
      addTearDown(gateway.close);
      final draft = await gateway.createSession();
      held = Completer<void>();
      final removal = host.removeAgent('fx');
      await Future<void>.delayed(Duration.zero);
      try {
        await expectLater(
          gateway.promptAsync(draft.id, text: 'fixture'),
          throwsA(
            isA<PaseoFailure>().having(
              (e) => e.kind,
              'kind',
              PaseoFailureKind.unavailable,
            ),
          ),
        );
        expect(daemon!.of('create_agent_request'), isEmpty);
        expect(daemon!.of('send_agent_message_request'), isEmpty);
      } finally {
        held!.complete();
        await removal;
      }
    },
  );
  test('pending installation preparation excludes removal', () async {
    holdArchitecture = Completer<void>();
    final installation = host.install('fx');
    await Future<void>.delayed(Duration.zero);
    await expectLater(
      host.removeAgent('codex'),
      throwsA(
        isA<AgentHostException>().having(
          (e) => e.reason,
          'reason',
          AgentHostFailure.busy,
        ),
      ),
    );
    expect(calls.where((call) => call.method == 'run'), isEmpty);
    holdArchitecture!.complete();
    await installation;
  });
  test('only the exact storage failure becomes a safe progress reason', () async {
    await host.dispose();
    final engine = FakeSetupEngine();
    host = BuiltinPhoneAgents(
      profileId: 'phone',
      prefs: prefs,
      loadLock: () async => '',
      engineFactory: (_, _) => engine,
    );
    await host.install('fx');
    for (final error in [
      'There is not enough free space on this phone. Free some storage and try again.',
      'private-provider-error-test-marker',
    ]) {
      engine.emit(
        SetupProgress(
          state: SetupState.failed,
          components: [],
          overall: 0,
          error: error,
        ),
      );
      expect(
        host.setupProgress.failure,
        error.startsWith('There is') ? AgentHostFailure.storage : null,
      );
    }
  });
}
