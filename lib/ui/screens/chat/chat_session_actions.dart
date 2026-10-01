part of '../chat_screen.dart';

// Actions on the conversation's run: stop, share, fork, timeline,
// compact, revert and retry.

mixin _ChatSessionActionFields {
  bool _aborting = false;
  String? _localShareUrl;
}

extension _ChatSessionActions on _ChatScreenState {
  String? get _shareUrl =>
      _conn.sessionsById[widget.sessionID]?.shareUrl ?? _localShareUrl;

  Future<void> _abort() async {
    if (_aborting) return;
    if (!_conn.isIsolated) KitHaptics.commit(context);
    final location = _conn.locationRevision;
    final profileID = _conn.profile?.id;
    _setChatState(() => _aborting = true);
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (location != _conn.locationRevision || profileID != _conn.profile?.id) {
      _setChatState(() => _aborting = false);
      _showActionError(_chatL10n(context).workContextChanged);
      return;
    }
    if (actionApi == null) {
      _setChatState(() => _aborting = false);
      _showActionError(
        _conn.connectionError ??
            _chatL10n(context).chatUiOpenCodeIsReconnectingTryAgainShortly,
      );
      return;
    }
    try {
      await actionApi.abort(widget.sessionID);
      if (mounted) {
        final prompt = _messages.lastIndexWhere(_isPrompt);
        _setChatState(() {
          _localTurnSince = null;
          if (prompt >= 0) _stoppedPromptID = _messages[prompt].info.id;
        });
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) _setChatState(() => _aborting = false);
    }
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
    final strings = _chatL10n(context);
    final repository = await _conn.prepareActionRepository();
    if (repository != null) return repository;
    throw ProductException(
      _conn.connectionError ??
          strings.chatUiOpenCodeIsReconnectingTryAgainShortly,
    );
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

  Future<void> _continueTruncated() async {
    if (_sending) return;
    final draft = _composer.text;
    _composer.text = _chatL10n(context).returnBriefContinue;
    await _send();
    if (mounted && _composer.text.isEmpty && draft.trim().isNotEmpty) {
      _composer.text = draft;
    }
  }

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
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw ProductException(strings.chatUiOpenCodeIsReconnecting);
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
