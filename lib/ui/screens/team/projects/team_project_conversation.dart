import 'package:flutter/material.dart';

import '../../../../domain/relative_age.dart';
import '../../../../l10n/app_localizations.dart';
import '../../../../state/team_project_controller.dart';
import '../../../kit/kit.dart';
import 'team_execution_gate.dart';
import 'team_merge_flow.dart';
import 'team_project_editors.dart';
import 'team_refusal.dart';

/// Finish line: review, steer, verify and promote a task from its conversation.
/// Non-goal: choosing or directly calling an execution engine.
class TeamProjectConversation extends StatefulWidget {
  const TeamProjectConversation({
    super.key,
    required this.controller,
    required this.projectId,
    required this.taskId,
    this.embedded = false,
  });
  final TeamProjectController controller;
  final String projectId;
  final String taskId;
  final bool embedded;
  @override
  State<TeamProjectConversation> createState() =>
      _TeamProjectConversationState();
}

class _TeamProjectConversationState extends State<TeamProjectConversation> {
  final _message = TextEditingController();
  final _focus = FocusNode();
  final _expanded = <String>{};
  final _selected = <String>{};
  final _drafts = <(String, String), String>{};

  @override
  void didUpdateWidget(covariant TeamProjectConversation oldWidget) {
    super.didUpdateWidget(oldWidget);
    final switchedController = oldWidget.controller != widget.controller;
    if (!switchedController &&
        oldWidget.projectId == widget.projectId &&
        oldWidget.taskId == widget.taskId) {
      return;
    }
    if (switchedController) {
      _drafts.clear();
    } else {
      _drafts[(oldWidget.projectId, oldWidget.taskId)] = _message.text;
    }
    _message.text = _drafts[(widget.projectId, widget.taskId)] ?? '';
    _selected.clear();
    _expanded.clear();
    _focus.unfocus();
  }

  AppLocalizations get l => AppLocalizations.of(context);
  TeamProjectController get c => widget.controller;

  @override
  void dispose() {
    _message.dispose();
    _focus.dispose();
    super.dispose();
  }

  Future<TeamCommandResult> _run(
    TeamProject p,
    TeamProjectAction action, {
    String? target,
    String text = '',
    bool confirmed = false,
    TeamRepo? repo,
    List<String> findingIds = const [],
    String serverId = '',
  }) => c.execute(
    TeamProjectCommand(
      requestId: c.newRequestId(),
      action: action,
      projectId: p.id,
      expectedRevision: p.revision,
      targetId: target ?? widget.taskId,
      text: text,
      findingIds: findingIds,
      serverId: serverId,
      confirmed: confirmed,
      expectedDevCommit: repo?.devCommit ?? '',
      expectedMainCommit: repo?.mainCommit ?? '',
    ),
  );

  KitAction _action(
    String label,
    VoidCallback callback, {
    bool destructive = false,
  }) => KitAction(
    label: label,
    onPressed: c.busy ? null : callback,
    destructive: destructive,
    disabledReason: c.busy ? l.teamProjectTaskWaiting : null,
  );

  /// An action that needs the engine to run work: not drawn while it
  /// cannot (one plain line above says why and carries the fix).
  KitAction? _gated(
    TeamExecutionNeed need,
    String label,
    VoidCallback callback, {
    bool destructive = false,
  }) => TeamExecutionGate.allows(c, need)
      ? _action(label, callback, destructive: destructive)
      : null;

  String _age(String value) {
    final at = DateTime.tryParse(value);
    return at == null
        ? ''
        : relativeAgeLabel(DateTime.now().difference(at), at: at, l10n: l);
  }

