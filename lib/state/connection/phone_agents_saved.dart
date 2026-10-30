part of '../connection.dart';

// The agents' saved rows: what the list shows before a folder is read live.
extension _PhoneAgentSavedRows on _ConnectionControllerPhoneAgents {
  /// This profile's saved rows, read from the cache once per owner.
  List<({ChatFeedItem item, bool canReopen})> get _paSavedRows {
    final id = _paProfile?.id;
    if (id == null || !phoneAgentsAvailable) return const [];
    if (_paCacheOwner != id) {
      _paCacheOwner = id;
      _paCacheRows = _paCache.read(id);
    }
    return _paCacheRows ?? const [];
  }

  /// The saved row for [item] while its folder has not been read live yet.
  ({ChatFeedItem item, bool canReopen})? _paSavedRowFor(ChatFeedItem item) {
    if (_paLoadedDirs.contains(item.directory)) return null;
    for (final row in _paSavedRows) {
      if (row.item.identity == item.identity) return row;
    }
    return null;
  }

  /// Folders read live replace their saved rows; the rest are kept.
  void _paSaveRows(MergedChatFeed merged) {
    final id = _paProfile?.id;
    if (id == null) return;
    final checks = merged.sourceChecks;
    for (final entry in _paSources.keys) {
      final check = checks[_paseoSourceId(entry)];
      if (check != null && (check.complete || check.items.isNotEmpty)) {
        _paLoadedDirs.add(entry);
      }
    }
    final live = merged
        .chatFeed(const ChatFeedFilter(includeSubagents: true))
        .items
        .where(
          (item) =>
              item.sourceId != _openCodeSourceId &&
              _paLoadedDirs.contains(item.directory),
        )
        .map(
          (item) => (
            item: _paPersistableRow(item),
            canReopen: agentResumeNotice(item).canReopen,
          ),
        )
        .toList();
    final rows = [
      ...live,
      for (final row in _paSavedRows)
        if (!_paLoadedDirs.contains(row.item.directory)) row,
    ]..sort((a, b) => compareChatFeedItems(a.item, b.item));
    _paCacheRows = List.unmodifiable(rows);
    _paCacheOwner = id;
    unawaited(_paCache.write(id, rows));
  }

  /// This server's own rows from its last full read, while its first read
  /// of this start is on the way and none of its live rows show yet: the
  /// list's first paint already holds them, instead of their arriving in
  /// the middle of the agents' saved rows half a second later (jitter J3,
  /// 2026-10-28). Each carries the source id its live row will have, so the
  /// live row takes its place without an entrance.
  List<ChatFeedItem> _paMainSavedRows(
    ChatFeedSnapshot snapshot,
    ChatFeedFilter filter,
  ) {
    final owner = _self._feedOwnerID;
    if (owner == null || (_self._feedLoaded && _self._feedCacheCurrent)) {
      return const [];
    }
    final liveSource = _paMerged == null ? null : _openCodeSourceId;
    if (snapshot.items.any((item) => item.sourceId == liveSource)) {
      return const [];
    }
    final memo = _paMainSaved[_self] ??= <String, List<ChatFeedItem>>{};
    final saved = memo[owner] ??= _paCache.readMain(owner);
    return [
      for (final item in saved)
        if (chatFeedMatches(item, filter)) _withSource(item, liveSource),
    ];
  }

  /// Keeps this server's rows after a full read, for the next start.
  void _paSaveMainRows() {
    final owner = _self._feedOwnerID;
    if (owner == null || !_self._feedLoaded || !_self._feedComplete) return;
    final rows = _self
        ._ocChatFeed(const ChatFeedFilter(includeSubagents: true))
        .items
        .map((item) => _withSource(item, _AgentFeedCache.mainSourceId))
        .toList();
    _paMainSaved[_self]?.remove(owner);
    unawaited(_paCache.writeMain(owner, rows));
  }

  /// Whether [row] says for sure whether its agent is signed in: until it
  /// does (not read yet, the helper starting, sign-in still being checked)
  /// the line saved last time stands in.
  bool _paSignInKnown(AgentRow row) => const {
    PhoneAgentStatus.signedOut,
    PhoneAgentStatus.limitReached,
    PhoneAgentStatus.ready,
    PhoneAgentStatus.needsInstall,
  }.contains(row.status);

