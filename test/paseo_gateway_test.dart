import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProductException;
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/mappers.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/paseo_connection_probe.dart';
import 'package:opencode_mobile/state/profiles.dart';

const _dir = '/work/app';

/// A scripted daemon: answers `hello`, records every session message, and
/// lets a test reply to or push at will. Shapes follow frames captured from
/// Paseo 0.8.0 (docs/research/paseo-backend-spike-2026-09-19.md).
class FakeDaemon implements PaseoSocket {
  final _incoming = StreamController<Object?>();
  final sent = <Map<String, dynamic>>[];
  Map<String, dynamic>? hello;
  bool closed = false;

  /// When set, `hello` is answered by closing with this code instead.
  int? rejectWith;
  @override
  int? closeCode;

  /// Auto-replies by request type; a handler returns the response type and
  /// payload, or null to stay silent.
  final handlers =
      <
        String,
        (String, Map<String, dynamic>)? Function(Map<String, dynamic>)
      >{};

  @override
  Stream<Object?> get messages => _incoming.stream;

  @override
  void send(String message) {
    final json = jsonDecode(message) as Map<String, dynamic>;
    if (json['type'] == 'hello') {
      hello = json;
      if (rejectWith != null) {
        closeCode = rejectWith;
        scheduleMicrotask(close);
        return;
      }
      push('status', {'status': 'server_info', 'version': '0.8.0'});
      return;
    }
    if (json['type'] == 'ping') return;
    final inner = json['message'] as Map<String, dynamic>;
    sent.add(inner);
    final handler = handlers[inner['type']];
    final reply = handler?.call(inner);
    if (reply != null) {
      push(reply.$1, {...reply.$2, 'requestId': inner['requestId']});
    }
  }

  void push(String type, Map<String, dynamic> payload) {
    scheduleMicrotask(() {
      if (closed) return;
      _incoming.add(
        jsonEncode({
          'type': 'session',
          'message': {'type': type, 'payload': payload},
        }),
      );
    });
  }

  List<Map<String, dynamic>> of(String type) =>
      sent.where((m) => m['type'] == type).toList();

  @override
  Future<void> close() async {
    closed = true;
    await _incoming.close();
  }
}

Map<String, dynamic> agentJson(
  String id, {
  String cwd = _dir,
  String status = 'idle',
  String? archivedAt,
  List<Map<String, dynamic>> pendingPermissions = const [],
}) => {
  'id': id,
  'provider': 'claude',
  'persistence': {'provider': 'claude', 'sessionId': 'native-$id'},
  'cwd': cwd,
  'model': 'claude-haiku-4-5',
  'title': 'Agent $id',
  'status': status,
  'createdAt': '2026-09-19T03:35:27.840Z',
  'updatedAt': '2026-09-19T03:35:31.806Z',
  'currentModeId': 'default',
  'availableModes': [
    {'id': 'default', 'label': 'Always Ask'},
    {'id': 'acceptEdits', 'label': 'Accept File Edits'},
  ],
  'pendingPermissions': pendingPermissions,
  'lastUsage': {'totalCostUsd': 0.04},
  'archivedAt': archivedAt,
};

Map<String, dynamic> writePermission(String id) => {
  'id': id,
  'provider': 'claude',
  'name': 'Write',
  'kind': 'tool',
  'input': {'file_path': '$_dir/hello.txt', 'content': 'hi'},
  'detail': {'type': 'write', 'filePath': '$_dir/hello.txt', 'content': 'hi'},
  'suggestions': [
    {'type': 'setMode', 'mode': 'acceptEdits', 'destination': 'session'},
  ],
  'metadata': {'toolUseId': 'toolu_1'},
};

Map<String, dynamic> stream(
  String agentID,
  Map<String, dynamic> event, {
  int? seq,
}) => {
  'agentId': agentID,
  'event': event,
  'timestamp': '2026-09-19T03:40:54.082Z',
  'seq': ?seq,
  if (seq != null) 'epoch': 'epoch-1',
};

Map<String, dynamic> timeline(Map<String, dynamic> item) => {
  'type': 'timeline',
  'provider': 'claude',
  'item': item,
  'turnId': 'turn-1',
};

