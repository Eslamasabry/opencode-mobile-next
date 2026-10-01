part of '../chat_screen.dart';

// The composer's draft: saving it as the session's draft, recovering its
// attachments, and the leave flow that asks before an unsaved draft is lost.

mixin _ChatDraftFields {
  bool _draftTrackingEnabled = false;
  bool _restoringDraftAttachments = false;
  bool _draftRecoveryBlocked = false;
  Future<void>? _draftRecoveryFuture;
  List<PromptAttachment> _lastDraftAttachments = const [];
  late final int _draftLocation;
  late final String? _draftDirectory;
  late final String? _draftWorkspace;
  Timer? _draftSaveTimer;
  String _lastDraftText = '';
  late final String _draftProfileID;
  int _draftWriteGeneration = 0;
  SessionDraftFailure? _draftSaveFailure;
  bool _allowRoutePop = false;
  bool _leavingProvisionalSession = false;
}

extension _ChatDrafts on _ChatScreenState {
  /// Saves the composer text as this session's draft (or clears the draft
  /// when the composer is empty). Runs on navigation away, app pause, and
  /// after sends so the persisted draft always mirrors the composer.
  Future<bool> _persistDraft() async {
    _draftSaveTimer?.cancel();
    // Conversation review is transient. Only an explicit Send publishes text;
    // lifecycle, debounce and route persistence must not save an utterance.
    if (_conn.isIsolated || _voiceConversation || _watching) return true;
    final sessionID = widget.sessionID;
    final generation = ++_draftWriteGeneration;
    final text = _promptHistory.original?.text ?? _composer.text;
    final attachments = List<PromptAttachment>.of(_attachments);
    final wasRecovering = _draftRecoveryBlocked;
    try {
      await _draftRecoveryFuture;
      if (_draftRecoveryBlocked) return false;
      if (widget.sessionID != sessionID) return false;
      await _conn.saveSessionDraft(
        sessionID,
        text,
        profileID: _draftProfileID,
        attachments: wasRecovering ? _attachments : attachments,
        attachmentDirectory: _draftDirectory,
        attachmentWorkspace: _draftWorkspace,
      );
      if (mounted &&
          generation == _draftWriteGeneration &&
          _draftSaveFailure != null) {
        _setChatState(() => _draftSaveFailure = null);
      }
      return true;
    } catch (error) {
      if (mounted && generation == _draftWriteGeneration) {
        _setChatState(
          () => _draftSaveFailure = error is SessionDraftWriteException
              ? error.failure
              : SessionDraftFailure.storage,
        );
      }
      return false;
    }
  }

  Future<void> _recoverDraftAttachments() async {
    _setChatState(() {
      _restoringDraftAttachments = true;
      _draftRecoveryBlocked = true;
    });
    try {
      final recovered = await _conn.restoreDraftAttachments(
        widget.sessionID,
        profileID: _draftProfileID,
        directory: _draftDirectory,
        workspace: _draftWorkspace,
      );
      if (!mounted || _draftLocation != _conn.locationRevision) return;
      _setChatState(() => _restoringDraftAttachments = false);
      if (recovered.unavailable.isNotEmpty) {
        final accept = await showKitConfirm(
          context,
          icon: AppIconography.attach,
          title: _chatL10n(context).draftAttachmentRecoveryTitle,
          body: _chatL10n(
            context,
          ).draftAttachmentRecoveryDetail(recovered.unavailable.join(', ')),
          confirmLabel: _chatL10n(context).draftUseAvailableAttachments,
          cancelLabel: _chatL10n(context).draftKeepSavedAttachments,
        );
        if (!mounted || !accept || _draftLocation != _conn.locationRevision) {
          return;
        }
      }
      _setChatState(() {
        _attachments
          ..clear()
          ..addAll(recovered.attachments);
        _draftRecoveryBlocked = false;
        _draftSaveFailure = null;
      });
      _draftSaveTimer?.cancel();
      _draftSaveTimer = Timer(const Duration(milliseconds: 600), _persistDraft);
    } catch (_) {
      // Keep the stored snapshot intact until the user explicitly recovers it.
    } finally {
      if (mounted) {
        _setChatState(() {
          _restoringDraftAttachments = false;
          if (_draftRecoveryBlocked) {
            _draftSaveFailure = SessionDraftFailure.attachments;
          }
        });
      }
    }
  }

  /// True once the draft is saved.
  Future<bool> _retryDraftPersistence() async {
    if (_draftRecoveryBlocked) {
      _draftRecoveryFuture = _recoverDraftAttachments();
    }
    return _persistDraft();
  }

  String _draftFailureText(SessionDraftFailure failure) => switch (failure) {
    SessionDraftFailure.attachments => _chatL10n(
      context,
    ).draftAttachmentsFailed,
    SessionDraftFailure.storage =>
      _composer.text.trim().isEmpty
          ? _chatL10n(context).draftClearFailed
          : _chatL10n(context).draftSaveFailed,
    SessionDraftFailure.full => _chatL10n(context).draftStorageFull,
    SessionDraftFailure.profileRemoved => _chatL10n(
      context,
    ).draftProfileRemoved,
  };

