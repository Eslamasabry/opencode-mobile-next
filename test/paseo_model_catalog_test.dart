import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/mappers.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  setUp(() {
    SharedPreferences.setMockInitialValues({});
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
    daemon = FakeDaemon();
    daemon.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': [
              {
                'id': 'opus',
                'label': 'Opus',
                'thinkingOptions': [
                  {'id': 'low', 'label': 'Low'},
                  {'id': 'high', 'label': 'High', 'isDefault': true},
                  {'id': 'off', 'label': 'Off'},
                ],
              },
              {'id': 'haiku', 'label': 'Haiku'},
            ],
          },
          {
            'provider': 'opencode',
            'status': 'ready',
            'models': [
              {'id': 'big-pickle', 'label': 'Big Pickle'},
            ],
          },
        ],
      },
    );
    daemon.handlers['fetch_agent_request'] = (_) => (
      'fetch_agent_response',
      {
        'agent': {
          ...agentJson('a1'),
          'model': 'opus',
          'thinkingOptionId': 'low',
        },
      },
    );
    for (final kind in ['model', 'thinking']) {
      daemon.handlers['set_agent_${kind}_request'] = (_) =>
          ('set_agent_${kind}_response', {'agentId': 'a1'});
    }
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: '/work/app',
    );
  });
  tearDown(() => gateway.close());

  test('Paseo thinking options survive provider catalog mapping', () async {
    final providers = await gateway.providers();
    final models = providers.providers.first.modelData;
    expect(models['opus']!['variants'], {
      'low': {'label': 'Low'},
      'high': {'label': 'High', 'isDefault': true},
      'off': {'label': 'Off'},
    });
    expect(models['haiku']!['variants'], isNull);
  });

  test('Paseo snapshot restores the current thinking choice', () {
    final session = paseoSession({
      ...agentJson('a1'),
      'thinkingOptionId': 'high',
    });
    expect(session.selection!.variant, 'high');
    final restored = Session.fromJson(paseoSessionJson(session));
    expect(restored.selection!.variant, 'high');
  });

  test(
    'existing session applies thinking after model and restores it',
    () async {
      await gateway.setSessionModel(
        'a1',
        ModelRef(providerID: 'claude', modelID: 'opus'),
        'high',
      );
      expect(
        daemon.of('set_agent_thinking_request').single,
        containsPair('thinkingOptionId', 'high'),
      );
      expect(
        daemon.sent
            .map((m) => m['type'])
            .toList()
            .indexOf('set_agent_model_request'),
        lessThan(
          daemon.sent
              .map((m) => m['type'])
              .toList()
              .indexOf('set_agent_thinking_request'),
        ),
      );
    },
  );

  test('default thinking resets an existing choice with null', () async {
    await gateway.setSessionModel(
      'a1',
      ModelRef(providerID: 'claude', modelID: 'opus'),
      '',
    );
    expect(
      daemon.of('set_agent_thinking_request').single,
      containsPair('thinkingOptionId', null),
    );
  });

  test('a provider-bound draft rejects another provider model', () async {
    final draft = await gateway.createSession();
    gateway.seedDraftProviderForSession(draft.id, 'claude');
    await expectLater(
      gateway.setSessionModel(
        draft.id,
        ModelRef(providerID: 'opencode', modelID: 'big-pickle'),
        '',
      ),
      throwsA(isA<PaseoFailure>()),
    );
  });

  test('draft thinking survives model selection', () async {
    final draft = await gateway.createSession();
    gateway.seedDraftProviderForSession(draft.id, 'claude');
    await gateway.setSessionModel(
      draft.id,
      ModelRef(providerID: 'claude', modelID: 'opus'),
      'high',
    );
    expect((await gateway.session(draft.id)).selection!.variant, 'high');
  });

  test(
    'controller catalog isolates each provider without changing the full catalog',
    () async {
      final controller = ConnectionController(
        ProfileStore(prefs: await SharedPreferences.getInstance()),
      )..api = gateway;
      addTearDown(controller.dispose);
      await controller.refreshCatalog();
      expect(controller.catalogError, isNull);
      final claude = await gateway.createSession();
      gateway.seedDraftProviderForSession(claude.id, 'claude');
      final openCode = await gateway.createSession();
      gateway.seedDraftProviderForSession(openCode.id, 'opencode');
      expect(controller.catalogForSession(claude.id)!.models.map((m) => m.id), [
        'opus',
        'haiku',
      ]);
      expect(
        controller.catalogForSession(claude.id)!.providers.map((p) => p.id),
        ['claude'],
      );
      expect(
        controller.catalogForSession(openCode.id)!.models.map((m) => m.id),
        ['big-pickle'],
      );
      expect(controller.catalogForSession(null)!.models, hasLength(3));
      expect(controller.catalogForSession('unresolved')!.models, isEmpty);
      final model = controller.catalogForSession(claude.id)!.models.first;
      expect(model.reasoning, isTrue);
      expect(model.variants.map((v) => v.id), ['low', 'high', 'off']);
      expect(model.variants.last.options['label'], 'Off');
      expect(paseoServerCapabilities.withGenUi(true).agentSelection, isFalse);
      expect(
        paseoServerCapabilities.withGenUi(true).sessionModelProviderSwitching,
        isFalse,
      );
      expect(ServerCapabilities.allV1.agentSelection, isTrue);
      expect(ServerCapabilities.allV1.sessionModelProviderSwitching, isTrue);
    },
  );

  test('a saved draft thinking level reaches the create request', () async {
    final draft = await gateway.createSession();
    gateway.seedDraftProviderForSession(draft.id, 'claude');
    await gateway.setSessionModel(
      draft.id,
      ModelRef(providerID: 'claude', modelID: 'opus'),
      'high',
    );
    daemon.handlers['create_agent_request'] = (_) => (
      'agent_created_response',
      {
        'agent': {
          ...agentJson('a1'),
          'model': 'opus',
          'thinkingOptionId': 'high',
        },
      },
    );
    await gateway.promptAsync(draft.id, text: 'hello');
    expect(
      daemon.of('create_agent_request').single['config'],
      containsPair('thinkingOptionId', 'high'),
    );
  });

  test('follow-up thinking reaches the runtime before the message', () async {
    daemon.handlers['send_agent_message_request'] = (_) =>
        ('send_agent_message_response', {'accepted': true});
    await gateway.promptAsync(
      'a1',
      text: 'hello',
      model: ModelRef(providerID: 'claude', modelID: 'opus'),
      variant: 'high',
    );
    expect(
      daemon.of('set_agent_thinking_request').single['thinkingOptionId'],
      'high',
    );
    final requests = daemon.sent.map((m) => m['type']).toList();
    expect(
      requests.indexOf('set_agent_thinking_request'),
      lessThan(requests.indexOf('send_agent_message_request')),
    );
  });

  test('thinking rejection does not send the follow-up prompt', () async {
    daemon.handlers['set_agent_thinking_request'] = (_) => (
      'set_agent_thinking_response',
      {'accepted': false, 'error': 'private runtime detail'},
    );
    await expectLater(
      gateway.promptAsync('a1', text: 'hello', variant: 'high'),
      throwsA(isA<PaseoFailure>()),
    );
    expect(daemon.of('send_agent_message_request'), isEmpty);
  });

  test(
    'a location change after model update prevents thinking on the new scope',
    () async {
      daemon.handlers['set_agent_model_request'] = (_) {
        gateway.setLocation(directory: '/work/other');
        return ('set_agent_model_response', {'accepted': true});
      };
      await expectLater(
        gateway.setSessionModel(
          'a1',
          ModelRef(providerID: 'claude', modelID: 'opus'),
          'high',
        ),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('set_agent_thinking_request'), isEmpty);
    },
  );
}
