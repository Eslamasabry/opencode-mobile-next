part of '../project_fixture_gateway.dart';

extension _ProjectFixtureApply on ProjectFixtureGateway {
  TeamWorkspace _apply(TeamWorkspace w, TeamProjectCommand c) {
    if (c.action == TeamProjectAction.updateDefaults) {
      _validateSettings(c.settings);
      return w.copyWith(revision: w.revision + 1, defaultSettings: c.settings);
    }
    if (c.action == TeamProjectAction.saveRole) {
      final role = c.role;
      if (role == null || role.id.isEmpty || role.name.trim().isEmpty) {
        return _fail('invalidRole');
      }
      if (role.id == 'checker' && !role.readOnly) {
        return _fail('checkerMustBeReadOnly');
      }
      return w.copyWith(
        revision: w.revision + 1,
        roles: [...w.roles.where((r) => r.id != role.id), role],
      );
    }
    if (c.action == TeamProjectAction.deleteRole) {
      if (w.projects.any((p) => p.tasks.any((t) => t.roleId == c.targetId))) {
        return _fail('roleInUse');
      }

      return w.copyWith(
        revision: w.revision + 1,
        roles: w.roles.where((r) => r.id != c.targetId).toList(),
      );
    }
    if (c.action == TeamProjectAction.updateServer) {
      final server = c.server;
      if (server == null || server.id.isEmpty || server.laneCap < 1) {
        return _fail('invalidServer');
      }
      return w.copyWith(
        revision: w.revision + 1,
        servers: [...w.servers.where((s) => s.id != server.id), server],
      );
    }
    if (c.action == TeamProjectAction.createProject ||
        c.action == TeamProjectAction.createQuickTask) {
      _validateSettings(c.settings);
      if (c.name.trim().isEmpty ||
          c.spec == null ||
          c.spec!.goal.trim().isEmpty ||
          c.repos.isEmpty) {
        return _fail('missingProjectDetails');
      }
      if (c.repos.any(
        (r) => r.id.isEmpty || !w.servers.any((s) => s.id == r.serverId),
      )) {
        return _fail('invalidPlacement');
      }
      final id = 'project-${w.revision + 1}';
      var p = TeamProject(
        id: id,
        name: c.name,
        settings: c.settings!,
        repos: c.repos,
        specDraft: c.spec!,
        updatedAt: _at,
        requests: [
          TeamRequest(
            id: '$id-question',
            title: 'What must the first milestone prove?',
            createdAt: _at,
          ),
        ],
      );
      p = p.copyWith(specDraft: _plan(p).specDraft);
      if (c.action == TeamProjectAction.createQuickTask) {
        if (c.repos.length != 1) return _fail('quickTaskNeedsOneRepo');
        if (c.roleId.isNotEmpty && !w.roles.any((r) => r.id == c.roleId)) {
          return _fail('roleNotFound');
        }
        if (c.serverId.isNotEmpty &&
            !w.servers.any((s) => s.id == c.serverId)) {
          return _fail('serverNotFound');
        }
        p = _plan(p).copyWith(
          status: c.confirmed ? 'running' : 'plan',
          planApproved: c.confirmed,
          quickTask: true,
          requests: [],
          specVersions: [
            c.spec!.copyWith(approvedBy: 'person', approvedAt: _at),
          ],
        );
        p = p.copyWith(
          tasks: [
            p.tasks.first.copyWith(
              title: c.spec!.goal,
              roleId: c.roleId.isEmpty ? p.tasks.first.roleId : c.roleId,
              serverId: c.serverId.isEmpty
                  ? p.tasks.first.serverId
                  : c.serverId,
            ),
          ],
        );
      }
      p = _syncRequests(p);
      p = _log(p, 'created', 'Project created with simulated work');
      return w.copyWith(revision: w.revision + 1, projects: [...w.projects, p]);
    }
    final foundProject = w.projects
        .where((p) => p.id == c.projectId)
        .firstOrNull;
    if (foundProject == null) return _fail('projectNotFound');
    TeamProject p = foundProject;
    if (c.expectedRevision != p.revision) return _fail('staleRevision');
    if (c.action == TeamProjectAction.deleteProject) {
      if (!c.confirmed) return _fail('confirmationRequired');
      return w.copyWith(
        revision: w.revision + 1,
        projects: w.projects.where((p) => p.id != c.projectId).toList(),
      );
    }
    final before = p;
    switch (c.action) {
      case TeamProjectAction.simulatePlanFailure:
        if (p.planApproved || p.specVersions.isEmpty) {
          return _fail('planSimulationUnavailable');
        }
        p = p.copyWith(status: 'planFailed');
      case TeamProjectAction.usePlanAsTask:
        if (p.status != 'planFailed') return _fail('planFallbackUnavailable');
        final planned = _plan(p);
        p = planned.copyWith(
          status: 'plan',
          quickTask: true,
          tasks: [planned.tasks.first.copyWith(title: p.specDraft.goal)],
        );
      case TeamProjectAction.retryPlan:
        if (p.status != 'planFailed') return _fail('planFallbackUnavailable');
        p = _plan(p).copyWith(status: 'plan');
      case TeamProjectAction.simulateManualCommit:
        final repo = p.repos.where((r) => r.id == c.targetId).firstOrNull;
        if (repo == null) return _fail('repoNotFound');
        final commit = c.text.trim().isEmpty
            ? 'fixture-person-${p.revision + 1}'
            : c.text.trim();
        p = p.copyWith(
          repos: p.repos
              .map((r) => r.id == repo.id ? r.copyWith(devCommit: commit) : r)
              .toList(),
          mergeQueue: p.mergeQueue
              .map(
                (m) =>
                    m.repoId == repo.id && m.status != 'merged' && c.confirmed
                    ? m.copyWith(
                        status: 'conflict',
                        reason: 'Changes on dev need a conflict resolution',
                      )
                    : m,
              )
              .toList(),
          receipts: [
            ...p.receipts,
            TeamProjectReceipt(
              id: c.requestId,
              kind: 'manualCommit',
              repoId: repo.id,
              before: repo.devCommit,
              after: commit,
              at: _at,
            ),
          ],
        );
        p = _log(
          p,
          c.confirmed ? 'conflict' : 'rebase',
          c.confirmed
              ? 'Manual changes preserved; resolve the conflicting edits'
              : 'Manual changes preserved; task branches rebased onto dev',
          actor: 'fixture',
        );
      case TeamProjectAction.simulateConflict:
        if (!p.mergeQueue.any(
          (m) => m.id == c.targetId && m.status != 'merged',
        )) {
          return _fail('queueItemNotFound');
        }
        p = p.copyWith(
          mergeQueue: p.mergeQueue
              .map(
                (m) => m.id == c.targetId
                    ? m.copyWith(
                        status: 'conflict',
                        reason: 'Simulated overlapping edits need a resolution',
                      )
                    : m,
              )
              .toList(),
        );
      case TeamProjectAction.requestSpecChange:
        if (c.text.trim().isEmpty) return _fail('emptyMessage');
        p = p.copyWith(
          specDraft: p.specDraft.copyWith(
            constraints:
                '${p.specDraft.constraints}\nProposed change: ${c.text}'.trim(),
            version: p.specVersions.length + 1,
            approvedBy: '',
            approvedAt: '',
          ),
          tasks: p.tasks
              .map((t) => t.copyWith(affected: t.status != 'merged'))
              .toList(),
        );
      case TeamProjectAction.saveSpecDraft:
        if (c.spec == null || c.spec!.goal.trim().isEmpty) {
          return _fail('invalidSpec');
        }
        p = p.copyWith(
          specDraft: c.spec!.copyWith(
            version: p.specVersions.length + 1,
            approvedBy: '',
            approvedAt: '',
          ),
          tasks: p.tasks
              .map((t) => t.copyWith(affected: t.status != 'merged'))
              .toList(),
        );
      case TeamProjectAction.approveSpec:
        if (p.requests.any((r) => !r.answered && r.kind == 'question')) {
          return _fail('answerQuestionsFirst');
        }
        final approved = p.specDraft.copyWith(
          version: p.specVersions.length + 1,
          approvedBy: 'person',
          approvedAt: _at,
        );
        final revised = p.copyWith(
          specDraft: approved,
          specVersions: [...p.specVersions, approved],
        );
        p = p.tasks.isEmpty ? _plan(revised.copyWith(status: 'plan')) : revised;
      case TeamProjectAction.approvePlan:
        if (p.specVersions.isEmpty) return _fail('approveSpecFirst');
        if (p.tasks.any((t) => t.status != 'queued') && c.tasks != null) {
          return _fail('planAlreadyRunning');
        }
        final edited = c.tasks;
        if (edited != null &&
            edited.any(
              (t) =>
                  t.status != 'queued' || t.steps != 0 || t.findings.isNotEmpty,
            )) {
          return _fail('invalidPlan');
        }
        p = p.copyWith(tasks: edited ?? p.tasks, phases: c.phases ?? p.phases);
        _validatePlan(p, w);
        p = p.copyWith(status: 'running', planApproved: true);
      case TeamProjectAction.replan:
        if (p.specVersions.isEmpty) return _fail('approveSpecFirst');
        final incoming = c.tasks ?? _plan(p).tasks;
        if (incoming.any(
          (t) => t.steps != 0 || t.findings.isNotEmpty || t.status != 'queued',
        )) {
          return _fail('invalidPlan');
        }
        if (c.phases != null) {
          p = p.copyWith(
            phases: p.phases
                .map(
                  (phase) =>
                      p.tasks.any(
                        (t) =>
                            t.phaseId == phase.id &&
                            (t.status != 'queued' || t.steps > 0),
                      )
                      ? phase
                      : c.phases!
                                .where((next) => next.id == phase.id)
                                .firstOrNull ??
                            phase,
                )
                .toList(),
          );
        }
        p = p.copyWith(
          tasks: p.tasks
              .map(
                (t) =>
                    t.affected &&
                        const [
                          'queued',
                          'paused',
                          'interrupted',
                        ].contains(t.status)
                    ? (incoming.where((n) => n.id == t.id).firstOrNull ?? t)
                          .copyWith(affected: false)
                    : t,
              )
              .toList(),
        );
        _validatePlan(p, w);
      case TeamProjectAction.answerRequest:
        final r = p.requests.where((r) => r.id == c.targetId).firstOrNull;
        if (r == null || r.answered || c.text.trim().isEmpty) {
          return _fail('requestUnavailable');
        }
        if (r.kind != 'question' && r.kind != 'permission') {
          return _fail('useTargetedAction');
        }
        p = p.copyWith(
          requests: p.requests
              .map(
                (r) => r.id == c.targetId
                    ? r.copyWith(answered: true, answer: c.text)
                    : r,
              )
              .toList(),
          specDraft: p.specDraft.copyWith(
            decisions: '${p.specDraft.decisions}\n${c.text}'.trim(),
          ),
          tasks: p.tasks
              .map(
                (t) => t.id == r.taskId
                    ? t.copyWith(status: 'queued', reason: '')
                    : t,
              )
              .toList(),
        );
      case TeamProjectAction.updateSettings:
        _validateSettings(c.settings);
        p = p.copyWith(settings: c.settings);
      case TeamProjectAction.pauseProject:
        p = p.copyWith(
          status: 'paused',
          tasks: p.tasks
              .map(
                (t) => t.status == 'running'
                    ? t.copyWith(
                        status: 'paused',
                        reason: 'Project paused',
                        changedAt: _at,
                      )
                    : t,
              )
              .toList(),
        );
      case TeamProjectAction.resumeProject:
        if (!p.planApproved) return _fail('approvePlanFirst');
        if (p.specVersions.isEmpty) return _fail('approveSpecFirst');
        p = p.copyWith(
          status: 'running',
          tasks: p.tasks
              .map(
                (t) => const ['paused', 'interrupted'].contains(t.status)
                    ? t.copyWith(status: 'queued', reason: '')
                    : t,
              )
              .toList(),
        );
      case TeamProjectAction.stopProject:
        p = p.copyWith(
          status: 'stopped',
          tasks: p.tasks
              .map(
                (t) => const ['merged', 'done'].contains(t.status)
                    ? t
                    : t.copyWith(
                        status: 'stopped',
                        reason: 'Stopped by you',
                        changedAt: _at,
                      ),
              )
              .toList(),
        );
      case TeamProjectAction.messageTask:
      case TeamProjectAction.pauseTask:
      case TeamProjectAction.resumeTask:
      case TeamProjectAction.restartTask:
      case TeamProjectAction.stopTask:
      case TeamProjectAction.moveTask:
      case TeamProjectAction.verifyTask:
      case TeamProjectAction.fixFindings:
      case TeamProjectAction.recheckTask:
      case TeamProjectAction.ignoreFinding:
        final task = p.tasks.where((t) => t.id == c.targetId).firstOrNull;
        if (task == null) return _fail('taskNotFound');
        final changed = _task(task, c, p, w);
        p = p.copyWith(
          tasks: p.tasks.map((t) => t.id == task.id ? changed : t).toList(),
        );
        if (changed.status == 'done' && changed.id.startsWith('resolve-')) {
          p = p.copyWith(
            mergeQueue: p.mergeQueue
                .map(
                  (m) => 'resolve-${m.id}' == changed.id
                      ? m.copyWith(
                          status: 'queued',
                          reason: 'Resolution checked in the task branch',
                        )
                      : m,
                )
                .toList(),
          );
        }
        if (changed.status == 'done' &&
            !changed.id.startsWith('resolve-') &&
            !p.mergeQueue.any((m) => m.taskId == task.id)) {
          p = p.copyWith(
            mergeQueue: [
              ...p.mergeQueue,
              TeamMergeItem(
                id: 'merge-${task.id}',
                taskId: task.id,
                repoId: task.repoId,
              ),
            ],
          );
        }
      case TeamProjectAction.processMergeQueue:
        p = _merge(p, c);
      case TeamProjectAction.resolveConflict:
        final item = p.mergeQueue
            .where((m) => m.id == c.targetId && m.status == 'conflict')
            .firstOrNull;
        if (item == null) return _fail('conflictNotFound');
        if (!const ['agent', 'manual', 'recheck'].contains(c.text)) {
          return _fail('resolutionStrategyRequired');
        }
        if (c.text == 'agent') {
          if (p.tasks.any((t) => t.id == 'resolve-${item.id}')) {
            return _fail('resolutionAlreadyStarted');
          }
          final source = p.tasks.firstWhere((t) => t.id == item.taskId);
          p = p.copyWith(
            tasks: [
              ...p.tasks,
              TeamTask(
                id: 'resolve-${item.id}',
                title: 'Resolve conflicting changes',
                phaseId: source.phaseId,
                roleId: 'backend',
                repoId: source.repoId,
                serverId: source.serverId,
                branch: source.branch,
                criteria: [
                  'Both sets of changes remain intact',
                  'Repository checks pass',
                ],
                changedAt: _at,
              ),
            ],
            mergeQueue: p.mergeQueue
                .map(
                  (m) => m.id == item.id
                      ? m.copyWith(reason: 'Waiting for the resolution task')
                      : m,
                )
                .toList(),
          );
        } else if (c.text == 'manual') {
          p = p.copyWith(
            mergeQueue: p.mergeQueue
                .map(
                  (m) => m.id == item.id
                      ? m.copyWith(
                          reason: 'Waiting for your conflict resolution',
                        )
                      : m,
                )
                .toList(),
          );
        } else {
          if (item.reason != 'Waiting for your conflict resolution') {
            return _fail('manualResolutionNotStarted');
          }
          p = p.copyWith(
            mergeQueue: p.mergeQueue
                .map(
                  (m) => m.id == item.id
                      ? m.copyWith(
                          status: 'queued',
                          reason:
                              'Simulated local checks passed after your resolution',
                        )
                      : m,
                )
                .toList(),
          );
        }
      case TeamProjectAction.promote:
        p = _promote(p, c);
      case TeamProjectAction.acknowledgeDigest:
        p = p.copyWith(digestReadAt: _at);
      case TeamProjectAction.acceptPhase:
        if (!p.phases.any((phase) => phase.id == c.targetId) ||
            p.tasks
                .where((t) => t.phaseId == c.targetId)
                .any((t) => t.status != 'merged')) {
          return _fail('phaseNotReady');
        }
        p = p.copyWith(
          phases: p.phases
              .map(
                (phase) => phase.id == c.targetId
                    ? phase.copyWith(accepted: true)
                    : phase,
              )
              .toList(),
        );
      case TeamProjectAction.acceptMilestone:
        final phases = p.phases.where(
          (phase) => phase.milestoneId == c.targetId,
        );
        if (phases.isEmpty || phases.any((phase) => !phase.accepted)) {
          return _fail('milestoneNotReady');
        }
        p = p.copyWith(
          specDraft: p.specDraft.copyWith(
            milestones: p.specDraft.milestones
                .map((m) => m.id == c.targetId ? m.copyWith(accepted: true) : m)
                .toList(),
          ),
        );
        if (p.specDraft.milestones.every((m) => m.accepted)) {
          p = p.copyWith(status: 'done');
        }
      case TeamProjectAction.advance:
        p = _advance(p, w);
      case TeamProjectAction.undoMerge:
        final receipt = p.receipts.where((r) => r.id == c.targetId).firstOrNull;
        if (receipt == null || receipt.kind != 'merge' || !c.confirmed) {
          return _fail('undoUnavailable');
        }
        final repo = p.repos.firstWhere((r) => r.id == receipt.repoId);
        final after = 'fixture-revert-${p.revision + 1}';
        p = p.copyWith(
          repos: p.repos
              .map((r) => r.id == repo.id ? r.copyWith(devCommit: after) : r)
              .toList(),
          receipts: [
            ...p.receipts,
            TeamProjectReceipt(
              id: c.requestId,
              kind: 'revert',
              repoId: repo.id,
              before: repo.devCommit,
              after: after,
              at: _at,
            ),
          ],
        );
      case TeamProjectAction.updateDefaults:
      case TeamProjectAction.createProject:
      case TeamProjectAction.createQuickTask:
      case TeamProjectAction.saveRole:
      case TeamProjectAction.deleteRole:
      case TeamProjectAction.updateServer:
      case TeamProjectAction.deleteProject:
        return _fail('invalidAction');
    }
    p = _syncRequests(_budgetNotice(p));
    if (c.action == TeamProjectAction.advance) {
      for (final task in p.tasks) {
        final old = before.tasks.where((t) => t.id == task.id).firstOrNull;
        if (old?.status != task.status) {
          p = p.copyWith(
            timeline: [
              ...p.timeline,
              TeamTimelineEvent(
                id: 'event-${p.timeline.length + 1}',
                kind: 'taskState',
                text: '${task.title}: ${task.status}',
                actor: 'fixture',
                at: _at,
                taskId: task.id,
              ),
            ],
          );
        }
      }
    }
    // A tick already wrote one row per task that changed; a generic
    // "work advanced" row on top would repeat itself forever.
    if (c.action != TeamProjectAction.advance) {
      p = _log(
        p,
        c.action.name,
        c.action == TeamProjectAction.moveTask && c.confirmed
            ? 'Started over on another server with a new branch and context'
            : c.text.isEmpty
            ? _actionText(c.action)
            : c.text,
        actor: 'person',
      );
    }
    p = p.copyWith(revision: before.revision + 1, updatedAt: _at);
    return w.copyWith(
      revision: w.revision + 1,
      projects: w.projects.map((old) => old.id == p.id ? p : old).toList(),
    );
  }
}