  KitTeamState _state(String status) => switch (status) {
    'running' || 'verifying' || 'fixing' => KitTeamState.running,
    'needsYou' ||
    'needs_you' ||
    'question' ||
    'review' => KitTeamState.needsYou,
    'failed' => KitTeamState.failed,
    'stalled' || 'interrupted' => KitTeamState.stalled,
    'done' || 'merged' || 'accepted' => KitTeamState.done,
    _ => KitTeamState.empty,
  };
  String _status(String status) => switch (_state(status)) {
    KitTeamState.running => l.teamProjectTaskRunning,
    KitTeamState.needsYou => l.teamProjectTaskReview,
    KitTeamState.failed => l.teamProjectTaskFailed,
    KitTeamState.done => l.teamProjectTaskDone,
    _ => l.teamProjectTaskWaiting,
  };

  Future<void> _answer(TeamProject p, TeamRequest request) async {
    await showKitInputDialog(
      context,
      title: request.title,
      label: l.teamProjectTaskAnswerLabel,
      confirmLabel: l.teamProjectTaskAnswer,
      maxLines: 5,
      validate: (v) =>
          v.trim().isEmpty ? l.teamProjectTaskReasonRequired : null,
      onSubmit: (v) async {
        final result = await _run(
          p,
          TeamProjectAction.answerRequest,
          target: request.id,
          text: v.trim(),
        );
        return result.accepted ? null : l.teamProjectTaskSaveFailed;
      },
    );
  }

  Future<void> _ignore(TeamProject p) async {
    final selected = _selected
        .where(
          (id) => p.tasks.any(
            (task) =>
                task.id == widget.taskId &&
                task.findings.any((f) => f.id == id && f.status == 'open'),
          ),
        )
        .single;
    await showKitInputDialog(
      context,
      title: l.teamProjectTaskIgnore,
      label: l.teamProjectTaskIgnoreReason,
      confirmLabel: l.teamProjectTaskIgnore,
      maxLines: 4,
      validate: (v) =>
          v.trim().isEmpty ? l.teamProjectTaskReasonRequired : null,
      onSubmit: (v) async {
        final result = await _run(
          p,
          TeamProjectAction.ignoreFinding,
          findingIds: [selected],
          text: v.trim(),
        );
        return result.accepted ? null : l.teamProjectTaskSaveFailed;
      },
    );
  }

  List<KitAction> _requestActions(
    TeamProject p,
    TeamTask task,
    TeamRequest request,
  ) {
    switch (request.kind) {
      case 'question':
      case 'permission':
        return [_action(l.teamProjectTaskAnswer, () => _answer(p, request))];
      case 'interrupted':
        return [
          ?_gated(
            TeamExecutionNeed.lanes,
            l.teamProjectTaskResume,
            () => _run(p, TeamProjectAction.resumeTask),
          ),
        ];
      case 'stalled':
      case 'failed':
        return [
          ?_gated(
            TeamExecutionNeed.lanes,
            l.teamProjectTaskRestart,
            () => _run(p, TeamProjectAction.restartTask),
          ),
        ];
      case 'findings':
        return [
          _action(
            l.teamProjectTaskReviewFindings,
            () => setState(() {
              _selected.addAll(
                task.findings.where((f) => f.status == 'open').map((f) => f.id),
              );
            }),
          ),
        ];
      case 'conflict':
        final item = p.mergeQueue
            .where(
              (m) =>
                  m.taskId == task.id &&
                  const ['conflict', 'manual'].contains(m.status),
            )
            .firstOrNull;
        if (item == null) return [];
        return [
          _action(
            l.teamProjectTaskResolveAgent,
            () => _run(
              p,
              TeamProjectAction.resolveConflict,
              target: item.id,
              text: 'agent',
            ),
          ),
          _action(
            l.teamProjectTaskResolveManually,
            () => _run(
              p,
              TeamProjectAction.resolveConflict,
              target: item.id,
              text: 'manual',
            ),
          ),
          if (item.reason == 'Waiting for your conflict resolution')
            _action(
              l.teamProjectTaskRecheckResolution,
              () => _run(
                p,
                TeamProjectAction.resolveConflict,
                target: item.id,
                text: 'recheck',
              ),
            ),
        ];
      default:
        return [];
    }
  }

