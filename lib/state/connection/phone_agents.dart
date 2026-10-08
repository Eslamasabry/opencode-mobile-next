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
    implements
        PhoneAgentsSource,
        AgentChatFeedSource,
        PhoneAgentAccountSource,
        PhoneAgentRemovalSource {
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
  AgentSignInForegroundBinding? _paForegroundBinding;
  final _paClosingOwners = <String, _PhoneAgentCloseScope>{};
  StreamSubscription<AgentSetupProgress>? _paSetupSub;
  final _paSignIns = <String, AgentSignInSession>{};
  final _paSignInSubs = <String, StreamSubscription<AgentSignInState>>{};
  List<AgentRow> _paRows = const [];
  bool _paHostRunning = false;
  final _paChecks = <String, AgentPhoneCheckResult>{};
  final _paLive = <String>{};
  bool _paNeedRestart = false;
  String? _paRemovingAgent;
  Object? _paRemovalToken;
  Future<void>? _paRefreshingRows;
  final _paSources =
      <String, ({PaseoGateway gateway, PaseoChatFeedSource source})>{};
  MergedChatFeed? _paMerged;
  StreamSubscription<void>? _paMergedSub;

  /// Folders whose live agent conversations have been read since start: the
  /// saved rows of every other folder still show (see [_AgentFeedCache]).
  final _paLoadedDirs = <String>{};

  /// Titles of agent conversations as their rows show them, for a record a
  /// resume left without one.
  final _paTitleHints = <String, String>{};
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
    if (live != null && live.sessionsById.containsKey(sessionID)) return live;
    // Another server's conversation (its alert was tapped).
    for (final side in _self._sides.values) {
      if (!side._disposed && side.sessionsById.containsKey(sessionID)) {
        return side;
      }
    }
    return null;
  }

  AgentCatalog get _paCatalog => AgentCatalog.builtIn;

  /// The phone profile the agents run under: the main connection's when it
  /// is the in-app Ubuntu, else a shown in-app Ubuntu beside it (the app on
  /// Termux still lists Claude's conversations).
  ServerProfile? get _paProfile {
    final main = _self._connectedProfile ?? _self.profile;
    if (main == null || BuiltinLinux.managesServerUrl(main.baseUrl)) {
      return main == null ? null : _self.store.phoneAgentOwnerProfile(main);
    }
    for (final side in _self._sides.values) {
      final profile = side._connectedProfile;
      if (profile != null && BuiltinLinux.managesServerUrl(profile.baseUrl)) {
        return _self.store.phoneAgentOwnerProfile(profile);
      }
    }
    return main;
  }

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
    if (_paClosingOwners.containsKey(profile.id)) {
      throw _PhoneAgentRoutes._signInStopFailed;
    }
    final existing = _paHost;
    if (existing != null && _paHostProfile == profile.id) return existing;
    if (existing != null) {
      unawaited(_paCloseAll(stopHost: false).catchError((Object _) {}));
    }
    final factory = _self._phoneAgentHostFactory;
    final host = factory != null
        ? factory(profile)
        : BuiltinPhoneAgentHostPort(
            BuiltinPhoneAgents(profileId: profile.id, prefs: _self.store.prefs),
          );
    _paHost = host;
    _paHostProfile = profile.id;
    if (!_self._isSecondary) {
      _paForegroundBinding = AgentSignInForegroundRegistry.bind(
        profile.id,
        _self.backgroundLive,
      );
    }
    _paSetupSub = host.setupChanges.listen((progress) {
      if (_self._disposed || !identical(_paHost, host)) return;
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
    _paEnsureHost();
    final existing = _paSignIns[agentId];
    if (existing != null) return existing;
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
      if (_self._disposed || !identical(_paSignIns[agentId], session)) return;
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

  Future<void> _paRefreshRows({bool syncSources = true}) async {
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
    final helperVersion = _self._paObservedHelperVersion;
    final rows = <AgentRow>[];
    var running = false;
    for (final descriptor in _paCatalog.agents) {
      PhoneAgentRuntime? runtime;
      if (_paEligible(descriptor)) {
        try {
          runtime = await host.inspect(
            descriptor.id,
            signIn: _paSignIns[descriptor.id]?.state,
            capabilities: _self._paHostCapabilities(descriptor, arch),
          );
          if (runtime.installed &&
              descriptor.signInMethod != AgentSignInMethod.none &&
              (host is PhoneAgentAuthPort || runtime.signInPhase == null)) {
            final auth = await _paProbeSignIn(host, descriptor.id);
            if (_self._disposed || _paHost != host || _paHostProfile != owner) {
              return;
            }
            _paAuthResults[descriptor.id] = auth;
            runtime = PhoneAgentRuntime(
              agentId: runtime.agentId,
              installed: runtime.installed,
              hostAvailable: runtime.hostAvailable,
              architectureQualified: runtime.architectureQualified,
              capabilities: runtime.capabilities,
              signInPhase:
                  auth.state == AgentAuthProbeState.signedIn &&
                      runtime.signInPhase == AgentSignInPhase.limitReached
                  ? AgentSignInPhase.limitReached
                  : _paAuthPhase(auth),
              stoppedInBackground: runtime.stoppedInBackground,
              resetAt: runtime.resetAt,
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
    final now = DateTime.now();
    final autoResume =
        rows.any((row) => row.status == PhoneAgentStatus.stoppedInBackground) &&
        (_paAutoResumedAt == null ||
            now.difference(_paAutoResumedAt!) > const Duration(minutes: 1));
    _paRows = List.unmodifiable(rows);
    _paHostRunning = running;
    _paScheduleSignInRecheck(rows);
    if (syncSources) {
      try {
        await _paSyncSources();
      } catch (_) {}
      if (_self._disposed || _paHost != host || _paHostProfile != owner) return;
      // The first handshake may supply the exact version missing above.
      // The second scan reads fresh gates and skips source synchronization.
      if (_self._paObservedHelperVersion != helperVersion) {
        await _paRefreshRows(syncSources: false);
        return;
      }
    }
    // Mark the auto-resume only for the final, freshly inspected rows.
    if (autoResume) _paAutoResuming = true;
    if (!_self._disposed) _self._notifyListeners();
    // Rows are read when the person opens an agent screen or sheet: an
    // installed agent whose helper Android stopped is started again here
    // instead of asking them to tap Resume after every app start. One try a
    // minute, so a helper that keeps failing shows its Resume row.
    if (autoResume) {
      _paAutoResumedAt = now;
      unawaited(
        resumeAgentHost().catchError((Object _) {}).whenComplete(() {
          _paAutoResuming = false;
          if (!_self._disposed) _self._notifyListeners();
        }),
      );
    }
  }

  DateTime? _paAutoResumedAt;

  /// The helper is being started again by the app itself.
  bool _paAutoResuming = false;

  static const _paSignInReadLimit = Duration(seconds: 10);
  final _paAuthResults = <String, AgentAuthProbeResult>{};
  final _paAuthRevisions = <String, int>{};

  AgentSignInPhase _paAuthPhase(AgentAuthProbeResult result) =>
      switch (result.state) {
        AgentAuthProbeState.signedIn => AgentSignInPhase.signedIn,
        AgentAuthProbeState.signedOut => AgentSignInPhase.signedOut,
        AgentAuthProbeState.error => AgentSignInPhase.failed,
      };

  Future<AgentAuthProbeResult> _paProbeSignIn(
    PhoneAgentHostPort host,
    String id,
  ) async {
    final revision = (_paAuthRevisions[id] ?? 0) + 1;
    _paAuthRevisions[id] = revision;
    try {
      if (host is PhoneAgentAuthPort) {
        final result = await (host as PhoneAgentAuthPort)
            .probeSignIn(id)
            .timeout(_paSignInReadLimit);
        if (_paAuthRevisions[id] != revision) {
          return _paAuthResults[id] ??
              const AgentAuthProbeResult.failed(
                AgentAuthProbeError.hostUnavailable,
              );
        }
        if (id != 'claude' ||
            result.error != AgentAuthProbeError.probeUnsupported) {
          return result;
        }
      }
      // Preserve the existing direct Claude CLI status while lane BB supplies
      // the private multi-agent bridge. Never use generic setup receipts or
      // provider error wording as an auth fallback.
      if (id == 'claude') {
        await _paSession(id).inspectStatus().timeout(_paSignInReadLimit);
        final phase = _paSignIns[id]?.state.phase;
        return AgentAuthProbeResult(
          state: switch (phase) {
            AgentSignInPhase.signedIn => AgentAuthProbeState.signedIn,
            AgentSignInPhase.signedOut => AgentAuthProbeState.signedOut,
            _ => AgentAuthProbeState.error,
          },
        );
      }
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.probeUnsupported,
      );
    } on TimeoutException {
      return const AgentAuthProbeResult.failed(AgentAuthProbeError.timedOut);
    } catch (_) {
      return const AgentAuthProbeResult.failed(
        AgentAuthProbeError.hostUnavailable,
      );
    }
  }

  /// A row still "Checking sign-in…" is read again a few seconds later, so
  /// it reaches a final state without the person reopening the sheet.
  Timer? _paSignInRecheck;

  void _paScheduleSignInRecheck(List<AgentRow> rows) {
    final checking = rows.any(
      (row) =>
          row.status == PhoneAgentStatus.unavailable &&
          row.hiddenReason == PhoneAgentHiddenReason.runtimeUnknown,
    );
    if (!checking || _paSignInRecheck != null || _self._disposed) return;
    _paSignInRecheck = Timer(const Duration(seconds: 5), () {
      _paSignInRecheck = null;
      if (!_self._disposed) unawaited(refreshAgentRows().catchError((_) {}));
    });
  }

  @override
  List<PhoneAgentStatusLine> get agentStatusLines => List.unmodifiable([
    for (final row in agentRows)
      if (row.status == PhoneAgentStatus.limitReached ||
          row.status == PhoneAgentStatus.signedOut ||
          (row.status == PhoneAgentStatus.stoppedInBackground &&
              !_paAutoResuming))
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
  Future<void> installAgent(String agentId) => _paInstallAgent(agentId);

  @override
  String? get removingAgentId => _paRemovingAgent;
  @override
  bool canRemoveAgent(String agentId) => _paCanRemoveAgent(agentId);
  @override
  Future<AgentRemovalResult> removeAgent(String agentId) =>
      _paRemoveAgent(agentId);

  @override
  Future<void> cancelAgentInstall() async {
    final host = _paHost;
    if (host == null) return;
    await host.cancelInstall();
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  Future<AgentPhoneCheckResult> runAgentPhoneCheck(String agentId) =>
      _self._runAgentPhoneCheck(agentId);

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
  AgentSignInState? agentSignInState(String agentId) {
    final descriptor = _paCatalog.byId(agentId);
    if (descriptor == null) return null;
    final interactive = _paSignIns[agentId]?.state;
    if (interactive?.phase == AgentSignInPhase.urlReady ||
        interactive?.phase == AgentSignInPhase.awaitingCode) {
      return interactive;
    }
    final result = _paAuthResults[agentId];
    return AgentSignInState(
      phase: result == null ? AgentSignInPhase.signedOut : _paAuthPhase(result),
      method: descriptor.signInMethod,
      inspected: result != null,
    );
  }

  /// Explicit CLI status only; account labels are transient and never logged.
  @override
  AgentAuthProbeResult? agentAccount(String agentId) => _paAuthResults[agentId];

  @override
  String? get agentSignInProfileId =>
      phoneAgentsAvailable ? (_paHostProfile ?? _paProfile?.id) : null;

  @override
  Future<void> recheckAgentSignIn(String agentId) async {
    final host = _paEnsureHost();
    final owner = _paHostProfile;
    final result = await _paProbeSignIn(host, agentId);
    if (_self._disposed || _paHost != host || _paHostProfile != owner) return;
    _paAuthResults[agentId] = result;
    await refreshAgentRows();
    if (!_self._disposed) _self._notifyListeners();
  }

  /// A terminal exit, including zero, is never authentication proof.
  @override
  Future<bool> confirmAgentSignIn(String agentId) async {
    await recheckAgentSignIn(agentId);
    return _paAuthResults[agentId]?.state == AgentAuthProbeState.signedIn;
  }

  @override
  bool canSignOutAgent(String agentId) {
    final host = _paHost;
    return host is PhoneAgentAuthPort &&
        (host as PhoneAgentAuthPort).supportsSignOut(agentId);
  }

  @override
  Future<void> signOutAgent(String agentId) async {
    final host = _paEnsureHost();
    if (host is! PhoneAgentAuthPort ||
        !(host as PhoneAgentAuthPort).supportsSignOut(agentId)) {
      throw const ProductException(
        'Sign out is not available for this agent yet. Use its terminal to sign out.',
      );
    }
    final owner = _paHostProfile;
    _paAuthRevisions[agentId] = (_paAuthRevisions[agentId] ?? 0) + 1;
    final result = await (host as PhoneAgentAuthPort).signOut(agentId);
    if (_self._disposed || _paHost != host || _paHostProfile != owner) return;
    _paAuthResults[agentId] = result;
    if (result.state != AgentAuthProbeState.signedOut) {
      _self._notifyListeners();
      throw const ProductException(
        'Sign out could not be confirmed. Check the agent in its terminal and try again.',
      );
    }
    await refreshAgentRows();
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
    // Switching the OpenCode protocol changes its last-used project, but
    // these conversations still belong to the same Ubuntu agent home.
    for (final row in _paSavedRows) {
      if (ok(row.item.directory)) yield row.item.directory;
    }
    for (final directory in _paSources.keys) {
      if (ok(directory)) yield directory;
    }
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
    final helperVersion = _self._paObservedHelperVersion;
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
    // A helper that was just started takes a while to listen (longer on a
    // cold phone): the missing projects are tried every 3 s for half a
    // minute, then every 15 s for two more, so their conversations appear.
    // One retry waits at a time, however many reads missed.
    if (missed && _paSyncRetries < 18 && _paSyncTimer == null) {
      _paSyncRetries++;
      final wait = Duration(seconds: _paSyncRetries <= 10 ? 3 : 15);
      _paSyncTimer = Timer(wait, () {
        _paSyncTimer = null;
        if (!_self._disposed) unawaited(_paSyncSources().catchError((_) {}));
      });
    } else if (!missed) {
      _paSyncRetries = 0;
    }
    // Retry syncs run without a row scan. Publish newly observed proof too.
    if (!_self._disposed &&
        _paHost == host &&
        _paRefreshingRows == null &&
        _self._paObservedHelperVersion != helperVersion) {
      await refreshAgentRows();
    }
  }

  int _paSyncRetries = 0;
  Timer? _paSyncTimer;

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
    _paWireBrowserGateway(gateway, directory);
    final folder = directory.split('/').where((p) => p.isNotEmpty).last;
    final source = _self._genUiPhoneFeed(gateway, directory, folder);
    _paSources[directory] = (gateway: gateway, source: source);
    return source;
  }

  Future<void> _paDropSource(String directory) async {
    _self._genUiDropPhone(directory);
    final entry = _paSources.remove(directory);
    if (entry == null) return;
    await entry.gateway.revokeBrowserClaudeLaunches();
    await entry.source.dispose();
    entry.gateway.close();
  }

  void _paRebuildMerged() {
    final old = _paMerged;
    unawaited(_paMergedSub?.cancel());
    _paMergedSub = null;
    _paMerged = null;
    if (old != null) unawaited(old.dispose());
    final sides = _self._sideFeedSources;
    if (_paSources.isEmpty && sides.isEmpty) {
      if (!_self._disposed) _self._notifyListeners();
      return;
    }
    final merged = MergedChatFeed(
      defaultSourceId: _openCodeSourceId,
      sources: [
        NamedChatFeedSource(
          id: _openCodeSourceId,
          // Beside other servers, its rows name this one.
          label: sides.isEmpty
              ? 'OpenCode'
              : (_self.profile?.name ?? 'OpenCode'),
          source: _OpenCodeFeed(_self),
        ),
        ...sides,
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
          (item) => item.sourceId?.startsWith('paseo:') ?? false,
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

  /// The saved list outlives the feed gateway and its private draft aliases.
  /// Keep live row identities intact, but persist only the daemon's identity.
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
    );
  }

  // ---- one paint ------------------------------------------------------------

  /// How long the conversations list waits for agents on this phone before
  /// it shows OpenCode's conversations alone.
  static const _paFeedHold = Duration(seconds: 4);
  bool _paFeedSettled = false;
  DateTime? _paHoldUntil;
  Timer? _paHoldTimer;

  bool get _paUsedBefore => _paCache.usedBefore(_paProfile?.id);

  /// How long the list says the agents are loading before it stops saying
  /// so; their saved rows stay.
  static const _paReadingFor = Duration(seconds: 30);
  DateTime? _paReadingSince;
  Timer? _paReadingTimer;

  /// While the agents' first read is outstanding: their folders are read
  /// again every 5 s (the helper may still be starting), and after
  /// [_paReadingFor] the list stops saying it is loading.
  bool _paStillReading() {
    final now = clock.now();
    final since = _paReadingSince ??= now;
    if (now.difference(since) >= _paReadingFor) {
      _paFeedSettled = true;
      _paReadingTimer?.cancel();
      _paReadingTimer = null;
      return false;
    }
    _paReadingTimer ??= Timer(const Duration(seconds: 5), () {
      _paReadingTimer = null;
      if (_self._disposed || _paFeedSettled) return;
      unawaited(
        refreshAgentRows().catchError((Object _) {}).whenComplete(() {
          if (!_self._disposed) _self._notifyListeners();
        }),
      );
    });
    return true;
  }

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
    final agentsShown = _self._agentsShown;
    final shown = {for (final item in snapshot.items) item.identity};
    final saved = [
      if (agentsShown)
        for (final row in _paSavedRows)
          if (!_paLoadedDirs.contains(row.item.directory) &&
              !shown.contains(row.item.identity) &&
              chatFeedMatches(row.item, filter))
            row.item,
      // Other servers' rows from last time, while they connect.
      for (final item in _self._sideSavedRows)
        if (!shown.contains(item.identity) && chatFeedMatches(item, filter))
          item,
    ];
    final backend = _paBackendLive;
    final items = List<ChatFeedItem>.unmodifiable(
      <ChatFeedItem>[
        for (final item in snapshot.items)
          if (agentsShown || !_isAgentRow(item)) _agentRowSeen(item, backend),
        ...saved,
      ]..sort(compareChatFeedItems),
    );
    // Sources still being read while other rows already show, said in one
    // quiet line: the agents on their first read this run (their saved rows
    // stand in), the other servers while they connect, and this one while
    // only others' rows show.
    final agents = <String>{
      if (agentsShown &&
          !_paFeedSettled &&
          phoneAgentsAvailable &&
          _paUsedBefore) ...{
        for (final row in _paSavedRows)
          if (!_paLoadedDirs.contains(row.item.directory))
            row.item.agentLabel ?? 'Claude Code',
      },
    };
    if (agentsShown &&
        !_paFeedSettled &&
        phoneAgentsAvailable &&
        _paUsedBefore &&
        agents.isEmpty) {
      agents.add('Claude Code');
    }
    if (agents.isNotEmpty && !_paStillReading()) agents.clear();
    final mainLoading = snapshot.loading && items.isNotEmpty;
    final mainId = _self.profile?.id;
    final servers = <String>{
      if (mainLoading && _self._sides.isNotEmpty && mainId != null) mainId,
      ..._self._sidesLoading,
    };
    final reading = <String>{
      if (mainLoading && (_self._sides.isEmpty || mainId == null)) 'OpenCode',
      ...agents,
    };
    return ChatFeedSnapshot(
      items: items,
      stillLoading: List.unmodifiable(reading),
      stillLoadingServers: List.unmodifiable(servers),
      unreachableServers: List.unmodifiable(_self._sidesUnreachable),
      // OpenCode answers for every project; a scoped phone source never makes
      // the whole list look single-project.
      acrossProjects: _self._ocAcross,
      loading: snapshot.loading && items.isEmpty,
      // Saved rows stand in while their folder loads; they are no failure.
      // A shown server that can't be reached is.
      complete: snapshot.complete && !_self._sidesFailed,
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
    // The list says the agents are still loading (the helper was starting):
    // a pull to refresh reads their folders too.
    // So does one after that read when a folder was never reached.
    if (phoneAgentsAvailable &&
        (_paFeedSettled ? _paSourcesMissing : _paReadingSince != null)) {
      try {
        await refreshAgentRows();
      } catch (_) {}
    }
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
    if (_paRemovingAgent != null) {
      throw _PhoneAgentRoutes._removalBusy;
    }
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
    final ids = {
      for (final d in _paSources.keys) _paseoSourceId(d),
      for (final side in _self._sideFeedSources) side.id,
    };
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
  Future<ChatFeedRoute> openChatFeedItem(ChatFeedItem item) =>
      _paOpenChatFeedItem(item);

  @override
  AgentResumeNotice agentResumeNotice(ChatFeedItem item) =>
      _paAgentResumeNotice(item);

  @override
  Future<String> startNewChatReplacing(
    ChatFeedItem old, {
    required bool newChatAcknowledged,
  }) => _paStartNewChatReplacing(old, newChatAcknowledged: newChatAcknowledged);

  void _paWireBrowserGateway(PaseoGateway gateway, String directory) {
    final owner = _paProfile?.id;
    if (owner == null) return;
    gateway.configureBrowserClaudeLaunch(
      registry: _self._browserLaunches,
      profileId: owner,
      sourceId: _paseoSourceId(directory),
      sourceIdForDirectory: _paseoSourceId,
    );
  }

  // ---- closing ------------------------------------------------------------

  Future<void> _paCloseAll({required bool stopHost}) => _paCloseOwned(
    _paHostProfile ?? _paForegroundBinding?.profileId ?? _paProfile?.id,
    stopHost: stopHost,
  );

  /// Deletion hook: runs before ProfileStore removes the profile.
  Future<void> _paCloseForDeletion(String profileId) async {
    if (_self.store.phoneAgentOwnerRetainedAfterRemoving(profileId)) return;
    profileId = _self.store.phoneAgentOwnerId(profileId);
    if (_paHostProfile != profileId &&
        _paProfile?.id != profileId &&
        _paSignIns.isEmpty &&
        !_paClosingOwners.containsKey(profileId)) {
      return;
    }
    await _paCloseOwned(profileId, stopHost: true);
    if (!_self._disposed) _self._notifyListeners();
  }

  @override
  Future<void> closePhoneAgentsForSignInReset() async {
    await _paCloseAll(stopHost: true);
    _paNeedRestart = true;
    if (!_self._disposed) _self._notifyListeners();
  }

  /// Disposal drains captured terminals; the Android service owns the host.
  void _paShutdown() => _paShutdownOwned();
}
