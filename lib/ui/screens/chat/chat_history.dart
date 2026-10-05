part of '../chat_screen.dart';

// Loading the conversation's history: the first read, older pages, and
// merging a fresh read with what is already on screen.

typedef _HistoryScope = ({
  ServerGateway? api,
  int location,
  String? profile,
  String session,
});

mixin _ChatHistoryFields {
  bool _freshMergeRecorded = false;
  Timer? _historyRefreshTimer;
  bool _historyRefreshPending = false;
  int _loadGeneration = 0;
  String? _olderCursor;
  Object? _olderError;
  bool _loadingOlder = false;
  bool _resetHistoryOnLoad = true;
  bool _olderNeedsReload = false;
  final Set<String> _usedOlderCursors = {};
  _HistoryScope? _loadedHistoryScope;
  _HistoryScope? _requestedHistoryScope;
}

extension _ChatHistory on _ChatScreenState {
  _HistoryScope get _historyScope => (
    api: _conn.api,
    location: _conn.locationRevision,
    profile: _conn.profile?.id,
    session: widget.sessionID,
  );

  void _scheduleRecentHistoryRefresh() {
    _historyRefreshPending = true;
    if (_historyRefreshTimer != null) return;
    _historyRefreshTimer = Timer(const Duration(milliseconds: 150), () {
      _historyRefreshTimer = null;
      if (!mounted || _loading || _loadingOlder) return;
      _historyRefreshPending = false;
      unawaited(_load());
    });
  }

  bool _currentHistory(int generation, _HistoryScope scope) =>
      mounted && generation == _loadGeneration && scope == _historyScope;

  ({String id, int index, double alignment})? _historyAnchor() {
    final count = _renderedMessageCount;
    final positions =
        _messagePositions.itemPositions.value
            .where(
              (position) =>
                  position.index < count &&
                  position.itemTrailingEdge > 0 &&
                  position.itemLeadingEdge < 1,
            )
            .toList()
          ..sort((a, b) => a.itemLeadingEdge.compareTo(b.itemLeadingEdge));
    if (positions.isEmpty) return null;
    final position = positions.firstWhere(
      (item) => item.itemLeadingEdge >= 0,
      orElse: () => positions.first,
    );
    return (
      id: _messages[count - 1 - position.index].info.id,
      index: position.index,
      alignment: position.itemLeadingEdge.clamp(0.0, 1.0),
    );
  }

