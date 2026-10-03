import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/merged_chat_feed.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

ChatFeedItem _row(
  String id, {
  ChatStatus status = ChatStatus.idle,
  String agent = 'codex',
  int time = 1,
}) => ChatFeedItem(
  sessionID: id,
  title: 'Conversation',
  directory: '/work/project',
  projectName: 'Project',
  isGit: false,
  status: status,
  lastActivity: DateTime.fromMillisecondsSinceEpoch(time),
  agentId: agent,
  agentLabel: 'Agent',
);

class _Feed implements AgentChatFeedSource, ChatFeedChangeSource {
  ChatFeedSnapshot snapshot = const ChatFeedSnapshot(items: []);
  final controller = StreamController<void>.broadcast();
  final started = <(String, String?)>[];
  Future<void> Function()? refresh;
  int reads = 0;
  String? lastProject;
  @override
  Stream<void> get changes => controller.stream;
  @override
  bool get chatFeedAcrossProjects => snapshot.acrossProjects;
  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) =>
      ChatFeedSnapshot(
        items: snapshot.items
            .where((item) => chatFeedMatches(item, filter))
            .toList(),
        complete: snapshot.complete,
        loading: snapshot.loading,
        acrossProjects: snapshot.acrossProjects,
      );
  @override
  List<ProjectSummary> get projectSummaries => [
    ProjectSummary(
      directory: '/work/project',
      name: 'Project',
      isGit: false,
      chatCount: snapshot.items.length,
      runningCount: 0,
      needsYouCount: 0,
    ),
  ];
  @override
  Future<void> refreshChatFeed() async {
    reads++;
    await refresh?.call();
  }

  @override
  String? get lastUsedProjectDirectory => lastProject;
  @override
  Future<void> rememberLastUsedProject(String directory) async {
    lastProject = directory;
  }

  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) async {
    started.add((directory, null));
    return 'created';
  }

  @override
  Future<String> startAgentChatIn(
    String directory, {
    required String agentId,
    String? firstPrompt,
  }) async {
    started.add((directory, agentId));
    return 'created';
  }

  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);
}

