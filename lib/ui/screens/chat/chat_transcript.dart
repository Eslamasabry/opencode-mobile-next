part of '../chat_screen.dart';

// The transcript list: its rows, the live turn and its pace, and the
// per-block expansion store.

String _fmtSessionTime(int ms, BuildContext context) {
  final date = DateTime.fromMillisecondsSinceEpoch(ms);
  final now = DateTime.now();
  final material = MaterialLocalizations.of(context);
  final time = material.formatTimeOfDay(
    TimeOfDay.fromDateTime(date),
    alwaysUse24HourFormat: MediaQuery.alwaysUse24HourFormatOf(context),
  );
  return DateUtils.isSameDay(date, now)
      ? time
      : '${material.formatShortDate(date)}, $time';
}

/// The transcript's per-block open/closed choices (`work:`, `tool:`,
/// `reasoning:` keys). Tells the page when the first block opens or the last
/// one closes so the top bar can offer "Collapse all steps".
class _ExpansionStore extends MapBase<String, bool> {
  _ExpansionStore({required this.onOpenChanged});

  final VoidCallback onOpenChanged;
  final Map<String, bool> _values = {};
  bool _anyOpen = false;

  bool get anyOpen => _anyOpen;

  void _sync() {
    final now = _values.containsValue(true);
    if (now == _anyOpen) return;
    _anyOpen = now;
    onOpenChanged();
  }

  /// Closes every open block, keeping the choice so a default-open block
  /// stays closed too.
  void collapseAll() {
    for (final key in _values.keys.toList()) {
      _values[key] = false;
    }
    _sync();
  }

  @override
  bool? operator [](Object? key) => _values[key];

  @override
  void operator []=(String key, bool value) {
    _values[key] = value;
    _sync();
  }

  @override
  void clear() {
    _values.clear();
    _sync();
  }

  @override
  Iterable<String> get keys => _values.keys;

  @override
  bool? remove(Object? key) {
    final removed = _values.remove(key);
    _sync();
    return removed;
  }
}

mixin _ChatTranscriptFields {
  /// The running turn's live line and the row that carries it, worked out
  /// once per build ([_liveTurn]).
  ({KitTurnLive live, int index})? _live;

  // How fast the running reply is arriving, for the composer edge's light:
  // characters (and a weight per step) that came in since the last change,
  // per second, eased. Kept across builds; reset when the turn changes.
  String? _paceTurn;
  int _paceMark = 0;
  DateTime? _paceAt;
  double _paceValue = 0;
}

extension _ChatTranscript on _ChatScreenState {
  /// The transcript: the newest turn at the bottom, clear of the floating
  /// composer (under [KitComposer.layer]), with the two jump pills over it.
  Widget _transcript({
    required int queuedAfterIndex,
    required List<List<Part>> displayParts,
    required Set<String> waitingLocalIDs,
    required Set<int> turnActionOwners,
  }) {
    final Widget list = Builder(
      builder: (context) {
        final tokens = KitTokens.of(context);
        final clearance = KitBottomInset.of(context).bottom;
        return Center(
          child: ConstrainedBox(
            // The conversation's cap (VL §5, LAY-5), its gutters inside.
            constraints: BoxConstraints(
              maxWidth: KitLayout.paneDetailMaxWidth + 2 * tokens.gutter,
            ),
            // Scrolling repaints up to the nearest boundary; without one
            // that is the whole route, so every scroll frame redrew the
            // composer and its glass.
            child: RepaintBoundary(
              key: const ValueKey('transcript-repaint-boundary'),
              child: ScrollablePositionedList.builder(
                reverse: true,
                itemScrollController: _messageScroll,
                itemPositionsListener: _messagePositions,
                padding: EdgeInsets.fromLTRB(
                  tokens.gutter,
                  tokens.space2,
                  tokens.gutter,
                  tokens.space2 + clearance,
                ),
                itemCount:
                    _renderedMessageCount + (_olderCursor == null ? 0 : 1),
                itemBuilder: (context, i) => _transcriptRow(
                  context,
                  i,
                  queuedAfterIndex: queuedAfterIndex,
                  displayParts: displayParts,
                  waitingLocalIDs: waitingLocalIDs,
                  turnActionOwners: turnActionOwners,
                ),
              ),
            ),
          ),
        );
      },
    );
    final latest = KitJumpPillLayer(
      pill: KitJumpPill(
        pillKey: const ValueKey('jump-to-latest'),
        label: KitJumpPill.latestLabel(context),
        onPressed: _jumpToLatest,
        visible: _awayFromLatest,
      ),
      child: list,
    );
    // Only while reading history, and counting only the messages that
    // actually sit above the viewport.
    return ValueListenableBuilder<Iterable<ItemPosition>>(
      valueListenable: _messagePositions.itemPositions,
      child: latest,
      builder: (context, positions, child) {
        final earlier = _awayFromLatest && _messages.length > 30
            ? _earlierMessageCount(positions)
            : 0;
        // A leaving pill keeps its last words while it fades.
        if (earlier > 0) _earlierPillCount = earlier;
        return KitJumpPillLayer(
          pill: KitJumpPill.older(
            pillKey: const ValueKey('earlier-messages-pill'),
            label: _chatL10n(
              context,
            ).chatUiEarlierMessageCount(_earlierPillCount),
            onPressed: () => unawaited(_openTimeline()),
            visible: earlier > 0,
          ),
          child: child!,
        );
      },
    );
  }

