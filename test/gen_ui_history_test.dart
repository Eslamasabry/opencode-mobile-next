import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/domain/agent_handoff_history.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

void main() {
  late HttpServer server;
  late List<Uri> requests;
  late FutureOr<void> Function(HttpRequest) respond;
  late Api2Gateway oc2;

  setUp(() async {
    requests = [];
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    respond = (request) => request.response.write('[]');
    server.listen((request) async {
      requests.add(request.uri);
      request.response.headers.contentType = ContentType.json;
      await respond(request);
      await request.response.close();
    });
    final base = 'http://${server.address.host}:${server.port}';
    oc2 = Api2Gateway.connect(baseUrl: base, password: '');
  });
  tearDown(() async {
    oc2.close();
    await server.close(force: true);
  });

  test('HTTP rejects oversized body and hides server error content', () async {
    respond = (request) => request.response.write('x' * (1024 * 1024 + 1));
    await expectLater(
      oc2.genUiHistoryPage('ses_1'),
      throwsA(isA<ProductException>()),
    );
    respond = (request) {
      request.response.statusCode = 401;
      request.response.write('private-provider-value');
    };
    try {
      await oc2.genUiHistoryPage('ses_1');
      fail('must reject');
    } catch (error) {
      expect(error, isA<ProductException>());
      expect(error.toString(), isNot(contains('private-provider-value')));
    }
  });

  test('OC2 requests descending page and returns oldest-first', () async {
    respond = (request) => request.response.write(
      jsonEncode({
        'data': [
          {'id': 'm2', 'type': 'user', 'text': 'newer'},
          {'id': 'm1', 'type': 'user', 'text': 'older'},
        ],
        'cursor': {'next': 'opaque'},
      }),
    );
    final page = await oc2.genUiHistoryPage('ses_1', limit: 2);
    expect(page.items.map((e) => e.info.id), ['m1', 'm2']);
    expect(page.olderCursor, 'opaque');
    expect(requests.single.path, '/api/session/ses_1/message');
    expect(requests.single.queryParameters, {'order': 'desc', 'limit': '2'});
    respond = (request) =>
        request.response.write(jsonEncode({'data': [], 'cursor': {}}));
    final end = await oc2.genUiHistoryPage('ses_1', cursor: 'opaque', limit: 2);
    expect(end.hasMore, isFalse);
    expect(requests.last.queryParameters, {'cursor': 'opaque', 'limit': '2'});
  });

  test(
    'OC2 handoff reads a conversation whose screenshots exceed the card budget',
    () async {
      // A tool result carrying a screenshot as a data URI, as in a phone's
      // Design review chat (one 1.8 MB message).
      final screenshot = 'data:image/png;base64,${'A' * (1024 * 1024 + 1)}';
      respond = (request) {
        final older = request.uri.queryParameters['cursor'] == 'older';
        request.response.write(
          jsonEncode({
            'data': older
                ? [
                    {'id': 'm1', 'type': 'user', 'text': 'look at this'},
                  ]
                : [
                    for (var i = 51; i >= 3; i--)
                      {'id': 'm$i', 'type': 'user', 'text': 'turn $i'},
                    {'id': 'm2', 'type': 'user', 'text': screenshot},
                  ],
            'cursor': {if (!older) 'next': 'older'},
          }),
        );
      };
      await expectLater(
        oc2.genUiHistoryPage('ses_1'),
        throwsA(isA<ProductException>()),
      );
      final history = await readAgentHandoffHistory(
        oc2,
        'ses_1',
        isCurrent: () => true,
      );
      expect(history.first.info.id, 'm1');
      expect(history[1].info.id, 'm2');
      expect(history.last.info.id, 'm51');
      expect(history, hasLength(51));
      expect(requests.last.queryParameters, {'cursor': 'older', 'limit': '50'});
    },
  );

  test(
    'OC2 idle verifies existence and validates active map before absence',
    () async {
      Object active = <String, Object>{};
      respond = (request) => request.response.write(
        jsonEncode({
          'data': request.uri.path.endsWith('/active')
              ? active
              : {'id': 'ses_1'},
        }),
      );
      expect(await oc2.genUiSessionIdle('ses_1'), isTrue);
      active = {
        'ses_1': {'type': 'running'},
      };
      expect(await oc2.genUiSessionIdle('ses_1'), isFalse);
      active = [];
      await expectLater(
        oc2.genUiSessionIdle('ses_1'),
        throwsA(isA<ProductException>()),
      );
      active = {'other': 42};
      await expectLater(
        oc2.genUiSessionIdle('ses_1'),
        throwsA(isA<ProductException>()),
      );
      respond = (request) => request.response.write('{"data":{"id":"other"}}');
      await expectLater(
        oc2.genUiSessionIdle('ses_1'),
        throwsA(isA<ProductException>()),
      );
    },
  );

  test(
    'Paseo idle admits only a fresh idle snapshot in the same directory',
    () async {
      String? status = 'idle';
      String cwd = '/work';
      final transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:1/ws',
        socketFactory: (_, _) async =>
            _HistorySocket(agentStatus: status, agentCwd: cwd),
      );
      final gateway = PaseoGateway(transport: transport, directory: '/work');
      addTearDown(gateway.close);
      for (final candidate in [
        'idle',
        'initializing',
        'running',
        'error',
        'closed',
        'invalid',
        null,
      ]) {
        status = candidate;
        expect(await gateway.genUiSessionIdle('agent-1'), candidate == 'idle');
      }
      status = 'idle';
      cwd = '/other';
      await expectLater(
        gateway.genUiSessionIdle('agent-1'),
        throwsA(isA<ProductException>()),
      );
    },
  );

  test('invalid limits never send a wire request', () async {
    for (final GenUiHistoryGateway gateway in [oc2]) {
      for (final limit in [0, -1, 51]) {
        await expectLater(
          gateway.genUiHistoryPage('ses_1', limit: limit),
          throwsA(isA<ProductException>()),
        );
      }
    }
    expect(requests, isEmpty);
  });

  test(
    'Paseo private history rejects missing or unqualified handshake versions',
    () async {
      for (final version in [null, 123, '', 'invalid', '0.9.1', '0.9.3']) {
        final socket = _HistorySocket(version: version);
        final transport = PaseoTransport(
          endpoint: 'ws://127.0.0.1:1/ws',
          socketFactory: (_, _) async => socket,
        );
        addTearDown(transport.close);
        await expectLater(
          transport.requestBoundedHistory({'agentId': 'agent-1', 'limit': 1}),
          throwsA(isA<PaseoFailure>()),
        );
        expect(socket.requests, isEmpty);
        expect(socket.closed, isTrue);
      }
    },
  );

  test(
    'Paseo loses qualified version on disconnect and invalid handshake',
    () async {
      final sockets = <_HistorySocket>[];
      final transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:1/ws',
        socketFactory: (_, _) async {
          final socket = _HistorySocket(
            version: sockets.isEmpty ? '0.9.2' : null,
          );
          sockets.add(socket);
          return socket;
        },
      );
      addTearDown(transport.close);
      await transport.connect();
      expect(transport.serverVersion, '0.9.2');
      sockets.first.push('status', {'status': 'server_info', 'version': 123});
      await pumpEventQueue();
      expect(transport.serverVersion, isNull);
      sockets.first.push('status', {
        'status': 'server_info',
        'version': '0.9.2',
      });
      await pumpEventQueue();
      expect(transport.serverVersion, '0.9.2');
      await sockets.first.close();
      await pumpEventQueue();
      expect(transport.connected, isFalse);
      expect(transport.serverVersion, isNull);
      await transport.connect();
      expect(transport.connected, isTrue);
      expect(transport.serverVersion, isNull);
    },
  );

  test('Paseo finite tail uses private connection and before cursor', () async {
    final sockets = <_HistorySocket>[];
    final transport = PaseoTransport(
      endpoint: 'ws://127.0.0.1:1/ws',
      socketFactory: (_, _) async {
        final socket = _HistorySocket();
        sockets.add(socket);
        return socket;
      },
    );
    final gateway = PaseoGateway(transport: transport, directory: '/work');
    addTearDown(gateway.close);
    final page = await gateway.genUiHistoryPage('agent-1', limit: 2);
    expect(page.items.single.info.id, 'user-1');
    expect(page.hasMore, isTrue);
    expect(sockets.single.requests.single['limit'], 2);
    expect(sockets.single.requests.single['direction'], 'tail');
    expect(sockets.single.closed, isTrue);
    expect(transport.connected, isFalse);
    await gateway.genUiHistoryPage(
      'agent-1',
      cursor: page.olderCursor,
      limit: 2,
    );
    expect(sockets.last.requests.single['direction'], 'before');
    expect(sockets.last.requests.single['cursor'], {'epoch': 'e1', 'seq': 10});
  });

  test(
    'Paseo rejects oversized frame without disturbing live transport',
    () async {
      final sockets = <_HistorySocket>[];
      final transport = PaseoTransport(
        endpoint: 'ws://127.0.0.1:1/ws',
        socketFactory: (_, _) async {
          final socket = _HistorySocket(oversized: sockets.isNotEmpty);
          sockets.add(socket);
          return socket;
        },
      );
      // Establish an independent live connection before the bounded read.
      await transport.request('fetch_agent_timeline_request', {
        'agentId': 'agent-1',
      });
      final gateway = PaseoGateway(transport: transport, directory: '/work');
      addTearDown(gateway.close);
      await expectLater(
        gateway.genUiHistoryPage('agent-1'),
        throwsA(isA<ProductException>()),
      );
      expect(transport.connected, isTrue);
      expect(sockets.first.closed, isFalse);
      expect(sockets.last.closed, isTrue);
    },
  );
}

