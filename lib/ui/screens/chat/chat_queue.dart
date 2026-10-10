part of '../chat_screen.dart';

// Prompts waiting to be sent: the offline queue and the server's inbox
// (steering and queued prompts).

extension _ChatQueue on _ChatScreenState {
  /// Queues a drafted prompt for delivery when the server returns. Returns
  /// false (with the limits message shown) when the entry cannot be queued.
  Future<bool> _queueDraft(
    String text,
    List<PromptAttachment> attachments,
    List<PromptAgentMention> mentions, {
    SessionSelection? selection,
    String? profileID,
  }) async {
    selection ??= _conn.selectionForSession(widget.sessionID);
    profileID ??= _conn.profile?.id;
    if (profileID == null) return false;
    final now = DateTime.now();
    final bool queued;
    try {
      queued = await _conn.queuePrompt(
        QueuedPrompt(
          id: 'queued-${now.microsecondsSinceEpoch}',
          profileID: profileID,
          sessionID: widget.sessionID,
          text: text,
          attachments: attachments,
          mentions: mentions,
          modelProviderID: selection.model?.providerID,
          modelID: selection.model?.modelID,
          agent: selection.agent,
          variant: selection.variant,
          createdAt: now.millisecondsSinceEpoch,
        ),
      );
    } on OfflineQueueWriteException {
      if (mounted) _showActionError(_chatL10n(context).queueSaveFailed);
      return false;
    }
    if (!mounted) return queued;
    if (queued) {
      // The queue evicts on age and size. Whatever it dropped to make room
      // is said here, in the same breath as the confirmation, rather than
      // leaving the user to notice a missing draft later.
      final evicted = _conn.takeQueueEvictionNotice();
      _showComposerNote(
        evicted == null
            ? _chatL10n(context).chatUiQueuedWillSendWhenReconnected
            : _chatL10n(context).chatUiQueuedWithEviction(evicted),
        key: const Key('queued-draft-notice'),
      );
    } else {
      _showActionError(_chatL10n(context).chatUiThisDraftIsTooLargeToQueue);
    }
    return queued;
  }

  /// Removes a queued draft. False when storage refused or when the
  /// controller declined because a flush is dispatching the entry — the
  /// bubble already shows that state, so the refusal needs no notice.
  Future<bool> _removeQueuedDraft(String id) async {
    try {
      return await _conn.removeQueuedPrompt(id);
    } on OfflineQueueWriteException {
      if (mounted) _showActionError(_chatL10n(context).queueRemoveFailed);
      return false;
    }
  }

  /// The entry as the controller holds it now, not as the tap saw it. A
  /// reconnect can mark and dispatch a draft while a sheet is open.
  QueuedPrompt? _liveQueuedPrompt(String id) {
    for (final entry in _conn.queuedPromptsFor(widget.sessionID)) {
      if (entry.id == id) return entry;
    }
    return null;
  }

  /// Edit takes the draft out of the queue and back into the composer,
  /// ahead of what was typed since, with "Returned to your draft · Undo"
  /// (P4.3). Undo takes it back out and queues it again. Nothing is sent
  /// until the person presses Send. A draft whose send was never confirmed
  /// also says that sending it again may duplicate it.
  Future<void> _editQueuedPrompt(QueuedPrompt entry) async {
    final live = _liveQueuedPrompt(entry.id);
    if (live == null) return;
    if (!await _removeQueuedDraft(live.id)) return;
    if (!mounted) return;
    final added = [
      for (final attachment in live.attachments)
        if (!_attachments.contains(attachment)) attachment,
    ];
    _setChatState(() => _attachments.addAll(added));
    returnWithdrawnToDraft(
      _undoHost,
      composer: _composer,
      text: live.text,
      focus: _focus,
      onUndo: () async {
        if (mounted) {
          _setChatState(() => _attachments.removeWhere(added.contains));
        }
        try {
          await _conn.queuePrompt(live);
        } on OfflineQueueWriteException {
          if (mounted) _showActionError(_chatL10n(context).queueSaveFailed);
        }
      },
    );
    if (live.dispatched) {
      _showComposerNote(
        _chatL10n(context).queuedResendMessage,
        key: const Key('queued-edit-unconfirmed-note'),
      );
    }
  }