  /// The newest turn's live line while it runs, and the message row it
  /// sits under: from the moment a send is accepted here (before the server
  /// says the conversation is busy) until the turn ends, so a reply is
  /// never waited for in silence. Null when nothing runs.
  ({KitTurnLive live, int index})? _liveTurn({
    required bool busy,
    required int queuedAfterIndex,
  }) {
    if (_conn.isIsolated || _watching || _messages.isEmpty) return null;
    bool ended(MessageInfo info) =>
        info.errorText != null || info.time?.isDone == true;
    // OpenCode 1 runs a prompt sent mid-turn after the turn: the running
    // reply is then not the newest row, and its turn is the one that runs.
    final queuedBehind =
        queuedAfterIndex >= 0 &&
        queuedAfterIndex < _messages.length &&
        _messages[queuedAfterIndex].info.role == 'assistant' &&
        !ended(_messages[queuedAfterIndex].info) &&
        _messages.skip(queuedAfterIndex + 1).any(_isPrompt);
    final end = queuedBehind ? queuedAfterIndex + 1 : _messages.length;
    // A turn with no prompt of its own (an automated first turn) still
    // runs, and still needs its Stop.
    final prompt = _messages.take(end).toList().lastIndexWhere(_isPrompt);
    if (prompt < 0 && !busy) return null;
    MessageWithParts? step;
    var output = false;
    for (var index = prompt + 1; index < end; index += 1) {
      final message = _messages[index];
      if (message.info.role != 'assistant') continue;
      step = message;
      output =
          output ||
          message.parts.any(
            (part) =>
                (part.type == 'text' && part.text.trim().isNotEmpty) ||
                part.type == 'tool' ||
                part.type == 'reasoning',
          );
    }
    final stepEnded = step != null && ended(step.info);
    // The server's words win over this phone's guess: a finished step that
    // does not go on to run tools, with the conversation idle, is a
    // finished turn.
    if (!busy &&
        !_sending &&
        step != null &&
        stepEnded &&
        (step.info.errorText != null || step.info.finish != 'tool-calls')) {
      _localTurnSince = null;
    }
    final running = busy || _sending || _localTurnSince != null;
    if (!running) return null;
    final session = widget.sessionID;
    final KitTurnActivity activity;
    if (_sending) {
      activity = KitTurnActivity.sending;
    } else if (_conn.permissionsForSession(session).isNotEmpty ||
        _conn.questionForSession(session) != null) {
      activity = KitTurnActivity.waitingForYou;
    } else if (!output) {
      activity = busy || step != null
          ? KitTurnActivity.waitingForModel
          : KitTurnActivity.waitingForServer;
    } else {
      Part? newest;
      for (final part in step?.parts.reversed ?? const <Part>[]) {
        if ((part.type == 'text' && part.text.trim().isNotEmpty) ||
            part.type == 'tool' ||
            part.type == 'reasoning') {
          newest = part;
          break;
        }
      }
      activity = switch (newest) {
        final part? when part.type == 'tool' =>
          part.toolState.status == 'completed' ||
                  part.toolState.status == 'error'
              ? KitTurnActivity.thinking
              : KitTurnActivity.working,
        final part? when part.type == 'text' && !stepEnded =>
          KitTurnActivity.writing,
        _ => KitTurnActivity.thinking,
      };
    }
    final created = prompt < 0 ? null : _messages[prompt].info.time?.created;
    return (
      live: KitTurnLive(
        activity: activity,
        pace: _livePace(prompt, end),
        since:
            _localTurnSince ??
            (created == null
                ? null
                : DateTime.fromMillisecondsSinceEpoch(created)),
        // Not while the prompt is still on its way: there is nothing to
        // stop yet.
        onStop: _sending ? null : () => unawaited(_abort()),
        stopping: _aborting,
        stopKey: const Key('chat-stop-button'),
        teamAlsoWorking: _inAppTeamWorking(),
      ),
      // Under the reply that runs, above any prompt waiting behind it.
      index: queuedBehind ? queuedAfterIndex : _messages.length - 1,
    );
  }

