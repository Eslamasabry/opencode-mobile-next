part of '../team_projects_screen.dart';

Widget _needsYou(
  BuildContext context,
  AppLocalizations l,
  TeamProjectController c,
  TeamProject p,
  TeamRequest r,
) {
  final t = p.tasks.where((t) => t.id == r.taskId).firstOrNull;
  final role = t == null ? '' : _role(context, c, t.roleId);
  final server = t == null
      ? null
      : c.snapshot?.servers.where((s) => s.id == t.serverId).firstOrNull;
  final after = t == null
      ? 0
      : p.tasks.where((o) => o.dependsOn.contains(t.id)).length;
  return KitPlanCard(
    title: r.title,
    status: [
      l.teamProjectNeedsYou,
      if (t != null && server != null)
        l.teamProjectRequestWhere(role, server.name),
    ].join(' · '),
    state: KitTeamState.needsYou,
    summary: t != null && after > 0
        ? l.teamProjectRequestBlocks(role, after)
        : null,
    actions: [
      KitAction(
        label: _requestLabel(l, r),
        onPressed: () => _answer(context, c, p, r),
      ),
    ],
  );
}

String _money(double v) => v == v.roundToDouble()
    ? '\$${v.toStringAsFixed(0)}'
    : '\$${v.toStringAsFixed(2)}';

String _costLine(AppLocalizations l, TeamProject p) {
  if (!p.usageReported) return l.teamProjectCostNotReported;
  final b = p.settings.budget;
  final daily = b.unlimited ? null : b.daily;
  final total = b.unlimited ? null : b.total;
  final today = daily == null
      ? l.teamProjectCostToday(_money(p.spentToday))
      : l.teamProjectCostTodayOf(_money(p.spentToday), _money(daily));
  final all = total == null
      ? l.teamProjectCostTotal(_money(p.spent))
      : l.teamProjectCostTotalOf(_money(p.spent), _money(total));
  return [
    today,
    all,
    if (daily == null && total == null) l.teamProjectNoLimit,
  ].join(' · ');
}

String _todayCost(AppLocalizations l, TeamProject p) {
  final b = p.settings.budget;
  final daily = b.unlimited ? null : b.daily;
  return daily == null
      ? l.teamProjectCostToday(_money(p.spentToday))
      : l.teamProjectCostTodayOf(_money(p.spentToday), _money(daily));
}

String _headline(AppLocalizations l, TeamProject p) {
  if (p.status != 'running') return _word(l, p.status);
  final ms = p.specDraft.milestones;
  if (ms.isEmpty) return _progress(l, p);
  final i = ms.indexWhere((m) => !m.accepted);
  return i < 0
      ? l.teamProjectHeadlineDone(ms.length)
      : l.teamProjectHeadlineMilestone(
          i + 1,
          ms.length,
          p.tasks.where((t) => t.status == 'running').length,
        );
}

String _goalStatus(BuildContext context, AppLocalizations l, TeamProject p) {
  final spec = p.specDraft;
  final approved = p.specVersions.lastOrNull;
  final at = DateTime.tryParse(approved?.approvedAt ?? '');
  return at == null
      ? l.teamProjectGoalStatusDraft(spec.milestones.length, p.repos.length)
      : l.teamProjectGoalStatus(
          _age(context, approved!.approvedAt),
          spec.milestones.length,
          p.repos.length,
        );
}

String _elapsed(AppLocalizations l, String raw) {
  final at = DateTime.tryParse(raw);
  if (at == null) return '';
  final d = DateTime.now().difference(at);
  if (d.inMinutes < 1) {
    return l.teamProjectElapsedSeconds(d.inSeconds < 0 ? 0 : d.inSeconds);
  }
  if (d.inHours < 1) return l.teamProjectElapsedMinutes(d.inMinutes);
  return l.teamProjectElapsedHours(d.inHours, d.inMinutes % 60);
}

String _laneLine(
  BuildContext context,
  AppLocalizations l,
  TeamProjectController c,
  TeamTask t,
  bool stale,
) {
  final server = c.snapshot?.servers
      .where((s) => s.id == t.serverId)
      .firstOrNull;
  final name = server?.name ?? '';
  if (stale) return '$name · ${l.teamProjectTaskStale}';
  if (server != null && !server.online) {
    return '$name · ${l.teamProjectOffline}';
  }
  return t.status == 'running'
      ? l.teamProjectLaneRunning(name, _elapsed(l, t.changedAt))
      : l.teamProjectLaneWaiting(name);
}

String _laneNote(AppLocalizations l, TeamProject p, int total) {
  final note = p.settings.mode == 'single'
      ? l.teamProjectLaneNoteSingle
      : l.teamProjectLaneNoteParallel(total);
  return p.simulated ? l.teamProjectLaneNoteDemo(note) : note;
}

String _who(
  BuildContext context,
  AppLocalizations l,
  TeamProjectController c,
  TeamTimelineEvent e,
) {
  if (e.actor == 'person') return l.teamProjectYou;
  return c.snapshot?.roles.where((r) => r.id == e.actor).firstOrNull?.name ??
      '';
}

