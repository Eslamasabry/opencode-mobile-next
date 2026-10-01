part of '../chat_screen.dart';

// The page: what one build reads from the connection, the page frame
// (top bar, status line, header, body) and its keyboard shortcuts.

extension _ChatPage on _ChatScreenState {
  /// The index of the message the server is working on while busy on
  /// OpenCode 1: the assistant's current message, or, before it has been
  /// created, the first user prompt. Every user message after it is queued.
  static int _queuedAfterIndex(List<MessageWithParts> messages) {
    final assistant = messages.lastIndexWhere(
      (m) => m.info.role == 'assistant',
    );
    if (assistant >= 0) {
      // The newest reply already ended: the prompt after it is the one now
      // starting (its reply is not written yet), not one waiting its turn.
      final info = messages[assistant].info;
      final ended =
          info.errorText != null ||
          (info.time?.isDone == true && info.finish != 'tool-calls');
      return ended ? -1 : assistant;
    }
    return messages.indexWhere((m) => m.info.role == 'user');
  }

  Widget _buildPage(BuildContext context) {
    _syncFind();
    _queueNudgeObservation();
    // Read above the page frame: its body sees the keyboard inset removed,
    // having already shrunk to make room for it.
    final keyboardUp = MediaQuery.viewInsetsOf(context).bottom > 0;
    final busy = _conn.busySessions.contains(widget.sessionID);
    // OpenCode 1 runs a prompt sent mid-turn after that turn: every user
    // message past the assistant's current one is waiting, and says so.
    final queuedAfterIndex = busy && !_conn.supportsInbox
        ? _queuedAfterIndex(_messages)
        : -1;
    final displayParts = _timelineDisplayParts(_messages, liveTail: busy);
    _live = _liveTurn(busy: busy, queuedAfterIndex: queuedAfterIndex);
    _unanswered = _conn.isIsolated || _watching
        ? null
        : _unansweredTurn(
            _messages,
            refused: _promptError != null,
            running: busy || _sending || _live != null,
            stoppedPromptID: _stoppedPromptID,
          );
    // A prompt waiting in the server's inbox (steering, or queued behind the
    // run) is shown once, as its waiting bubble above the composer, where
    // it can still be flipped or cancelled. Its optimistic copy in the
    // transcript would say the same thing a second time, and would make the
    // running turn look finished.
    final waiting = !_conn.isIsolated && _conn.capabilities.inbox
        ? [
            for (final item in _conn.inboxItemsFor(widget.sessionID))
              if (item.type == 'user') item,
          ]
        : const <Api2InboxItem>[];
    final waitingIDs = {for (final item in waiting) item.id};
    final waitingTexts = {
      for (final item in waiting) (item.promptText ?? '').trim(),
    };
    // Where the agent's latest words end. A waiting prompt is always after
    // them; an older prompt with the same words is history and stays.
    final lastAssistant = _messages.lastIndexWhere(
      (message) => message.info.role == 'assistant',
    );
    final waitingLocalIDs = <String>{
      if (waiting.isNotEmpty)
        for (var i = 0; i < _messages.length; i++)
          if (_messages[i].info.role == 'user' &&
              v2VariantPart(_messages[i]) == null &&
              // The server admits a prompt under the id it will keep, and
              // the optimistic copy is swapped for that message at once, so
              // the copy in the transcript usually carries the item's id.
              (waitingIDs.contains(_messages[i].info.id) ||
                  (i > lastAssistant &&
                      waitingTexts.contains(
                        _ChatScreenState._messageText(_messages[i]).trim(),
                      ))))
            _messages[i].info.id,
    };
    final turnActionOwners = _turnActionOwners(
      _messages,
      displayParts,
      ignore: waitingLocalIDs,
      metaAlways: _conn.transcriptTimestampsVisible,
      running: _conn.busySessions.contains(widget.sessionID),
    );
    final showAttachmentNote = _attachmentNoteVisible();
    var pendingPermissions = _conn.permissionsForSession(widget.sessionID);
    // The request this chat was opened for leads (P4.2a).
    if (widget.landOnRequestID case final landing?
        when pendingPermissions.length > 1 &&
            pendingPermissions.first.id != landing &&
            pendingPermissions.any((p) => p.id == landing)) {
      pendingPermissions = [
        ...pendingPermissions.where((p) => p.id == landing),
        ...pendingPermissions.where((p) => p.id != landing),
      ];
    }

    final session = _conn.sessionsById[widget.sessionID];
    final shareUrl = _shareUrl;
    final parentID = session?.parentID;
    final siblings = parentID == null
        ? const <Session>[]
        : (_conn.sessionsById.values
              .where((candidate) => candidate.parentID == parentID)
              .toList()
            ..sort(
              (a, b) => (a.time?.created ?? 0).compareTo(b.time?.created ?? 0),
            ));
    final siblingIndex = siblings.indexWhere(
      (candidate) => candidate.id == widget.sessionID,
    );
    final runningAgents = runningAgentEntries(
      sessionID: widget.sessionID,
      sessions: _conn.sessionsById,
      busy: _conn.busySessions,
      includeIdle: true,
    );
    final relatedSessionIDs = {
      widget.sessionID,
      for (final session in _conn.sessionsById.values)
        if (session.parentID == widget.sessionID) session.id,
    };
    final runningWorkCount =
        runningAgents.where((entry) => entry.busy && !entry.current).length +
        _runningShells
            .where(
              (shell) =>
                  shell.running &&
                  (relatedSessionIDs.contains(shell.sessionID) ||
                      _shellIDs.contains(shell.id)),
            )
            .length;

    // Which server (and so which agent) this conversation is with, when
    // there is more than one to be with: OpenCode and Claude Code can both be
    // running on this phone, and their conversations look alike.
    final serverName = _conn.isIsolated || _conn.store.profiles.length < 2
        ? null
        : serverDisplayName(
            _conn.profile,
            lookupAppLocalizations(Localizations.localeOf(context)),
            // Two in-app profiles (OpenCode 1 and 2) are both "This phone";
            // the line must say which one this conversation is on.
            among: _conn.store.profiles,
          );
    final reconnecting = _conn.connectionStatus.waiting;
    // Speed contract item 2: while the first history read is on its way the
    // chat shows the end it had last time, read-only, instead of
    // placeholder turns. Never part of [_messages].
    final openingExcerpt =
        _loading && _messages.isEmpty && !_watching && !_conn.isIsolated
        ? _conn.cachedSessionTail(widget.sessionID)
        : null;
    final showExcerpt = openingExcerpt?.messages.isNotEmpty ?? false;
    if (!_firstTranscriptRecorded && (_messages.isNotEmpty || showExcerpt)) {
      _firstTranscriptRecorded = true;
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => PerfTrace.recordSince(
          'chat.open_to_first_transcript',
          _openedMicros,
          attrs: {'source': showExcerpt ? 'saved_excerpt' : 'live'},
        ),
      );
    }

