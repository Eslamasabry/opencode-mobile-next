part of '../connection.dart';

// Transport construction, connect and disconnect, and the managed runtime flavor.

bool _isLoopbackUrl(String value) {
  final uri = Uri.tryParse(value);
  // One shared predicate, so this cannot drift from what the URL
  // normalizer and the profile validator consider local.
  return uri != null && isLoopbackHost(uri.host);
}

/// Production wiring for an OpenCode 2 profile: one Basic-auth transport
/// and client shared by both gateway halves. Direct OC2 uses `opencode`;
/// an authenticated BYO tunnel uses its per-phone identity at the proxy.
/// The profile's password rides every request in either case.
({ServerGateway gateway, ServerOperationsGateway operations})
_createV2GatewayPair(ServerProfile profile) {
  final client = Api2Client.connect(
    baseUrl: profile.baseUrl,
    password: profile.password,
    username: profile.transientTransport ? profile.username : 'opencode',
    cleartextConfirmedOrigin: profile.cleartextConfirmedOrigin,
    certificatePinSha256:
        profile.certificatePinOrigin == cleartextOriginOf(profile.baseUrl)
        ? profile.certificatePinSha256
        : null,
  );
  return (
    gateway: Api2Gateway(client: client),
    operations: Api2OperationsGateway(client: client),
  );
}

({ServerGateway gateway, ServerOperationsGateway operations})
_createCodexGatewayPair(ServerProfile profile) {
  final gateway = CodexGateway.connect(
    baseUrl: profile.baseUrl,
    token: profile.codexToken,
    directory: profile.codexDirectory,
  );
  return (gateway: gateway, operations: gateway);
}

({ServerGateway gateway, ServerOperationsGateway operations})
_createPaseoGatewayPair(ServerProfile profile) {
  final gateway = PaseoGateway.connect(
    baseUrl: profile.baseUrl,
    password: profile.codexToken,
    directory: profile.codexDirectory,
  );
  return (gateway: gateway, operations: gateway);
}

LiveEventChannel _createEventStream({
  required ServerGateway api,
  required void Function(EventEnvelope event) onEvent,
  required void Function(StreamStatus status) onStatus,
  void Function(Object error)? onError,
}) => api.openEventChannel(
  onEvent: onEvent,
  onStatus: onStatus,
  onError: onError,
);

LiveEventChannel _createGlobalEventStream({
  required ServerGateway api,
  required void Function(EventEnvelope event) onEvent,
  required void Function(StreamStatus status) onStatus,
  void Function(Object error)? onError,
}) => api.openGlobalEventChannel(
  onEvent: onEvent,
  onStatus: onStatus,
  onError: onError,
);

bool _suggestsWrongFlavor(Object error) =>
    ConnectionController.suggestsWrongFlavor(error);

