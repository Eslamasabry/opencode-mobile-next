part of '../connection.dart';

// Generations, lifecycle suspend and resume, and transport teardown.

/// [ConnectionController]'s generations and app lifecycle.
mixin _ConnectionControllerLifecycle on ChangeNotifier {
  ConnectionController get _self;

  int _generation = 0;
  int _sessionsRefreshGeneration = 0;
  int _catalogRefreshGeneration = 0;
  int _questionsRefreshGeneration = 0;
  int _questionRevision = 0;
  final Map<String, int> _questionRevisions = {};
  int _sessionRevision = 0;
  final Map<String, int> _sessionRevisions = {};
  final Map<String, int> _sessionStatusRevisions = {};
  final Map<String, Future<void>> _selectionMutations = {};
  final Map<String, Object> _revertMutations = {};
  final Map<String, int> _historyRevisions = {};
  bool _disposed = false;
  bool _lifecycleSuspended = false;
  bool _lifecycleWasBackgrounded = false;
  Future<void>? _lifecycleResume;

  /// Completes once the wake-time transport can carry requests, which is
  /// usually long before the sessions, permissions and model catalog behind
  /// it have reloaded. Foreground actions wait on this, not on the reload:
  /// OpenCode 1 answers `/provider` with a multi-megabyte catalog that a
  /// phone-hosted server takes seconds to build, and every settings check or
  /// sent message used to sit behind it after each app switch.
  Future<void>? _lifecycleTransportReady;
  Future<void>? _manualReconnect;

  /// True only while the app intentionally has its transport retired in the
  /// background. UI must not treat this as a user-initiated disconnect.
  bool get lifecycleSuspended => _lifecycleSuspended;

  /// True while a user-requested reconnect is rebuilding the transport for
  /// the retained server and location.
  bool get manualReconnectInProgress => _manualReconnect != null;

  /// Stops all network work while the application is backgrounded without
  /// clearing the selected profile, location, or already-rendered data.
  void suspendForLifecycle() {
    // An answer still in its Undo window goes now: the app may not wake.
    unawaited(_self.delayedAnswers.flush());
    _self._suspendForLifecycle();
    // The agents' own connection follows the app the same way.
    _self._paBackendLive?.suspendForLifecycle();
    for (final side in _self._sides.values) {
      side.suspendForLifecycle();
    }
  }

  /// Recreates one transport for the profile/location retained by
  /// [suspendForLifecycle]. Concurrent resume signals share the same future.
  Future<void> resumeFromLifecycle() {
    final resume = _self._resumeFromLifecycle();
    for (final side in _self._sides.values) {
      unawaited(side.resumeFromLifecycle());
    }
    final backend = _self._paBackendLive;
    if (backend == null) return resume;
    unawaited(backend.resumeFromLifecycle());
    return resume;
  }

  /// Reconnects the active profile without discarding the selected location
  /// or already-rendered product data. Repeated taps share one operation.
  Future<void> retryConnection() => _self._retryConnection();

  @visibleForTesting
  void signalDataRefreshForTesting() {
    _self.dataRefreshRevision += 1;
    notifyListeners();
  }
}