  Future<void> _merge(TeamProject p, TeamRepo? repo) async {
    if (repo == null) return;
    await confirmAndMergeToDev(context, c, p, repo);
  }

  Future<void> _promote(TeamProject p, TeamRepo repo) =>
      confirmAndPromote(context, c, p, repo);

  Future<void> _chooseServer(TeamProject p, TeamTask task) async {
    final servers = c.snapshot?.servers ?? const <TeamServer>[];
    final target = await showKitChoiceSheet<String>(
      context,
      title: l.teamProjectPlanServerTitle(task.title),
      choices: [for (final s in servers) KitChoice(value: s.id, title: s.name)],
      selected: task.serverId,
    );
    if (target == null || target == task.serverId || !mounted) return;
    await _run(
      p,
      TeamProjectAction.moveTask,
      target: task.id,
      serverId: target,
    );
  }

  Future<void> _stop(TeamProject p) async {
    final yes = await showKitConfirm(
      context,
      title: l.teamProjectTaskStopConfirmTitle,
      body: l.teamProjectTaskStopBody,
      confirmLabel: l.teamProjectTaskStop,
      kind: KitConfirmKind.stop,
    );
    if (yes && mounted) {
      await _run(p, TeamProjectAction.stopTask, confirmed: true);
    }
  }

  Future<void> _send(TeamProject p) async {
    final text = _message.text;
    final sentTask = (widget.projectId, widget.taskId);
    final sentController = c;
    if (text.trim().isEmpty || c.busy) return;
    final result = await _run(p, TeamProjectAction.messageTask, text: text);
    if (!mounted || !result.accepted || c != sentController) return;
    if (sentTask == (widget.projectId, widget.taskId)) {
      if (_message.text == text) _message.clear();
    } else if (_drafts[sentTask] == text) {
      _drafts.remove(sentTask);
    }
  }

  Widget _fold(String id, String title, List<Widget> children) => KitExpandRow(
    title: title,
    expanded: _expanded.contains(id),
    children: children,
    onExpansionChanged: (value) => setState(() {
      if (value) {
        _expanded.add(id);
      } else {
        _expanded.remove(id);
      }
    }),
  );

  String _findingsTitle(TeamTask t) {
    final open = t.findings.where((f) => f.status == 'open').toList();
    int n(String s) => open.where((f) => f.severity == s).length;
    final checker = c.snapshot?.roles.where((r) => r.readOnly).firstOrNull;
    final parts = [
      if (n('critical') > 0) l.teamProjectFindingsCritical(n('critical')),
      if (n('major') > 0) l.teamProjectFindingsMajor(n('major')),
      if (n('minor') > 0) l.teamProjectFindingsMinor(n('minor')),
    ];
    if (checker == null || parts.isEmpty) return l.teamProjectTaskFindings;
    return l.teamProjectFindingsTitle(checker.name, parts.join(' · '));
  }

