part of '../chat_screen.dart';

// Actions on one message: copy, its context menu, delete, fork from it,
// and undo from it.

extension _ChatMessageActions on _ChatScreenState {
  /// Adjacent assistant messages form the reply already presented by the
  /// transcript. Copy that complete text without changing which individual
  /// message destructive actions target.
  ({String label, String text}) _messageCopy(MessageWithParts message) {
    final index = _messages.indexWhere(
      (item) => item.info.id == message.info.id,
    );
    if (message.info.role != 'assistant' || index < 0) {
      return (
        label: _chatL10n(context).chatUiCopyMessageText,
        text: _ChatScreenState._messageText(message),
      );
    }
    // The reply is the whole turn; a notice in the middle of it (context
    // added, project moved) is not where it ends.
    final reply = _turnSteps(_messages, index);
    final start = _messages.indexOf(reply.first);
    final end = _messages.indexOf(reply.last);
    final unfinished =
        _conn.busySessions.contains(widget.sessionID) &&
        reply.any((item) => item.info.time?.isDone != true);
    return (
      label: start == end
          ? _chatL10n(context).chatUiCopyMessageText
          : unfinished
          ? _chatL10n(context).chatCopyReplySoFar
          : start == 0 && _olderCursor != null
          ? _chatL10n(context).historyCopyLoadedReply
          : _chatL10n(context).chatCopyCompleteReply,
      text: reply
          .map(_ChatScreenState._messageText)
          .where((text) => text.trim().isNotEmpty)
          .join('\n\n'),
    );
  }

  /// Copies verbatim (a reply is the person's to paste), confirmed by the
  /// kit's tick and announcement, like the reply footer's Copy.
  Future<void> _copyMessageText(MessageWithParts message) =>
      KitCopy.copy(context, _messageCopy(message).text, redact: false);

  /// A transcript message's actions, one list for every way in: the reply
  /// footer's More, long-press, right-click, Shift+F10 and the screen
  /// reader's custom actions (the footer draws Copy beside More, so its
  /// menu leaves Copy out). Ordered by use; delete last (KIT-28).
  List<KitMenuItem> _messageContextActions(MessageWithParts message) => [
    if (_messageCopy(message).text.isNotEmpty)
      KitMenuItem(
        key: const ValueKey('message-menu-copy'),
        label: _messageCopy(message).label,
        icon: AppIcons.copy,
        onSelected: () => unawaited(_copyMessageText(message)),
      ),
    if (message.info.role == 'user' && _conn.capabilities.sessionFork)
      KitMenuItem(
        key: const ValueKey('message-menu-fork'),
        label: _chatL10n(context).chatUiForkFromThisPrompt,
        icon: AppIconography.fork,
        onSelected: () => unawaited(_forkFromMessage(message)),
      ),
    if (_canReadReply(message))
      KitMenuItem(
        key: const ValueKey('message-menu-read-aloud'),
        label: _chatL10n(context).readAloudAction,
        icon: AppIconography.volume,
        onSelected: () => unawaited(_readReply(message)),
      ),
    if (_canReadReply(message) && _readAloudConsented)
      KitMenuItem(
        key: const ValueKey('message-menu-read-aloud-voice'),
        label: _chatL10n(context).readAloudOtherVoice,
        icon: AppIconography.speakUser,
        onSelected: () => unawaited(_readReply(message, chooseVoice: true)),
      ),
    if (_canUndoFrom(message))
      KitMenuItem(
        key: const ValueKey('message-menu-revert'),
        label: _chatL10n(context).revertFromHere,
        icon: AppIconography.history,
        onSelected: () => unawaited(_undoFrom(message)),
      ),
    if (_conn.capabilities.messageDelete)
      KitMenuItem(
        key: const ValueKey('message-menu-delete'),
        label: _chatL10n(context).chatUiDeleteMessage,
        icon: AppIconography.delete,
        destructive: true,
        onSelected: () => unawaited(_deleteMessage(message)),
      ),
  ];

