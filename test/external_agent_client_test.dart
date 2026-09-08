import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/a2a/client.dart';
import 'package:opencode_mobile/domain/external_agent.dart';

void main() {
  late HttpServer server;
  late A2aClient client;
  late Map<String, dynamic> fixture;
  late String origin;
  late Future<void> Function(HttpRequest) handler;
  setUp(() async {
    fixture =
        jsonDecode(
              File('test/fixtures/a2a/official-1.1.0.json').readAsStringSync(),
            )
            as Map<String, dynamic>;
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    origin = 'http://127.0.0.1:${server.port}';
    fixture['card']['supportedInterfaces'][0]['url'] = '$origin/rpc';
    client = A2aClient();
    handler = (request) async {
      request.response.headers.contentType = ContentType.json;
      request.response.write(jsonEncode(fixture['card']));
      await request.response.close();
    };
    server.listen((request) async => handler(request));
  });
  tearDown(() async {
    client.close();
    await server.close(force: true);
  });

  test(
    'official fixture discovery, send, same-task reply, query and cancel use pinned binding',
    () async {
      final card = await client.discover(origin);
      expect(card.supported, isTrue);
      expect(card.auth, ExternalAgentAuth.bearer);
      final calls = <Map<String, dynamic>>[];
      handler = (request) async {
        expect(request.uri.path, '/rpc');
        expect(request.headers.value('authorization'), 'Bearer fixture-token');
        expect(request.headers.value('a2a-version'), '1.0');
        final body =
            jsonDecode(await utf8.decoder.bind(request).join())
                as Map<String, dynamic>;
        calls.add(body);
        final result = switch (body['method']) {
          'SendMessage' => fixture['submitted'],
          'GetTask' => fixture['inputRequired'],
          'CancelTask' => {
            ...fixture['inputRequired'] as Map,
            'status': {'state': 'TASK_STATE_CANCELED'},
          },
          _ => throw StateError('Unexpected method'),
        };
        request.response.write(
          jsonEncode({'jsonrpc': '2.0', 'id': body['id'], 'result': result}),
        );
        await request.response.close();
      };
      final sent = await client.send(card, 'fixture-token', 'choose');
      final waiting = await client.getTask(card, 'fixture-token', sent.id!);
      expect(waiting.state, ExternalTaskState.inputRequired);
      await client.send(card, 'fixture-token', 'blue', continuation: waiting);
      final canceled = await client.cancel(card, 'fixture-token', sent.id!);
      expect(canceled.state, ExternalTaskState.canceled);
      final first = calls[0]['params']['message'] as Map;
      final reply = calls[2]['params']['message'] as Map;
      expect(first['role'], 'ROLE_USER');
      expect(first.containsKey('taskId'), isFalse);
      expect(reply['taskId'], sent.id);
      expect(reply['contextId'], sent.contextId);
      expect(reply['messageId'], isNot(first['messageId']));
      expect(calls[0]['params']['configuration']['returnImmediately'], isTrue);
    },
  );

  for (final mismatch in [
    'version',
    'binding',
    'origin',
    'oauth',
    'extension',
    'skill',
  ]) {
    test('unsupported $mismatch card stays unavailable', () async {
      final card = fixture['card'] as Map;
      switch (mismatch) {
        case 'version':
          card['supportedInterfaces'][0]['protocolVersion'] = '0.2';
        case 'binding':
          card['supportedInterfaces'][0]['protocolBinding'] = 'HTTP+JSON';
        case 'origin':
          card['supportedInterfaces'][0]['url'] = 'https://other.example/rpc';
        case 'oauth':
          card['securitySchemes']['bearer'] = {'oauth2SecurityScheme': {}};
        case 'extension':
          card['capabilities'] = {
            'extensions': [
              {'required': true},
            ],
          };
        case 'skill':
          card['skills'][0]['securityRequirements'] = [
            {
              'schemes': {'other': {}},
            },
          ];
      }
      final found = await client.discover(origin);
      expect(found.supported, isFalse);
      await expectLater(
        client.send(found, 'fixture-token', 'text'),
        throwsA(
          isA<ExternalAgentException>().having(
            (e) => e.issue,
            'issue',
            ExternalAgentIssue.unsupported,
          ),
        ),
      );
    });
  }
  test(
    'redirect does not forward credentials or follow another origin',
    () async {
      final card = await client.discover(origin);
      var requests = 0;
      handler = (request) async {
        requests++;
        request.response.statusCode = 302;
        request.response.headers.set('location', '$origin/steal');
        await request.response.close();
      };
      await expectLater(
        client.send(card, 'fixture-token', 'text'),
        throwsA(
          isA<ExternalAgentException>().having(
            (e) => e.issue,
            'issue',
            ExternalAgentIssue.uncertain,
          ),
        ),
      );
      expect(requests, 1);
    },
  );
  test('public discovery and unauthenticated operation omit bearer', () async {
    fixture['card'].remove('securityRequirements');
    var checks = 0;
    handler = (request) async {
      expect(request.headers.value('authorization'), isNull);
      checks++;
      if (request.method == 'GET') {
        request.response.write(jsonEncode(fixture['card']));
      } else {
        final body = jsonDecode(await utf8.decoder.bind(request).join()) as Map;
        request.response.write(
          jsonEncode({
            'jsonrpc': '2.0',
            'id': body['id'],
            'result': fixture['submitted'],
          }),
        );
      }
      await request.response.close();
    };
    final card = await client.discover(origin);
    await client.send(card, 'must-not-be-sent', 'text');
    expect(checks, 2);
  });
  test(
    'mismatched receipt is uncertain and never exposes remote error text',
    () async {
      final card = await client.discover(origin);
      handler = (request) async {
        await request.drain<void>();
        request.response.write(
          jsonEncode({
            'jsonrpc': '2.0',
            'id': 'wrong',
            'error': {'code': -32000, 'message': 'secret-fixture-text'},
          }),
        );
        await request.response.close();
      };
      try {
        await client.send(card, 'fixture-token', 'text');
        fail('expected error');
      } on ExternalAgentException catch (e) {
        expect(e.issue, ExternalAgentIssue.uncertain);
        expect(e.toString(), isNot(contains('secret-fixture-text')));
      }
    },
  );
  test('oversized card fails closed', () async {
    handler = (request) async {
      request.response.write('x' * (1024 * 1024 + 1));
      await request.response.close();
    };
    await expectLater(
      client.discover(origin),
      throwsA(isA<ExternalAgentException>()),
    );
  });
  test('terminal task cannot be continued and output is bounded', () async {
    final card = await client.discover(origin);
    final task = parseTask({
      'id': 't',
      'status': {'state': 'TASK_STATE_COMPLETED'},
      'artifacts': [
        {
          'parts': [
            {'text': 'x' * 70000},
            {'raw': 'unsupported'},
            {'url': 'https://example.com/result', 'filename': 'Result'},
          ],
        },
      ],
    });
    expect(task.parts.first.text!.length, 64000);
    expect(task.omittedContent, isTrue);
    expect(task.parts.last.url, 'https://example.com/result');
    await expectLater(
      client.send(card, 'fixture-token', 'again', continuation: task),
      throwsA(isA<ExternalAgentException>()),
    );
  });
  test('unsafe addresses fail before network', () {
    for (final url in [
      'http://remote.example',
      'https://user:pass@example.com',
      'https://example.com/?token=x',
      'file:///tmp/card',
      'https://example.com/#x',
    ]) {
      expect(
        () => A2aClient.address(url),
        throwsA(isA<ExternalAgentException>()),
      );
    }
  });
}
