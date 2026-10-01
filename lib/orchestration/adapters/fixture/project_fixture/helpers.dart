part of '../project_fixture_gateway.dart';

extension _ProjectFixtureHelpers on ProjectFixtureGateway {
  String _actionText(TeamProjectAction action) => switch (action) {
    TeamProjectAction.simulatePlanFailure =>
      'Simulated an unstructured planner reply',
    TeamProjectAction.usePlanAsTask => 'Planner notes converted into one task',
    TeamProjectAction.retryPlan => 'A new structured plan was requested',
    TeamProjectAction.simulateManualCommit =>
      'Simulated a manual commit on dev',
    TeamProjectAction.simulateConflict => 'Simulated conflicting changes',
    TeamProjectAction.saveSpecDraft => 'Spec draft saved',
    TeamProjectAction.approveSpec => 'Spec approved',
    TeamProjectAction.approvePlan => 'Plan approved',
    TeamProjectAction.replan => 'Affected tasks replanned',
    TeamProjectAction.answerRequest => 'Question answered',
    TeamProjectAction.messageTask => 'Message sent to the task',
    TeamProjectAction.pauseProject => 'Project paused',
    TeamProjectAction.resumeProject => 'Project continued',
    TeamProjectAction.stopProject => 'Project stopped',
    TeamProjectAction.pauseTask => 'Task paused',
    TeamProjectAction.resumeTask => 'Task continued from its branch',
    TeamProjectAction.restartTask => 'Task restarted from its branch',
    TeamProjectAction.stopTask => 'Task stopped',
    TeamProjectAction.moveTask => 'Task handed over',
    TeamProjectAction.verifyTask => 'Fresh acceptance check completed',
    TeamProjectAction.fixFindings => 'Selected findings fixed',
    TeamProjectAction.recheckTask => 'Earlier findings checked again',
    TeamProjectAction.ignoreFinding => 'Findings ignored with a reason',
    TeamProjectAction.updateSettings => 'Project settings updated',
    TeamProjectAction.processMergeQueue => 'Local merge queue checked',
    TeamProjectAction.resolveConflict => 'Simulated conflict resolved',
    TeamProjectAction.promote => 'Development changes promoted to main',
    TeamProjectAction.acknowledgeDigest => 'Activity digest read',
    TeamProjectAction.acceptPhase => 'Phase review accepted',
    TeamProjectAction.acceptMilestone => 'Milestone accepted',
    TeamProjectAction.advance => 'Simulated work advanced',
    TeamProjectAction.undoMerge => 'A reverting commit was added',
    TeamProjectAction.requestSpecChange => 'Spec change proposed for review',
    _ => 'Project updated',
  };

