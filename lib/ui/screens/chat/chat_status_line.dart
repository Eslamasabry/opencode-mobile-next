part of '../chat_screen.dart';

// The one status line under the top bar: what this conversation says
// about itself, most important first.

extension _ChatStatusLine on _ChatScreenState {
  /// Whether two error texts are the same problem: the same words, or the
  /// same recognised cause (a banner from a session event and the reply's
  /// own error often differ by a prefix or a trailing sentence).
  static bool _sameError(String? a, String b) {
    if (a == null) return false;
    if (a.trim() == b.trim()) return true;
    final cause = classifyAgentError(a);
    return cause != null && cause == classifyAgentError(b);
  }

  KitStatus? _chatStatusLine(
    BuildContext context, {
    required Session? session,
    required String? shareUrl,
    required String? parentID,
    required List<Session> siblings,
    required int siblingIndex,
  }) {
    return _chatStatus([
      if (widget.watch case final watch?) _watchingStatus(watch),
      if (!_watching) ...[
        if (_sendError case final error?)
          _sendErrorStatus(
            context,
            error: error,
            onDismiss: () => _setChatState(() => _sendError = null),
            onSignIn: switch (agentRunFailureOf(error)) {
              final run? => _agentSignIn(run),
              null => null,
            },
            onChooseModel: _chooseAnotherModel(),
            onTryAgain: () => unawaited((_retrySend ?? _send)()),
            onContinueElsewhere: _conn.isIsolated
                ? null
                : () => unawaited(_continueWithAnotherAgent()),
            accountNeeded: switch (agentRunFailureOf(error)) {
              final run? => _accountNeeded(run),
              null => false,
            },
          ),
        if (_promptError case final promptError?
            when !_messages.any(
              (message) => _sameError(message.info.errorText, promptError),
            ))
          _promptErrorStatus(
            context,
            message: promptError,
            onDismiss: () => _setChatState(() => _promptError = null),
            onChooseModel: _conn.isIsolated
                ? null
                : () => showModelPicker(
                    context,
                    applyScope: _modelApplyScope,
                    sessionID: widget.sessionID,
                  ),
            onResend: _unansweredWords == null
                ? null
                : () => unawaited(_resendUnanswered()),
            suggestion: switch (_suggestedModelFor(promptError)) {
              final model? when _unansweredWords != null => _modelName(model),
              _ => null,
            },
            onUseSuggestion: switch (_suggestedModelFor(promptError)) {
              final model? when _unansweredWords != null => () => unawaited(
                _resendUnanswered(model: model),
              ),
              _ => null,
            },
          ),
        _queuedDraftsStatus(
          context,
          _conn,
          onMove: (source) => unawaited(
            showQueuedPromptMoveSheet(
              context,
              connection: _conn,
              source: source,
              onProblem: (message, {details}) => _showComposerNote(message),
            ),
          ),
        ),
        if (!_conn.isIsolated &&
            _conn.supportsStagedRevert &&
            session?.reverted == true)
          _stagedRevertStatus(
            context,
            onReview: () => unawaited(_reviewStagedRevert()),
          )
        else if (!_conn.isIsolated &&
            _conn.capabilities.sessionRevert &&
            session?.reverted == true)
          _undoneStatus(context, onPutBack: () => unawaited(_restore())),
        if (!_conn.isIsolated && parentID != null)
          _subagentStatus(
            context,
            position: siblingIndex < 0 ? null : siblingIndex + 1,
            total: siblings.isEmpty ? null : siblings.length,
            onParent: _openParentSession,
            onAll: _showSubagents,
            // The one line cannot carry both: this conversation's parent
            // link keeps it, and says that approvals are automatic.
            supporting: _autoApproveStatus(context)?.message,
          ),
        if (!_conn.isIsolated && shareUrl != null)
          _sharedStatus(context, url: shareUrl, onStop: _stopSharing),
        // Before the first send with nothing signed in: why a send cannot
        // work. It outranks the Auto-approve note.
        ?_noModelStatus(context),
        // Replies here come from OpenCode's free model because no provider
        // is signed in: one quiet caption in the status slot (the reply at
        // the bottom never moves), dismissed once per conversation. Above
        // Auto-approve so only one quiet note shows; "No model signed in"
        // and every real status line outrank it.
        if (!_freeModelNoteDismissed &&
            freeModelNoteDue(_conn, widget.sessionID) &&
            _noteShown(_freeModelNoteShown))
          _ChatStatus(
            id: 'free-model',
            icon: AppIconography.speed,
            quiet: true,
            message: _chatL10n(context).chatFreeModelNote,
            action: KitAction(
              key: const ValueKey('chat-free-model-sign-in'),
              label: _chatL10n(context).chatFreeModelAddKey,
              onPressed: () => unawaited(_signInToProvider()),
            ),
            onDismiss: _dismissFreeModelNote,
          ),
        // The one standing line while approvals are automatic: muted, not a
        // warning, and the way out is one tap on it.
        ?_autoApproveStatus(context),
        // Below every real status line: first run's one notification
        // question (and what went wrong turning it on).
        _notifyOfferStatus(context),
      ],
    ]);
  }

