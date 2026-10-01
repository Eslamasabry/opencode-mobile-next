part of '../chat_screen.dart';

// The live event stream: server events folded into the transcript, with
// streaming deltas coalesced into bounded rebuilds.

/// Counts coalesced streaming-rebuild flushes. Tests use it to assert that a
/// burst of N part deltas produces a bounded number of transcript rebuilds.
@visibleForTesting
int debugChatStreamFlushes = 0;

mixin _ChatStreamFields {
  final Map<String, int> _messageVersions = {};
  final Map<String, int> _partVersions = {};
  final Map<String, Map<String, Part>> _deferredParts = {};
  final Map<String, MessageInfo> _deferredMessages = {};
  final Map<String, List<({String field, String delta})>> _deferredPartDeltas =
      {};
  int _eventVersion = 0;
  bool _streamFlushScheduled = false;
  bool _streamDirty = false;
  Timer? _streamFlushTimer;
}

extension _ChatStream on _ChatScreenState {
  ConnectionController _readConn() {
    final ctx = context;
    final container = ProviderScope.containerOf(ctx, listen: false);
    return container.read(connProvider);
  }

  void _onEvent(EventEnvelope env) {
    _observeVoiceReplyStatus(env);
    if (!mounted) return;
    // Inbox delivery creates a canonical server message without a message event.
    // Refresh every delivery; the pending inbox item may already be removed.
    if ((env.type == 'session.skill.changed' ||
            env.type == 'session.inbox.delivered') &&
        env.properties['sessionID'] == widget.sessionID) {
      _scheduleRecentHistoryRefresh();
    }
    if ((env.type == 'session.compacted' ||
            env.type == 'session.history.reset') &&
        env.properties['sessionID'] == widget.sessionID) {
      if (env.type == 'session.history.reset' &&
          _conn.sessionsById[widget.sessionID]?.stagedRevert == null) {
        final removedFrom = env.properties['removedFrom'] as String?;
        // A cleared boundary may mean commit, clear, or an ambiguous response.
        // Never reveal the cached staged tail before authoritative hydration.
        _messages.removeWhere(
          (message) =>
              removedFrom == null ||
              message.info.id.compareTo(removedFrom) >= 0,
        );
        _deferredMessages.clear();
        _deferredParts.clear();
        _deferredPartDeltas.clear();
      }
      unawaited(_load(resetHistory: true));
    }
    if (env.type.startsWith('shell.')) {
      unawaited(_loadRunningShells());
    }
    if (env.type == 'session.shell.changed' &&
        env.properties['sessionID'] == widget.sessionID) {
      unawaited(_load());
      unawaited(_loadRunningShells());
    }
    switch (env.type) {
      case 'message.part.updated':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final partJson = env.properties['part'];
        if (partJson is Map<String, dynamic>) {
          final p = Part.fromJson(partJson);
          final mid = p.messageID;
          if (mid != null) {
            _setChatState(() {
              _partVersions[_partKey(mid, p.id ?? p.callID ?? '')] =
                  ++_eventVersion;
              _upsertPart(mid, p);
            });
          }
        }
        break;
      case 'message.part.delta':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final messageID = env.properties['messageID']?.toString();
        final partID = env.properties['partID']?.toString();
        final field = env.properties['field']?.toString();
        final delta = env.properties['delta']?.toString();
        if (messageID != null &&
            messageID.isNotEmpty &&
            partID != null &&
            partID.isNotEmpty &&
            field != null &&
            delta != null &&
            _isSupportedDeltaField(field)) {
          // Deltas mutate the model synchronously (versioning and deferred
          // bookkeeping must stay ordered against hydration), but the
          // rebuild is coalesced: one setState per burst, then at most one
          // per ~50ms while the stream keeps flowing.
          _partVersions[_partKey(messageID, partID)] = ++_eventVersion;
          if (!_applyPartDelta(messageID, partID, field, delta)) {
            _deferredPartDeltas
                .putIfAbsent(_partKey(messageID, partID), () => [])
                .add((field: field, delta: delta));
          }
          _scheduleStreamFlush();
        }
        break;
      case 'message.part.removed':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final messageID = env.properties['messageID']?.toString();
        final partID = env.properties['partID']?.toString();
        if (messageID != null &&
            messageID.isNotEmpty &&
            partID != null &&
            partID.isNotEmpty) {
          _setChatState(() {
            final key = _partKey(messageID, partID);
            _partVersions[key] = ++_eventVersion;
            _deferredPartDeltas.remove(key);
            _deferredParts[messageID]?.remove(partID);
            final message = _messageByID(messageID);
            message?.parts.removeWhere(
              (part) => part.id == partID || part.callID == partID,
            );
          });
        }
        break;
      case 'message.removed':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final messageID = env.properties['messageID']?.toString();
        if (messageID != null && messageID.isNotEmpty) {
          _setChatState(() {
            _messageVersions[messageID] = ++_eventVersion;
            _resetHistoryOnLoad = true;
            _olderNeedsReload = true;
            _messages.removeWhere((message) => message.info.id == messageID);
            _deferredParts.remove(messageID);
            _deferredMessages.remove(messageID);
            _deferredPartDeltas.removeWhere(
              (key, _) => key.startsWith('$messageID\u0000'),
            );
            _pendingSends.removeWhere(
              (pending) =>
                  pending.localID == messageID ||
                  pending.canonicalID == messageID,
            );
          });
        }
        break;
      case 'message.updated':
        final info = env.properties['info'];
        if (info is Map<String, dynamic>) {
          final msg = MessageInfo.fromJson(info);
          if (msg.sessionID != widget.sessionID) break;
          final watch = _voiceReplyWatch;
          if (watch != null &&
              msg.role == 'user' &&
              !watch.existingMessageIDs.contains(msg.id)) {
            watch.liveUserIDs.add(msg.id);
          }
          _setChatState(() {
            if (msg.role == 'assistant' && msg.errorText != null) {
              _promptError = msg.errorText;
              _recoverFromPromptError(msg.errorText);
            } else if (msg.role == 'assistant' &&
                _promptError != null &&
                !_messages.any((known) => known.info.id == msg.id)) {
              // A new step after the error: the turn moved on (the server
              // retried, or the agent carried on). A banner still saying
              // something is wrong would now be false.
              _promptError = null;
            }
            _messageVersions[msg.id] = ++_eventVersion;
            if (!_reconcilePendingMessage(
              msg,
              canonicalParts: _deferredParts[msg.id]?.values.toList(),
            )) {
              final idx = _messages.indexWhere((m) => m.info.id == msg.id);
              if (idx >= 0) {
                _messages[idx] = MessageWithParts(
                  info: msg,
                  parts: _messages[idx].parts,
                );
              } else {
                final knownTimes = _messages
                    .where((m) => !m.info.id.startsWith('local-'))
                    .map((m) => m.info.time?.created)
                    .whereType<int>();
                final created = msg.time?.created;
                if (_olderCursor != null &&
                    (created == null ||
                        knownTimes.isEmpty ||
                        created <= knownTimes.last)) {
                  _deferredMessages[msg.id] = msg;
                  // A late edit/completion is not a new row. If it might be in
                  // the recent window, ask the server to establish its order.
                  if (created == null ||
                      knownTimes.isEmpty ||
                      created >= knownTimes.first) {
                    _scheduleRecentHistoryRefresh();
                  }
                  return;
                }
                _messages.add(MessageWithParts(info: msg));
              }
            }
            final deferred = _deferredParts.remove(msg.id);
            for (final part in deferred?.values ?? const <Part>[]) {
              _upsertPart(msg.id, part);
            }
          });
        }
        break;
      case 'session.idle':
      case 'session.status':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final raw = env.properties['status'];
        final status = env.type == 'session.idle'
            ? 'idle'
            : raw is Map
            ? raw['type']?.toString()
            : raw?.toString();
        // Idle without a busy first (a missed event, or a turn that ended
        // at once): the turn this phone started is over all the same.
        if (status == 'idle' && !_sending && _localTurnSince != null) {
          _setChatState(() => _localTurnSince = null);
        }
        break;
      case 'session.error':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        _setChatState(() {
          if (!_sending) _localTurnSince = null;
          _promptError = _eventErrorMessage(env.properties['error']);
        });
        _recoverFromPromptError(_promptError);
        break;
      case 'session.updated':
        final info = env.properties['info'];
        if (info is Map<String, dynamic> &&
            info['id']?.toString() == widget.sessionID) {
          if (mounted) _setChatState(() {});
        }
        break;
    }
    _historyChanges.value++;
    _checkVoiceReply();
  }