  TeamProject _syncRequests(TeamProject p) {
    p = p.copyWith(
      phases: p.phases.map((phase) {
        final tasks = p.tasks.where((t) => t.phaseId == phase.id);
        return !phase.risky &&
                p.settings.reviewLevel != 'everyStep' &&
                tasks.isNotEmpty &&
                tasks.every((t) => t.status == 'merged')
            ? phase.copyWith(accepted: true)
            : phase;
      }).toList(),
    );
    final persistent = p.requests
        .where(
          (r) =>
              r.kind == 'question' ||
              r.kind == 'permission' ||
              r.answered ||
              (r.kind == 'budget' && p.status == 'paused'),
        )
        .toList();
    void add(
      String id,
      String kind,
      String title, {
      String taskId = '',
      String phaseId = '',
    }) {
      final old = p.requests.where((r) => r.id == id).firstOrNull;
      persistent.add(
        TeamRequest(
          id: id,
          kind: kind,
          title: title,
          taskId: taskId,
          phaseId: phaseId,
          createdAt: old?.createdAt ?? _at,
        ),
      );
    }

    if (p.status == 'spec' ||
        (p.specVersions.isNotEmpty && p.specDraft.approvedAt.isEmpty)) {
      add('${p.id}-spec', 'spec', 'Review and approve the living spec');
    }
    if (!persistent.any((r) => r.kind == 'budget') &&
        p.status == 'paused' &&
        p.tasks.any((t) => t.reason == 'Budget reached')) {
      add(
        '${p.id}-budget',
        'budget',
        'Budget reached. Raise the budget or pause the project.',
      );
    }
    if (p.status == 'planFailed') {
      add(
        '${p.id}-plan-format',
        'planFormat',
        'The planner returned notes instead of a structured plan',
      );
    }
    if (p.status == 'plan') {
      add('${p.id}-plan', 'plan', 'Review and approve the plan');
    }
    for (final t in p.tasks) {
      if (const ['stalled', 'failed', 'interrupted'].contains(t.status)) {
        add('recover-${t.id}', t.status, t.reason, taskId: t.id);
      }
      if (t.status == 'findings') {
        add(
          'findings-${t.id}',
          'findings',
          'Review the findings',
          taskId: t.id,
        );
      }
    }
    for (final phase in p.phases) {
      final tasks = p.tasks.where((t) => t.phaseId == phase.id);
      if (!phase.accepted &&
          (phase.risky || p.settings.reviewLevel == 'everyStep') &&
          tasks.isNotEmpty &&
          tasks.every((t) => t.status == 'merged')) {
        add(
          'phase-${phase.id}',
          'phase',
          'Review ${phase.title}',
          phaseId: phase.id,
        );
      }
    }
    for (final milestone in p.specDraft.milestones) {
      final phases = p.phases.where(
        (phase) => phase.milestoneId == milestone.id,
      );
      if (!milestone.accepted &&
          phases.isNotEmpty &&
          phases.every((phase) => phase.accepted)) {
        add(
          'milestone-${milestone.id}',
          'milestone',
          'Review ${milestone.title}',
          phaseId: milestone.id,
        );
      }
    }
    for (final item in p.mergeQueue.where((m) => m.status == 'conflict')) {
      add('conflict-${item.id}', 'conflict', item.reason, taskId: item.taskId);
    }
    return p.copyWith(requests: persistent);
  }

  void _validateSettings(TeamProjectSettings? s) {
    if (s == null ||
        !const ['single', 'parallel'].contains(s.mode) ||
        s.maxLanes < 1 ||
        s.maxLanes > 32) {
      _fail<void>('chooseExecutionMode');
    }
    final b = s.budget;
    if (!b.chosen ||
        (!b.unlimited && b.daily == null && b.total == null) ||
        (b.daily != null && (!b.daily!.isFinite || b.daily! <= 0)) ||
        (b.total != null && (!b.total!.isFinite || b.total! <= 0)) ||
        (b.taskTokens != null && b.taskTokens! <= 0)) {
      _fail<void>('chooseBudget');
    }
    if (!const ['milestones', 'everyStep'].contains(s.reviewLevel)) {
      _fail<void>('invalidReviewLevel');
    }
    if (s.maxFixRounds < 0 || s.maxFixRounds > 3) {
      _fail<void>('invalidFixRounds');
    }
  }

  void _validatePlan(TeamProject p, TeamWorkspace w) {
    if (p.tasks.isEmpty ||
        p.tasks.map((t) => t.id).toSet().length != p.tasks.length) {
      _fail<void>('invalidPlan');
    }
    final visited = <String>{}, visiting = <String>{};
    void visit(TeamTask t) {
      if (visiting.contains(t.id)) _fail<void>('dependencyCycle');
      if (visited.contains(t.id)) return;
      visiting.add(t.id);
      for (final id in t.dependsOn) {
        final dep = p.tasks.where((t) => t.id == id).firstOrNull;
        if (dep == null) _fail<void>('missingDependency');
        visit(dep);
      }
      visiting.remove(t.id);
      visited.add(t.id);
    }

    for (final t in p.tasks) {
      if (t.title.trim().isEmpty ||
          t.criteria.isEmpty ||
          !p.repos.any((r) => r.id == t.repoId) ||
          !w.servers.any((s) => s.id == t.serverId) ||
          !w.roles.any((r) => r.id == t.roleId) ||
          !p.phases.any((phase) => phase.id == t.phaseId)) {
        _fail<void>('invalidPlan');
      }
      visit(t);
    }
  }