void main() {
  late _Feed first, second;
  late MergedChatFeed merged;
  setUp(() {
    first = _Feed();
    second = _Feed();
    merged = MergedChatFeed(
      sources: [
        NamedChatFeedSource(id: 'first', label: 'Computer', source: first),
        NamedChatFeedSource(id: 'second', label: 'Phone', source: second),
      ],
    );
  });
  tearDown(() async {
    await merged.dispose();
    await first.controller.close();
    await second.controller.close();
  });

  test('attention then running then recent rows sort across named sources', () {
    first.snapshot = ChatFeedSnapshot(
      items: [
        _row('idle', time: 99),
        _row('running', status: ChatStatus.running, time: 2),
      ],
    );
    second.snapshot = ChatFeedSnapshot(
      items: [
        _row('permission', status: ChatStatus.needsYou),
        _row('failed', status: ChatStatus.failed, time: 100),
      ],
    );
    expect(merged.chatFeed().items.map((row) => row.sessionID), [
      'permission',
      'running',
      'failed',
      'idle',
    ]);
    expect(merged.chatFeed().items.first.sourceLabel, 'Phone');
    expect(() => merged.chatFeed().items.clear(), throwsUnsupportedError);
    expect(() => merged.sources.clear(), throwsUnsupportedError);
  });

  test(
    'equal session IDs remain distinct and route only to original source',
    () {
      first.snapshot = ChatFeedSnapshot(items: [_row('same')]);
      second.snapshot = ChatFeedSnapshot(items: [_row('same')]);
      final rows = merged.chatFeed().items;
      expect(rows.map((row) => row.identity).toSet(), hasLength(2));
      expect(merged.sourceFor(merged.routeFor(rows.first)), same(first));
      expect(merged.sourceFor(merged.routeFor(rows.last)), same(second));
      first.snapshot = const ChatFeedSnapshot(items: []);
      expect(
        () => merged.sourceFor(merged.routeFor(rows.first)),
        throwsA(isA<ProductException>()),
      );
      expect(second.snapshot.items, hasLength(1));
    },
  );

  test('agent identity changes invalidate stale navigation routes', () {
    first.snapshot = ChatFeedSnapshot(items: [_row('one')]);
    final route = merged.routeFor(merged.chatFeed().items.single);
    first.snapshot = ChatFeedSnapshot(items: [_row('one', agent: 'claude')]);
    expect(() => merged.sourceFor(route), throwsA(isA<ProductException>()));
  });

  test('agent and project filters preserve additive attention chips', () {
    first.snapshot = ChatFeedSnapshot(
      items: [
        _row('one', status: ChatStatus.needsYou),
        _row('two', agent: 'claude', status: ChatStatus.running),
      ],
    );
    second.snapshot = ChatFeedSnapshot(
      items: [
        _row('three', status: ChatStatus.running),
        _row('four'),
      ],
    );
    expect(
      merged
          .chatFeed(
            const ChatFeedFilter(
              agentId: 'codex',
              needsYou: true,
              running: true,
            ),
          )
          .items
          .map((row) => row.sessionID),
      ['one', 'three'],
    );
    expect(
      merged.chatFeed(const ChatFeedFilter(projectDirectory: '/missing')).items,
      isEmpty,
    );
  });

  test('partial and project scope flags survive per source and aggregate', () {
    first.snapshot = ChatFeedSnapshot(
      items: [_row('one')],
      acrossProjects: false,
    );
    second.snapshot = const ChatFeedSnapshot(
      items: [],
      complete: false,
      loading: true,
    );
    expect(merged.chatFeed().complete, isFalse);
    expect(merged.chatFeed().acrossProjects, isFalse);
    expect(merged.chatFeed().loading, isFalse); // Held rows remain usable.
    expect(merged.sourceChecks['first']!.complete, isTrue);
    expect(merged.sourceChecks['second']!.loading, isTrue);
    expect(() => merged.sourceChecks.clear(), throwsUnsupportedError);
    expect(merged.projectSummaries.map((project) => project.sourceId), [
      'first',
      'second',
    ]);
  });

  test(
    'coalesced refresh isolates a failed child and recovers on next read',
    () async {
      final pending = Completer<void>();
      first.refresh = () => pending.future;
      second.refresh = () async =>
          throw StateError('synthetic raw provider credential');
      final one = merged.refreshChatFeed();
      expect(identical(one, merged.refreshChatFeed()), isTrue);
      pending.complete();
      await one;
      expect(first.reads, 1);
      expect(second.reads, 1);
      expect(merged.sourceChecks['first']!.complete, isTrue);
      expect(merged.sourceChecks['second']!.complete, isFalse);
      second.refresh = null;
      await merged.refreshChatFeed();
      expect(merged.sourceChecks['second']!.complete, isTrue);
    },
  );

  test(
    'start requires an explicit source for multiple backends and preserves agent',
    () async {
      await expectLater(
        merged.startChatIn('/work/project'),
        throwsA(isA<ProductException>()),
      );
      expect(first.started, isEmpty);
      expect(second.started, isEmpty);
      expect(
        await merged.startChatInSource(
          'second',
          '/work/project',
          agentId: 'claude',
          firstPrompt: 'hello',
        ),
        'created',
      );
      expect(second.started, [('/work/project', 'claude')]);
      expect(first.started, isEmpty);
      await merged.rememberLastUsedProject('/work/project');
      expect(merged.lastUsedProjectDirectory, '/work/project');
      expect(first.lastProject, isNull);
    },
  );

  test('one source keeps original startChatIn behavior', () async {
    await merged.dispose();
    merged = MergedChatFeed(
      sources: [
        NamedChatFeedSource(id: 'first', label: 'Computer', source: first),
      ],
    );
    expect(await merged.startChatIn('/work/project'), 'created');
    expect(first.started, [('/work/project', null)]);
  });

  test(
    'disposed projection rejects late reads and never disposes shared child',
    () async {
      final pending = Completer<void>();
      first.refresh = () => pending.future;
      final refreshing = merged.refreshChatFeed();
      await merged.dispose();
      first.snapshot = ChatFeedSnapshot(items: [_row('late')]);
      pending.complete();
      await refreshing;
      expect(merged.chatFeed().items, isEmpty);
      expect(merged.chatFeed().complete, isFalse);
      expect(
        () => merged.routeFor(_row('late')),
        throwsA(isA<ProductException>()),
      );
      expect(first.controller.isClosed, isFalse);
    },
  );

  test('child notifications reach owner and stop after disposal', () async {
    var updates = 0;
    final subscription = merged.changes.listen((_) => updates++);
    first.controller.add(null);
    await Future<void>.delayed(Duration.zero);
    expect(updates, 1);
    await merged.dispose();
    second.controller.add(null);
    await Future<void>.delayed(Duration.zero);
    expect(updates, 1);
    await subscription.cancel();
  });

  test('duplicate names and missing defaults are refused', () {
    expect(
      () => MergedChatFeed(
        sources: [
          NamedChatFeedSource(id: 'same', label: 'A', source: first),
          NamedChatFeedSource(id: 'same', label: 'B', source: second),
        ],
      ),
      throwsArgumentError,
    );
    expect(
      () => MergedChatFeed(sources: [], defaultSourceId: 'unknown'),
      throwsArgumentError,
    );
  });
}
