import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

const _directory = '/work/cards';

class _Daemon implements PaseoSocket {
  final incoming = StreamController<Object?>();
  final sent = <Map<String, dynamic>>[];
  String version = '0.9.2';
  final agents = <Map<String, dynamic>>[];
  bool closed = false;

  @override
  int? get closeCode => null;
  @override
  Stream<Object?> get messages => incoming.stream;
  @override
  void send(String value) {
    final frame = jsonDecode(value) as Map<String, dynamic>;
    if (frame['type'] == 'hello') {
      push('status', {'status': 'server_info', 'version': version});
      return;
    }
    if (frame['type'] == 'ping') return;
    final message = frame['message'] as Map<String, dynamic>;
    sent.add(message);
    if (message['type'] == 'fetch_agents_request') {
      push('fetch_agents_response', {
        'requestId': message['requestId'],
        'entries': agents.map((agent) => {'agent': agent}).toList(),
        'pageInfo': {'nextCursor': null, 'hasMore': false},
      });
    }
  }

  void push(String type, Map<String, dynamic> payload) {
    if (!closed) {
      incoming.add(
        jsonEncode({
          'type': 'session',
          'message': {'type': type, 'payload': payload},
        }),
      );
    }
  }

  List<Map<String, dynamic>> get replies => sent
      .where((message) => message['type'] == 'agent_permission_response')
      .toList();

  @override
  Future<void> close() async {
    closed = true;
    await incoming.close();
  }
}

class _RefusingGateway extends PaseoGateway {
  _RefusingGateway({required super.transport}) : super(directory: _directory);

  int attempts = 0;

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    attempts++;
    throw StateError('The request was not accepted');
  }
}

Map<String, dynamic> _agent(
  String id, {
  String provider = 'claude',
  String directory = _directory,
  List<Map<String, dynamic>> pending = const [],
}) => {
  'id': id,
  'provider': provider,
  'cwd': directory,
  'title': 'Cards',
  'status': 'idle',
  'createdAt': '2026-10-07T10:00:00.000Z',
  'updatedAt': '2026-10-07T10:00:00.000Z',
  'pendingPermissions': pending,
};

Map<String, dynamic> _request({
  String id = 'request-1',
  String name = 'mcp__oc-ui__show',
  String kind = 'tool',
  String provider = 'claude',
}) => {
  'id': id,
  'provider': provider,
  'name': name,
  'kind': kind,
  'input': {'title': 'Display only'},
  'detail': {'type': 'unknown'},
  'suggestions': [
    {'type': 'setMode', 'mode': 'bypassPermissions'},
  ],
  'metadata': {'toolUseId': 'call-1'},
};

