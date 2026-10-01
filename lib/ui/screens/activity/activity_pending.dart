part of '../activity_screen.dart';

/// Loading pending questions and gates, and opening conversations.
extension _ActivityPending on _ActivityScreenState {
  /// Opens the Gate sheet a notification or link named, once the plugin
  /// controller has its first snapshot (or gave up): the sheet itself says
  /// when the gate is gone. Exactly one open per screen; nothing is sent.
  void _scheduleInitialGate() {
    final gateId = widget.initialTeamGateId;
    final team = widget.controller.orchestration;
    if (!mounted ||
        gateId == null ||
        team == null ||
        _initialGateHandled ||
        _initialGateScheduled) {
      return;
    }
    final settled =
        team.snapshot.hasData ||
        team.phase == OrchestrationPhase.failed ||
        team.phase == OrchestrationPhase.stopped;
    if (!settled) return;
    _initialGateScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initialGateScheduled = false;
      if (!mounted || _initialGateHandled) return;
      _initialGateHandled = true;
      if (team.capabilities.projectLifecycle &&
          team.projectController != null) {
        unawaited(openTeamProjectDestination(context, team, requestId: gateId));
        return;
      }
      final now = (widget.now ?? DateTime.now)();
      showGateSheet(context, team, gateId, now: () => now);
    });
  }

  void _scheduleInitialQuestion() {
    final sessionID = widget.initialQuestionSessionID;
    if (!mounted ||
        sessionID == null ||
        _initialQuestionHandled ||
        _initialQuestionScheduled) {
      return;
    }
    PendingQuestion? target;
    for (final question in widget.controller.questions.values) {
      if (question.sessionID == sessionID) {
        target = question;
        break;
      }
    }
    if (target == null) return;
    _initialQuestionScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _initialQuestionScheduled = false;
      if (!mounted || _initialQuestionHandled) return;
      final current = widget.controller.questions[target!.id];
      if (current == null || current.sessionID != sessionID) return;
      _initialQuestionHandled = true;
      // P4.2a: the notification lands on the question's card.
      _openChat(current.sessionID, landOnRequestID: current.id);
    });
  }

  /// Pending work only — the cheap half, run on entry so a notification tap
  /// never shows a stale queue.
  Future<void> _refreshPending() async {
    _set(() {
      _loading = true;
      _error = null;
    });
    try {
      await Future.wait([
        widget.controller.refreshPendingPermissions(),
        widget.controller.refreshPendingQuestions(),
        widget.controller.refreshPendingForms(),
      ]);
    } catch (error) {
      if (mounted) _set(() => _error = productErrorText(error));
    } finally {
      if (mounted) _set(() => _loading = false);
    }
  }

  /// Wake-safe manual refresh: reconciliation first, so a retained screen
  /// cannot query through a repository being retired after Android idle.
  /// Refreshes both halves — pending work and the session fleet.
  Future<void> _refresh() async {
    final failureMessage = _l10n(context).e7WorkspaceReconnectingAgain;
    if (_refreshing) return;
    _set(() {
      _refreshing = true;
      _error = null;
    });
    try {
      await widget.controller.profileMonitor.refresh();
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw ProductException(failureMessage);
      }
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) _set(() => _error = productErrorText(error));
    } finally {
      if (mounted) _set(() => _refreshing = false);
    }
    if (!mounted) return;
    await _refreshPending();
  }

  int _subagentCount(String rootID) {
    var count = 0;
    for (final session in widget.controller.sessionsById.values) {
      if (session.parentID == rootID) count += 1;
    }
    return count;
  }

  /// Opens [sessionID]; with [landOnRequestID] the chat lands on that
  /// request's card, with [landOnFailure] on its newest failed turn (P4.2a).
  Future<void> _openChat(
    String sessionID, {
    String? landOnRequestID,
    bool landOnFailure = false,
  }) async {
    final controller = widget.controller;
    final location = controller.locationRevision;
    final profile = controller.profile?.id;
    final l10n = _l10n(context);
    try {
      if (!controller.sessionsById.containsKey(sessionID)) {
        await controller.ensureSession(sessionID);
      }
      if (!mounted ||
          controller.locationRevision != location ||
          controller.profile?.id != profile) {
        return;
      }
      final session = controller.sessionsById[sessionID];
      if (session == null) {
        throw ProductException(
          controller.sessionDetailsErrors[sessionID] ??
              l10n.sessionsDetailsUnavailable,
        );
      }
      if (session.directory != null &&
          (session.directory != controller.directory ||
              session.workspaceID != controller.workspace)) {
        await controller.selectLocationForExistingSession(
          directory: session.directory,
          workspace: session.workspaceID,
        );
      }
      if (mounted && controller.profile?.id == profile) {
        // Speed contract item 2: the chat joins this history read.
        unawaited(controller.prefetchSessionTail(sessionID));
        Navigator.of(context).pushNamed(
          '/chat/$sessionID',
          arguments: ChatRouteArguments(
            landOnRequestID: landOnRequestID,
            landOnFailure: landOnFailure,
          ),
        );
      }
    } catch (error) {
      if (mounted) {
        await showKitAlert(
          context,
          title: l10n.activityOpenFailedTitle,
          body: productErrorText(error, l10n: l10n),
          alertKey: const ValueKey('activity-open-failed'),
        );
      }
    }
  }

  /// A request row's tap: the detail pane where it shows (expanded), the
  /// request's own sheet elsewhere.
  VoidCallback _opener(_Pick pick, VoidCallback openSheet) => () {
    if (KitScreen.showsDetail(context)) {
      _set(() => _picked = pick);
    } else {
      openSheet();
    }
  };

  /// The AI Team rows in the §47 order, oldest first within a rank so the
  /// thing blocked longest leads (UX plan 5.7): every gate of the snapshot
  /// plus the blocked agents. A run that completed
  /// since the last view (rank 7) is omitted: the controller keeps no
  /// per-view watermark, and inventing one here would mean guessing.
  List<_TeamRow> _teamRows(OrchestrationController? team) {
    if (team == null) return const [];
    final snapshot = team.snapshot;
    final now = (widget.now ?? DateTime.now)();
    final rows = <_TeamRow>[
      // A gate answered from here leaves once the host confirmed
      // (02-ux §6); until then it stays with its receipt chip.
      for (final gate in snapshot.gates)
        if (!teamGateAnswered(team, gate))
          _TeamRow(
            rank: teamActivityGateRank(gate.kind),
            at: gate.createdAt,
            widget: ActivityGateTile(
              key: ValueKey('activity-team-gate-${gate.id}'),
              gate: gate,
              team: team,
              serverName: widget.controller.profile?.name,
              now: now,
            ),
          ),
      for (final agent in snapshot.agents)
        if (agent.state == AgentState.blocked)
          _TeamRow(
            rank: teamActivityAgentBlockedRank,
            at: agent.lastActivity,
            widget: ActivityAgentBlockedTile(
              key: ValueKey('activity-team-agent-${agent.id}'),
              agent: agent,
              team: team,
              serverName: widget.controller.profile?.name,
              now: now,
            ),
          ),
    ];
    rows.sort((a, b) {
      final rank = a.rank.compareTo(b.rank);
      if (rank != 0) return rank;
      final at = a.at, bt = b.at;
      if (at == null || bt == null) return 0;
      return at.compareTo(bt);
    });
    return rows;
  }

  /// The picked request's view for the detail pane, or null when nothing is
  /// picked or the pick was answered meanwhile (here or on another device).
  Widget? _detail() {
    final pick = _picked;
    if (pick == null) return null;
    final controller = widget.controller;
    switch (pick.kind) {
      case _PickKind.permission:
        for (final permission in controller.awaitingPermissions) {
          if (permission.id == pick.id) {
            return _PermissionDetail(
              key: ValueKey('activity-detail-permission-${permission.id}'),
              permission: permission,
              controller: controller,
              onOpenConversation: () => _openChat(
                permission.sessionID,
                landOnRequestID: permission.id,
              ),
            );
          }
        }
      case _PickKind.question:
        final question = controller.questions[pick.id];
        if (question != null) {
          return _QuestionDetail(
            key: ValueKey('activity-detail-question-${question.id}'),
            question: question,
            controller: controller,
            onOpenConversation: () =>
                _openChat(question.sessionID, landOnRequestID: question.id),
          );
        }
      case _PickKind.form:
        final form = controller.forms[pick.id];
        if (form != null && controller.capabilities.forms) {
          return _FormDetail(
            key: ValueKey('activity-detail-form-${form.id}'),
            form: form,
            controller: controller,
          );
        }
    }
    return null;
  }
}