  TeamProject _log(
    TeamProject p,
    String kind,
    String text, {
    String actor = 'person',
  }) => p.copyWith(
    timeline: [
      ...p.timeline,
      TeamTimelineEvent(
        id: 'event-${p.timeline.length + 1}',
        kind: kind,
        text: text,
        actor: actor,
        at: _at,
      ),
    ],
  );
  TeamTask _task(
    TeamTask t,
    TeamProjectCommand c,
    TeamProject p,
    TeamWorkspace w,
  ) {
    if (t.status == 'merged' && c.action != TeamProjectAction.messageTask) {
      return _fail('taskAlreadyDone');
    }
    switch (c.action) {
      case TeamProjectAction.messageTask:
        if (c.text.trim().isEmpty) return _fail('emptyMessage');
        return t.copyWith(
          messages: [
            ...t.messages,
            TeamMessage(id: c.requestId, text: c.text, at: _at),
          ],
        );
      case TeamProjectAction.pauseTask:
        return t.copyWith(
          status: 'paused',
          reason: 'Paused by you',
          changedAt: _at,
        );
      case TeamProjectAction.stopTask:
        return t.copyWith(
          status: 'stopped',
          reason: 'Stopped by you',
          changedAt: _at,
        );
      case TeamProjectAction.resumeTask:
      case TeamProjectAction.restartTask:
        if (const ['merged', 'done'].contains(t.status)) {
          return _fail('taskAlreadyDone');
        }
        return t.copyWith(
          status: 'queued',
          reason: 'Continue from the saved branch',
          changedAt: _at,
        );
      case TeamProjectAction.moveTask:
        if (!w.servers.any((s) => s.id == c.serverId)) {
          return _fail('serverNotFound');
        }
        final repo = p.repos.firstWhere((r) => r.id == t.repoId);
        if (c.roleId.isNotEmpty && !w.roles.any((r) => r.id == c.roleId)) {
          return _fail('roleNotFound');
        }
        if ((t.status == 'running' || t.steps > 0) && !repo.sharedRemote) {
          if (!c.confirmed) return _fail('sharedRemoteRequired');
          return t.copyWith(
            serverId: c.serverId,
            roleId: c.roleId.isEmpty ? t.roleId : c.roleId,
            status: 'queued',
            steps: 0,
            tokens: 0,
            fixRounds: 0,
            findings: [],
            criterionResults: [],
            diff: '',
            branch: '${t.branch}-restart-${p.revision + 1}',
            changedAt: _at,
            reason: 'Started over on the selected server',
            messages: [
              TeamMessage(
                id: c.requestId,
                actor: 'team',
                text:
                    'Explicitly started over elsewhere. Previous work remains on ${t.branch}. Begin again from the task criteria.',
                at: _at,
              ),
            ],
          );
        }
        return t.copyWith(
          serverId: c.serverId,
          roleId: c.roleId.isEmpty ? t.roleId : c.roleId,
          messages: [
            ...t.messages,
            TeamMessage(
              id: c.requestId,
              actor: 'team',
              text:
                  'Hand-off: ${t.title}. Continue from ${t.branch}. ${t.findings.where((f) => f.status == "open").length} open findings.',
              at: _at,
            ),
          ],
        );
      case TeamProjectAction.verifyTask:
      case TeamProjectAction.recheckTask:
        if (!const ['review', 'done', 'findings'].contains(t.status)) {
          return _fail('taskNotReady');
        }
        final fresh = c.action == TeamProjectAction.verifyTask;
        final findings = fresh && t.fixRounds == 0
            ? [
                TeamFinding(
                  id: 'finding-${t.id}',
                  criterion: t.criteria.first,
                  location: 'lib/example.dart:12',
                  text: 'The empty state needs an acceptance check.',
                ),
              ]
            : t.findings;
        final open = findings.any(
          (f) => f.status == 'open' && f.severity != 'notApplicable',
        );
        return t.copyWith(
          findings: findings,
          criterionResults: t.criteria
              .map(
                (criterion) => TeamCriterionResult(
                  criterion: criterion,
                  status:
                      findings.any(
                        (f) =>
                            f.criterion == criterion &&
                            f.status == 'open' &&
                            f.severity != 'notApplicable',
                      )
                      ? 'unmet'
                      : findings.any(
                          (f) =>
                              f.criterion == criterion &&
                              f.severity == 'notApplicable',
                        )
                      ? 'notApplicable'
                      : 'met',
                ),
              )
              .toList(),
          status: open ? 'findings' : 'done',
          reason: open ? 'Review the findings' : 'Checks passed',
          changedAt: _at,
        );
      case TeamProjectAction.fixFindings:
        if (!t.findings.any(
          (f) => f.status == 'open' && f.severity != 'notApplicable',
        )) {
          return _fail('noOpenFindings');
        }
        return t.copyWith(
          findings: t.findings
              .map(
                (f) => c.findingIds.isEmpty || c.findingIds.contains(f.id)
                    ? f.copyWith(status: 'resolved')
                    : f,
              )
              .toList(),
          fixRounds: t.fixRounds + 1,
          status: 'review',
          reason: 'Fix applied; re-check the criteria',
          changedAt: _at,
        );
      case TeamProjectAction.ignoreFinding:
        if (c.text.trim().isEmpty) return _fail('reasonRequired');
        return t.copyWith(
          findings: t.findings
              .map(
                (f) =>
                    f.status == 'open' &&
                        (c.findingIds.isEmpty || c.findingIds.contains(f.id))
                    ? f.copyWith(status: 'ignored')
                    : f,
              )
              .toList(),
          status: 'review',
        );
      default:
        return _fail('invalidTaskAction');
    }
  }

