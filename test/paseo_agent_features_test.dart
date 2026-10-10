import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

Map<String, dynamic> _fast(bool on) => {
  'type': 'toggle',
  'id': 'fast_mode',
  'label': 'Fast',
  'description': 'Lower latency Opus responses at higher token cost',
  'tooltip': 'Toggle fast mode',
  'icon': 'zap',
  'value': on,
};

/// An agent's own switches: read from the agent, changed on the server, and
/// shown as the server answers.
void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  var fastOn = false;
  var applyChange = true;

  setUp(() {
    fastOn = false;
    applyChange = true;
    daemon = FakeDaemon();
    daemon.handlers['fetch_agent_request'] = (_) => (
      'fetch_agent_response',
      {
        'agent': {
          ...agentJson('a1'),
          'model': 'opus',
          'features': [_fast(fastOn)],
        },
      },
    );
    daemon.handlers['set_agent_feature_request'] = (request) {
      if (applyChange) fastOn = request['value'] == true;
      return (
        'set_agent_feature_response',
        {'agentId': 'a1', 'accepted': true, 'error': null},
      );
    };
    daemon.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': [
              {'id': 'opus', 'label': 'Opus'},
            ],
            'modes': [
              {'id': 'default', 'label': 'Always Ask'},
            ],
          },
        ],
      },
    );
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: '/work/app',
    );
    addTearDown(gateway.close);
  });

  test('the switches the agent lists are read from the agent', () async {
    expect(gateway.agentFeaturesSupported, isTrue);
    final features = await gateway.agentFeatures('a1');
    expect(features, hasLength(1));
    expect(features.single.id, 'fast_mode');
    expect(features.single.kind, AgentFeatureKind.toggle);
    expect(features.single.on, isFalse);
  });

  test('turning one on asks the server and shows its answer', () async {
    final after = await gateway.setAgentFeature('a1', 'fast_mode', true);
    final sent = daemon.of('set_agent_feature_request').single;
    expect(sent['agentId'], 'a1');
    expect(sent['featureId'], 'fast_mode');
    expect(sent['value'], true);
    expect(after.single.on, isTrue);
  });

  test('a change the server did not apply is not shown as applied', () async {
    applyChange = false;
    final after = await gateway.setAgentFeature('a1', 'fast_mode', true);
    expect(after.single.on, isFalse, reason: 'the agent still has it off');
  });

  test('a refusal throws and leaves the agent as it was', () async {
    daemon.handlers['set_agent_feature_request'] = (_) => (
      'set_agent_feature_response',
      {'agentId': 'a1', 'accepted': false, 'error': 'model has no fast mode'},
    );
    await expectLater(
      gateway.setAgentFeature('a1', 'fast_mode', true),
      throwsA(isA<PaseoFailure>()),
    );
    expect((await gateway.agentFeatures('a1')).single.on, isFalse);
  });

  test('a value of the wrong kind is never sent', () async {
    await expectLater(
      gateway.setAgentFeature('a1', 'fast_mode', 'yes'),
      throwsA(isA<PaseoFailure>()),
    );
    await expectLater(
      gateway.setAgentFeature('a1', 'not_a_switch', true),
      throwsA(isA<PaseoFailure>()),
    );
    expect(daemon.of('set_agent_feature_request'), isEmpty);
  });

  test('a choice switch keeps only options it names', () async {
    daemon.handlers['fetch_agent_request'] = (_) => (
      'fetch_agent_response',
      {
        'agent': {
          ...agentJson('a1'),
          'features': [
            {
              'type': 'select',
              'id': 'effort',
              'label': 'Effort',
              'value': 'high',
              'options': [
                {'id': 'low', 'label': 'Low'},
                {'id': 'high', 'label': 'High'},
                {'label': 'no id'},
              ],
            },
            {'type': 'toggle', 'id': 'bad id!', 'label': 'x', 'value': true},
          ],
        },
      },
    );
    final features = await gateway.agentFeatures('a1');
    expect(features, hasLength(1));
    expect(features.single.kind, AgentFeatureKind.choice);
    expect(features.single.selected, 'high');
    expect(features.single.options.map((o) => o.id), ['low', 'high']);
  });

  group('before the conversation starts', () {
    test('the daemon lists what the chosen agent would offer', () async {
      daemon.handlers['list_provider_features_request'] = (_) => (
        'list_provider_features_response',
        {
          'provider': 'claude',
          'features': [_fast(false)],
          'fetchedAt': '2026-10-10T00:00:00Z',
        },
      );
      final draft = await gateway.createSession();
      await gateway.setSessionModel(
        draft.id,
        ModelRef(providerID: 'claude', modelID: 'opus'),
        '',
      );
      final features = await gateway.agentFeatures(draft.id);
      expect(features.single.id, 'fast_mode');
      final config =
          daemon.of('list_provider_features_request').single['draftConfig']
              as Map;
      expect(config['provider'], 'claude');
      expect(config['model'], 'opus');
      expect(config['cwd'], '/work/app');
    });

    test('a choice made first is sent when the conversation starts', () async {
      daemon.handlers['list_provider_features_request'] = (request) {
        final values = (request['draftConfig'] as Map)['featureValues'] as Map?;
        return (
          'list_provider_features_response',
          {
            'provider': 'claude',
            'features': [_fast(values?['fast_mode'] == true)],
            'fetchedAt': '2026-10-10T00:00:00Z',
          },
        );
      };
      daemon.handlers['create_agent_request'] = (_) => (
        'status',
        {
          'status': 'agent_created',
          'agentId': 'a9',
          'agent': {...agentJson('a9'), 'model': 'opus'},
        },
      );
      final draft = await gateway.createSession();
      await gateway.setSessionModel(
        draft.id,
        ModelRef(providerID: 'claude', modelID: 'opus'),
        '',
      );
      final after = await gateway.setAgentFeature(draft.id, 'fast_mode', true);
      expect(after.single.on, isTrue);
      await gateway.providers();
      await gateway.promptAsync(
        draft.id,
        text: 'hello',
        model: ModelRef(providerID: 'claude', modelID: 'opus'),
        agent: 'default',
      );
      final created = daemon.of('create_agent_request');
      expect(created, isNotEmpty);
      expect((created.single['config'] as Map)['featureValues'], {
        'fast_mode': true,
      });
    });
  });
}
