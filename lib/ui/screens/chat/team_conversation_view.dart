part of '../chat_screen.dart';

// The AI Team as a conversation (docs/design/team-conversation-2026-09-26.md):
// a task is a conversation, its workers are its sub-agents. The team's own
// data is assembled into the chat's kit parts (option A) and follows the
// transcript turn model (STATE-16, KIT-41): your prompt is the task, the
// lead's reply is written by the app from real team events
// (`teamLeadLines`), the task's steps fold under one KitWorkLine, each
// worker or reviewer is a sub-agent line (KitToolRow.agent) that opens its
// real OpenCode session in watching mode, gates are the request cards, and
// the turn has one control row (its footer) once it has ended. Around the
// turn: the one "Now" line and the agent strip (KitAgentStrip) under the
// header, the merge section after the turn, and the composer, whose words
// go through the team's own message control.

/// A task just given to the team, shown before the team lists its run: the
/// person's words as the prompt, and the conversation binds to the run once
/// one carries the created work item ([workId]) or the planning request
/// ([record] with [title] as its objective).
@immutable
class TeamPendingTask {
  const TeamPendingTask({
    required this.title,
    required this.sentAt,
    this.details,
    this.workId,
    this.record,
  });

  final String title;
  final String? details;
  final DateTime sentAt;

  /// The work item the direct path created (TEAM-306), when known.
  final String? workId;

  /// The planner message (Start-a-run), when the planner got the task.
  final MutationRecord? record;
}

/// The chat page for one AI Team task.
class TeamConversationScreen extends StatefulWidget {
  const TeamConversationScreen({
    super.key,
    required this.team,
    this.runId,
    this.pending,
    this.now,
    this.onOpenAgent,
  }) : assert(runId != null || pending != null);

  final OrchestrationController team;

  /// The task (run) this conversation is; null while [pending] waits for it.
  final String? runId;
  final TeamPendingTask? pending;

  /// Clock for elapsed times; tests pin it.
  final DateTime Function()? now;

  /// Opens a worker's own conversation; [openTeamAgentConversation] when
  /// null. Tests inject their own.
  final Future<void> Function(BuildContext context, OrchestrationAgent agent)?
  onOpenAgent;

  /// Unsent words per task, kept while the app runs: leaving the page and
  /// coming back finds the draft where it was (map actionsMissing "draft
  /// kept when leaving"). Nothing is written to storage.
  static final Map<String, String> _drafts = <String, String>{};

  @override
  State<TeamConversationScreen> createState() => _TeamConversationScreenState();
}

class _TeamConversationScreenState extends State<TeamConversationScreen> {
  final _expansion = <String, bool>{};
  final _message = TextEditingController();
  final _focus = FocusNode();

  /// The transcript's scroll: a message just sent is brought into view
  /// (the Now line above can take room and leave it under the fold).
  final _scroll = ScrollController();
  Timer? _tick;
  String? _runId;

  /// The last run and steps seen: a finished task leaves the team's open
  /// list, and its conversation keeps what it last showed.
  OrchestrationRun? _lastRun;
  List<WorkItem> _lastWork = const [];

  /// Message records sent from this page (their receipts show under them).
  final _sentHere = <String>{};
  bool _sending = false;

  /// The team's roles, once loaded: the task's worker is named by the role
  /// the task was given to ("Frontend took the task").
  TeamRolesController? _roles;

  OrchestrationController get _team => widget.team;
  DateTime Function() get _clock => widget.now ?? DateTime.now;

  String get _draftKey =>
      _runId ?? 'pending:${widget.pending?.sentAt.microsecondsSinceEpoch ?? 0}';

  /// setState for this library's extensions (a protected member).
  void _setTeamState(VoidCallback change) => setState(change);

