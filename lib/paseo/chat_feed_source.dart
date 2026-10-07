import 'dart:async';

import '../api/models.dart';
import '../diagnostics/perf_trace.dart';
import '../domain/agent_catalog.dart';
import '../domain/chat_feed.dart';
import '../domain/server_gateway.dart' show ProductException;
import 'gateway.dart';

/// A feed for exactly one gateway scope. The owner refreshes after gateway
/// events and selects project scopes separately; this adapter never rescope
/// a shared gateway or claims a global host inventory. No timeline is read.
class PaseoChatFeedSource implements AgentChatFeedSource, ChatFeedChangeSource {
  PaseoChatFeedSource(
    this.gateway, {
    String? projectName,
    this.isGit = false,
    AgentCatalog? catalog,
    this.maxPages = 5,
    this.refreshTimeout = const Duration(seconds: 45),
    String? initialLastUsedProjectDirectory,
    this.persistLastUsedProject,
    this.hasWaitingCard,
    this.cardsIncomplete,
    this.refreshCards,
  }) : catalog = catalog ?? AgentCatalog.builtIn,
       _directory = gateway.directory,
       _projectName = projectName {
    if (maxPages < 1 || maxPages > 8 || refreshTimeout <= Duration.zero) {
      throw ArgumentError('Feed read bounds are invalid.');
    }
    if (initialLastUsedProjectDirectory == _directory &&
        !isTemporaryProjectDirectory(_directory)) {
      _lastUsed = initialLastUsedProjectDirectory;
    }
  }

  final bool Function(String sessionID)? hasWaitingCard;
  final bool Function()? cardsIncomplete;
  final Future<void> Function(List<ChatFeedItem> items)? refreshCards;
  final PaseoGateway gateway;
  final AgentCatalog catalog;
  final bool isGit;
  final int maxPages;
  final Duration refreshTimeout;
  final Future<void> Function(String directory)? persistLastUsedProject;
  final String? _directory, _projectName;
  final _changes = StreamController<void>.broadcast();
  List<ChatFeedItem> _items = const [];
  final _draftProviders = <String, String>{};
  bool _complete = false, _disposed = false;
  Future<void>? _refreshing;
  int _revision = 0;
  String? _lastUsed;

  bool get _valid =>
      !_disposed && !gateway.isClosed && gateway.directory == _directory;
  void _notify() {
    if (!_disposed) _changes.add(null);
  }

  String get _name =>
      _projectName ??
      (_directory?.split('/').where((part) => part.isNotEmpty).lastOrNull ??
          'Project');

