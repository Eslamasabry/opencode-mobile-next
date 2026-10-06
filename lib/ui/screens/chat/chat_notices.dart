part of '../chat_screen.dart';

// What the chat tells the person: the first-reply notification offer, the
// free-model note, an unanswered prompt, retries, and composer notes.

mixin _ChatNoticeFields {
  int _dataRefreshRevision = 0;
  int _offlineFlushRevision = 0;

  /// Whether the last connection snapshot had this session running; the
  /// busy→idle edge is the "run finished" moment.
  bool _wasBusy = false;

  /// Temporary feedback kept beside the composer without replacing its editor.
  String? _composerNote;
  Key? _composerNoteKey;
  Timer? _composerNoteTimer;

  /// Ticks once a second while this session sits in a provider-retry
  /// backoff so the banner's countdown stays live; null otherwise.
  Timer? _retryTicker;
  String? _promptError;

  /// The newest turn when it got no answer, as (its prompt's index, the
  /// index of the step that ended it, or null when no step came, and
  /// whether it ended silently: no words, no steps and no error). Worked out
  /// once per build.
  (int, int?, bool)? _unanswered;

  /// Hidden at once on Dismiss, before the save lands.
  bool _freeModelNoteDismissed = false;

  /// First run's one notification question, asked in the status slot.
  late final FirstReplyNotifyOffer _notifyOffer;
}

extension _ChatNotices on _ChatScreenState {
  /// True once this conversation holds a finished reply and is idle. A
  /// reply that failed (or a message that was not sent) is not the moment
  /// to offer "get told when it needs you".
  bool get _replyCompleted =>
      !_conn.busySessions.contains(widget.sessionID) &&
      _promptError == null &&
      _sendError == null &&
      _messages.any(
        (message) =>
            message.info.role == 'assistant' && message.info.errorText == null,
      );

  /// The notification question, or why "Notify me" did not work: a line in
  /// the status slot over the transcript's top, so the reply and its actions
  /// at the bottom never move when it comes or goes (F16).
  _ChatStatus? _notifyOfferStatus(BuildContext context) {
    final l10n = _chatL10n(context);
    final offer = _notifyOffer;
    if (offer.failure case final failure?) {
      final error = offer.failureError;
      return _ChatStatus(
        id: 'notify-failed',
        icon: AppIconography.error,
        tone: AppStatusTone.failure,
        message: switch (failure) {
          FirstReplyNotifyFailure.saveFailed => l10n.consentSaveFailed,
          // The app's own sentence from the Android side ("Notification
          // access is required.") stays; exception text is said in words.
          FirstReplyNotifyFailure.notEnabled =>
            error == null ? l10n.e7SettingsUi22 : productErrorText(error),
        },
        onDismiss: offer.dismissFailure,
      );
    }
    if (!offer.showFor(replyCompleted: _replyCompleted)) return null;
    return _ChatStatus(
      id: 'notify-offer',
      icon: AppIconography.inbox,
      message: l10n.firstRunNotifyTitle,
      action: KitAction(
        key: const ValueKey('chat-notify-offer-accept'),
        label: l10n.firstRunNotifyAccept,
        onPressed: offer.working ? null : () => unawaited(offer.accept()),
      ),
      onDismiss: offer.working ? null : () => unawaited(offer.decline()),
      dismissTooltip: l10n.firstRunNotifyDecline,
    );
  }

  /// The free-model note's Dismiss: hidden now, and kept dismissed for this
  /// conversation on this server.
  void _dismissFreeModelNote() {
    _setChatState(() => _freeModelNoteDismissed = true);
    final profileId = _conn.profile?.id;
    if (profileId == null) return;
    unawaited(
      FreeModelNoteDismissals.dismiss(
        _conn.store.prefs,
        profileId,
        widget.sessionID,
      ),
    );
  }

  /// The free-model note's way out: the provider sign-ins of this server.
  Future<void> _signInToProvider() async {
    if (_conn.isIsolated) return;
    await Navigator.of(context).push(
      KitPageRoute<void>(
        builder: (_) => IntegrationsScreen(
          controller: _conn,
          mode: IntegrationsMode.providers,
        ),
      ),
    );
  }

  /// The providers/integrations screen, reached from a provider-auth error
  /// card; the same destination the `/integrations` command opens.
  Future<void> _openProviders() async {
    if (_conn.isIsolated) return;
    // An agent on this phone signs in on its own (the agent sheet's
    // terminal sign-in), not through OpenCode's providers.
    if (_conn.isAgentBackend) {
      final agentId =
          AgentCatalog.builtIn.agents
              .where((agent) => agent.name == _conn.profile?.name)
              .firstOrNull
              ?.id ??
          'claude';
      await showAgentSheet(
        context,
        agentId: agentId,
        step: AgentSheetStep.signIn,
      );
      return;
    }
    await Navigator.of(context).push(
      KitPageRoute<void>(builder: (_) => IntegrationsScreen(controller: _conn)),
    );
  }