  Future<void> _deleteMessage(MessageWithParts message) async {
    final confirmed = await showKitConfirm(
      context,
      kind: KitConfirmKind.destructive,
      icon: AppIconography.delete,
      title: _chatL10n(context).chatUiDeleteThisMessage,
      body: _chatL10n(context).chatUiTheMessageAndAllOfItsParts,
      confirmLabel: _chatL10n(context).chatUiDeleteMessage,
    );
    if (!confirmed || !mounted) return;
    try {
      final repository = await _requireActionRepository();
      await repository.deleteMessage(
        sessionID: widget.sessionID,
        messageID: message.info.id,
      );
      if (!mounted) return;
      // The message leaving the transcript is the confirmation.
      _setChatState(() {
        _messages.removeWhere((entry) => entry.info.id == message.info.id);
      });
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _forkFromMessage(MessageWithParts message) async {
    if (!_conn.capabilities.sessionFork || message.info.role != 'user') return;
    final text = message.parts
        .where((part) => part.type == 'text' && !part.synthetic)
        .map((part) => part.text)
        .join();
    final attachments = <PromptAttachment>[];
    for (final part in message.parts.where(
      (part) => part.type == 'file' && !part.synthetic,
    )) {
      final url = part.url;
      if (url == null || url.isEmpty) {
        _showActionError(
          _chatL10n(context).chatUiThisPromptCannotBeRestoredBecauseAn,
        );
        return;
      }
      final filename = part.filename?.trim().isNotEmpty == true
          ? part.filename!
          : 'attachment';
      attachments.add(
        PromptAttachment(
          mime: part.mime?.trim().isNotEmpty == true
              ? part.mime!
              : _mimeForFilename(filename),
          filename: filename,
          url: url,
        ),
      );
    }
    try {
      final repository = await _requireActionRepository();
      final id = await repository.forkSession(
        widget.sessionID,
        messageID: message.info.id,
      );
      await _conn.refreshSessions();
      if (!mounted) return;
      await _landInFork(id, initialText: text, initialAttachments: attachments);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// A prompt the server saved and still holds: undo can start there.
  bool _isUndoTarget(MessageWithParts message) =>
      message.info.role == 'user' &&
      !message.info.id.startsWith('local-') &&
      !_conn
          .inboxItemsFor(widget.sessionID)
          .any((item) => item.id == message.info.id);

  /// Whether a prompt's menu offers "Undo from here". A busy conversation
  /// still offers it: the sheet says why its primary is off.
  bool _canUndoFrom(MessageWithParts message) =>
      _conn.capabilities.sessionRevert && _isUndoTarget(message);

  /// The one "Undo from here" flow (map stage-revert-sheet) behind every
  /// door: a prompt's menu, the conversation menu and /undo. Where the
  /// server stages an undo, the sheet stages it and the review page follows;
  /// otherwise the sheet is the honest one-step confirm and undoes at once.
  Future<void> _undoFrom(MessageWithParts message) async {
    if (!_canUndoFrom(message)) return;
    final review = _conn.reviewSessionRevert(widget.sessionID);
    final prompt = message.parts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .join('\n');
    if (_conn.supportsStagedRevert) {
      // The sheet stages itself: its primary shows the work, and a failure
      // stays in the sheet with the choice kept, instead of an alert after
      // it closed.
      final applyFiles = await showStageRevertSheet(
        context,
        controller: _conn,
        review: review,
        prompt: prompt,
        stage: (applyFiles) => _conn.stageSessionRevert(
          review,
          message.info.id,
          applyFiles: applyFiles,
        ),
      );
      if (!mounted || applyFiles == null) return;
      if (review.scope == _conn.reviewSessionRevert(widget.sessionID).scope) {
        await _reviewStagedRevert();
      }
      return;
    }
    final after = _messagesAfter(message);
    final undone = await showStageRevertSheet(
      context,
      controller: _conn,
      review: review,
      prompt: prompt,
      messagesAfter: after?.length,
      editedFiles: after == null ? const [] : RunResult.editedPaths(after),
      undo: () async {
        final repository = await _requireActionRepository();
        await repository.revertSession(widget.sessionID, message.info.id);
      },
    );
    if (!mounted || undone != true) return;
    try {
      // The undo boundary comes from the session; read it now rather than
      // wait for its event, so the undone turns hide at once.
      await _conn.ensureSession(widget.sessionID);
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// The server's messages after [message] in the loaded transcript, or null
  /// when [message] is not in it. History loads newest first, so a loaded
  /// prompt has everything after it loaded too.
  List<MessageWithParts>? _messagesAfter(MessageWithParts message) {
    final index = _messages.indexWhere((m) => m.info.id == message.info.id);
    if (index < 0) return null;
    return [
      for (final m in _messages.skip(index + 1))
        if (!m.info.id.startsWith('local-')) m,
    ];
  }

  Future<void> _reviewStagedRevert() => Navigator.of(context).push<void>(
    KitPageRoute<void>(
      builder: (_) =>
          StagedRevertScreen(controller: _conn, sessionID: widget.sessionID),
    ),
  );
}