/// [ConnectionController]'s transport, connect and disconnect.
mixin _ConnectionControllerConnect on ChangeNotifier {
  ConnectionController get _self;

  ServerGateway? api;
  ServerOperationsGateway? repository;
  LiveEventChannel? _events;
  LiveEventChannel? _globalEvents;
  Timer? _poll;
  Future<void>? _busyStatusRefresh;
  ServerProfile? _connectedProfile;
  Future<void> _activeProfileWrite = Future.value();

  /// What screens show of the saved profiles when they last heard about
  /// them: ids, names, generations and addresses.
  late String _profilesShown;

  /// [redetectOnFailure] lets one failed connect re-probe the address and
  /// correct a stale cached [ServerProfile.flavor] (a server swapped between
  /// `opencode serve` generations) before giving up; the corrected retry runs
  /// with it false so detection can never loop.
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) => _self._connect(profile, redetectOnFailure: redetectOnFailure);

  /// Legacy managed data must be imported before any transport can run.
  bool get managedOpenCode2MigrationRequired {
    final owner = _connectedProfile ?? _self.profile;
    return owner != null &&
        (owner.requiresOpenCode2Migration || owner.openCode2HistoryPending) &&
        owner.backend == ServerBackend.openCode &&
        (TermuxBridge.managesServerUrl(owner.baseUrl) ||
            BuiltinLinux.managesServerUrl(owner.baseUrl));
  }

  bool get managedRuntimeMismatch =>
      _self.lastError == ConnectionController.managedRuntimeMismatchMessage;

  /// Confirms the copied history before the phone can leave upgrade mode.
  /// A failed/unknown response retains the durable pending marker.
  Future<bool> confirmManagedOpenCode2Migration({
    Duration timeout = const Duration(minutes: 3),
    Duration pollInterval = const Duration(seconds: 1),
  }) => _self._confirmManagedOpenCode2Migration(
    timeout: timeout,
    pollInterval: pollInterval,
  );

  /// Prepares the app-owned local server for an explicitly confirmed runtime
  /// switch. This checks work observed by this app, not server-wide idleness.
  /// Recovery must be durably disabled before retiring the local transport.
  /// Saved profiles, credentials, drafts and queued prompts remain intact.
  Future<void> prepareManagedRuntimeSwitch() =>
      _self._prepareManagedRuntimeSwitch();

  Future<void> disconnect({bool keepActive = false, bool silent = false}) =>
      _self._disconnect(keepActive: keepActive, silent: silent);

  /// Capabilities for setup, including profiles that are not connected yet.
  /// A live gateway is authoritative only for the exact selected profile.
  ServerCapabilities serverCapabilitiesFor(ServerProfile candidate) {
    // An OC2 health response does not prove the copied conversations finished
    // importing. Setup must not start AI Team before that durable proof.
    if (candidate.backend == ServerBackend.openCode &&
        (candidate.requiresOpenCode2Migration ||
            candidate.openCode2HistoryPending) &&
        (BuiltinLinux.managesServerUrl(candidate.baseUrl) ||
            TermuxBridge.managesServerUrl(candidate.baseUrl))) {
      return const ServerCapabilities();
    }
    if (_connectedProfile?.id == candidate.id && _self.api != null) {
      return _self.api!.capabilities;
    }
    if (candidate.backend != ServerBackend.openCode) {
      return const ServerCapabilities();
    }
    return api2ServerCapabilities;
  }

  /// Marks [profile] as the connected one without a transport, so tests can
  /// exercise profile-bound guards such as [openSessionInWorktree].
  @visibleForTesting
  void adoptConnectedProfileForTesting(ServerProfile profile) {
    _connectedProfile = profile;
  }

  /// Waits until the wake-time transport answers, then returns the API
  /// instance that foreground actions should use. The data reload that
  /// follows a wake is not awaited.
  ///
  /// Chat and other retained screens must not capture [api] before this
  /// future completes because a stale background transport may be replaced.
  Future<ServerGateway?> prepareActionTransport() =>
      _self._prepareActionTransport();

  /// Call after asynchronous preparation, immediately before starting work.
  /// A schedule-owned phone process must stay bounded to its own run.
  Future<void> ensureInteractiveSendAllowed({ServerGateway? expectedApi}) =>
      _self._ensureInteractiveSendAllowed(expectedApi: expectedApi);

  /// Returns the product repository paired with the wake-reconciled API.
  ///
  /// Retained screens must resolve this after [prepareActionTransport]
  /// completes because lifecycle recovery can replace both objects together.
  Future<ServerOperationsGateway?> prepareActionRepository() =>
      _self._prepareActionRepository();
}

extension _ConnectionControllerConnectImpl on ConnectionController {
  Future<void> _ensureInteractiveSendAllowed({
    ServerGateway? expectedApi,
  }) async {
    final transport = expectedApi ?? api;
    final owner = _connectedProfile ?? profile;
    bool current() =>
        !_disposed &&
        identical(api, transport) &&
        (_connectedProfile ?? profile)?.id == owner?.id &&
        (_connectedProfile ?? profile)?.baseUrl == owner?.baseUrl;
    if (!current()) {
      throw const ProductException(
        'The connection changed. Reopen the session and try again.',
      );
    }
    await _ensureScheduledPhoneSendAllowed(owner);
    if (!current()) {
      throw const ProductException(
        'The connection changed. Reopen the session and try again.',
      );
    }
  }

  Future<void> _ensureScheduledPhoneSendAllowed(ServerProfile? owner) async {
    if (!looksLikeInAppServer(owner)) return;
    final bool active;
    try {
      active = await const ScheduledPromptsPlatform().activePhoneLease();
    } catch (_) {
      throw ProductException(_shellStrings().schedulePhoneCheckFailed);
    }
    if (active) throw ProductException(_shellStrings().schedulePhoneBusy);
  }

