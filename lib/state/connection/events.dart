part of '../connection.dart';

// The event streams and the event switch, with per-domain handlers.

/// [ConnectionController]'s event streams.
mixin _ConnectionControllerEvents on ChangeNotifier {
  ConnectionController get _self;

  int ptyRevision = 0;
  EventEnvelope? lastPtyEvent;

  /// Broadcast of every event for screen-scoped listeners (chat streaming).
  final _eventBus = StreamController<EventEnvelope>.broadcast();
  Stream<EventEnvelope> get events => _eventBus.stream;

  /// Shared persisted policy used by settings and automatic executors.
  AutomationPolicy get automationPolicy => _self._automationPolicy;

  AutomationPolicyController? _streamPolicy;
  VoidCallback? _streamPolicyChanged;
  StreamStatus _globalStreamStatus = StreamStatus.disconnected;

  /// Counts event-stream connects, so a waiting-request read knows whether
  /// the stream has carried every change since it started.
  int _streamConnects = 0;

  @visibleForTesting
  void handleEventForTesting(EventEnvelope event) => _self._onEvent(event);
}

extension _ConnectionControllerEventsImpl on ConnectionController {
  /// The body of [automationPolicy].
  AutomationPolicy get _automationPolicy {
    final owner = _connectedProfile ?? profile;
    return owner == null
        ? AutomationPolicy.disabled()
        : AutomationPolicyController.forProfile(store.prefs, owner.id).value;
  }

  void _stopDisallowedReconnect() {
    if (automationPolicy.allows(AutomationBehavior.reconnect)) return;
    if (_globalStreamStatus == StreamStatus.reconnecting ||
        _globalStreamStatus == StreamStatus.disconnected) {
      final global = _globalEvents;
      _globalEvents = null;
      unawaited(global?.dispose());
    }
    if (status != StreamStatus.reconnecting &&
        status != StreamStatus.disconnected) {
      return;
    }
    // Retire both channels and their timers, while retaining rendered data.
    // An explicit Reconnect can still create a fresh transport.
    _retireTransport();
    status = StreamStatus.disconnected;
    _notifyListeners();
  }

  void _startEvents(
    int generation,
    ServerGateway currentApi, {
    bool automaticRecovery = false,
  }) {
    late final LiveEventChannel stream;
    var wasConnected = false;
    var recovering = automaticRecovery;
    void handleEvent(EventEnvelope event) {
      if (!_isCurrentStream(generation, currentApi, stream)) return;
      _onEvent(event);
    }

    void handleStatus(StreamStatus s) {
      if (!_isCurrentStream(generation, currentApi, stream)) return;
      final previousStatus = status;
      status = s;
      if (s != StreamStatus.connected) {
        final scope = _genUiScope;
        if (scope != null) _genUiState.invalidateScope(scope);
      }
      if (s == StreamStatus.reconnecting || s == StreamStatus.disconnected) {
        recovering = recovering || wasConnected;
        _stopDisallowedReconnect();
        if (!_isCurrentStream(generation, currentApi, stream)) return;
      }
      if (s == StreamStatus.connected) {
        _streamConnects += 1;
        _genUiSync();
        _genUiRefreshFeed();
        PerfTrace.mark('events.connected');
        PerfTrace.markOnce('app.first_connected');
        lastError = null;
        passwordRejected = false;
        unawaited(refreshPendingPermissions());
        unawaited(refreshPendingQuestions());
        // Form events are ephemeral: re-poll the pending list after
        // every (re)connect. No-op on servers without forms.
        unawaited(refreshPendingForms());
        unawaited(flushOfflineQueue());
        if (previousStatus == StreamStatus.reconnecting ||
            previousStatus == StreamStatus.disconnected) {
          _markDataRefreshReady(generation, currentApi);
          unawaited(refreshSessions());
        }
        // The stream lost the server and found it again by itself: an
        // automatic act, filed for While you were away (P6.2). A first
        // connect or a person's Reconnect starts a new stream instead.
        if (recovering) {
          final scope = _automaticActScope();
          if (scope != null) {
            final at = DateTime.now();
            unawaited(
              recordAutomaticAct(
                profileId: scope.profileId,
                location: scope.server,
                kind: AutomaticActKind.reconnect,
                target: scope.name,
                eventId:
                    'stream.reconnect:$generation:'
                    '${at.microsecondsSinceEpoch}',
                at: at,
              ),
            );
          }
        }
        wasConnected = true;
        recovering = false;
      } else {
        _cancelPermissionHydration();
      }
      _notifyListeners();
    }

    void handleError(Object e) {
      if (!_isCurrentStream(generation, currentApi, stream)) return;
      _invalidatePhoneChatStatus();
      _noteAuthFailure(e);
      lastError = e.toString();
      _notifyListeners();
    }

    // The v1 factory stays the injected test seam (typed on OpenCodeApi);
    // every other gateway supplies its own channel through the EventGateway
    // interface — the v2 SSE consumer lives behind that seam.
    stream = currentApi is OpenCodeApi
        ? _eventStreamFactory(
            api: currentApi,
            onEvent: handleEvent,
            onStatus: handleStatus,
            onError: handleError,
          )
        : currentApi.openEventChannel(
            onEvent: handleEvent,
            onStatus: handleStatus,
            onError: handleError,
          );
    _events = stream;
    final owner = _connectedProfile ?? profile;
    if (owner != null) {
      _streamPolicy = AutomationPolicyController.forProfile(
        store.prefs,
        owner.id,
      );
      _streamPolicyChanged = _stopDisallowedReconnect;
      _streamPolicy!.addListener(_streamPolicyChanged!);
    }
    stream.start();
    if (_isCurrentStream(generation, currentApi, stream)) {
      _startGlobalEvents(generation, currentApi);
    }
  }