void main() {
  late _Daemon daemon;
  late PaseoGateway gateway;
  late List<EventEnvelope> events;
  var allowed = true;

  setUp(() {
    daemon = _Daemon()..agents.add(_agent('agent-1'));
    allowed = true;
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _directory,
    )..genUiShowAllowed = () => allowed;
    events = [];
    gateway.openEventChannel(onEvent: events.add, onStatus: (_) {});
  });
  tearDown(() => gateway.close());

  Future<void> ask(
    Map<String, dynamic> request, {
    String agent = 'agent-1',
  }) async {
    daemon.push('agent_permission_request', {
      'agentId': agent,
      'request': request,
    });
    await pumpEventQueue();
  }

  test(
    'qualified exact show is allowed once without an approval card',
    () async {
      await gateway.sessions();
      await ask(_request());
      expect(daemon.replies, hasLength(1));
      expect(daemon.replies.single, {
        'type': 'agent_permission_response',
        'agentId': 'agent-1',
        'requestId': 'request-1',
        'response': {'behavior': 'allow'},
      });
      expect(events.where((e) => e.type == 'permission.asked'), isEmpty);
      expect(await gateway.pendingPermissions(), isEmpty);
      await ask(_request());
      daemon.push('agent_update', {
        'agent': _agent('agent-1', pending: [_request()]),
      });
      await pumpEventQueue();
      expect(daemon.replies, hasLength(1));
    },
  );

  test('hydrated show for a non-open session is allowed once', () async {
    daemon.agents.add(_agent('not-open', pending: [_request()]));
    await gateway.sessions();
    await pumpEventQueue();
    expect(daemon.replies.single['agentId'], 'not-open');
    expect(events.where((e) => e.type == 'permission.asked'), isEmpty);
  });

  test('disable is checked again for every request', () async {
    await gateway.sessions();
    await ask(_request());
    allowed = false;
    await ask(_request(id: 'request-2'));
    expect(daemon.replies, hasLength(1));
    expect((await gateway.pendingPermissions()).single.id, 'request-2');
  });

  test('qualification sweeps an already pending show request', () async {
    gateway.genUiShowAllowed = null;
    await gateway.sessions();
    await ask(_request());
    expect(await gateway.pendingPermissions(), hasLength(1));
    gateway.genUiShowAllowed = () => true;
    await pumpEventQueue();
    expect(daemon.replies, hasLength(1));
    expect(await gateway.pendingPermissions(), isEmpty);
  });

  test('known Claude agent qualifies when request omits provider', () async {
    await gateway.sessions();
    final request = _request()..remove('provider');
    await ask(request);
    expect(daemon.replies, hasLength(1));
  });

  test(
    'revoked qualification before queued reply leaves request visible',
    () async {
      gateway.genUiShowAllowed = null;
      await gateway.sessions();
      await ask(_request());
      gateway.genUiShowAllowed = () => true;
      gateway.genUiShowAllowed = null;
      await pumpEventQueue();
      expect(daemon.replies, isEmpty);
      expect((await gateway.pendingPermissions()).single.id, 'request-1');
    },
  );

  test('location change retires a queued show approval', () async {
    gateway.genUiShowAllowed = null;
    await gateway.sessions();
    await ask(_request());
    gateway.genUiShowAllowed = () => true;
    gateway.setLocation(directory: '/work/other');
    await pumpEventQueue();
    expect(daemon.replies, isEmpty);
    expect(await gateway.pendingPermissions(), isEmpty);
  });

  test('qualification failure keeps the ordinary permission request', () async {
    gateway.genUiShowAllowed = () => throw StateError('qualification failed');
    await gateway.sessions();
    await ask(_request());
    expect(daemon.replies, isEmpty);
    expect((await gateway.pendingPermissions()).single.id, 'request-1');
  });

  test(
    'failed show approval is not retried when a listener rebinds qualification',
    () async {
      final server = _Daemon()..agents.add(_agent('agent-1'));
      final refusing = _RefusingGateway(
        transport: PaseoTransport(
          endpoint: 'ws://127.0.0.1:6767',
          socketFactory: (_, _) async => server,
        ),
      );
      addTearDown(refusing.close);
      refusing.genUiShowAllowed = () => true;
      refusing.openEventChannel(
        onEvent: (event) {
          if (event.type == 'permission.asked' && refusing.attempts == 1) {
            refusing.genUiShowAllowed = () => true;
          }
        },
        onStatus: (_) {},
      );
      await refusing.sessions();
      server.push('agent_permission_request', {
        'agentId': 'agent-1',
        'request': _request(),
      });
      await pumpEventQueue();
      expect(refusing.attempts, 1);
      expect((await refusing.pendingPermissions()).single.id, 'request-1');
    },
  );

  test('unqualified gateway never auto-allows', () async {
    gateway.genUiShowAllowed = null;
    await gateway.sessions();
    await ask(_request());
    expect(daemon.replies, isEmpty);
    expect((await gateway.pendingPermissions()).single.id, 'request-1');
  });

  test('unqualified daemon version never auto-allows', () async {
    daemon.version = '0.8.0';
    await gateway.sessions();
    await ask(_request());
    expect(daemon.replies, isEmpty);
    expect(await gateway.pendingPermissions(), hasLength(1));
  });

  test(
    'exact raw name and tool kind are required, not normalized names',
    () async {
      await gateway.sessions();
      final requests = [
        _request(name: 'MCP__OC-UI__SHOW'),
        _request(name: 'mcp__oc-ui__show '),
        _request(name: 'mcp__oc-ui__show_more'),
        _request(name: 'mcp__other__show'),
        _request(name: 'Bash'),
        _request(kind: 'question'),
        _request(kind: 'plan'),
        _request(provider: 'codex'),
      ];
      for (var i = 0; i < requests.length; i++) {
        await ask({...requests[i], 'id': 'request-$i'});
      }
      expect(daemon.replies, isEmpty);
      // Malformed native questions/plans are never converted to tool grants;
      // a foreign provider must not turn into a Claude approval either.
      expect(
        (await gateway.pendingPermissions()).map((request) => request.id),
        [for (var i = 0; i < 5; i++) 'request-$i'],
      );
      expect(await gateway.pendingQuestionsV2(), isEmpty);
    },
  );

  test('foreign, unknown and non-Claude sessions never auto-allow', () async {
    daemon.agents.addAll([
      _agent('foreign', directory: '/elsewhere'),
      _agent('other-provider', provider: 'pi'),
    ]);
    await gateway.sessions();
    await ask(_request(), agent: 'foreign');
    await ask(_request(), agent: 'unknown');
    await ask(_request(), agent: 'other-provider');
    expect(daemon.replies, isEmpty);
  });
}
