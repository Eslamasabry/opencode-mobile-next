part of '../connection.dart';

// Connection status, server version and the active profile and its capabilities.

/// [ConnectionController]'s connection status and active profile.
mixin _ConnectionControllerStatus on ChangeNotifier {
  ConnectionController get _self;

  StreamStatus _status = StreamStatus.disconnected;

  Timer? _quietReconnectTimer;
  Object? _quietReconnectToken;
  String? _quietReconnectOwner;
  int? _quietReconnectAttempt;
  AgentHelperStatus? _lastConnectionHelperExit;
  String? _lastConnectionHelperOwner;

  /// A bounded presentation grace, never a claim that the link is reachable.
  bool get defersTurnInterruption =>
      _self._localServerBusyQuiet ||
      _quietReconnectToken != null &&
          _quietReconnectOwner == (_self._connectedProfile ?? profile)?.id &&
          _quietReconnectAttempt == connectionAttemptRevision &&
          !passwordRejected &&
          !((_self._connectedProfile ?? profile)?.requiresPasswordReentry ??
              false) &&
          !((_self._connectedProfile ?? profile)?.requiresCodexTokenReentry ??
              false);

  /// The quiet start of a return from the background
  /// ([resumeQuietGrace]): the link is being rebuilt, and saying so for the
  /// second or two it usually takes was a row on every return (owner,
  /// 2026-10-27). Null outside it.
  DateTime? _resumeQuietUntil;
  Timer? _resumeQuietTimer;

  /// How long a return from the background reconnects without a line.
  @visibleForTesting
  Duration resumeQuietGrace = const Duration(seconds: 4);

  /// An agent's command (a 15-minute test run, an APK build) was running
  /// when its link went down, from this moment: a helper too busy to
  /// answer its health check in time is loaded, not gone, for
  /// [agentCommandHoldOff].
  DateTime? _agentCommandSince;
  Timer? _agentCommandTimer;

  /// As long as a command may run: Claude Code's longest command timeout.
  @visibleForTesting
  Duration agentCommandHoldOff = const Duration(minutes: 10);

  /// Set when [agentCommandHoldOff] ran out for the current hold.
  bool _agentCommandHoldSpent = false;

  bool get _agentCommandHolds =>
      _agentCommandSince != null && !_agentCommandHoldSpent;

  /// Read-only BD13 evidence, retained across recovery within this profile.
  Future<AgentHelperStatus?> readConnectionHelperExit() =>
      _self._readConnectionHelperExit();

  Timer? _connectionStatusTimer;
  String? _connectionStatusOwner;
  int? _connectionStatusAttempt;
  DateTime? _connectionStatusSince;
  bool _connectionStatusExpired = false;

  /// The phone's own server process, as the native side sees it, while the
  /// link to it is down: alive but not answering is busy (a heavy build loads
  /// the phone), not dead. Polled only while disconnected.
  Timer? _localAliveTimer;
  bool _localServerAlive = false;
  DateTime? _localBusySince;

  /// Test seam for the native liveness read.
  @visibleForTesting
  Future<bool> Function()? localServerAliveProbe;

  /// Alive but not answering, within the quiet period.
  bool get localServerBusy =>
      _localServerAlive && _self.status != StreamStatus.connected;

  @visibleForTesting
  Duration localBusyLongAfter = const Duration(minutes: 2);

  /// Longer than two minutes with a command running: worth one neutral line.
  bool get localServerBusyLong =>
      localServerBusy &&
      _hasRunningTool &&
      _localBusySince != null &&
      DateTime.now().difference(_localBusySince!) >= localBusyLongAfter;

  bool get _localServerBusyQuiet => localServerBusy && !localServerBusyLong;

  /// An agent backend's helper, asked over its own health check while this
  /// link is down (set by the connection that owns the helper). The link's
  /// own failures are no evidence the helper stopped answering: a long tool
  /// call or a slow list read leave it healthy.
  Future<bool> Function()? _agentDaemonProbe;
  Timer? _agentDaemonTimer;
  bool _agentDaemonProbing = false;
  bool? _agentDaemonAnswers;

  /// The helper's last health answer while this link is down: null when it
  /// is not being asked (connected, not an agent backend) or not answered
  /// yet. Read by Details.
  bool? get agentDaemonAnswers =>
      _agentDaemonTimer == null ? null : _agentDaemonAnswers;

  /// What the agent link's last recovery could not finish, in fixed words
  /// (never daemon text), for Details; null when nothing failed.
  String? get agentLinkFailureDetails {
    final gateway = _self.api;
    final failure = gateway is PaseoGateway
        ? gateway.lastRecoveryFailure
        : null;
    if (failure == null) return null;
    if (failure is PaseoFailure) {
      final reason = failure.reason;
      return '${failure.errorTag}: ${failure.message}'
          '${reason == null ? '' : ' ($reason)'}';
    }
    return failure.runtimeType.toString();
  }

  /// A command, sub-agent or other tool of any turn is still running.
  bool get hasRunningTool => _hasRunningTool;
  bool get _hasRunningTool =>
      _self._turnStallTools.values.any((s) => s.isNotEmpty);

  /// Consumers localize this snapshot; they never infer their own grace period
  /// or promote a disconnected transport to a healthy status.
  ConnectionStatusSnapshot get connectionStatus => _self._connectionStatus;

  String? version;
  bool _transportReady = false;

  /// A healthy gateway is usable even when its server omits version metadata.
  /// Keep it available through SSE reconnects; retiring the gateway resets it.
  bool get hasConnectedServer =>
      _self.api != null &&
      _self.repository != null &&
      (_transportReady || isConnected);
  String? availableServerVersion;
  String? installedServerVersion;
  String? lastError;

  /// What made [lastError] when it came from a failure, for Details; the
  /// words shown to people come from mapping this, never from its text.
  Object? lastFailure;

  /// True after the connected v2 server answered 401 mid-session — the serve
  /// password rotated (it changes on every restart unless OPENCODE_PASSWORD
  /// is set). Basic auth cannot self-heal, so retry loops stay off and the
  /// connection banner offers "Update password" instead (never a modal).
  /// Cleared when a new connect starts, on disconnect, and when the stream
  /// recovers.
  bool passwordRejected = false;

  int connectionRevision = 0;

  /// Identifies the user/lifecycle operation owning a connection bootstrap.
  /// Unlike transport generations, this survives internal flavor correction
  /// and restoration of a saved location within the same connect call.
  int connectionAttemptRevision = 0;

  /// Advances whenever a usable transport is ready and screen-owned data
  /// should be rehydrated. This also advances after an SSE reconnection so
  /// events missed during a network handoff are reconciled from REST.
  int dataRefreshRevision = 0;

  /// Initial connection or a bounded phone-helper launch, not an outage.
  bool get connectionStarting =>
      _self.status == StreamStatus.connecting ||
      (_self.isAgentBackend && _self._genUiParent?.phoneAgentStarting == true);

  bool get connectionLoading =>
      _self.status == StreamStatus.connecting ||
      _self.status == StreamStatus.reconnecting;

  /// The connection's last problem in words for people: the failure that
  /// made it is never quoted (its text stays in [lastFailure] for Details).
  String? get connectionError {
    final line = lastError;
    final failure = lastFailure;
    if (line == null || failure == null) return line;
    final known = ProductFailure.from(failure);
    return known.category == ProductFailureCategory.words &&
            known.authoredMessage != null
        ? known.authoredMessage
        : 'The server is not answering. Try again shortly.';
  }

  bool get pollingFallbackEnabled => _self._poll?.isActive ?? false;
  bool get shouldPoll =>
      _self.api != null && _self.status != StreamStatus.connected;

  bool get isConnected =>
      _self.status == StreamStatus.connected && _self.api != null;

  final _profileDataChanges = ChangeNotifier();

  /// Notifies read-only, profile-scoped companions before local deletion or
  /// controller disposal can race their in-flight requests. Unlike the main
  /// notifier this does not publish widget snapshots or connection state.
  Listenable get profileDataChanges => _profileDataChanges;

  bool isProfileReadable(String id) =>
      !_self._disposed && id.isNotEmpty && _self._readProfileAvailable(id);

  ServerProfile? get profile => _self._profile;

  /// The protocol the live transport speaks. Reads the profile the connection
  /// actually opened, not the selected one, so a switch mid-connection cannot
  /// make screens describe the wrong server.
  ServerFlavor get serverFlavor =>
      _self._connectedProfile?.flavor ?? profile?.flavor ?? ServerFlavor.v2;

  /// Feature switches for the live transport. Screens gate on these — never on
  /// [serverFlavor], which is only ever copy ("OpenCode 2 servers"). Before a
  /// connection exists a Codex profile retains its restricted capability set;
  /// attaching the live gateway supplies the authoritative set.
  ServerCapabilities get capabilities => _self._capabilities;

  bool get usesConnectionToken =>
      (_self._connectedProfile ?? profile)?.usesAgentSocket ?? false;

  void recordServerUpgradeInstalled(String rawVersion) {
    final installed = rawVersion.trim();
    if (!isExactServerVersion(installed)) return;
    installedServerVersion = installed;
    if (availableServerVersion == installed) availableServerVersion = null;
    notifyListeners();
  }
}

