part of '../chat_screen.dart';

// The AI Team conversation's parts: the lead's reply, the one "Now" line,
// the no-progress notice, and a task's steps.

/// A team write's receipt in words: "Message · Sending…", then
/// "Message · Confirmed" or the host's reason, with Try again when the
/// record may be retried ([teamControlReceipt]).
Widget _teamReceipt(
  BuildContext context,
  MutationRecord record, {
  required Key key,
  String? control,
  Future<void> Function()? onRetry,
}) => teamControlReceipt(
  context,
  record,
  key: key,
  control: control,
  onRetry: onRetry,
  retryKey: ValueKey('${(key as ValueKey<String>).value}-retry'),
);

/// The lead's reply, read like any reply in the chat: one plain line per
/// real team event with its time, on the prose's edge, no frame or fill.
/// When there are many, the earlier ones fold under one quiet line and the
/// newest stay in view.
class _TeamLeadReply extends StatefulWidget {
  const _TeamLeadReply({required this.rows, required this.expansion});

  final List<String> rows;
  final Map<String, bool> expansion;

  /// Up to this many lines show unfolded; beyond it, all but the newest
  /// [_kept] fold.
  static const _openUpTo = 3;
  static const _kept = 2;
  static const _storeKey = 'team-lead-earlier';

  /// The lead's lines with their times, oldest first.
  static List<String> rowsFor(
    BuildContext context, {
    required List<TeamLeadLine> lines,
    required TeamPendingTask? pending,
    String? taskTitle,
    String? roleName,
  }) {
    final l10n = _chatL10n(context);
    String at(DateTime? time, {bool seconds = false}) {
      if (time == null) return '';
      final clock = teamClockLabel(context, time);
      if (!seconds) return ' · $clock';
      final second = time.toLocal().second.toString().padLeft(2, '0');
      return ' · $clock:$second';
    }

    return [
      if (pending case final task?)
        '${l10n.teamChatLeadSent}${at(task.sentAt)}',
      for (final line in lines)
        // The moment a worker began the task is said to the second: it is
        // the pickup the person waited for.
        '${teamLeadSentence(l10n, line, taskTitle: taskTitle, roleName: roleName)}'
            '${at(line.at, seconds: line.event == TeamLeadEvent.claimed)}',
    ];
  }

  @override
  State<_TeamLeadReply> createState() => _TeamLeadReplyState();
}

class _TeamLeadReplyState extends State<_TeamLeadReply> {
  bool get _expanded => widget.expansion[_TeamLeadReply._storeKey] ?? false;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final rows = widget.rows;
    final fold = rows.length > _TeamLeadReply._openUpTo;
    final earlier = fold
        ? rows.sublist(0, rows.length - _TeamLeadReply._kept)
        : const <String>[];
    final recent = fold
        ? rows.sublist(rows.length - _TeamLeadReply._kept)
        : rows;
    return Column(
      key: const ValueKey('team-conversation-lead'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (fold) ...[
          KitMessage.notice(
            noticeKey: const ValueKey('team-conversation-lead-earlier'),
            icon: AppIconography.timeline,
            text: l10n.teamChatLeadEarlier(earlier.length),
            detail: KitMarkdown(earlier.join('\n\n'), selectable: false),
            expanded: _expanded,
            onExpansionChanged: (open) => setState(
              () => widget.expansion[_TeamLeadReply._storeKey] = open,
            ),
          ),
          SizedBox(height: tokens.space2),
        ],
        KitMessage.reply(
          bodyKey: const ValueKey('team-conversation-lead-reply'),
          body: KitMarkdown(
            (recent.isEmpty ? [l10n.teamChatLeadNothingYet] : recent).join(
              '\n\n',
            ),
          ),
        ),
      ],
    );
  }
}