  void _clearDraftText() {
    if (_promptShelfBusy) return;
    final previous = _composer.value;
    _composer.clear();
    _persistDraft();
    showKitUndo(
      _undoHost,
      message: _chatL10n(context).composerDraftCleared,
      key: const Key('composer-cleared-undo'),
      onUndo: () {
        if (!mounted) return;
        // Never overwrite text entered since Clear. Keep both drafts.
        final current = _composer.text;
        _composer.value = current.isEmpty
            ? previous
            : TextEditingValue(
                text: '${previous.text}\n\n$current',
                selection: TextSelection.collapsed(
                  offset: previous.text.length + 2 + current.length,
                ),
              );
        _persistDraft();
        _focus.requestFocus();
      },
    );
    _focus.requestFocus();
  }

  /// Deletes the conversation this screen made when the person leaves it
  /// untouched. Best effort: when OpenCode cannot confirm it is still empty
  /// (or cannot delete it), it stays, as an ordinary empty conversation in
  /// the list, and nothing interrupts the person on their way out.
  Future<void> _discardUntouchedMobileSession() async {
    if (_conn.isIsolated) return;
    if (!widget.discardIfUntouched ||
        _messages.isNotEmpty ||
        _pendingSends.isNotEmpty ||
        _sending ||
        // A typed draft persists per session, so the session must survive
        // to give that draft a home to be restored into.
        _composer.text.trim().isNotEmpty ||
        _attachments.isNotEmpty ||
        _draftRecoveryBlocked ||
        _photoBusy ||
        (_conn.promptPhotos.pending?.sessionID == widget.sessionID &&
            _conn.promptPhotos.pending?.profileID == _draftProfileID) ||
        _conn.busySessions.contains(widget.sessionID)) {
      return;
    }
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) return;
      final currentMessages = await api.messagePage(widget.sessionID, limit: 1);
      if (currentMessages.items.isNotEmpty || currentMessages.hasMore) {
        return;
      }
      await api.deleteSession(widget.sessionID);
      try {
        await _conn.refreshSessions();
      } catch (_) {
        // The exact empty session is already gone. The destination screen will
        // reconcile on its normal refresh even if this optional refresh fails.
      }
    } catch (_) {
      // Kept: an empty conversation is harmless and the list shows it.
    }
  }

  /// The draft could not be saved on the way out. The sheet offers what its
  /// words say: copy the text and leave (the main answer), try saving
  /// again (it leaves once the save works), or leave without saving. Back,
  /// Esc and a swipe keep editing. True when the chat should close.
  Future<bool> _askLeaveUnsavedDraft() async {
    final l10n = _chatL10n(context);
    final navigator = Navigator.of(context);
    final text = _composer.text;
    final hasText = text.trim().isNotEmpty;
    final stillFailing = ValueNotifier<bool>(false);
    final retry = ValueNotifier<KitAction?>(null);
    var retrying = false;
    var open = true;
    void close() {
      if (!open) return;
      open = false;
      navigator.pop(true);
    }

    late final VoidCallback showRetry;
    Future<void> tryAgain() async {
      retrying = true;
      stillFailing.value = false;
      showRetry();
      final saved = await _retryDraftPersistence();
      retrying = false;
      if (!mounted || !open) return;
      if (saved) {
        close();
        return;
      }
      stillFailing.value = true;
      showRetry();
    }

    showRetry = () => retry.value = KitAction(
      key: const ValueKey('leave-draft-retry'),
      label: l10n.draftLeaveRetry,
      working: retrying,
      onPressed: retrying ? null : () => unawaited(tryAgain()),
    );
    showRetry();
    // The two notifiers outlive the sheet on purpose: a save still running
    // when the sheet is dismissed reports into them afterwards.
    final answer = await showKitSheet<bool>(
      context,
      sheetKey: const ValueKey('leave-unsaved-draft'),
      icon: AppIconography.save,
      title: l10n.draftLeaveTitle,
      body: (_) => ValueListenableBuilder<bool>(
        valueListenable: stillFailing,
        builder: (context, failed, _) => Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(
              hasText ? l10n.draftLeaveMessage : l10n.draftLeaveMessageNoText,
              tone: KitTextTone.secondary,
            ),
            if (failed) ...[
              SizedBox(height: KitTokens.of(context).space3),
              KitNotice(
                key: const ValueKey('leave-draft-still-failing'),
                tone: AppStatusTone.failure,
                message: l10n.draftLeaveStillFailing,
              ),
            ],
          ],
        ),
      ),
      primary: hasText
          ? KitAction(
              key: const ValueKey('leave-draft-copy'),
              label: l10n.draftLeaveCopyAction,
              icon: AppIconography.copy,
              onPressed: () async {
                if (!open) return;
                await KitCopy.copy(context, text, redact: false);
                close();
              },
            )
          : null,
      primaryListenable: hasText ? null : retry,
      secondaryListenable: hasText ? retry : null,
      tertiary: [
        KitAction(
          key: const ValueKey('leave-draft-discard'),
          label: l10n.draftLeaveAction,
          destructive: true,
          onPressed: close,
        ),
      ],
    );
    open = false;
    return answer == true;
  }

  Future<void> _leaveChat() async {
    if (_leavingProvisionalSession) return;
    final textBeforeSave = _composer.text;
    final attachmentsBeforeSave = List.of(_attachments);
    final saved = await _persistDraft();
    if (!mounted) return;
    if (!saved) {
      final leave = await _askLeaveUnsavedDraft();
      if (!mounted || !leave) return;
    }
    if (_composer.text != textBeforeSave ||
        !listEquals(attachmentsBeforeSave, _attachments)) {
      return;
    }
    _leavingProvisionalSession = true;
    await _discardUntouchedMobileSession();
    if (!mounted) return;
    _setChatState(() => _allowRoutePop = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.of(context).maybePop();
    });
  }
}