  /// "Auto-approve is on · Turn off" (owner decision 4): shown only while
  /// the agent works and this conversation approves automatically, in a window
  /// too short for the Auto-approve chip over the composer (the risky-switch
  /// rule). Where the chip shows, it says it, so the line does not. Paused
  /// (disconnected), it says so and offers nothing: nothing is being
  /// approved.
  ///
  /// It says its piece once per conversation (owner, 2026-10-27): it folds
  /// away by itself after a few seconds, or on Dismiss, and stays away; the
  /// chip over the composer (idle) and the + sheet keep the state.
  _ChatStatus? _autoApproveStatus(BuildContext context) {
    if (!_approvalModeOffered(_conn) || !_agentWorking) return null;
    // The chip over the composer stays while the agent works and says it.
    if (!_conn.isIsolated && _bodyHeightSeen >= 480) return null;
    final effective = _conn.autoApprovalFor(widget.sessionID);
    if (!effective.automatic || _autoApproveNoteSaid) return null;
    _noteShown(_autoApproveNoteShown);
    final strings = _chatL10n(context);
    final paused = !_conn.isConnected && !_conn.connectionStarting;
    return _ChatStatus(
      id: 'auto-approve',
      icon: paused ? AppIconography.pause : AppIconography.shield,
      quiet: true,
      onDismiss: _dismissAutoApproveNote,
      message: paused
          ? strings.chatStripAutoApprovePaused
          : strings.chatAutoApproveOn,
      action: paused
          ? null
          : KitAction(
              key: const ValueKey('chat-auto-approve-off'),
              label: strings.chatAutoApproveTurnOff,
              onPressed: () =>
                  unawaited(_chooseApprovalMode(_ApprovalChoice.ask)),
            ),
    );
  }

  /// Starts a standing note's own clock after this frame (never during the
  /// build); true, so it can sit in a collection-if.
  bool _noteShown(VoidCallback start) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) start();
    });
    return true;
  }

  /// "No model signed in · Sign in", quiet, in the status slot; never while a
  /// model answers (the Model row of the + sheet says the same).
  _ChatStatus? _noModelStatus(BuildContext context) {
    if (_conn.isIsolated || !_modelChipWidget().signInNeeded) return null;
    final strings = _chatL10n(context);
    return _ChatStatus(
      id: 'no-model',
      icon: AppIconography.model,
      quiet: true,
      message: strings.chatNoModelSignedIn,
      action: KitAction(
        key: const ValueKey('chat-no-model-sign-in'),
        label: strings.chatNoModelSignIn,
        onPressed: () => unawaited(_signInToProvider()),
      ),
    );
  }
}