/// The lead's words for one line (see [TeamLeadEvent]). A step that is the
/// task itself ([taskTitle], said once in the prompt) is "it", never its
/// words again (owner rule: nothing shown twice).
String teamLeadSentence(
  AppLocalizations l10n,
  TeamLeadLine line, {
  String? taskTitle,
  String? roleName,
}) {
  final title = line.workTitle ?? '';
  final role = roleName == null || roleName.isEmpty ? null : roleName;
  if (_sameTaskText(line.workTitle, taskTitle)) {
    switch (line.event) {
      case TeamLeadEvent.routed:
        return l10n.teamChatLeadRoutedIt;
      case TeamLeadEvent.workerStarting:
        return role == null
            ? l10n.teamChatLeadStartingIt
            : l10n.teamChatLeadStartingItRole(role);
      case TeamLeadEvent.claimed:
        return role == null
            ? l10n.teamChatLeadClaimedWorkerIt
            : l10n.teamChatLeadClaimedItRole(role);
      case TeamLeadEvent.pushed:
        return l10n.teamChatLeadPushedIt;
      case TeamLeadEvent.handedToReview:
        return l10n.teamChatLeadReviewIt;
      case TeamLeadEvent.merged:
        return l10n.teamChatLeadMergedIt;
      case TeamLeadEvent.stepFailed:
        return l10n.teamChatLeadStepFailedIt;
      case TeamLeadEvent.stepCancelled:
        return l10n.teamChatLeadStepCancelledIt;
      default:
        break;
    }
  }
  return switch (line.event) {
    TeamLeadEvent.planned => l10n.teamChatLeadPlanned(line.count ?? 0),
    TeamLeadEvent.routed => l10n.teamChatLeadRouted(title),
    TeamLeadEvent.workerStarting =>
      role == null
          ? l10n.teamChatLeadStarting(title)
          : l10n.teamChatLeadStartingRole(role, title),
    TeamLeadEvent.claimed =>
      role == null
          ? l10n.teamChatLeadClaimedWorker(title)
          : l10n.teamChatLeadClaimedRole(role, title),
    TeamLeadEvent.pushed => l10n.teamChatLeadPushed(title),
    TeamLeadEvent.handedToReview => l10n.teamChatLeadReview(title),
    TeamLeadEvent.merged => l10n.teamChatLeadMerged(title),
    TeamLeadEvent.stepFailed => l10n.teamChatLeadStepFailed(title),
    TeamLeadEvent.stepCancelled => l10n.teamChatLeadStepCancelled(title),
    TeamLeadEvent.needsYou => l10n.teamChatLeadNeedsYou(line.detail ?? ''),
    TeamLeadEvent.taskMerged => l10n.teamChatLeadTaskMerged,
    TeamLeadEvent.taskFinished => l10n.teamChatLeadTaskFinished,
    TeamLeadEvent.taskFailed => l10n.teamChatLeadTaskFailed,
    TeamLeadEvent.taskCancelled => l10n.teamChatLeadTaskCancelled,
  };
}

/// The task's one Now line ([TeamNowLineView], slice-P5.1): what the team
/// is doing for this task now, for how long, what comes next and how long
/// that usually takes; after 8 s without the next stage it says why and
/// unfolds the Why in place. A team that has not answered for 8 s is
/// "not answering", with Try again.
class _TeamNowLine extends StatelessWidget {
  const _TeamNowLine({
    required this.team,
    required this.clock,
    required this.startingWorker,
    required this.input,
    required this.wayOut,
    required this.quiet,
  });

  final OrchestrationController team;
  final DateTime Function() clock;

  /// A worker is starting: a late answer is the phone being busy, not the
  /// team being gone.
  final bool startingWorker;

  /// The line's facts, given whether the team answers; null hides it.
  final TeamNowInput? Function(bool connected) input;
  final KitAction? Function(TeamNowAction action) wayOut;

  /// How long the task has shown no progress, past an hour; its notice
  /// in the transcript explains, so the line only says how long.
  final Duration? quiet;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final waiting =
        !team.snapshot.hasData ||
        // Not answering only: a paused team answers, and says so itself; a
        // team busy starting a worker answers late and keeps its stage.
        (!teamHostBusyStarting(team, startingWorker: startingWorker) &&
            teamHostCondition(
                  l10n,
                  team,
                  working: true,
                  startingWorker: startingWorker,
                ) !=
                null);
    return GraceTimer(
      waiting: waiting,
      grace: KitMotion.escalateAfter,
      builder: (context, overdue) {
        final facts = input(!(overdue && waiting));
        final quiet = this.quiet;
        return KitReveal(
          child: facts == null
              ? null
              : TeamNowLineView(
                  keyPrefix: 'team-conversation',
                  input: facts,
                  wayOut: wayOut,
                  clock: clock,
                  message:
                      quiet == null ||
                          facts.activity == TeamNowActivity.unavailable
                      ? null
                      : l10n.teamChatNowNoProgress(
                          KitSince.durationWords(l10n, quiet),
                        ),
                  explains: quiet == null,
                ),
        );
      },
    );
  }
}

