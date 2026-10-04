part of '../connection.dart';

// Agents on this phone: one host and one auth owner inside the current profile
// (see domain/phone_agents_source.dart). The OpenCode feed (chat_feed.dart) and
// the phone Paseo feeds are merged here behind the one ChatFeedSource.

/// Source id of the OpenCode feed in the merged feed.
const _openCodeSourceId = 'opencode';

/// Source id of the Paseo feed scoped to [directory].
String _paseoSourceId(String directory) => 'paseo:$directory';

/// The OpenCode half of the merged feed: the controller's own live state.
final class _OpenCodeFeed implements ChatFeedSource {
  _OpenCodeFeed(this._c);
  final ConnectionController _c;

  @override
  bool get chatFeedAcrossProjects => _c._ocAcross;
  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) =>
      _c._ocChatFeed(filter);
  @override
  List<ProjectSummary> get projectSummaries => _c._ocProjectSummaries;
  @override
  Future<void> refreshChatFeed() => _c._ocRefresh();
  @override
  String? get lastUsedProjectDirectory => _c._ocLastUsed;
  @override
  Future<void> rememberLastUsedProject(String directory) =>
      _c._ocRemember(directory);
  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) =>
      _c._ocStartChatIn(directory, firstPrompt: firstPrompt);
  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);
}