extension _ConnectionControllerStatusImpl on ConnectionController {
  /// One eight-second grace period per attempt, independent of route lifetime.
  /// Stream reconnect churn preserves the period; an explicit retry resets it.
  void _syncConnectionStatusClock() {
    final owner = _connectedProfile ?? profile;
    _syncLocalLiveness(owner);
    _syncAgentDaemonWatch(owner);
    _syncAgentCommandHold(owner);
    if (_disposed ||
        isIsolated ||
        owner == null ||
        status == StreamStatus.connected) {
      _resetConnectionStatusClock();
      _endResumeQuiet();
      return;
    }
    if (_connectionStatusOwner != owner.id ||
        _connectionStatusAttempt != connectionAttemptRevision) {
      _resetConnectionStatusClock();
      _connectionStatusOwner = owner.id;
      _connectionStatusAttempt = connectionAttemptRevision;
    }
    if (!connectionLoading || _connectionStatusSince != null) return;
    _connectionStatusSince = DateTime.now();
    _connectionStatusTimer = Timer(const Duration(seconds: 8), () {
      _connectionStatusTimer = null;
      if (_disposed) return;
      _connectionStatusExpired = true;
      _notifyListeners();
    });
  }

  /// Starts [resumeQuietGrace] for a return from the background.
  void _beginResumeQuiet() {
    _endResumeQuiet();
    if (_disposed || isIsolated || status == StreamStatus.connected) return;
    _resumeQuietUntil = DateTime.now().add(resumeQuietGrace);
    _resumeQuietTimer = Timer(resumeQuietGrace, () {
      _resumeQuietTimer = null;
      _resumeQuietUntil = null;
      if (!_disposed) _notifyListeners();
    });
  }