/// How long a stalled task has shown no progress, or null when it is not
/// stalled or has been quiet less than [teamNoProgressAfter].
Duration? _noProgress(TeamNow? now, DateTime clock) {
  final quiet = now?.quietSince;
  if (now == null || now.kind != TeamNowKind.stalled || quiet == null) {
    return null;
  }
  final span = clock.difference(quiet);
  return span < teamNoProgressAfter ? null : span;
}

/// Whether two texts name the same task: equal once trimmed, spaced and
/// cased alike, or one the other cut short by the host (a long title ends
/// in "…").
bool _sameTaskText(String? a, String? b) {
  String norm(String text) => text
      .trim()
      .replaceAll(RegExp(r'\s+'), ' ')
      .toLowerCase()
      .replaceAll(RegExp(r'[….]+$'), '')
      .trim();
  if (a == null || b == null) return false;
  final x = norm(a), y = norm(b);
  if (x.isEmpty || y.isEmpty) return false;
  if (x == y) return true;
  final (short, long) = x.length < y.length ? (x, y) : (y, x);
  return short.length >= 24 && long.startsWith(short);
}

/// "Worker": the agent's role word; its generated name stays on its own page.
String _teamAgentName(AppLocalizations l10n, OrchestrationAgent agent) =>
    teamAgentRoleWord(l10n, teamAgentRole(agent));

/// Under a task with no progress for [quiet]: what happened, since when,
/// and the ways forward — nudge or restart its worker (where the host
/// takes agent controls; restart asks first), or report the problem. The
/// control's receipt follows.
class _TeamNoProgress extends StatelessWidget {
  const _TeamNoProgress({
    required this.team,
    required this.agent,
    required this.agentName,
    required this.quiet,
    required this.since,
    required this.runId,
    required this.onControl,
    required this.today,
  });

  final OrchestrationController team;

  /// The page's clock now, to tell a time today from another day's.
  final DateTime today;
  final OrchestrationAgent? agent;
  final String? agentName;
  final Duration quiet;
  final DateTime since;
  final String? runId;
  final Future<void> Function(OrchestrationAgent, AgentControlAction) onControl;

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final agent = this.agent;
    final name = agentName ?? l10n.teamChatAWorker;
    final controls = agent != null && team.capabilities.controlAgent;
    // A time from another day names the day too ("Sep 24, 02:55").
    final local = since.toLocal();
    final today = this.today.toLocal();
    final time =
        local.year == today.year &&
            local.month == today.month &&
            local.day == today.day
        ? teamClockLabel(context, since)
        : '${MaterialLocalizations.of(context).formatShortMonthDay(local)}, '
              '${teamClockLabel(context, since)}';
    final receipt = agent == null
        ? null
        : team.latestMutation(
            kind: MutationKind.controlAgent,
            targetId: agent.id,
          );
    final elapsed = KitSince.durationWords(l10n, quiet);
    return Column(
      key: const ValueKey('team-conversation-no-progress'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitNotice(
          key: const ValueKey('team-conversation-no-progress-notice'),
          icon: AppIconography.waiting,
          message: controls
              ? l10n.teamChatNoProgressBody(name, time)
              : l10n.teamChatNoProgressBodyNoControls(name, time),
          liveRegion: false,
        ),
        SizedBox(height: tokens.space2),
        // Nudge is the light first step; restart (asked first) and the
        // report sit beside it.
        KitActionBlock(
          key: const ValueKey('team-conversation-no-progress-actions'),
          secondary: controls
              ? KitAction(
                  key: const ValueKey('team-conversation-no-progress-nudge'),
                  label: l10n.teamAgentScreenNudge(name),
                  onPressed: () =>
                      unawaited(onControl(agent, AgentControlAction.nudge)),
                )
              : null,
          tertiary: [
            if (controls)
              KitAction(
                key: const ValueKey('team-conversation-no-progress-restart'),
                label: l10n.teamAgentScreenRestart(name),
                onPressed: () =>
                    unawaited(onControl(agent, AgentControlAction.restart)),
              ),
            KitAction(
              key: const ValueKey('team-conversation-no-progress-report'),
              label: l10n.teamChatNoProgressReport,
              onPressed: () => unawaited(
                openBugReport(
                  context,
                  error: KitReport(
                    title: l10n.teamChatNoProgressReportTitle(elapsed),
                    source: 'team-conversation',
                    details: [
                      if (runId case final id?) 'task: $id',
                      if (agent != null) 'agent: ${agent.id}',
                      if (agent?.currentWorkId case final work?) 'step: $work',
                      'quiet since: ${since.toUtc().toIso8601String()}',
                    ].join('\n'),
                  ),
                ),
              ),
            ),
          ],
        ),
        if (receipt != null) ...[
          SizedBox(height: tokens.space2),
          _teamReceipt(
            context,
            receipt,
            key: const ValueKey('team-conversation-no-progress-receipt'),
            onRetry: () => team.retryMutation(receipt.key),
          ),
        ],
      ],
    );
  }
}

