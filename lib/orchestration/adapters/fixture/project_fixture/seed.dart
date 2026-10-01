part of '../project_fixture_gateway.dart';

extension _ProjectFixtureSeed on ProjectFixtureGateway {
  TeamWorkspace _initial() {
    final base = TeamWorkspace(
      // A person who picks Parallel expects more than one lane.
      defaultSettings: const TeamProjectSettings(maxLanes: 3),
      servers: const [
        TeamServer(id: 'computer', name: 'Home PC', memoryMb: 120),
        TeamServer(
          id: 'phone',
          name: 'This phone',
          phone: true,
          laneCap: 3,
          memoryMb: 180,
        ),
      ],
      roles: const [
        TeamProjectRole(
          id: 'frontend',
          name: 'Frontend',
          instructions: 'Build accessible user journeys',
        ),
        TeamProjectRole(
          id: 'backend',
          name: 'Backend',
          instructions: 'Implement stable domain contracts',
        ),
        TeamProjectRole(
          id: 'planner',
          name: 'Planner',
          instructions: 'Plan milestones and criteria',
        ),
        TeamProjectRole(
          id: 'checker',
          name: 'Checker',
          instructions: 'Read-only acceptance verification',
          readOnly: true,
        ),
      ],
    );
    if (!seedDemo) return base;
    return base.copyWith(
      roles: [
        ...base.roles,
        const TeamProjectRole(
          id: 'tester',
          name: 'Tester',
          instructions: 'Run the checks and report what fails',
        ),
        const TeamProjectRole(
          id: 'docs',
          name: 'Docs',
          instructions: 'Write and translate the copy',
        ),
      ],
      projects: [_syncRequests(_demoProject())],
    );
  }