/// [ConnectionController]'s [PhoneAgentsSource] and [AgentChatFeedSource].
mixin _ConnectionControllerPhoneAgents on ChangeNotifier
    implements PhoneAgentsSource, AgentChatFeedSource {
  ConnectionController get _self;

  static const _notReady = ProductException(
    'Other agents are not ready on this phone yet.',
  );
  static const _gone = ProductException(
    'Refresh the conversation before opening it.',
  );
  static const _maxPaseoSources = 4;

  PhoneAgentHostPort? _paHost;
  String? _paHostProfile;
  StreamSubscription<AgentSetupProgress>? _paSetupSub;
  final _paSignIns = <String, AgentSignInSession>{};
  final _paSignInSubs = <String, StreamSubscription<AgentSignInState>>{};
  List<AgentRow> _paRows = const [];
  bool _paHostRunning = false;
  final _paChecks = <String, AgentPhoneCheckResult>{};
  final _paLive = <String>{};
  bool _paNeedRestart = false;
  Future<void>? _paRefreshingRows;
  final _paSources =
      <String, ({PaseoGateway gateway, PaseoChatFeedSource source})>{};
  MergedChatFeed? _paMerged;
  StreamSubscription<void>? _paMergedSub;

  /// The Paseo gateway that currently owns the connection, or null while the
  /// connection is OpenCode's. Set only by [_paActivateRoute].
  ({String profileId, String directory})? _phoneAgentRoute;

  AgentCatalog get _paCatalog => AgentCatalog.builtIn;

  ServerProfile? get _paProfile => _self._connectedProfile ?? _self.profile;

  @override
  bool get phoneAgentsAvailable {
    final profile = _paProfile;
    return !_paNeedRestart &&
        !_self.isIsolated &&
        profile != null &&
        BuiltinLinux.managesServerUrl(profile.baseUrl) &&
        (_self._phoneAgentHostFactory != null || BuiltinLinux.supported);
  }

  @override
  bool get phoneAgentsNeedRestart => _paNeedRestart;

  // ---- host and auth owners ------------------------------------------------

  PhoneAgentHostPort _paEnsureHost() {
    final profile = _paProfile;
    if (!phoneAgentsAvailable || profile == null) throw _notReady;
    final existing = _paHost;
    if (existing != null && _paHostProfile == profile.id) return existing;
    if (existing != null) unawaited(_paCloseAll(stopHost: false));
    final factory = _self._phoneAgentHostFactory;
    final host = factory != null
        ? factory(profile)
        : BuiltinPhoneAgentHostPort(
            BuiltinPhoneAgents(profileId: profile.id, prefs: _self.store.prefs),
          );
    _paHost = host;
    _paHostProfile = profile.id;
    _paSetupSub = host.setupChanges.listen((progress) {
      if (_self._disposed) return;
      _self._notifyListeners();
      if (progress.phase == AgentSetupPhase.done) {
        unawaited(refreshAgentRows());
      }
    });
    return host;
  }

  AgentSignInSession _paSession(String agentId) {
    final descriptor = _paCatalog.byId(agentId);
    final profile = _paProfile;
    if (descriptor == null || profile == null) throw _notReady;
    final existing = _paSignIns[agentId];
    if (existing != null) return existing;
    _paEnsureHost();
    final session = AgentSignInSession(
      host: (_self._agentSignInHostFactory ?? ChannelAgentSignInHost.new)(),
      profileId: profile.id,
      agentId: agentId,
      method: descriptor.signInMethod,
    );
    _paSignIns[agentId] = session;
    _paListen(agentId, session);
    return session;
  }

  void _paListen(String agentId, AgentSignInSession session) {
    _paSignInSubs[agentId] = session.changes.listen((state) {
      if (_self._disposed) return;
      _self._notifyListeners();
      if (state.phase == AgentSignInPhase.signedIn ||
          state.phase == AgentSignInPhase.limitReached) {
        unawaited(refreshAgentRows());
      }
    });
  }

  bool _paEligible(AgentDescriptor descriptor) =>
      descriptor.recipe != null &&
      (descriptor.route == AgentRoute.paseoNative ||
          descriptor.route == AgentRoute.acpPaseo);

  // ---- rows ---------------------------------------------------------------

  @override
  List<AgentRow> get agentRows => phoneAgentsAvailable ? _paRows : const [];

  AgentRow? _paRowFor(String agentId) {
    for (final row in _paRows) {
      if (row.id == agentId) return row;
    }
    final byProvider = _paCatalog.agents
        .where((agent) => agent.providerId == agentId)
        .firstOrNull;
    if (byProvider == null) return null;
    for (final row in _paRows) {
      if (row.id == byProvider.id) return row;
    }
    return null;
  }

  @override
  List<ChatAgentChoice> get chatAgentChoices {
    final selected = selectedChatAgentId;
    return List.unmodifiable([
      ChatAgentChoice(
        agentId: openCodeChatAgentId,
        name: 'OpenCode',
        iconKey: 'opencode',
        selected: selected == openCodeChatAgentId,
      ),
      for (final row in agentRows)
        if (row.chatVisible)
          ChatAgentChoice(
            agentId: row.id,
            name: row.name,
            iconKey: row.iconKey,
            selected: selected == row.id,
            row: row,
          ),
    ]);
  }

  String _paChatAgentKey(String profileID) => 'oc.chatAgent.$profileID';

  @override
  String get selectedChatAgentId {
    final id = _paProfile?.id;
    if (id == null) return openCodeChatAgentId;
    final saved = _self.store.prefs.getString(_paChatAgentKey(id));
    if (saved == null || saved == openCodeChatAgentId) {
      return openCodeChatAgentId;
    }
    final row = phoneAgentsAvailable ? _paRowFor(saved) : null;
    return row != null && row.chatSelectable ? saved : openCodeChatAgentId;
  }

  @override
  Future<void> selectChatAgent(String agentId) async {
    final id = _paProfile?.id;
    if (id == null) throw _notReady;
    if (agentId != openCodeChatAgentId) {
      final row = phoneAgentsAvailable ? _paRowFor(agentId) : null;
      if (row == null || !row.chatSelectable) throw _notReady;
    }
    try {
      await _self.store.prefs.setString(_paChatAgentKey(id), agentId);
    } catch (_) {}
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  Future<void> refreshAgentRows() =>
      _paRefreshingRows ??= _paRefreshRows().whenComplete(() {
        _paRefreshingRows = null;
      });

  Future<void> _paRefreshRows() async {
    if (!phoneAgentsAvailable) {
      if (_paRows.isNotEmpty) {
        _paRows = const [];
        if (!_self._disposed) _self._notifyListeners();
      }
      return;
    }
    final PhoneAgentHostPort host;
    try {
      host = _paEnsureHost();
    } catch (_) {
      return;
    }
    final owner = _paHostProfile;
    AgentArchitecture? arch;
    try {
      arch = await host.architecture();
    } catch (_) {
      arch = null;
    }
    final rows = <AgentRow>[];
    var running = false;
    for (final descriptor in _paCatalog.agents) {
      PhoneAgentRuntime? runtime;
      if (_paEligible(descriptor)) {
        try {
          runtime = await host.inspect(
            descriptor.id,
            signIn: _paSignIns[descriptor.id]?.state,
          );
          // Sign-in is only inspected for an installed, qualified agent: it
          // starts a native status process.
          if (descriptor.signInMethod == AgentSignInMethod.browserOAuthHost &&
              runtime.installed &&
              runtime.architectureQualified &&
              runtime.signInPhase == null) {
            final session = _paSession(descriptor.id);
            try {
              await session.inspectStatus();
            } catch (_) {}
            runtime = await host.inspect(descriptor.id, signIn: session.state);
          }
          running = running || runtime.hostAvailable;
        } catch (_) {
          runtime = null;
        }
      }
      rows.add(
        buildAgentRow(
          descriptor: descriptor,
          architecture: arch ?? AgentArchitecture.arm64,
          serverCapabilities: _self.capabilities,
          runtime: runtime,
        ),
      );
    }
    if (_self._disposed || _paHost != host || _paHostProfile != owner) return;
    _paRows = List.unmodifiable(rows);
    _paHostRunning = running;
    try {
      await _paSyncSources();
    } catch (_) {}
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  List<PhoneAgentStatusLine> get agentStatusLines => List.unmodifiable([
    for (final row in agentRows)
      if (row.status == PhoneAgentStatus.limitReached ||
          row.status == PhoneAgentStatus.signedOut ||
          row.status == PhoneAgentStatus.stoppedInBackground)
        PhoneAgentStatusLine(
          agentId: row.id,
          agentName: row.name,
          kind: switch (row.status) {
            PhoneAgentStatus.limitReached =>
              PhoneAgentStatusLineKind.limitReached,
            PhoneAgentStatus.signedOut => PhoneAgentStatusLineKind.signedOut,
            _ => PhoneAgentStatusLineKind.stopped,
          },
          resetAt: row.resetAt,
        ),
  ]);

  // ---- setup, phone check, host -------------------------------------------

  @override
  AgentSetupProgress get agentSetupProgress =>
      _paHost?.setupProgress ??
      const AgentSetupProgress(agentId: '', phase: AgentSetupPhase.idle);

  @override
  AgentPhoneCheckResult? agentPhoneCheck(String agentId) => _paChecks[agentId];

  @override
  Future<void> installAgent(String agentId) async {
    final host = _paEnsureHost();
    await host.install(agentId);
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  Future<void> cancelAgentInstall() async {
    final host = _paHost;
    if (host == null) return;
    await host.cancelInstall();
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  Future<AgentPhoneCheckResult> runAgentPhoneCheck(String agentId) async {
    final host = _paEnsureHost();
    final result = await host.selfTest(agentId);
    _paChecks[agentId] = result;
    await refreshAgentRows();
    return result;
  }

  @override
  Future<void> resumeAgentHost() async {
    final host = _paEnsureHost();
    try {
      await host.start();
    } finally {
      await refreshAgentRows();
      unawaited(refreshChatFeed());
    }
  }

  // ---- sign-in ------------------------------------------------------------

  @override
  AgentSignInState? agentSignInState(String agentId) =>
      _paSignIns[agentId]?.state;

  @override
  Uri? agentSignInUrl(String agentId) {
    final state = _paSignIns[agentId]?.state;
    if (state == null ||
        (state.phase != AgentSignInPhase.urlReady &&
            state.phase != AgentSignInPhase.awaitingCode)) {
      return null;
    }
    return state.authorizationUrl?.uri;
  }

  @override
  Future<void> startAgentSignIn(String agentId) async {
    final session = _paSession(agentId);
    if (!session.state.inspected) await session.inspectStatus();
    final phase = session.state.phase;
    if (phase == AgentSignInPhase.signedIn ||
        phase == AgentSignInPhase.limitReached) {
      return;
    }
    try {
      await session.start();
    } on AgentSignInException catch (error) {
      if (error.failure != AgentSignInFailure.staleRun) rethrow;
    }
    // The host prints the page first (urlReady) and only then asks for the
    // code, so the code prompt is read here and again when the code is sent.
    final after = session.state.phase;
    if (after == AgentSignInPhase.awaitingCode ||
        after == AgentSignInPhase.urlReady ||
        after == AgentSignInPhase.signedOut) {
      await _paReadChallenge(session);
    }
  }

  Future<void> _paReadChallenge(AgentSignInSession session) async {
    try {
      await session.readChallenge();
    } on AgentSignInException {
      // The state already carries the failure.
    }
  }

  @override
  Future<void> submitAgentSignInCode(
    String agentId,
    AgentSignInCode code,
  ) async {
    final session = _paSignIns[agentId];
    if (session == null) {
      code.clear();
      throw _notReady;
    }
    // The person may have come back from the browser before the host's code
    // prompt was read: ask for it now so the code is accepted.
    if (session.state.phase == AgentSignInPhase.urlReady) {
      await _paReadChallenge(session);
    }
    await session.submitCode(code);
  }

  @override
  Future<void> cancelAgentSignIn(String agentId) async {
    final session = _paSignIns[agentId];
    if (session == null) return;
    // Stop listening first: closing the session closes its change stream.
    await _paSignInSubs.remove(agentId)?.cancel();
    try {
      await session.close();
    } catch (_) {
      // The drain is unconfirmed: keep the session, and its listener, so the
      // sheet can retry or keep waiting.
      _paListen(agentId, session);
      rethrow;
    }
    _paSignIns.remove(agentId);
    if (!_self._disposed) {
      _self._notifyListeners();
      unawaited(refreshAgentRows());
    }
  }

  // ---- merged feed --------------------------------------------------------

  Iterable<String> _paDesiredDirectories() sync* {
    final profile = _paProfile;
    final seen = <String>{};
    bool ok(String? directory) =>
        directory != null &&
        directory.startsWith('/root/projects/') &&
        _self._feedEligible(directory) &&
        seen.add(directory);
    final route = _phoneAgentRoute?.directory;
    if (ok(route)) yield route!;
    final last = _self._ocLastUsed;
    if (ok(last)) yield last!;
    if (profile != null) {
      for (final recent in _self.store.recentLocations(profile.id)) {
        if (ok(recent.directory)) yield recent.directory!;
      }
    }
  }

  Future<void> _paSyncSources() async {
    final host = _paHost;
    final profile = _paProfile;
    if (host == null || profile == null) return;
    final wanted = _paHostRunning
        ? _paDesiredDirectories().take(_maxPaseoSources).toList()
        : const <String>[];
    var changed = false;
    for (final directory in _paSources.keys.toList()) {
      if (!wanted.contains(directory)) {
        await _paDropSource(directory);
        changed = true;
      }
    }
    for (final directory in wanted) {
      if (_paSources.containsKey(directory)) continue;
      try {
        await _paAddSource(host, directory);
        changed = true;
      } catch (_) {
        // One unreachable project never hides the others.
      }
    }
    if (changed) _paRebuildMerged();
  }

  Future<PaseoChatFeedSource> _paAddSource(
    PhoneAgentHostPort host,
    String directory,
  ) async {
    final existing = _paSources[directory];
    if (existing != null) return existing.source;
    final gateway = await host.openGateway(directory);
    final folder = directory.split('/').where((p) => p.isNotEmpty).last;
    final source = PaseoChatFeedSource(
      gateway,
      projectName: folder,
      persistLastUsedProject: _self._ocRemember,
      initialLastUsedProjectDirectory: _self._ocLastUsed,
    );
    _paSources[directory] = (gateway: gateway, source: source);
    return source;
  }

  Future<void> _paDropSource(String directory) async {
    final entry = _paSources.remove(directory);
    if (entry == null) return;
    await entry.source.dispose();
    entry.gateway.close();
  }

  void _paRebuildMerged() {
    final old = _paMerged;
    unawaited(_paMergedSub?.cancel());
    _paMergedSub = null;
    _paMerged = null;
    if (old != null) unawaited(old.dispose());
    if (_paSources.isEmpty) {
      if (!_self._disposed) _self._notifyListeners();
      return;
    }
    final merged = MergedChatFeed(
      defaultSourceId: _openCodeSourceId,
      sources: [
        NamedChatFeedSource(
          id: _openCodeSourceId,
          label: 'OpenCode',
          source: _OpenCodeFeed(_self),
        ),
        for (final entry in _paSources.entries)
          NamedChatFeedSource(
            id: _paseoSourceId(entry.key),
            label: 'Phone agents',
            source: entry.value.source,
          ),
      ],
    );
    _paMerged = merged;
    _paMergedSub = merged.changes.listen((_) {
      if (!_self._disposed) _self._notifyListeners();
    });
    unawaited(merged.refreshChatFeed());
  }

  @override
  bool get chatFeedAcrossProjects => _self._ocAcross;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    final merged = _paMerged;
    if (merged == null) return _self._ocChatFeed(filter);
    final snapshot = merged.chatFeed(filter);
    return ChatFeedSnapshot(
      items: snapshot.items,
      // OpenCode answers for every project; a scoped phone source never makes
      // the whole list look single-project.
      acrossProjects: _self._ocAcross,
      loading: snapshot.loading,
      complete: snapshot.complete,
    );
  }

  @override
  List<ProjectSummary> get projectSummaries {
    final merged = _paMerged;
    if (merged == null) return _self._ocProjectSummaries;
    final byDirectory = <String, ProjectSummary>{};
    for (final project in merged.projectSummaries) {
      final before = byDirectory[project.directory];
      if (before == null) {
        byDirectory[project.directory] = project;
        continue;
      }
      final a = before.lastActivity;
      final b = project.lastActivity;
      byDirectory[project.directory] = ProjectSummary(
        directory: before.directory,
        name: before.name,
        isGit: before.isGit || project.isGit,
        chatCount: before.chatCount + project.chatCount,
        runningCount: before.runningCount + project.runningCount,
        needsYouCount: before.needsYouCount + project.needsYouCount,
        lastActivity: a == null || (b != null && b.isAfter(a)) ? b : a,
        kind: before.kind ?? project.kind,
      );
    }
    final rows = byDirectory.values.toList()
      ..sort((x, y) {
        final xt = x.lastActivity, yt = y.lastActivity;
        if (xt != null && yt != null) {
          final byTime = yt.compareTo(xt);
          if (byTime != 0) return byTime;
        } else if (xt != null || yt != null) {
          return xt != null ? -1 : 1;
        }
        return x.name.toLowerCase().compareTo(y.name.toLowerCase());
      });
    return List.unmodifiable(rows);
  }

  @override
  Future<void> refreshChatFeed() async {
    final merged = _paMerged;
    if (merged == null) return _self._ocRefresh();
    await merged.refreshChatFeed();
  }

  @override
  String? get lastUsedProjectDirectory => _self._ocLastUsed;

  @override
  Future<void> rememberLastUsedProject(String directory) =>
      _self._ocRemember(directory);

  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);

  // ---- starting and opening chats -----------------------------------------

  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) =>
      startAgentChatIn(
        directory,
        agentId: selectedChatAgentId,
        firstPrompt: firstPrompt,
      );

  @override
  Future<String> startAgentChatIn(
    String directory, {
    required String agentId,
    String? firstPrompt,
  }) async {
    if (agentId == openCodeChatAgentId) {
      await _paClearRoute();
      return _self._ocStartChatIn(directory, firstPrompt: firstPrompt);
    }
    if (!phoneAgentsAvailable) throw _notReady;
    final row = _paRowFor(agentId);
    final descriptor = row == null ? null : _paCatalog.byId(row.id);
    if (row == null || descriptor == null || !row.chatSelectable) {
      throw const ProductException('This agent is not ready yet.');
    }
    final host = _paEnsureHost();
    final PaseoChatFeedSource source;
    try {
      source = await _paAddSource(host, directory);
    } catch (_) {
      throw const ProductException(
        'Could not reach the agent on this phone. Resume it and try again.',
      );
    }
    _paRebuildMergedIfNeeded();
    final id = await source.startAgentChatIn(
      directory,
      agentId: descriptor.providerId,
      firstPrompt: firstPrompt,
    );
    _paLive.add(jsonEncode([_paseoSourceId(directory), id, directory]));
    await _self._ocRemember(directory);
    await _paActivateRoute(directory);
    return id;
  }

  void _paRebuildMergedIfNeeded() {
    final merged = _paMerged;
    final ids = {for (final d in _paSources.keys) _paseoSourceId(d)};
    final have = {
      if (merged != null)
        for (final s in merged.sources)
          if (s.id != _openCodeSourceId) s.id,
    };
    if (ids.length != have.length || !ids.containsAll(have)) {
      _paRebuildMerged();
    }
  }

  /// The pair a live connect or rescope should use while a phone-agent route
  /// owns the connection; null when the connection is OpenCode's. Built
  /// synchronously from the host, so every rebuild (location change, wake)
  /// stays on the phone host.
  PhoneAgentRoutePair? _phoneAgentConnectPair(ServerProfile profile) {
    final route = _phoneAgentRoute;
    final host = _paHost;
    if (route == null || host == null || route.profileId != profile.id) {
      return null;
    }
    try {
      final gateway = host.newGatewaySync(route.directory);
      return (gateway: gateway, operations: gateway);
    } catch (_) {
      return null;
    }
  }

  Future<void> _paActivateRoute(String directory) async {
    final profile = _paProfile;
    if (profile == null) throw _notReady;
    final current = _phoneAgentRoute;
    if (current != null &&
        current.directory == directory &&
        _self.api is PaseoGateway) {
      return;
    }
    final host = _paEnsureHost();
    // Proves the daemon answers and caches its credential for rebuilds.
    final probe = await host.openGateway(directory);
    probe.close();
    _phoneAgentRoute = (profileId: profile.id, directory: directory);
    await _self.connect(profile);
    await _self.selectLocationForExistingSession(directory: directory);
  }

  Future<void> _paClearRoute() async {
    if (_phoneAgentRoute == null) return;
    final profile = _paProfile;
    _phoneAgentRoute = null;
    _paLive.clear(); // Live admission ends when the gateway is retired.
    if (profile != null) await _self.connect(profile);
  }

  @override
  Future<ChatFeedRoute> openChatFeedItem(ChatFeedItem item) async {
    final merged = _paMerged;
    if (merged == null) {
      if (item.sourceId != null && item.sourceId != _openCodeSourceId) {
        throw _gone;
      }
      await _paClearRoute();
      await _self.selectLocationForExistingSession(directory: item.directory);
      return ChatFeedRoute(
        sourceId: _openCodeSourceId,
        sessionID: item.sessionID,
        directory: item.directory,
        agentId: item.agentId,
      );
    }
    final current = merged
        .chatFeed(const ChatFeedFilter(includeSubagents: true))
        .items
        .where((row) => row.identity == item.identity)
        .toList();
    if (current.length != 1) throw _gone;
    final route = merged.routeFor(current.single);
    if (route.sourceId.startsWith('paseo:')) {
      await _paActivateRoute(route.directory);
    } else {
      await _paClearRoute();
      await _self.selectLocationForExistingSession(directory: route.directory);
    }
    return route;
  }

  @override
  AgentResumeNotice agentResumeNotice(ChatFeedItem item) {
    if (item.agentId == openCodeChatAgentId ||
        (item.sourceId ?? _openCodeSourceId) == _openCodeSourceId) {
      return const AgentResumeNotice(canReopen: true);
    }
    if (_paLive.contains(item.identity) && _phoneAgentRoute != null) {
      return const AgentResumeNotice(canReopen: true);
    }
    final row = _paRowFor(item.agentId);
    if (row != null && row.capabilities.resumeVerified) {
      return const AgentResumeNotice(canReopen: true);
    }
    return const AgentResumeNotice(
      canReopen: false,
      label: agentResumeUnverifiedLabel,
      note: agentStartsNewChatNote,
      requiresAcknowledgement: true,
    );
  }

  @override
  Future<String> startNewChatReplacing(
    ChatFeedItem old, {
    required bool newChatAcknowledged,
  }) async {
    if (!newChatAcknowledged) {
      throw const ProductException('Choose Start new chat to continue.');
    }
    final merged = _paMerged;
    if (merged == null) throw _gone;
    final current = merged
        .chatFeed(const ChatFeedFilter(includeSubagents: true))
        .items
        .where((row) => row.identity == old.identity)
        .toList();
    if (current.length != 1) throw _gone;
    final source = merged.sourceFor(merged.routeFor(current.single));
    if (source is! PaseoChatFeedSource) throw _gone;
    final id = await source.gateway.startNewHostAgentChat(
      old.sessionID,
      newChatAcknowledged: true,
    );
    _paLive.add(jsonEncode([old.sourceId, id, old.directory]));
    await source.refreshChatFeed();
    await _paActivateRoute(old.directory);
    return id;
  }

  // ---- closing ------------------------------------------------------------

  /// Closes everything this profile's phone agents own, in the order the
  /// deletion contract requires: auth, owned setup, host, then feeds.
  Future<void> _paCloseAll({required bool stopHost}) async {
    for (final entry in _paSignIns.entries.toList()) {
      await _paSignInSubs.remove(entry.key)?.cancel();
      try {
        await entry.value.close();
      } catch (_) {
        // Native cleanup drains this profile's processes again on removal.
      }
    }
    _paSignIns.clear();
    final host = _paHost;
    _paHost = null;
    _paHostProfile = null;
    await _paSetupSub?.cancel();
    _paSetupSub = null;
    if (host != null) {
      try {
        await host.cancelInstall();
      } catch (_) {}
      if (stopHost) {
        try {
          await host.stop();
        } catch (_) {}
      }
      try {
        await host.dispose();
      } catch (_) {}
    }
    final merged = _paMerged;
    _paMerged = null;
    await _paMergedSub?.cancel();
    _paMergedSub = null;
    if (merged != null) await merged.dispose();
    for (final directory in _paSources.keys.toList()) {
      await _paDropSource(directory);
    }
    _phoneAgentRoute = null;
    _paLive.clear();
    _paRows = const [];
    _paHostRunning = false;
  }

  /// Deletion hook: runs before ProfileStore removes the profile.
  Future<void> _paCloseForDeletion(String profileId) async {
    if (_paHostProfile != profileId &&
        _paProfile?.id != profileId &&
        _paSignIns.isEmpty) {
      return;
    }
    await _paCloseAll(stopHost: true);
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  Future<void> closePhoneAgentsForSignInReset() async {
    await _paCloseAll(stopHost: true);
    _paNeedRestart = true;
    if (!_self._disposed) _self._notifyListeners();
  }

  /// Controller disposal: stop listening; the host keeps running for the
  /// Android service owner.
  void _paShutdown() {
    unawaited(_paSetupSub?.cancel());
    unawaited(_paMergedSub?.cancel());
    for (final sub in _paSignInSubs.values) {
      unawaited(sub.cancel());
    }
    unawaited(_paMerged?.dispose());
    for (final entry in _paSources.values) {
      unawaited(entry.source.dispose());
      entry.gateway.close();
    }
    _paSources.clear();
    for (final session in _paSignIns.values) {
      unawaited(session.close().catchError((Object _) {}));
    }
    _paSignIns.clear();
  }
}

/// A gateway pair as the connect path takes it.
typedef PhoneAgentRoutePair = ({
  ServerGateway gateway,
  ServerOperationsGateway operations,
});