  String _partKey(String messageID, String partID) => '$messageID\u0000$partID';

  // ----- streaming delta batching (C1) -----
  //
  // Leading edge: the first delta of an idle stream flushes on the next
  // microtask, so one synchronous SSE burst costs one setState. Trailing
  // edge: each flush opens a ~50ms window; deltas landing inside it only
  // mutate the model and are flushed together when the window closes.
  static const _streamFlushInterval = Duration(milliseconds: 50);

  void _scheduleStreamFlush() {
    if (_streamFlushTimer != null) {
      _streamDirty = true;
      return;
    }
    if (_streamFlushScheduled) return;
    _streamFlushScheduled = true;
    scheduleMicrotask(() {
      _streamFlushScheduled = false;
      if (mounted) _flushStreamDeltas();
    });
  }

  void _flushStreamDeltas() {
    debugChatStreamFlushes++;
    _streamDirty = false;
    _setChatState(() {});
    _historyChanges.value++;
    _streamFlushTimer?.cancel();
    _streamFlushTimer = Timer(_streamFlushInterval, () {
      _streamFlushTimer = null;
      if (_streamDirty && mounted) _flushStreamDeltas();
    });
  }

  /// A "Model not found" error means the server's model list moved under
  /// the selection (typically a provider it only just loaded). Re-read the
  /// catalog so the picker and the selected model reflect what it can serve.
  void _recoverFromPromptError(String? text) {
    if (text == null) return;
    final kind = MessageErrorKind.refineFromText(
      MessageErrorKind.unknown,
      text,
    );
    if (kind != MessageErrorKind.modelNotFound) return;
    unawaited(_readConn().refreshCatalog());
  }