  /// 0..1: about 90 characters a second and up is full pace.
  double _livePace(int prompt, int end) {
    var mark = 0;
    for (var i = prompt + 1; i < end; i += 1) {
      final message = _messages[i];
      if (message.info.role != 'assistant') continue;
      for (final part in message.parts) {
        if (part.type == 'text' || part.type == 'reasoning') {
          mark += part.text.length;
        } else if (part.type == 'tool') {
          mark += 60;
        }
      }
    }
    final now = DateTime.now();
    final turn = prompt < 0 ? null : _messages[prompt].info.id;
    if (turn != _paceTurn) {
      _paceTurn = turn;
      _paceMark = mark;
      _paceAt = now;
      _paceValue = 0;
      return 0;
    }
    final at = _paceAt;
    if (at != null && mark != _paceMark) {
      final seconds = (now.difference(at).inMilliseconds / 1000).clamp(
        0.05,
        5.0,
      );
      final target = ((mark - _paceMark).abs() / seconds / 90).clamp(0.0, 1.0);
      _paceValue += (target - _paceValue) * (1 - math.exp(-seconds / 0.8));
      _paceMark = mark;
      _paceAt = now;
    }
    return _paceValue;
  }

  /// This reply runs on the phone's own OpenCode while the AI Team on this
  /// phone has work going: the two share the phone, so a slow first word
  /// says why.
  bool _inAppTeamWorking() {
    final profile = _conn.profile;
    final team = _conn.orchestration;
    if (!looksLikeInAppServer(profile) || team == null) return false;
    if (!BuiltinTeam.isBuiltinConfig(profile?.orchestration)) return false;
    return teamGlanceFromSnapshot(team.snapshot).working > 0;
  }

  /// A server notice row (compaction, a sub-agent, a shell step).
  Widget _v2Row(MessageWithParts m, int index, Part tagged) => V2TranscriptRow(
    key: ValueKey('message-${m.info.id}'),
    part: tagged,
    messageId: m.info.id,
    parentSessionID: widget.sessionID,
    knownSessions: _conn.sessionsById,
    onCompactAgain: !_watching && _canCompactAgain(index)
        ? () => unawaited(_compact())
        : null,
    onOpenChild: _watching
        ? _openWatchedChild
        : _conn.capabilities.projectManagement
        ? (id) => _openSubagentSession(id, requireChild: true)
        : null,
  );