  Future<void> _discardQueuedPrompt(QueuedPrompt entry) async {
    if (!await _confirmDiscardQueuedPrompt(entry)) return;
    if (!mounted) return;
    var live = _liveQueuedPrompt(entry.id);
    if (live == null) return;
    // The sheet promised "not sent" but a flush dispatched the draft
    // meanwhile: ask once more with the copy that matches its real state.
    // A marker never comes off without the user's own resend, so a second
    // premise change is impossible and one re-ask is enough.
    if (live.dispatched && !entry.dispatched) {
      if (!await _confirmDiscardQueuedPrompt(live)) return;
      if (!mounted) return;
      live = _liveQueuedPrompt(entry.id);
      if (live == null) return;
    }
    await _removeQueuedDraft(live.id);
  }

  /// The discard sheet, worded for the entry's state at the moment it opens.
  Future<bool> _confirmDiscardQueuedPrompt(QueuedPrompt asked) {
    final l10n = _chatL10n(context);
    return showKitConfirm(
      context,
      kind: KitConfirmKind.discard,
      icon: AppIconography.clearAll,
      title: l10n.chatUiDiscardQueuedDraft,
      body: asked.dispatched
          ? l10n.queuedDiscardUnconfirmedMessage
          : l10n.chatUiThisDraftHasNotBeenSentTo,
      confirmLabel: l10n.chatUiDiscardDraft,
      cancelLabel: asked.dispatched
          ? l10n.queuedKeepForReview
          : l10n.chatUiKeepItQueued,
    );
  }

  /// The explicit resend for a draft whose send was never confirmed. Only
  /// the user's confirmation clears the dispatch marker; a duplicate is the
  /// risk they accept here, so the sheet names it. The controller declines
  /// silently when the entry is no longer in review by the time they
  /// confirm; the bubble shows why.
  Future<void> _resendQueuedPrompt(QueuedPrompt entry) async {
    final l10n = _chatL10n(context);
    final confirmed = await showKitConfirm(
      context,
      icon: AppIconography.send,
      title: l10n.queuedResendTitle,
      body: l10n.queuedResendMessage,
      confirmLabel: l10n.queuedResendConfirm,
      cancelLabel: l10n.queuedKeepForReview,
    );
    if (!confirmed) return;
    try {
      await _conn.resendQueuedPrompt(entry.id);
    } on OfflineQueueWriteException {
      if (mounted) _showActionError(_chatL10n(context).queueSaveFailed);
    }
  }

