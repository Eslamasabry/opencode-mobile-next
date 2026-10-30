part of '../chat_screen.dart';

// Actions on the conversation's run: stop, share, fork, timeline,
// compact, revert and retry.

mixin _ChatSessionActionFields {
  bool _aborting = false;
  String? _localShareUrl;

  /// Completed when the person presses Stop while a send waits (for the
  /// connection, the model choice, storage): the send goes no further.
  Completer<void>? _sendCancel;

  /// The bubble the send in flight shows, taken back out by a Stop.
  _PendingSend? _sendingBubble;

  /// The prompt request has left: a Stop now stops the turn on the server.
  bool _promptOnWire = false;

  /// Stop was pressed while the prompt request was on its way: the turn is
  /// stopped again once the server took it, in case it started after the
  /// first stop arrived.
  bool _stopAfterDispatch = false;

  /// The last [_abort] reached the server and stopped the turn.
  bool _abortSucceeded = false;
}

extension _ChatSessionActions on _ChatScreenState {
  String? get _shareUrl =>
      _conn.sessionsById[widget.sessionID]?.shareUrl ?? _localShareUrl;

  /// How long a Stop waits for the connection before it says it could not
  /// reach the server: the person pressed it to stop something now.
  static const _abortTransportWait = Duration(seconds: 5);