  @override
  void initState() {
    super.initState();
    _runId = widget.runId;
    _message.text = TeamConversationScreen._drafts[_draftKey] ?? '';
    _message.addListener(_keepDraft);
    _team.watchCycles();
    _team.addListener(_bind);
    _bind();
    unawaited(_loadRoles());
    // Elapsed times ("waited 3 min") move without a new answer.
    _tick = Timer.periodic(const Duration(seconds: 30), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    _team.removeListener(_bind);
    _team.unwatchCycles();
    _message
      ..removeListener(_keepDraft)
      ..dispose();
    _focus.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _keepDraft() {
    final text = _message.text;
    if (text.trim().isEmpty) {
      TeamConversationScreen._drafts.remove(_draftKey);
    } else {
      TeamConversationScreen._drafts[_draftKey] = text;
    }
  }

  /// Finds the run a pending task became.
  void _bind() {
    if (_runId != null) return;
    final pending = widget.pending;
    if (pending == null) return;
    final snapshot = _team.snapshot;
    String? found;
    if (pending.workId case final workId?) {
      for (final item in snapshot.work) {
        if (item.id == workId && item.runId != null) found = item.runId;
      }
    }
    if (found == null && pending.record != null) {
      for (final run in snapshot.runs) {
        if (teamPlanningRunMatches(run, pending.record!, pending.title)) {
          found = run.id;
          break;
        }
      }
    }
    if (found != null && mounted) {
      // The draft follows the task to its run.
      final draft = TeamConversationScreen._drafts.remove(_draftKey);
      setState(() => _runId = found);
      if (draft != null) TeamConversationScreen._drafts[_draftKey] = draft;
    }
  }

  bool _refreshing = false;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    // The task's dispatch stage (P6.3) moves with its own attempt.
    listenable: Listenable.merge([_team, TeamDispatchAttempts.of(_team)]),
    builder: (context, _) {
      final l10n = _chatL10n(context);
      final run = _run();
      final work = run == null ? const <WorkItem>[] : _work(run);
      final snapshot = _team.snapshot;
      DispatchCycle cycleOf(String id) => _team.cycleFor(id);
      final agents = run == null
          ? const <OrchestrationAgent>[]
          : teamConversationAgents(
              agents: snapshot.agents,
              work: work,
              cycleOf: cycleOf,
            );
      final gates = run == null
          ? const <OrchestrationGate>[]
          : teamOpenGates(_team, runId: run.id);
      final lines = run == null
          ? const <TeamLeadLine>[]
          : teamLeadLines(
              run: run,
              work: work,
              cycleOf: cycleOf,
              agents: snapshot.agents,
              gates: gates,
            );
      final now = run == null
          ? null
          : teamNow(
              run: run,
              work: work,
              cycleOf: cycleOf,
              agents: snapshot.agents,
              gates: gates,
              now: _clock(),
            );
      final attempt = TeamDispatchAttempts.of(_team).latest;
      // A worker is starting: its step says so, a session is being made, or
      // (a direct task) the team just observed its worker.
      final starting =
          now?.kind == TeamNowKind.starting ||
          agents.any(
            (agent) =>
                teamWorkerStage(agent) == TeamWorkerStage.preparing &&
                teamSessionState(agent) != AgentState.stopped,
          ) ||
          (run == null &&
              attempt != null &&
              attempt.workId == widget.pending?.workId &&
              attempt.phase == TeamDispatchPhase.workerObserved);
      if (now != null) _rememberWorkerStart(now, agents);
      final title = run?.title ?? widget.pending?.title ?? '';
      final recipient = _recipient(agents);
      final loading =
          _team.phase != OrchestrationPhase.ready || !snapshot.hasData;
      // A task this page knew by id that the team no longer lists, and never
      // listed while the page was open: removed on the team's computer (map
      // statesMissing "task cancelled or removed").
      final gone =
          _runId != null &&
          run == null &&
          snapshot.hasData &&
          _team.phase == OrchestrationPhase.ready;
      final pendingRecord = run == null ? _pendingRecord() : null;
      final working =
          agents.any(
            (agent) => teamSessionState(agent) == AgentState.working,
          ) ||
          switch (now?.kind) {
            TeamNowKind.starting ||
            TeamNowKind.working ||
            TeamNowKind.review => true,
            _ => false,
          };
      return KitScreen(
        key: const ValueKey('team-conversation'),
        width: KitScreenWidth.reading,
        loading: loading,
        loadingLabel: l10n.teamChatLoading,
        topBar: KitTopBar(
          title: title.isEmpty ? l10n.teamChatUntitled : title,
          titleKey: const ValueKey('team-conversation-title'),
          // Never "Paused" while this task's worker starts or works: the
          // task's own state and its agents' sessions say so.
          subtitle: l10n.teamChatSubtitle(
            teamHostPhrase(
              l10n,
              _team,
              working: working,
              startingWorker: starting,
            ),
          ),
          needsYou: gates.length,
          actions: [
            KitAction(
              key: const ValueKey('team-conversation-team-page'),
              label: l10n.teamChatOpenTeam,
              icon: AppIconography.agent,
              onPressed: _openTeamPage,
            ),
          ],
          // The conversation menu's two kinds (P10.2): "Go to" the task's
          // details, "Do" refresh, and Stop task last (it confirms).
          menu: [
            if (run != null)
              KitMenuItem(
                key: const ValueKey('team-conversation-details'),
                label: l10n.teamChatTaskDetails,
                icon: AppIconography.info,
                group: KitMenuGroup(l10n.sessionMenuGoTo),
                onSelected: () => _openDetails(run.id),
              ),
            KitMenuItem(
              key: const ValueKey('team-conversation-refresh'),
              label: l10n.teamUiRefresh,
              icon: AppIconography.sync,
              group: KitMenuGroup(l10n.sessionMenuDo),
              enabled: !_refreshing,
              onSelected: () => unawaited(_refresh()),
            ),
            if (run != null) ...[
              // Only while the task runs and this host can stop it.
              if (_canStop(run))
                KitMenuItem(
                  key: const ValueKey('team-conversation-stop'),
                  label: l10n.teamChatStopTask,
                  icon: AppIconography.stop,
                  destructive: true,
                  onSelected: () => unawaited(_stop(run)),
                ),
            ],
          ],
          menuKey: const ValueKey('team-conversation-menu'),
        ),
        header: [
          if (!gone)
            _TeamNowLine(
              team: _team,
              clock: _clock,
              startingWorker: starting,
              input: (connected) => _nowInput(
                run: run,
                work: work,
                gates: gates,
                pendingRecord: pendingRecord,
                connected: connected,
              ),
              wayOut: (action) => _wayOut(
                action,
                run: run,
                agents: agents,
                pendingRecord: pendingRecord,
              ),
              // Quiet for an hour or more: the notice under the worker
              // line says since when and offers its ways out.
              quiet: _noProgress(now, _clock()),
            ),
        ],
        body: gone
            ? KitStateView(
                key: const ValueKey('team-conversation-gone'),
                icon: AppIconography.agent,
                title: l10n.teamChatGoneTitle,
                body: l10n.teamChatGoneBody,
                primary: KitAction(
                  key: const ValueKey('team-conversation-gone-team-page'),
                  label: l10n.teamChatGoneOpenTeam,
                  icon: AppIconography.agent,
                  onPressed: _openTeamPage,
                ),
              )
            : KitComposer.layer(
                body: _transcript(
                  context,
                  run: run,
                  work: work,
                  agents: agents,
                  gates: gates,
                  lines: lines,
                  title: title,
                  pendingRecord: pendingRecord,
                  now: now,
                ),
                composer: _composer(
                  context,
                  recipient,
                  agents,
                  run: run,
                  work: work,
                ),
              ),
      );
    },
  );
}