  TeamProject _plan(TeamProject p) {
    final milestones = p.specDraft.milestones.isEmpty
        ? [
            const TeamMilestone(
              id: 'milestone-1',
              title: 'First usable milestone',
              criteria: ['The complete journey works'],
            ),
          ]
        : p.specDraft.milestones;
    final phases = <TeamPhase>[];
    final tasks = <TeamTask>[];
    var previousMilestoneTasks = <String>[];
    for (final m in milestones) {
      final currentMilestoneTasks = <String>[];
      final phaseId = '${p.id}-${m.id}-build';
      phases.add(
        TeamPhase(
          id: phaseId,
          milestoneId: m.id,
          title: 'Build ${m.title}',
          risky: true,
        ),
      );
      for (var i = 0; i < p.repos.length; i++) {
        final repo = p.repos[i];
        final taskId = '${p.id}-${m.id}-${repo.id}';
        currentMilestoneTasks.add(taskId);
        tasks.add(
          TeamTask(
            id: taskId,
            title: 'Deliver ${m.title} in ${repo.name}',
            phaseId: phaseId,
            dependsOn: previousMilestoneTasks,
            roleId: i.isEven ? 'frontend' : 'backend',
            repoId: repo.id,
            serverId: repo.serverId,
            criteria: m.criteria.isEmpty
                ? ['The complete journey works']
                : m.criteria,
            branch: 'team/${p.id}/$taskId',
            changedAt: _at,
          ),
        );
      }
      previousMilestoneTasks = currentMilestoneTasks;
    }
    return p.copyWith(
      specDraft: p.specDraft.copyWith(milestones: milestones),
      phases: phases,
      tasks: p.quickTask ? tasks.take(1).toList() : tasks,
    );
  }

  TeamProject _budgetNotice(TeamProject p) {
    final b = p.settings.budget;
    final near =
        !b.unlimited &&
        ((b.total != null && p.spent >= b.total! * 0.8) ||
            (b.daily != null && p.spentToday >= b.daily! * 0.8));
    final day = _at.substring(0, 10);
    final key = 'budget80-$day';
    if (near && !p.timeline.any((e) => e.kind == key)) {
      p = _log(
        p,
        key,
        '80% of the project budget has been used',
        actor: 'fixture',
      );
    }
    return p.copyWith(budgetWarning: near);
  }
}
