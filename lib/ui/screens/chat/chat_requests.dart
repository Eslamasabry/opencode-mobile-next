part of '../chat_screen.dart';

// Requests that wait on the person: permissions, questions and forms, and
// the region above the composer that holds them.

mixin _ChatRequestFields {
  String? _activePermissionID;
  bool _questionReplying = false;
  String? _activeFormID;
}

extension _ChatRequests on _ChatScreenState {
  /// Opens the form renderer from the inline card. Forms no longer
  /// auto-present: the card above the composer is the entry point, so an
  /// arriving form never steals the keyboard.
  Future<void> _openForm(Api2FormInfo form) async {
    if (_activeFormID != null) return;
    _activeFormID = form.id;
    try {
      await presentConnectionForm(context, _conn, form);
    } finally {
      _activeFormID = null;
    }
  }

  /// The Review path from the attention card: the full permission sheet,
  /// now dismissible — closing it leaves the card in place.
  Future<void> _showPermissionDialog(PermissionRequest permission) async {
    if (_activePermissionID != null) return;
    _activePermissionID = permission.id;
    final tool = permission.tool;
    try {
      await showPermissionSheet(
        context,
        permission: permission,
        controller: _conn,
        onShowSource: tool == null
            ? null
            : () => _jumpToMessage(tool.messageID),
      );
    } finally {
      _activePermissionID = null;
    }
  }

  /// The inline question card's answer path: the same controller call the
  /// Activity sheet's Send answers makes, so the server sees one contract.
  Future<void> _answerQuestion(
    PendingQuestion question,
    List<List<String>> answers,
  ) async {
    if (_questionReplying) return;
    _setChatState(() => _questionReplying = true);
    try {
      await _conn.answerQuestion(question.id, answers);
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) _setChatState(() => _questionReplying = false);
    }
  }

  /// More / Answer on the question card: the full sheet Activity uses.
  Future<void> _showQuestionSheet(PendingQuestion question) =>
      showQuestionSheet(context, _conn, question);

  /// Whether a pending permission request is for this tool call: its step
  /// then says "Waiting for you", never "Running" (AUTO-15).
  bool _toolWaitsForYou(Part part) {
    final callID = part.callID;
    if (callID == null || callID.isEmpty) return false;
    return _conn
        .permissionsForSession(widget.sessionID)
        .any((request) => request.tool?.callID == callID);
  }

  /// A permission request lands as an inline card above the composer —
  /// oldest first, one at a time — instead of a modal sheet that steals the
  /// keyboard mid-sentence; then a question, then a retry countdown. The
  /// slot unfolds when one arrives and folds away when none is left
  /// ([KitReveal], instant under reduced motion); one card replacing
  /// another changes in place.
  Widget _attentionRegion(
    List<PermissionRequest> pendingPermissions, {
    bool arrive = true,
  }) {
    final permission = pendingPermissions.firstOrNull;
    final question = _conn.questionForSession(widget.sessionID);
    final retry = _retryState;
    // The card an Inbox row or a notification opened this chat for is
    // washed once where it settles (above the composer), never in the
    // loading layout it leaves a moment later.
    Widget landing(String requestID, Widget card) => arrive
        ? KitArrival(id: chatRequestArrivalId(requestID), child: card)
        : card;
    // Automatic approval is never silent, but it is a standing fact, not an
    // event: it lives in the chip strip above the composer
    // ([_composerStatusStrip]), not in this slot.
    return KitReveal(
      child: permission != null
          ? landing(
              permission.id,
              Column(
                key: ValueKey('permission-region-${permission.id}'),
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _PermissionAttentionCard(
                    key: ValueKey('permission-card-${permission.id}'),
                    permission: permission,
                    autoApprovalFailed:
                        _conn.autoApprovalFailure(permission.id) != null,
                    onReview: () =>
                        unawaited(_showPermissionDialog(permission)),
                  ),
                  // P6.7: the third identical ask offers "Always allow"
                  // once, directly under its card.
                  // Not while this conversation approves automatically: the
                  // person already chose not to be asked.
                  if (!_conn.isIsolated &&
                      !_conn.autoApprovalFor(permission.sessionID).automatic)
                    AlwaysAllowInvitation(
                      key: ValueKey('always-allow-${permission.id}'),
                      controller: _conn,
                      sessionID: permission.sessionID,
                      requestID: permission.id,
                    ),
                ],
              ),
            )
          : question != null
          ? landing(
              question.id,
              _QuestionAttentionCard(
                key: ValueKey('question-card-${question.id}'),
                question: question,
                replying: _questionReplying,
                onAnswer: (answers) =>
                    unawaited(_answerQuestion(question, answers)),
                onMore: () => unawaited(_showQuestionSheet(question)),
              ),
            )
          : retry != null
          ? _RetryAttentionCard(
              key: const ValueKey('retry-banner'),
              retry: retry,
            )
          : null,
    );
  }

  /// Whether the request slot over the composer has something in it.
  bool _attentionPending(List<PermissionRequest> pendingPermissions) =>
      pendingPermissions.isNotEmpty ||
      _conn.questionForSession(widget.sessionID) != null ||
      _retryState != null ||
      (!_conn.isIsolated && _conn.autoApprovalFor(widget.sessionID).automatic);
}
