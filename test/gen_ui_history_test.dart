import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

Map<String, dynamic> bundle(String id) => {
  'info': {
    'id': id,
    'sessionID': 'ses_1',
    'role': 'user',
    'time': {'created': 1},
  },
  'parts': <Object>[],
};

void main() {
  late HttpServer server;
  late List<Uri> requests;
  late FutureOr<void> Function(HttpRequest) respond;
  late OpenCodeApi oc1;
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
    oc1 = OpenCodeApi(baseUrl: base)
      ..setLocation(directory: '/work', workspace: 'w');
    oc2 = Api2Gateway.connect(baseUrl: base, password: '');
  });
  tearDown(() async {
    oc1.close();
    oc2.close();
    await server.close(force: true);
  });

  test(
    'OC1 bounded page preserves trusted scope and extracts only cursor',
    () async {
      respond = (request) {
        if (request.uri.queryParameters['before'] == null) {
          request.response.headers.set(
            'link',
            '<https://untrusted.invalid/x?before=older>; rel="next"',
          );
        }
        request.response.write(jsonEncode([bundle('m1')]));
      };
      final page = await oc1.genUiHistoryPage('ses_1', limit: 2);
      expect(page.items.single.info.id, 'm1');
      expect(page.olderCursor, 'older');
      expect(page.hasMore, isTrue);
      await oc1.genUiHistoryPage('ses_1', cursor: page.olderCursor, limit: 2);
      expect(requests.last.path, '/session/ses_1/message');
      expect(requests.last.queryParameters, {
        'directory': '/work',
        'workspace': 'w',
        'before': 'older',
        'limit': '2',
      });
    },
  );

  test('OC1 full cursorless page retains unknown older coverage', () async {
    respond = (request) => request.response.write(jsonEncode([bundle('m1')]));
    final page = await oc1.genUiHistoryPage('ses_1', limit: 1);
    expect(page.hasMore, isTrue);
    expect(page.olderCursor, isNull);
  });

  test('OC1 rejects foreign session and over-limit response', () async {
    respond = (request) =>
        request.response.write(jsonEncode([bundle('m1'), bundle('m2')]));
    await expectLater(
      oc1.genUiHistoryPage('ses_1', limit: 1),
      throwsA(isA<ProductException>()),
    );
    respond = (request) {
      final item = bundle('m1');
      (item['info'] as Map)['sessionID'] = 'ses_foreign';
      request.response.write(jsonEncode([item]));
    };
    await expectLater(
      oc1.genUiHistoryPage('ses_1'),
      throwsA(isA<ProductException>()),
    );
  });

  test('HTTP rejects oversized body and hides server error content', () async {
    respond = (request) => request.response.write('x' * (1024 * 1024 + 1));
    await expectLater(
      oc1.genUiHistoryPage('ses_1'),
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
    'OC1 idle requires explicit fresh idle, absent and malformed deny',
    () async {
      for (final status in [null, 'idle', 'busy', 'retry', 'unknown']) {
        respond = (request) => request.response.write(
          jsonEncode({
            if (status != null) 'ses_1': {'type': status},
          }),
        );
        expect(await oc1.genUiSessionIdle('ses_1'), status == 'idle');
      }
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
    for (final GenUiHistoryGateway gateway in [oc1, oc2]) {
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