extension _ConnectionControllerLifecycleImpl on ConnectionController {
  /// The body of [suspendForLifecycle].
  void _suspendForLifecycle() {
    if (_disposed || isIsolated) return;
    ++_paIdleLifecycleEpoch;
    _resetTurnStalls();
    _lifecycleWasBackgrounded = true;
    _quotaMonitor?.setRuntime(
      foreground: false,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    _profileMonitor?.setRuntime(
      foreground: false,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    if (keepLiveInBackground) {
      _attentionActiveSessions.addAll(busySessions);
      _syncInputAlerts();
      return;
    }
    if (_lifecycleSuspended) return;
    _lifecycleSuspended = true;
    // A resume already in flight is invalidated by the generation change
    // below. Detach it so a later resume can create a fresh transport.
    _lifecycleResume = null;
    _lifecycleTransportReady = null;
    if (api == null) return;
    _beginGeneration();
    _retireTransport();
    status = StreamStatus.disconnected;
    _notifyListeners();
  }

  /// The body of [resumeFromLifecycle].
  Future<void> _resumeFromLifecycle() {
    if (_disposed || isIsolated) return Future.value();
    _quotaMonitor?.setRuntime(
      foreground: true,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    _profileMonitor?.setRuntime(
      foreground: true,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    final inFlight = _lifecycleResume;
    if (inFlight != null) return inFlight;
    if (!_lifecycleSuspended) {
      if (keepLiveInBackground && _lifecycleWasBackgrounded) {
        _lifecycleWasBackgrounded = false;
        _dismissAllCodingAlerts(clearActive: true);
        return _trackLifecycleResume(
          (transportReady) =>
              _reconcileAfterBackground(onTransportReady: transportReady),
        );
      }
      return Future.value();
    }
    if (!automationPolicy.allows(AutomationBehavior.reconnect)) {
      return Future.value();
    }
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _dismissAllCodingAlerts(clearActive: true);
    final profile = _connectedProfile;
    if (profile == null) return Future.value();
    return _trackLifecycleResume(
      (transportReady) => _resumeLifecycleTransport(
        profile,
        directory: directory,
        workspace: workspace,
        onTransportReady: transportReady,
        automaticRecovery: true,
      ),
    );
  }

  /// The body of [retryConnection].
  Future<void> _retryConnection() {
    if (_disposed || isIsolated) return Future.value();
    final inFlight = _manualReconnect ?? _lifecycleResume;
    if (inFlight != null) return inFlight;

    final retainedProfile = _connectedProfile ?? profile;
    if (retainedProfile == null) {
      lastError = 'Choose an OpenCode server before retrying.';
      status = StreamStatus.disconnected;
      _notifyListeners();
      return Future.value();
    }

    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _dismissAllCodingAlerts(clearActive: true);

    late final Future<void> tracked;
    tracked =
        _resumeLifecycleTransport(
          retainedProfile,
          directory: directory,
          workspace: workspace,
        ).whenComplete(() {
          if (identical(_manualReconnect, tracked)) {
            _manualReconnect = null;
            if (!_disposed) _notifyListeners();
          }
        });
    _manualReconnect = tracked;
    _notifyListeners();
    return tracked;
  }

  /// Runs one wake-time recovery. [start] receives a callback it invokes as
  /// soon as the transport answers; the returned future still covers the
  /// data reload behind it.
  Future<void> _trackLifecycleResume(
    Future<void> Function(void Function() transportReady) start,
  ) {
    final ready = Completer<void>();
    void transportReady() {
      if (!ready.isCompleted) ready.complete();
    }

    late final Future<void> tracked;
    tracked = start(transportReady).whenComplete(() {
      // A recovery that failed or was superseded never reported a ready
      // transport; release waiting actions so they see the outcome.
      transportReady();
      if (identical(_lifecycleResume, tracked)) {
        _lifecycleResume = null;
        _lifecycleTransportReady = null;
      }
    });
    _lifecycleResume = tracked;
    _lifecycleTransportReady = ready.future;
    return tracked;
  }

  Future<void> _reconcileAfterBackground({void Function()? onTransportReady}) =>
      PerfTrace.span(
        'lifecycle.reconcile',
        () => _reconcileAfterBackgroundUntraced(
          onTransportReady: onTransportReady,
        ),
      );

  Future<void> _reconcileAfterBackgroundUntraced({
    void Function()? onTransportReady,
  }) async {
    final currentApi = api;
    if (currentApi == null) return;
    final generation = _generation;
    try {
      _ensureLocalServerWakeLock();
      final health = await currentApi.health();
      if (!_isCurrent(generation, currentApi)) return;
      if (!health.healthy) {
        throw ApiException('Server health check reported unhealthy');
      }
      _acceptRunningServerVersion(health.version ?? version);
    } catch (_) {
      if (!_isCurrent(generation, currentApi)) return;
      final profile = _connectedProfile;
      if (profile == null ||
          !automationPolicy.allows(AutomationBehavior.reconnect)) {
        return;
      }
      await _resumeLifecycleTransport(
        profile,
        directory: directory,
        workspace: workspace,
        onTransportReady: onTransportReady,
        automaticRecovery: true,
      );
      return;
    }
    onTransportReady?.call();
    _markDataRefreshReady(generation, currentApi);
    _notifyListeners();
    await _reloadRetainedLocationData();
  }

  /// Reloads what a wake or reconnect may have missed, behind the screen
  /// already shown. The model catalog goes last and only when stale: on
  /// OpenCode 1 its `/provider` answer is several megabytes the server
  /// serialises on its only thread, and reloading it on every wake held
  /// each resume for 2.5 s on a phone-hosted server. A change while away
  /// still reaches it through age ([catalogFreshFor]) or Reload.
  Future<void> _reloadRetainedLocationData() =>
      PerfTrace.span('lifecycle.reload', () async {
        await _refreshLocationReads();
        await _ensureCatalog();
      });

  /// Rebuilds the transport (`lifecycle.resume`: health, then the event
  /// stream), then reconciles by refetch (`lifecycle.reload`). The returned
  /// future covers both; actions wait only for the transport.
  Future<void> _resumeLifecycleTransport(
    ServerProfile profile, {
    String? directory,
    String? workspace,
    void Function()? onTransportReady,
    bool automaticRecovery = false,
  }) async {
    final ready = await PerfTrace.span(
      'lifecycle.resume',
      () => _resumeLifecycleTransportUntraced(
        profile,
        directory: directory,
        workspace: workspace,
        onTransportReady: onTransportReady,
        automaticRecovery: automaticRecovery,
      ),
    );
    if (ready) await _reloadRetainedLocationData();
  }

  /// Returns whether the transport came up for this generation.
  Future<bool> _resumeLifecycleTransportUntraced(
    ServerProfile profile, {
    String? directory,
    String? workspace,
    void Function()? onTransportReady,
    bool automaticRecovery = false,
  }) async {
    if (automaticRecovery &&
        !automationPolicy.allows(AutomationBehavior.reconnect)) {
      return false;
    }
    final generation = _beginGeneration();
    _retireTransport();
    _connectedProfile = profile;
    _syncOrchestration(profile);
    final pair = _buildTransportPair(profile);
    final currentApi = pair.gateway
      ..setLocation(directory: directory, workspace: workspace);
    final currentRepository = pair.operations
      ..setLocation(directory: directory, workspace: workspace);
    api = currentApi;
    repository = currentRepository;
    status = StreamStatus.connecting;
    lastError = null;
    passwordRejected = false;
    _notifyListeners();
    enablePollingFallback();
    try {
      _ensureLocalServerWakeLock();
      final health = await currentApi.health();
      if (!_isCurrent(generation, currentApi)) return false;
      if (!health.healthy) {
        throw ApiException('Server health check reported unhealthy');
      }
      _acceptRunningServerVersion(health.version ?? version);
    } catch (error) {
      if (!_isCurrent(generation, currentApi)) return false;
      _noteAuthFailure(error);
      _failCurrentConnection(
        error is ApiException
            ? error.message
            : 'Cannot reach ${profile.baseUrl}: $error',
      );
      return false;
    }
    if (automaticRecovery &&
        !automationPolicy.allows(AutomationBehavior.reconnect)) {
      _retireTransport();
      _lifecycleSuspended = true;
      status = StreamStatus.disconnected;
      _notifyListeners();
      return false;
    }
    _startEvents(generation, currentApi, automaticRecovery: automaticRecovery);
    onTransportReady?.call();
    _markDataRefreshReady(generation, currentApi);
    return true;
  }

  int _beginGeneration({bool preserveConnectionAttempt = false}) {
    _genUiRetireQualification();
    _resetTurnStalls();
    _invalidatePhoneChatStatus();
    if (!preserveConnectionAttempt) connectionAttemptRevision++;
    _generation += 1;
    connectionRevision = _generation;
    return _generation;
  }

  void _markDataRefreshReady(int generation, ServerGateway currentApi) {
    if (_isCurrent(generation, currentApi)) {
      _transportReady = true;
      dataRefreshRevision += 1;
    }
  }

  bool _isCurrent(int generation, ServerGateway? currentApi) =>
      !_disposed && generation == _generation && identical(api, currentApi);

  bool _isCurrentStream(
    int generation,
    ServerGateway currentApi,
    LiveEventChannel stream,
  ) => _isCurrent(generation, currentApi) && identical(_events, stream);

  bool _isCurrentGlobalStream(
    int generation,
    ServerGateway currentApi,
    LiveEventChannel stream,
  ) => _isCurrent(generation, currentApi) && identical(_globalEvents, stream);

  bool _isCurrentSessionsRefresh(
    int generation,
    ServerGateway currentApi,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      refreshGeneration == _sessionsRefreshGeneration;

  bool _isCurrentCatalogRefresh(
    int generation,
    ServerGateway currentApi,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      refreshGeneration == _catalogRefreshGeneration;

  bool _isCurrentQuestionsRefresh(
    int generation,
    ServerGateway? currentApi,
    ServerOperationsGateway currentRepository,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      identical(repository, currentRepository) &&
      refreshGeneration == _questionsRefreshGeneration;

  void _markSessionChanged(String id, {bool affectsStatus = true}) {
    _sessionRevision += 1;
    _sessionRevisions[id] = _sessionRevision;
    // Metadata hydration must not invalidate a concurrent status snapshot.
    if (affectsStatus) _sessionStatusRevisions[id] = _sessionRevision;
  }

  void _markQuestionChanged(String id) {
    _questionRevision += 1;
    _questionRevisions[id] = _questionRevision;
  }

  void _failCurrentConnection(String error) {
    _retireTransport();
    version = null;
    status = StreamStatus.disconnected;
    lastError = error;
    _notifyListeners();
  }

  void _retireTransport() {
    _feedRetireTransport();
    _resetTurnStalls();
    _invalidatePhoneChatStatus();
    _syncPhoneChatHeartbeat();
    elsewhereAttention.markStale();
    final policyListener = _streamPolicyChanged;
    if (policyListener != null) _streamPolicy?.removeListener(policyListener);
    _streamPolicy = null;
    _streamPolicyChanged = null;
    _transportReady = false;
    _sessionTailReads.clear();
    _cancelPermissionHydration();
    final oldEvents = _events;
    _events = null;
    unawaited(oldEvents?.dispose());
    final oldGlobalEvents = _globalEvents;
    _globalEvents = null;
    unawaited(oldGlobalEvents?.dispose());
    _poll?.cancel();
    _poll = null;
    _busyStatusRefresh = null;
    final oldApi = api;
    api = null;
    repository = null;
    oldApi?.close();
  }

  void _clearLocationData() {
    _feedQuestionEpoch++;
    _feedDirectoryQuestions.clear();
    _feedDirectoryForms.clear();
    _genUiReset();
    _invalidatePhoneChatStatus();
    if (_phoneChatDispatchProfile != _connectedProfile?.id) {
      _phoneChatDispatch.reset();
      _phoneChatDispatchProfile = _connectedProfile?.id;
    }
    _phoneChatDispatch.epoch++;
    _attentionReads.clear();
    _attentionEvents.clear();
    _attentionReadRevisions.clear();
    _failedAttentionSessions.clear();
    _attentionTransportRevision++;
    _noteRevisions.clear();
    _noteReceipts.clear();
    _dismissAllCodingAlerts(clearActive: true);
    _sessionsRefreshGeneration += 1;
    _catalogRefreshGeneration += 1;
    _catalogFollowUp = null;
    _catalogFollowUpAfter = null;
    _catalogFollowUpAnnounced = false;
    _catalogLoadedAt = null;
    _catalogLoadedKey = null;
    _questionsRefreshGeneration += 1;
    _questionRevision += 1;
    _questionRevisions.clear();
    _sessionRevision += 1;
    _sessionRevisions.clear();
    _sessionStatusRevisions.clear();
    _selectionMutations.clear();
    _revertMutations.clear();
    _historyRevisions.clear();
    sessionRevertErrors.clear();
    sessionSelectionErrors.clear();
    sessionsById = {};
    _sortedSessionInputs = null;
    _sortedSessionPins = null;
    _sortedSessionResult = const [];
    _sessionsCursor = null;
    sessionsLoadingMore = false;
    sessionsMoreError = null;
    sessionsNeedReload = false;
    _sessionPageIDs.clear();
    _usedSessionCursors.clear();
    _sessionInventoryIDs.clear();
    _sessionInventoryInitialized = false;
    _sessionReads.clear();
    _sessionTailReads.clear();
    sessionDetailsErrors.clear();
    _deletedSessionIDs.clear();
    sessionModels = {};
    _modelLibrary = const ModelLibrary();
    busySessions = {};
    _openTurns.clear();
    observedCompletedMessageIDs.clear();
    retryStates = {};
    permissions = {};
    _autoApprovingPermissionIDs.clear();
    _autoApprovalFailures.clear();
    _autoApprovedBySession.clear();
    questions = {};
    forms = {};
    _resolvedFormIDs.clear();
    _formRevision = 0;
    _formRefreshGeneration += 1;
    formsLoading = false;
    formsError = null;
    _inboxBySession.clear();
    inboxRevision += 1;
    _resolvedPermissionIDs.clear();
    _legacyPermissionIdentities.clear();
    _v2PermissionSessions.clear();
    _v2QuestionSessions.clear();
    _resolvedQuestionIDs.clear();
    _permissionRevision = 0;
    providers = null;
    agents = [];
    catalog = null;
    unloadedProviderIDs = const {};
    unloadedProvidersUnusable = false;
    providerReloadWaitingOn = 0;
    _providerHealDeferred = false;
    _runtimeJustRefreshed = false;
    catalogDetailed = false;
    sessionsLoading = false;
    sessionsError = null;
    catalogLoading = false;
    catalogError = null;
    permissionsLoading = false;
    permissionsError = null;
    questionsLoading = false;
    questionsError = null;
    lastPtyEvent = null;
  }

  void _recordLocationError(String error) {
    if (locationLoading) locationError ??= error;
  }

  Future<void> _writeActiveProfile(int generation, String? id) {
    // The app's active server is the main connection's to choose.
    if (!_ownsProfileServices) return Future.value();
    final previous = _activeProfileWrite;
    final write = () async {
      try {
        await previous;
      } catch (_) {
        // A later generation still needs a chance to persist its selection.
      }
      if (_disposed || generation != _generation) return;
      await store.setActiveId(id);
    }();
    _activeProfileWrite = write;
    return write;
  }
}
