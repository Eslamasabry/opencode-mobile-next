import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/chat_feed_source.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _directory = '/root/projects/IPTV_King';
Map<String, dynamic> _agent(
  String id,
  String provider, {
  String status = 'idle',
  String cwd = _directory,
}) => {
  'id': id,
  'provider': provider,
  'cwd': cwd,
  'model': null,
  'title': 'Conversation $id',
  'status': status,
  'createdAt': '2026-10-02T08:00:00Z',
  'updatedAt': '2026-10-03T08:00:00Z',
  'pendingPermissions': <Object>[],
  'labels': <String, String>{},
  'archivedAt': null,
};
Map<String, dynamic> _page(
  List<Map<String, dynamic>> agents, {
  String? next,
}) => {
  'entries': [
    for (final agent in agents) {'agent': agent},
  ],
  'pageInfo': {'hasMore': next != null, 'nextCursor': next, 'prevCursor': null},
};

void main() {
  late FakePaseoSocket socket;
  late PaseoGateway gateway;
  late PaseoChatFeedSource source;
  late List<Map<String, dynamic>> agents;
  setUp(() {
    socket = FakePaseoSocket();
    agents = [
      _agent('one', 'codex'),
      _agent('two', 'claude', status: 'running'),
    ];
    socket.handlers['fetch_agents_request'] = (_) =>
        ('fetch_agents_response', _page(agents));
    gateway = PaseoGateway(
      directory: _directory,
      defaultProviderModes: const {'claude': 'default'},
      transport: PaseoTransport(
        endpoint: 'ws://100.64.0.20:6767',
        socketFactory: (_, _) async => socket,
      ),
    );
    source = PaseoChatFeedSource(gateway);
  });
  tearDown(() async {
    await source.dispose();
    gateway.close();
    await socket.close();
  });

  test(
    'scoped feed preserves provider identity without a selected model',
    () async {
      expect(source.chatFeed().complete, isFalse);
      await source.refreshChatFeed();
      final snapshot = source.chatFeed();
      expect(snapshot.complete, isTrue);
      expect(snapshot.acrossProjects, isFalse);
      expect(snapshot.items.map((item) => item.agentId).toSet(), {
        'codex',
        'claude',
      });
      expect(snapshot.items.map((item) => item.agentLabel).toSet(), {
        'Codex',
        'Claude Code',
      });
      expect(snapshot.items.every((item) => item.preview.isEmpty), isTrue);
      expect(() => snapshot.items.clear(), throwsUnsupportedError);
      expect(source.projectSummaries.single.chatCount, 2);
      expect(socket.of('fetch_agent_timeline_request'), isEmpty);
    },
  );

  test(
    'agent filtering and clearing retain the original filter contract',
    () async {
      await source.refreshChatFeed();
      const filter = ChatFeedFilter(
        agentId: 'codex',
        projectDirectory: _directory,
      );
      expect(source.chatFeed(filter).items.single.sessionID, 'one');
      expect(
        filter.copyWith(clearAgent: true),
        const ChatFeedFilter(projectDirectory: _directory),
      );
      expect(
        source.chatFeed(const ChatFeedFilter(projectDirectory: '/other')).items,
        isEmpty,
      );
    },
  );

  test(
    'bounded pagination reports partial rows without inventing global inventory',
    () async {
      await source.dispose();
      source = PaseoChatFeedSource(gateway, maxPages: 1);
      socket.handlers['fetch_agents_request'] = (_) =>
          ('fetch_agents_response', _page(agents, next: 'older'));
      await source.refreshChatFeed();
      expect(source.chatFeed().items, hasLength(2));
      expect(source.chatFeed().complete, isFalse);
      expect(source.chatFeedAcrossProjects, isFalse);
      expect(socket.of('fetch_agents_request'), hasLength(1));
    },
  );

  test('repeated cursors stop the bounded read', () async {
    socket.handlers['fetch_agents_request'] = (_) =>
        ('fetch_agents_response', _page(agents, next: 'same'));
    await source.refreshChatFeed();
    expect(socket.of('fetch_agents_request'), hasLength(2));
    expect(source.chatFeed().complete, isFalse);
  });

  test(
    'a failed refresh retains rows and exposes only partial truth',
    () async {
      await source.refreshChatFeed();
      socket.handlers['fetch_agents_request'] = (_) =>
          ('fetch_agents_response', {'entries': 'invalid synthetic error'});
      await source.refreshChatFeed();
      expect(source.chatFeed().items, hasLength(2));
      expect(source.chatFeed().complete, isFalse);
    },
  );

  test('coalesced refresh and disposal fence a late response', () async {
    final request = Completer<Map<String, dynamic>>();
    socket.handlers['fetch_agents_request'] = (value) {
      request.complete(value);
      return null;
    };
    final first = source.refreshChatFeed();
    final second = source.refreshChatFeed();
    expect(identical(first, second), isTrue);
    final pending = await request.future;
    expect(source.chatFeed().loading, isTrue);
    await source.dispose();
    socket.reply(pending, 'fetch_agents_response', _page(agents));
    await first;
    expect(source.chatFeed().items, isEmpty);
    expect(source.chatFeed().complete, isFalse);
  });

  test(
    'changing gateway scope hides held rows and prevents retargeting',
    () async {
      await source.refreshChatFeed();
      gateway.setLocation(directory: '/root/projects/other');
      expect(source.chatFeed().items, isEmpty);
      await expectLater(
        source.startChatIn('/root/projects/other'),
        throwsA(isA<ProductException>()),
      );
      expect(socket.of('create_agent_request'), isEmpty);
    },
  );

  test(
    'timeout keeps bounded partial state and rejects late completion',
    () async {
      await source.dispose();
      source = PaseoChatFeedSource(
        gateway,
        refreshTimeout: const Duration(milliseconds: 15),
      );
      final request = Completer<Map<String, dynamic>>();
      socket.handlers['fetch_agents_request'] = (value) {
        request.complete(value);
        return null;
      };
      final refresh = source.refreshChatFeed();
      final pending = await request.future;
      await refresh;
      socket.reply(pending, 'fetch_agents_response', _page(agents));
      await Future<void>.delayed(Duration.zero);
      expect(source.chatFeed().items, isEmpty);
      expect(source.chatFeed().complete, isFalse);
    },
  );

  test(
    'unknown agents and other projects are refused before a draft is made',
    () async {
      socket.handlers['get_providers_snapshot_request'] = (request) => (
        'get_providers_snapshot_response',
        {
          'cwd': request['cwd'],
          'entries': [
            {
              'provider': 'gemini',
              'status': 'ready',
              'enabled': true,
              'source': 'builtin',
              'models': [],
              'modes': [],
            },
          ],
          'generatedAt': '2026-10-03T08:00:00Z',
        },
      );
      await expectLater(
        source.startAgentChatIn(_directory, agentId: 'gemini'),
        throwsA(isA<ProductException>()),
      );
      await expectLater(
        source.startChatIn('/etc'),
        throwsA(isA<ProductException>()),
      );
      expect(socket.of('create_agent_request'), isEmpty);
      expect(
        await gateway.sessions(),
        isNotEmpty,
      ); // Only the original daemon rows.
      expect(
        (await gateway.sessions()).every(
          (session) => !session.title!.startsWith('New'),
        ),
        isTrue,
      );
    },
  );

  test(
    'pending permission outranks a running row without exposing details',
    () async {
      agents.first['pendingPermissions'] = [
        {
          'id': 'approval',
          'provider': 'codex',
          'name': 'Write',
          'kind': 'tool',
          'title': 'Write file',
          'detail': {'type': 'write', 'content': 'synthetic secret'},
          'actions': [
            {'id': 'allow', 'label': 'Allow', 'behavior': 'allow'},
            {'id': 'deny', 'label': 'Deny', 'behavior': 'deny'},
          ],
        },
      ];
      await source.refreshChatFeed();
      expect(source.chatFeed().items.first.sessionID, 'one');
      expect(source.chatFeed().items.first.status, ChatStatus.needsYou);
      expect(source.projectSummaries.single.needsYouCount, 1);
      expect(source.chatFeed().items.first.preview, isEmpty);
    },
  );

  test(
    'selected runtime survives an empty initial draft until first prompt',
    () async {
      socket.handlers['get_providers_snapshot_request'] = (request) => (
        'get_providers_snapshot_response',
        {
          'cwd': request['cwd'],
          'entries': [
            {
              'provider': 'codex',
              'status': 'ready',
              'enabled': true,
              'source': 'builtin',
              'models': [],
              'modes': [],
            },
          ],
          'generatedAt': '2026-10-03T08:00:00Z',
        },
      );
      socket.handlers['create_agent_request'] = (request) {
        expect((request['config'] as Map)['provider'], 'codex');
        final created = _agent('new-real-id', 'codex');
        agents.add(created);
        return ('create_agent_response', {'agent': created});
      };
      final id = await source.startAgentChatIn(_directory, agentId: 'codex');
      expect(socket.of('create_agent_request'), isEmpty);
      expect(
        source
            .chatFeed()
            .items
            .singleWhere((item) => item.sessionID == id)
            .agentId,
        'codex',
      );
      await gateway.promptAsync(id, text: 'hello');
      expect(socket.of('create_agent_request'), hasLength(1));
    },
  );

  for (final supportsAsk in [true, false]) {
    test(
      'phone Claude defaults to asking and refuses missing ask mode: $supportsAsk',
      () async {
        socket.handlers['get_providers_snapshot_request'] = (request) => (
          'get_providers_snapshot_response',
          {
            'cwd': request['cwd'],
            'entries': [
              {
                'provider': 'claude',
                'status': 'ready',
                'enabled': true,
                'source': 'builtin',
                'models': [],
                'modes': supportsAsk
                    ? [
                        {'id': 'default', 'label': 'Always ask'},
                      ]
                    : [],
              },
            ],
            'generatedAt': '2026-10-03T08:00:00Z',
          },
        );
        socket.handlers['create_agent_request'] = (request) {
          expect((request['config'] as Map)['modeId'], 'default');
          final created = _agent('phone-chat', 'claude');
          agents.add(created);
          return ('create_agent_response', {'agent': created});
        };
        final starting = source.startAgentChatIn(
          _directory,
          agentId: 'claude',
          firstPrompt: 'hello',
        );
        if (supportsAsk) {
          await starting;
          expect(socket.of('create_agent_request'), hasLength(1));
        } else {
          await expectLater(starting, throwsA(isA<ProductException>()));
          expect(socket.of('create_agent_request'), isEmpty);
        }
      },
    );
  }

  test('remember callback is optional and scope-fenced', () async {
    final saved = <String>[];
    await source.dispose();
    source = PaseoChatFeedSource(
      gateway,
      persistLastUsedProject: (directory) async => saved.add(directory),
    );
    await source.rememberLastUsedProject('/tmp/transient');
    expect(saved, isEmpty);
    await source.rememberLastUsedProject(_directory);
    expect(saved, [_directory]);
    expect(source.lastUsedProjectDirectory, _directory);
    gateway.setLocation(directory: '/root/projects/other');
    expect(source.lastUsedProjectDirectory, isNull);
  });
}