  void _restoreHistoryAnchor(
    ({String id, int index, double alignment})? anchor,
    int generation,
  ) {
    if (anchor == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          generation != _loadGeneration ||
          !_messageScroll.isAttached) {
        return;
      }
      final chronological = _messages.indexWhere(
        (message) => message.info.id == anchor.id,
      );
      if (chronological < 0) {
        if (_renderedMessageCount > 0) _messageScroll.jumpTo(index: 0);
        return;
      }
      final index = _renderedMessageCount - 1 - chronological;
      if (index >= 0 && index != anchor.index) {
        _messageScroll.jumpTo(index: index, alignment: anchor.alignment);
      }
    });
  }

  void _retainPinnedEnd(String? lastVisibleID) {
    if (!_awayFromLatest || lastVisibleID == null) return;
    final index = _messages.indexWhere(
      (message) => message.info.id == lastVisibleID,
    );
    _pinnedMessageCount = index < 0 ? _messages.length : index + 1;
  }

  Future<void> _load({bool resetHistory = false}) async {
    final generation = ++_loadGeneration;
    final versionAtStart = _eventVersion;
    final scope = _historyScope;
    _requestedHistoryScope = scope;
    if (resetHistory || _loadedHistoryScope != scope) {
      _resetHistoryOnLoad = true;
    }
    _setChatState(() {
      _loading = true;
      _loadingOlder = false;
      _error = null;
    });
    _historyChanges.value++;
    // Inbox events are volatile: reconcile this session's pending sends
    // from REST whenever the transcript (re)hydrates. No-op on v1.
    unawaited(_conn.refreshInbox(widget.sessionID));
    try {
      final api = scope.api;
      if (api == null) {
        // Reached synchronously from initState on an offline open.
        throw ProductException(_reconnectingWords(context));
      }
      // The controller's newest-page read is shared with a prefetch fired
      // on the tap that opened this chat (one HTTP call for both) and saves
      // the opening excerpt for next time. A host without a saved,
      // connected server (the demo, tests) reads the gateway directly, and
      // so does watching: its poll must not rewrite the saved excerpt.
      final page =
          !_conn.isIsolated &&
              !_watching &&
              identical(api, _conn.api) &&
              _conn.canLoadSessionTail(scope.session)
          ? await _conn.loadSessionTail(scope.session)
          : await readHistoryAtStagedBoundary(
              api,
              scope.session,
              boundary:
                  _conn.sessionsById[scope.session]?.stagedRevert?.messageID,
              isCurrent: () => _currentHistory(generation, scope),
            );
      if (!_currentHistory(generation, scope)) return;
      final anchor = _historyAnchor();
      final pinnedEnd = _renderedMessageCount == 0
          ? null
          : _messages[_renderedMessageCount - 1].info.id;
      final incomingIDs = page.items.map((message) => message.info.id).toSet();
      final overlap = _messages.indexWhere(
        (message) => incomingIDs.contains(message.info.id),
      );
      final retainPrefix = !_resetHistoryOnLoad && page.hasMore && overlap >= 0;
      final prefix = retainPrefix
          ? _messages.take(overlap).toList()
          : <MessageWithParts>[];
      _setChatState(() {
        _messages = _mergeHydratedMessages(
          page.items,
          versionAtStart,
          prefix: prefix,
        );
        if (!retainPrefix) {
          _olderCursor = page.hasMore ? page.nextCursor : null;
          _usedOlderCursors.clear();
        }
        _loadedHistoryScope = scope;
        _resetHistoryOnLoad = false;
        _olderNeedsReload = false;
        _olderError = null;
        _retainPinnedEnd(pinnedEnd);
        if (anchor != null &&
            !_messages.any((message) => message.info.id == anchor.id)) {
          _awayFromLatest = false;
          _pinnedMessageCount = null;
          _composerNote = _chatL10n(context).historyRefreshed;
        }
      });
      if (!_freshMergeRecorded) {
        _freshMergeRecorded = true;
        PerfTrace.recordSince('chat.open_to_fresh', _openedMicros);
      }
      _restoreHistoryAnchor(anchor, generation);
      _landOnFailedTurn();
      _runRouteMenuAction();
    } catch (e) {
      if (!_currentHistory(generation, scope)) return;
      _setChatState(() => _error = e);
      if (_messages.isNotEmpty) _showComposerNote(productErrorText(e));
    } finally {
      if (mounted && generation == _loadGeneration) {
        _setChatState(() => _loading = false);
        _historyChanges.value++;
        if (_historyRefreshPending) _scheduleRecentHistoryRefresh();
      }
    }
  }

  Future<void> _loadOlder() async {
    final cursor = _olderCursor;
    if (_loading || _loadingOlder || cursor == null) return;
    if (_olderNeedsReload || _resetHistoryOnLoad) {
      await _load(resetHistory: true);
      if (mounted) {
        _setChatState(() => _olderError = _error);
        _historyChanges.value++;
      }
      return;
    }
    final generation = ++_loadGeneration;
    final scope = _historyScope;
    final versionAtStart = _eventVersion;
    _setChatState(() {
      _loadingOlder = true;
      _olderError = null;
    });
    _historyChanges.value++;
    final expiredMessage = _chatL10n(context).historyCursorExpired;
    try {
      final api = scope.api;
      if (api == null) {
        throw ProductException(_reconnectingWords(context));
      }
      final page = await api.messagePage(scope.session, cursor: cursor);
      if (!_currentHistory(generation, scope)) return;
      final next = page.hasMore ? page.nextCursor : null;
      if (next != null &&
          (next == cursor || _usedOlderCursors.contains(next))) {
        _olderNeedsReload = true;
        throw ProductException(expiredMessage);
      }
      final anchor = _historyAnchor();
      final pinnedEnd = _renderedMessageCount == 0
          ? null
          : _messages[_renderedMessageCount - 1].info.id;
      _setChatState(() {
        _messages = _mergeHydratedMessages(
          page.items,
          versionAtStart,
          preserveUnseen: true,
          reconcilePending: false,
        );
        _usedOlderCursors.add(cursor);
        _olderCursor = next;
        _retainPinnedEnd(pinnedEnd);
      });
      _restoreHistoryAnchor(anchor, generation);
    } catch (error) {
      if (!_currentHistory(generation, scope)) return;
      _setChatState(() {
        _olderError = error;
        if (error is ApiException &&
            (error.statusCode == 400 || error.statusCode == 410)) {
          _olderNeedsReload = true;
        }
      });
    } finally {
      if (mounted && generation == _loadGeneration) {
        _setChatState(() => _loadingOlder = false);
        _historyChanges.value++;
        if (_historyRefreshPending) _scheduleRecentHistoryRefresh();
      }
    }
  }

  Widget _olderHistoryRow() {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final error = _olderError;
    return Padding(
      key: const ValueKey('chat-older-history'),
      padding: EdgeInsets.all(tokens.space4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        spacing: tokens.space2,
        children: [
          if (error != null)
            KitText(
              productErrorText(error),
              tone: KitTextTone.danger,
              textAlign: TextAlign.center,
            ),
          KitButton.secondary(
            key: const ValueKey('chat-load-older'),
            expand: false,
            working: _loadingOlder,
            onPressed: _loading || _loadingOlder ? null : _loadOlder,
            label: _olderNeedsReload
                ? l10n.historyReload
                : error != null
                ? l10n.refreshRetry
                : l10n.historyLoadOlder,
          ),
        ],
      ),
    );
  }

  List<MessageWithParts> _mergeHydratedMessages(
    List<MessageWithParts> hydrated,
    int versionAtStart, {
    List<MessageWithParts> prefix = const [],
    bool preserveUnseen = false,
    bool reconcilePending = true,
  }) {
    for (final message in hydrated) {
      if (!reconcilePending) break;
      if (message.info.role != 'user') continue;
      for (final pending in List<_PendingSend>.from(_pendingSends)) {
        if (pending.canonicalID != null) continue;
        if (_matchesPendingPrompt(message.parts, pending)) {
          _reconcilePendingMessage(
            message.info,
            canonicalParts: message.parts,
            pendingSend: pending,
          );
          break;
        }
      }
    }

    final currentByID = {
      for (final entry in _deferredMessages.entries)
        entry.key: MessageWithParts(info: entry.value),
      for (final message in _messages) message.info.id: message,
    };
    final merged = <MessageWithParts>[];
    final hydratedIDs = <String>{};
    for (final snapshot in hydrated) {
      final messageID = snapshot.info.id;
      if (!hydratedIDs.add(messageID)) continue;
      final current = currentByID[messageID];
      _deferredMessages.remove(messageID);
      final messageChanged =
          (_messageVersions[messageID] ?? 0) > versionAtStart;
      if (messageChanged && current == null) continue;

      final currentParts = {
        ...?_deferredParts.remove(messageID),
        for (final part in current?.parts ?? const <Part>[])
          if ((part.id ?? part.callID)?.isNotEmpty == true)
            (part.id ?? part.callID)!: part,
      };
      final parts = <Part>[];
      final includedPartIDs = <String>{};
      for (final snapshotPart in snapshot.parts) {
        final partID = snapshotPart.id ?? snapshotPart.callID;
        if (partID == null || partID.isEmpty) {
          parts.add(snapshotPart);
          continue;
        }
        includedPartIDs.add(partID);
        if ((_partVersions[_partKey(messageID, partID)] ?? 0) >
            versionAtStart) {
          final newerPart = currentParts[partID];
          if (newerPart != null) {
            parts.add(newerPart);
          } else {
            Part? deferredPart = snapshotPart;
            final key = _partKey(messageID, partID);
            final deferred = _deferredPartDeltas[key];
            for (final delta
                in deferred ?? const <({String field, String delta})>[]) {
              deferredPart = _partWithDelta(
                deferredPart!,
                delta.field,
                delta.delta,
              );
              if (deferredPart == null) break;
            }
            if (deferred != null) {
              parts.add(deferredPart ?? snapshotPart);
              _deferredPartDeltas.remove(key);
            }
          }
        } else {
          parts.add(snapshotPart);
        }
      }
      for (final entry in currentParts.entries) {
        if (!includedPartIDs.contains(entry.key) &&
            (_partVersions[_partKey(messageID, entry.key)] ?? 0) >
                versionAtStart) {
          parts.add(entry.value);
        }
      }
      merged.add(
        MessageWithParts(
          info: messageChanged ? current!.info : snapshot.info,
          parts: parts,
        ),
      );
      _deferredPartDeltas.removeWhere(
        (key, _) =>
            key.startsWith('$messageID\u0000') &&
            (_partVersions[key] ?? 0) <= versionAtStart,
      );
    }

    final prefixIDs = prefix.map((message) => message.info.id).toSet();
    for (final current in _messages) {
      if (hydratedIDs.contains(current.info.id)) continue;
      final isPending = _pendingSends.any(
        (pending) =>
            pending.localID == current.info.id ||
            pending.canonicalID == current.info.id,
      );
      final hasNewMessage =
          (_messageVersions[current.info.id] ?? 0) > versionAtStart;
      final hasNewPart = current.parts.any((part) {
        final partID = part.id ?? part.callID;
        return partID != null &&
            (_partVersions[_partKey(current.info.id, partID)] ?? 0) >
                versionAtStart;
      });
      if (preserveUnseen || isPending || hasNewMessage || hasNewPart) {
        if (!prefixIDs.contains(current.info.id)) merged.add(current);
      }
    }
    return [
      ...prefix.where((message) => !hydratedIDs.contains(message.info.id)),
      ...merged,
    ];
  }

  bool _matchesPendingPrompt(List<Part> parts, _PendingSend pending) {
    final text = parts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .join('\n')
        .trim();
    if (text != pending.text.trim()) return false;

    final files = parts.where((part) => part.type == 'file').toList();
    if (files.length != pending.attachments.length) return false;
    for (var i = 0; i < files.length; i++) {
      final part = files[i];
      final attachment = pending.attachments[i];
      // The name is what identifies an attachment across the round trip. A
      // server that stores none cannot contradict the one that was sent.
      final name = part.filename ?? '';
      if (name.isNotEmpty && name != attachment.filename) return false;
      // The URL and the type are deliberately not compared. The app sends a
      // `data:` URI (or a path); a server keeps the file itself and answers
      // with its own location, and may name the type differently
      // (`image/jpg`). Requiring them to match left the optimistic bubble
      // unreconciled, so the same prompt appeared again after every turn
      // whenever photos were attached.
    }
    return true;
  }
}
