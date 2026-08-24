import 'package:opencode_sdk/opencode_sdk.dart';
import 'package:opencode_sdk/src/deserialize.dart';
import 'package:test/test.dart';

void expectRoundTrip<T>(
  Map<String, dynamic> fixture,
  T Function(Map<String, dynamic>) fromJson,
  Object? Function(T) toJson,
) {
  expect(toJson(fromJson(fixture)), equals(fixture));
}

void main() {
  group('ToolState every branch', () {
    final fixtures = <Map<String, dynamic>>[
      {
        'status': 'pending',
        'input': {'path': 'a.txt'},
        'raw': 'raw input',
      },
      {
        'status': 'running',
        'input': {'command': 'dart test'},
        'title': 'Testing',
        'metadata': {'pid': 42},
        'time': {'start': 100},
      },
      {
        'status': 'completed',
        'input': {'path': 'a.txt'},
        'output': 'complete',
        'title': 'Read file',
        'metadata': {'bytes': 8},
        'time': {'start': 100, 'end': 110},
        'attachments': <Object?>[],
      },
      {
        'status': 'error',
        'input': {'path': 'missing.txt'},
        'error': 'not found',
        'time': {'start': 100, 'end': 101},
      },
    ];
    for (final fixture in fixtures) {
      test(fixture['status'] as String, () {
        expectRoundTrip(fixture, ToolState.fromJson, (value) => value.toJson());
      });
    }
  });

  test('Event preserves a representative branch through endpoint dispatch', () {
    final fixtures = <Map<String, dynamic>>[
      {
        'id': 'evt_1',
        'type': 'session.idle',
        'properties': {'sessionID': 'ses_1'},
      },
      {'id': 'evt_2', 'type': 'server.connected', 'properties': {}},
    ];
    for (final fixture in fixtures) {
      final event = deserialize<Event, Event>(fixture, 'Event');
      expect(event.toJson(), equals(fixture));
    }
  });

  test('V2Event preserves metadata, location, durable, and branch data', () {
    final fixture = <String, dynamic>{
      'id': 'evt_2',
      'metadata': {'trace': 'abc'},
      'type': 'session.next.tool.progress',
      'durable': {'aggregateID': 'ses_1', 'seq': 7},
      'location': {'directory': '/tmp/project', 'workspace': 'main'},
      'data': {
        'sessionID': 'ses_1',
        'messageID': 'msg_1',
        'callID': 'call_1',
        'tool': 'bash',
        'title': 'Running',
        'metadata': {'percent': 50},
        'time': {'start': 100},
      },
    };
    expectRoundTrip(
      fixture,
      (json) => deserialize<V2Event, V2Event>(json, 'V2Event'),
      (value) => value.toJson(),
    );
  });

  test('SessionDurableEvent preserves a representative branch', () {
    final fixture = <String, dynamic>{
      'id': 'evt_3',
      'type': 'session.next.revert.cleared',
      'durable': {'aggregateID': 'ses_1', 'seq': 8},
      'data': {'sessionID': 'ses_1'},
    };
    expectRoundTrip(
      fixture,
      SessionDurableEvent.fromJson,
      (value) => value.toJson(),
    );
  });

  test('Auth representative oauth, api, and wellknown branches', () {
    final fixtures = <Map<String, dynamic>>[
      {
        'type': 'oauth',
        'refresh': 'refresh',
        'access': 'access',
        'expires': 123,
        'accountId': 'acct',
      },
      {'type': 'api', 'key': 'key'},
      {'type': 'wellknown', 'key': 'key', 'token': 'token'},
    ];
    for (final fixture in fixtures) {
      expectRoundTrip(fixture, Auth.fromJson, (value) => value.toJson());
    }
  });

  test('Part representative text and tool branches', () {
    final fixtures = <Map<String, dynamic>>[
      {
        'id': 'prt_1',
        'sessionID': 'ses_1',
        'messageID': 'msg_1',
        'type': 'text',
        'text': 'hello',
      },
      {
        'id': 'prt_2',
        'sessionID': 'ses_1',
        'messageID': 'msg_1',
        'type': 'tool',
        'callID': 'call_1',
        'tool': 'read',
        'state': {'status': 'pending', 'input': {}, 'raw': ''},
      },
    ];
    for (final fixture in fixtures) {
      expectRoundTrip(fixture, ModelPart.fromJson, (value) => value.toJson());
    }
  });

  test('Message representative user and assistant branches', () {
    final fixtures = <Map<String, dynamic>>[
      {
        'id': 'msg_1',
        'sessionID': 'ses_1',
        'role': 'user',
        'time': {'created': 100},
        'agent': 'build',
        'model': {'providerID': 'openai', 'modelID': 'gpt'},
      },
      {
        'id': 'msg_2',
        'sessionID': 'ses_1',
        'role': 'assistant',
        'time': {'created': 101},
        'parentID': 'msg_1',
        'modelID': 'gpt',
        'providerID': 'openai',
        'mode': 'build',
        'agent': 'build',
        'path': {'cwd': '/tmp/project', 'root': '/tmp/project'},
        'cost': 0,
        'tokens': {
          'input': 1,
          'output': 2,
          'reasoning': 0,
          'cache': {'read': 0, 'write': 0},
        },
      },
    ];
    for (final fixture in fixtures) {
      expectRoundTrip(fixture, Message.fromJson, (value) => value.toJson());
    }
  });

  test('MCPStatus representative branches', () {
    final fixtures = <Map<String, dynamic>>[
      {'status': 'connected'},
      {'status': 'disabled'},
      {'status': 'failed', 'error': 'offline'},
      {'status': 'needs_auth'},
      {'status': 'needs_client_registration', 'error': 'register first'},
    ];
    for (final fixture in fixtures) {
      expectRoundTrip(fixture, MCPStatus.fromJson, (value) => value.toJson());
    }
  });

  test('SessionStatus idle, retry, and busy branches', () {
    final fixtures = <Map<String, dynamic>>[
      {'type': 'idle'},
      {'type': 'busy'},
      {
        'type': 'retry',
        'attempt': 2,
        'message': 'rate limited',
        'next': 200,
        'action': {
          'reason': 'limit',
          'provider': 'openai',
          'title': 'Retrying',
          'message': 'Please wait',
          'label': 'Retry',
        },
      },
    ];
    for (final fixture in fixtures) {
      expectRoundTrip(
        fixture,
        SessionStatus.fromJson,
        (value) => value.toJson(),
      );
    }
  });

  test('GlobalEvent preserves non-sync payload', () {
    final fixture = <String, dynamic>{
      'directory': '/tmp/project',
      'payload': {
        'id': 'evt_4',
        'type': 'session.idle',
        'properties': {'sessionID': 'ses_1'},
      },
    };
    expectRoundTrip(fixture, GlobalEvent.fromJson, (value) => value.toJson());
  });

  test('GlobalEvent preserves sync payload', () {
    final fixture = <String, dynamic>{
      'directory': '/tmp/project',
      'project': 'project-1',
      'payload': {
        'type': 'sync',
        'id': 'sync-envelope-1',
        'syncEvent': {
          'type': 'session.created.1',
          'id': 'sync-event-1',
          'seq': 1,
          'aggregateID': 'ses_1',
          'data': {'id': 'ses_1', 'projectID': 'project-1'},
        },
      },
    };
    expectRoundTrip(fixture, GlobalEvent.fromJson, (value) => value.toJson());
  });
}
