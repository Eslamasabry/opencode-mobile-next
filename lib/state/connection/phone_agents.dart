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

  /// Folders whose live agent conversations have been read since start: the
  /// saved rows of every other folder still show (see [_AgentFeedCache]).
  final _paLoadedDirs = <String>{};
  late final _AgentFeedCache _paCache = _AgentFeedCache(_self.store.prefs);
  List<({ChatFeedItem item, bool canReopen})>? _paCacheRows;
  String? _paCacheOwner;

  /// The conversation backend for agents on this phone: its own
  /// [ConnectionController], so a Claude conversation never moves this
  /// connection off OpenCode. Created by [_paBackendFor] on first use.
  ConnectionController? _paBackend;
  PhoneAgentHostPort? _paBackendHost;

  /// Which backend each opened or started agent conversation lives on, for
  /// [backendForConversation].
  final _paOwners = <String, ConnectionController>{};

  /// Conversations whose resume the helper refused: the next tap offers a
  /// new conversation instead.
  final _paResumeFailed = <String>{};

  /// Conversations last opened as OpenCode's: the page is found by id
  /// alone, and these never fall back to the agents' connection.
  final _paOpenCodeOpened = <String>{};

  /// Catalog id of the agent last opened: the one whose host status the
  /// recovery reads.
  String? _paBackendAgentId;

  String? get _paHostProbeAgent =>
      _paBackendAgentId ??
      _paCatalog.agents
          .where((descriptor) => descriptor.id == 'claude')
          .firstOrNull
          ?.id;

  /// The agents' connection while it exists (app lifecycle follows it).
  ConnectionController? get _paBackendLive {
    final backend = _paBackend;
    return backend == null || backend._disposed ? null : backend;
  }

  /// The controller a conversation's screen talks to when it is not this
  /// one: an agent on this phone's conversation lives on its own backend.
  /// Null for this connection's own (OpenCode) conversations.
  ConnectionController? backendForConversation(String sessionID) {
    final owner = _paOwners[sessionID];
    if (owner != null && !owner._disposed) return owner;
    // A conversation the agents' connection lists (a notification about one
    // that was never opened here); this connection's own come first.
    if (_self.sessionsById.containsKey(sessionID) ||
        _paOpenCodeOpened.contains(sessionID)) {
      return null;
    }
    final live = _paBackendLive;
    return live != null && live.sessionsById.containsKey(sessionID)
        ? live
        : null;
  }

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
    if (!phoneAgentsAvailable) return openCodeChatAgentId;
    // Before the first row read (app start) the saved choice stands; it
    // falls back only once the rows say the agent can't be chosen.
    if (_paRows.isEmpty) return saved;
    final row = _paRowFor(saved);
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

  // ---- models ---------------------------------------------------------------

  String _paModelKey(String profileID) => 'oc.agentModel.$profileID';

  Map<String, String> _paSavedModels() {
    final id = _paProfile?.id;
    if (id == null) return const {};
    try {
      final raw = _self.store.prefs.getString(_paModelKey(id));
      if (raw == null) return const {};
      final decoded = jsonDecode(raw);
      if (decoded is! Map) return const {};
      return {
        for (final entry in decoded.entries)
          if (entry.key is String && entry.value is String)
            entry.key as String: entry.value as String,
      };
    } catch (_) {
      return const {};
    }
  }

  @override
  String? selectedAgentModel(String agentId) => _paSavedModels()[agentId];

  @override
  Future<void> selectAgentModel(String agentId, String? modelId) async {
    final id = _paProfile?.id;
    if (id == null) throw _notReady;
    final models = Map<String, String>.of(_paSavedModels());
    if (modelId == null || modelId.isEmpty) {
      models.remove(agentId);
    } else {
      models[agentId] = modelId;
    }
    try {
      await _self.store.prefs.setString(_paModelKey(id), jsonEncode(models));
    } catch (_) {}
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  Future<List<AgentModelChoice>> agentModels(String agentId) async {
    if (!phoneAgentsAvailable) throw _notReady;
    final descriptor = _paCatalog.byId(agentId);
    if (descriptor == null) throw _notReady;
    // The host's runtimes and models are global; any project opens it.
    // The folder a new chat most likely starts in, so its gateway already
    // holds the list when the chat starts.
    final last = lastUsedProjectDirectory;
    final directory =
        (last != null && last.startsWith('/root/projects/') ? last : null) ??
        _paSources.keys.firstOrNull ??
        _paDesiredDirectories().firstOrNull ??
        _self.directory;
    if (directory == null || directory.isEmpty) {
      throw const ProductException('Choose a project first.');
    }
    final PaseoChatFeedSource source;
    final ProvidersResponse providers;
    try {
      source = await _paReachSource(directory);
      providers = await source.providers();
    } on ProductException {
      rethrow;
    } catch (_) {
      throw const ProductException(
        'Could not reach the agent on this phone. Resume it and try again.',
      );
    }
    final provider = providers.providers
        .where((entry) => entry.id == descriptor.providerId)
        .firstOrNull;
    if (provider == null) return const [];
    return [
      for (final modelID in provider.modelIDs)
        AgentModelChoice(
          id: modelID,
          name: provider.modelData[modelID]?['name'] as String? ?? modelID,
          isDefault: provider.modelData[modelID]?['isDefault'] == true,
        ),
    ];
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
    // Conversations with these agents were listed before: their folders are
    // read now, beside the inspection below, so the list paints once.
    if (_paSources.isEmpty && _paUsedBefore) {
      unawaited(_paSyncSources(assumeRunning: true).catchError((Object _) {}));
    }
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
            capabilities: _paHostCapabilities(descriptor),
          );
          // Sign-in is only inspected for an installed, qualified agent: it
          // starts a native status process. Claude's is read natively; the
          // other agents' from what the helper reports about them.
          if (descriptor.id != 'claude' &&
              descriptor.signInMethod != AgentSignInMethod.none &&
              runtime.installed &&
              runtime.architectureQualified &&
              runtime.hostAvailable) {
            final phase = await _paProviderSignIn(descriptor.providerId);
            if (phase != null) {
              runtime = PhoneAgentRuntime(
                agentId: runtime.agentId,
                installed: runtime.installed,
                hostAvailable: runtime.hostAvailable,
                architectureQualified: runtime.architectureQualified,
                capabilities: runtime.capabilities,
                signInPhase: phase,
                stoppedInBackground: runtime.stoppedInBackground,
                resetAt: runtime.resetAt,
              );
            }
          } else if (descriptor.signInMethod ==
                  AgentSignInMethod.browserOAuthHost &&
              runtime.installed &&
              runtime.architectureQualified &&
              runtime.signInPhase == null) {
            final session = _paSession(descriptor.id);
            try {
              await session.inspectStatus();
            } catch (_) {}
            runtime = await host.inspect(
              descriptor.id,
              signIn: session.state,
              capabilities: _paHostCapabilities(descriptor),
            );
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
    // Rows are read when the person opens an agent screen or sheet: an
    // installed agent whose helper Android stopped is started again here
    // instead of asking them to tap Resume after every app start. One try a
    // minute, so a helper that keeps failing shows its Resume row.
    final now = DateTime.now();
    if (rows.any((row) => row.status == PhoneAgentStatus.stoppedInBackground) &&
        (_paAutoResumedAt == null ||
            now.difference(_paAutoResumedAt!) > const Duration(minutes: 1))) {
      _paAutoResumedAt = now;
      unawaited(resumeAgentHost().catchError((Object _) {}));
    }
  }

  DateTime? _paAutoResumedAt;

  /// What this phone's helper is known to do for [descriptor]: a runtime
  /// fact, not a catalog claim. Claude's sessions resume through the helper
  /// (resume_agent_request, proven on Paseo 0.9.2); the others are unproven.
  AgentCapabilities _paHostCapabilities(AgentDescriptor descriptor) =>
      descriptor.id == 'claude' && descriptor.route == AgentRoute.paseoNative
      ? const AgentCapabilities(resumeVerified: true)
      : descriptor.capabilities;

  /// An agent other than Claude, as the helper reports it: ready to start is
  /// signed in, "authentication required" is signed out; null while unknown
  /// (no folder connected yet, or the helper still checking).
  Future<AgentSignInPhase?> _paProviderSignIn(String providerId) async {
    final gateway = _paSources.values.firstOrNull?.gateway;
    if (gateway == null) return null;
    try {
      final catalog = await gateway.loadHostAgentProviders();
      final provider = catalog.providers
          .where((entry) => entry.id == providerId)
          .firstOrNull;
      return switch (provider?.availability) {
        HostAgentProviderAvailability.ready => AgentSignInPhase.signedIn,
        HostAgentProviderAvailability.needsHostSignIn =>
          AgentSignInPhase.signedOut,
        _ => null,
      };
    } catch (_) {
      return null;
    }
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
  String? get agentSignInProfileId =>
      phoneAgentsAvailable ? (_paHostProfile ?? _paProfile?.id) : null;

  @override
  Future<void> recheckAgentSignIn(String agentId) async {
    if (agentId != 'claude') {
      // The helper re-reads its agents, then the row says where it stands.
      try {
        await _paSources.values.firstOrNull?.gateway.loadHostAgentProviders(
          refresh: true,
        );
      } catch (_) {}
      await refreshAgentRows();
      if (!_self._disposed) _self._notifyListeners();
      return;
    }
    // A fresh reading: the old session may still hold an earlier answer.
    await _paSignInSubs.remove(agentId)?.cancel();
    final previous = _paSignIns.remove(agentId);
    if (previous != null) {
      try {
        await previous.close();
      } catch (_) {}
    }
    try {
      await _paSession(agentId).inspectStatus();
    } catch (_) {
      // The rows below still say what is known.
    }
    await refreshAgentRows();
    if (!_self._disposed) _self._notifyListeners();
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
    final open = _paBackend?.directory;
    if (ok(open)) yield open!;
    final last = _self._ocLastUsed;
    if (ok(last)) yield last!;
    if (profile != null) {
      for (final recent in _self.store.recentLocations(profile.id)) {
        if (ok(recent.directory)) yield recent.directory!;
      }
    }
  }

  Future<void> _paSyncSources({bool assumeRunning = false}) async {
    final host = _paHost;
    final profile = _paProfile;
    if (host == null || profile == null) return;
    final wanted = _paHostRunning || assumeRunning
        ? _paDesiredDirectories().take(_maxPaseoSources).toList()
        : const <String>[];
    var changed = false;
    for (final directory in _paSources.keys.toList()) {
      if (!wanted.contains(directory)) {
        await _paDropSource(directory);
        changed = true;
      }
    }
    var missed = false;
    for (final directory in wanted) {
      if (_paSources.containsKey(directory)) continue;
      try {
        await _paAddSource(host, directory);
        changed = true;
      } catch (_) {
        // One unreachable project never hides the others.
        missed = true;
      }
    }
    // A folder reached by an action (opening, starting) has a connection the
    // list doesn't show yet.
    if (changed) {
      _paRebuildMerged();
    } else {
      _paRebuildMergedIfNeeded();
    }
    // A helper that was just started takes a few seconds to listen: try the
    // missing projects again, a few times, so their conversations appear.
    if (missed && _paSyncRetries < 10) {
      _paSyncRetries++;
      unawaited(
        Future<void>.delayed(const Duration(seconds: 3), () async {
          if (!_self._disposed) await _paSyncSources();
        }),
      );
    } else if (!missed) {
      _paSyncRetries = 0;
    }
  }

  int _paSyncRetries = 0;

  /// Reaches the agent host for [directory] the way a person's action needs
  /// it: a folder outside the agents' project space is said plainly, and a
  /// helper Android stopped is started again and given up to 30 s to listen
  /// before the action fails.
  Future<PaseoChatFeedSource> _paReachSource(String directory) async {
    if (!directory.startsWith('/root/projects/')) {
      throw const ProductException(
        'Agents on this phone work in projects under /root/projects. Choose one of those.',
      );
    }
    final host = _paEnsureHost();
    try {
      return await _paAddSource(host, directory);
    } catch (_) {}
    try {
      await host.start();
    } on AgentHostException catch (error) {
      // Another start is already running: wait for it below.
      if (error.reason != AgentHostFailure.busy) rethrow;
    }
    final deadline = DateTime.now().add(const Duration(seconds: 30));
    while (true) {
      try {
        final source = await _paAddSource(host, directory);
        // No row refresh here: its source sync would close this new folder's
        // connection before the caller uses it.
        _paHostRunning = true;
        return source;
      } catch (_) {
        if (DateTime.now().isAfter(deadline)) rethrow;
        await Future<void>.delayed(const Duration(seconds: 1));
      }
    }
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
    unawaited(
      merged.refreshChatFeed().whenComplete(() {
        if (_self._disposed || _paMerged != merged) return;
        final agents = merged.chatFeed().items.any(
          (item) => item.sourceId != _openCodeSourceId,
        );
        unawaited(_paRememberUsed(agents));
        _paFeedSettled = true;
        _paSaveRows(merged);
        _self._notifyListeners();
      }),
    );
  }

  // ---- saved rows -----------------------------------------------------------

  List<({ChatFeedItem item, bool canReopen})> get _paSavedRows {
    final id = _paProfile?.id;
    if (id == null || !phoneAgentsAvailable) return const [];
    if (_paCacheOwner != id) {
      _paCacheOwner = id;
      _paCacheRows = _paCache.read(id);
    }
    return _paCacheRows ?? const [];
  }

  /// The agents' saved conversations as titles, for the opening screen's
  /// last-known list (beside OpenCode's), newest first.
  List<SessionPreview> get savedAgentSessionPreviews => List.unmodifiable([
    for (final row in _paSavedRows)
      if (!row.item.isSubagent)
        SessionPreview(
          row.item.sessionID,
          row.item.title,
          row.item.lastActivity.millisecondsSinceEpoch,
        ),
  ]);

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
          (item) => (item: item, canReopen: agentResumeNotice(item).canReopen),
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

  // ---- one paint ------------------------------------------------------------

  /// How long the conversations list waits for agents on this phone before
  /// it shows OpenCode's conversations alone.
  static const _paFeedHold = Duration(seconds: 4);
  bool _paFeedSettled = false;
  DateTime? _paHoldUntil;
  Timer? _paHoldTimer;

  bool get _paUsedBefore => _paCache.usedBefore(_paProfile?.id);

  Future<void> _paRememberUsed(bool used) =>
      _paCache.rememberUsed(_paProfile?.id, used);

  /// The list holds its first paint while this phone's agents, which had
  /// conversations last time, are still being read: one paint with every
  /// conversation instead of OpenCode's first and the agents' a moment later.
  bool get _paHoldFeed {
    if (_paFeedSettled ||
        !phoneAgentsAvailable ||
        !_paUsedBefore ||
        _paSavedRows.isNotEmpty) {
      return false;
    }
    final now = DateTime.now();
    final until = _paHoldUntil ??= now.add(_paFeedHold);
    if (!now.isBefore(until)) {
      _paFeedSettled = true;
      return false;
    }
    _paHoldTimer ??= Timer(until.difference(now), () {
      _paHoldTimer = null;
      if (!_self._disposed) _self._notifyListeners();
    });
    return true;
  }

  @override
  bool get chatFeedAcrossProjects => _self._ocAcross;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    if (_paHoldFeed) {
      return ChatFeedSnapshot(
        items: const [],
        acrossProjects: _self._ocAcross,
        loading: true,
        complete: false,
      );
    }
    final merged = _paMerged;
    final snapshot = merged?.chatFeed(filter) ?? _self._ocChatFeed(filter);
    final shown = {for (final item in snapshot.items) item.identity};
    final saved = [
      for (final row in _paSavedRows)
        if (!_paLoadedDirs.contains(row.item.directory) &&
            !shown.contains(row.item.identity) &&
            chatFeedMatches(row.item, filter))
          row.item,
    ];
    final items = saved.isEmpty
        ? snapshot.items
        : List<ChatFeedItem>.unmodifiable(
            <ChatFeedItem>[...snapshot.items, ...saved]
              ..sort(compareChatFeedItems),
          );
    return ChatFeedSnapshot(
      items: items,
      // OpenCode answers for every project; a scoped phone source never makes
      // the whole list look single-project.
      acrossProjects: _self._ocAcross,
      loading: snapshot.loading && items.isEmpty,
      // Saved rows stand in while their folder loads; they are no failure.
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
      return _self._ocStartChatIn(directory, firstPrompt: firstPrompt);
    }
    if (!phoneAgentsAvailable) throw _notReady;
    if (_paRows.isEmpty) await refreshAgentRows();
    final row = _paRowFor(agentId);
    final descriptor = row == null ? null : _paCatalog.byId(row.id);
    if (row == null || descriptor == null || !row.chatSelectable) {
      throw const ProductException('This agent is not ready yet.');
    }
    final PaseoChatFeedSource source;
    try {
      source = await PerfTrace.span(
        'agent.start.reach',
        () => _paReachSource(directory),
      );
    } on ProductException {
      rethrow;
    } catch (_) {
      throw const ProductException(
        'Could not reach the agent on this phone. Resume it and try again.',
      );
    }
    _paRebuildMergedIfNeeded();
    // Remembered first: a row refresh keeps only remembered folders'
    // connections, and this one is creating the agent.
    await _self._ocRemember(directory);
    // The agent's own backend connects while the agent starts: neither waits
    // for the other (the agent is created on the folder's own gateway).
    final backend = PerfTrace.span(
      'agent.start.backend',
      () => _paBackendFor(
        directory,
        agentName: descriptor.name,
        agentId: descriptor.id,
      ),
    );
    final String id;
    try {
      id = await PerfTrace.span(
        'agent.start.create',
        () => source.startAgentChatIn(
          directory,
          agentId: descriptor.providerId,
          firstPrompt: firstPrompt,
          modelId: selectedAgentModel(agentId),
        ),
      );
    } catch (_) {
      unawaited(backend.then((_) {}, onError: (Object _) {}));
      rethrow;
    }
    _paLive.add(jsonEncode([_paseoSourceId(directory), id, directory]));
    _paOwners[id] = await backend;
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

  Timer? _paBackendWatch;
  Timer? _paListRefresh;
  bool _paRecovering = false;
  int _paOfflineTicks = 0;
  DateTime? _paKeptUpAt;

  @override
  Future<ChatFeedRoute> openChatFeedItem(ChatFeedItem item) async {
    // A saved row whose folder has not been read yet opens on the agent's
    // backend directly; it starts the helper when needed.
    final saved = _paSavedRowFor(item);
    if (saved != null) {
      final row = saved.item;
      final descriptor =
          _paCatalog.byId(row.agentId) ??
          _paCatalog.agents
              .where((agent) => agent.providerId == row.agentId)
              .firstOrNull;
      _paOwners[row.sessionID] = await _paBackendFor(
        row.directory,
        agentId: descriptor?.id,
        agentName: row.agentLabel ?? descriptor?.name,
      );
      return ChatFeedRoute(
        sourceId: row.sourceId!,
        sessionID: row.sessionID,
        directory: row.directory,
        agentId: row.agentId,
      );
    }
    final merged = _paMerged;
    if (merged == null) {
      if (item.sourceId != null && item.sourceId != _openCodeSourceId) {
        throw _gone;
      }
      _paOwners.remove(item.sessionID);
      _paOpenCodeOpened.add(item.sessionID);
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
    var route = merged.routeFor(current.single);
    if (route.sourceId.startsWith('paseo:')) {
      final row = current.single;
      final source = merged.sourceFor(route);
      if (source is PaseoChatFeedSource &&
          !source.gateway.isAgentLoaded(row.sessionID) &&
          source.gateway.canResumeAgent(row.sessionID)) {
        try {
          final id = await source.gateway.resumeHostAgentChat(row.sessionID);
          _paLive.add(row.identity);
          route = ChatFeedRoute(
            sourceId: route.sourceId,
            sessionID: id,
            directory: route.directory,
            agentId: route.agentId,
          );
        } catch (_) {
          _paResumeFailed.add(row.identity);
          if (!_self._disposed) _self._notifyListeners();
          throw ProductException(
            '${row.agentLabel ?? 'The agent'} could not reopen this conversation. '
            'Tap it again to start a new one from it.',
          );
        }
      }
      final descriptor =
          _paCatalog.byId(row.agentId) ??
          _paCatalog.agents
              .where((agent) => agent.providerId == row.agentId)
              .firstOrNull;
      _paOwners[route.sessionID] = await _paBackendFor(
        route.directory,
        agentId: descriptor?.id,
        agentName: row.agentLabel ?? descriptor?.name,
      );
    } else {
      // The page is found by id alone: this one is OpenCode's again.
      _paOwners.remove(route.sessionID);
      _paOpenCodeOpened.add(route.sessionID);
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
    if (_paLive.contains(item.identity)) {
      return const AgentResumeNotice(canReopen: true);
    }
    // Not read live yet: what it was when saved, so the label doesn't flash.
    final saved = _paSavedRowFor(item);
    if (saved != null && saved.canReopen) {
      return const AgentResumeNotice(canReopen: true);
    }
    // Still loaded in the helper (running or idle since it last started):
    // opening it shows the live conversation, whoever started it. One the
    // helper no longer holds reopens through the runtime's own resume,
    // unless that already failed here.
    for (final entry in _paSources.entries) {
      if (_paseoSourceId(entry.key) != item.sourceId) continue;
      final gateway = entry.value.gateway;
      if (gateway.isAgentLoaded(item.sessionID) ||
          (gateway.canResumeAgent(item.sessionID) &&
              !_paResumeFailed.contains(item.identity))) {
        return const AgentResumeNotice(canReopen: true);
      }
    }
    // The agent can resume in general, but this conversation has nothing to
    // resume by (no session handle, or the helper refused): a new one.
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
    _paOwners[id] = await _paBackendFor(old.directory);
    return id;
  }

  // ---- closing ------------------------------------------------------------

  /// Closes everything this profile's phone agents own, in the order the
  /// deletion contract requires: auth, owned setup, host, then feeds.
  Future<void> _paCloseAll({required bool stopHost}) async {
    _paDisposeBackend();
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
    _paDisposeBackend();
    _paHoldTimer?.cancel();
    _paHoldTimer = null;
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