  /// Retry on a draft the server refused: nothing was delivered, so it is
  /// sent again at once, with no question.
  Future<void> _retryQueuedPrompt(QueuedPrompt entry) async {
    try {
      await _conn.retryQueuedPrompt(entry.id);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Takes a pending server send back: its text returns to the draft with
  /// "Returned to your draft · Undo", and Undo sends it again the same way.
  /// Only a send that carries files asks first, since Undo brings back the
  /// words, not the files.
  Future<void> _cancelInboxSend(Api2InboxItem item) async {
    final files = item.payload['files'];
    if (files is List && files.isNotEmpty) {
      final l10n = _chatL10n(context);
      final confirmed = await showKitConfirm(
        context,
        kind: KitConfirmKind.discard,
        icon: AppIconography.clearAll,
        title: l10n.chatUiCancelThisPendingMessage,
        body: l10n.chatUiItsTextReturnsToTheComposerAs,
        confirmLabel: l10n.chatUiCancelMessage,
        cancelLabel: l10n.chatUiKeepItPending,
      );
      if (!confirmed || !mounted) return;
    }
    String? text;
    try {
      text = await _conn.cancelInboxItem(widget.sessionID, item.id);
    } on ApiException catch (error) {
      if (!mounted) return;
      if (error.statusCode == 409) {
        _showComposerNote(_chatL10n(context).chatUiAlreadyDelivered);
        return;
      }
      _showActionError(error);
      return;
    } catch (error) {
      if (mounted) _showActionError(error);
      return;
    }
    if (!mounted || text == null || text.isEmpty) return;
    final withdrawn = text;
    final delivery = switch (item.delivery) {
      Api2Delivery.steer => PromptDelivery.steer,
      Api2Delivery.queue => PromptDelivery.queue,
      _ => null,
    };
    returnWithdrawnToDraft(
      _undoHost,
      composer: _composer,
      text: withdrawn,
      focus: _focus,
      onUndo: () => _sendWithdrawnAgain(withdrawn, delivery),
    );
  }

  /// Undo for a cancelled server send: the same words go back to the
  /// server with the same delivery. A failure reaches the Undo bar, which
  /// says so and offers Try again.
  Future<void> _sendWithdrawnAgain(
    String text,
    PromptDelivery? delivery,
  ) async {
    final reconnecting = _chatL10n(
      context,
    ).chatUiOpenCodeIsReconnectingTryAgainWhenThe;
    final api = await _conn.prepareActionTransport();
    if (api == null) throw StateError(_conn.connectionError ?? reconnecting);
    final selection = _conn.selectionForSession(widget.sessionID);
    await api.promptAsync(
      widget.sessionID,
      text: text,
      model: selection.model,
      agent: selection.agent?.isNotEmpty == true ? selection.agent : null,
      variant: selection.variant.isEmpty ? null : selection.variant,
      delivery: _conn.busySessions.contains(widget.sessionID) ? delivery : null,
    );
  }

  /// Withdraws this conversation's steering messages that the agent has not
  /// picked up yet and returns their text, oldest first, so the message being
  /// sent can carry them. Only plain-text steers are taken: one with files
  /// stays as it is, as does anything queued for after the run (that was a
  /// deliberate choice of timing). An item the agent took in the meantime
  /// (409) is simply not ours to merge any more.
  Future<List<String>> _takeBackWaitingSteers() async {
    if (_conn.isIsolated || !_conn.supportsInbox) return const [];
    if (!_conn.busySessions.contains(widget.sessionID)) return const [];
    final waiting =
        _conn
            .inboxItemsFor(widget.sessionID)
            .where(
              (item) =>
                  item.type == 'user' &&
                  item.delivery == Api2Delivery.steer &&
                  (item.promptText ?? '').trim().isNotEmpty &&
                  (item.payload['files'] is! List ||
                      (item.payload['files'] as List).isEmpty),
            )
            .toList()
          ..sort((a, b) => (a.timeCreated ?? 0).compareTo(b.timeCreated ?? 0));
    final texts = <String>[];
    for (final item in waiting) {
      try {
        final text = await _conn.cancelInboxItem(widget.sessionID, item.id);
        if (text != null && text.trim().isNotEmpty) texts.add(text.trim());
      } catch (_) {
        // Delivered already, or the server said no: it goes out on its own.
      }
    }
    return texts;
  }

  /// Flips a pending server send between steer and queue delivery.
  Future<void> _flipInboxDelivery(Api2InboxItem item) async {
    final next = item.delivery == Api2Delivery.steer
        ? Api2Delivery.queue
        : Api2Delivery.steer;
    try {
      await _conn.setInboxDelivery(widget.sessionID, item.id, delivery: next);
    } on ApiException catch (error) {
      if (!mounted) return;
      if (error.statusCode == 409) {
        _showComposerNote(_chatL10n(context).chatUiAlreadyDelivered);
        return;
      }
      _showActionError(error);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// The delivery mode that rides on an OpenCode 2 send made while a turn
  /// runs. Off a running turn — and on v1, which has no inbox — nothing is
  /// sent, so the server default applies. While a turn runs the composer's
  /// visible delivery control decides; "Send after this reply" is the
  /// default and adding to the running turn is the choice (P6.6).
  /// This conversation's agent adds a message sent while it works to the
  /// running turn (Paseo steering), so the message is never "queued".
  bool get _sendJoinsRunningTurn {
    final api = _conn.api;
    return api is MidTurnPromptGateway &&
        (api as MidTurnPromptGateway).midTurnPromptJoinsTurn(widget.sessionID);
  }

  PromptDelivery? get _activeDelivery =>
      _conn.supportsInbox && _conn.busySessions.contains(widget.sessionID)
      ? _delivery
      : null;
}