    final screen = PopScope(
      canPop: _conn.isIsolated || _allowRoutePop || _watching,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_leaveChat());
      },
      // The page frame (kit-v2.md §8.2): full width on a phone, the
      // conversation centred at its cap from a tablet up, and the detail
      // pane of Work's two panes on a PC (no Back there).
      child: KitScreen(
        topBar: widget.showAppBar
            ? _chatTopBar(
                session: session,
                serverName: serverName,
                shared: shareUrl != null,
                runningWorkCount: runningWorkCount,
              )
            : null,
        // The screen's one loading bar (design standard §4): the
        // conversation's first load, and a reconnect.
        loading: (_loading && _messages.isEmpty) || reconnecting,
        loadingLabel: reconnecting
            ? _chatL10n(context).e7BannerReconnectingServerSemantic(
                _conn.profile?.name ?? 'OpenCode',
              )
            : _chatL10n(context).chatLoadingConversation,
        status: _chatStatusLine(
          context,
          session: session,
          shareUrl: shareUrl,
          parentID: parentID,
          siblings: siblings,
          siblingIndex: siblingIndex,
        ),
        header: [
          // The demo has no bar of its own here; its one extra action sits
          // under the host's bar. While the keyboard is up the room goes to
          // the conversation and what waits on the person; the action is
          // back when the keyboard is down.
          if (!widget.showAppBar &&
              _conn.isIsolated &&
              _messages.isNotEmpty &&
              !keyboardUp &&
              !widget.hostKeyboardUp)
            Align(
              alignment: AlignmentDirectional.centerEnd,
              child: KitButton.tertiary(
                icon: AppIconography.review,
                label: _chatL10n(context).demoReviewChanges,
                onPressed: _showDiff,
              ),
            ),
        ],
        body: _chatBody(
          keyboardUp: keyboardUp,
          busy: busy,
          openingExcerpt: openingExcerpt,
          showExcerpt: showExcerpt,
          pendingPermissions: pendingPermissions,
          queuedAfterIndex: queuedAfterIndex,
          displayParts: displayParts,
          waitingLocalIDs: waitingLocalIDs,
          turnActionOwners: turnActionOwners,
          showAttachmentNote: showAttachmentNote,
        ),
      ),
    );
    return _withChatShortcuts(screen);
  }

  Widget _withChatShortcuts(Widget screen) {
    if (_conn.isIsolated) {
      return MarkdownInteractionScope(enabled: false, child: screen);
    }
    return Actions(
      actions: {
        FindInSurfaceIntent: CallbackAction<FindInSurfaceIntent>(
          onInvoke: (_) {
            _openFind();
            return null;
          },
        ),
      },
      child: CallbackShortcuts(
        bindings: {
          if (_findOpen)
            const SingleActivator(LogicalKeyboardKey.escape): _closeFind,
          if (_findOpen)
            const SingleActivator(LogicalKeyboardKey.f3): () =>
                _navigateFind(1),
          if (_findOpen)
            const SingleActivator(LogicalKeyboardKey.f3, shift: true): () =>
                _navigateFind(-1),
        },
        child: Focus(
          focusNode: _findNavigationFocus,
          // This node only routes keyboard shortcuts; exposing the whole
          // screen as a focusable semantics node merges unrelated labels.
          includeSemantics: false,
          // Watching changes nothing: no model switch, no background
          // hand-off, no read receipt sent for the worker's session.
          child: _watching
              ? screen
              : ModelShortcuts(
                  onCycle: _cycleModel,
                  onBackground: _canBackgroundWork
                      ? _backgroundRunningWork
                      : null,
                  child: SessionViewObserver(
                    controller: _conn,
                    sessionID: widget.sessionID,
                    ready: !_loading && _error == null && !_awayFromLatest,
                    child: screen,
                  ),
                ),
        ),
      ),
    );
  }
}
