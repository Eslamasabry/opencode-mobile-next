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
      _quietReconnectToken != null &&
      _quietReconnectOwner == (_self._connectedProfile ?? profile)?.id &&
      _quietReconnectAttempt == connectionAttemptRevision &&
      !passwordRejected &&
      !((_self._connectedProfile ?? profile)?.requiresPasswordReentry ??
          false) &&
      !((_self._connectedProfile ?? profile)?.requiresCodexTokenReentry ??
          false);

  /// Read-only BD13 evidence, retained across recovery within this profile.
  Future<AgentHelperStatus?> readConnectionHelperExit() =>
      _self._readConnectionHelperExit();

  Timer? _connectionStatusTimer;
  String? _connectionStatusOwner;
  int? _connectionStatusAttempt;
  DateTime? _connectionStatusSince;
  bool _connectionStatusExpired = false;

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
      _self._connectedProfile?.flavor ?? profile?.flavor ?? ServerFlavor.v1;

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
    if (_disposed ||
        isIsolated ||
        owner == null ||
        status == StreamStatus.connected) {
      _resetConnectionStatusClock();
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
        : !connectionLoading || _connectionStatusExpired
        ? ConnectionStatusPhase.notAnswering
        : status == StreamStatus.connecting
        ? ConnectionStatusPhase.connecting
        : ConnectionStatusPhase.reconnecting;
    return ConnectionStatusSnapshot(
      phase: phase,
      quiet: defersTurnInterruption,
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
                _ => ServerCapabilities.allV1,
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