  String _eventErrorMessage(Object? raw) {
    if (raw is Map) {
      final data = raw['data'];
      final nested = data is Map ? data['message'] : null;
      return (raw['message'] ?? nested ?? raw['name'])?.toString() ??
          _chatL10n(context).chatUiOpenCodeCouldNotCompleteThisPrompt;
    }
    final text = raw?.toString().trim();
    return text?.isNotEmpty == true
        ? text!
        : _chatL10n(context).chatUiOpenCodeCouldNotCompleteThisPrompt;
  }

  MessageWithParts? _messageByID(String messageID) {
    for (final message in _messages) {
      if (message.info.id == messageID) return message;
    }
    return null;
  }

  bool _reconcilePendingMessage(
    MessageInfo info, {
    List<Part>? canonicalParts,
    _PendingSend? pendingSend,
  }) {
    if (info.role != 'user') return false;
    _PendingSend? pending = pendingSend;
    for (final candidate in _pendingSends) {
      if (candidate.dispatchedMessageID == info.id ||
          candidate.canonicalID == info.id) {
        pending = candidate;
        break;
      }
    }
    if (pending == null) {
      final parts = canonicalParts ?? _messageByID(info.id)?.parts ?? const [];
      final matches = _pendingSends
          .where(
            (candidate) =>
                candidate.canonicalID == null &&
                candidate.dispatchedMessageID == null &&
                _matchesPendingPrompt(parts, candidate),
          )
          .toList();
      if (matches.isNotEmpty) {
        final created = info.time?.created;
        if (created != null) {
          matches.sort(
            (a, b) => (a.createdAt - created).abs().compareTo(
              (b.createdAt - created).abs(),
            ),
          );
        }
        pending = matches.first;
      }
    }
    if (pending == null) return false;
    if (pending.dispatchedMessageID != null &&
        pending.dispatchedMessageID != info.id) {
      return false;
    }

    final localIndex = _messages.indexWhere(
      (message) => message.info.id == pending!.localID,
    );
    final canonicalIndex = _messages.indexWhere(
      (message) => message.info.id == info.id,
    );
    final parts =
        canonicalParts ??
        (canonicalIndex >= 0 && _messages[canonicalIndex].parts.isNotEmpty
            ? _messages[canonicalIndex].parts
            : localIndex >= 0
            ? _messages[localIndex].parts
            : <Part>[]);
    final replacement = MessageWithParts(info: info, parts: parts);
    if (localIndex >= 0) {
      _messages[localIndex] = replacement;
      if (canonicalIndex >= 0 && canonicalIndex != localIndex) {
        _messages.removeAt(canonicalIndex);
      }
    } else if (canonicalIndex >= 0) {
      _messages[canonicalIndex] = replacement;
    } else {
      _messages.add(replacement);
    }
    pending.canonicalID = info.id;
    _messageVersions.remove(pending.localID);
    if (pending.requestComplete) _pendingSends.remove(pending);
    return true;
  }

