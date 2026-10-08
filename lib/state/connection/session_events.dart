part of '../connection.dart';

// Event handlers for sessions, their messages and their runs.

extension _ConnectionControllerSessionEventsImpl on ConnectionController {
  /// Session record events: create, update, metadata, revert, selection,
  /// deletion and usage.
  void _onSessionEvent(EventEnvelope env, Map<String, dynamic> props) {
    switch (env.type) {
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
    }
  }

  /// Message events, including the streamed part updates.
  void _onMessageEvent(EventEnvelope env, Map<String, dynamic> props) {
    switch (env.type) {
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
                _openTurns.remove(msg.sessionID);
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
                _shellStrings(),
              );
            } else if (toolStatus == 'completed' || toolStatus == 'error') {
              _runningToolDetail.remove(sid);
            }
            if (_runningToolDetail[sid] != before) _publishLiveStatus();
          }
        }
        break;
    }
  }

  /// Run status events: busy, retry, idle, error and compaction.
  void _onRunStatusEvent(EventEnvelope env, Map<String, dynamic> props) {
    switch (env.type) {
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
    }
  }
}