  void _startGlobalEvents(int generation, ServerGateway currentApi) {
    elsewhereAttention.markStale();
    // The volatile stream lost whatever it missed: refetch, never replay.
    _feedScheduleRefresh();
    late final LiveEventChannel stream;
    // What this server's other projects are doing is only knowable from
    // here; a different server's tally would be wrong.
    final profileID = profile?.id;
    if (_elsewhereProfileID != profileID) {
      _elsewhereProfileID = profileID;
      elsewhereAttention.clear();
    }
    void handleEvent(EventEnvelope event) {
      if (!_isCurrentGlobalStream(generation, currentApi, stream)) return;
      elsewhereAttention.handle(event);
      _invalidateFeedDirectoryQuestions(event);
      _genUiGlobalEvent(event);
      if (event.type == 'session.created' ||
          event.type == 'session.deleted' ||
          event.type == 'session.updated') {
        _feedScheduleRefresh();
      }
      // OpenCode 1's `/event` only carries its own folder's events. While it
      // is down, this server-wide stream still carries them: pass them on so
      // a running reply keeps moving instead of waiting for the 5 s list
      // poll and the refresh at the end. Never while it is up, or every
      // streamed word would arrive twice.
      if (currentApi is OpenCodeApi &&
          status != StreamStatus.connected &&
          event.directory != null &&
          directory != null &&
          ConnectionController.sameDirectoryPath(event.directory, directory)) {
        _onEvent(event);
        return;
      }
      if (event.type == 'installation.update-available' ||
          event.type == 'installation.updated' ||
          event.type == 'worktree.ready' ||
          event.type == 'worktree.failed') {
        _onEvent(event);
      }
    }

    void handleStatus(StreamStatus value) {
      if (!_isCurrentGlobalStream(generation, currentApi, stream)) return;
      _globalStreamStatus = value;
      if (value != StreamStatus.connected) {
        elsewhereAttention.markStale();
        _feedQuestionEpoch++;
        _feedDirectoryQuestions.clear();
        _feedDirectoryForms.clear();
      } else {
        _feedScheduleRefresh();
      }
      if ((value == StreamStatus.reconnecting ||
              value == StreamStatus.disconnected) &&
          !automationPolicy.allows(AutomationBehavior.reconnect)) {
        _globalEvents = null;
        unawaited(stream.dispose());
      }
    }

    if (currentApi is OpenCodeApi) {
      final factory = _globalEventStreamFactory;
      if (factory == null) return;
      stream = factory(
        api: currentApi,
        onEvent: handleEvent,
        // The location-scoped stream owns visible connection state. A global
        // update-notification retry must never make a healthy chat look
        // offline.
        onStatus: handleStatus,
        onError: (_) {},
      );
    } else {
      stream = currentApi.openGlobalEventChannel(
        onEvent: handleEvent,
        onStatus: handleStatus,
        onError: (_) {},
      );
    }
    _globalEvents = stream;
    _globalStreamStatus = StreamStatus.connecting;
    stream.start();
  }