  /// Presentation data for the simulator: four milestones, eight tasks in the
  /// second (five merged), three lanes (two busy), one open question, two
  /// decisions and two repos on two servers. Times are relative to the clock.
  TeamProject _demoProject() {
    final now = _now().toUtc();
    String ago(Duration d) => now.subtract(d).toIso8601String();
    TeamTask task(
      String id,
      String title,
      String phase,
      String role,
      String repo,
      String server,
      String status,
      int criteria, {
      List<String> after = const [],
      String changed = '',
    }) => TeamTask(
      id: id,
      title: title,
      phaseId: phase,
      roleId: role,
      repoId: repo,
      serverId: server,
      status: status,
      dependsOn: after,
      criteria: [for (var i = 1; i <= criteria; i++) '$title: check $i passes'],
      branch: 'team/demo/$id',
      changedAt: changed.isEmpty ? ago(const Duration(days: 1)) : changed,
      steps: status == 'running' ? 2 : 0,
    );
    const done = 'merged';
    final tasks = [
      task(
        't-tokens',
        'Colour tokens',
        'ph-m1',
        'frontend',
        'site',
        'computer',
        done,
        2,
      ),
      task(
        't-type',
        'Type scale',
        'ph-m1',
        'frontend',
        'site',
        'computer',
        done,
        2,
      ),
      task(
        't-data',
        'Pricing data model',
        'ph-data',
        'backend',
        'site',
        'computer',
        done,
        3,
      ),
      task(
        't-checkout',
        'Checkout endpoint',
        'ph-data',
        'backend',
        'site',
        'computer',
        done,
        2,
        after: ['t-data'],
      ),
      task(
        't-table',
        'Pricing table',
        'ph-screens',
        'frontend',
        'site',
        'computer',
        'running',
        4,
        after: ['t-data'],
        changed: ago(const Duration(minutes: 12, seconds: 20)),
      ),
      task(
        't-copy',
        'Arabic copy',
        'ph-screens',
        'docs',
        'docs',
        'phone',
        'queued',
        2,
        after: ['t-data'],
      ),
      task(
        't-lang',
        'Language switcher',
        'ph-screens',
        'frontend',
        'site',
        'computer',
        done,
        2,
      ),
      task(
        't-layout',
        'Pricing page layout',
        'ph-screens',
        'frontend',
        'site',
        'computer',
        done,
        3,
      ),
      task(
        't-checks',
        'Checkout checks',
        'ph-checks',
        'tester',
        'site',
        'phone',
        'running',
        2,
        after: ['t-checkout'],
        changed: ago(const Duration(minutes: 3, seconds: 20)),
      ),
      task(
        't-a11y',
        'Accessibility pass',
        'ph-checks',
        'tester',
        'docs',
        'phone',
        done,
        2,
      ),
      task(
        't-drafts',
        'Draft storage',
        'ph-docs',
        'backend',
        'site',
        'computer',
        'queued',
        2,
      ),
      task(
        't-pages',
        'Docs pages',
        'ph-docs',
        'docs',
        'docs',
        'phone',
        'queued',
        2,
        after: ['t-drafts'],
      ),
      task(
        't-search',
        'Docs search',
        'ph-docs',
        'frontend',
        'docs',
        'phone',
        'queued',
        3,
        after: ['t-drafts'],
      ),
      task(
        't-launch',
        'Launch checklist',
        'ph-launch',
        'tester',
        'site',
        'computer',
        'queued',
        2,
        after: ['t-pages'],
      ),
    ];
    const spec = TeamSpec(
      goal: 'Launch the marketing site and its docs in Arabic and English',
      constraints: 'Accessible on phones and computers',
      outOfScope: 'Public releases',
      milestones: [
        TeamMilestone(
          id: 'm1',
          title: 'Design tokens',
          accepted: true,
          criteria: ['Colours and type are named once'],
        ),
        TeamMilestone(
          id: 'm2',
          title: 'Landing and pricing',
          criteria: ['Pricing reads well at twice the text size'],
        ),
        TeamMilestone(
          id: 'm3',
          title: 'Docs site',
          criteria: ['Every docs page is searchable'],
        ),
        TeamMilestone(
          id: 'm4',
          title: 'Launch checks',
          criteria: ['Every check passes on phone and computer'],
        ),
      ],
    );
    return TeamProject(
      id: 'demo-project',
      name: 'Lumen launch site',
      status: 'running',
      planApproved: true,
      simulated: true,
      usageReported: true,
      spent: 18,
      spentToday: 4.1,
      spendDay: now.toIso8601String().substring(0, 10),
      updatedAt: ago(const Duration(minutes: 2)),
      digestReadAt: ago(const Duration(minutes: 1)),
      settings: const TeamProjectSettings(
        mode: 'parallel',
        maxLanes: 3,
        budget: TeamBudget(chosen: true, daily: 10, total: 50),
      ),
      repos: const [
        TeamRepo(
          id: 'site',
          name: 'site',
          serverId: 'computer',
          path: '~/work/lumen-site',
          devCommit: 'd4e5f6a',
          mainCommit: 'a1b2c3d',
        ),
        TeamRepo(
          id: 'docs',
          name: 'docs',
          serverId: 'phone',
          path: '/project/lumen-docs',
          devCommit: 'e7f8a9b',
          mainCommit: 'c0d1e2f',
        ),
      ],
      specDraft: spec.copyWith(
        version: 3,
        approvedBy: 'person',
        approvedAt: ago(const Duration(days: 2)),
      ),
      specVersions: [
        for (var v = 1; v <= 3; v++)
          spec.copyWith(
            version: v,
            approvedBy: 'person',
            approvedAt: ago(Duration(days: 6 - v * 2 + 2)),
          ),
      ],
      phases: const [
        TeamPhase(
          id: 'ph-m1',
          milestoneId: 'm1',
          title: 'Tokens',
          accepted: true,
        ),
        TeamPhase(
          id: 'ph-data',
          milestoneId: 'm2',
          title: 'Data',
          risky: true,
          accepted: true,
        ),
        TeamPhase(id: 'ph-screens', milestoneId: 'm2', title: 'Screens'),
        TeamPhase(id: 'ph-checks', milestoneId: 'm2', title: 'Checks'),
        TeamPhase(id: 'ph-docs', milestoneId: 'm3', title: 'Docs pages'),
        TeamPhase(id: 'ph-launch', milestoneId: 'm4', title: 'Launch'),
      ],
      tasks: tasks,
      requests: [
        TeamRequest(
          id: 'demo-question',
          kind: 'question',
          title: 'Keep drafts in SQLite?',
          taskId: 't-drafts',
          createdAt: ago(const Duration(minutes: 5)),
        ),
      ],
      mergeQueue: [
        for (final t in tasks.where((t) => t.status == done))
          TeamMergeItem(
            id: 'merge-${t.id}',
            taskId: t.id,
            repoId: t.repoId,
            status: 'merged',
            checksPassed: true,
          ),
      ],
      timeline: [
        TeamTimelineEvent(
          id: 'demo-created',
          kind: 'created',
          text: 'Project started',
          at: ago(const Duration(days: 5)),
          actor: 'person',
        ),
        TeamTimelineEvent(
          id: 'demo-decision-tests',
          kind: 'decision',
          text: 'Tests run on Home PC only',
          at: ago(const Duration(days: 2)),
          actor: 'planner',
        ),
        TeamTimelineEvent(
          id: 'demo-decision-languages',
          kind: 'decision',
          text: 'Pricing page ships in two languages',
          at: ago(const Duration(days: 1)),
          actor: 'person',
        ),
      ],
    );
  }
}