/// The same state as a sub-agent line's status word.
KitToolStatus _teamAgentToolStatus(OrchestrationAgent agent) =>
    switch (teamSessionState(agent)) {
      AgentState.working => KitToolStatus.running,
      AgentState.crashed => KitToolStatus.failed,
      AgentState.stopped => KitToolStatus.done,
      AgentState.idle ||
      AgentState.waiting ||
      AgentState.blocked ||
      AgentState.unknown => KitToolStatus.pending,
    };

/// "Worker", or "Worker 2" when [among] holds several of the same role. The
/// generated name (Gas City's) is shown only on the worker's own page.
String _teamAgentTitle(
  AppLocalizations l10n,
  OrchestrationAgent agent, [
  List<OrchestrationAgent> among = const [],
  // The role the task was given to names its workers ("Frontend").
  String? roleName,
]) {
  final role = teamAgentRole(agent);
  final word = role == TeamAgentRole.worker && roleName != null
      ? roleName
      : teamAgentRoleWord(l10n, role);
  final same = [
    for (final other in among)
      if (teamAgentRole(other) == role) other.id,
  ];
  final index = same.indexOf(agent.id);
  return same.length > 1 && index >= 0
      ? l10n.teamChatWorkerNumbered(word, index + 1)
      : word;
}

/// The task's steps, folded under the turn's one work line: "{n} steps ·
/// {done} done" while the task runs. Up to three steps show open; more
/// fold, and the person's choice is kept in the page's expansion store.
class _TeamSteps extends StatefulWidget {
  const _TeamSteps({
    required this.work,
    required this.expansion,
    required this.onOpen,
  });

  final List<WorkItem> work;
  final Map<String, bool> expansion;

  /// Opens a step's Work sheet (who has it, since when, what it waits on).
  final ValueChanged<String> onOpen;

  static const _openUpTo = 3;
  static const _storeKey = 'team-steps';

  @override
  State<_TeamSteps> createState() => _TeamStepsState();
}

class _TeamStepsState extends State<_TeamSteps> {
  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final work = widget.work;
    final expansion = widget.expansion;
    final done = work.where((item) => item.state == WorkState.completed).length;
    final failed = work.any((item) => item.state == WorkState.failed);
    final running = work.any(
      (item) => switch (item.state) {
        WorkState.completed || WorkState.failed || WorkState.cancelled => false,
        _ => true,
      },
    );
    final needsYou = work.any((item) => item.state == WorkState.needsInput);
    final state = needsYou
        ? KitWorkState.waitingForYou
        : running
        ? KitWorkState.running
        : failed
        ? KitWorkState.endedFailed
        : KitWorkState.done;
    return KitWorkLine(
      key: const ValueKey('team-conversation-steps'),
      lineKey: const ValueKey('team-conversation-steps-fold'),
      counts: KitWorkCounts(steps: work.length),
      state: state,
      now: l10n.teamChatStepsSummary(work.length, done),
      expanded:
          expansion[_TeamSteps._storeKey] ??
          work.length <= _TeamSteps._openUpTo,
      onExpansionChanged: (open) =>
          setState(() => expansion[_TeamSteps._storeKey] = open),
      steps: [
        for (final item in work)
          KitToolRow(
            rowKey: ValueKey('team-conversation-step-${item.id}'),
            kind: KitToolKind.todo,
            title: item.title,
            status: _teamStepStatus(item.state),
            onOpen: () => widget.onOpen(item.id),
          ),
      ],
    );
  }
}

/// A step's state as a work step's status.
KitToolStatus _teamStepStatus(WorkState state) => switch (state) {
  WorkState.completed => KitToolStatus.done,
  WorkState.failed => KitToolStatus.failed,
  WorkState.cancelled => KitToolStatus.stopped,
  WorkState.working || WorkState.review => KitToolStatus.running,
  WorkState.needsInput => KitToolStatus.waitingForYou,
  WorkState.queued ||
  WorkState.ready ||
  WorkState.waiting ||
  WorkState.blocked ||
  WorkState.unknown => KitToolStatus.pending,
};
