part of '../chat_screen.dart';

/// The conversation side of the one-time nudges (UX plan 5.8 item 4). The
/// trigger rules live in [ConversationNudgeWatcher]; this only reads facts
/// from existing state, renders the single slot and wires each action to the
/// door that already exists for it.
extension _ChatNudges on _ChatScreenState {
  NudgeRegistry get _nudges => _conn.nudges;

  void _startNudges() {
    // The demo has no approvals, notifications or compaction to point at.
    if (_conn.isIsolated) return;
    _nudgeWatcher = ConversationNudgeWatcher(
      registry: _nudges,
      conversation: widget.sessionID,
    );
    _nudges.addListener(_nudgesChanged);
  }

  void _stopNudges() {
    if (_nudgeWatcher == null) return;
    _nudges.removeListener(_nudgesChanged);
    _nudgeWatcher?.dispose();
  }

  /// THE place to make nudges yield to another inline card above the
  /// composer (for example the phase 3b "Get told when it's done?" card):
  /// add its visibility here. While this is true no nudge is offered (and
  /// none is consumed) and a showing nudge is hidden until the card is gone.
  bool get _nudgeSuppressed =>
      _conn.permissionsForSession(widget.sessionID).isNotEmpty ||
      _conn.questionForSession(widget.sessionID) != null ||
      _conn.formForSession(widget.sessionID) != null ||
      _retryState != null ||
      FirstReplyNotifyOffer.pendingFor(_conn);

  ConversationNudgeFacts _nudgeFacts() {
    final busy = _conn.busySessions.contains(widget.sessionID);
    final summary = _conn.sessionsById[widget.sessionID]?.summary;
    return ConversationNudgeFacts(
      busy: busy,
      hasAssistantReply: _messages.any(
        (message) => message.info.role == 'assistant',
      ),
      pendingPermissions: {
        for (final permission in _conn.permissionsForSession(widget.sessionID))
          permission.id: permission.permission,
      },
      // The Approvals sheet is offered on every connected server (see
      // _openSessionMenu); it has nothing to add once it is already on.
      approvalsAvailable: !_conn.autoApprovalFor(widget.sessionID).automatic,
      runChangedFiles: runChangedFiles(_messages) || (summary?.files ?? 0) > 0,
      reviewAvailable: _conn.capabilities.sessionDiff,
      contextUsage: _contextWindowUsage(),
      compactAvailable: _supportsSessionCompact,
      finishedRunNotificationsReady: _conn.finishedRunNotificationsReady,
      suppressed: _nudgeSuppressed,
    );
  }

