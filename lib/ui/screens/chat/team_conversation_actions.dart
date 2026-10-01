part of '../chat_screen.dart';

// What the AI Team conversation does and draws besides its frame: the run
// and its work, sending to the lead, stopping and refreshing, the "Now"
// line's input, the way out, the transcript and the composer.

extension _TeamConversationActions on _TeamConversationScreenState {
  Future<void> _loadRoles() async {
    try {
      final roles = await loadTeamRoles(_team.profileId);
      if (mounted) _setTeamState(() => _roles = roles);
    } catch (_) {
      // Without roles the worker keeps its plain name.
    }
  }

  /// The role name of this task's worker, or null when it is unknown.
  String? _roleName(
    AppLocalizations l10n,
    OrchestrationRun? run,
    List<WorkItem> work,
  ) {
    final roles = _roles;
    if (roles == null) return null;
    String? id;
    if (run != null) {
      id = roles.roleOfRun(run, _team);
    } else if (widget.pending?.workId case final workId?) {
      id = roles.roleOfTask(workId);
    }
    final role = id == null ? null : roles.byId(id);
    return role == null ? null : teamRoleName(l10n, role);
  }

  OrchestrationRun? _run() {
    final id = _runId;
    if (id == null) return null;
    for (final run in _team.snapshot.runs) {
      if (run.id == id) return _lastRun = run;
    }
    return _lastRun;
  }

  List<WorkItem> _work(OrchestrationRun run) {
    final items = [
      for (final item in _team.snapshot.work)
        if (item.runId == run.id) item,
    ];
    if (items.isNotEmpty) _lastWork = items;
    return items.isEmpty ? _lastWork : items;
  }

  /// The planner's record for a pending task, as it stands now (a refusal
  /// arrives after the page opened).
  MutationRecord? _pendingRecord() {
    final record = widget.pending?.record;
    if (record == null) return null;
    for (final latest in _team.mutations) {
      if (latest.key == record.key) return latest;
    }
    return record;
  }

  /// Who the composer's words go to: the worker on the task, else the
  /// planner when it is on. Never a worker's OpenCode session directly:
  /// the team's message control delivers it.
  OrchestrationAgent? _recipient(List<OrchestrationAgent> agents) {
    for (final agent in agents) {
      if (teamAgentRole(agent) == TeamAgentRole.worker) return agent;
    }
    if (agents.isNotEmpty) return agents.first;
    final planner = teamPlannerAgent(_team.snapshot.agents);
    if (planner != null && !teamPlannerIsOff(planner)) return planner;
    return null;
  }

  Future<void> _send(OrchestrationAgent recipient) async {
    final text = _message.text.trim();
    if (text.isEmpty || _sending) return;
    _setTeamState(() => _sending = true);
    try {
      final record = await _team.messageAgent(recipient.id, text);
      if (!mounted) return;
      _sentHere.add(record.key);
      _message.clear();
      _showLatest();
    } finally {
      if (mounted) _setTeamState(() => _sending = false);
    }
  }

