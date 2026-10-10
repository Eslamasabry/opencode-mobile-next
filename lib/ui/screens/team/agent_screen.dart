/// The Agent detail (02-ux-flows-and-screens §5.2): what a fleet row
/// opens. A short status page whose primary is the worker's own
/// conversation; the agent's work itself is shown in one place only, the
/// chat (docs/design/team-conversation-2026-09-26.md).
///
/// Built from kit parts only (screen-team-1, STANDARDS §4), top to bottom
/// in one list ordered by urgency:
///
/// 1. The top bar ([KitTopBar]) names the agent by role and name ("Worker ·
///    fox") with what it works on and what holds it up as the subtitle
///    ("On “Sync engine” · nothing blocking it"). Refresh is its one icon.
/// 2. A question waiting on the person, as the pointing [KitNeedsYou.row]
///    that opens the Gate sheet, when there is one.
/// 3. What went wrong, when something did: the worker didn't start, it
///    stopped or crashed (with "Start fox again"), its context is nearly
///    full ("Recycling soon").
/// 4. The status panel: the state from the agent's **session** with its
///    mark and word, context use and session age; the newest step in plain
///    words ("Ran the tests") and when it was last active; the model in
///    plain words; "Not working on anything" when it has no task. The
///    team's usage is not this agent's and stays on the team's pages.
/// 5. The newest control receipt.
/// 6. Technical details, one [KitDetailsFold], last and collapsed: the raw
///    last command among them.
///
/// The pinned action ([KitActionBlock]) is **Open conversation**: the
/// worker's session in watching mode, or, when the connected server has no
/// conversation of it, the same watching page drawn from the team's live
/// output; the one-line note under it says so. Messaging the worker
/// happens in that conversation's composer (slice-P3.6). The fallbacks
/// (the team restarts, wakes and routes work itself) sit in the top bar's
/// overflow: Pause fox, Nudge fox, Restart fox and Stop fox (last, in the
/// destructive tone). Pause is undone with [showKitUndo]; Stop and Restart
/// are confirmed with [showKitConfirm] (Stop in the stop tone, Restart
/// neutral). The controls exist only when the host takes them (TEAM-204);
/// when it takes none, the page says so and offers the host guide instead
/// of hiding them.
///
/// Removed here (owner verdicts 2026-09-26): the Technical details sheet
/// (merged into the fold) and Reassign work (the dispatcher routes ready
/// work; the board's "Start now" is the manual fallback).
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';

import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/orchestration.dart';
import '../../../state/team_conversation.dart' show teamSessionState;
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/team_controls.dart' show teamControlReceipt;
import '../../widgets/team_host_form.dart' show showTeamHostGuideSheet;
import '../../widgets/team_now.dart';
import '../../widgets/team_vocabulary.dart';
import '../../widgets/tool_card.dart' show toolLabel;
import '../team_conversation/team_conversation.dart';
import 'gate_sheet.dart' show showGateSheet;
import 'team_states.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// A team agent's step in plain words ("Ran the tests"); the raw command
/// waits under Technical details. Any other tool is named as its step row
/// names it ([toolLabel]), never by its raw id.
@visibleForTesting
String agentStepWords(AppLocalizations l10n, AgentStep step) =>
    switch (step.kind) {
      AgentStepKind.command => l10n.teamAgentStepCommand,
      AgentStepKind.test => l10n.teamAgentStepTest,
      AgentStepKind.read => l10n.teamAgentStepRead,
      AgentStepKind.edit => l10n.teamAgentStepEdit,
      AgentStepKind.search => l10n.teamAgentStepSearch,
      AgentStepKind.other =>
        step.tool.trim().isEmpty
            ? l10n.teamAgentStepCommand
            : l10n.teamAgentStepTool(toolLabel(step.tool, l10n: l10n)),
    };

class AgentScreen extends StatefulWidget {
  const AgentScreen({
    super.key,
    required this.controller,
    required this.agentId,
    this.now,
    this.sessionTitle,
  });