  void _endResumeQuiet() {
    _resumeQuietTimer?.cancel();
    _resumeQuietTimer = null;
    _resumeQuietUntil = null;
  }

  /// Cleared by its own timer when the grace runs out.
  bool get _resumeQuiet => _resumeQuietUntil != null;

  /// Keeps [_agentCommandSince] while an agent's link is down with one of
  /// its commands running, and wakes the line once the hold runs out.
  void _syncAgentCommandHold(ServerProfile? owner) {
    final holds =
        !_disposed &&
        !isIsolated &&
        isAgentBackend &&
        owner != null &&
        status != StreamStatus.connected &&
        _hasRunningTool;
    if (!holds) {
      _agentCommandTimer?.cancel();
      _agentCommandTimer = null;
      _agentCommandSince = null;
      _agentCommandHoldSpent = false;
      return;
    }
    if (_agentCommandSince != null) return;
    _agentCommandSince = DateTime.now();
    _agentCommandTimer = Timer(agentCommandHoldOff, () {
      _agentCommandTimer = null;
      _agentCommandHoldSpent = true;
      if (!_disposed) _notifyListeners();
    });
  }

  void _syncLocalLiveness(ServerProfile? owner) {
    final probe = localServerAliveProbe;
    final wanted =
        !_disposed &&
        !isIsolated &&
        owner != null &&
        status != StreamStatus.connected &&
        (probe != null ||
            (BuiltinLinux.supported && looksLikeInAppServer(owner)));
    if (!wanted) {
      _localAliveTimer?.cancel();
      _localAliveTimer = null;
      _localServerAlive = false;
      _localBusySince = null;
      return;
    }
    if (_localAliveTimer != null) return;
    _localAliveTimer = Timer.periodic(
      const Duration(seconds: 10),
      (_) => unawaited(_probeLocalAlive()),
    );
    unawaited(_probeLocalAlive());
  }