  /// Sends "Continue" through the normal send path after an output-length
  /// cut, keeping any half-typed draft for afterwards.
  /// The words of the newest turn's prompt when that turn got no answer
  /// and the prompt is words alone (a resend from here cannot bring files).
  String? get _unansweredWords {
    final prompt = _unanswered?.$1;
    if (prompt == null || prompt >= _messages.length) return null;
    return _promptWordsOnly(_messages[prompt]);
  }

  /// The model a "model not found" [raw] error suggests, when this server
  /// has it ([_suggestedModel]).
  CatalogModel? _suggestedModelFor(String? raw) {
    final models = _conn.catalog?.models;
    if (raw == null || models == null || models.isEmpty) return null;
    return _suggestedModel(raw, models);
  }

  /// A model by the name the catalog gives it.
  String _modelName(CatalogModel model) => model.name.trim().isNotEmpty
      ? model.name.trim()
      : presentedModelLabel(model.providerID, model.id);

  /// Sends the unanswered prompt again, as it was, after switching this
  /// conversation to [model] when one is given ("Use GPT-5.6 Pro and
  /// resend"). Whatever the message box holds is set aside for the send and
  /// put back after it.
  Future<void> _resendUnanswered({CatalogModel? model}) async {
    final words = _unansweredWords;
    if (words == null || _sending) return;
    if (model != null) {
      try {
        await _conn.selectModelForSession(
          widget.sessionID,
          ModelRef(providerID: model.providerID, modelID: model.id),
        );
      } catch (error) {
        if (mounted) _showActionError(error);
        return;
      }
      if (!mounted) return;
    }
    final draft = _composer.value;
    final draftAttachments = List<PromptAttachment>.of(_attachments);
    _setChatState(() {
      _attachments.clear();
      _promptError = null;
    });
    _composer.text = words;
    await _send();
    if (!mounted) return;
    if (draft.text.isNotEmpty || draftAttachments.isNotEmpty) {
      _composer.value = draft;
      _setChatState(
        () => _attachments
          ..clear()
          ..addAll(draftAttachments),
      );
    }
  }

  SessionRetryState? get _retryState => _conn.retryStates[widget.sessionID];

  /// Starts the one-second countdown ticker when the session enters a retry
  /// backoff and cancels it as soon as the backoff clears, so an idle chat
  /// never pays for a periodic rebuild.
  void _syncRetryTicker() {
    final retry = _retryState;
    if (retry == null || retry.next == null) {
      _retryTicker?.cancel();
      _retryTicker = null;
      return;
    }
    _retryTicker ??= Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      if (_retryState == null) {
        _syncRetryTicker();
        return;
      }
      _setChatState(() {});
    });
  }

  /// The finish haptic ([KitHaptics.done]) when this session goes from
  /// busy to idle: the reply the person waited for is in. Keeps the editor
  /// in place; skipped under reduced motion.
  void _noteRunFinished() {
    if (_conn.isIsolated) return;
    final busy = _conn.busySessions.contains(widget.sessionID);
    final finished = _wasBusy && !busy;
    _wasBusy = busy;
    if (!finished) return;
    // The server's own idle ends the turn this phone started.
    _localTurnSince = null;
    KitHaptics.done(context);
  }

  /// Shows a composer-local note above the field for three seconds. Used
  /// for outcomes about the draft itself (queued, staged, already present)
  /// so they never cover the field as a snackbar would.
  void _showComposerNote(String text, {Key? key}) {
    if (!mounted) return;
    _composerNoteTimer?.cancel();
    _setChatState(() {
      _composerNote = text;
      _composerNoteKey = key;
    });
    _composerNoteTimer = Timer(const Duration(seconds: 3), () {
      if (!mounted) return;
      _setChatState(() {
        _composerNote = null;
        _composerNoteKey = null;
      });
    });
  }

  /// Confirms a reconnect flush that delivered queued drafts, closing the
  /// loop the "Queued — will send when reconnected" note opened. Also names
  /// drafts the flush deliberately left for other servers.
  void _announceCompletedFlush() {
    if (_offlineFlushRevision == _conn.offlineFlushRevision) return;
    _offlineFlushRevision = _conn.offlineFlushRevision;
    final sent = _conn.lastFlushedPromptCount;
    if (sent <= 0) return;
    final waiting = _conn.lastFlushSkippedForOtherProfiles;
    final message = StringBuffer(_chatL10n(context).chatUiQueuedSent(sent));
    if (waiting > 0) {
      message.write(_chatL10n(context).chatUiOtherDraftsWaitingSuffix(waiting));
    }
    // About the person's own drafts, so it sits by the composer.
    _showComposerNote(message.toString());
  }
}