  Widget _planCard(TeamProject p, TeamTask t, TeamPhase? phase) {
    final milestones = p.specDraft.milestones;
    final mIndex = phase == null
        ? -1
        : milestones.indexWhere((m) => m.id == phase.milestoneId);
    final phases = p.phases
        .where((f) => phase == null || f.milestoneId == phase.milestoneId)
        .toList();
    final ordered = <TeamTask>[
      for (final f in phases) ...p.tasks.where((task) => task.phaseId == f.id),
    ];
    var shown = 0;
    const limit = 4;
    final movable =
        (c.snapshot?.servers.length ?? 0) > 1 &&
        TeamExecutionGate.allows(c, TeamExecutionNeed.placement);
    final cards = <KitPlanPhase>[];
    for (var i = 0; i < phases.length; i++) {
      final tasks = <KitPlanTask>[];
      for (final task in p.tasks.where((x) => x.phaseId == phases[i].id)) {
        if (shown >= limit) break;
        shown++;
        final number = ordered.indexOf(task) + 1;
        final after = task.dependsOn
            .map((id) => ordered.indexWhere((o) => o.id == id) + 1)
            .where((n) => n > 0)
            .firstOrNull;
        final repo = p.repos.where((r) => r.id == task.repoId).firstOrNull;
        final role = c.snapshot?.roles
            .where((r) => r.id == task.roleId)
            .firstOrNull;
        final server = c.snapshot?.servers
            .where((s) => s.id == task.serverId)
            .firstOrNull;
        final canMove = movable && server != null;
        tasks.add(
          KitPlanTask(
            number: number,
            title: task.title,
            detail: [
              if (repo != null) l.teamProjectPlanRepo(repo.name),
              if (after != null) l.teamProjectPlanAfter(after),
              l.teamProjectPlanCriteria(task.criteria.length),
            ].join(' · '),
            who: [
              if (role != null) role.name,
              if (server != null) server.name,
            ].join(' · '),
            onChangeWho: canMove ? () => _chooseServer(p, task) : null,
          ),
        );
      }
      if (tasks.isNotEmpty) {
        cards.add(
          KitPlanPhase(
            title: l.teamProjectPlanPhase(i + 1, phases[i].title),
            flagLabel: phases[i].risky ? l.teamProjectPlanReviewPoint : null,
            tasks: tasks,
          ),
        );
      }
    }
    final repos = {for (final o in ordered) o.repoId}.length;
    final rest = ordered.length - shown;
    return KitPlanCard(
      title: mIndex < 0
          ? l.teamProjectPlanForProject
          : l.teamProjectPlanFor(mIndex + 1),
      status: l.teamProjectPlanSummary(phases.length, ordered.length, repos),
      state: KitTeamState.needsYou,
      summary:
          (c.snapshot?.servers.length ?? 0) > 1 &&
              !TeamExecutionGate.allows(c, TeamExecutionNeed.placement)
          ? l.teamProjectPlanServerFixed
          : null,
      phases: cards,
      more: rest > 0 ? l.teamProjectPlanMore(rest) : null,
      primary: widget.embedded
          ? null
          : _gated(
              TeamExecutionNeed.lanes,
              l.teamProjectTaskApprovePlan,
              () => _run(p, TeamProjectAction.approvePlan),
            ),
      actions: [
        if (widget.embedded)
          ?_gated(
            TeamExecutionNeed.lanes,
            l.teamProjectTaskApprovePlan,
            () => _run(p, TeamProjectAction.approvePlan),
          ),
        _action(
          l.teamProjectPlanEdit,
          () => openTeamPlanEditor(context, c, p.id),
        ),
        _action(l.teamProjectPlanAsk, _focus.requestFocus),
      ],
      secondary: widget.embedded
          ? null
          : _action(
              l.teamProjectPlanNotYet,
              () => Navigator.of(context).maybePop(),
            ),
    );
  }