  void _upsertPart(String messageID, Part part) {
    final bundle = _messageByID(messageID);
    if (bundle == null) {
      // An edit or tool update may belong to unloaded history. A part alone
      // cannot establish the message's role or its place in the transcript.
      final partID = part.id ?? part.callID ?? '';
      var deferred = part;
      for (final delta
          in _deferredPartDeltas.remove(_partKey(messageID, partID)) ??
              const <({String field, String delta})>[]) {
        deferred =
            _partWithDelta(deferred, delta.field, delta.delta) ?? deferred;
      }
      _deferredParts.putIfAbsent(messageID, () => {})[partID] = deferred;
      return;
    }
    final idx = bundle.parts.indexWhere(
      (p) =>
          (part.callID != null && p.callID == part.callID) ||
          (part.id != null && p.id == part.id && p.type == part.type),
    );
    if (idx >= 0) {
      bundle.parts[idx] = part;
    } else {
      final optimisticIndex = bundle.info.role == 'user'
          ? bundle.parts.indexWhere(
              (candidate) =>
                  candidate.id == null &&
                  candidate.type == part.type &&
                  (part.type != 'file' || candidate.filename == part.filename),
            )
          : -1;
      if (optimisticIndex >= 0) {
        bundle.parts[optimisticIndex] = part;
      } else {
        bundle.parts.add(part);
      }
    }
    final key = _partKey(messageID, part.id ?? part.callID ?? '');
    final deferred = _deferredPartDeltas.remove(key);
    if (deferred != null) {
      for (final delta in deferred) {
        _applyPartDelta(
          messageID,
          part.id ?? part.callID ?? '',
          delta.field,
          delta.delta,
        );
      }
    }
    if (bundle.info.role == 'user') {
      _reconcilePendingMessage(bundle.info, canonicalParts: bundle.parts);
    }
  }

  bool _isSupportedDeltaField(String field) =>
      field == 'text' ||
      field == 'input' ||
      field == 'raw' ||
      field == 'state.input' ||
      field == 'state.raw';

  bool _applyPartDelta(
    String messageID,
    String partID,
    String field,
    String delta,
  ) {
    final bundle = _messageByID(messageID);
    if (bundle == null) {
      final part = _deferredParts[messageID]?[partID];
      if (part == null) return false;
      final updated = _partWithDelta(part, field, delta);
      if (updated == null) return false;
      _deferredParts[messageID]![partID] = updated;
      return true;
    }
    final index = bundle.parts.indexWhere(
      (part) => part.id == partID || part.callID == partID,
    );
    if (index < 0) return false;
    final part = bundle.parts[index];
    final updated = _partWithDelta(part, field, delta);
    if (updated == null) return false;
    bundle.parts[index] = updated;
    return true;
  }

  Part? _partWithDelta(Part part, String field, String delta) {
    if (field == 'text' && (part.type == 'text' || part.type == 'reasoning')) {
      return _copyPart(part, text: '${part.text}$delta');
    }
    if (part.type == 'tool' &&
        (field == 'input' ||
            field == 'raw' ||
            field == 'state.input' ||
            field == 'state.raw')) {
      final state = part.toolState;
      return _copyPart(
        part,
        toolState: ToolState(
          status: state.status,
          title: state.title,
          inputJson: '${state.inputJson ?? ''}$delta',
          output: state.output,
          metadata: state.metadata,
          outputFiles: state.outputFiles,
        ),
      );
    }
    return null;
  }

  Part _copyPart(Part part, {String? text, ToolState? toolState}) => Part(
    id: part.id,
    type: part.type,
    text: text ?? part.text,
    messageID: part.messageID,
    callID: part.callID,
    toolName: part.toolName,
    toolState: toolState ?? part.toolState,
    mime: part.mime,
    filename: part.filename,
    url: part.url,
    synthetic: part.synthetic,
  );
}
