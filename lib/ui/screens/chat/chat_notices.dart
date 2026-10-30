part of '../chat_screen.dart';

// What the chat tells the person: the first-reply notification offer, the
// free-model note, an unanswered prompt, retries, and composer notes.

mixin _ChatNoticeFields {
  int _dataRefreshRevision = 0;
  int _offlineFlushRevision = 0;

  /// Whether the last connection snapshot had this session running; the
  /// busy→idle edge is the "run finished" moment.
  bool _wasBusy = false;

  /// The connection's generation and data-refresh count when the running
  /// turn began (or the chat opened on it): a change by the time it ends
  /// means the stream dropped or the phone resumed while it ran.
  (int, int)? _turnStreamMark;

  /// Feedback kept beside the composer without replacing its editor, until
  /// the next change ([_ChatNotices._showComposerNote]).
  String? _composerNote;
  Key? _composerNoteKey;

  /// What the box held once the note took hold; other words dismiss it.
  String? _composerNoteTyped;

  /// What a screen reader hears of the turn ([_ChatNotices._sayTurnEnd]):
  /// a turn is in view from its start until its end has been said; the
  /// error words wait for the end, and so does "Reply finished".
  bool _turnInView = false;
  bool _turnEnded = false;
  bool _turnEndPending = false;
  String? _turnErrorWords;

  /// The conversation waited on the person at the last look.
  bool _waitedForYou = false;

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

  /// Folds the free-model note away once it has been on screen for
  /// [_ChatNotices._standingNoteSeenAfter].
  Timer? _freeModelNoteTimer;

  /// The Auto-approve note under the header: hidden at once on Dismiss or
  /// once seen, before the save lands; its timer, while it shows.
  bool _autoApproveNoteHidden = false;
  Timer? _autoApproveNoteTimer;

  /// The body's height as last laid out: the settings chips over the composer
  /// (which say Auto-approve is on) show from 480 dp up, so the note under the
  /// header only speaks where they do not.
  double _bodyHeightSeen = 0;

  /// First run's one notification question, asked in the status slot.
  late final FirstReplyNotifyOffer _notifyOffer;

  /// A send of words other than the message box's (a phrase chip, Send
  /// again, Continue) is running ([_sendInsteadOfDraft]).
  bool _sendingInstead = false;

  /// What the failure banner's Try again repeats when the last send was not
  /// the message box's own; null for the box's own Send.
  Future<void> Function()? _retrySend;

  /// The last change made to [AgentTurnFailureMemory] for this
  /// conversation, so a build writes only when it changes.
  String? _turnFailureNoted;
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
      icon: AppIconography.notificationImportant,
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

  /// How long a standing note (Auto-approve is on, the free model) stays
  /// before it folds away by itself; what it names stays where it lives.
  static const _standingNoteSeenAfter = Duration(seconds: 6);

  /// Starts the free-model note's own clock the first time it shows.
  void _freeModelNoteShown() {
    _freeModelNoteTimer ??= Timer(_standingNoteSeenAfter, () {
      if (mounted) _dismissFreeModelNote();
    });
  }

  /// Whether the Auto-approve note was already said in this conversation.
  bool get _autoApproveNoteSaid {
    if (_autoApproveNoteHidden) return true;
    final profileId = _conn.profile?.id;
    if (profileId == null) return false;
    return ChatNoticeMemory.seen(
      _conn.store.prefs,
      profileId,
      ChatNoticeMemory.autoApprove,
      widget.sessionID,
    );
  }

  /// Starts the Auto-approve note's own clock the first time it shows.
  void _autoApproveNoteShown() {
    _autoApproveNoteTimer ??= Timer(_standingNoteSeenAfter, () {
      if (mounted) _dismissAutoApproveNote();
    });
  }

  /// The Auto-approve note's Dismiss, or its clock running out: hidden now
  /// and kept said for this conversation on this server. The chip over the
  /// composer and the + sheet keep saying Auto-approve is on.
  void _dismissAutoApproveNote() {
    _autoApproveNoteTimer?.cancel();
    _setChatState(() => _autoApproveNoteHidden = true);
    final profileId = _conn.profile?.id;
    if (profileId == null) return;
    unawaited(
      ChatNoticeMemory.remember(
        _conn.store.prefs,
        profileId,
        ChatNoticeMemory.autoApprove,
        widget.sessionID,
      ),
    );
  }

  /// Auto-approve was turned on again: the next working turn says so once.
  void _rearmAutoApproveNote() {
    _autoApproveNoteTimer?.cancel();
    _autoApproveNoteTimer = null;
    _setChatState(() => _autoApproveNoteHidden = false);
    final profileId = _conn.profile?.id;
    if (profileId == null) return;
    unawaited(
      ChatNoticeMemory.forget(
        _conn.store.prefs,
        profileId,
        ChatNoticeMemory.autoApprove,
        widget.sessionID,
      ),
    );
  }

  /// The free-model note's Dismiss (or its clock): hidden now, and kept
  /// dismissed for this conversation on this server.
  void _dismissFreeModelNote() {
    _freeModelNoteTimer?.cancel();
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
      KitPageRoute<void>(
        builder: (_) => IntegrationsScreen(
          controller: _conn,
          mode: IntegrationsMode.providers,
        ),
      ),
    );
  }

  /// The sign-in (or account) of the agent that refused a send, when the
  /// app has one: an agent on this phone signs in from its sheet. Null for
  /// anything else (an agent on a computer signs in there).
  /// When the app already shows the agent signed in ([_accountNeeded]), it
  /// goes straight on to the agent's own sign-in, past the sheet's "Signed
  /// in · ready" summary.
  VoidCallback? _agentSignIn(AgentRunFailure failure) {
    if (_conn.isIsolated || !_conn.isAgentBackend) return null;
    final agentId = failure.provider;
    if (agentId == null || AgentCatalog.builtIn.byId(agentId) == null) {
      return null;
    }
    final again = _accountNeeded(failure);
    return () => unawaited(
      showAgentSheet(
        context,
        agentId: agentId,
        step: AgentSheetStep.signIn,
        signInAgain: again,
      ),
    );
  }

  /// A sign-in refusal from an agent the app already shows signed in or
  /// ready on this phone (Kilo signs in without an account and runs its free
  /// models): the model needs an account, the agent is not signed out.
  bool _accountNeeded(AgentRunFailure failure) {
    final agentId = failure.provider;
    if (failure.kind != AgentRunFailureKind.signIn || agentId == null) {
      return false;
    }
    if (_conn.isIsolated || !_conn.isAgentBackend) return false;
    final ConnectionController phone;
    try {
      phone = ProviderScope.containerOf(
        context,
        listen: false,
      ).read(connProvider);
    } catch (_) {
      return false;
    }
    return agentShownSignedIn(phone, agentId);
  }

  /// A failure's words in the app's language ([agentRunFailureWords]), with
  /// "needs an account" where [_accountNeeded].
  String _agentRunWords(AgentRunFailure failure) => agentRunFailureWords(
    failure,
    _chatL10n(context),
    accountNeeded: _accountNeeded(failure),
  );

  /// An error an agent on this phone ended a turn with, read as a refusal
  /// with its reason, agent and model (the Paseo bridge writes it as
  /// "[System Error] Internal error: … code: …"); null for any other
  /// conversation or message.
  AgentRunFailure? _agentRunFor(MessageInfo info) {
    final text = info.errorText;
    final provider = info.providerID;
    if (text == null || provider == null || !_conn.isAgentBackend) return null;
    if (AgentCatalog.builtIn.byId(provider) == null) return null;
    return AgentRunFailure(
      reason: text,
      provider: provider,
      model: info.modelID ?? _conn.sessionsById[widget.sessionID]?.model,
    );
  }

  /// This conversation's model picker, to leave a model that cannot answer.
  VoidCallback? _chooseAnotherModel() => _conn.isIsolated
      ? null
      : () => unawaited(
          showModelPicker(
            context,
            applyScope: _modelApplyScope,
            sessionID: widget.sessionID,
          ),
        );

  /// Sends [text] through the normal send path in place of what the message
  /// box holds, then gives the box back as it was, sent or not. A failed
  /// chip, Send again or Continue used to stay in the box (the failure put
  /// its words back) and the person's next words joined it ("pongSay pong",
  /// emulator 2026-10-27); the way that sent it is still one tap away, and
  /// the banner's Try again repeats it ([_retrySend]).
  Future<void> _sendInsteadOfDraft(String text) async {
    if (_sending) return;
    final draft = _composer.value;
    final draftAttachments = List<PromptAttachment>.of(_attachments);
    _setChatState(_attachments.clear);
    _composer.text = text;
    _sendingInstead = true;
    _retrySend = () => _sendInsteadOfDraft(text);
    try {
      await _send();
    } finally {
      _sendingInstead = false;
    }
    if (!mounted) return;
    _composer.value = draft;
    _setChatState(
      () => _attachments
        ..clear()
        ..addAll(draftAttachments),
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
    _setChatState(() => _promptError = null);
    await _sendInsteadOfDraft(words);
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
  /// in place; skipped under reduced motion. A turn that ran across a
  /// dropped stream or a resume may have lost its last events on the way:
  /// its end reads the newest history again (reconcile by refetch).
  void _noteRunFinished() {
    if (_conn.isIsolated) return;
    _sayWhenItNeedsYou();
    final busy = _conn.busySessions.contains(widget.sessionID);
    final finished = _wasBusy && !busy;
    if (busy && !_wasBusy) {
      _turnInView = true;
      _turnErrorWords = null;
    }
    // From the turn's start, or the first look at a turn already running
    // when the chat opened.
    if (busy && (!_wasBusy || _turnStreamMark == null)) {
      _turnStreamMark = _streamMarkNow;
    }
    _wasBusy = busy;
    if (!finished) return;
    // The server's own idle ends the turn this phone started.
    _localTurnSince = null;
    KitHaptics.done(context);
    _turnEnded = true;
    _scheduleTurnEndWords();
    final mark = _turnStreamMark;
    _turnStreamMark = null;
    if (mark != null && mark != _streamMarkNow) {
      _scheduleRecentHistoryRefresh();
    }
  }

  /// An error in the running turn (or a prompt refused before it ran): its
  /// plain words are what a screen reader hears when the turn ends, never
  /// the server's text. A Stop says what the turn says.
  void _noteTurnError(String raw, {bool stopped = false}) {
    if (_conn.isIsolated || (!_turnInView && _localTurnSince == null)) return;
    final l10n = _chatL10n(context);
    _turnInView = true;
    _turnErrorWords = stopped
        ? l10n.kitTurnStopped
        : _plainErrorHeadline(
            agentErrorWords(raw, l10n),
            MessageErrorKind.refineFromText(MessageErrorKind.unknown, raw),
            l10n,
          );
    _scheduleTurnEndWords();
  }

  /// Once this frame is done: an error and the turn's end arrive together,
  /// and the end says the error when there is one.
  void _scheduleTurnEndWords() {
    if (_turnEndPending) return;
    _turnEndPending = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _turnEndPending = false;
      if (mounted) _sayTurnEnd();
    });
    WidgetsBinding.instance.ensureVisualUpdate();
  }

  /// Tells a screen reader how the turn ended, once per turn: the reason in
  /// plain words when it failed, "Reply finished" when it did not. Nothing
  /// while it still runs (a retry after an error), and never per streamed
  /// word: only a turn's start and end lead here.
  void _sayTurnEnd() {
    if (!_turnInView || _conn.busySessions.contains(widget.sessionID)) return;
    // Cleared when the turn went on after the error (a retry, a new step).
    final failed = _promptError == null ? null : _turnErrorWords;
    if (failed == null && !_turnEnded) return;
    _turnInView = false;
    _turnEnded = false;
    _turnErrorWords = null;
    _sayToScreenReader(failed ?? _chatL10n(context).chatAnnounceReplyFinished);
  }

  /// Says once when this conversation starts waiting on the person (a
  /// permission, a question, a form); the request's own card has the rest.
  void _sayWhenItNeedsYou() {
    if (_watching) return;
    final waits = _waitsForYou;
    if (waits && !_waitedForYou) {
      _sayToScreenReader(_chatL10n(context).chatAnnounceNeedsYou);
    }
    _waitedForYou = waits;
  }

  /// A status change a screen reader user would otherwise miss: said only
  /// with TalkBack (or another screen reader) on and this chat in front.
  void _sayToScreenReader(String words) {
    if (!(MediaQuery.maybeAccessibleNavigationOf(context) ?? false) ||
        !(ModalRoute.of(context)?.isCurrent ?? true)) {
      return;
    }
    unawaited(
      SemanticsService.sendAnnouncement(
        View.of(context),
        words,
        Directionality.of(context),
      ),
    );
  }

  (int, int) get _streamMarkNow =>
      (_conn.connectionRevision, _conn.dataRefreshRevision);

  /// Shows a composer-local note above the field: outcomes about the draft
  /// itself (queued, staged, already present), so they never cover the field
  /// as a snackbar would. It stays until the next change (the next note, the
  /// next words in the box) or a tap on it, never on a timer: a slow reader,
  /// large text or a screen reader all get the whole sentence.
  void _showComposerNote(String text, {Key? key}) {
    if (!mounted) return;
    _composerNoteTyped = null;
    _setChatState(() {
      _composerNote = text;
      _composerNoteKey = key;
    });
    // The note's own action may clear or fill the box in the same moment;
    // what the box holds after this frame is where "the next words" start.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _composerNote == text) {
        _composerNoteTyped = _composer.text;
      }
    });
  }

  void _dismissComposerNote() {
    _composerNoteTyped = null;
    if (_composerNote == null) return;
    _setChatState(() {
      _composerNote = null;
      _composerNoteKey = null;
    });
  }

  /// Listens to the box: new words after the note dismiss it.
  void _composerNoteFollowsEdits() {
    final typed = _composerNoteTyped;
    if (typed != null && _composer.text != typed) _dismissComposerNote();
  }

  /// Confirms a reconnect flush that delivered queued drafts, closing the
  /// loop the "Queued — will send when reconnected" note opened. Also names
  /// drafts the flush deliberately left for other servers.
  void _announceCompletedFlush() {
    if (_offlineFlushRevision == _conn.offlineFlushRevision) return;
    _offlineFlushRevision = _conn.offlineFlushRevision;
    final sent = _conn.lastFlushedPromptCount;
    if (sent <= 0) return;
    // A prompt reached the server without the event stream carrying it back
    // (it was sent after a drop, or resent by hand). The stream is volatile,
    // so reconcile by refetch: the prompt and any reply show up now rather
    // than only after the chat is reopened.
    unawaited(_load());
    final waiting = _conn.lastFlushSkippedForOtherProfiles;
    final message = StringBuffer(_chatL10n(context).chatUiQueuedSent(sent));
    if (waiting > 0) {
      message.write(_chatL10n(context).chatUiOtherDraftsWaitingSuffix(waiting));
    }
    // About the person's own drafts, so it sits by the composer.
    _showComposerNote(message.toString());
  }
}

/// The note above the composer for composer-local outcomes (queued, staged,
/// already present). It replaces snackbars that used to cover the field the
/// person is typing into. It wraps instead of cutting the sentence: two or
/// three lines at most for the longest note, at 200 % text too (the cap of
/// six only guards the composer's room). A live region, so a screen reader
/// says it when it comes; a tap dismisses it.
class _ComposerNote extends StatelessWidget {
  const _ComposerNote({super.key, required this.text, required this.onDismiss});

  final String text;
  final VoidCallback onDismiss;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: KitLayout.readingWidth),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          // Its own node, so the live region is the note's words and not
          // the page around them.
          child: Semantics(
            container: true,
            liveRegion: true,
            label: text,
            onTap: onDismiss,
            onTapHint: _chatL10n(context).chatComposerNoteDismiss,
            excludeSemantics: true,
            child: KitTappable(
              onTap: onDismiss,
              shape: KitShape.button,
              surface: KitSurfaceLevel.ground,
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                widthFactor: 1,
                child: KitText(
                  text,
                  role: KitTextRole.caption,
                  tone: KitTextTone.secondary,
                  maxLines: 6,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
