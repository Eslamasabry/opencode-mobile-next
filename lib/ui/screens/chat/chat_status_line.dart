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
          ),
        if (!_conn.isIsolated && shareUrl != null)
          _sharedStatus(context, url: shareUrl, onStop: _stopSharing),
        // Below every real status line: first run's one notification
        // question (and what went wrong turning it on).
        _notifyOfferStatus(context),
        // Last, so anything else this chat says outranks it: replies
        // here come from OpenCode's free model because no provider is
        // signed in. A quiet line in the page's status slot, over the
        // transcript's top, so the reply at the bottom never moves;
        // dismissed once per conversation.
        if (!_freeModelNoteDismissed &&
            freeModelNoteDue(_conn, widget.sessionID))
          _ChatStatus(
            id: 'free-model',
            icon: AppIconography.speed,
            message: _chatL10n(context).freeModelNotice,
            action: KitAction(
              key: const ValueKey('chat-free-model-sign-in'),
              label: _chatL10n(context).freeModelSignIn,
              onPressed: () => unawaited(_signInToProvider()),
            ),
            onDismiss: _dismissFreeModelNote,
          ),
      ],
    ]);
  }
}
