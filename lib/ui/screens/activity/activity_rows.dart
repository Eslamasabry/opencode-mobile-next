part of '../activity_screen.dart';

/// The finished and automatic rows and their actions.
extension _ActivityRows on _ActivityScreenState {
  void _reviewDigest(String sessionID, Object scope) {
    if (_currentDigestScope != scope) return;
    final controller = widget.controller;
    // P4.2a: what waits in the conversation is answered on its card there.
    for (final permission in controller.awaitingPermissions) {
      if (permission.sessionID == sessionID) {
        _openChat(sessionID, landOnRequestID: permission.id);
        return;
      }
    }
    for (final question in controller.questions.values) {
      if (question.sessionID == sessionID) {
        _openChat(sessionID, landOnRequestID: question.id);
        return;
      }
    }
    if (controller.capabilities.forms) {
      for (final form in controller.forms.values) {
        if (form.sessionID == sessionID) {
          _openChat(sessionID, landOnRequestID: form.id);
          return;
        }
      }
    }
    // Chat owns the authoritative review/task actions; do not create a second
    // diff cache or a route retaining another profile's content here.
    _openChat(sessionID);
  }

  /// Hides one digest at once and offers Undo (DATA-11 b: a deferred local
  /// act). When the window closes the dismissal is saved as the same
  /// "reviewed" mark Work's rows use, so it holds after a restart; a save
  /// that fails brings the row back.
  void _dismissDigest(Session session, int idle) {
    final key = (session.id, idle);
    final controller = widget.controller;
    final scope = controller.returnBriefScope;
    _set(() {
      _dismissedDigests.add(key);
      _expandedDigests.remove(key);
    });
    showKitUndo(
      context,
      message: _l10n(context).activityDigestHidden,
      undoKey: const ValueKey('activity-digest-undo'),
      onUndo: () {
        if (mounted) _set(() => _dismissedDigests.remove(key));
      },
      onCommit: () async {
        try {
          await controller.dismissReturnBrief(
            ReturnBrief.single(ReturnBriefRun(session: session, idleAt: idle)),
            expectedScope: scope,
          );
        } catch (_) {
          if (mounted) _set(() => _dismissedDigests.remove(key));
        }
      },
    );
  }

  /// What finished while the person was away, newest first: one ordinary
  /// row each in the Inbox's one list, its done mark and the word
  /// "Finished" carrying the state (owner rule R1: no headed sections). A
  /// row unfolds to its digest card. Only conversations the person has not
  /// looked at since they finished, where the server keeps that record.
  List<({DateTime at, Widget row})> _finishedRows() {
    final l10n = _l10n(context);
    final controller = widget.controller;
    final scope = _currentDigestScope;
    final reviewed = controller.returnBriefAcknowledgement;
    final sessions =
        controller.sessionsById.values.where((session) {
          final idle = session.time?.idle;
          return session.parentID == null &&
              session.directory == controller.directory &&
              session.workspaceID == controller.workspace &&
              idle != null &&
              idle > 0 &&
              !controller.busySessions.contains(session.id) &&
              !_dismissedDigests.contains((session.id, idle)) &&
              !reviewed.coversRun(session.id, idle) &&
              (!controller.supportsSessionReadState ||
                  controller.isSessionUnread(session));
        }).toList()..sort((a, b) {
          final byTime = b.time!.idle!.compareTo(a.time!.idle!);
          return byTime == 0 ? a.id.compareTo(b.id) : byTime;
        });
    final pendingKnown =
        !controller.permissionsLoading &&
        !controller.questionsLoading &&
        controller.permissionsError == null &&
        controller.questionsError == null &&
        (!controller.capabilities.forms ||
            (!controller.formsLoading && controller.formsError == null));
    final now = (widget.now ?? DateTime.now)();
    return [
      for (final session in sessions)
        (
          at: DateTime.fromMillisecondsSinceEpoch(session.time!.idle!),
          row: _finishedRow(session, scope, pendingKnown, now, l10n),
        ),
    ];
  }

