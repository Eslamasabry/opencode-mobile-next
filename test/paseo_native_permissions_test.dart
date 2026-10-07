import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;

  Future<void> settle() =>
      Future<void>.delayed(const Duration(milliseconds: 30));

  Map<String, dynamic> request({
    String command = 'pwd',
    String mode = 'default',
  }) => {
    'id': 'permission-one',
    'provider': 'claude',
    'name': 'Bash',
    'kind': 'tool',
    'detail': {'type': 'shell', 'command': command},
    'suggestions': [
      {'type': 'setMode', 'mode': mode, 'destination': 'session'},
    ],
  };

  Future<void> push(Map<String, dynamic> value) async {
    daemon.push('agent_permission_request', {
      'agentId': 'a1',
      'request': value,
    });
    await settle();
  }

  setUp(() async {
    daemon = FakeDaemon();
    daemon.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          {'agent': agentJson('a1')},
        ],
        'pageInfo': {'nextCursor': null, 'prevCursor': null, 'hasMore': false},
      },
    );
    gateway = PaseoGateway(
      directory: '/work/app',
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
    );
    await gateway.sessions();
  });

  tearDown(() => gateway.close());

  test(
    'same-ID command and suggested rule replace the pending permission',
    () async {
      await push(request());
      expect((await gateway.pendingPermissions()).single.patterns, ['pwd']);
      await push(request(command: 'git status', mode: 'acceptEdits'));
      expect((await gateway.pendingPermissions()).single.patterns, [
        'git status',
      ]);
      await gateway.respondPermission(
        'permission-one',
        'always',
        legacySessionID: 'a1',
      );
      expect(daemon.of('agent_permission_response').single['response'], {
        'behavior': 'allow',
        'updatedPermissions': [
          {'type': 'setMode', 'mode': 'acceptEdits', 'destination': 'session'},
        ],
      });
      expect(daemon.of('resume_agent_request'), isEmpty);
      expect(daemon.of('fetch_agent_timeline_request'), isEmpty);
    },
  );

  test(
    'native permission snapshots detach data and preserve equivalent revisions',
    () async {
      await push(request());
      final captured = gateway.nativePermissionSnapshot('a1')!;
      captured.permission.patterns[0] = 'mutated';
      captured.permission.metadata['command'] = 'mutated';
      captured.permission.always.clear();
      final untouched = gateway.nativePermissionSnapshot('a1')!;
      expect(untouched.permission.patterns, ['pwd']);
      expect(untouched.permission.metadata['command'], 'pwd');
      expect(untouched.permission.always, ['pwd']);
      await push(request());
      expect(
        gateway.isNativePermissionCurrent(
          'a1',
          'permission-one',
          captured.revision,
        ),
        true,
      );
      expect(
        gateway.isNativePermissionCurrent(
          'other',
          'permission-one',
          captured.revision,
        ),
        false,
      );
      expect(gateway.nativePermissionSnapshot('other'), isNull);
    },
  );

  test(
    'hidden suggested rule changes invalidate a captured approval',
    () async {
      await push(request());
      final captured = gateway.nativePermissionSnapshot('a1')!;
      await push(request(mode: 'acceptEdits'));
      final changed = gateway.nativePermissionSnapshot('a1')!;
      expect(changed.permission.patterns, captured.permission.patterns);
      expect(
        gateway.isNativePermissionCurrent(
          'a1',
          'permission-one',
          captured.revision,
        ),
        false,
      );
      expect(
        gateway.isNativePermissionCurrent(
          'a1',
          'permission-one',
          changed.revision,
        ),
        true,
      );
    },
  );

  test(
    'changed hidden input invalidates an otherwise identical display',
    () async {
      await push({
        ...request(),
        'input': {'command': 'pwd'},
      });
      final captured = gateway.nativePermissionSnapshot('a1')!;
      await push({
        ...request(),
        'input': {'command': 'git status'},
      });
      expect(gateway.nativePermissionSnapshot('a1')!.permission.patterns, [
        'pwd',
      ]);
      expect(
        gateway.isNativePermissionCurrent(
          'a1',
          'permission-one',
          captured.revision,
        ),
        false,
      );
    },
  );

  test(
    'malformed replacement retires the prior actionable permission',
    () async {
      await push(request());
      final captured = gateway.nativePermissionSnapshot('a1')!;
      await push({...request(), 'suggestions': 'malformed'});
      expect(gateway.nativePermissionSnapshot('a1'), isNull);
      expect(
        gateway.isNativePermissionCurrent(
          'a1',
          'permission-one',
          captured.revision,
        ),
        false,
      );
      await expectLater(
        gateway.respondPermission('permission-one', 'once'),
        throwsA(isA<PaseoFailure>()),
      );
      expect(daemon.of('agent_permission_response'), isEmpty);
    },
  );

  test('resolve and disconnect retire revisions without resending', () async {
    var changes = 0;
    final listener = gateway.nativePermissionChanges.listen((_) => changes++);
    addTearDown(listener.cancel);
    await push(request());
    final captured = gateway.nativePermissionSnapshot('a1')!;
    daemon.push('agent_permission_resolved', {
      'agentId': 'a1',
      'requestId': 'permission-one',
    });
    await settle();
    expect(gateway.nativePermissionSnapshot('a1'), isNull);
    expect(
      gateway.isNativePermissionCurrent(
        'a1',
        'permission-one',
        captured.revision,
      ),
      false,
    );
    expect(changes, 2);
    await push(request());
    final next = gateway.nativePermissionSnapshot('a1')!;
    await daemon.close();
    await settle();
    expect(gateway.nativePermissionSnapshot('a1'), isNull);
    expect(
      gateway.isNativePermissionCurrent('a1', 'permission-one', next.revision),
      false,
    );
    expect(daemon.of('agent_permission_response'), isEmpty);
  });

  test(
    'location change and session deletion notify and retire permissions',
    () async {
      var changes = 0;
      final listener = gateway.nativePermissionChanges.listen((_) => changes++);
      addTearDown(listener.cancel);
      await push(request());
      final captured = gateway.nativePermissionSnapshot('a1')!;
      daemon.push('agent_deleted', {'agentId': 'a1'});
      await settle();
      expect(
        gateway.isNativePermissionCurrent(
          'a1',
          'permission-one',
          captured.revision,
        ),
        false,
      );
      expect(changes, 2);
      gateway.setLocation(directory: '/work/other');
      await settle();
      expect(changes, 3);
      expect(gateway.nativePermissionSnapshot('a1'), isNull);
    },
  );

  test(
    'feed permission attention updates without a refresh or timeline subscription',
    () async {
      final source = PaseoChatFeedSource(gateway);
      addTearDown(source.dispose);
      await source.refreshChatFeed();
      expect(source.chatFeed().items.single.status, ChatStatus.idle);
      var changes = 0;
      final listener = source.changes.listen((_) => changes++);
      addTearDown(listener.cancel);
      final reads = daemon.of('fetch_agents_request').length;
      await push(request());
      expect(source.chatFeed().items.single.status, ChatStatus.needsYou);
      expect(changes, 1);
      await gateway.respondPermission(
        'permission-one',
        'reject',
        legacySessionID: 'a1',
      );
      await settle();
      expect(source.chatFeed().items.single.status, ChatStatus.idle);
      expect(changes, 2);
      expect(daemon.of('fetch_agents_request').length, reads);
      expect(daemon.of('resume_agent_request'), isEmpty);
      expect(daemon.of('fetch_agent_timeline_request'), isEmpty);
    },
  );
}