  /// Scrolls the transcript to its end once the sent message is laid out.
  void _showLatest() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scroll.hasClients) return;
      final position = _scroll.position;
      if (KitMotion.reduced(context)) {
        position.jumpTo(position.maxScrollExtent);
      } else {
        unawaited(
          position.animateTo(
            position.maxScrollExtent,
            duration: KitMotion.standard,
            curve: KitMotion.emphasized,
          ),
        );
      }
    });
  }

  Future<void> _openAgent(OrchestrationAgent agent) {
    final open = widget.onOpenAgent;
    if (open != null) return open(context, agent);
    return openTeamAgentConversation(context, agent, team: _team);
  }

  /// The team's page ([openTeamPage], the one AI Team page), over this
  /// conversation. Its row for this same task comes back here rather than
  /// stacking a second copy of the page; another task opens its own
  /// conversation. A conversation whose team is not the connected server's
  /// (none in the app today) opens that team's home directly.
  void _openTeamPage() {
    final here = ModalRoute.of(context);
    void openRun(OrchestrationRun run) {
      if (!mounted) return;
      if (run.id == _runId && here != null) {
        Navigator.of(context).popUntil((route) => route == here);
        return;
      }
      unawaited(TeamConversation.open(context, _team, runId: run.id));
    }

    ConnectionController? connection;
    try {
      connection = ProviderScope.containerOf(
        context,
        listen: false,
      ).read(connProvider);
    } catch (_) {
      connection = null;
    }
    if (connection != null && identical(connection.orchestration, _team)) {
      unawaited(
        openTeamPage(context, connection, now: widget.now, onOpenRun: openRun),
      );
      return;
    }
    unawaited(
      Navigator.of(context).push(
        KitPageRoute<void>(
          builder: (_) => TeamHomeScreen(
            controller: _team,
            now: widget.now,
            onOpenRun: openRun,
          ),
        ),
      ),
    );
  }

  /// A task the host can still stop: not finished, failed or cancelled,
  /// on a host whose team takes the cancel control.
  bool _canStop(OrchestrationRun run) =>
      _team.capabilities.controlCancelRun &&
      switch (run.state) {
        RunState.completed || RunState.cancelled || RunState.failed => false,
        _ => true,
      };

  /// Stop task: the confirm sheet names the task and what happens to its
  /// running workers; backing out sends nothing. The receipt shows in the
  /// conversation (Sent, then Confirmed or the host's refusal).
  Future<void> _stop(OrchestrationRun run) async {
    final l10n = _chatL10n(context);
    final ok = await showKitConfirm(
      context,
      title: l10n.teamChatStopConfirmTitle,
      body: l10n.teamChatStopConfirmBody(
        run.title.trim().isEmpty ? l10n.teamChatUntitled : run.title.trim(),
      ),
      confirmLabel: l10n.teamChatStopTask,
      cancelLabel: l10n.teamChatStopKeepRunning,
      kind: KitConfirmKind.stop,
      sheetKey: const ValueKey('team-conversation-stop-confirm'),
      confirmKey: const ValueKey('team-conversation-stop-confirm-action'),
    );
    if (!ok || !mounted) return;
    await _team.cancelRun(run.id);
  }

  /// Task details (P3.5): what the retired run page showed that the
  /// conversation does not — the stages, the steps as a graph, the
  /// agents' pages, the numbers and the technical fold.
  void _openDetails(String runId) =>
      unawaited(showTeamTaskDetails(context, _team, runId, now: widget.now));

  /// The page's Refresh (a computer has no pull gesture): fetches the team
  /// again, or retries a team that did not answer.
  Future<void> _refresh() async {
    if (_refreshing) return;
    _setTeamState(() => _refreshing = true);
    try {
      if (_team.phase == OrchestrationPhase.failed) {
        await _team.retry();
      } else {
        await _team.refresh();
      }
    } finally {
      if (mounted) _setTeamState(() => _refreshing = false);
    }
  }

  /// Nudge or restart the stalled task's worker; restart asks first. The
  /// receipt shows under the stall's notice.
  Future<void> _control(
    OrchestrationAgent agent,
    AgentControlAction action,
  ) async {
    if (action == AgentControlAction.restart) {
      final l10n = _chatL10n(context);
      final ok = await showKitConfirm(
        context,
        title: l10n.teamUiControlRestartConfirmTitle(
          _teamAgentTitle(l10n, agent),
        ),
        body: l10n.teamUiControlRestartConfirmBody,
        confirmLabel: l10n.teamAgentScreenRestart(_teamAgentName(l10n, agent)),
        icon: AppIconography.restart,
        sheetKey: const ValueKey('team-conversation-restart-confirm'),
        confirmKey: const ValueKey('team-conversation-restart-confirm-action'),
      );
      if (!ok || !mounted) return;
    }
    await _team.controlAgent(agent.id, action);
  }

  /// Where the task's one turn stands, from the task's own state.
  KitTurnPhase _phase(
    OrchestrationRun? run,
    List<OrchestrationGate> gates,
    MutationRecord? pendingRecord,
  ) {
    if (run == null) {
      return pendingRecord?.status == MutationStatus.rejected
          ? KitTurnPhase.failed
          : KitTurnPhase.starting;
    }
    return switch (run.state) {
      RunState.completed => KitTurnPhase.finished,
      RunState.failed => KitTurnPhase.failed,
      RunState.cancelled => KitTurnPhase.stopped,
      _ when gates.isNotEmpty => KitTurnPhase.waitingForYou,
      _ => KitTurnPhase.running,
    };
  }

  /// The moment a worker begins the task, with the session's creation, is
  /// one measured start: kept per profile as "took 42 s last time".
  void _rememberWorkerStart(TeamNow now, List<OrchestrationAgent> agents) {
    if (now.kind != TeamNowKind.working) return;
    for (final agent in agents) {
      final took = teamWorkerStartMeasured(
        sessionStartedAt: agent.sessionStartedAt,
        began: now.since,
      );
      if (took != null && teamSessionState(agent) == AgentState.working) {
        unawaited(_team.workerStarts.record(_team.profileId, took));
        return;
      }
    }
  }

  /// The facts for the task's one Now line (slice-P5.1), or null when the
  /// line has nothing to add: a refused task says so in its turn, with Try
  /// again. [connected] is false once the team has not answered for 8 s.
  TeamNowInput? _nowInput({
    required OrchestrationRun? run,
    required List<WorkItem> work,
    required List<OrchestrationGate> gates,
    required MutationRecord? pendingRecord,
    required bool connected,
  }) {
    final profile = _team.profileId;
    final every = teamCheckInterval(_team);
    // How long the next stage takes, only where the app knows it: a
    // worker's start is this phone's own last measured start (nothing when
    // none was measured), and a wait for a worker on the team inside the
    // app is its own check interval.
    TeamNowInput timed(TeamNowInput input, {TeamWorkerStage? stage}) {
      if (input.activity == TeamNowActivity.startingWorker) {
        return input.withWorkerStart(
          stage: stage,
          lastStart: _team.workerStarts.read(_team.profileId),
        );
      }
      if (input.activity != TeamNowActivity.waitingForWorker) return input;
      return input.withWorkerStart(typicalUpperBound: every);
    }

    if (run != null) {
      return timed(
        TeamNowInput.forRun(
          activityKey: '$profile:${run.id}',
          run: run,
          work: work,
          cycleOf: _team.cycleFor,
          agents: _team.snapshot.agents,
          gates: gates,
          connected: connected,
          canCancel: _canStop(run),
          now: _clock(),
        ),
      );
    }
    final pending = widget.pending;
    if (pending == null) return null;
    final key = '$profile:pending:${pendingRecord?.key ?? pending.workId}';
    if (!connected) {
      return TeamNowInput(
        activityKey: key,
        activity: TeamNowActivity.unavailable,
        next: TeamNowNext.checkActivity,
        reason: TeamNowReason.connectionUnavailable,
      );
    }
    if (pendingRecord != null) {
      if (pendingRecord.status == MutationStatus.rejected) return null;
      final request = teamPlanningRequests(
        mutations: [pendingRecord],
        runs: const [],
        dismissed: const {},
        now: _clock(),
      ).firstOrNull;
      if (request != null) {
        return TeamNowInput.forPlanning(activityKey: key, request: request);
      }
    }
    // A direct task: its stage from the attempt that made it (P6.3), never
    // more than the host confirmed. Without that attempt (another device,
    // an older page) the task was made and given to the worker pool.
    final attempt = TeamDispatchAttempts.of(_team).latest;
    final stage = attempt != null && attempt.workId == pending.workId
        ? attempt.phase
        : null;
    TeamNowInput line(
      TeamNowActivity activity,
      TeamNowNext next,
      TeamNowReason reason,
    ) => TeamNowInput(
      activityKey: '$key:${stage?.name}',
      activity: activity,
      next: next,
      reason: reason,
      since: pending.sentAt,
    );
    switch (stage) {
      case TeamDispatchPhase.creating || TeamDispatchPhase.sending:
        // The assignment is on its way: the turn says "starting"; the line
        // waits for the host's answer instead of guessing it.
        return null;
      case TeamDispatchPhase.workerObserved:
        return timed(
          line(
            TeamNowActivity.startingWorker,
            TeamNowNext.work,
            TeamNowReason.workerStarting,
          ),
          stage: teamWorkerStage(
            _team.snapshot.agents
                .where((agent) => agent.currentWorkId == pending.workId)
                .firstOrNull,
          ),
        );
      case TeamDispatchPhase.assignRefused:
        return line(
          TeamNowActivity.refused,
          TeamNowNext.checkActivity,
          TeamNowReason.requestRefused,
        );
      case TeamDispatchPhase.dispatchUnconfirmed:
        return line(
          TeamNowActivity.unconfirmed,
          TeamNowNext.checkActivity,
          TeamNowReason.confirmationMissing,
        );
      case TeamDispatchPhase.unknown:
        return line(
          TeamNowActivity.unavailable,
          TeamNowNext.checkActivity,
          TeamNowReason.connectionUnavailable,
        );
      default:
        break;
    }
    return timed(
      TeamNowInput(
        activityKey: key,
        activity: TeamNowActivity.waitingForWorker,
        next: TeamNowNext.worker,
        reason: TeamNowReason.noWorkerReported,
        since: pending.sentAt,
      ),
    );
  }

  /// This page's way out for a Now line suggestion, naming its target.
  KitAction? _wayOut(
    TeamNowAction action, {
    required OrchestrationRun? run,
    required List<OrchestrationAgent> agents,
    required MutationRecord? pendingRecord,
  }) {
    final l10n = _chatL10n(context);
    switch (action) {
      case TeamNowAction.refresh:
        return KitAction(
          key: const ValueKey('team-conversation-now-refresh'),
          label: l10n.teamUiRefresh,
          working: _refreshing,
          onPressed: _refreshing ? null : () => unawaited(_refresh()),
        );
      case TeamNowAction.openActivity:
        if (run == null) {
          final planner = pendingRecord?.targetId;
          if (planner == null) return null;
          return KitAction(
            key: const ValueKey('team-conversation-now-watch'),
            label: l10n.teamNowWatchPlanner,
            onPressed: () => unawaited(
              openTeamAgentConversationById(context, _team, planner),
            ),
          );
        }
        OrchestrationAgent? worker;
        for (final agent in agents) {
          if (teamAgentRole(agent) == TeamAgentRole.worker) {
            worker = agent;
            break;
          }
        }
        worker ??= agents.firstOrNull;
        if (worker == null) {
          return KitAction(
            key: const ValueKey('team-conversation-now-details'),
            label: l10n.teamChatTaskDetails,
            onPressed: () => _openDetails(run.id),
          );
        }
        final agent = worker;
        return KitAction(
          key: const ValueKey('team-conversation-now-watch'),
          label: l10n.teamNowWatchAgent(_teamAgentName(l10n, agent)),
          onPressed: () => unawaited(_openAgent(agent)),
        );
      case TeamNowAction.dismissRequest:
        final record = pendingRecord;
        if (record == null) return null;
        return KitAction(
          key: const ValueKey('team-conversation-now-dismiss'),
          label: l10n.teamNowDismissRequest,
          onPressed: () async {
            await _team.dismissPlanning(record.key);
            if (mounted) await Navigator.of(context).maybePop();
          },
        );
      case TeamNowAction.cancelRun:
        if (run == null || !_canStop(run)) return null;
        return KitAction(
          key: const ValueKey('team-conversation-now-stop'),
          label: l10n.teamChatStopTask,
          destructive: true,
          onPressed: () => unawaited(_stop(run)),
        );
      case TeamNowAction.answer:
        return null;
    }
  }

  /// The task's one turn, then what the person sent and the merge section.
  Widget _transcript(
    BuildContext context, {
    required OrchestrationRun? run,
    required List<WorkItem> work,
    required List<OrchestrationAgent> agents,
    required List<OrchestrationGate> gates,
    required List<TeamLeadLine> lines,
    required String title,
    required MutationRecord? pendingRecord,
    required TeamNow? now,
  }) {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final clock = _clock();
    // The prompt says the task once; a step or a worker's line that is
    // the task itself refers back to it instead of repeating its words.
    final soleStep =
        work.length == 1 && _sameTaskText(work.single.title, title);
    final quiet = _noProgress(now, clock);
    OrchestrationAgent? stalledAgent;
    if (quiet != null) {
      for (final agent in [...agents, ..._team.snapshot.agents]) {
        if (agent.id == now?.agentId) {
          stalledAgent = agent;
          break;
        }
      }
    }
    final prompt = [
      title,
      ?_withoutRolePreamble(
        run == null ? widget.pending?.details : _runDetails(run, work),
      ),
    ].where((t) => t.trim().isNotEmpty).join('\n\n');
    final phase = _phase(run, gates, pendingRecord);
    final roleName = _roleName(l10n, run, work);
    final leadRows = _TeamLeadReply.rowsFor(
      context,
      lines: lines,
      pending: run == null ? widget.pending : null,
      taskTitle: title,
      roleName: roleName,
    );
    final sent = [
      for (final record in _team.mutations)
        if (_sentHere.contains(record.key) ||
            (record.kind == MutationKind.message &&
                agents.any((a) => a.id == record.targetId) &&
                (run?.startedAt == null ||
                    !record.createdAt.isBefore(run!.startedAt!))))
          record,
    ];
    final stop = run == null
        ? null
        : _team.latestMutation(kind: MutationKind.cancelRun, targetId: run.id);
    // The end padding is read under the composer layer, which adds its
    // height to the bottom inset.
    return Builder(
      builder: (context) => ListView(
        key: const ValueKey('team-conversation-list'),
        controller: _scroll,
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space3,
          tokens.gutter,
          KitScreen.endPadding(context),
        ),
        children: [
          KitTurn(
            turnKey: const ValueKey('team-conversation-turn'),
            prompt: KitMessage.prompt(
              key: const ValueKey('team-conversation-prompt'),
              body: KitMarkdown(prompt, selectable: false),
            ),
            phase: phase,
            since: run?.startedAt ?? widget.pending?.sentAt,
            latest: sent.isEmpty,
            footer: KitTurnFooter(copyText: () => leadRows.join('\n')),
            footerKey: const ValueKey('team-conversation-footer'),
            blocks: [
              _TeamLeadReply(rows: leadRows, expansion: _expansion),
              if (pendingRecord case final record?
                  when record.status == MutationStatus.rejected)
                KitNotice(
                  key: const ValueKey('team-conversation-refused'),
                  tone: AppStatusTone.failure,
                  icon: AppIconography.error,
                  title: l10n.teamChatRefusedTitle,
                  message: teamReceiptLine(l10n, record),
                  actions: [
                    if (record.canRetry)
                      KitAction(
                        key: const ValueKey('team-conversation-refused-retry'),
                        label: l10n.teamChatRefusedRetry,
                        icon: AppIconography.retry,
                        onPressed: () =>
                            unawaited(_team.retryMutation(record.key)),
                      ),
                  ],
                ),
              // One step that is the task itself is not listed again (its
              // Work sheet is a row of Task details).
              if (work.isNotEmpty && !soleStep)
                _TeamSteps(
                  work: work,
                  expansion: _expansion,
                  onOpen: (id) => unawaited(
                    showWorkSheet(context, _team, id, now: widget.now),
                  ),
                ),
              for (final agent in agents)
                KitToolRow.agent(
                  rowKey: ValueKey('team-conversation-agent-${agent.id}'),
                  title: _teamAgentTitle(l10n, agent, agents, roleName),
                  status: _teamAgentToolStatus(agent),
                  liveMark: false,
                  task: [
                    for (final item in work)
                      if (item.id == agent.currentWorkId &&
                          !_sameTaskText(item.title, title))
                        item.title,
                  ].firstOrNull,
                  openLabel: l10n.teamOpenConversation,
                  onOpen: () => unawaited(_openAgent(agent)),
                ),
              if (quiet != null)
                _TeamNoProgress(
                  team: _team,
                  agent: stalledAgent,
                  quiet: quiet,
                  since: now!.quietSince!,
                  runId: run?.id,
                  agentName: stalledAgent == null
                      ? null
                      : _teamAgentName(l10n, stalledAgent),
                  onControl: _control,
                  today: clock,
                ),
              // An Inbox row or a notification for one gate lands on its
              // card (P4.2a).
              for (final gate in gates)
                KitArrival(
                  id: chatRequestArrivalId(gate.id),
                  child: TeamNeedsYouCard(
                    keyPrefix: 'team-conversation-gate-${gate.id}',
                    controller: _team,
                    gate: gate,
                    title: teamGateWho(l10n, _team.snapshot, gate),
                    onOpen: () => unawaited(
                      showGateSheet(context, _team, gate.id, now: widget.now),
                    ),
                  ),
                ),
              if (stop != null)
                _teamReceipt(
                  context,
                  stop,
                  key: const ValueKey('team-conversation-stop-receipt'),
                  control: l10n.teamChatStopTask,
                  onRetry: () => _team.retryMutation(stop.key),
                ),
            ],
          ),
          for (final record in sent)
            KitTurn(
              key: ValueKey('team-conversation-message-${record.key}'),
              prompt: KitMessage.prompt(
                body: KitMarkdown(record.request.text ?? '', selectable: false),
              ),
              // The message's own receipt; the team's answer arrives in the
              // task's turn above (the lead's lines and the workers' state).
              phase: KitTurnPhase.finished,
              latest: record == sent.last,
              blocks: [
                _teamReceipt(
                  context,
                  record,
                  key: ValueKey('team-conversation-sent-${record.key}'),
                  onRetry: () => _team.retryMutation(record.key),
                ),
              ],
            ),
          if (run != null) ...[
            // Merged: the celebration, once per task (it is remembered).
            TeamMergedCelebration(
              profileId: _team.profileId,
              runId: run.id,
              merged: run.state == RunState.completed && run.merged,
            ),
            Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space4),
              child: TeamMergeSection(
                controller: _team,
                run: run,
                now: widget.now,
              ),
            ),
          ],
        ],
      ),
    );
  }

  /// "Message the team…": the words go through the team's own message
  /// control to the worker on the task (or the planner), never typed into a
  /// worker's OpenCode session. The note says who gets them before typing.
  KitComposer _composer(
    BuildContext context,
    OrchestrationAgent? to,
    List<OrchestrationAgent> agents, {
    OrchestrationRun? run,
    List<WorkItem> work = const [],
  }) {
    final l10n = _chatL10n(context);
    final roleName = _roleName(l10n, run, work);
    final canMessage = _team.capabilities.controlMessage;
    final readOnly = !canMessage
        ? l10n.teamChatComposerCannot
        : to == null
        ? l10n.teamChatComposerNobody
        : null;
    return KitComposer(
      composerKey: const ValueKey('team-conversation-composer'),
      fieldKey: const ValueKey('team-conversation-field'),
      sendKey: const ValueKey('team-conversation-send'),
      controller: _message,
      focusNode: _focus,
      hint: l10n.teamChatComposerHint,
      fieldLabel: l10n.teamChatComposerHint,
      readOnlyReason: readOnly,
      note: to == null
          ? null
          : l10n.teamChatComposerGoesTo(
              _teamAgentTitle(l10n, to, agents, roleName),
            ),
      sending: _sending,
      canSendWhileBusy: true,
      onSend: () {
        if (to != null) unawaited(_send(to));
      },
    );
  }

  /// The task's own text beyond its title: a one-step task's description.
  String? _runDetails(OrchestrationRun run, List<WorkItem> work) {
    final raw = run.raw['description'];
    if (raw is String && raw.trim().isNotEmpty) return raw.trim();
    if (work.length == 1) {
      final text = work.single.raw['description'];
      if (text is String && text.trim().isNotEmpty) return text.trim();
    }
    return null;
  }
}
