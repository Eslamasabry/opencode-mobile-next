import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  setUp(() {
    daemon = FakeDaemon();
    daemon.handlers['fetch_agent_request'] = (_) =>
        ('fetch_agent_response', {'agent': agentJson('a1')});
    daemon.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': <Object>[],
            'modes': <Object>[],
          },
        ],
      },
    );
    daemon.handlers['send_agent_message_request'] = (_) =>
        ('send_agent_message_response', {'agentId': 'a1', 'accepted': true});
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: '/work/app',
    );
  });
  tearDown(() => gateway.close());

  test(
    'correlated prompt forwards exact caller ID after final fence',
    () async {
      var checks = 0;
      final CorrelatedPromptGateway correlated = gateway;
      final id = correlated.createPromptMessageID();
      await correlated.promptWithMessageID(
        'a1',
        messageID: id,
        text: 'Answer',
        beforeSend: () {
          checks++;
          expect(daemon.of('fetch_agent_request'), isNotEmpty);
          expect(daemon.of('get_providers_snapshot_request'), isNotEmpty);
          expect(daemon.of('send_agent_message_request'), isEmpty);
        },
      );
      expect(checks, 1);
      expect(daemon.of('send_agent_message_request').single['messageId'], id);
    },
  );

  test('caller refusal prevents dispatch and releases awaiting turn', () async {
    const refused = ProductException('The card is stale.');
    await expectLater(
      gateway.promptWithMessageID(
        'a1',
        messageID: 'card-answer',
        text: 'Answer',
        beforeSend: () => throw refused,
      ),
      throwsA(same(refused)),
    );
    expect(daemon.of('send_agent_message_request'), isEmpty);
    await gateway.session('a1');
    expect((await gateway.sessionStatuses())['a1'], 'idle');
    await gateway.promptWithMessageID(
      'a1',
      messageID: 'next-answer',
      text: 'Next',
    );
    expect(
      daemon.of('send_agent_message_request').single['messageId'],
      'next-answer',
    );
  });

  test('correlated draft is rejected before any server mutation', () async {
    final draft = await gateway.createSession();
    await expectLater(
      gateway.promptWithMessageID(
        draft.id,
        messageID: 'answer',
        text: 'Answer',
      ),
      throwsA(isA<PaseoFailure>()),
    );
    expect(daemon.of('create_agent_request'), isEmpty);
    expect(daemon.of('send_agent_message_request'), isEmpty);
  });

  test(
    'transport fence runs after async connection and propagates refusal',
    () async {
      const refused = ProductException('Deleted while connecting.');
      await expectLater(
        gateway.transport.request(
          'send_agent_message_request',
          {'agentId': 'a1'},
          mutation: true,
          beforeSend: () {
            expect(gateway.transport.connected, isTrue);
            throw refused;
          },
        ),
        throwsA(same(refused)),
      );
      expect(daemon.of('send_agent_message_request'), isEmpty);
    },
  );
}