String _boardSummary(AppLocalizations l, TeamProject p) {
  if (p.tasks.isEmpty) return l.teamProjectBoardEmpty;
  final withTasks = p.specDraft.milestones.where(
    (m) => p.tasks.any(
      (t) => p.phases.any((ph) => ph.id == t.phaseId && ph.milestoneId == m.id),
    ),
  );
  return l.teamProjectBoardSummary(
    p.tasks.length,
    withTasks.isEmpty ? 1 : withTasks.length,
  );
}

String _timelineSummary(
  BuildContext context,
  AppLocalizations l,
  TeamProject p,
) {
  if (p.timeline.isEmpty) return l.teamProjectTimelineEmpty;
  return l.teamProjectTimelineSummary(
    p.timeline.length,
    _age(context, p.timeline.last.at),
  );
}

void _openTask(
  BuildContext context,
  TeamProjectController c,
  String p,
  String t,
) {
  unawaited(
    pushKitPage<void>(
      context,
      (_) => TeamProjectConversation(controller: c, projectId: p, taskId: t),
    ),
  );
}

void _openBoard(
  BuildContext context,
  TeamProjectController c,
  String p, {
  String? milestone,
}) {
  unawaited(
    pushKitPage<void>(
      context,
      (_) =>
          TeamProjectBoard(controller: c, projectId: p, milestone: milestone),
    ),
  );
}

Future<TeamCommandResult> _command(
  TeamProjectController c,
  TeamProject p,
  TeamProjectAction action, {
  String targetId = '',
  String text = '',
  String serverId = '',
  bool confirmed = false,
}) => c.execute(
  TeamProjectCommand(
    requestId: c.newRequestId(),
    projectId: p.id,
    expectedRevision: p.revision,
    action: action,
    targetId: targetId,
    text: text,
    serverId: serverId,
    confirmed: confirmed,
  ),
);
Future<void> _answer(
  BuildContext context,
  TeamProjectController c,
  TeamProject p,
  TeamRequest r,
) async {
  final l = lookupAppLocalizations(Localizations.localeOf(context));
  switch (r.kind) {
    case 'spec':
      await openTeamSpecEditor(context, c, p.id);
      return;
    case 'planFormat':
    case 'plan':
      await openTeamPlanEditor(context, c, p.id);
      return;
    case 'budget':
      await openTeamProjectSettings(context, c, p.id);
      return;
    case 'milestone':
      final milestone = p.specDraft.milestones
          .where((m) => m.id == r.phaseId)
          .firstOrNull;
      if (milestone != null &&
          await showKitConfirm(
            context,
            title: l.teamProjectAcceptMilestoneConfirmTitle,
            body: '${milestone.title}\n${milestone.criteria.join('\n')}',
            confirmLabel: l.teamProjectAccept,
          )) {
        await _command(
          c,
          p,
          TeamProjectAction.acceptMilestone,
          targetId: milestone.id,
        );
      }
      return;
    case 'phase':
      final task = p.tasks.where((t) => t.phaseId == r.phaseId).firstOrNull;
      if (task != null) _openTask(context, c, p.id, task.id);
      return;
    case 'question':
    case 'permission':
      break;
    default:
      if (r.taskId.isNotEmpty) _openTask(context, c, p.id, r.taskId);
      return;
  }
  if (!context.mounted) return;
  await showKitInputDialog(
    context,
    title: r.title,
    label: l.teamProjectAnswerLabel,
    confirmLabel: l.teamProjectAnswer,
    onSubmit: (value) async {
      final latest =
          c.snapshot?.projects.where((v) => v.id == p.id).firstOrNull ?? p;
      final result = await _command(
        c,
        latest,
        TeamProjectAction.answerRequest,
        targetId: r.id,
        text: value,
      );
      return result.accepted ? null : l.teamProjectError;
    },
  );
}

String _requestLabel(AppLocalizations l, TeamRequest r) => switch (r.kind) {
  'spec' => l.teamProjectSpec,
  'plan' || 'planFormat' => l.teamProjectPlan,
  'budget' => l.teamProjectSettings,
  'milestone' => l.teamProjectAccept,
  'question' || 'permission' => l.teamProjectAnswer,
  _ => l.teamProjectReview,
};

