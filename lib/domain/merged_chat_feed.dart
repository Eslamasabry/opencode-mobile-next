import 'dart:async';

import 'chat_feed.dart';
import 'server_gateway.dart' show ProductException;

/// Sources belong to one explicit owner context. Names are app-authored copy;
/// IDs are stable routing keys and must not be reused for a different backend.
class NamedChatFeedSource {
  const NamedChatFeedSource({
    required this.id,
    required this.label,
    required this.source,
  });
  final String id, label;
  final ChatFeedSource source;
}

class ChatFeedRoute {
  const ChatFeedRoute({
    required this.sourceId,
    required this.sessionID,
    required this.directory,
    this.agentId,
  });
  final String sourceId, sessionID, directory;
  final String? agentId;
}

/// A read-only merged projection. Sources retain their transport and storage
/// ownership. Refreshes coalesce, failures stay partial per source, and dispose
/// fences late results without disposing shared child sources.
class MergedChatFeed implements ChatFeedSource, ChatFeedChangeSource {
  MergedChatFeed({
    required List<NamedChatFeedSource> sources,
    this.defaultSourceId,
    this.refreshTimeout = const Duration(seconds: 45),
  }) : sources = List.unmodifiable(sources) {
    final ids = <String>{};
    for (final source in this.sources) {
      if (source.id.isEmpty || !ids.add(source.id)) {
        throw ArgumentError('Feed source IDs must be unique.');
      }
    }
    if (defaultSourceId != null && !ids.contains(defaultSourceId)) {
      throw ArgumentError('The default feed source must be named.');
    }
    if (refreshTimeout <= Duration.zero) {
      throw ArgumentError('Refresh timeout must be positive.');
    }
    for (final source in this.sources) {
      final child = source.source;
      if (child is ChatFeedChangeSource) {
        _subscriptions.add(
          (child as ChatFeedChangeSource).changes.listen(
            (_) => _notify(),
            onError: (Object _) {
              if (!_disposed) {
                _failed.add(source.id);
                _notify();
              }
            },
          ),
        );
      }
    }
  }

  final List<NamedChatFeedSource> sources;
  final String? defaultSourceId;
  final Duration refreshTimeout;
  final _changes = StreamController<void>.broadcast();
  final _subscriptions = <StreamSubscription<void>>[];
  final _failed = <String>{};
  bool _disposed = false;
  Future<void>? _refreshing;
  String? _lastSourceId;

  @override
  Stream<void> get changes => _changes.stream;

  void _notify() {
    if (!_disposed) _changes.add(null);
  }

  Map<String, ChatFeedSnapshot> get sourceChecks => Map.unmodifiable({
    for (final named in sources) named.id: _snapshot(named),
  });

  ChatFeedSnapshot _snapshot(NamedChatFeedSource named) {
    if (_disposed) {
      return const ChatFeedSnapshot(
        items: [],
        complete: false,
        acrossProjects: false,
      );
    }
    try {
      final snapshot = named.source.chatFeed(
        const ChatFeedFilter(includeSubagents: true),
      );
      return ChatFeedSnapshot(
        items: List.unmodifiable(snapshot.items),
        acrossProjects: snapshot.acrossProjects,
        loading: snapshot.loading,
        complete: snapshot.complete && !_failed.contains(named.id),
      );
    } catch (_) {
      return const ChatFeedSnapshot(
        items: [],
        complete: false,
        acrossProjects: false,
      );
    }
  }