  // ---------------- Event handling ----------------

  void _onEvent(EventEnvelope env) {
    if (_disposed) return;
    final props = env.properties;
    _genUiOnEvent(env);
    final attentionKind = env.type.startsWith('permission.')
        ? AttentionKind.permission
        : env.type.startsWith('question.')
        ? AttentionKind.question
        : env.type.startsWith('form.')
        ? AttentionKind.form
        : null;
    if (attentionKind != null) _attentionEvents[attentionKind] = DateTime.now();
    PromptTrace.observe(env.type, props);
    switch (env.type) {
      case 'server.connected':
      case 'installation.update-available':
      case 'installation.updated':
      case 'integration.connection.updated':
      case 'catalog.updated':
      case 'agent.updated':
      case 'config.updated':
        _onServerEvent(env, props);
        break;

      case 'session.created':
      case 'session.updated':
      case 'session.metadata.updated':
      case 'session.revert.staged':
      case 'session.revert.cleared':
      case 'session.revert.committed':
      case 'session.instructions.updated':
      case 'session.viewed':
      case 'session.model.selected':
      case 'session.agent.selected':
      case 'session.deleted':
      case 'session.usage.updated':
        _onSessionEvent(env, props);
        break;

      case 'message.updated':
      case 'message.removed':
      case 'message.part.updated':
        _onMessageEvent(env, props);
        break;

      case 'permission.asked':
      case 'permission.v2.asked':
      case 'permission.updated':
      case 'permission.replied':
      case 'permission.v2.replied':
      case 'form.v2.created':
      case 'form.v2.replied':
      case 'form.v2.cancelled':
      case 'session.inbox.enqueued':
      case 'session.inbox.delivered':
      case 'session.inbox.cancelled':
      case 'session.inbox.delivery.changed':
      case 'question.asked':
      case 'question.updated':
      case 'question.v2.asked':
      case 'question.replied':
      case 'question.rejected':
      case 'question.v2.replied':
      case 'question.v2.rejected':
        _onRequestEvent(env, props);
        break;

      case 'session.status':
      case 'session.error':
      case 'session.idle':
      case 'session.compacted':
        _onRunStatusEvent(env, props);
        break;

      case 'pty.created':
      case 'pty.updated':
      case 'pty.exited':
      case 'pty.deleted':
        _onPtyEvent(env, props);
        break;

      default:
        break;
    }
    _turnStallOnEvent(env);
    _eventBus.add(env);
  }

  /// Server, installation and catalog events.
  void _onServerEvent(EventEnvelope env, Map<String, dynamic> props) {
    switch (env.type) {
      case 'server.connected':
        final v = props['version']?.toString();
        if (v != null && v.isNotEmpty) {
          _acceptRunningServerVersion(v);
          _notifyListeners();
        }
        break;

      case 'installation.update-available':
        final target = props['version']?.toString().trim() ?? '';
        if (isExactServerVersion(target) &&
            target != version &&
            target != installedServerVersion) {
          availableServerVersion = target;
          _notifyListeners();
        }
        break;

      case 'installation.updated':
        recordServerUpgradeInstalled(props['version']?.toString() ?? '');
        break;

      case 'integration.connection.updated':
      case 'catalog.updated':
      case 'agent.updated':
      case 'config.updated':
        // Provider credentials and catalog overlays can change without a
        // reconnect. Refetch the current catalog just like upstream clients,
        // behind the list already shown; a burst shares one reload.
        unawaited(_loadCatalog(announce: false));
        break;
    }
  }

  /// Terminal (pty) events.
  void _onPtyEvent(EventEnvelope env, Map<String, dynamic> props) {
    switch (env.type) {
      case 'pty.created':
      case 'pty.updated':
      case 'pty.exited':
      case 'pty.deleted':
        lastPtyEvent = env;
        ptyRevision += 1;
        _notifyListeners();
        break;
    }
  }
}