  String _profilesSignature() => [
    for (final profile in store.profiles)
      '${profile.id}\u0000${profile.name}\u0000${profile.flavor.name}'
          '\u0000${profile.baseUrl}',
  ].join('\u0001');

  /// Asks Termux to hold its wake lock without waiting for the answer. The
  /// request round-trips through Termux's command service and can take up to
  /// its ten-second timeout; nothing the app does next depends on it, and
  /// awaiting it put that delay in front of every connect and every return
  /// to the app.
  void _ensureLocalServerWakeLock() {
    if (_disposed || !keepLiveInBackground) return;
    final profile = _connectedProfile;
    if (profile == null ||
        profile.usesAgentSocket ||
        !_isLoopbackUrl(profile.baseUrl) ||
        // The built-in Linux server lives in this app's own process, not in
        // Termux: a Termux wake lock would only launch Termux for nothing.
        BuiltinLinux.managesServerUrl(profile.baseUrl)) {
      return;
    }
    unawaited(
      Future<void>.sync(_localWakeLockEnsurer).catchError((Object _) {
        // The profile may point at a developer server rather than managed
        // Termux. Transport recovery must continue even when the bridge is
        // not installed or Android has revoked its command permission.
      }),
    );
  }

  /// Constructs one transport and operations pair for the selected backend.
  ({ServerGateway gateway, ServerOperationsGateway operations})
  _buildTransportPair(ServerProfile profile) {
    if (isIsolated) {
      throw StateError(
        'An isolated session cannot create a network transport.',
      );
    }
    if (profile.backend == ServerBackend.codex) {
      return _codexGatewayFactory(profile);
    }
    if (profile.backend == ServerBackend.paseo) {
      return _paseoGatewayFactory(profile);
    }
    final injected = _apiFactory;
    final ({ServerGateway gateway, ServerOperationsGateway operations}) pair;
    if (injected != null) {
      final gateway = injected(profile);
      pair = (
        gateway: gateway,
        operations:
            _repositoryFactory?.call(gateway) ??
            _v2GatewayFactory(profile).operations,
      );
    } else {
      pair = _v2GatewayFactory(profile);
    }
    final gateway = pair.gateway;
    if (gateway is Api2Gateway) {
      gateway.serverRunsTools = (id) =>
          identical(api, gateway) && _autoApprovalFor(id).automatic;
    }
    if (gateway is Api2Gateway &&
        BuiltinLinux.managesServerUrl(profile.baseUrl)) {
      gateway.beforeSessionDispatch = (id) =>
          _beforePhoneChatDispatch(profile, gateway, id);
      gateway.sessionDispatchSettled = (id) {
        if (identical(api, gateway)) _phoneChatDispatchSettled(id);
      };
    }
    return pair;
  }