  /// Row [i] of the reversed transcript: item 0 is the newest turn. The
  /// running turn's live line ([_liveTurn]) rides on the row it names.
  Widget _transcriptRow(
    BuildContext context,
    int i, {
    required int queuedAfterIndex,
    required List<List<Part>> displayParts,
    required Set<String> waitingLocalIDs,
    required Set<int> turnActionOwners,
  }) {
    if (i == _renderedMessageCount) return _olderHistoryRow();
    final index = _renderedMessageCount - 1 - i;
    final m = _messages[index];
    // The running turn's status lives on the composer's edge
    // ([KitComposer.rail]), so no transcript row draws a live line.
    if (waitingLocalIDs.contains(m.info.id) || _isFoldedNotice(m)) {
      return const SizedBox.shrink();
    }
    if (v2VariantPart(m) case final tagged?) {
      return _v2Row(m, index, tagged);
    }
    final rawMeta = _messageMeta(_messages, index);
    final meta = rawMeta.withModelLabel(_catalogModelNames(rawMeta.modelLabel));
    final parts = displayParts[index];
    if (parts.isEmpty && meta.isEmpty && m.info.errorText == null) {
      return const SizedBox.shrink();
    }
    final hit =
        _findHits.isNotEmpty && _findHits[_findCursor].messageID == m.info.id
        ? _findHits[_findCursor]
        : null;
    final offline = _conn.isIsolated || _watching;
    final unanswered = _unanswered;
    final endsUnanswered = !offline && unanswered?.$2 == index;
    final silent = unanswered?.$3 ?? false;
    final suggestion = endsUnanswered
        ? _suggestedModelFor(m.info.errorText)
        : null;
    return _MessageView(
      key: ValueKey('message-${m.info.id}'),
      statusOnComposer: _live?.index == index,
      unanswered: !silent && unanswered?.$1 == index,
      onSendAgainNoReply: silent && !offline && unanswered?.$1 == index
          ? () => unawaited(
              _unansweredWords != null ? _resendUnanswered() : _retryLast(),
            )
          : null,
      onResendPrompt: endsUnanswered && _unansweredWords != null
          ? () => unawaited(_resendUnanswered())
          : null,
      onSendInterruptedAgain: offline ? null : () => unawaited(_retryLast()),
      suggestedModel: suggestion == null ? null : _modelName(suggestion),
      onUseSuggestedModel: suggestion == null || _unansweredWords == null
          ? null
          : () => unawaited(_resendUnanswered(model: suggestion)),
      queued:
          queuedAfterIndex >= 0 &&
          m.info.role == 'user' &&
          index > queuedAfterIndex,
      m: m,
      meta: meta,
      parts: parts,
      reasoningExpanded: _conn.transcriptReasoningExpanded,
      expansionStore: _transcriptExpansion,
      showTimestamp: _conn.transcriptTimestampsVisible,
      highlighted: hit != null || _highlightedMessageID == m.info.id,
      searchQuery: _findQuery,
      onSearchExcerptContext: (context) {
        if (_findHits.isNotEmpty &&
            _findHits[_findCursor].messageID == m.info.id) {
          _findExcerptContext = context;
        }
      },
      searchMatch: hit,
      // One "more" control per turn: under the message that ends a reply,
      // never under each step of it or under the prompt. An error with
      // more of the turn after it was got over.
      errorRecovered: m.info.errorText != null && !_endsTurn(_messages, index),
      showActions: turnActionOwners.contains(index),
      onCopy: _conn.isIsolated || _messageCopy(m).text.isEmpty
          ? null
          : () => unawaited(_copyMessageText(m)),
      // Watching: copy is the one message action; revert and fork are not
      // the person's.
      contextActions: offline ? null : () => _messageContextActions(m),
      filePreviewLoader: _loadToolOutputFile,
      onAttachFile: _supportsPromptAttachments && !_watching
          ? _attachToolOutputFile
          : null,
      onDownloadFile: _downloadToolOutputFile,
      onCompact: offline || !_supportsSessionCompact ? null : _compact,
      onOpenProviders: offline ? null : _openProviders,
      onContinue: offline ? null : _continueTruncated,
      onChooseModel: offline
          ? null
          : () => showModelPicker(
              context,
              applyScope: _modelApplyScope,
              sessionID: widget.sessionID,
            ),
      onOpenSession: _watching
          ? _openWatchedChild
          : _conn.isIsolated || !_conn.capabilities.projectManagement
          ? null
          : _openSubagentSession,
    );
  }
}