  Future<void> _abort() async {
    _abortSucceeded = false;
    if (_aborting) return;
    if (!_conn.isIsolated) KitHaptics.commit(context);
    // The send is still waiting (for the connection, the model choice):
    // it is called off here, and nothing reaches the server.
    if (_sending && _sendCancel != null && !_promptOnWire) {
      _stopPendingSend();
      return;
    }
    // Its request is on the way: the turn is stopped now and once more
    // when the server has taken it.
    if (_sending && _promptOnWire) _stopAfterDispatch = true;
    final location = _conn.locationRevision;
    final profileID = _conn.profile?.id;
    _setChatState(() => _aborting = true);
    final actionApi = await _conn.prepareActionTransport().timeout(
      _abortTransportWait,
      onTimeout: () => null,
    );
    if (!mounted) return;
    if (location != _conn.locationRevision || profileID != _conn.profile?.id) {
      _setChatState(() => _aborting = false);
      _showActionError(_chatL10n(context).workContextChanged);
      return;
    }
    if (actionApi == null) {
      _setChatState(() => _aborting = false);
      _showActionError(
        _conn.connectionError ?? _reconnectingShortlyWords(context),
      );
      return;
    }
    // Noted before the call: the server's interrupt can arrive before the
    // call returns, and must not read as a stop from another device.
    _conn.noteStopFromThisApp(widget.sessionID);
    try {
      await actionApi.abort(widget.sessionID);
      _abortSucceeded = true;
      _conn.markStoppedByPerson(widget.sessionID);
      if (mounted) {
        final prompt = _messages.lastIndexWhere(_isPrompt);
        _setChatState(() {
          _localTurnSince = null;
          if (prompt >= 0) _stoppedPromptID = _messages[prompt].info.id;
        });
      }
    } catch (error) {
      _conn.noteStopFromThisApp(widget.sessionID, asked: false);
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) _setChatState(() => _aborting = false);
    }
  }

  // A send in flight, and Stop while it still waits.

  /// How long a send made while the connection is away waits for it to
  /// come back (a wake's reconnect) before it is queued or kept as a draft.
  static const _sendTransportWait = Duration(seconds: 10);

  /// Whether the connection is back for a send made while it was away:
  /// the action transport answered (it waits out a wake's reconnect) and
  /// the event stream is up or coming up on it.
  Future<bool> _connectionBackForSend() async {
    final ServerGateway? api;
    try {
      api = await _untilSendStopped<ServerGateway?>(
        _conn.prepareActionTransport().timeout(
          _sendTransportWait,
          onTimeout: () => null,
        ),
      );
    } catch (_) {
      return false;
    }
    if (api == null || !mounted) return false;
    final status = _conn.status;
    return status == StreamStatus.connected ||
        (status == StreamStatus.connecting && _conn.hasConnectedServer);
  }

  /// Whether the person stopped the send in flight ([_stopPendingSend]).
  bool get _sendStopped => _sendCancel?.isCompleted ?? false;

  /// [work], or null as soon as the person stops the send.
  Future<T?> _untilSendStopped<T>(Future<T> work) {
    final cancel = _sendCancel;
    if (cancel == null) return work;
    return Future.any<T?>([work, cancel.future.then((_) => null)]);
  }

  /// Stop while the send still waits, before its request left: nothing is
  /// sent, the bubble goes and the words are back in the box.
  void _stopPendingSend() {
    final cancel = _sendCancel;
    if (cancel == null || cancel.isCompleted) return;
    cancel.complete();
    final bubble = _sendingBubble;
    _sendingBubble = null;
    if (bubble != null) _rollbackOptimisticBubble(bubble);
    _setChatState(() => _sending = false);
  }

  /// Whether the server still works on this conversation's newest turn,
  /// asked before a reply that looks cut off is called dropped: true while
  /// it is asked and when it says so. An unreachable server leaves the
  /// call to the chat's own reading.
  bool _serverStillRunsTurn() {
    final id = widget.sessionID;
    final word = _conn.turnRunCheck(id);
    if (word == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) unawaited(_conn.checkTurnStillRuns(id));
      });
      return true;
    }
    return word == TurnRunCheck.checking || word == TurnRunCheck.running;
  }

  /// Whether the newest turn's prompt ended without its reply because the
  /// server interrupted it (OpenCode 2), so that turn ends on a stopped line.
  bool get _serverStoppedTurn =>
      !_conn.busySessions.contains(widget.sessionID) &&
      _conn.turnInterruptReason(widget.sessionID) != null;

  /// The line that ends a stopped turn, and its way on: "You stopped this
  /// reply." (null) only when the stop came from this app; otherwise who or
  /// what stopped it, as the server said, or plainly that it was stopped.
  (String?, KitAction?) _stoppedLine({
    required bool latest,
    VoidCallback? onContinue,
  }) {
    final l10n = _chatL10n(context);
    final id = widget.sessionID;
    if (latest &&
        (_stoppedPromptID != null || _conn.turnStoppedFromThisApp(id))) {
      return (null, null);
    }
    KitAction? resume() => onContinue == null
        ? null
        : KitAction(
            key: const Key('stopped-continue'),
            label: l10n.messageViewContinueReply,
            onPressed: onContinue,
          );
    return switch (latest ? _conn.turnInterruptReason(id) : null) {
      TurnInterruptReason.inactivity => (l10n.chatTurnPausedInactive, resume()),
      TurnInterruptReason.shutdown => (
        l10n.chatTurnStoppedServerRestart,
        resume(),
      ),
      TurnInterruptReason.user => (l10n.chatTurnStoppedElsewhere, null),
      TurnInterruptReason.superseded => (l10n.chatTurnSuperseded, null),
      TurnInterruptReason.unknown || null => (l10n.chatTurnStoppedEarly, null),
    };
  }

  Future<void> _share() async {
    final strings = _chatL10n(context);
    final confirmed = await showKitConfirm(
      context,
      icon: AppIconography.globe,
      title: strings.chatUiShareThisSession,
      body: strings.chatUiAnyoneWithTheLinkCanViewThis,
      confirmLabel: strings.chatUiShareSession,
    );
    if (!confirmed) return;
    try {
      final repository = await _requireActionRepository();
      final url = await repository.shareSession(widget.sessionID);
      if (url == null) {
        throw ProductException(strings.chatUiNoShareLinkWasReturned);
      }
      if (mounted) {
        _setChatState(() => _localShareUrl = url);
        await _conn.refreshSessions();
        if (!mounted) return;
        // The share banner shows the link; the copy is confirmed by the
        // kit's tick and announcement, and only a failed copy says more.
        await _copyShareLink(url);
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Copies the public link. A clipboard that refuses it points at the
  /// share banner, where the link stays visible.
  Future<void> _copyShareLink(String url) async {
    try {
      // The kit's redaction leaves a plain share address as it is and masks
      // a credential a server might put in it (G12).
      await KitCopy.copy(
        context,
        url,
        announcement: _chatL10n(context).chatUiShareLinkCopied,
      );
    } catch (_) {
      if (!mounted) return;
      _showComposerNote(
        _chatL10n(context).chatUiSessionSharedCopyTheVisibleLinkManually,
      );
    }
  }

  /// Every entry point (banner, session menu, /unshare) lands here, so the
  /// confirmation cannot be skipped by choosing a different one.
  Future<void> _stopSharing() async {
    if (!await confirmStopSharing(context) || !mounted) return;
    try {
      final repository = await _requireActionRepository();
      await repository.unshareSession(widget.sessionID);
      if (!mounted) return;
      // The share banner leaves with the link: that is the confirmation.
      _setChatState(() => _localShareUrl = null);
      await _conn.refreshSessions();
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _fork() async {
    if (!_conn.capabilities.sessionFork) return;
    try {
      final repository = await _requireActionRepository();
      final id = await repository.forkSession(widget.sessionID);
      await _conn.refreshSessions();
      if (mounted) await _landInFork(id);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Every fork lands in one place (P10.2): the copy opens in place of
  /// this conversation, from the menu's Fork, "/fork", a prompt's own Fork
  /// and the timeline alike. A fork from a prompt brings that prompt back
  /// into the copy's composer.
  Future<void> _landInFork(
    String id, {
    String initialText = '',
    List<PromptAttachment> initialAttachments = const [],
  }) => Navigator.of(context).pushReplacement(
    KitPageRoute<void>(
      builder: (_) => ChatScreen(
        sessionID: id,
        initialText: initialText,
        initialAttachments: initialAttachments,
      ),
    ),
  );

  Future<ServerOperationsGateway> _requireActionRepository() async {
    final reconnecting = _reconnectingShortlyWords(context);
    final repository = await _conn.prepareActionRepository();
    if (repository != null) return repository;
    throw ProductException(_conn.connectionError ?? reconnecting);
  }

  Future<void> _openTimeline() async {
    if (_messages.isEmpty) return;
    // A prompt's own Fork in the timeline lands like every fork (P10.2).
    final selection = await showKitFramedSheet<_TimelineSelection>(
      context,
      useSafeArea: true,
      maxWidth: 720,
      builder: (context) => ListenableBuilder(
        listenable: _historyChanges,
        builder: (context, _) => _TimelineSheet(
          messages: List.of(_visibleHistory),
          forkAvailable: _conn.capabilities.sessionFork,
          hasOlder: _olderCursor != null,
          loadingOlder: _loading || _loadingOlder,
          olderError: _olderError,
          olderNeedsReload: _olderNeedsReload || _resetHistoryOnLoad,
          loadOlder: _loadOlder,
        ),
      ),
    );
    if (!mounted || selection == null) return;
    if (selection.fork) {
      await _forkFromMessage(selection.message);
      return;
    }
    if (selection.query.isNotEmpty) {
      _openFind(query: selection.query, messageID: selection.message.info.id);
    } else {
      _jumpToMessage(selection.message.info.id);
    }
  }

  /// Whether the failed compaction at [index] is still the state of things:
  /// it is the newest compaction notice, nothing is running, and this server
  /// can compact on request.
  bool _canCompactAgain(int index) {
    if (_conn.isIsolated || !_supportsSessionCompact) return false;
    if (_conn.busySessions.contains(widget.sessionID)) return false;
    final part = v2VariantPart(_messages[index]);
    if (part?.type != 'v2:compaction' || part?.toolName != 'failed') {
      return false;
    }
    for (var later = index + 1; later < _messages.length; later += 1) {
      if (v2VariantPart(_messages[later])?.type == 'v2:compaction') {
        return false;
      }
    }
    return true;
  }

  Future<void> _compact() async {
    if (!_supportsSessionCompact) return;
    final model = _conn.modelForSession(widget.sessionID);
    if (model == null && !_conn.serverOwnsSessionSelection) {
      _showActionError(
        _chatL10n(context).chatUiSelectAModelBeforeCompactingThisSession,
      );
      return;
    }
    final strings = _chatL10n(context);
    try {
      // Asked first; the sheet shows progress while the request runs and
      // keeps the question open with Try again if it fails.
      final confirmed = await showKitConfirm(
        context,
        icon: AppIconography.collapse,
        title: strings.chatUiCompactConfirmTitle,
        body: strings.chatUiCompactConfirmBody,
        confirmLabel: strings.chatUiCompactConfirmAction,
        action: () async {
          final repository = await _requireActionRepository();
          await repository.compactSession(
            widget.sessionID,
            providerID: model?.providerID ?? '',
            modelID: model?.modelID ?? '',
          );
        },
      );
      if (!confirmed || !mounted) return;
      _showComposerNote(_chatL10n(context).chatUiCompactionStarted);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _continueTruncated() =>
      _sendInsteadOfDraft(_chatL10n(context).returnBriefContinue);

  /// "Undo last prompt" (conversation menu, /undo): the same "Undo from
  /// here" flow as a prompt's own menu, from the newest prompt.
  Future<void> _revertLast() async {
    if (!_conn.capabilities.sessionRevert) return;
    MessageWithParts? target;
    for (final message in _visibleHistory.toList().reversed) {
      if (_isUndoTarget(message)) {
        target = message;
        break;
      }
    }
    if (target == null) return;
    await _undoFrom(target);
  }

  Future<void> _restore() async {
    if (!_conn.capabilities.sessionRevert) return;
    if (_conn.supportsStagedRevert) {
      await _reviewStagedRevert();
      return;
    }
    try {
      final repository = await _requireActionRepository();
      await repository.restoreSession(widget.sessionID);
      // The boundary clears on the session: read it so the turns come back.
      await _conn.ensureSession(widget.sessionID);
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _retryLast() async {
    final strings = _chatL10n(context);
    if (_sending) return;
    MessageWithParts? target;
    for (final message in _visibleHistory.toList().reversed) {
      if (message.info.role == 'user' &&
          !message.info.id.startsWith('local-')) {
        target = message;
        break;
      }
    }
    final text =
        target?.parts
            .where((part) => part.type == 'text')
            .map((part) => part.text)
            .join('\n') ??
        '';
    final files =
        target?.parts.where((part) => part.type == 'file').toList() ??
        const <Part>[];
    if (text.trim().isEmpty && files.isEmpty) return;
    if (!_supportsPromptAttachments && files.isNotEmpty) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return;
    }
    final attachments = <PromptAttachment>[];
    for (final file in files) {
      final url = file.url;
      if (url == null || url.isEmpty) {
        _showActionError(strings.chatUiThisPromptCannotBeRetriedBecauseAn);
        return;
      }
      final filename = file.filename?.isNotEmpty == true
          ? file.filename!
          : 'attachment';
      attachments.add(
        PromptAttachment(
          mime: file.mime?.isNotEmpty == true
              ? file.mime!
              : _mimeForFilename(filename),
          filename: filename,
          url: url,
        ),
      );
    }
    final reconnecting = _reconnectingWords(context);
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw ProductException(reconnecting);
      }
      await _conn.waitForSessionSelection(widget.sessionID, expectedApi: api);
      await api.promptAsync(
        widget.sessionID,
        text: text,
        model: _conn.modelForSession(widget.sessionID),
        agent: _conn.agentForSession(widget.sessionID).isEmpty
            ? null
            : _conn.agentForSession(widget.sessionID),
        variant: _conn.variantForSession(widget.sessionID).isEmpty
            ? null
            : _conn.variantForSession(widget.sessionID),
        attachments: attachments,
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  void _showActionError(Object error) {
    showProductError(context, error);
  }
}