  @override
  bool get chatFeedAcrossProjects =>
      !_disposed &&
      sources.isNotEmpty &&
      sourceChecks.values.every((check) => check.acrossProjects);

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    final checks = sourceChecks;
    final rows = <ChatFeedItem>[];
    for (final named in sources) {
      for (final item in checks[named.id]!.items) {
        final row = ChatFeedItem(
          sessionID: item.sessionID,
          title: item.title,
          directory: item.directory,
          projectName: item.projectName,
          isGit: item.isGit,
          status: item.status,
          lastActivity: item.lastActivity,
          preview: item.preview,
          parentID: item.parentID,
          agentId: item.agentId,
          agentLabel: item.agentLabel,
          sourceId: named.id,
          sourceLabel: named.label,
        );
        if (chatFeedMatches(row, filter)) rows.add(row);
      }
    }
    rows.sort(compareChatFeedItems);
    return ChatFeedSnapshot(
      items: List.unmodifiable(rows),
      acrossProjects: chatFeedAcrossProjects,
      complete:
          !_disposed &&
          checks.isNotEmpty &&
          checks.values.every((check) => check.complete),
      loading:
          rows.isEmpty &&
          !_disposed &&
          (_refreshing != null || checks.values.any((check) => check.loading)),
    );
  }

  @override
  List<ProjectSummary> get projectSummaries {
    if (_disposed) return const [];
    final projects = <ProjectSummary>[];
    for (final named in sources) {
      try {
        for (final project in named.source.projectSummaries) {
          projects.add(
            ProjectSummary(
              directory: project.directory,
              name: project.name,
              isGit: project.isGit,
              chatCount: project.chatCount,
              runningCount: project.runningCount,
              needsYouCount: project.needsYouCount,
              lastActivity: project.lastActivity,
              kind: project.kind,
              sourceId: named.id,
              sourceLabel: named.label,
            ),
          );
        }
      } catch (_) {
        /* One unavailable source never removes another's projects. */
      }
    }
    projects.sort((a, b) {
      final recent = (b.lastActivity ?? DateTime.fromMillisecondsSinceEpoch(0))
          .compareTo(a.lastActivity ?? DateTime.fromMillisecondsSinceEpoch(0));
      return recent != 0
          ? recent
          : '${a.sourceId}:${a.directory}'.compareTo(
              '${b.sourceId}:${b.directory}',
            );
    });
    return List.unmodifiable(projects);
  }

  @override
  Future<void> refreshChatFeed() {
    if (_disposed) return Future.value();
    return _refreshing ??= _refreshNow().whenComplete(() {
      _refreshing = null;
      _notify();
    });
  }

  Future<void> _refreshNow() async {
    _notify();
    await Future.wait(
      sources.map((named) async {
        try {
          await named.source.refreshChatFeed().timeout(refreshTimeout);
          if (!_disposed) _failed.remove(named.id);
        } catch (_) {
          if (!_disposed) _failed.add(named.id);
        }
      }),
    );
  }

  NamedChatFeedSource _named(String id) {
    if (_disposed) {
      throw const ProductException(
        'Refresh the conversation before opening it.',
      );
    }
    for (final named in sources) {
      if (named.id == id) return named;
    }
    throw const ProductException('Choose an available chat source.');
  }

  NamedChatFeedSource get _default {
    if (defaultSourceId != null) return _named(defaultSourceId!);
    if (sources.length == 1) return _named(sources.single.id);
    throw const ProductException('Choose which agent should start this chat.');
  }

  ChatFeedRoute routeFor(ChatFeedItem item) {
    final route = ChatFeedRoute(
      sourceId: item.sourceId ?? '',
      sessionID: item.sessionID,
      directory: item.directory,
      agentId: item.agentId,
    );
    sourceFor(route);
    return route;
  }

  /// Validate the original named source and held row before navigation. Never
  /// search other sources for a colliding ID or substitute another session.
  ChatFeedSource sourceFor(ChatFeedRoute route) {
    final named = _named(route.sourceId);
    final matches = _snapshot(named).items.where(
      (item) =>
          item.sessionID == route.sessionID &&
          item.directory == route.directory &&
          item.agentId == route.agentId,
    );
    if (matches.length != 1) {
      throw const ProductException(
        'Refresh the conversation before opening it.',
      );
    }
    return named.source;
  }

  Future<String> startChatInSource(
    String sourceId,
    String directory, {
    String? firstPrompt,
    String? agentId,
  }) async {
    final named = _named(sourceId);
    final source = named.source;
    final String id;
    try {
      if (agentId == null) {
        id = await source.startChatIn(directory, firstPrompt: firstPrompt);
      } else if (source is AgentChatFeedSource) {
        id = await source.startAgentChatIn(
          directory,
          agentId: agentId,
          firstPrompt: firstPrompt,
        );
      } else {
        throw const ProductException(
          'This chat source cannot select that agent.',
        );
      }
    } on ProductException {
      rethrow;
    } catch (_) {
      throw const ProductException(
        'Could not start this chat. Refresh before trying again.',
      );
    }
    _named(sourceId); // A late completion after disposal is never actionable.
    _lastSourceId = sourceId;
    _notify();
    return id;
  }

  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) async =>
      startChatInSource(_default.id, directory, firstPrompt: firstPrompt);

  @override
  String? get lastUsedProjectDirectory {
    if (_disposed || sources.isEmpty) return null;
    if (_lastSourceId != null) {
      return _named(_lastSourceId!).source.lastUsedProjectDirectory;
    }
    if (defaultSourceId != null || sources.length == 1) {
      return _default.source.lastUsedProjectDirectory;
    }
    return null;
  }

  @override
  Future<void> rememberLastUsedProject(String directory) async {
    final named = _lastSourceId == null ? _default : _named(_lastSourceId!);
    await rememberLastUsedProjectInSource(named.id, directory);
  }

  Future<void> rememberLastUsedProjectInSource(
    String sourceId,
    String directory,
  ) async {
    final named = _named(sourceId);
    await named.source.rememberLastUsedProject(directory);
    _named(sourceId);
    _lastSourceId = sourceId;
    _notify();
  }

  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    for (final subscription in _subscriptions) {
      await subscription.cancel();
    }
    await _changes.close();
  }
}