  /// While an agent backend's link is down, its helper's health check is
  /// asked every five seconds. Its answer decides the line ("Reconnecting…"
  /// or "isn't answering" with Restart), and a helper that answers gets a
  /// fresh link without a tap.
  void _syncAgentDaemonWatch(ServerProfile? owner) {
    final wanted =
        !_disposed &&
        !isIsolated &&
        isAgentBackend &&
        _agentDaemonProbe != null &&
        owner != null &&
        status != StreamStatus.connected &&
        !_lifecycleSuspended;
    if (!wanted) {
      _agentDaemonTimer?.cancel();
      _agentDaemonTimer = null;
      _agentDaemonAnswers = null;
      return;
    }
    if (_agentDaemonTimer != null) return;
    _agentDaemonTimer = Timer.periodic(
      const Duration(seconds: 5),
      (_) => unawaited(_probeAgentDaemon()),
    );
    unawaited(_probeAgentDaemon());
  }

  Future<void> _probeAgentDaemon() async {
    final probe = _agentDaemonProbe;
    if (probe == null || _agentDaemonProbing) return;
    _agentDaemonProbing = true;
    var answers = false;
    try {
      answers = await probe().timeout(const Duration(seconds: 4));
    } catch (_) {
      // No answer in time is not answering.
    } finally {
      _agentDaemonProbing = false;
    }
    if (_disposed || _agentDaemonTimer == null) return;
    final was = _agentDaemonAnswers;
    _agentDaemonAnswers = answers;
    if (answers) _reconnectAgentLink();
    if (was != answers) _notifyListeners();
  }

  /// The helper answers but this link is down and not coming back by
  /// itself: no transport, a failed attempt, or one past its grace. A fresh
  /// link is built, the way Try again would; one at a time.
  void _reconnectAgentLink() {
    if (_disposed ||
        isConnected ||
        _lifecycleSuspended ||
        _manualReconnect != null ||
        _lifecycleResume != null ||
        !automationPolicy.allows(AutomationBehavior.reconnect)) {
      return;
    }
    final stalled =
        api == null ||
        status == StreamStatus.disconnected ||
        _connectionStatusExpired;
    if (!stalled) return;
    unawaited(_retryConnection(byPerson: false).catchError((Object _) {}));
  }

  Future<void> _probeLocalAlive() async {
    var alive = false;
    try {
      alive =
          await (localServerAliveProbe ??
              () async => (await BuiltinLinux().status()).serverRunning)();
    } catch (_) {}
    if (_disposed || _localAliveTimer == null) return;
    final wasLong = localServerBusyLong;
    final was = _localServerAlive;
    _localServerAlive = alive;
    if (!alive) {
      _localBusySince = null;
    } else {
      _localBusySince ??= DateTime.now();
    }
    if (was != alive || wasLong != localServerBusyLong) _notifyListeners();
  }

  void _resetConnectionStatusClock() {
    _connectionStatusTimer?.cancel();
    _connectionStatusTimer = null;
    _connectionStatusOwner = null;
    _connectionStatusAttempt = null;
    _connectionStatusSince = null;
    _connectionStatusExpired = false;
  }