  /// Facts come from many sources (events, the catalog, permissions), all of
  /// which end in a rebuild, so they are reported once after each frame
  /// rather than from every mutation site. An offer notifies the registry,
  /// which must not happen while building.
  void _queueNudgeObservation() {
    if (_nudgeWatcher == null || _nudgeObserveQueued) return;
    _nudgeObserveQueued = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _nudgeObserveQueued = false;
      if (!mounted) return;
      _nudgeWatcher?.observe(_nudgeFacts());
      _claimModelDefault();
    });
  }

  /// P6.6a: once the catalog is loaded and the composer is shown, the
  /// model the app picked by itself (the server's default) is said once
  /// per server, here where it is used. An explicit pick, an unresolved
  /// catalog or a notice already given says nothing.
  void _claimModelDefault() {
    if (_modelDefaultClaimed || _conn.isIsolated || _watching) return;
    final choice = modelDefaultOf(_conn);
    if (choice.reason == DefaultReason.unresolved) return;
    _modelDefaultClaimed = true;
    unawaited(() async {
      final said = await claimDefaultNotice(
        kind: DefaultKind.model,
        choice: choice,
        profileId: _conn.profile?.id,
        prefs: _conn.store.prefs,
      );
      if (said == null || !mounted) return;
      _setChatState(() => _modelDefaultSaid = said);
    }());
  }

  /// The model notice: "Using {model}, this server's default model.", with
  /// "Choose another model" when there is another to choose.
  Widget? _modelDefaultNotice(BuildContext context) {
    final said = _modelDefaultSaid;
    if (said == null) return null;
    final strings = _chatL10n(context);
    final canChange = modelDefaultOf(_conn).canChange;
    void done() => _setChatState(() => _modelDefaultSaid = null);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: KitTokens.of(context).gutter,
        end: KitTokens.of(context).space1,
      ),
      child: canChange
          ? KitNotice.offer(
              key: const ValueKey('chat-default-model-notice'),
              message: strings.defaultModelNotice(KitBidi.auto(said)),
              icon: AppIconography.idea,
              action: KitAction(
                key: const ValueKey('chat-default-model-change'),
                label: strings.defaultModelChange,
                onPressed: () {
                  done();
                  unawaited(
                    showModelPicker(
                      context,
                      applyScope: _modelApplyScope,
                      sessionID: widget.sessionID,
                    ),
                  );
                },
              ),
              onDismiss: done,
              dismissKey: const ValueKey('chat-default-model-dismiss'),
              dismissLabel: strings.nudgeDismiss,
            )
          // The only model: nothing to choose, so only the close.
          : KitNotice(
              key: const ValueKey('chat-default-model-notice'),
              icon: AppIconography.idea,
              message: strings.defaultModelNotice(KitBidi.auto(said)),
              onDismiss: done,
            ),
    );
  }

  Widget _nudgeSlot(BuildContext context) {
    // The model the app picked is said first, once; a tip waits for it.
    if (_modelDefaultNotice(context) case final notice?) return notice;
    final active = _nudgeWatcher == null
        ? null
        : _nudges.activeFor(widget.sessionID);
    if (active == null || _nudgeSuppressed) return const SizedBox.shrink();
    final strings = _chatL10n(context);
    void done() => unawaited(_nudges.dismiss(active.id));
    final (message, actionLabel, icon, action) = switch (active.id) {
      NudgeId.approvals => (
        strings.nudgeApprovals(
          permissionRequestTitle(active.detail, l10n: strings),
        ),
        strings.approvalsUiMenu,
        AppIconography.shield,
        () => unawaited(
          showSessionApprovalsSheet(
            context,
            controller: _conn,
            sessionID: widget.sessionID,
          ),
        ),
      ),
      NudgeId.reviewChanges => (
        switch (_conn.sessionsById[widget.sessionID]?.summary?.files ?? 0) {
          final files when files > 0 => strings.nudgeReviewChangesCount(files),
          _ => strings.nudgeReviewChanges,
        },
        strings.demoReviewChanges,
        AppIconography.review,
        () => unawaited(_showDiff()),
      ),
      NudgeId.compact => (
        strings.nudgeCompact(active.detail),
        strings.chatUiCompactSession,
        AppIconography.idea,
        () => unawaited(_compact()),
      ),
      // Work owns the pin tip; a conversation never renders it.
      NudgeId.leaveAndBeTold || NudgeId.pinConversations => (
        strings.nudgeLeave,
        strings.e7GlossaryGotIt,
        AppIconography.notificationImportant,
        () {},
      ),
    };
    final wire = active.id.wire;
    // One sentence, its action and the close, on the transcript's rails
    // just above the composer, where the moment happened (KitNotice.offer).
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: KitTokens.of(context).gutter,
        end: KitTokens.of(context).space1,
      ),
      child: KitNotice.offer(
        key: ValueKey('nudge-$wire'),
        message: message,
        icon: icon,
        action: KitAction(
          key: ValueKey('nudge-$wire-action'),
          label: actionLabel,
          onPressed: () {
            // Taking the action is also the end of the tip.
            done();
            action();
          },
        ),
        onDismiss: done,
        dismissKey: ValueKey('nudge-$wire-dismiss'),
        dismissLabel: strings.nudgeDismiss,
      ),
    );
  }
}

mixin _ChatNudgeFields {
  // One-time nudges (UX plan 5.8): the rules live in the watcher, the screen
  // only reports facts and renders the slot. See chat/nudge_slot.dart.
  ConversationNudgeWatcher? _nudgeWatcher;
  bool _nudgeObserveQueued = false;

  /// P6.6a: the model the app picked by itself, said once per server
  /// where it is used (the composer); null once dismissed or not to say.
  String? _modelDefaultSaid;
  bool _modelDefaultClaimed = false;
}
