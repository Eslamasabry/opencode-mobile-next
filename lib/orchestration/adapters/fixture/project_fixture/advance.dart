part of '../project_fixture_gateway.dart';

extension _ProjectFixtureAdvance on ProjectFixtureGateway {
  TeamProject _advance(TeamProject p, TeamWorkspace w) {
    if (p.status != 'running') return _fail('projectNotRunning');
    final day = _at.substring(0, 10);
    if (p.spendDay != day) p = p.copyWith(spentToday: 0, spendDay: day);
    final budget = p.settings.budget;
    final capped =
        !budget.unlimited &&
        ((budget.total != null && p.spent >= budget.total!) ||
            (budget.daily != null && p.spentToday >= budget.daily!));
    if (capped) {
      return _log(
        p.copyWith(
          status: 'paused',
          tasks: p.tasks
              .map(
                (t) => t.status == 'running'
                    ? t.copyWith(
                        status: 'paused',
                        reason: 'Budget reached',
                        changedAt: _at,
                      )
                    : t,
              )
              .toList(),
          requests: [
            ...p.requests.where((r) => r.id != '${p.id}-budget'),
            TeamRequest(
              id: '${p.id}-budget',
              kind: 'budget',
              title: 'Budget reached. Raise the budget or pause the project.',
              createdAt: _at,
            ),
          ],
        ),
        'budget',
        'Budget reached',
        actor: 'fixture',
      );
    }
    final tasks = [...p.tasks];
    final requests = [...p.requests];
    var spent = 0.0;
    for (var i = 0; i < tasks.length; i++) {
      var t = tasks[i];
      if (t.status != 'running') continue;
      final server = w.servers.firstWhere((s) => s.id == t.serverId);
      if (!server.online) {
        tasks[i] = t.copyWith(
          status: 'interrupted',
          reason: 'Server is not reachable',
          changedAt: _at,
        );
        continue;
      }
      if (server.chatWaiting) {
        tasks[i] = t.copyWith(
          reason: 'Waiting for your chat to receive its first word',
        );
        continue;
      }
      if (p.settings.chargingOnly && server.phone && !isCharging) {
        tasks[i] = t.copyWith(reason: 'Waiting for this phone to charge');
        continue;
      }
      if (budget.taskTokens != null && t.tokens + 100 > budget.taskTokens!) {
        tasks[i] = t.copyWith(
          status: 'stalled',
          reason: 'Task token limit reached',
          changedAt: _at,
        );
        continue;
      }
      t = t.copyWith(
        steps: t.steps + 1,
        tokens: t.tokens + 100,
        changedAt: _at,
      );
      spent += 0.01;
      if (t.steps >= 3) {
        t = t.copyWith(
          status: 'review',
          reason: 'Ready for a read-only check',
          diff:
              '--- a/lib/example.dart\n+++ b/lib/example.dart\n+// Simulated implementation of ${t.title}',
        );
      }
      if (p.id == 'demo-project' &&
          t.id == p.tasks.first.id &&
          t.steps == 2 &&
          !requests.any((r) => r.id == 'demo-question')) {
        requests.add(
          TeamRequest(
            id: 'demo-question',
            kind: 'question',
            taskId: t.id,
            title: 'Keep completed work visible in the overview?',
            createdAt: _at,
          ),
        );
        t = t.copyWith(status: 'needsInput', reason: 'Waiting for your answer');
      }
      tasks[i] = t;
    }
    final max = p.settings.mode == 'single' ? 1 : p.settings.maxLanes;
    var running = tasks.where((t) => t.status == 'running').length;
    final reachedAfterStep =
        !budget.unlimited &&
        ((budget.total != null && p.spent + spent >= budget.total!) ||
            (budget.daily != null && p.spentToday + spent >= budget.daily!));
    for (
      var i = 0;
      i < tasks.length && running < max && !reachedAfterStep;
      i++
    ) {
      final t = tasks[i];
      if (t.status != 'queued') continue;
      final server = w.servers.firstWhere((s) => s.id == t.serverId);
      if (!server.online ||
          server.chatWaiting ||
          (p.settings.chargingOnly && server.phone && !isCharging)) {
        tasks[i] = t.copyWith(
          reason: !server.online
              ? 'Waiting for ${server.name}'
              : server.chatWaiting
              ? 'Waiting for your chat to receive its first word'
              : 'Waiting for this phone to charge',
        );
        continue;
      }
      if (tasks
                  .where(
                    (t) => t.serverId == server.id && t.status == 'running',
                  )
                  .length +
              w.projects
                  .where((other) => other.id != p.id)
                  .expand((other) => other.tasks)
                  .where(
                    (other) =>
                        other.serverId == server.id &&
                        other.status == 'running',
                  )
                  .length >=
          server.laneCap) {
        continue;
      }
      if (t.dependsOn.any(
        (id) => !tasks.any(
          (d) => d.id == id && const ['done', 'merged'].contains(d.status),
        ),
      )) {
        continue;
      }
      final dependencyPhases = p.phases.where(
        (phase) =>
            phase.id != t.phaseId &&
            tasks.any(
              (d) => t.dependsOn.contains(d.id) && d.phaseId == phase.id,
            ),
      );
      if (dependencyPhases.any(
        (phase) =>
            (phase.risky || p.settings.reviewLevel == 'everyStep') &&
            !phase.accepted,
      )) {
        tasks[i] = t.copyWith(reason: 'Waiting for the previous phase review');
        continue;
      }
      tasks[i] = t.copyWith(status: 'running', reason: '', changedAt: _at);
      running++;
    }
    var next = p.copyWith(
      tasks: tasks,
      requests: requests,
      spent: p.spent + spent,
      spentToday: p.spentToday + spent,
      usageReported: true,
    );
    if (p.settings.autoFix && p.settings.reviewLevel != 'everyStep') {
      final queue = [...next.mergeQueue];
      final checked = next.tasks.map((t) {
        final previous = p.tasks.firstWhere((old) => old.id == t.id);
        if (previous.status != 'review' && previous.status != 'findings') {
          return t;
        }
        if (previous.status == 'findings') {
          if (t.fixRounds >= p.settings.maxFixRounds ||
              !t.findings.any(
                (f) =>
                    f.status == 'open' &&
                    const ['critical', 'major'].contains(f.severity),
              )) {
            return t;
          }
          return _task(
            t,
            TeamProjectCommand(
              requestId: 'auto-fix',
              action: TeamProjectAction.fixFindings,
              findingIds: t.findings
                  .where(
                    (f) =>
                        const ['critical', 'major'].contains(f.severity) &&
                        f.status == 'open',
                  )
                  .map((f) => f.id)
                  .toList(),
            ),
            p,
            w,
          );
        }
        final result = _task(
          t,
          TeamProjectCommand(
            requestId: 'auto-check',
            action: t.fixRounds > 0
                ? TeamProjectAction.recheckTask
                : TeamProjectAction.verifyTask,
          ),
          p,
          w,
        );
        if (result.status == 'done' && !queue.any((m) => m.taskId == t.id)) {
          queue.add(
            TeamMergeItem(id: 'merge-${t.id}', taskId: t.id, repoId: t.repoId),
          );
        }
        return result;
      }).toList();
      next = next.copyWith(tasks: checked, mergeQueue: queue);
      if (checked.any(
        (t) => t.status != p.tasks.firstWhere((old) => old.id == t.id).status,
      )) {
        next = _log(
          next,
          'automaticCheck',
          'Simulated acceptance checks advanced',
          actor: 'checker',
        );
      }
    }
    return _budgetNotice(next);
  }