class _HistorySocket implements PaseoSocket {
  final bool oversized;
  final Object? version;
  final String? agentStatus;
  final String agentCwd;
  final incoming = StreamController<Object?>();
  final requests = <Map<String, dynamic>>[];
  bool closed = false;
  _HistorySocket({
    this.oversized = false,
    this.version = '0.9.2',
    this.agentStatus = 'idle',
    this.agentCwd = '/work',
  });
  @override
  int? get closeCode => null;
  @override
  Stream<Object?> get messages => incoming.stream;
  void push(String type, Map<String, dynamic> payload) => incoming.add(
    jsonEncode({
      'type': 'session',
      'message': {'type': type, 'payload': payload},
    }),
  );
  @override
  void send(String message) {
    final value = jsonDecode(message) as Map<String, dynamic>;
    if (value['type'] == 'hello') {
      push('status', {'status': 'server_info', 'version': ?version});
      return;
    }
    final request = value['message'] as Map<String, dynamic>;
    requests.add(request);
    if (oversized) {
      incoming.add('x' * (1024 * 1024 + 1));
      return;
    }
    if (request['type'] == 'fetch_agent_request') {
      push('fetch_agent_response', {
        'requestId': request['requestId'],
        'agent': {
          'id': request['agentId'],
          'status': agentStatus,
          'cwd': agentCwd,
        },
      });
      return;
    }
    push('fetch_agent_timeline_response', {
      'requestId': request['requestId'],
      'agentId': request['agentId'],
      'epoch': 'e1',
      'hasOlder': request['direction'] != 'before',
      'hasNewer': false,
      'startCursor': {'epoch': 'e1', 'seq': 10},
      'entries': [
        {
          'provider': 'claude',
          'seqStart': 10,
          'item': {
            'type': 'user_message',
            'messageId': 'user-1',
            'text': 'Hello',
          },
        },
      ],
    });
  }

  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    await incoming.close();
  }
}