  Widget _promoteCard(TeamProject p, TeamTask t, TeamRepo repo) {
    final phase = p.phases.where((f) => f.id == t.phaseId).firstOrNull;
    final milestones = p.specDraft.milestones;
    final mIndex = phase == null
        ? -1
        : milestones.indexWhere((m) => m.id == phase.milestoneId);
    final phaseIds = {
      for (final f in p.phases.where(
        (f) => phase == null || f.milestoneId == phase.milestoneId,
      ))
        f.id,
    };
    final tasks = p.tasks.where((x) => phaseIds.contains(x.phaseId)).toList();
    final merged = tasks.where((x) => x.status == 'merged').length;
    final started = p.timeline
        .map((e) => DateTime.tryParse(e.at))
        .whereType<DateTime>()
        .fold<DateTime?>(null, (a, b) => a == null || b.isBefore(a) ? b : a);
    final days = started == null
        ? null
        : DateTime.now().difference(started).inDays;
    final repoTasks = p.tasks.where((x) => x.repoId == repo.id).toList();
    final commits = p.mergeQueue
        .where((m) => m.repoId == repo.id && m.status == 'merged')
        .length;
    final files = repoTasks
        .map((x) => RegExp('^diff --git', multiLine: true).allMatches(x.diff))
        .fold<int>(0, (a, b) => a + b.length);
    final firstDiff = repoTasks.where((x) => x.diff.isNotEmpty).firstOrNull;
    String short(String v) => v.length > 7 ? v.substring(0, 7) : v;
    final reviewed = phase?.accepted ?? false;
    return KitPromoteCard(
      title: l.teamProjectPromoteTitle,
      status: l.teamProjectPromoteStatus(repo.name),
      state: KitTeamState.needsYou,
      items: [
        KitTeamItem(
          title: mIndex < 0
              ? p.name
              : l.teamProjectPromoteMilestone(
                  mIndex + 1,
                  milestones[mIndex].title,
                ),
          detail: [
            l.teamProjectPromoteMerged(merged, tasks.length),
            if (days != null && days > 0) l.teamProjectPromoteDays(days),
          ].join(' · '),
        ),
        KitTeamItem(
          title: l.teamProjectPromoteChecks,
          detail: l.teamProjectPromoteChecksPassed,
        ),
        KitTeamItem(
          title: l.teamProjectPromoteReview,
          detail: reviewed && mIndex >= 0
              ? l.teamProjectPromoteAccepted(mIndex + 1)
              : l.teamProjectPromoteNoReview,
        ),
        KitTeamItem(
          title: [
            l.teamProjectPromoteChanges(commits),
            if (files > 0) l.teamProjectPromoteFiles(files),
          ].join(' · '),
          detail: '${short(repo.mainCommit)} → ${short(repo.devCommit)}',
          meta: firstDiff == null ? null : l.teamProjectPromoteSeeChanges,
          onPressed: firstDiff == null
              ? null
              : () => showKitDiff(
                  context,
                  title: firstDiff.title,
                  files: [
                    for (final x in repoTasks.where((x) => x.diff.isNotEmpty))
                      KitDiffFile.fromPatch(x.title, x.diff),
                  ],
                ),
        ),
      ],
      primary: widget.embedded
          ? null
          : _gated(
              TeamExecutionNeed.promotion,
              l.teamProjectTaskPromote,
              () => _promote(p, repo),
            ),
      actions: [
        if (widget.embedded)
          ?_gated(
            TeamExecutionNeed.promotion,
            l.teamProjectTaskPromote,
            () => _promote(p, repo),
          ),
        if (!widget.embedded)
          _action(
            l.teamProjectPromoteNotYet,
            () => Navigator.of(context).maybePop(),
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: c,
    builder: (context, _) {
      final p = c.snapshot?.projects
          .where((p) => p.id == widget.projectId)
          .firstOrNull;
      final t = p?.tasks.where((t) => t.id == widget.taskId).firstOrNull;
      if (p == null || t == null) {
        return KitScreen(
          loading: c.loading,
          body: KitStateView.error(
            title: l.teamProjectTaskMissing,
            retry: _action(l.teamProjectRefreshTask, c.load),
          ),
        );
      }
      final unavailable = c.errorCode == 'unavailable';
      final running = !unavailable && _state(t.status) == KitTeamState.running;
      final role = c.snapshot?.roles.where((r) => r.id == t.roleId).firstOrNull;
      final server = c.snapshot?.servers
          .where((s) => s.id == t.serverId)
          .firstOrNull;
      final requests = p.requests
          .where((r) => r.taskId == t.id && !r.answered)
          .toList();
      final phase = p.phases.where((f) => f.id == t.phaseId).firstOrNull;
      final repo = p.repos.where((r) => r.id == t.repoId).firstOrNull;
      final queue = p.mergeQueue.where((m) => m.taskId == t.id).toList();
      final events = p.timeline.where((e) => e.taskId == t.id).toList();
      final planning = p.status == 'plan' || p.status == 'planned';
      final canPromote = teamCanPromote(p, repo);
      final selected = _selected.intersection(
        t.findings.where((f) => f.status == 'open').map((f) => f.id).toSet(),
      );
      return KitScreen(
        width: widget.embedded ? KitScreenWidth.full : KitScreenWidth.reading,
        topBar: KitTopBar(
          title: t.title,
          subtitle: [
            if (role != null) role.name,
            if (server != null) server.name,
          ].join(' · '),
          menu: [
            if (p.simulated && queue.any((q) => q.status != 'merged'))
              KitMenuItem(
                label: l.teamProjectTaskDemoConflict,
                onSelected: () => _run(
                  p,
                  TeamProjectAction.simulateConflict,
                  target: queue.first.id,
                ),
              ),
            if (p.simulated && repo != null)
              KitMenuItem(
                label: l.teamProjectTaskDemoCommit,
                onSelected: () => _run(
                  p,
                  TeamProjectAction.simulateManualCommit,
                  target: repo.id,
                ),
              ),
          ],
          menuLabel: l.teamProjectTaskMenu,
        ),
        loading: c.loading,
        body: ListView(
          padding: KitScreen.padding(context),
          children:
              <Widget>[
                    if (unavailable)
                      KitMessage.notice(
                        text:
                            '${l.teamProjectTaskStale} · ${_age(p.updatedAt)}',
                      ),
                    if (!widget.embedded) TeamExecutionBlocked(controller: c),
                    if (c.errorCode != null)
                      KitStateView.error(
                        title: l.teamProjectTaskSaveFailed,
                        size: KitStateSize.inline,
                        secondary: _action(l.teamProjectRefreshTask, c.load),
                      ),
                    if (_expanded.isNotEmpty)
                      KitButton.tertiary(
                        label: l.teamProjectTaskCollapse,
                        onPressed: () => setState(_expanded.clear),
                      ),
                    if (t.messages.isEmpty && t.status == 'queued' && !planning)
                      KitMessage.notice(text: l.teamProjectTaskEmpty),
                    for (final m in t.messages)
                      if (m.actor == 'person')
                        KitMessage.prompt(
                          bubbleWidth: KitBubbleWidth.compact,
                          body: KitMarkdown(m.text, selectable: false),
                          time: DateTime.tryParse(m.at),
                        )
                      else if (m.actor == 'team')
                        KitMessage.thought(
                          heading: l.teamProjectTaskInstructions,
                          body: KitMarkdown(
                            m.text,
                            role: KitTextRole.secondary,
                          ),
                          expanded: _expanded.contains(m.id),
                          onExpansionChanged: (v) => setState(() {
                            if (v) {
                              _expanded.add(m.id);
                            } else {
                              _expanded.remove(m.id);
                            }
                          }),
                        )
                      else
                        KitMessage.reply(body: KitMarkdown(m.text)),
                    if (planning) _planCard(p, t, phase),
                    if (phase != null &&
                        (t.status == 'failed' ||
                            p.requests.any(
                              (r) => r.phaseId == phase.id && !r.answered,
                            ) ||
                            (!phase.accepted &&
                                p.tasks
                                    .where((task) => task.phaseId == phase.id)
                                    .every((task) => task.status == 'merged'))))
                      KitPhaseCard(
                        title: phase.title,
                        status: phase.accepted
                            ? l.teamProjectTaskAccepted
                            : t.status == 'failed'
                            ? l.teamProjectTaskFailed
                            : l.teamProjectTaskCriteria,
                        state: t.status == 'failed'
                            ? KitTeamState.failed
                            : p.requests.any(
                                (r) => r.phaseId == phase.id && !r.answered,
                              )
                            ? KitTeamState.needsYou
                            : KitTeamState.done,
                        items: [
                          KitTeamItem(
                            title: t.title,
                            state: const ['done', 'merged'].contains(t.status)
                                ? KitTeamState.done
                                : KitTeamState.empty,
                          ),
                        ],
                        actions: [
                          if (!phase.accepted &&
                              p.tasks
                                  .where((task) => task.phaseId == phase.id)
                                  .isNotEmpty &&
                              p.tasks
                                  .where((task) => task.phaseId == phase.id)
                                  .every((task) => task.status == 'merged'))
                            _action(
                              l.teamProjectTaskAcceptPhase,
                              () => _run(
                                p,
                                TeamProjectAction.acceptPhase,
                                target: phase.id,
                              ),
                            ),
                        ],
                      ),
                    for (final request in requests)
                      KitPhaseCard(
                        title: request.title,
                        status: _age(request.createdAt),
                        state: KitTeamState.needsYou,
                        actions: _requestActions(p, t, request),
                      ),
                    if (events.isNotEmpty)
                      _fold(
                        'work',
                        running
                            ? l.teamProjectTaskWorkLive
                            : const [
                                'done',
                                'merged',
                                'accepted',
                              ].contains(t.status)
                            ? l.teamProjectTaskWork
                            : l.teamProjectTaskWorkLog,
                        [
                          for (final event
                              in (running
                                  ? events.reversed.take(3).toList().reversed
                                  : events))
                            KitText(
                              '${teamTimelineWords(l, event.text)} · ${_age(event.at)}',
                            ),
                        ],
                      ),
                    if (t.criterionResults.isNotEmpty)
                      KitPhaseCard(
                        title: l.teamProjectTaskCriteria,
                        status: l.teamProjectTaskVerificationResults,
                        state: KitTeamState.done,
                        items: [
                          for (final result in t.criterionResults)
                            KitTeamItem(
                              title: result.criterion,
                              detail: switch (result.status) {
                                'met' => l.teamProjectTaskCriterionMet,
                                'unmet' => l.teamProjectTaskCriterionUnmet,
                                _ => l.teamProjectTaskCriterionNotApplicable,
                              },
                              state: result.status == 'unmet'
                                  ? KitTeamState.failed
                                  : KitTeamState.done,
                            ),
                        ],
                      ),
                    if (t.findings.isNotEmpty)
                      KitFindingsCard(
                        title: _findingsTitle(t),
                        status: t.findings.any((f) => f.status == 'open')
                            ? l.teamProjectTaskOpenFindings
                            : l.teamProjectTaskFindingsAddressed,
                        state:
                            t.findings.any((f) => f.status == 'open') &&
                                t.status == 'review'
                            ? KitTeamState.needsYou
                            : KitTeamState.done,
                        findings: [
                          for (final f in t.findings)
                            KitTeamFinding(
                              id: f.id,
                              title: f.text,
                              detail: f.criterion,
                              location: f.location,
                              severity: switch (f.severity) {
                                'critical' => KitFindingSeverity.critical,
                                'minor' => KitFindingSeverity.minor,
                                _ => KitFindingSeverity.major,
                              },
                              severityLabel: switch (f.severity) {
                                'critical' => l.teamProjectTaskCritical,
                                'minor' => l.teamProjectTaskMinor,
                                _ => l.teamProjectTaskMajor,
                              },
                              selected: selected.contains(f.id),
                              onChanged: f.status == 'open'
                                  ? (v) => setState(() {
                                      if (v) {
                                        _selected.add(f.id);
                                      } else {
                                        _selected.remove(f.id);
                                      }
                                    })
                                  : null,
                            ),
                        ],
                        primary: selected.isEmpty
                            ? null
                            : _gated(
                                TeamExecutionNeed.verification,
                                l.teamProjectTaskFix,
                                () => _run(
                                  p,
                                  TeamProjectAction.fixFindings,
                                  findingIds: selected.toList(),
                                ),
                              ),
                        actions: [
                          ?_gated(
                            TeamExecutionNeed.verification,
                            l.teamProjectTaskRecheck,
                            () => _run(p, TeamProjectAction.recheckTask),
                          ),
                          if (selected.length == 1)
                            _action(l.teamProjectTaskIgnore, () => _ignore(p)),
                        ],
                      ),
                    if (queue.any((q) => q.status != 'merged'))
                      KitMergeQueue(
                        title: l.teamProjectTaskMerge,
                        status: _status(queue.first.status),
                        state: _state(queue.first.status),
                        items: [
                          for (final q in queue)
                            KitTeamItem(
                              title: t.title,
                              detail: q.reason,
                              meta: _status(q.status),
                            ),
                        ],
                        actions: [
                          ?_gated(
                            TeamExecutionNeed.mergeQueue,
                            l.teamProjectTaskMergeRun,
                            () => _merge(p, repo),
                          ),
                        ],
                      ),
                    for (final receipt
                        in p.receipts
                            .where(
                              (r) => r.repoId == t.repoId && r.kind == 'merge',
                            )
                            .toList()
                            .reversed
                            .take(3)
                            .toList()
                            .reversed)
                      KitPhaseCard(
                        title: l.teamProjectReceiptMerged(repo?.name ?? ''),
                        status: _age(receipt.at),
                        state: KitTeamState.done,
                        summary: teamCommitChange(
                          receipt.before,
                          receipt.after,
                        ),
                      ),
                    if (canPromote) _promoteCard(p, t, repo!),
                    for (final receipt in p.receipts.where(
                      (r) =>
                          r.repoId == t.repoId &&
                          const ['promote', 'promotion'].contains(r.kind),
                    ))
                      KitPhaseCard(
                        title: l.teamProjectTaskReceipt,
                        status: _age(receipt.at),
                        state: KitTeamState.done,
                        summary: '${receipt.before} → ${receipt.after}',
                      ),
                    KitActionBlock(
                      tertiary: [
                        if (t.diff.isNotEmpty && !canPromote)
                          _action(
                            l.teamProjectTaskDiff,
                            () => showKitDiff(
                              context,
                              title: t.title,
                              files: [KitDiffFile.fromPatch(t.title, t.diff)],
                            ),
                          ),
                        if (running)
                          _action(
                            l.teamProjectTaskPause,
                            () => _run(p, TeamProjectAction.pauseTask),
                          ),
                        if (t.status == 'paused' || t.status == 'interrupted')
                          ?_gated(
                            t.status == 'interrupted'
                                ? TeamExecutionNeed.resume
                                : TeamExecutionNeed.lanes,
                            l.teamProjectTaskResume,
                            () => _run(p, TeamProjectAction.resumeTask),
                          ),
                        if (t.status == 'failed' || t.status == 'stopped')
                          ?_gated(
                            TeamExecutionNeed.lanes,
                            l.teamProjectTaskRestart,
                            () => _run(p, TeamProjectAction.restartTask),
                          ),
                        if (t.status == 'done')
                          ?_gated(
                            TeamExecutionNeed.verification,
                            l.teamProjectTaskVerify,
                            () => _run(p, TeamProjectAction.verifyTask),
                          ),
                        if (running ||
                            t.status == 'paused' ||
                            requests.isNotEmpty)
                          _action(
                            l.teamProjectTaskStop,
                            () => _stop(p),
                            destructive: true,
                          ),
                      ],
                    ),
                    if (t.branch.isNotEmpty) KitDetailsFold(text: t.branch),
                  ]
                  .expand(
                    (child) => <Widget>[
                      child,
                      if (child is KitPlanCard ||
                          child is KitPhaseCard ||
                          child is KitFindingsCard ||
                          child is KitMergeQueue ||
                          child is KitPromoteCard)
                        SizedBox(height: KitTokens.of(context).space3),
                    ],
                  )
                  .toList(),
        ),
        bottom: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            KitComposer(
              controller: _message,
              focusNode: _focus,
              hint: l.teamProjectTaskMessage,
              fieldLabel: l.teamProjectTaskMessage,
              onSend: () => _send(p),
              busy: running,
              sending: c.busy,
              canSendWhileBusy: true,
              onStop: running && !c.busy ? () => _stop(p) : null,
            ),
          ],
        ),
      );
    },
  );
}