  TeamProject _merge(TeamProject p, TeamProjectCommand c) {
    if (p.settings.reviewLevel == 'everyStep' && !c.confirmed) {
      return _fail('confirmationRequired');
    }
    final queue = [...p.mergeQueue];
    final repos = [...p.repos];
    final tasks = [...p.tasks];
    final receipts = [...p.receipts];
    for (var i = 0; i < queue.length; i++) {
      final item = queue[i];
      if (item.status != 'queued' ||
          (c.targetId.isNotEmpty && item.repoId != c.targetId)) {
        continue;
      }
      final t = tasks.firstWhere((t) => t.id == item.taskId);
      if (t.status != 'done' ||
          t.findings.any(
            (f) => f.status == 'open' && f.severity != 'notApplicable',
          )) {
        continue;
      }
      if (t.dependsOn.any(
        (id) => !tasks.any((t) => t.id == id && t.status == 'merged'),
      )) {
        continue;
      }
      final ri = repos.indexWhere((r) => r.id == item.repoId);
      final repo = repos[ri];
      if (repo.checkCommand.trim().isEmpty) return _fail('checksRequired');
      final after = 'fixture-dev-${p.revision + 1}-$i';
      repos[ri] = repo.copyWith(devCommit: after);
      queue[i] = item.copyWith(status: 'merged', checksPassed: true);
      final ti = tasks.indexWhere((t) => t.id == item.taskId);
      tasks[ti] = t.copyWith(status: 'merged', changedAt: _at);
      final resolution = tasks.indexWhere((t) => t.id == 'resolve-${item.id}');
      if (resolution >= 0) {
        tasks[resolution] = tasks[resolution].copyWith(
          status: 'merged',
          changedAt: _at,
        );
      }
      receipts.add(
        TeamProjectReceipt(
          id: '${c.requestId}-$i',
          kind: 'merge',
          repoId: repo.id,
          before: repo.devCommit,
          after: after,
          at: _at,
          actor: 'fixture',
        ),
      );
    }
    return p.copyWith(
      mergeQueue: queue,
      repos: repos,
      tasks: tasks,
      receipts: receipts,
    );
  }

  TeamProject _promote(TeamProject p, TeamProjectCommand c) {
    if (!c.confirmed) return _fail('confirmationRequired');
    final repo = p.repos.where((r) => r.id == c.targetId).firstOrNull;
    if (repo == null) return _fail('repoNotFound');
    if (c.expectedDevCommit != repo.devCommit ||
        c.expectedMainCommit != repo.mainCommit) {
      return _fail('staleCommits');
    }
    final phases = p.phases.where(
      (phase) =>
          p.tasks.any((t) => t.phaseId == phase.id && t.repoId == repo.id),
    );
    if (phases.any(
      (phase) =>
          (phase.risky || p.settings.reviewLevel == 'everyStep') &&
          !phase.accepted,
    )) {
      return _fail('phaseReviewRequired');
    }
    if (repo.devCommit == repo.mainCommit ||
        p.tasks
            .where((t) => t.repoId == repo.id)
            .any((t) => t.status != 'merged') ||
        p.mergeQueue
            .where((m) => m.repoId == repo.id)
            .any((m) => m.status != 'merged' || !m.checksPassed)) {
      return _fail('notReadyToPromote');
    }
    return p.copyWith(
      repos: p.repos
          .map(
            (r) => r.id == repo.id ? r.copyWith(mainCommit: repo.devCommit) : r,
          )
          .toList(),
      receipts: [
        ...p.receipts,
        TeamProjectReceipt(
          id: c.requestId,
          kind: 'promotion',
          repoId: repo.id,
          before: repo.mainCommit,
          after: repo.devCommit,
          at: _at,
        ),
      ],
    );
  }
}