  /// The agent's own conversation title, when opened from that page; shown
  /// under Technical details.
  final String? sessionTitle;

  final OrchestrationController controller;
  final String agentId;

  /// Clock for the session age; tests pin it.
  final DateTime Function()? now;

  @override
  State<AgentScreen> createState() => _AgentScreenState();
}

class _AgentScreenState extends State<AgentScreen> {
  late AgentOutputTail _tail;
  bool _refreshing = false;
  bool _busy = false;

  /// Where "Open conversation" leads, looked up once per agent session
  /// (its folder and start); null while looking.
  TeamAgentConversationLookup? _lookup;
  String? _lookupFor;

  DateTime get _now => (widget.now ?? DateTime.now)();
  OrchestrationController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    _tail = _controller.watchAgentOutput(widget.agentId)..addListener(_changed);
    _controller.addListener(_rebind);
  }

  @override
  void dispose() {
    _controller.removeListener(_rebind);
    _tail.removeListener(_changed);
    _controller.unwatchAgentOutput(widget.agentId);
    super.dispose();
  }

  void _changed() {
    if (!mounted) return;
    // The live watching page opening on top notifies the shared tail from
    // its own initState, mid-build: redraw after that frame instead.
    if (SchedulerBinding.instance.schedulerPhase ==
        SchedulerPhase.persistentCallbacks) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) setState(() {});
      });
      return;
    }
    setState(() {});
  }

  /// The screen may open before the snapshot names the agent's session;
  /// once it does, the tail is opened for real.
  void _rebind() {
    if (_tail.sessionId != null || _tail.watching) return;
    final agent = _agent;
    if (agent?.sessionId == null) return;
    _tail.removeListener(_changed);
    _controller.unwatchAgentOutput(widget.agentId);
    _tail = _controller.watchAgentOutput(widget.agentId)..addListener(_changed);
  }

  /// Looks for the agent's OpenCode session when the agent's folder or
  /// session start changed since the last look.
  void _lookUp(OrchestrationAgent agent) {
    final key = '${agent.workDir}|${agent.sessionStartedAt}';
    if (key == _lookupFor) return;
    _lookupFor = key;
    _lookup = null;
    unawaited(() async {
      final found = await lookupTeamAgentConversation(context, agent);
      if (!mounted || _lookupFor != key) return;
      setState(() => _lookup = found);
    }());
  }

  Future<void> _refresh() async {
    if (_refreshing) return;
    setState(() => _refreshing = true);
    try {
      if (_controller.phase == OrchestrationPhase.failed) {
        await _controller.retry();
      } else {
        await _controller.refresh();
      }
    } finally {
      // Look for its conversation again too: a worker still starting has
      // one by now.
      _lookupFor = null;
      if (mounted) setState(() => _refreshing = false);
    }
  }

  OrchestrationAgent? get _agent {
    for (final agent in _controller.snapshot.agents) {
      if (agent.id == widget.agentId || agent.sessionId == widget.agentId) {
        return agent;
      }
    }
    return null;
  }

  WorkItem? _workOf(OrchestrationAgent agent) {
    for (final item in _controller.snapshot.work) {
      if (item.id == agent.currentWorkId) return item;
    }
    return null;
  }

  OrchestrationGate? _gateOf(OrchestrationAgent agent, WorkItem? work) {
    for (final g in _controller.snapshot.gates) {
      if (g.kind == GateKind.reviewReady) continue;
      if (g.agentId == agent.id ||
          (agent.sessionId != null && g.agentId == agent.sessionId) ||
          (work != null && g.workId == work.id)) {
        return g;
      }
    }
    return null;
  }

  /// A worker that is not running while a task waits for one: it should
  /// have started (and the home's Now line says the same).
  bool _didNotStart(OrchestrationAgent agent) {
    if (teamAgentIsLive(agent)) return false;
    if (teamAgentRole(agent) != TeamAgentRole.worker) return false;
    final snapshot = _controller.snapshot;
    return teamVisibleRuns(snapshot.runs).any(
      (run) => teamRunWaitsForWorker(
        run,
        snapshot.work,
        cycleOf: _controller.cycleFor,
      ),
    );
  }

  /// The newest tool call in the agent's live output: what it is doing now
  /// or did last.
  AgentStep? _lastStep() {
    final blocks = parseAgentTranscript(_tail.text);
    for (final block in blocks.reversed) {
      if (block is AgentStepGroup && block.steps.isNotEmpty) {
        return block.steps.last;
      }
    }
    return null;
  }

  /// The raw command of [step], its first line, for Technical details.
  static String? _stepCommand(AgentStep? step) {
    final line = step?.command.trim().split('\n').first.trim();
    return line == null || line.isEmpty ? null : line;
  }

  /// What holds the agent's task up, as the end of the top bar's subtitle:
  /// "nothing blocking it", "blocked", "waiting on 2 other steps". Only a
  /// dependency the host lists as still open counts: one that finished
  /// (or was stopped) holds nothing up, so "Working" and "waiting on…"
  /// never show together for a step whose dependencies are done.
  static String _workHold(
    AppLocalizations l10n,
    WorkItem work,
    List<WorkItem> all,
  ) {
    if (work.isBlocked) return l10n.teamAgentWorkBlockedShort;
    final open = teamOpenDependencies(work, all).length;
    return open > 0
        ? l10n.teamUiRunBlockedByDeps(open)
        : l10n.teamAgentWorkUnblockedShort;
  }

  /// The newest control record for this agent, by any of its ids.
  MutationRecord? _receipt(OrchestrationAgent agent) {
    MutationRecord? best;
    for (final id in {agent.id, ?agent.sessionId}) {
      for (final kind in const [
        MutationKind.controlAgent,
        MutationKind.message,
      ]) {
        final record = _controller.latestMutation(kind: kind, targetId: id);
        if (record != null &&
            (best == null || record.createdAt.isAfter(best.createdAt))) {
          best = record;
        }
      }
    }
    return best;
  }

  // -------------------------------------------------------------------------
  // Actions (TEAM-204)
  // -------------------------------------------------------------------------

  Future<void> _run(Future<MutationRecord> Function() send) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await send();
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _control(String agentId, AgentControlAction action) =>
      _run(() => _controller.controlAgent(agentId, action));

  /// Pause, then "Paused fox · Undo" (DATA-11: undo, not a confirmation).
  Future<void> _pause(OrchestrationAgent agent) async {
    final l10n = _copy(context);
    MutationRecord? record;
    await _run(() async {
      final result = await _controller.controlAgent(
        agent.id,
        AgentControlAction.pause,
      );
      record = result;
      return result;
    });
    // A refused or unconfirmed request has nothing to undo. Keep its
    // receipt visible instead of claiming the agent was paused.
    final status = record?.status;
    if (!mounted ||
        status == null ||
        status == MutationStatus.rejected ||
        status == MutationStatus.unconfirmed) {
      return;
    }
    showKitUndo(
      context,
      message: l10n.teamAgentScreenPaused(agent.name),
      onUndo: () => _control(agent.id, AgentControlAction.resume),
      key: const ValueKey('team-agent-pause-undo'),
      undoKey: const ValueKey('team-agent-pause-undo-action'),
    );
  }

  Future<void> _stop(OrchestrationAgent agent, WorkItem? work) async {
    final l10n = _copy(context);
    final ok = await showKitConfirm(
      context,
      title: l10n.teamUiControlStopConfirmTitle(teamAgentTitle(l10n, agent)),
      body: work == null
          ? l10n.teamUiControlStopConfirmBody
          : l10n.teamAgentScreenStopBody(agent.name, work.title),
      confirmLabel: l10n.teamAgentScreenStop(agent.name),
      kind: KitConfirmKind.stop,
      sheetKey: const ValueKey('team-agent-stop-confirm'),
      confirmKey: const ValueKey('team-agent-stop-confirm-action'),
    );
    if (!ok || !mounted) return;
    await _control(agent.id, AgentControlAction.stop);
  }

  Future<void> _restart(OrchestrationAgent agent) async {
    final l10n = _copy(context);
    final ok = await showKitConfirm(
      context,
      title: l10n.teamUiControlRestartConfirmTitle(teamAgentTitle(l10n, agent)),
      body: l10n.teamUiControlRestartConfirmBody,
      confirmLabel: l10n.teamAgentScreenRestart(agent.name),
      icon: AppIconography.restart,
      sheetKey: const ValueKey('team-agent-restart-confirm'),
      confirmKey: const ValueKey('team-agent-restart-confirm-action'),
    );
    if (!ok || !mounted) return;
    await _control(agent.id, AgentControlAction.restart);
  }

  /// The worker's conversation; Back returns here, so it offers no way
  /// back to this page.
  void _openConversation(OrchestrationAgent agent) => unawaited(
    openTeamAgentConversation(
      context,
      agent,
      team: _controller,
      lookup: _lookup,
      details: false,
    ),
  );

  // -------------------------------------------------------------------------
  // Build
  // -------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _controller,
    builder: (context, _) {
      final l10n = _copy(context);
      final agent = _agent;
      final work = agent == null ? null : _workOf(agent);
      final onRetry = _refreshing ? null : _refresh;
      final state = teamScreenState(
        context,
        controller: _controller,
        keyPrefix: 'team-agent',
        onRetry: onRetry,
      );
      if (agent != null && state == null) _lookUp(agent);
      final miss = _lookup?.miss;
      final ready = state == null && agent != null;
      return KitScreen(
        key: const ValueKey('team-agent'),
        width: KitScreenWidth.reading,
        topBar: KitTopBar(
          title: agent == null ? widget.agentId : teamAgentTitle(l10n, agent),
          // What it works on and what holds it up, said once here.
          subtitle: work == null
              ? null
              : [
                  l10n.teamAgentWorksOn(work.title),
                  _workHold(l10n, work, _controller.snapshot.work),
                ].join(teamUsageSeparator),
          titleKey: const ValueKey('team-agent-title'),
          actions: [
            KitAction(
              key: const ValueKey('team-agent-refresh'),
              label: l10n.teamUiRefresh,
              icon: AppIconography.sync,
              onPressed: onRetry,
            ),
          ],
          menu: [if (ready) ..._controls(context, agent, work)],
          menuKey: const ValueKey('team-agent-more'),
        ),
        status: ready
            ? teamStatusLine(
                context,
                controller: _controller,
                keyPrefix: 'team-agent',
                onRetry: onRetry,
              )
            : null,
        loading: teamScreenLoading(_controller) || _refreshing,
        loadingLabel: l10n.teamUiCardLoading,
        body:
            state ??
            (agent == null
                ? KitStateView(
                    key: const ValueKey('team-agent-missing'),
                    icon: AppIconography.cloudOff,
                    title: l10n.teamUiAgentMissingTitle,
                    body: l10n.teamUiAgentMissingHint,
                    primary: KitAction(
                      label: l10n.teamUiRunBack,
                      onPressed: () => Navigator.of(context).maybePop(),
                    ),
                  )
                : _list(context, agent, work)),
        bottom: ready ? _bottom(context, agent, miss) : null,
      );
    },
  );

  /// The pinned action, and under it, when the conversation opens from the
  /// team's live output, why: no conversation matched on the connected
  /// server.
  Widget _bottom(
    BuildContext context,
    OrchestrationAgent agent,
    TeamAgentConversationMiss? miss,
  ) {
    final actions = _actions(context, agent);
    if (miss == null) return actions;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        actions,
        SizedBox(height: KitTokens.of(context).space2),
        KitText(
          teamAgentConversationMissNote(context, miss),
          key: const ValueKey('team-agent-conversation-miss'),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }

  /// The pinned block: the worker's conversation, where it is also
  /// messaged.
  KitActionBlock _actions(BuildContext context, OrchestrationAgent agent) {
    final l10n = _copy(context);
    return KitActionBlock(
      primary: KitAction(
        key: const ValueKey('team-agent-open-conversation'),
        label: l10n.teamOpenConversation,
        icon: AppIconography.chat,
        onPressed: () => _openConversation(agent),
      ),
    );
  }

  /// The fallbacks, in the top bar's overflow (the team restarts, wakes and
  /// routes work itself): Pause fox, Nudge fox, Restart fox, and Stop fox
  /// last in the destructive tone. Only when the host takes them.
  List<KitMenuItem> _controls(
    BuildContext context,
    OrchestrationAgent agent,
    WorkItem? work,
  ) {
    final l10n = _copy(context);
    if (!_controller.capabilities.controlAgent) return const [];
    final state = teamSessionState(agent);
    final stopped = state == AgentState.stopped || state == AgentState.crashed;
    final name = agent.name;
    const group = 'controls';
    return [
      if (!stopped)
        KitMenuItem(
          key: const ValueKey('team-agent-control-pause'),
          label: l10n.teamAgentScreenPause(name),
          icon: AppIconography.pause,
          group: group,
          enabled: !_busy,
          disabledReason: _busy ? l10n.teamUiReceiptSent : null,
          onSelected: () => unawaited(_pause(agent)),
        ),
      KitMenuItem(
        key: const ValueKey('team-agent-control-nudge'),
        label: l10n.teamAgentScreenNudge(name),
        icon: AppIconography.lightning,
        group: group,
        enabled: !_busy,
        disabledReason: _busy ? l10n.teamUiReceiptSent : null,
        onSelected: () =>
            unawaited(_control(agent.id, AgentControlAction.nudge)),
      ),
      KitMenuItem(
        key: const ValueKey('team-agent-control-restart'),
        label: l10n.teamAgentScreenRestart(name),
        icon: AppIconography.restart,
        group: group,
        enabled: !_busy,
        disabledReason: _busy ? l10n.teamUiReceiptSent : null,
        onSelected: () => unawaited(_restart(agent)),
      ),
      if (!stopped)
        KitMenuItem(
          key: const ValueKey('team-agent-control-stop'),
          label: l10n.teamAgentScreenStop(name),
          icon: AppIconography.stopCircle,
          destructive: true,
          enabled: !_busy,
          disabledReason: _busy ? l10n.teamUiReceiptSent : null,
          onSelected: () => unawaited(_stop(agent, work)),
        ),
    ];
  }

  Widget _list(BuildContext context, OrchestrationAgent agent, WorkItem? work) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final caps = _controller.capabilities;
    final state = teamSessionState(agent);
    final gate = _gateOf(agent, work);
    final receipt = _receipt(agent);
    final percent = agent.contextPercent;
    final inset = EdgeInsetsDirectional.symmetric(
      horizontal: tokens.gutter,
      vertical: tokens.space2,
    );
    Widget pad(Widget child) => Padding(padding: inset, child: child);

    return KitRefresh(
      key: const ValueKey('team-agent-pull'),
      onRefresh: _refresh,
      child: ListView(
        key: const ValueKey('team-agent-list'),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: KitScreen.endPadding(context),
        ),
        children: [
          // 1. What needs the person.
          if (gate != null)
            KitRowGroup(
              margin: _groupMargin(context),
              children: [
                KitNeedsYou.row(
                  key: const ValueKey('team-agent-gate'),
                  title: gate.title,
                  reason: KitNeedsYouReason.decision,
                  ifIgnored: l10n.teamAgentScreenGateIfIgnored(agent.name),
                  onOpen: () => unawaited(
                    showGateSheet(
                      context,
                      _controller,
                      gate.id,
                      now: widget.now,
                    ),
                  ),
                ),
              ],
            ),
          // 2. What went wrong.
          if (_didNotStart(agent))
            pad(
              KitNotice(
                key: const ValueKey('team-agent-did-not-start'),
                icon: AppIconography.warning,
                title: l10n.teamAgentDidNotStartTitle,
                message: l10n.teamAgentDidNotStartBody,
                actions: [
                  teamUnstickAction(
                    context,
                    _controller,
                    keyPrefix: 'team-agent-did-not-start',
                    wake: [agent],
                    wakeLabel: l10n.teamAgentStartIt,
                  ),
                ],
              ),
            )
          else if (teamAgentUnavailableWords(l10n, agent) != null)
            pad(
              KitNotice(
                key: const ValueKey('team-agent-unavailable'),
                tone: AppStatusTone.failure,
                icon: AppIconography.error,
                title: l10n.teamAgentUnavailableTitle(agent.name),
                message: l10n.teamAgentUnavailableBody(
                  teamAgentUnavailableWords(l10n, agent)!,
                ),
              ),
            )
          else if (state == AgentState.stopped || state == AgentState.crashed)
            pad(
              _stoppedNotice(
                context,
                agent,
                crashed: state == AgentState.crashed,
              ),
            ),
          if (percent != null && percent >= teamContextRecyclePercent)
            pad(
              KitNotice(
                key: const ValueKey('team-agent-recycling'),
                icon: AppIconography.restart,
                title: l10n.teamUiAgentRecyclingSoon,
                message: l10n.teamAgentScreenRecyclingBody,
              ),
            ),
          // 3. Where it stands.
          _status(context, agent, work),
          // 4. The newest control.
          if (receipt != null)
            pad(
              teamControlReceipt(
                context,
                receipt,
                key: const ValueKey('team-agent-receipt'),
                onRetry: receipt.canRetry
                    ? () => _run(
                        () async =>
                            (await _controller.retryMutation(receipt.key)) ??
                            receipt,
                      )
                    : null,
                retryKey: const ValueKey('team-agent-receipt-retry'),
              ),
            ),
          if (!caps.controlAgent && !caps.controlMessage)
            pad(
              KitNotice(
                key: const ValueKey('team-agent-controls-elsewhere'),
                icon: AppIconography.info,
                message: l10n.teamAgentScreenControlsElsewhere(agent.name),
                actions: [
                  KitAction(
                    key: const ValueKey('team-agent-controls-how'),
                    label: l10n.teamUiHow,
                    onPressed: () => unawaited(showTeamHostGuideSheet(context)),
                  ),
                ],
              ),
            ),
          // 5. The technical truth, last.
          pad(
            KitDetailsFold(
              label: l10n.teamUiTechnicalDetails,
              foldKey: const ValueKey('team-agent-technical'),
              values: _technical(
                l10n,
                agent,
                work,
                lastCommand: _stepCommand(_lastStep()),
                sessionTitle: widget.sessionTitle,
              ),
            ),
          ),
        ],
      ),
    );
  }

  /// Stopped: "Start fox again" (resume). Crashed: restart it.
  Widget _stoppedNotice(
    BuildContext context,
    OrchestrationAgent agent, {
    required bool crashed,
  }) {
    final l10n = _copy(context);
    final caps = _controller.capabilities;
    return KitNotice(
      key: const ValueKey('team-agent-stopped'),
      tone: crashed ? AppStatusTone.failure : AppStatusTone.neutral,
      icon: crashed ? AppIconography.error : AppIconography.stopCircle,
      title: crashed
          ? l10n.teamAgentScreenCrashedTitle(agent.name)
          : l10n.teamAgentScreenStoppedTitle(agent.name),
      message: crashed
          ? l10n.teamAgentScreenCrashedBody
          : l10n.teamAgentScreenStoppedBody,
      actions: [
        // Never for an agent the app keeps off on its phone team: waking
        // it runs the phone past Android's process limit.
        if (caps.controlAgent && !teamAgentKeptOff(_controller.config, agent))
          KitAction(
            key: const ValueKey('team-agent-control-resume'),
            label: l10n.teamAgentScreenResume(agent.name),
            onPressed: _busy
                ? null
                : () => unawaited(
                    _control(
                      agent.id,
                      crashed
                          ? AgentControlAction.restart
                          : AgentControlAction.resume,
                    ),
                  ),
          ),
      ],
    );
  }

  /// The status panel: state with context and age, the newest step, the
  /// model in plain words, and "Not working on anything" when it has no
  /// task (the task itself is the top bar's subtitle).
  Widget _status(
    BuildContext context,
    OrchestrationAgent agent,
    WorkItem? work,
  ) {
    final l10n = _copy(context);
    final now = _now;
    final state = teamSessionState(agent);
    final percent = agent.contextPercent;
    final started = agent.sessionStartedAt;
    String elapsed(DateTime at) => teamElapsedLabel(
      l10n,
      now.isBefore(at) ? Duration.zero : now.difference(at),
    );
    final facts = [
      if (percent != null) l10n.teamUiAgentContextSemantics(percent),
      if (started != null) l10n.teamUiAgentSessionAge(elapsed(started)),
    ].join(teamUsageSeparator);
    final lastActive = agent.lastActivity;
    final step = _lastStep();
    final model = agent.model?.trim();
    return KitRowGroup(
      margin: _groupMargin(context),
      key: const ValueKey('team-agent-header'),
      children: [
        KitRow(
          key: const ValueKey('team-agent-state-row'),
          leading: KitStatusMark(
            state: _mark(state),
            paused: state == AgentState.stopped,
          ),
          title: teamAgentUnavailableWords(l10n, agent) != null
              ? l10n.teamAgentUnavailableState
              : teamAgentStateWord(l10n, state),
          titleKey: const ValueKey('team-agent-state'),
          supporting: facts.isEmpty ? null : TextSpan(text: facts),
          supportingKey: const ValueKey('team-agent-age'),
          supportingMaxLines: 2,
        ),
        if (step != null || lastActive != null)
          KitRow(
            key: const ValueKey('team-agent-activity-line'),
            leading: KitRow.icon(context, AppIconography.terminal),
            title: step == null
                ? l10n.teamAgentLastActive(elapsed(lastActive!))
                : l10n.teamAgentLastStep(agentStepWords(l10n, step)),
            supporting: step != null && lastActive != null
                ? TextSpan(text: l10n.teamAgentLastActive(elapsed(lastActive)))
                : null,
          ),
        if (model != null && model.isNotEmpty)
          KitRow(
            key: const ValueKey('team-agent-model'),
            leading: KitRow.icon(context, AppIconography.model),
            title: l10n.teamUiAgentLabelModel,
            supporting: TextSpan(text: _modelWords(l10n, agent, model)),
          ),
        if (work == null)
          KitRow(
            key: const ValueKey('team-agent-no-work'),
            leading: KitRow.icon(context, AppIconography.checklist),
            title: l10n.teamUiHomeAgentNoWork,
          ),
      ],
    );
  }

  /// A panel's place in the list: the gutter at the sides, a small step
  /// between panels.
  static EdgeInsetsDirectional _groupMargin(BuildContext context) {
    final tokens = KitTokens.of(context);
    return EdgeInsetsDirectional.fromSTEB(
      tokens.gutter,
      tokens.space2,
      tokens.gutter,
      tokens.space2,
    );
  }

  /// "gpt-x from openai": the model's own name, then who serves it.
  static String _modelWords(
    AppLocalizations l10n,
    OrchestrationAgent agent,
    String model,
  ) {
    final slash = model.indexOf('/');
    final name = slash < 0 ? model : model.substring(slash + 1);
    final provider = slash > 0 ? model.substring(0, slash) : agent.provider;
    if (provider == null || provider.trim().isEmpty || name.isEmpty) {
      return model;
    }
    return l10n.teamAgentScreenModelFrom(name, provider.trim());
  }

  static KitMarkState _mark(AgentState state) => switch (state) {
    AgentState.working => KitMarkState.working,
    AgentState.crashed => KitMarkState.failed,
    AgentState.idle ||
    AgentState.waiting ||
    AgentState.blocked ||
    AgentState.stopped ||
    AgentState.unknown => KitMarkState.waiting,
  };

  /// Every value the host reports about the agent, once each, mono and
  /// copyable (KIT-32, KIT-33): what it runs on, where it works, then the
  /// raw scalars.
  static List<KitTechnicalValue> _technical(
    AppLocalizations l10n,
    OrchestrationAgent agent,
    WorkItem? work, {
    String? lastCommand,
    String? sessionTitle,
  }) {
    bool has(String? value) => value != null && value.trim().isNotEmpty;
    final values = <KitTechnicalValue>[
      if (has(sessionTitle))
        KitTechnicalValue(l10n.teamUiAgentLabelSessionTitle, sessionTitle!),
      KitTechnicalValue(l10n.teamAgentScreenLabelId, agent.id),
      if (has(agent.sessionId))
        KitTechnicalValue(l10n.teamUiAgentLabelSessionId, agent.sessionId!),
      if (has(agent.sessionName))
        KitTechnicalValue(l10n.teamUiAgentLabelSessionName, agent.sessionName!),
      if (has(agent.provider))
        KitTechnicalValue(l10n.teamUiLabelProvider, agent.provider!),
      if (has(agent.model))
        KitTechnicalValue(l10n.teamUiAgentLabelModel, agent.model!),
      if (has(agent.harness))
        KitTechnicalValue(l10n.teamUiAgentLabelHarness, agent.harness!),
      if (has(agent.workDir))
        KitTechnicalValue(l10n.teamUiAgentLabelWorkDir, agent.workDir!),
      if (has(agent.branch))
        KitTechnicalValue(l10n.teamUiAgentLabelBranch, agent.branch!),
      if (work != null) KitTechnicalValue(l10n.teamUiGateLabelWorkId, work.id),
      if (lastCommand != null)
        KitTechnicalValue(
          l10n.teamAgentLastCommandLabel,
          lastCommand,
          key: const ValueKey('team-agent-last-command'),
        ),
      if (has(agent.unavailableReason))
        KitTechnicalValue(
          l10n.teamAgentUnavailableHostSays,
          agent.unavailableReason!,
          key: const ValueKey('team-agent-unavailable-reason'),
        ),
      if (has(agent.rawState))
        KitTechnicalValue(l10n.teamUiRunLabelRawState, agent.rawState!),
      if (has(agent.pool))
        KitTechnicalValue(l10n.teamUiAgentLabelPool, agent.pool!),
      if (has(agent.pack))
        KitTechnicalValue(l10n.teamUiAgentLabelPack, agent.pack!),
    ];
    final scalars = <(String, String)>[];
    void collect(Map<String, Object?> map, String prefix) {
      for (final entry in map.entries) {
        final value = entry.value;
        if (prefix.isEmpty && entry.key == 'unavailable_reason') continue;
        if (value is String || value is num || value is bool) {
          final text = '$value';
          if (text.trim().isEmpty || KitRedact.containsSecret(text)) continue;
          scalars.add(('$prefix${entry.key}', text));
        } else if (value is Map && prefix.isEmpty) {
          collect({
            for (final e in value.entries) '${e.key}': e.value,
          }, '${entry.key}.');
        }
      }
    }

    collect(agent.raw, '');
    scalars.sort((a, b) => a.$1.compareTo(b.$1));
    return [
      ...values,
      for (final (label, value) in scalars) KitTechnicalValue(label, value),
    ];
  }
}
