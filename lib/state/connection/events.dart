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
      if (s == StreamStatus.reconnecting || s == StreamStatus.disconnected) {
        recovering = recovering || wasConnected;
        _stopDisallowedReconnect();
        if (!_isCurrentStream(generation, currentApi, stream)) return;
      }
      if (s == StreamStatus.connected) {
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
      if (value != StreamStatus.connected) elsewhereAttention.markStale();
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
        // reconnect. Refetch the current catalog just like upstream clients.
        unawaited(_loadCatalog());
        break;

      case 'session.created':
      case 'session.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final s = Session.fromJson(info);
          if (_deletedSessionIDs.contains(s.id) &&
              env.type != 'session.created') {
            break;
          }
          _deletedSessionIDs.remove(s.id);
          _markSessionChanged(s.id);
          sessionsById[s.id] = s;
          _rememberSessionMembership(s);
          _notifyListeners();
        }
        break;

      case 'session.metadata.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final id = info['id']?.toString();
          if (id == null || _deletedSessionIDs.contains(id)) break;
          final previous = sessionsById[id];
          var next = previous ?? Session(id: id);
          if (info.containsKey('title')) {
            next = next.copyWith(title: info['title'] as String?);
          }
          if (info.containsKey('directory')) {
            next = next.copyWith(directory: info['directory']);
          }
          if (info.containsKey('workspaceID')) {
            next = next.copyWith(workspaceID: info['workspaceID']);
          }
          if (info.containsKey('projectID')) {
            next = next.copyWith(projectID: info['projectID']);
          }
          if (info.containsKey('path')) {
            next = next.copyWith(path: info['path']);
          }
          _markSessionChanged(id, affectsStatus: false);
          sessionsById[id] = next;
          _rememberSessionMembership(
            next,
            authoritative: info.containsKey('directory'),
          );
          _notifyListeners();
          if (previous == null) unawaited(_refreshOneSession(id));
        }
        break;

      case 'session.revert.staged':
      case 'session.revert.cleared':
      case 'session.revert.committed':
        final id = props['sessionID']?.toString();
        if (id == null || id.isEmpty || _deletedSessionIDs.contains(id)) break;
        final staged = SessionRevert.fromJson(props['revert']);
        if (env.type == 'session.revert.staged' && staged == null) break;
        final previous = sessionsById[id];
        _markSessionChanged(id, affectsStatus: false);
        sessionsById[id] = (previous ?? Session(id: id)).copyWith(
          stagedRevert: staged,
        );
        _resetSessionHistory(
          id,
          removedFrom: env.type == 'session.revert.committed'
              ? props['to']?.toString() ?? previous?.stagedRevert?.messageID
              : null,
        );
        if (previous == null || env.type != 'session.revert.staged') {
          unawaited(_refreshOneSession(id));
        }
        break;

      case 'session.instructions.updated':
        final id = props['sessionID']?.toString();
        if (id != null && !_deletedSessionIDs.contains(id)) {
          final key = (locationRevision, id);
          _noteRevisions[key] = (_noteRevisions[key] ?? 0) + 1;
          _noteReceipts.remove(key);
          _notifyListeners();
        }
        break;

      case 'session.viewed':
        final id = props['sessionID']?.toString();
        final idle = props['idle'];
        if (id == null ||
            idle is! int ||
            idle < 0 ||
            _deletedSessionIDs.contains(id)) {
          break;
        }
        _applySessionViewed(id, idle);
        break;

      case 'session.model.selected':
      case 'session.agent.selected':
        final id = props['sessionID']?.toString();
        if (id == null || _deletedSessionIDs.contains(id)) break;
        final previous = sessionsById[id] ?? Session(id: id);
        final old =
            previous.selection ??
            const SessionSelection(modelKnown: false, agentKnown: false);
        final parsed = SessionSelection.fromJson(props);
        final next = env.type == 'session.model.selected'
            ? old.withModel(parsed.model, parsed.variant)
            : old.withAgent(parsed.agent);
        _markSessionChanged(id, affectsStatus: false);
        sessionsById[id] = previous.copyWith(
          selection: next,
          model: next.model?.wireName,
          agent: next.agent,
        );
        sessionSelectionErrors.remove(id);
        _notifyListeners();
        if (!next.modelKnown || !next.agentKnown) {
          unawaited(_refreshOneSession(id));
        }
        break;

      case 'session.deleted':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final id = info['id']?.toString();
          if (id != null && id.isNotEmpty) {
            _removeSession(id);
          }
        }
        break;

      case 'session.usage.updated':
        // v2 live usage: merge into the stored session instead of replacing
        // it, since the event carries only cost + tokens.
        final sid = props['sessionID']?.toString();
        if (sid != null && sid.isNotEmpty) {
          final existing = sessionsById[sid];
          if (existing != null) {
            final cost = props['cost'];
            _markSessionChanged(sid);
            sessionsById[sid] = existing.copyWith(
              cost: cost is num ? cost.toDouble() : null,
              tokens: props['tokens'] is Map
                  ? Tokens.fromJson(props['tokens'])
                  : null,
            );
            _notifyListeners();
          }
        }
        break;

      case 'message.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final msg = MessageInfo.fromJson(info);
          if (msg.role == 'user' && !_openTurns.containsKey(msg.sessionID)) {
            _openTurns[msg.sessionID] = DateTime.now();
          }
          if (msg.role == 'assistant') {
            _markSessionChanged(msg.sessionID);
            final working =
                (msg.time == null || !msg.time!.isDone) &&
                msg.errorText == null;
            if (!working) _noteObservedCompletion(msg.id);
            // A Codex turn can contain several completed items and still be
            // running. Its explicit session status owns the busy state.
            if (capabilities.messageCompletionEndsRun) {
              if (working) {
                busySessions.add(msg.sessionID);
                _markSessionAttentionActive(msg.sessionID);
              } else {
                busySessions.remove(msg.sessionID);
              }
            }
            _notifyListeners();
          }
        }
        break;

      case 'message.removed':
        final sid = props['sessionID']?.toString();
        if (sid != null && sid.isNotEmpty) {
          _markSessionChanged(sid);
          unawaited(_refreshOneSession(sid));
        }
        break;

      case 'permission.asked':
        _handlePermission(props);
        break;

      case 'permission.v2.asked':
        _handlePermissionV2(props);
        break;

      case 'permission.updated':
        _handleLegacyPermission(props);
        break;

      case 'permission.replied':
      case 'permission.v2.replied':
        _handlePermissionReply(props);
        break;

      // ---- OpenCode 2 interaction envelopes ----
      // Emitted by the v2 event adapter (lib/api2/gateway_events.dart):
      //   form.v2.created                 {form: Form.Info (raw v2 JSON)}
      //   form.v2.replied                 {id, sessionID}
      //   form.v2.cancelled               {id, sessionID}
      //   session.inbox.enqueued          {sessionID, inboxID, item}
      //   session.inbox.delivered         {sessionID, inboxID}
      //   session.inbox.cancelled         {sessionID, inboxID}
      //   session.inbox.delivery.changed  {sessionID, inboxID, delivery}
      case 'form.v2.created':
        if (supportsForms) _handleFormCreated(props);
        break;

      case 'form.v2.replied':
      case 'form.v2.cancelled':
        if (!supportsForms) break;
        final formID = props['id']?.toString() ?? '';
        if (formID.isNotEmpty) _resolveForm(formID);
        break;

      case 'session.inbox.enqueued':
        _handleInboxEnqueued(props);
        break;

      case 'session.inbox.delivered':
      case 'session.inbox.cancelled':
        _handleInboxRemoved(props);
        break;

      case 'session.inbox.delivery.changed':
        _handleInboxDeliveryChanged(props);
        break;

      case 'question.asked':
      case 'question.updated':
        questionsLoading = false;
        final question = PendingQuestion.fromJson(props);
        if (question.id.isNotEmpty && question.sessionID.isNotEmpty) {
          _markQuestionChanged(question.id);
          _resolvedQuestionIDs.remove(question.id);
          _v2QuestionSessions.remove(question.id);
          questions[question.id] = question;
          _syncInputAlerts();
          _notifyListeners();
        }
        break;

      case 'question.v2.asked':
        questionsLoading = false;
        final question = PendingQuestion.fromJson(props);
        if (question.id.isNotEmpty && question.sessionID.isNotEmpty) {
          _markQuestionChanged(question.id);
          _resolvedQuestionIDs.remove(question.id);
          _v2QuestionSessions[question.id] = question.sessionID;
          questions[question.id] = question;
          _syncInputAlerts();
          _notifyListeners();
        }
        break;

      case 'question.replied':
      case 'question.rejected':
      case 'question.v2.replied':
      case 'question.v2.rejected':
        questionsLoading = false;
        final id = props['requestID']?.toString() ?? props['id']?.toString();
        if (id != null && id.isNotEmpty) {
          _markQuestionChanged(id);
          _resolvedQuestionIDs.add(id);
          _v2QuestionSessions.remove(id);
          if (questions.remove(id) != null) {
            _syncInputAlerts();
            _notifyListeners();
          }
        }
        break;

      case 'session.status':
        final sid = props['sessionID']?.toString();
        final rawStatus = props['status'];
        final sessionStatus = rawStatus is Map
            ? rawStatus['type']?.toString()
            : rawStatus?.toString();
        if (sid != null && sid.isNotEmpty) {
          _markSessionChanged(sid);
          switch (sessionStatus) {
            case 'idle':
              if (_phoneChatDispatch.contains(sid)) {
                unawaited(_refreshBusySessionStatuses());
              }
              busySessions.remove(sid);
              _openTurns.remove(sid);
              retryStates.remove(sid);
              _settleSessionAttention(sid, CodingAlertKind.complete);
              unawaited(_refreshOneSession(sid));
              _resumeDeferredProviderHeal();
              break;
            case 'busy':
              _phoneChatDispatch.observeBusy(sid);
              busySessions.add(sid);
              retryStates.remove(sid);
              _markSessionAttentionActive(sid);
              break;
            case 'retry':
              _phoneChatDispatch.observeBusy(sid);
              busySessions.add(sid);
              final retry = SessionRetryState.fromStatusJson(rawStatus);
              if (retry != null) retryStates[sid] = retry;
              _markSessionAttentionActive(sid);
              break;
            default:
              _invalidatePhoneChatStatus();
              break;
          }
          _notifyListeners();
        }
        break;

      case 'session.error':
        final sid = props['sessionID']?.toString();
        // The person's own Stop ends the run with an "aborted" error. It is
        // not a failure: never "Failed" in Inbox or Work, never counted as
        // needing them, no error alert and no error text (F3).
        final stopped = sessionErrorIsStop(props['error']);
        if (sid != null) {
          if (stopped) {
            _attentionActiveSessions.remove(sid);
          } else {
            _failedAttentionSessions[sid] = (
              at: DateTime.now(),
              revision: _attentionTransportRevision,
            );
          }
          _markSessionChanged(sid);
          busySessions.remove(sid);
          _openTurns.remove(sid);
          retryStates.remove(sid);
          if (!stopped) _settleSessionAttention(sid, CodingAlertKind.error);
          _resumeDeferredProviderHeal();
        }
        final err = props['error'];
        if (!stopped && err is Map<String, dynamic>) {
          final data = err['data'];
          final message =
              (err['message']?.toString() ??
                      (data is Map ? data['message']?.toString() : null))
                  ?.trim();
          // A blank server message must not blank the banner: fall back to
          // copy chosen by the error name (v1 `name`, v2 `type`).
          lastError = message == null || message.isEmpty
              ? sessionErrorFallbackText(err['name']?.toString())
              : message;
        }
        _notifyListeners();
        break;

      case 'session.idle':
        final sid = props['sessionID']?.toString();
        if (sid != null) {
          if (_phoneChatDispatch.contains(sid)) {
            unawaited(_refreshBusySessionStatuses());
          }
          _markSessionChanged(sid);
          busySessions.remove(sid);
          _openTurns.remove(sid);
          retryStates.remove(sid);
          _settleSessionAttention(sid, CodingAlertKind.complete);
          unawaited(_refreshOneSession(sid));
          _resumeDeferredProviderHeal();
          _notifyListeners();
        }
        break;

      case 'session.compacted':
        final sid = props['sessionID']?.toString();
        if (sid != null && sid.isNotEmpty) {
          _markSessionChanged(sid);
          unawaited(_refreshOneSession(sid));
        }
        break;

      case 'pty.created':
      case 'pty.updated':
      case 'pty.exited':
      case 'pty.deleted':
        lastPtyEvent = env;
        ptyRevision += 1;
        _notifyListeners();
        break;

      case 'message.part.updated':
        // Parsed only as far as the ongoing notification needs; the chat
        // screen builds the full part from the event bus below. Deliberately
        // no notifyListeners: every streamed delta lands here.
        final part = props['part'];
        if (part is Map && part['type'] == 'tool') {
          final sid = part['sessionID']?.toString() ?? '';
          final state = part['state'];
          final toolStatus = state is Map ? state['status']?.toString() : null;
          if (sid.isNotEmpty && toolStatus != null) {
            final before = _runningToolDetail[sid];
            if (toolStatus == 'running') {
              _runningToolDetail[sid] = ConnectionController.toolSentence(
                part['tool']?.toString() ?? '',
              );
            } else if (toolStatus == 'completed' || toolStatus == 'error') {
              _runningToolDetail.remove(sid);
            }
            if (_runningToolDetail[sid] != before) _publishLiveStatus();
          }
        }
        break;

      default:
        break;
    }
    _eventBus.add(env);
  }
}