  /// The body of [connect].
  Future<void> _connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) {
    return PerfTrace.span(
      'connect',
      () => _connectProfile(profile, redetectOnFailure: redetectOnFailure),
      attrs: {'backend': profile.backend.name, 'flavor': profile.flavor.name},
    ).whenComplete(() {
      // A server saved since the monitor last looked (a second agent on
      // this phone) is watched from now, and the one just left is read
      // fresh instead of on the next tick.
      if (_ownsProfileServices && !_disposed) profileMonitor.start();
      // The other connections on this phone join the list beside this one.
      _syncSideConnections();
    });
  }

  Future<void> _connectProfile(
    ServerProfile profile, {
    bool redetectOnFailure = true,
    bool preserveConnectionAttempt = false,
  }) async {
    if (_connectedProfile?.id != profile.id) _forgetTurnStalls();
    if (isIsolated) {
      throw StateError('An isolated session cannot connect to a server.');
    }
    if (profile.requiresOpenCode2Migration &&
        profile.backend == ServerBackend.openCode &&
        (TermuxBridge.managesServerUrl(profile.baseUrl) ||
            BuiltinLinux.managesServerUrl(profile.baseUrl))) {
      final generation = _beginGeneration(
        preserveConnectionAttempt: preserveConnectionAttempt,
      );
      _retireTransport();
      _clearLocationData();
      directory = null;
      workspace = null;
      locationRevision += 1;
      passwordRejected = false;
      _connectedProfile = profile;
      status = StreamStatus.disconnected;
      lastError = ConnectionController.managedRuntimeMismatchMessage;
      lastFailure = null;
      try {
        await _writeActiveProfile(generation, profile.id);
      } catch (_) {
        lastError =
            'The move to OpenCode 2 could not be prepared safely. '
            'Your existing conversations have not been changed. Try again.';
      }
      if (!_disposed && generation == _generation) _notifyListeners();
      return;
    }
    final previousProfile = _connectedProfile ?? this.profile;
    if (previousProfile != null &&
        store.phoneAgentOwnerId(previousProfile.id) !=
            store.phoneAgentOwnerId(profile.id)) {
      // Even a switch away and back revokes the old helper restoration.
      // Reconnecting the same owner (including a protocol alias) does not.
      ++_paIdleLifecycleEpoch;
    }
    _syncProfileServices();
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _lifecycleResume = null;
    _lifecycleTransportReady = null;
    // Codex and Paseo share the socket-style profile and its connect path.
    final isCodex = profile.usesAgentSocket;
    final validationError = profile.backend == ServerBackend.paseo
        ? (profile.requiresCodexTokenReentry
              ? 'The saved password is unavailable. Enter it again.'
              : validatePaseoServerUrl(profile.baseUrl) ??
                    validatePaseoPassword(profile.codexToken) ??
                    validateCodexProjectDirectory(profile.codexDirectory))
        : isCodex
        ? (profile.requiresCodexTokenReentry
              ? 'The saved connection token is unavailable. Enter it again.'
              : validateCodexServerUrl(profile.baseUrl) ??
                    validateCodexConnectionToken(profile.codexToken) ??
                    validateCodexProjectDirectory(profile.codexDirectory))
        : profile.cleartextUnconfirmed
        ? cleartextUnconfirmedMessage
        : validateServerProfileUrl(
            profile.baseUrl,
            username: profile.username,
            password: profile.password,
          );
    if (validationError != null) {
      _beginGeneration(preserveConnectionAttempt: preserveConnectionAttempt);
      _retireTransport();
      status = StreamStatus.disconnected;
      lastError = validationError;
      lastFailure = null;
      _notifyListeners();
      return;
    }
    final generation = _beginGeneration(
      preserveConnectionAttempt: preserveConnectionAttempt,
    );
    _retireTransport();
    // A new connection asks the server which dialect it speaks again.
    _v2Reset();
    // The folder edited in a Codex connection is authoritative on connect.
    // Restoring an older OpenCode-style selection would undo that user edit.
    final initialDirectory = isCodex ? profile.codexDirectory : null;
    final pair = _buildTransportPair(profile);
    final currentApi = pair.gateway
      ..setLocation(directory: initialDirectory, workspace: null);
    final currentRepository = pair.operations
      ..setLocation(directory: initialDirectory, workspace: null);
    api = currentApi;
    repository = currentRepository;
    _connectedProfile = profile;
    _syncOrchestration(profile);
    availableServerVersion = null;
    installedServerVersion = null;
    directory = initialDirectory;
    workspace = null;
    _restoringSavedLocation = !isCodex;
    _pendingLocationRevalidation = false;
    locationRevision += 1;
    _clearLocationData();
    status = StreamStatus.connecting;
    lastError = null;
    lastFailure = null;
    passwordRejected = false;
    locationError = null;
    locationFailure = null;
    locationNotice = null;
    _notifyListeners();
    enablePollingFallback();

    try {
      await _writeActiveProfile(generation, profile.id);
    } catch (error) {
      if (!_isCurrent(generation, currentApi)) return;
      _failCurrentConnection(
        'Could not save the active server profile: $error',
        failure: error,
      );
      return;
    }
    if (!_isCurrent(generation, currentApi)) return;

    try {
      _ensureLocalServerWakeLock();
      final health = await PerfTrace.span('connect.health', currentApi.health);
      if (!_isCurrent(generation, currentApi)) return;
      if (!health.healthy) {
        throw ApiException('Server health check reported unhealthy');
      }
      _acceptRunningServerVersion(health.version);
      // A remote owner upgrades their own server. Once OC2 health is proven,
      // retire only its legacy metadata marker, without any phone-side import.
      if (profile.requiresOpenCode2Migration &&
          !BuiltinLinux.managesServerUrl(profile.baseUrl) &&
          !TermuxBridge.managesServerUrl(profile.baseUrl)) {
        profile.requiresOpenCode2Migration = false;
        try {
          await store.upsert(profile);
        } catch (_) {
          profile.requiresOpenCode2Migration = true;
        }
      }
    } catch (e) {
      if (!_isCurrent(generation, currentApi)) return;
      _noteAuthFailure(e);
      if (!isCodex && redetectOnFailure && _suggestsWrongFlavor(e)) {
        final corrected = await _redetectFlavor(profile);
        if (!_isCurrent(generation, currentApi)) return;
        if (corrected != null) {
          if (corrected.ok) {
            await _connectProfile(
              profile,
              redetectOnFailure: false,
              preserveConnectionAttempt: true,
            );
          } else {
            _failCurrentConnection(
              corrected.message ?? 'Cannot reach ${profile.baseUrl}: $e',
              failure: corrected,
            );
          }
          return;
        }
      }
      _failCurrentConnection(
        e is ApiException ? e.message : 'Cannot reach ${profile.baseUrl}: $e',
        failure: e,
      );
      return;
    }

    // Restore per-profile selections.
    final saved = store.modelFor(profile.id);
    selectedModel = (saved.$1 != null && saved.$2 != null)
        ? ModelRef(providerID: saved.$1!, modelID: saved.$2!).normalized
        : null;
    selectedAgent = store.agentFor(profile.id);
    selectedVariant = store.variantFor(profile.id);
    sessionModels = store.sessionModelsFor(profile.id);
    _modelLibrary = store.modelLibraryFor(profile.id);

    final savedLocation = isCodex
        ? null
        : await _validatedSavedLocation(
            profile,
            currentRepository,
            generation,
            currentApi,
          );
    if (!_isCurrent(generation, currentApi)) return;
    _restoringSavedLocation = false;
    if (savedLocation != null) {
      await _selectLocation(
        preserveConnectionAttempt: true,
        directory: savedLocation.directory,
        workspace: savedLocation.workspace,
        preserveNotice: true,
      );
      return;
    }

    _startEvents(generation, currentApi);
    _markDataRefreshReadyAfterStart(generation, currentApi);
    _notifyListeners();
    unawaited(
      _loadAfterConnect(
        generation: generation,
        currentApi: currentApi,
        currentRepository: currentRepository,
        profile: profile,
      ),
    );
  }

  /// Loads conversations and waiting requests before the provider catalog,
  /// keeping the first useful reads ahead of the slower model inventory.
  Future<void> _loadAfterConnect({
    required int generation,
    required ServerGateway currentApi,
    required ServerOperationsGateway currentRepository,
    required ServerProfile profile,
  }) async {
    try {
      await _refreshLocationReads();
    } catch (_) {
      // Each refresh reports its own failure; the catalog still loads.
    }
    if (!_isCurrent(generation, currentApi)) return;
    await _refreshPreexistingProviderRuntime(
      generation: generation,
      currentApi: currentApi,
      currentRepository: currentRepository,
      profile: profile,
    );
    if (!_isCurrent(generation, currentApi)) return;
    await _ensureCatalog();
  }

  /// Marks a mid-session Basic-auth rejection from the v2 transport so the
  /// connection banner can offer "Update password" instead of retry loops.
  void _noteAuthFailure(Object error) {
    if ((error is CodexFailure &&
            error.kind == CodexFailureKind.authentication) ||
        (error is PaseoFailure &&
            error.kind == PaseoFailureKind.authentication) ||
        error is Api2AuthRequired ||
        (error is ApiException &&
            error.statusCode == 401 &&
            _connectedProfile != null)) {
      passwordRejected = true;
      final rejectedProfile = _connectedProfile;
      if (rejectedProfile?.usesAgentSocket ?? false) {
        rejectedProfile!.requiresCodexTokenReentry = true;
      }
    }
  }

  /// After a failed connect, asks the probe which protocol generation the
  /// address actually speaks. Returns the probe result when it disagrees with
  /// the profile's cached flavor (persisting remote corrections), null
  /// otherwise. A managed local mismatch fails without changing its profile.
  Future<ServerProbeResult?> _redetectFlavor(ServerProfile profile) async {
    if (profile.usesAgentSocket) return null;
    try {
      final result = await probeTrustedServerConnection(
        baseUrl: profile.baseUrl,
        username: profile.username,
        password: profile.password,
        cleartextConfirmedOrigin: profile.cleartextConfirmedOrigin,
        certificatePinSha256:
            profile.certificatePinOrigin == cleartextOriginOf(profile.baseUrl)
            ? profile.certificatePinSha256
            : null,
      );
      if (result.unsupportedLegacy) {
        passwordRejected = false;
        if (BuiltinLinux.managesServerUrl(profile.baseUrl) ||
            TermuxBridge.managesServerUrl(profile.baseUrl)) {
          profile.requiresOpenCode2Migration = true;
          await store.upsert(profile);
          return const ServerProbeResult.failure(
            ConnectionController.managedRuntimeMismatchMessage,
          );
        }
        return result;
      }
      final detected = result.flavor;
      if (detected == ServerFlavor.unknown || detected == profile.flavor) {
        return null;
      }
      // Both managed runtimes share an address, but retain separate profile
      // identity, credentials and local history. Detecting its current owner
      // is not permission to turn the saved profile into the other runtime.
      if (TermuxBridge.managesServerUrl(profile.baseUrl)) {
        return const ServerProbeResult.failure(
          ConnectionController.managedRuntimeMismatchMessage,
        );
      }
      profile.flavor = detected;
      if (result.version != null) profile.serverVersion = result.version;
      try {
        await store.upsert(profile);
      } catch (_) {
        // The corrected flavor still applies to this in-memory connect.
      }
      return result;
    } catch (_) {
      return null;
    }
  }

  Future<bool> _confirmManagedOpenCode2Migration({
    required Duration timeout,
    required Duration pollInterval,
  }) async {
    final owner = _connectedProfile ?? profile;
    if (owner == null || owner.requiresOpenCode2Migration) return false;
    if (!owner.openCode2HistoryPending) return true;
    final operations = repository;
    if (operations is! HistoryMigrationGateway) return false;
    final reporter = operations as HistoryMigrationGateway;
    final generation = _generation;
    final watch = Stopwatch()..start();
    while (!_disposed &&
        generation == _generation &&
        _connectedProfile?.id == owner.id &&
        watch.elapsed <= timeout) {
      try {
        final result = await reporter.historyMigrationStatus().timeout(
          const Duration(seconds: 15),
        );
        if (_disposed || generation != _generation) return false;
        if (result?.phase == HistoryMigrationPhase.completed) {
          owner.openCode2HistoryPending = false;
          try {
            await store.upsert(owner);
          } catch (_) {
            owner.openCode2HistoryPending = true;
            return false;
          }
          _notifyListeners();
          unawaited(flushOfflineQueue());
          return true;
        }
        if (result == null || result.phase == HistoryMigrationPhase.error) {
          return false;
        }
      } catch (_) {
        return false;
      }
      if (watch.elapsed >= timeout) return false;
      await Future<void>.delayed(pollInterval);
    }
    return false;
  }

  /// The body of [prepareManagedRuntimeSwitch].
  Future<void> _prepareManagedRuntimeSwitch() async {
    bool isManagedLocal(ServerProfile? value) =>
        value != null &&
        value.backend == ServerBackend.openCode &&
        (TermuxBridge.managesServerUrl(value.baseUrl) ||
            BuiltinLinux.managesServerUrl(value.baseUrl));

    Set<String> managedIDs() => {
      for (final value in store.profiles)
        if (isManagedLocal(value)) value.id,
    };

    void checkKnownWork() {
      final ids = managedIDs();
      final current = _connectedProfile ?? profile;
      if (isManagedLocal(current) &&
          (busySessions.isNotEmpty ||
              retryStates.isNotEmpty ||
              permissions.isNotEmpty ||
              questions.isNotEmpty ||
              forms.isNotEmpty ||
              _inboxBySession.values.any((items) => items.isNotEmpty) ||
              _flushingOfflineQueue ||
              _queuedPromptInFlight != null ||
              _pendingReplies.isNotEmpty ||
              _revertMutations.isNotEmpty)) {
        throw StateError(
          'Finish the running work and pending requests on this phone before '
          'switching OpenCode versions.',
        );
      }
      for (final id in ids) {
        final observed = _profileMonitor?.snapshotFor(id);
        if (observed?.isCurrent == true &&
            ((observed?.runningCount ?? 0) > 0 ||
                (observed?.pendingCount ?? 0) > 0)) {
          throw StateError(
            'This phone has running work or pending requests. Open its server '
            'profile and finish them before switching OpenCode versions.',
          );
        }
      }
      final inFlight = _queuedPromptInFlight;
      if (inFlight != null &&
          _queueStore.load().any(
            (entry) => entry.id == inFlight && ids.contains(entry.profileID),
          )) {
        throw StateError(
          'Wait for the prompt being sent on this phone to finish before '
          'switching OpenCode versions.',
        );
      }
    }

    checkKnownWork();
    try {
      for (final id in managedIDs()) {
        await ManagedServerRecovery.suspendForProfile(store.prefs, id);
      }
    } catch (_) {
      throw StateError(
        'Automatic server recovery could not be disabled. Try again before '
        'switching OpenCode versions.',
      );
    }
    // Work may have arrived while recovery revocation was being persisted.
    checkKnownWork();
    if (isManagedLocal(_connectedProfile ?? profile)) {
      // Clear the remembered connection before retiring its transport so
      // lifecycle recovery cannot reconnect the old runtime at this address.
      // Use the serialized writer directly: disconnect() absorbs storage
      // errors, while a runtime switch must not proceed after one.
      try {
        await _writeActiveProfile(_generation, null);
      } catch (_) {
        throw StateError(
          'The local connection could not be cleared. Try again before '
          'switching OpenCode versions.',
        );
      }
      checkKnownWork();
      if (isManagedLocal(_connectedProfile ?? profile)) {
        await disconnect(keepActive: true);
      }
    }
  }

  /// The body of [disconnect].
  Future<void> _disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    ++_paIdleLifecycleEpoch;
    _forgetTurnStalls();
    _endResumeQuiet();
    _appBackgrounded = false;
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _lifecycleResume = null;
    _lifecycleTransportReady = null;
    _manualReconnect = null;
    final generation = _beginGeneration();
    _retireTransport();
    _clearLocationData();
    version = null;
    availableServerVersion = null;
    installedServerVersion = null;
    _connectedProfile = null;
    _syncOrchestration(null);
    _restoringSavedLocation = false;
    _pendingLocationRevalidation = false;
    directory = null;
    workspace = null;
    locationRevision += 1;
    locationLoading = false;
    locationError = null;
    locationFailure = null;
    locationNotice = null;
    passwordRejected = false;
    status = StreamStatus.disconnected;
    if (!silent) _notifyListeners();
    // A launcher shortcut or a tile count that points at a server the user
    // just left is a stale promise; withdraw both, after any publish still
    // in flight so it cannot land on top of the withdrawal. Lifecycle
    // suspension does not come through here, so backgrounding keeps them.
    await _pendingLauncherWrite;
    if (_disposed) return;
    unawaited(_pinnedShortcuts.clear());
    unawaited(_attentionTile.clear());
    if (!keepActive) {
      try {
        await _writeActiveProfile(generation, null);
      } catch (error) {
        if (_disposed || generation != _generation) return;
        lastError = 'Could not clear the active server profile: $error';
        lastFailure = error;
        _notifyListeners();
      }
    }
  }

  /// The body of [prepareActionTransport].
  Future<ServerGateway?> _prepareActionTransport() async {
    final resume = resumeFromLifecycle();
    // Only the transport matters to an action; the sessions and catalog
    // reload that follows a wake keeps running behind it.
    await (_lifecycleTransportReady ?? resume);
    if (_disposed || _lifecycleSuspended) return null;
    if (managedOpenCode2MigrationRequired) {
      throw ApiException(
        'Your conversations are still being moved to OpenCode 2. '
        'Wait for the move to finish before sending.',
      );
    }
    final owner = _connectedProfile;
    if (owner != null && BuiltinLinux.managesServerUrl(owner.baseUrl)) {
      try {
        await phoneProjectEngine.preparePhoneAliasDispatch(owner.id);
        if (!_phoneChatEligible(owner.id) &&
            owner.orchestration?.provider ==
                OrchestrationProvider.phoneEngine) {
          await phoneProjectEngine.suspendChatAdmission(owner.id);
        }
      } on PhoneEngineException {
        throw ApiException(
          'AI Team could not pause safely. Stop AI Team before sending.',
        );
      }
      if (_disposed ||
          _lifecycleSuspended ||
          _connectedProfile?.id != owner.id) {
        return null;
      }
    }
    return api;
  }

  /// The body of [prepareActionRepository].
  Future<ServerOperationsGateway?> _prepareActionRepository() async {
    await prepareActionTransport();
    if (_disposed || _lifecycleSuspended) return null;
    return repository;
  }
}