  /// The body of [connectionStatus].
  ConnectionStatusSnapshot get _connectionStatus {
    final owner = _connectedProfile ?? profile;
    final phase = isIsolated || owner == null
        ? ConnectionStatusPhase.hidden
        // In the background the app retired its link on purpose: nothing is
        // down, and a line (or a note built from it) would greet the person
        // on their return (owner, 2026-10-27).
        : _appBackgrounded && status != StreamStatus.connected
        ? ConnectionStatusPhase.hidden
        // Nothing was tried: the saved secret could not be read back from
        // the phone's secure storage, so only entering it again helps.
        : (owner.usesAgentSocket
              ? owner.requiresCodexTokenReentry
              : owner.requiresPasswordReentry)
        ? ConnectionStatusPhase.credentialsUnreadable
        : passwordRejected
        ? ConnectionStatusPhase.credentialsRequired
        : status == StreamStatus.connected
        ? ConnectionStatusPhase.connected
        : localServerBusyLong
        ? ConnectionStatusPhase.serverBusy
        // An agent's helper that answers its health check (or is still being
        // asked) is not "not answering": this link is being rebuilt. Nor is
        // one too loaded to answer in time while its command runs (a build
        // on this phone), for as long as a command may run.
        : _agentDaemonTimer != null &&
              (_agentDaemonAnswers != false || _agentCommandHolds)
        ? (status == StreamStatus.connecting
              ? ConnectionStatusPhase.connecting
              : ConnectionStatusPhase.reconnecting)
        : !connectionLoading || _connectionStatusExpired
        ? ConnectionStatusPhase.notAnswering
        : status == StreamStatus.connecting
        ? ConnectionStatusPhase.connecting
        : ConnectionStatusPhase.reconnecting;
    // The channel's own retry timer, while it is not connected and the
    // person lets the app reconnect by itself: never a guess.
    final events = _events;
    final nextAttemptAt =
        status != StreamStatus.connected &&
            events is RetryingEventChannel &&
            automationPolicy.allows(AutomationBehavior.reconnect)
        ? (events as RetryingEventChannel).nextAttempt.value
        : null;
    // The first seconds of a return from the background rebuild the link
    // without a line; a password or token problem still speaks at once.
    final resumeQuiet =
        _resumeQuiet &&
        const {
          ConnectionStatusPhase.connecting,
          ConnectionStatusPhase.reconnecting,
          ConnectionStatusPhase.notAnswering,
        }.contains(phase);
    return ConnectionStatusSnapshot(
      phase: phase,
      nextAttemptAt: nextAttemptAt,
      quiet: defersTurnInterruption || resumeQuiet,
      profileId: owner?.id,
      serverName: owner?.name ?? '',
      since: _connectionStatusSince,
      usesToken: owner?.usesAgentSocket ?? false,
      retrying: manualReconnectInProgress,
      attemptRevision: connectionAttemptRevision,
    );
  }

  /// The body of [profile].
  ServerProfile? get _profile {
    // A conversation backend's profile is its own, never saved or active.
    if (_isSecondary) return _connectedProfile;
    final id = store.activeId;
    if (id == null) return null;
    for (final p in store.profiles) {
      if (p.id == id) return p;
    }
    return null;
  }

  /// The body of [capabilities].
  ServerCapabilities get _capabilities =>
      (api?.capabilities ??
              switch ((_connectedProfile ?? profile)?.backend) {
                ServerBackend.codex => codexServerCapabilities,
                ServerBackend.paseo => paseoServerCapabilities,
                _ => api2ServerCapabilities,
              })
          .withGenUi(_genUiEffective);

  void _acceptRunningServerVersion(String? rawVersion) {
    final next = rawVersion?.trim() ?? '';
    if (next.isEmpty) return;
    version = next;
    if (availableServerVersion == next) availableServerVersion = null;
    if (installedServerVersion == next) installedServerVersion = null;
  }
}