  @override
  Stream<void> get changes => _changes.stream;
  @override
  bool get chatFeedAcrossProjects => false;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    final rows = _valid
        ? _items
              .map(_withCards)
              .where((item) => chatFeedMatches(item, filter))
              .toList()
        : <ChatFeedItem>[];
    rows.sort(compareChatFeedItems);
    return ChatFeedSnapshot(
      items: List.unmodifiable(rows),
      acrossProjects: false,
      loading: _valid && _refreshing != null && _items.isEmpty,
      complete: _valid && _complete && !(cardsIncomplete?.call() ?? false),
    );
  }

  @override
  List<ProjectSummary> get projectSummaries {
    if (!_valid || isTemporaryProjectDirectory(_directory)) return const [];
    final rows = _items
        .map(_withCards)
        .where((item) => !item.isSubagent)
        .toList();
    DateTime? latest;
    for (final item in rows) {
      if (latest == null || item.lastActivity.isAfter(latest)) {
        latest = item.lastActivity;
      }
    }
    return List.unmodifiable([
      ProjectSummary(
        directory: _directory!,
        name: _name,
        isGit: isGit,
        chatCount: rows.length,
        runningCount: rows
            .where((item) => item.status == ChatStatus.running)
            .length,
        needsYouCount: rows
            .where((item) => item.status == ChatStatus.needsYou)
            .length,
        lastActivity: latest,
      ),
    ]);
  }

  @override
  Future<void> refreshChatFeed() {
    if (!_valid) return Future.value();
    if (_refreshing != null) return _refreshing!;
    final revision = ++_revision;
    _refreshing = _read(revision)
        .timeout(refreshTimeout)
        .catchError((Object _) {
          if (_valid && _revision == revision) {
            _revision++;
            _complete = false;
          }
        })
        .whenComplete(() {
          _refreshing = null;
          _notify();
        });
    _notify();
    return _refreshing!;
  }

  bool _current(int revision) => _valid && revision == _revision;

  Future<void> _read(int revision) async {
    final sessions = <String, Session>{};
    String? cursor;
    var complete = false;
    final seen = <String>{};
    var receivedPage = false;
    try {
      try {
        for (var pageIndex = 0; pageIndex < maxPages; pageIndex++) {
          final page = await gateway.sessionPage(cursor: cursor, limit: 200);
          if (!_current(revision)) return;
          receivedPage = true;
          for (final session in page.items) {
            if (session.directory == _directory &&
                !isTemporaryProjectDirectory(session.directory)) {
              sessions[session.id] = session;
            }
          }
          if (!page.hasMore) {
            complete = true;
            break;
          }
          cursor = page.nextCursor;
          if (cursor == null || !seen.add(cursor)) break;
        }
      } catch (_) {
        if (!_current(revision)) return;
        if (!receivedPage) {
          _complete = false;
          return;
        }
      }
      // A resume leaves the old record of the same Claude session behind:
      // one row per session, the latest record, with the title it had.
      final bySession = <String, Session>{};
      for (final session in sessions.values.toList()) {
        final key = gateway.sessionKey(session.id);
        if (key == null) continue;
        final other = bySession[key];
        if (other == null) {
          bySession[key] = session;
          continue;
        }
        final newer = (session.time?.updated ?? 0) >= (other.time?.updated ?? 0)
            ? session
            : other;
        final older = identical(newer, session) ? other : session;
        sessions.remove(older.id);
        final titled = gateway.hasOwnTitle(newer.id)
            ? newer
            : newer.copyWith(title: older.title);
        // The helper gets the title back too, so the conversation's own
        // header says the same as this row.
        if (!gateway.hasOwnTitle(newer.id) && gateway.hasOwnTitle(older.id)) {
          gateway.keepTitle(newer.id, older.title ?? '');
        }
        sessions[newer.id] = titled;
        bySession[key] = titled;
      }
      final statuses = await gateway.sessionStatuses();
      final permissions = await gateway.pendingPermissions();
      if (!_current(revision)) return;
      final needsYou = permissions
          .map((permission) => permission.sessionID)
          .toSet();
      _items = List.unmodifiable(
        sessions.values.map((session) {
          final provider =
              gateway.providerIdForSession(session.id) ??
              _draftProviders[session.id];
          final status = needsYou.contains(session.id)
              ? ChatStatus.needsYou
              : switch (statuses[session.id]) {
                  'busy' || 'retry' => ChatStatus.running,
                  'error' => ChatStatus.failed,
                  _ => ChatStatus.idle,
                };
          return ChatFeedItem(
            sessionID: session.id,
            title: session.title?.isNotEmpty == true
                ? session.title!
                : 'New chat',
            directory: _directory!,
            projectName: _name,
            isGit: isGit,
            status: status,
            lastActivity: DateTime.fromMillisecondsSinceEpoch(
              session.time?.updated ?? session.time?.created ?? 0,
            ),
            parentID: session.parentID,
            agentId: provider ?? 'paseo',
            agentLabel: provider == null ? null : _label(provider),
          );
        }),
      );
      _complete = complete;
      await refreshCards?.call(_items);
    } catch (_) {
      if (_current(revision)) _complete = false;
    }
  }

  ChatFeedItem _withCards(ChatFeedItem item) {
    if (hasWaitingCard?.call(item.sessionID) != true ||
        item.status == ChatStatus.needsYou) {
      return item;
    }
    return ChatFeedItem(
      sessionID: item.sessionID,
      title: item.title,
      directory: item.directory,
      projectName: item.projectName,
      isGit: item.isGit,
      status: ChatStatus.needsYou,
      lastActivity: item.lastActivity,
      preview: item.preview,
      parentID: item.parentID,
      agentId: item.agentId,
      agentLabel: item.agentLabel,
      sourceId: item.sourceId,
      sourceLabel: item.sourceLabel,
      finishedUnseen: item.finishedUnseen,
    );
  }

  String _label(String provider) {
    for (final agent in catalog.agents) {
      if (agent.providerId == provider) return agent.name;
    }
    return switch (provider) {
      'pi' => 'Pi',
      'copilot' => 'GitHub Copilot',
      _ => 'Other agent',
    };
  }

  void _checkDirectory(String directory) {
    if (!_valid ||
        directory != _directory ||
        isTemporaryProjectDirectory(directory) ||
        !directory.startsWith('/') ||
        RegExp(r'[\x00-\x1f\x7f]').hasMatch(directory) ||
        directory.split('/').any((part) => part == '..' || part == '.') ||
        (directory.startsWith('/root/') &&
            !directory.startsWith('/root/projects/')) ||
        directory == '/root' ||
        const [
          '/etc',
          '/proc',
          '/sys',
          '/dev',
          '/boot',
        ].any((root) => directory == root || directory.startsWith('$root/'))) {
      throw const ProductException(
        'Choose an available project before starting a chat.',
      );
    }
  }

  @override
  String? get lastUsedProjectDirectory => _valid ? _lastUsed : null;

  @override
  Future<void> rememberLastUsedProject(String directory) async {
    if (!_valid ||
        directory != _directory ||
        isTemporaryProjectDirectory(directory)) {
      return;
    }
    try {
      await persistLastUsedProject?.call(directory);
      if (!_valid) return;
      _lastUsed = directory;
      _notify();
    } catch (_) {
      throw const ProductException('Could not save the selected project.');
    }
  }

  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) =>
      _start(directory, firstPrompt: firstPrompt);

  @override
  Future<String> startAgentChatIn(
    String directory, {
    required String agentId,
    String? firstPrompt,
    String? modelId,
  }) => _start(
    directory,
    agentId: agentId,
    firstPrompt: firstPrompt,
    modelId: modelId,
  );

  /// The runtimes and models the host offers (global, not per folder).
  Future<ProvidersResponse> providers() => gateway.providers();

  Future<String> _start(
    String directory, {
    String? agentId,
    String? firstPrompt,
    String? modelId,
  }) async {
    _checkDirectory(directory);
    try {
      final providers = await PerfTrace.span(
        'agent.start.providers',
        gateway.providers,
      );
      _checkDirectory(directory);
      final provider = agentId ?? providers.defaultProviderID;
      if (provider == null ||
          !providers.providers.any((entry) => entry.id == provider)) {
        throw const ProductException(
          'Choose an available agent for this project.',
        );
      }
      final session = await PerfTrace.span(
        'agent.start.session',
        gateway.createSession,
      );
      _checkDirectory(directory);
      gateway.seedDraftProviderForSession(session.id, provider);
      _draftProviders[session.id] = provider;
      if (firstPrompt != null && firstPrompt.trim().isNotEmpty) {
        await PerfTrace.span(
          'agent.start.prompt',
          () => gateway.promptAsync(
            session.id,
            text: firstPrompt,
            model: ModelRef(
              providerID: provider,
              modelID: modelId ?? paseoDefaultModel,
            ),
          ),
        );
        _checkDirectory(directory);
      }
      await rememberLastUsedProject(directory);
      await PerfTrace.span('agent.start.feed', refreshChatFeed);
      _checkDirectory(directory);
      // The chat opens on a fresh gateway, which knows the agent only by the
      // daemon's id; a draft's app id is local to this gateway.
      return gateway.daemonSessionId(session.id);
    } on ProductException {
      rethrow;
    } catch (error) {
      // The cause rides along for Details and the problem report.
      throw ProductException(
        'Could not start this chat. Refresh before trying again.',
        cause: error,
      );
    }
  }

  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    _revision++;
    _items = const [];
    _draftProviders.clear();
    await _changes.close();
  }
}