  /// [agentStatusLines]: the live lines, and a signed-out line saved last
  /// time for every agent whose sign-in is not known yet this run, in the
  /// catalog's order. The list's first paint then already has the line the
  /// helper will confirm seconds later, instead of it pushing the rows down
  /// then (jitter J4, 2026-10-28).
  List<PhoneAgentStatusLine> _paStatusLines() {
    final live = _paLiveStatusLines();
    final id = _paProfile?.id;
    if (id == null || !phoneAgentsAvailable) return live;
    final saved = _paCache.readSignedOut(id);
    if (saved.isEmpty) return live;
    final known = {
      for (final row in _paRows)
        if (_paSignInKnown(row)) row.id,
      for (final line in live) line.agentId,
    };
    final standing = [
      for (final entry in saved.entries)
        if (!known.contains(entry.key))
          PhoneAgentStatusLine(
            agentId: entry.key,
            agentName: entry.value,
            kind: PhoneAgentStatusLineKind.signedOut,
          ),
    ];
    if (standing.isEmpty) return live;
    final order = [for (final agent in _paCatalog.agents) agent.id];
    int rank(PhoneAgentStatusLine line) {
      final at = order.indexOf(line.agentId);
      return at < 0 ? order.length : at;
    }

    return List.unmodifiable(
      [...live, ...standing]..sort((a, b) => rank(a).compareTo(rank(b))),
    );
  }

  /// Keeps the agents a read found signed out for the next start; agents
  /// whose sign-in this read could not tell keep what was saved.
  void _paSaveStatusLines() {
    final id = _paProfile?.id;
    if (id == null) return;
    final saved = _paCache.readSignedOut(id);
    final next = <String, String>{
      for (final entry in saved.entries)
        if (!_paRows.any((row) => row.id == entry.key && _paSignInKnown(row)))
          entry.key: entry.value,
      for (final row in _paRows)
        if (row.status == PhoneAgentStatus.signedOut) row.id: row.name,
    };
    unawaited(_paCache.writeSignedOut(id, next));
  }

  /// Saved rows outlive the gateway's draft aliases: persist the daemon's ids.
  ChatFeedItem _paPersistableRow(ChatFeedItem item) {
    final gateway = _paSources[item.directory]?.gateway;
    if (gateway == null || item.sourceId != _paseoSourceId(item.directory)) {
      return item;
    }
    final id = gateway.daemonSessionId(item.sessionID);
    final parent = item.parentID == null
        ? null
        : gateway.daemonSessionId(item.parentID!);
    if (id == item.sessionID && parent == item.parentID) return item;
    return ChatFeedItem(
      sessionID: id,
      title: item.title,
      directory: item.directory,
      projectName: item.projectName,
      isGit: item.isGit,
      status: item.status,
      lastActivity: item.lastActivity,
      preview: item.preview,
      parentID: parent,
      agentId: item.agentId,
      agentLabel: item.agentLabel,
      sourceId: item.sourceId,
      sourceLabel: item.sourceLabel,
      finishedUnseen: item.finishedUnseen,
      contextUsage: item.contextUsage,
    );
  }
}

/// Running on this phone: which agent conversation a Claude Code process on
/// this phone belongs to, by its runtime session (`claude --resume <id>`).
extension PhoneAgentRuntimeSessions on ConnectionController {
  /// The title of the agent conversation whose runtime session is
  /// [runtimeSession], when a folder holding it has been read live; null
  /// otherwise (the page then says "Claude Code" alone).
  String? agentChatTitleForRuntimeSession(String runtimeSession) {
    final merged = _paMerged;
    if (merged == null || runtimeSession.isEmpty) return null;
    final items = merged
        .chatFeed(const ChatFeedFilter(includeSubagents: true))
        .items;
    for (final item in items) {
      if (item.sourceId == _openCodeSourceId) continue;
      final key = _paSources[item.directory]?.gateway.sessionKey(
        item.sessionID,
      );
      if (key == null) continue;
      final id = key.substring(key.indexOf(':') + 1);
      final title = item.title.trim();
      if (id == runtimeSession && title.isNotEmpty) return title;
    }
    return null;
  }
}

/// The main server's saved rows read this run, by profile id.
final _paMainSaved = Expando<Map<String, List<ChatFeedItem>>>();

/// [item] under [sourceId].
ChatFeedItem _withSource(ChatFeedItem item, String? sourceId) => ChatFeedItem(
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
  sourceId: sourceId,
  sourceLabel: item.sourceLabel,
  finishedUnseen: item.finishedUnseen,
  contextUsage: item.contextUsage,
);