/// A plan that failed always says why in plain words and what to do next:
/// Resume when the engine can pick the planning up again, otherwise approve
/// the spec again. The engine's code stays under Details.
List<Widget> _planFailed(
  BuildContext context,
  AppLocalizations l,
  TeamProjectController c,
  TeamProject p,
) {
  final state = p.planningState;
  final code = state?.reason ?? '';
  final why = code.isEmpty ? null : teamReasonFor(l, code);
  // The status is planFailed only when the engine advertises retryPlan.
  final canRetry = p.status == 'planFailed';
  final resumable =
      state?.stage == 'interrupted' &&
      (code == 'restartNeedsReconciliation' ||
          code == 'pauseNeedsReconciliation') &&
      TeamExecutionGate.allows(c, TeamExecutionNeed.resume);
  return [
    KitNotice(
      key: const ValueKey('team-plan-failed'),
      title: l.teamProjectPlanFailedTitle,
      message: why?.message ?? l.teamProjectFailed,
      notes: [
        why?.next ?? (canRetry ? '' : l.teamProjectApproveAgainNote),
      ].where((n) => n.isNotEmpty).toList(),
      actions: [
        if (resumable)
          KitAction(
            key: const ValueKey('team-plan-failed-resume'),
            label: l.teamUiControlResume,
            onPressed: () => _command(c, p, TeamProjectAction.resumeProject),
          ),
        if (teamReasonNeedsModel(code))
          KitAction(
            key: const ValueKey('team-plan-failed-model'),
            label: l.teamRefusalModelNotConfiguredAction,
            onPressed: () => openTeamDefaults(context, c),
          ),
        if (canRetry && !resumable)
          KitAction(
            key: const ValueKey('team-plan-failed-retry'),
            label: l.teamProjectEditorAskAgain,
            onPressed: () => _command(c, p, TeamProjectAction.retryPlan),
          ),
        if (!resumable && !canRetry)
          KitAction(
            key: const ValueKey('team-plan-failed-approve'),
            label: l.teamProjectApproveAgain,
            onPressed: () => openTeamSpecEditor(context, c, p.id),
          ),
      ],
    ),
    if (code.isNotEmpty)
      KitDetailsFold(values: [KitTechnicalValue(l.teamRefusalCode, code)]),
  ];
}

Widget _failure(BuildContext context, TeamProjectController c) {
  final l = lookupAppLocalizations(Localizations.localeOf(context));
  return KitNotice(
    message: l.teamProjectError,
    actions: [KitAction(label: l.teamProjectRetry, onPressed: c.load)],
  );
}

String _role(BuildContext context, TeamProjectController c, String id) =>
    c.snapshot?.roles.where((r) => r.id == id).firstOrNull?.name ??
    lookupAppLocalizations(Localizations.localeOf(context)).teamProjectHome;
String _age(BuildContext context, String raw) {
  final at = DateTime.tryParse(raw);
  if (at == null) return '';
  return relativeAgeLabel(
    DateTime.now().difference(at),
    at: at,
    l10n: lookupAppLocalizations(Localizations.localeOf(context)),
  );
}

int _urgency(TeamProject p) => p.requests.any((r) => !r.answered)
    ? 0
    : p.status == 'running'
    ? 1
    : p.status == 'done'
    ? 3
    : 2;
String _progress(AppLocalizations l, TeamProject p) => l.teamProjectProgress(
  p.tasks.where((t) => t.status == 'merged' || t.status == 'done').length,
  p.tasks.length,
  p.tasks.where((t) => t.status == 'running').length,
);
KitTeamState _state(String value) => switch (value) {
  'running' => KitTeamState.running,
  'failed' || 'planFailed' || 'conflict' => KitTeamState.failed,
  'stalled' || 'interrupted' => KitTeamState.stalled,
  'findings' || 'review' => KitTeamState.needsYou,
  'offline' => KitTeamState.stale,
  'done' || 'merged' || 'passed' => KitTeamState.done,
  _ => KitTeamState.empty,
};

/// Header rows sit on the page's one gutter, like the list rows below.
Widget _gutter(BuildContext context, Widget child) => Padding(
  padding: EdgeInsetsDirectional.symmetric(
    horizontal: KitTokens.of(context).gutter,
  ),
  child: child,
);

int _lanesHere(TeamProject p, int serverCap) {
  if (p.settings.mode == 'single') return 1;
  return serverCap < p.settings.maxLanes ? serverCap : p.settings.maxLanes;
}

/// What a merge-queue row says when it has no reason of its own: a queued
/// item is waiting for its turn (or its checks), never for dependencies.
String _mergeWord(AppLocalizations l, TeamMergeItem i) => i.status == 'queued'
    ? (i.checksPassed ? l.teamProjectMergeReady : l.teamProjectMergeChecking)
    : _word(l, i.status);

String _word(AppLocalizations l, String value) => switch (value) {
  'running' => l.teamProjectWorking,
  'failed' || 'planFailed' || 'conflict' => l.teamProjectFailed,
  'stalled' => l.teamProjectStalled,
  'interrupted' => l.teamProjectInterrupted,
  'paused' => l.teamProjectPaused,
  'stopped' => l.teamProjectStopped,
  'done' || 'merged' || 'passed' => l.teamProjectDone,
  'review' || 'verified' || 'findings' => l.teamProjectReview,
  'spec' => l.teamProjectPlanning,
  'plan' => l.teamProjectPlanWaiting,
  'waiting' => l.teamProjectNeedsYou,
  _ => l.teamProjectWaiting,
};