void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;
  late List<EventEnvelope> events;

  List<String> types() => events.map((e) => e.type).toList();

  setUp(() {
    daemon = FakeDaemon();
    daemon.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agentJson('a1')},
          {'agent': agentJson('other', cwd: '/work/elsewhere')},
          {'agent': agentJson('gone', archivedAt: '2026-09-19T04:00:00.000Z')},
        ],
        'pageInfo': {'nextCursor': null, 'prevCursor': null, 'hasMore': false},
      },
    );
    daemon.handlers['get_providers_snapshot_request'] = (_) => (
      'get_providers_snapshot_response',
      {
        'entries': [
          {
            'provider': 'claude',
            'status': 'ready',
            'models': [
              {'provider': 'claude', 'id': 'opus', 'label': 'Opus'},
              {
                'provider': 'claude',
                'id': 'haiku',
                'label': 'Haiku',
                'isDefault': true,
              },
            ],
            'modes': [
              {'id': 'plan', 'label': 'Plan'},
              {'id': 'default', 'label': 'Always Ask'},
            ],
          },
          {'provider': 'pi', 'status': 'ready', 'models': <Object>[]},
          {'provider': 'codex', 'status': 'error', 'error': 'not installed'},
        ],
        'generatedAt': '2026-09-19T03:35:27.840Z',
      },
    );
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _dir,
    );
    events = [];
    gateway.openEventChannel(onEvent: events.add, onStatus: (_) {}).start();
  });

  tearDown(() => gateway.close());

  group('endpoint policy', () {
    test('cleartext is limited to this device and Tailscale', () {
      expect(paseoEndpoint('ws://127.0.0.1:6767').path, '/ws');
      expect(paseoEndpoint('ws://100.64.0.10:6767').host, '100.64.0.10');
      expect(paseoEndpoint('ws://pc.tail1234.ts.net:6767').scheme, 'ws');
      expect(paseoEndpoint('wss://pc.tail1234.ts.net').path, '/ws');
      for (final bad in [
        'ws://192.168.1.10:6767',
        'ws://paseo.example:6767',
        'wss://paseo.example:6767',
        'http://127.0.0.1:6767',
        'ws://user:pw@127.0.0.1:6767',
        'ws://127.0.0.1:6767/other',
      ]) {
        expect(
          () => paseoEndpoint(bad),
          throwsA(
            isA<PaseoFailure>().having(
              (f) => f.kind,
              'kind',
              PaseoFailureKind.invalidEndpoint,
            ),
          ),
          reason: bad,
        );
      }
    });

    test('profile validators agree and an empty password is allowed', () {
      expect(validatePaseoServerUrl('ws://100.64.0.10:6767'), isNull);
      expect(validatePaseoServerUrl('ws://192.168.1.10:6767'), isNotNull);
      expect(validatePaseoServerUrl('wss://public.example:6767'), isNotNull);
      expect(validatePaseoServerUrl('wss://100.64.0.10:6767'), isNull);
      expect(normalizePaseoServerUrl('100.64.0.10:6767'), startsWith('ws://'));
      expect(normalizePaseoServerUrl('paseo.example'), startsWith('wss://'));
      expect(validatePaseoPassword(''), isNull);
      expect(validatePaseoPassword('s3cret'), isNull);
      expect(validatePaseoPassword('has space'), isNotNull);
      expect(validatePaseoPassword('a,b'), isNotNull);
    });

    test('a paseo profile round-trips without its password', () {
      final profile = ServerProfile(
        id: 'p1',
        name: 'PC',
        baseUrl: 'ws://100.64.0.10:6767',
        backend: ServerBackend.paseo,
        codexDirectory: _dir,
        codexToken: 's3cret',
      );
      final json = profile.toJson();
      expect(jsonEncode(json), isNot(contains('s3cret')));
      final restored = ServerProfile.fromJson(json);
      expect(restored.backend, ServerBackend.paseo);
      expect(restored.codexDirectory, _dir);
      expect(restored.usesAgentSocket, isTrue);
      expect(restored.agentSocketSecretRequired, isFalse);
    });
  });

  test('a rejected password is an authentication failure', () async {
    // The daemon accepts the upgrade and only then closes with 4401.
    final rejecting = FakeDaemon()..rejectWith = PaseoTransport.closeAuthFailed;
    final refused = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        password: 'wrong',
        socketFactory: (_, _) async => rejecting,
      ),
      directory: _dir,
    );
    addTearDown(refused.close);
    await expectLater(
      refused.health(),
      throwsA(
        isA<PaseoFailure>().having(
          (f) => f.kind,
          'kind',
          PaseoFailureKind.authentication,
        ),
      ),
    );
  });

  test('hello claims only what the client implements', () async {
    await gateway.health();
    expect(daemon.hello!['protocolVersion'], 1);
    expect(daemon.hello!['capabilities'], {
      'all_providers': true,
      'provider_subagents': true,
      'projected_subagent_timeline': true,
    });
    // Below 0.1.45 the daemon hides Pi and every other newer runtime.
    expect(daemon.hello!['appVersion'], PaseoTransport.clientAppVersion);
    expect((await gateway.health()).version, contains('0.8.0'));
  });

  test('the agent\'s own commands are listed without its plumbing', () async {
    daemon.handlers['list_commands_request'] = (_) => (
      'list_commands_response',
      {
        'commands': [
          {'name': 'compact', 'description': 'Compact the conversation'},
          {'name': '__remote-workflow'},
          {'name': '/__internal'},
          {'name': '/review'},
        ],
      },
    );
    final commands = await gateway.listCommands();
    expect(commands.map((c) => c.name), ['compact', 'review']);
  });

  test('an update keeps the conversation\'s model and mode', () async {
    await gateway.sessions();
    events.clear();
    daemon.push('agent_update', {
      'kind': 'upsert',
      'agent': agentJson('a1', status: 'running'),
    });
    await pumpEventQueue();
    final update = events.firstWhere((e) => e.type == 'session.updated');
    final session = Session.fromJson(
      update.properties['info'] as Map<String, dynamic>,
    );
    expect(session.selection?.model?.modelID, 'claude-haiku-4-5');
    expect(session.selection?.agent, 'default');
    expect(session.selection?.modelKnown, isTrue);
  });

  test('sessions are this folder\'s unarchived agents', () async {
    final sessions = await gateway.sessions();
    expect(sessions.map((s) => s.id), ['a1']);
    expect(sessions.single.title, 'Agent a1');
    expect(sessions.single.model, 'claude/claude-haiku-4-5');
    expect(sessions.single.cost, 0.04);
  });

  test('ready runtimes become providers; modes become agents', () async {
    final response = await gateway.providers();
    expect(response.providers.map((p) => p.id), ['claude', 'pi']);
    expect(response.providers.first.name, 'Claude Code');
    expect(response.providers.first.modelIDs, ['opus', 'haiku']);
    expect(response.providers.last.modelIDs, [paseoDefaultModel]);
    expect(response.defaultProviderID, 'claude');
    expect(response.defaultModelID, 'haiku');
    expect((await gateway.agents()).map((a) => a.name), ['plan', 'default']);
    final defaults = await gateway.loadChatDefaults();
    expect(defaults.agent, 'default');
    expect(defaults.model!.modelID, 'haiku');
  });

  test(
    'a daemon still listing its models is waited for, not believed',
    () async {
      providerWarmupInterval = Duration.zero;
      addTearDown(
        () => providerWarmupInterval = const Duration(milliseconds: 1500),
      );
      final settled = daemon.handlers['get_providers_snapshot_request']!;
      var reads = 0;
      daemon.handlers['get_providers_snapshot_request'] = (message) {
        reads++;
        if (reads < 3) {
          return (
            'get_providers_snapshot_response',
            {
              'entries': [
                {'provider': 'claude', 'status': 'loading'},
              ],
              'generatedAt': '2026-09-19T03:35:27.840Z',
            },
          );
        }
        return settled(message);
      };
      final defaults = await gateway.loadChatDefaults();
      expect(reads, 3);
      expect(defaults.model?.providerID, 'claude');
      expect(defaults.model?.modelID, 'haiku');
      // Settled snapshots are cached; a loading one never is.
      await gateway.providers();
      expect(reads, 3);
    },
  );

  group('a new conversation', () {
    late Session draft;

    Future<void> create({List<void Function()> before = const []}) async {
      daemon.handlers['create_agent_request'] = (_) {
        for (final action in before) {
          action();
        }
        return (
          'status',
          {
            'status': 'agent_created',
            'agent': agentJson('real-1', status: 'initializing'),
          },
        );
      };
      draft = await gateway.createSession();
      await gateway.promptAsync(
        draft.id,
        text: 'hello',
        model: ModelRef(providerID: 'claude', modelID: 'haiku'),
        agent: 'plan',
      );
    }

    test('stays local until its first prompt names the runtime', () async {
      draft = await gateway.createSession();
      expect(daemon.of('create_agent_request'), isEmpty);
      expect(await gateway.messages(draft.id), isEmpty);
      expect((await gateway.sessions()).first.id, draft.id);

      await gateway.providers();
      await create();
      final request = daemon.of('create_agent_request').single;
      expect(request['config'], {
        'provider': 'claude',
        'cwd': _dir,
        'model': 'haiku',
        'modeId': 'plan',
      });
      expect(request['initialPrompt'], 'hello');
    });

    test('keeps its app id while the daemon uses its own', () async {
      await create();
      daemon.handlers['fetch_agent_request'] = (m) {
        expect(m['agentId'], 'real-1');
        return ('fetch_agent_response', {'agent': agentJson('real-1')});
      };
      expect((await gateway.session(draft.id)).id, draft.id);

      daemon.handlers['send_agent_message_request'] = (_) => (
        'send_agent_message_response',
        {'agentId': 'real-1', 'accepted': true, 'error': null},
      );
      await gateway.promptAsync(draft.id, text: 'again');
      expect(
        daemon.of('send_agent_message_request').single['agentId'],
        'real-1',
      );

      events.clear();
      daemon.push(
        'agent_stream',
        stream('real-1', {'type': 'turn_started', 'provider': 'claude'}),
      );
      daemon.push(
        'agent_stream',
        stream(
          'real-1',
          timeline({
            'type': 'assistant_message',
            'text': 'Hi',
            'messageId': 'msg_1',
          }),
          seq: 2,
        ),
      );
      await pumpEventQueue();
      final updated = events.firstWhere((e) => e.type == 'message.updated');
      expect((updated.properties['info'] as Map)['sessionID'], draft.id);
    });

    test('replays events that raced ahead of the create answer', () async {
      await create(
        before: [
          () => daemon.push('agent_update', {
            'kind': 'upsert',
            'agent': agentJson('real-1', status: 'running'),
          }),
          () => daemon.push(
            'agent_stream',
            stream(
              'real-1',
              timeline({
                'type': 'user_message',
                'text': 'hello',
                'messageId': 'u1',
              }),
              seq: 1,
            ),
          ),
        ],
      );
      await pumpEventQueue();
      // No second session under the daemon's id, and the early message lands
      // on the conversation the chat screen is showing.
      expect(types(), isNot(contains('session.created')));
      final info =
          events
                  .firstWhere((e) => e.type == 'message.updated')
                  .properties['info']
              as Map;
      expect(info['sessionID'], draft.id);
      expect(
        (await gateway.sessions()).where((s) => s.id == 'real-1'),
        isEmpty,
      );
    });

    test('a spawn-time idle snapshot does not end the run', () async {
      await create();
      events.clear();
      daemon.push('agent_update', {
        'kind': 'upsert',
        'agent': agentJson('real-1', status: 'idle'),
      });
      daemon.push(
        'agent_stream',
        stream('real-1', {'type': 'turn_started', 'provider': 'claude'}),
      );
      // Snapshots lag the stream: this idle describes the moment before.
      daemon.push('agent_update', {
        'kind': 'upsert',
        'agent': agentJson('real-1', status: 'idle'),
      });
      await pumpEventQueue();
      expect(types(), isNot(contains('session.idle')));
      expect((await gateway.sessionStatuses())[draft.id], 'busy');

      daemon.push(
        'agent_stream',
        stream('real-1', {'type': 'turn_completed', 'provider': 'claude'}),
      );
      await pumpEventQueue();
      expect(types(), contains('session.idle'));
      expect((await gateway.sessionStatuses())[draft.id], 'idle');
    });
  });

  group('streaming', () {
    setUp(() async {
      await gateway.sessions();
      events.clear();
    });

    test('a finished turn finishes its replies', () async {
      daemon.push(
        'agent_stream',
        stream(
          'a1',
          timeline({
            'type': 'assistant_message',
            'text': 'Created flow1.txt',
            'messageId': 'msg_9',
          }),
          seq: 30,
        ),
      );
      await pumpEventQueue();
      final announced = events.lastWhere((e) => e.type == 'message.updated');
      expect(
        (announced.properties['info'] as Map)['time']['completed'],
        isNull,
      );
      daemon.push(
        'agent_stream',
        stream('a1', {'type': 'turn_completed', 'provider': 'claude'}, seq: 31),
      );
      await pumpEventQueue();
      final finished = events
          .where((e) => e.type == 'message.updated')
          .map((e) => e.properties['info'] as Map)
          .where((info) => info['id'] == 'msg_9')
          .last;
      expect(finished['time']['completed'], isNotNull);
    });

    test('assistant text is announced once, then streamed as deltas', () async {
      for (final (i, text) in ['Done', '. The file', ' exists.'].indexed) {
        daemon.push(
          'agent_stream',
          stream(
            'a1',
            timeline({
              'type': 'assistant_message',
              'text': text,
              'messageId': 'msg_1',
            }),
            seq: 10 + i,
          ),
        );
      }
      await pumpEventQueue();
      expect(types(), [
        'message.updated',
        'message.part.updated',
        'message.part.delta',
        'message.part.delta',
      ]);
      expect((events[1].properties['part'] as Map)['text'], 'Done');
      expect(events[2].properties['partID'], 'msg_1:0');
      expect(events[3].properties['delta'], ' exists.');
    });

    test('a replayed sequence number is ignored', () async {
      final frame = stream(
        'a1',
        timeline({'type': 'assistant_message', 'text': 'Hi', 'messageId': 'm'}),
        seq: 5,
      );
      daemon.push('agent_stream', frame);
      daemon.push('agent_stream', frame);
      await pumpEventQueue();
      expect(types(), ['message.updated', 'message.part.updated']);
    });

    test('reasoning deltas share one message until another item', () async {
      daemon.push(
        'agent_stream',
        stream('a1', timeline({'type': 'reasoning', 'text': 'Let me'}), seq: 1),
      );
      daemon.push(
        'agent_stream',
        stream('a1', timeline({'type': 'reasoning', 'text': ' think'}), seq: 2),
      );
      daemon.push(
        'agent_stream',
        stream(
          'a1',
          timeline({
            'type': 'tool_call',
            'callId': 'toolu_1',
            'name': 'Bash',
            'status': 'running',
            'detail': {'type': 'shell', 'command': 'ls'},
          }),
          seq: 3,
        ),
      );
      daemon.push(
        'agent_stream',
        stream('a1', timeline({'type': 'reasoning', 'text': 'Next'}), seq: 4),
      );
      await pumpEventQueue();
      final ids = events
          .where((e) => e.type == 'message.updated')
          .map((e) => (e.properties['info'] as Map)['id'])
          .toList();
      expect(ids, ['r:1', 'toolu_1', 'r:4']);
      expect(
        events
            .where((e) => e.type == 'message.part.delta')
            .single
            .properties['messageID'],
        'r:1',
      );
    });

    test('a tool call is one message updated by snapshots', () async {
      Map<String, dynamic> call(String status, [String? output]) => timeline({
        'type': 'tool_call',
        'callId': 'toolu_9',
        'name': 'Bash',
        'status': status,
        'error': null,
        'detail': {
          'type': 'shell',
          'command': 'cat hello.txt',
          'output': ?output,
        },
      });
      daemon.push('agent_stream', stream('a1', call('running'), seq: 1));
      daemon.push(
        'agent_stream',
        stream('a1', call('completed', 'hi'), seq: 2),
      );
      await pumpEventQueue();
      expect(types(), [
        'message.updated',
        'message.part.updated',
        'message.part.updated',
      ]);
      final part = events.last.properties['part'] as Map;
      expect(part['tool'], 'bash');
      expect(part['callID'], 'toolu_9');
      expect(part['state'], {
        'status': 'completed',
        'input': {'command': 'cat hello.txt'},
        'output': 'hi',
      });
    });

    test('a failed turn surfaces safe fixed copy', () async {
      daemon.push(
        'agent_stream',
        stream('a1', {'type': 'turn_started', 'provider': 'claude'}),
      );
      daemon.push(
        'agent_stream',
        stream('a1', {
          'type': 'turn_failed',
          'provider': 'claude',
          'error': 'Not logged in',
        }),
      );
      await pumpEventQueue();
      final error = events.firstWhere((e) => e.type == 'session.error');
      expect(
        ((error.properties['error'] as Map)['data'] as Map)['message'],
        'The agent could not finish this reply. Check it on your computer.',
      );
      expect(types().last, 'session.idle');
    });

    test('events for another folder\'s agent are dropped', () async {
      daemon.push(
        'agent_stream',
        stream('other', {'type': 'turn_started', 'provider': 'claude'}),
      );
      await pumpEventQueue();
      expect(events, isEmpty);
    });
  });

  group('permissions', () {
    setUp(() async {
      await gateway.sessions();
      events.clear();
      daemon.push('agent_permission_request', {
        'agentId': 'a1',
        'request': writePermission('perm-1'),
      });
      await pumpEventQueue();
    });

    test('a write request becomes an edit card with its file', () async {
      final asked = events.single;
      expect(asked.type, 'permission.asked');
      expect(asked.properties['permission'], 'edit');
      expect(asked.properties['patterns'], ['$_dir/hello.txt']);
      expect(asked.properties['always'], ['$_dir/hello.txt']);
      // A new file is a patch of added lines, so the diff view can draw it.
      expect(
        (asked.properties['metadata'] as Map)['diff'],
        '--- /dev/null\n+++ b/$_dir/hello.txt\n@@ -0,0 +1,1 @@\n+hi',
      );
      expect((await gateway.pendingPermissions()).single.id, 'perm-1');
    });

    test('once allows; always also applies the provider\'s rule', () async {
      await gateway.respondPermission('perm-1', 'once');
      expect(daemon.of('agent_permission_response').single, {
        'type': 'agent_permission_response',
        'agentId': 'a1',
        'requestId': 'perm-1',
        'response': {'behavior': 'allow'},
      });
      expect(await gateway.pendingPermissions(), isEmpty);

      daemon.push('agent_permission_request', {
        'agentId': 'a1',
        'request': writePermission('perm-2'),
      });
      await pumpEventQueue();
      await gateway.respondPermission('perm-2', 'always');
      expect(daemon.of('agent_permission_response').last['response'], {
        'behavior': 'allow',
        'updatedPermissions': [
          {'type': 'setMode', 'mode': 'acceptEdits', 'destination': 'session'},
        ],
      });
    });

    test('reject denies with the reason; cancel also interrupts', () async {
      await gateway.respondPermission(
        'perm-1',
        'reject',
        message: 'wrong file',
      );
      expect(daemon.of('agent_permission_response').single['response'], {
        'behavior': 'deny',
        'message': 'wrong file',
      });
      daemon.push('agent_permission_request', {
        'agentId': 'a1',
        'request': writePermission('perm-3'),
      });
      await pumpEventQueue();
      await gateway.respondPermission('perm-3', 'cancel');
      expect(daemon.of('agent_permission_response').last['response'], {
        'behavior': 'deny',
        'interrupt': true,
      });
    });

    test('an answered request does not return from a stale snapshot', () async {
      await gateway.respondPermission('perm-1', 'once');
      events.clear();
      daemon.push('agent_update', {
        'kind': 'upsert',
        'agent': agentJson(
          'a1',
          status: 'running',
          pendingPermissions: [writePermission('perm-1')],
        ),
      });
      await pumpEventQueue();
      expect(types(), isNot(contains('permission.asked')));
      await expectLater(
        gateway.respondPermission('perm-1', 'once'),
        throwsA(
          isA<PaseoFailure>().having(
            (f) => f.kind,
            'kind',
            PaseoFailureKind.staleRequest,
          ),
        ),
      );
    });

    test(
      'a request answered elsewhere is retired by the next snapshot',
      () async {
        events.clear();
        daemon.push('agent_update', {
          'kind': 'upsert',
          'agent': agentJson('a1'),
        });
        await pumpEventQueue();
        expect(types(), contains('permission.replied'));
        expect(await gateway.pendingPermissions(), isEmpty);
      },
    );
  });

  test('history maps the merged timeline and primes the live stream', () async {
    await gateway.sessions();
    daemon.handlers['fetch_agent_request'] = (_) =>
        ('fetch_agent_response', {'agent': agentJson('a1')});
    daemon.handlers['fetch_agent_timeline_request'] = (m) {
      expect(m['projection'], 'projected');
      return (
        'fetch_agent_timeline_response',
        {
          'agentId': 'a1',
          'epoch': 'epoch-1',
          'endCursor': {'epoch': 'epoch-1', 'seq': 12},
          'entries': [
            {
              'provider': 'claude',
              'item': {
                'type': 'user_message',
                'text': 'Read it',
                'messageId': 'u1',
              },
              'timestamp': '2026-09-19T03:35:27.922Z',
              'seqStart': 1,
            },
            {
              'provider': 'claude',
              'item': {
                'type': 'tool_call',
                'callId': 'toolu_1',
                'name': 'Read',
                'status': 'completed',
                'detail': {
                  'type': 'read',
                  'filePath': '$_dir/README.md',
                  'content': '# spike',
                },
              },
              'timestamp': '2026-09-19T03:35:30.000Z',
              'seqStart': 2,
            },
            {
              'provider': 'claude',
              'item': {'type': 'reasoning', 'text': 'Thinking'},
              'timestamp': '2026-09-19T03:35:31.000Z',
              'seqStart': 5,
            },
            {
              'provider': 'claude',
              'item': {
                'type': 'assistant_message',
                'text': 'Done.',
                'messageId': 'msg_1',
              },
              'timestamp': '2026-09-19T03:35:47.208Z',
              'seqStart': 9,
            },
          ],
        },
      );
    };
    final messages = await gateway.messages('a1');
    expect(messages.map((m) => m.info.id), ['u1', 'toolu_1', 'r:5', 'msg_1']);
    expect(messages.map((m) => m.info.role), [
      'user',
      'assistant',
      'assistant',
      'assistant',
    ]);
    expect(messages[1].parts.single.toolName, 'read');
    expect(messages[1].parts.single.toolState.output, '# spike');
    expect(messages[2].parts.single.type, 'reasoning');
    expect(messages.last.info.time!.completed, isNotNull);

    // Already-hydrated text continues as a delta, and old sequence numbers
    // from a reconnect replay are skipped.
    events.clear();
    daemon.push(
      'agent_stream',
      stream(
        'a1',
        timeline({
          'type': 'assistant_message',
          'text': 'x',
          'messageId': 'msg_1',
        }),
        seq: 12,
      ),
    );
    daemon.push(
      'agent_stream',
      stream(
        'a1',
        timeline({
          'type': 'assistant_message',
          'text': ' More',
          'messageId': 'msg_1',
        }),
        seq: 13,
      ),
    );
    await pumpEventQueue();
    expect(types(), ['message.part.delta']);
    expect(events.single.properties['delta'], ' More');
  });

  test(
    'delete archives, and an archive elsewhere removes the session',
    () async {
      await gateway.sessions();
      daemon.handlers['archive_agent_request'] = (m) => (
        'agent_archived',
        {'agentId': m['agentId'], 'archivedAt': '2026-09-19T05:00:00.000Z'},
      );
      await gateway.deleteSession('a1');
      expect(daemon.of('archive_agent_request').single['agentId'], 'a1');
      expect(daemon.of('delete_agent_request'), isEmpty);
    },
  );

  test('archiveSession sends the archive request (menu: Archive)', () async {
    await gateway.sessions();
    daemon.handlers['archive_agent_request'] = (m) => (
      'agent_archived',
      {'agentId': m['agentId'], 'archivedAt': '2026-09-19T05:00:00.000Z'},
    );
    await gateway.archiveSession('a1');
    expect(daemon.of('archive_agent_request').single['agentId'], 'a1');
    expect(daemon.of('delete_agent_request'), isEmpty);
  });

  test(
    'a refused request is a fixed failure, never the daemon\'s text',
    () async {
      daemon.handlers['cancel_agent_request'] = (_) =>
          ('rpc_error', {'error': 'secret internals', 'code': 'x'});
      await gateway.sessions();
      await expectLater(
        gateway.abort('a1'),
        throwsA(
          isA<PaseoFailure>()
              .having((f) => f.kind, 'kind', PaseoFailureKind.unavailable)
              .having((f) => f.message, 'message', isNot(contains('secret'))),
        ),
      );
      expect(
        gateway.transport.lastDaemonError,
        'The agent request could not be completed.',
      );
    },
  );

  test('a dropped socket reconnects and re-reads the agent list', () async {
    await gateway.sessions();
    final statuses = <StreamStatus>[];
    final replacement = FakeDaemon()..handlers.addAll(daemon.handlers);
    final reconnecting = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (() {
          var first = true;
          final initial = FakeDaemon()..handlers.addAll(daemon.handlers);
          return (Uri _, String _) async {
            if (first) {
              first = false;
              scheduleMicrotask(() async {
                await pumpEventQueue();
                await initial.close();
              });
              return initial;
            }
            return replacement;
          };
        })(),
      ),
      directory: _dir,
    );
    addTearDown(reconnecting.close);
    reconnecting
        .openEventChannel(onEvent: (_) {}, onStatus: statuses.add)
        .start();
    await Future<void>.delayed(const Duration(milliseconds: 1500));
    expect(
      statuses,
      containsAllInOrder([
        StreamStatus.connected,
        StreamStatus.reconnecting,
        StreamStatus.connected,
      ]),
    );
    expect(replacement.of('fetch_agents_request'), isNotEmpty);
  });

  group('sub-agents', () {
    Map<String, dynamic> agentCall(String status) => timeline({
      'type': 'tool_call',
      'callId': 'toolu_sub',
      'name': 'Agent',
      'status': status,
      'error': null,
      'detail': {
        'type': 'unknown',
        'input': {'description': 'List the files', 'subagent_type': 'Explore'},
      },
    });
    Map<String, dynamic> descriptor({String status = 'running'}) => {
      'id': 's1',
      'parentAgentId': 'a1',
      'parentSubagentId': null,
      'provider': 'claude',
      'title': 'List the files',
      'description': 'List the files',
      'status': status,
      'createdAt': '2026-10-06T10:00:00.000Z',
      'updatedAt': '2026-10-06T10:00:01.000Z',
      'toolCallId': 'toolu_sub',
      'cwd': _dir,
      'subtitle': null,
    };

    setUp(() async {
      await gateway.sessions();
      events.clear();
    });

    test(
      'a sub-agent is its conversation\'s child, and its card opens it',
      () async {
        daemon.push('agent_stream', stream('a1', agentCall('running'), seq: 1));
        daemon.push('agent.provider_subagents.update', {
          'kind': 'upsert',
          'subagent': descriptor(),
        });
        await pumpEventQueue();
        final created = events.firstWhere((e) => e.type == 'session.created');
        final info = Session.fromJson(
          created.properties['info'] as Map<String, dynamic>,
        );
        expect(info.id, paseoSubagentSessionId('s1'));
        expect(info.parentID, 'a1');
        expect(info.title, 'List the files');
        expect((await gateway.sessionStatuses())[info.id], 'busy');
        // The card shown before the sub-agent was reported now links to it.
        final card = events
            .where((e) => e.type == 'message.part.updated')
            .map((e) => e.properties['part'] as Map)
            .last;
        expect(card['tool'], 'task');
        expect((card['state'] as Map)['metadata'], {'sessionId': info.id});
      },
    );

    test('its steps stream into its own conversation', () async {
      daemon.push('agent.provider_subagents.update', {
        'kind': 'upsert',
        'subagent': descriptor(),
      });
      events.clear();
      daemon.push('agent.provider_subagents.update', {
        'kind': 'timeline',
        'parentAgentId': 'a1',
        'subagentId': 's1',
        'provider': 'claude',
        'item': {
          'type': 'assistant_message',
          'text': 'Found 7 files',
          'messageId': 'm_sub',
        },
        'timestamp': '2026-10-06T10:00:02.000Z',
        'seq': 3,
        'epoch': 'e1',
      });
      await pumpEventQueue();
      final message = events.firstWhere((e) => e.type == 'message.updated');
      expect(
        (message.properties['info'] as Map)['sessionID'],
        paseoSubagentSessionId('s1'),
      );
    });

    test('its history is read from the helper; it takes no replies', () async {
      daemon.push('agent.provider_subagents.update', {
        'kind': 'upsert',
        'subagent': descriptor(status: 'completed'),
      });
      daemon.handlers['agent.provider_subagents.timeline.get.request'] = (m) {
        expect(m['parentAgentId'], 'a1');
        expect(m['subagentId'], 's1');
        return (
          'agent.provider_subagents.timeline.get.response',
          {
            'parentAgentId': 'a1',
            'subagentId': 's1',
            'provider': 'claude',
            'epoch': 'e1',
            'endCursor': {'epoch': 'e1', 'seq': 4},
            'rows': [
              {
                'seq': 4,
                'seqStart': 4,
                'timestamp': '2026-10-06T10:00:03.000Z',
                'item': {
                  'type': 'assistant_message',
                  'text': '7 files',
                  'messageId': 'm_done',
                },
              },
            ],
            'error': null,
          },
        );
      };
      await pumpEventQueue();
      final id = paseoSubagentSessionId('s1');
      final history = await gateway.messages(id);
      expect(history.single.parts.single.text, '7 files');
      expect((await gateway.session(id)).parentID, 'a1');
      await expectLater(
        gateway.promptAsync(id, text: 'more'),
        throwsA(isA<ProductException>()),
      );
    });

    test('an old conversation\'s cards link to the sub-agents the helper '
        'still knows', () async {
      daemon.handlers['agent.provider_subagents.list.request'] = (m) => (
        'agent.provider_subagents.list.response',
        {
          'parentAgentId': 'a1',
          'subagents': [descriptor(status: 'completed')],
          'error': null,
        },
      );
      daemon.handlers['fetch_agent_request'] = (_) =>
          ('fetch_agent_response', {'agent': agentJson('a1')});
      daemon.handlers['fetch_agent_timeline_request'] = (_) => (
        'fetch_agent_timeline_response',
        {
          'agentId': 'a1',
          'epoch': 'epoch-1',
          'entries': [
            {
              'provider': 'claude',
              'seqStart': 1,
              'timestamp': '2026-10-06T10:00:00.000Z',
              'item': agentCall('completed')['item'],
            },
          ],
        },
      );
      final history = await gateway.messages('a1');
      expect(history.single.parts.single.toolName, 'task');
      // The list arrives beside the history; the card then links.
      await pumpEventQueue();
      final card = events
          .where((e) => e.type == 'message.part.updated')
          .map((e) => e.properties['part'] as Map)
          .last;
      expect((card['state'] as Map)['metadata'], {
        'sessionId': paseoSubagentSessionId('s1'),
      });
    });
  });

  group('probe', () {
    test('bad input never opens a socket', () async {
      var opened = false;
      final result = await probePaseoConnection(
        baseUrl: 'ws://192.168.1.4:6767',
        password: '',
        directory: _dir,
        gatewayFactory:
            ({required baseUrl, required password, required directory}) {
              opened = true;
              return gateway;
            },
      );
      expect(result.ok, isFalse);
      expect(opened, isFalse);
    });

    test('a verified daemon names its ready runtimes', () async {
      final result = await probePaseoConnection(
        baseUrl: '127.0.0.1:6767',
        password: '',
        directory: _dir,
        gatewayFactory:
            ({required baseUrl, required password, required directory}) {
              expect(baseUrl, 'ws://127.0.0.1:6767');
              return gateway;
            },
      );
      expect(result.ok, isTrue);
      expect(result.message, contains('Claude Code, Pi'));
      expect(result.version, contains('0.8.0'));
    });
  });

  test('permission mapping covers shell and unknown tools', () {
    final shell = paseoPermission('a1', {
      'id': 'p',
      'name': 'Bash',
      'kind': 'tool',
      'detail': {'type': 'shell', 'command': 'rm -rf build'},
    });
    expect(shell.permission, 'bash');
    expect(shell.patterns, ['rm -rf build']);
    expect(shell.always, isEmpty);
    final other = paseoPermission('a1', {
      'id': 'q',
      'name': 'WebFetch',
      'kind': 'tool',
      'input': {
        'url': 'https://example.com',
        'nested': {'x': 1},
      },
      'description': 'Fetch a page',
    });
    expect(other.permission, 'webfetch');
    expect(other.metadata, {
      'url': 'https://example.com',
      // What the card draws as labelled rows.
      'facts': [
        {'key': 'url', 'value': 'https://example.com'},
      ],
    });
    expect(other.message, 'Fetch a page');
  });
}
