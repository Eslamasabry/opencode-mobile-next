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

  /// The row that ends a turn the person stopped from here, which carries
  /// its "You stopped this reply." line ([_stoppedTurnRow]).
  int? _stoppedRow;

  /// Prompts still waiting in the server's inbox (a steer the agent has not
  /// read yet). Not drawn in the transcript, and not the start of a new
  /// turn: the turn they join is still the newest.
  Set<String> _waitingPromptIDs = const {};

  /// The newest prompt when it was sent while a step of the running turn
  /// still runs, and the agent reads it once that step is done (Claude
  /// Code, Codex): the turn it joined is still the one running, and the
  /// prompt says when it is read ([_steerWaitNote]). Worked out once per
  /// build ([_liveTurn]); null otherwise.
  ({String promptID, bool command, DateTime? sent})? _steerWait;

  /// Prompts the server withdrew that the chat sent again
  /// ([WithdrawnPromptMemory]), read once per screen. They are left out of
  /// the transcript: their words show once, on the copy the agent reads.
  Set<String>? _withdrawnPromptIDs;

  // What one build derived, kept for the next (chat_page_cache.dart).

  /// The connection as the page last drew it ([_pageConnectionKey]).
  List<Object?>? _pageConnKey;

  /// Everything before the newest turn, and the newest turn itself.
  _DerivedStretch? _derivedPrefix;
  _DerivedStretch? _derivedTail;
  int _derivedBoundary = -1;
  ({
    List<MessageWithParts> messages,
    List<List<Part>> displayParts,
    Set<int> owners,
  })?
  _derived;
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
    final Widget list = LayoutBuilder(
      builder: (context, constraints) {
        final tokens = KitTokens.of(context);
        final clearance = KitBottomInset.of(context).bottom;
        _transcriptExtent = constraints.maxHeight.isFinite
            ? constraints.maxHeight
            : 0;
        // While Stop shows in the chips row, the live line sits right above
        // it: the line's own 48 dp target and Stop's meet, and the pill's
        // margin is the breathing room (no extra gap on top).
        final padding = _stopRowShown
            ? (clearance - tokens.space2).clamp(0.0, double.infinity)
            : tokens.space2 + clearance;
        if (padding > _transcriptBottomPadding + .5) {
          _watchLiveEnd(_transcriptBottomPadding);
        }
        _transcriptBottomPadding = padding;
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
                scrollOffsetController: _messageOffset,
                itemPositionsListener: _messagePositions,
                // The top clears the frosted header when the transcript
                // runs under it (KitFrostHeader).
                padding: EdgeInsets.fromLTRB(
                  tokens.gutter,
                  tokens.space2 + KitFrostHeader.topClearanceOf(context),
                  tokens.gutter,
                  padding,
                ),
                itemCount:
                    _renderedMessageCount + (_olderCursor == null ? 0 : 1),
                itemBuilder: (context, i) => _withBackgroundWork(
                  i,
                  _transcriptRow(
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
    // actually sit above the viewport. Built again only when that count
    // changes, never on each frame of a scroll ([_earlierCount]).
    WidgetsBinding.instance.addPostFrameCallback((_) => _syncEarlierCount());
    return ValueListenableBuilder<int>(
      valueListenable: _earlierCount,
      child: latest,
      builder: (context, earlier, child) {
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
    _steerWait = null;
    _stuckFor = null;
    _stuckName = null;
    if (_conn.isIsolated || _watching || _messages.isEmpty) return null;
    bool ended(MessageInfo info) =>
        info.errorText != null || info.time?.isDone == true;
    // A prompt still waiting in the server's inbox is not drawn and does
    // not start a turn: the turn it joins is still the newest.
    bool startsTurn(MessageWithParts message) =>
        _isPrompt(message) && !_waitingPromptIDs.contains(message.info.id);
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
    final prompt = _messages.lastIndexWhere(startsTurn, end - 1);
    if (prompt < 0 && !busy) return null;
    // A message sent while a step runs joins that turn (Claude Code and
    // Codex read it once the step is done): until the agent answers it, the
    // turn is the one it joined, doing what it was doing, on its own clock
    // (owner, 2026-10-28: a steer read "Waiting for the model's first
    // word" under a test suite that ran on).
    final joined = busy && !queuedBehind ? _joinedTurnPrompt(prompt) : null;
    final from = joined ?? prompt;
    MessageWithParts? step;
    var output = false;
    for (var index = from + 1; index < end; index += 1) {
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
    String? detail;
    String? progress;
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
      if (newest != null &&
          newest.type == 'tool' &&
          newest.toolState.executed &&
          (newest.toolState.status == 'running' ||
              newest.toolState.status == 'pending')) {
        // The running step folds away, so the newest line it printed rides
        // in the live line's muted second slot (a plan step wins there).
        final printed = newest.toolState.metadata?['output'];
        if (printed is String) {
          final lines = printed
              .replaceAll(
                RegExp(r'<shell_metadata>.*?</shell_metadata>', dotAll: true),
                '',
              )
              .split('\n')
              .map((line) => line.trim())
              .where((line) => line.isNotEmpty);
          if (lines.isNotEmpty) progress = lines.last;
        }
        // Folded, the step lives in this line, which names it. Shown (a
        // tap on the line), the step's own row says its command: the line
        // keeps the verb and the time.
        detail = runningToolPhrase(
          newest.toolName ?? 'tool',
          newest.toolState,
          l10n: _chatL10n(context),
          named: !_liveStepsShown,
        );
        _noteRunningCommand(newest);
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
    DateTime? createdAt(int index) {
      final ms = index < 0 ? null : _messages[index].info.time?.created;
      return ms == null ? null : DateTime.fromMillisecondsSinceEpoch(ms);
    }

    if (joined != null) {
      Part? open;
      for (final part in step?.parts.reversed ?? const <Part>[]) {
        if (part.type == 'tool') {
          open = part;
          break;
        }
      }
      _steerWait = (
        promptID: _messages[prompt].info.id,
        command:
            open != null &&
            isShellTool(open.toolName ?? 'tool', open.toolState),
        // This phone's clock when it sent the steer: a server's clock may
        // be off, and the way out waits for a wait of its own length.
        sent: _localTurnSince ?? createdAt(prompt),
      );
    }
    return (
      live: KitTurnLive(
        activity: activity,
        since: joined != null && joined >= 0
            ? createdAt(joined) ?? _localTurnSince
            : _localTurnSince ?? createdAt(prompt),
        teamAlsoWorking: _inAppTeamWorking(),
        detail: detail,
        step: _planStep(context) ?? progress,
        stuck: _stuckWords(context),
      ),
      // Under the reply that runs, above any prompt waiting behind it or
      // still in the server's inbox (not drawn).
      index: queuedBehind
          ? queuedAfterIndex
          : math.max(
              _messages.lastIndexWhere(
                (m) => !_waitingPromptIDs.contains(m.info.id),
              ),
              0,
            ),
    );
  }

  /// The prompt before [prompt] when [prompt] joined the turn it started:
  /// nothing of the agent's came after [prompt] yet, the agent takes a
  /// message sent while it works into that turn, and the turn's newest step
  /// is a tool call still running. -1 when that turn had no prompt of its
  /// own; null when [prompt] starts a turn of its own.
  int? _joinedTurnPrompt(int prompt) {
    if (prompt <= 0) return null;
    if (!_sendJoinsRunningTurn && !_conn.capabilities.promptSteering) {
      return null;
    }
    for (var index = prompt + 1; index < _messages.length; index += 1) {
      if (_messages[index].info.role == 'assistant') return null;
    }
    for (var index = prompt - 1; index >= 0; index -= 1) {
      final message = _messages[index];
      if (message.info.role != 'assistant') continue;
      Part? newest;
      for (final part in message.parts.reversed) {
        if ((part.type == 'text' && part.text.trim().isNotEmpty) ||
            part.type == 'tool' ||
            part.type == 'reasoning') {
          newest = part;
          break;
        }
      }
      if (newest == null) continue;
      final open =
          newest.type == 'tool' &&
          newest.toolState.executed &&
          newest.toolState.inFlight;
      if (!open) return null;
      return _messages.lastIndexWhere(
        (m) => _isPrompt(m) && !_waitingPromptIDs.contains(m.info.id),
        index,
      );
    }
    return null;
  }

  /// The quiet note under a prompt the agent reads once its running step
  /// is done ([_steerWait]), with Stop for a step that runs long. Null for
  /// every other prompt.
  ({String text, KitAction stop, DateTime? since})? _steerWaitNote(
    BuildContext context,
    String promptID,
  ) {
    final wait = _steerWait;
    if (wait == null || wait.promptID != promptID) return null;
    final strings = _chatL10n(context);
    final agent = KitBidi.auto(_agentName ?? strings.chatSteerAgentFallback);
    return (
      text: wait.command
          ? strings.chatSteerReadsAfterCommand(agent)
          : strings.chatSteerReadsAfterStep(agent),
      stop: KitAction(
        key: ValueKey('steer-stop-$promptID'),
        label: wait.command
            ? strings.chatSteerStopCommand
            : strings.chatSteerStopStep,
        onPressed: () => unawaited(_stopForSteer(promptID)),
      ),
      since: wait.sent,
    );
  }

  /// Stops the step a steer waits on. Paseo withdraws a message Claude has
  /// not read yet when the turn is stopped (claude/agent.js
  /// withdrawUnstartedMessages), so once the turn has stopped the same
  /// words go out again and the agent reads them at once. Paseo keeps the
  /// withdrawn message in the conversation's record, so the chat leaves it
  /// out ([WithdrawnPromptMemory]): the words show once.
  Future<void> _stopForSteer(String promptID) async {
    final index = _messages.indexWhere((m) => m.info.id == promptID);
    if (index < 0) return;
    final words = _ChatScreenState._messageText(_messages[index]).trim();
    await _abort();
    if (!mounted || words.isEmpty || _stoppedPromptID != promptID) return;
    try {
      await _sendWithdrawnAgain(words);
    } catch (error) {
      if (mounted) _showActionError(error);
      return;
    }
    if (!mounted) return;
    _withdrawnPrompts.add(promptID);
    _setChatState(() {
      _messages.removeWhere((m) => m.info.id == promptID);
      _stoppedPromptID = null;
    });
    final profileId = _conn.profile?.id;
    if (profileId == null || _conn.isIsolated) return;
    unawaited(
      WithdrawnPromptMemory.remember(
        _conn.store.prefs,
        profileId,
        widget.sessionID,
        promptID,
      ),
    );
  }

  /// [_withdrawnPromptIDs], read from this server's saved list the first
  /// time it is needed.
  Set<String> get _withdrawnPrompts {
    final known = _withdrawnPromptIDs;
    if (known != null) return known;
    final profileId = _conn.profile?.id;
    return _withdrawnPromptIDs = profileId == null || _conn.isIsolated
        ? <String>{}
        : WithdrawnPromptMemory.read(
            _conn.store.prefs,
            profileId,
            widget.sessionID,
          );
  }

  /// Whether the newest turn has a step still running: a tool call the
  /// server started and has not ended, after the turn's prompt.
  bool get _liveTurnRunsTool {
    for (var index = _messages.length - 1; index >= 0; index -= 1) {
      final message = _messages[index];
      if (_isPrompt(message)) return false;
      if (message.info.role != 'assistant') continue;
      for (final part in message.parts) {
        final status = part.toolState.status;
        if (part.type == 'tool' &&
            part.toolState.executed &&
            (status == 'running' || status == 'pending')) {
          return true;
        }
      }
    }
    return false;
  }

  /// The running turn has gone quiet (the BA7 watchdog's finding): one plain
  /// line saying why, with Stop and Details (FC3). The watchdog's contract
  /// words and its evidence never reach the line; the facts it checked sit
  /// behind Details as technical text.
  KitTurnStall? _turnStallLine(BuildContext context) {
    if (_conn.isIsolated || _watching) return null;
    final stall = _conn.turnStallFor(widget.sessionID);
    if (stall == null) return null;
    // A step the transcript shows running (a long build, a test suite) is
    // the agent at work, not a slow model: its own row and the live line's
    // clock say so (owner, 2026-10-27). Only a measured fact (the helper
    // stopped, the link is down) still speaks.
    final checked = stall.evidence;
    final measured =
        stall.kind == TurnStallKind.helperDown ||
        (stall.kind == TurnStallKind.network &&
            (checked.endpointReachable == false ||
                !checked.transportConnected));
    if (_liveTurnRunsTool && !measured) return null;
    final strings = _chatL10n(context);
    final evidence = stall.evidence;
    // A read-only sub-agent stops with its main conversation: this page
    // cannot stop it, so the line says where it can be.
    final subagent = _subagentReadOnly;
    final message = subagent
        ? strings.chatStallSubagent
        : switch (stall.kind) {
            TurnStallKind.modelSlow => strings.chatStallModelSlow,
            TurnStallKind.helperDown => strings.chatStallHelperDown,
            TurnStallKind.network =>
              evidence.endpointReachable == false ||
                      !evidence.transportConnected
                  ? strings.chatStallConnectionLost
                  : strings.chatStallConnectionUnchecked,
          };
    String fact(bool? value) => switch (value) {
      true => 'yes',
      false => 'no',
      null => 'unknown',
    };
    final details = [
      'cause: ${stall.kind.name}',
      'silent_for: ${stall.silentFor.inSeconds}s',
      'live_connection: ${fact(evidence.transportConnected)}',
      'server_answered: ${fact(evidence.endpointReachable)}',
      'helper_running: ${fact(evidence.helperRunning)}',
    ].join('\n');
    return KitTurnStall(
      message: message,
      actions: [
        if (!subagent)
          KitAction(
            key: const Key('turn-stall-stop'),
            label: strings.kitTurnLiveStop,
            working: _aborting,
            onPressed: () => unawaited(_abort()),
          )
        else if (_subagentNavigation)
          KitAction(
            key: const Key('turn-stall-open-parent'),
            label: strings.chatUiOpenParentSession,
            onPressed: () => unawaited(_openParentSession()),
          ),
        KitAction(
          key: const Key('turn-stall-details'),
          label: strings.chatUiDetails,
          onPressed: () => unawaited(
            _showChatErrorDetails(
              context,
              title: strings.chatUiDetails,
              text: details,
            ),
          ),
        ),
      ],
    );
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

  /// The catalog's name for a model id, for the "Model → …" marker.
  String? _catalogModelName(String id) {
    for (final model in _conn.catalog?.models ?? const <CatalogModel>[]) {
      if (model.id == id || bareModelID(model.providerID, model.id) == id) {
        return model.name;
      }
    }
    return null;
  }

  /// A server notice row (compaction, a sub-agent, a shell step).
  Widget _v2Row(MessageWithParts m, int index, Part tagged) => V2TranscriptRow(
    key: ValueKey('message-${m.info.id}'),
    part: tagged,
    messageId: m.info.id,
    parentSessionID: widget.sessionID,
    knownSessions: _conn.sessionsById,
    modelName: _catalogModelName,
    onCompactAgain: !_watching && _canCompactAgain(index)
        ? () => unawaited(_compact())
        : null,
    onOpenChild: _watching
        ? _openWatchedChild
        : _subagentNavigation
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
    // As settled for drawing (settleStaleLive: one per message, same order).
    final shown = _derived?.messages;
    final m = shown != null && shown.length == _messages.length
        ? shown[index]
        : _messages[index];
    if (waitingLocalIDs.contains(m.info.id) || _isFoldedNotice(m)) {
      return const SizedBox.shrink();
    }
    if (v2VariantPart(m) case final tagged?) {
      return _v2Row(m, index, tagged);
    }
    final rawMeta = _messageMeta(_messages, index);
    final meta = rawMeta.withModelLabel(_catalogModelNames(rawMeta.modelLabel));
    final parts = displayParts[index];
    final stoppedHere = _stoppedRow == index;
    if (parts.isEmpty &&
        meta.isEmpty &&
        m.info.errorText == null &&
        !stoppedHere) {
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
    final VoidCallback? sendAgain =
        silent && !offline && unanswered?.$1 == index
        ? () => unawaited(
            _unansweredWords != null ? _resendUnanswered() : _retryLast(),
          )
        : null;
    return _MessageView(
      key: ValueKey('message-${m.info.id}'),
      // While the request card asks for the person it says so itself: the
      // turn adds no "Waiting for you…" line above it. The turn still counts
      // as running (_live stays set), so Stop and the busy look hold.
      live:
          _live?.index == index &&
              _live!.live.activity != KitTurnActivity.waitingForYou
          ? _live!.live
          : null,
      stall: _live?.index == index ? _turnStallLine(context) : null,
      stoppedHere: stoppedHere,
      unanswered: !silent && unanswered?.$1 == index,
      onSendAgainNoReply: sendAgain,
      noReplyReason: sendAgain == null ? null : _noReplyReason(),
      noReplyActions: sendAgain == null ? null : _noReplyActions(sendAgain),
      onResendPrompt: endsUnanswered && _unansweredWords != null
          ? () => unawaited(_resendUnanswered())
          : null,
      onContinueWithAnother: offline || index != _messages.length - 1
          ? null
          : () => unawaited(_continueWithAnotherAgent()),
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
      // A jump to one step marks the step, not its whole turn.
      highlighted:
          hit != null ||
          (_highlightedMessageID == m.info.id && _highlightedStepID == null),
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
      filePreviewLoader: _toolOutputLoader,
      onAttachFile: _supportsPromptAttachments && !_watching
          ? _attachToolOutputFile
          : null,
      onDownloadFile: _downloadToolOutputFile,
      onCompact: offline || !_supportsSessionCompact ? null : _compact,
      onOpenProviders: offline ? null : _openProviders,
      providersLabel: _conn.isAgentBackend
          ? _chatL10n(context).agentsSignInStart(KitBidi.auto(_agentName ?? ''))
          : null,
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
          : _conn.isIsolated || !_subagentNavigation
          ? null
          : _openSubagentSession,
    );
  }
}