  Widget _finishedRow(
    Session session,
    Object scope,
    bool pendingKnown,
    DateTime now,
    AppLocalizations l10n,
  ) {
    final controller = widget.controller;
    return KitExpandRow(
      key: ValueKey('activity-digest-${session.id}'),
      headerKey: ValueKey('activity-digest-${session.id}-header'),
      leading: KitStatusMark(
        state: KitMarkState.done,
        label: l10n.workFinished,
      ),
      title: presentedSessionTitle(
        session,
        fallback: l10n.globalSessionsUntitled,
        l10n: l10n,
      ),
      supporting: TextSpan(
        text: l10n.activityFinishedRow(
          relativeTimeLabel(session.time!.idle!, now: now, l10n: l10n),
        ),
      ),
      expanded: _expandedDigests.contains((session.id, session.time!.idle!)),
      onExpansionChanged: (_) => _set(() {
        final key = (session.id, session.time!.idle!);
        if (!_expandedDigests.remove(key)) _expandedDigests.add(key);
      }),
      children: [
        CompletionDigestCard(
          key: ValueKey((scope, session.id, session.time!.idle)),
          digest: CompletionDigest(
            sessionID: session.id,
            idleAt: session.time!.idle!,
            changedFiles: session.summary == null || session.summary!.files < 0
                ? null
                : session.summary!.files,
            pendingDecisions: !pendingKnown
                ? null
                : controller.awaitingPermissions
                          .where((p) => p.sessionID == session.id)
                          .length +
                      controller.questions.values
                          .where((q) => q.sessionID == session.id)
                          .length +
                      (controller.capabilities.forms
                          ? controller.forms.values
                                .where((f) => f.sessionID == session.id)
                                .length
                          : 0),
          ),
          onOpenConversation: () {
            if (scope == _currentDigestScope) _openChat(session.id);
          },
          onReview: () => _reviewDigest(session.id, scope),
          onRunResults: () {
            if (scope != _currentDigestScope) return;
            pushKitPage<void>(
              context,
              (_) => RunResultScreen(
                controller: controller,
                sessionID: session.id,
              ),
            );
          },
          onDismiss: () => _dismissDigest(session, session.time!.idle!),
        ),
      ],
    );
  }

  /// Every automatic act of this server and project the person has not
  /// dismissed, one row each (P6.2, AUTO-4, AUTO-13): the thing it was done
  /// to as the title, what was done and when as its line. Never Needs you:
  /// the done mark, no count, no badge.
  ///
  /// Routine reconnects are one current row per place (F4): the newest
  /// says when the app last found the server again, and the older ones
  /// fold into it, so a restart or a flaky network never fills the list.
  /// Dismissing that row dismisses what it folded.
  List<({DateTime at, Widget row})> _automaticRows() {
    final controller = widget.controller;
    final history = controller.automaticActivity;
    if (history == null) return const [];
    final l10n = _l10n(context);
    final now = (widget.now ?? DateTime.now)();
    final shown = <AutomaticAct>[];
    final folded = <String, List<String>>{};
    final reconnectRow = <String, String>{};
    for (final act in controller.automaticActsHere) {
      if (_dismissedActs.contains(act.id)) continue;
      if (act.kind == AutomaticActKind.reconnect) {
        // Newest first: the first reconnect of a place is its row.
        final row = reconnectRow[act.locationKey];
        if (row != null) {
          (folded[row] ??= []).add(act.id);
          continue;
        }
        reconnectRow[act.locationKey] = act.id;
      }
      shown.add(act);
    }
    return [
      for (final act in shown)
        (
          at: act.occurredAt,
          row: _AutomaticActRow(
            key: ValueKey('activity-auto-${act.id}'),
            act: act,
            now: now,
            title: _actTarget(act, l10n),
            words: _ActivityScreenState._actWords(act, l10n),
            undoResult: _undoResults[act.id],
            onOpen: act.sessionId == null
                ? null
                : () => _openChat(act.sessionId!),
            onUndo: history.canUndo(act.id)
                ? () => _undoAct(history, act.id)
                : null,
            dismiss: _dismissAct(
              history,
              act,
              l10n,
              also: folded[act.id] ?? const [],
            ),
          ),
        ),
    ];
  }

  /// What the act was done to: the conversation's current title where the
  /// app knows it, else the name saved with the act.
  String _actTarget(AutomaticAct act, AppLocalizations l10n) {
    final session = act.sessionId == null
        ? null
        : widget.controller.sessionsById[act.sessionId];
    if (session != null) {
      return presentedSessionTitle(
        session,
        fallback: l10n.globalSessionsUntitled,
        l10n: l10n,
      );
    }
    return presentedSessionTitleText(
      act.summary,
      fallback: l10n.globalSessionsUntitled,
      l10n: l10n,
    );
  }

  Future<void> _undoAct(AutomaticActivityController history, String id) async {
    final result = await history.undo(id);
    if (mounted) _set(() => _undoResults[id] = result);
  }

  /// Dismiss: the row goes at once, with Undo; the history keeps the act
  /// but stops listing it once the window closes (DATA-11 b). A save that
  /// fails brings the row back.
  KitSwipeAction _dismissAct(
    AutomaticActivityController history,
    AutomaticAct act,
    AppLocalizations l10n, {
    List<String> also = const [],
  }) {
    final ids = [act.id, ...also];
    return KitSwipeAction(
      id: ValueKey('activity-auto-dismiss-${act.id}'),
      label: l10n.whileAwayDismiss,
      icon: AppIconography.close,
      undoMessage: l10n.whileAwayDismissed(_actTarget(act, l10n)),
      onAct: () async {
        if (!mounted) return false;
        _set(() => _dismissedActs.addAll(ids));
        return true;
      },
      onUndo: () {
        if (mounted) _set(() => _dismissedActs.removeAll(ids));
      },
      onCommit: () async {
        final saved = await history.acknowledge(ids);
        if (!saved && mounted) _set(() => _dismissedActs.removeAll(ids));
      },
    );
  }
}
