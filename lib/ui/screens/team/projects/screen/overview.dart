part of '../team_projects_screen.dart';

class TeamProjectOverview extends StatelessWidget {
  const TeamProjectOverview({
    super.key,
    required this.controller,
    required this.projectId,
    this.embedded = false,
    this.onOpenTask,
  });
  final TeamProjectController controller;
  final String projectId;
  final bool embedded;
  final ValueChanged<TeamTask>? onOpenTask;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final l = lookupAppLocalizations(Localizations.localeOf(context));
      final p = controller.snapshot?.projects
          .where((p) => p.id == projectId)
          .firstOrNull;
      if (p == null) {
        return KitStateView(
          icon: Icons.work_outline,
          title: l.teamProjectSelect,
        );
      }
      final c = controller;
      final stale = c.errorCode == 'unavailable';
      void task(TeamTask t) => onOpenTask != null
          ? onOpenTask!(t)
          : _openTask(context, c, p.id, t.id);
      final unread = DateTime.tryParse(p.digestReadAt);
      final showDigest =
          p.timeline.isNotEmpty &&
          (unread == null || DateTime.now().difference(unread).inHours >= 4);
      final events = teamDigestEvents(p.timeline, unread);
      final digestModel = events.any((e) {
        final code = teamTimelineCode(e.text);
        return code != null && teamReasonNeedsModel(code);
      });
      final milestones = p.specDraft.milestones;
      final current = milestones.indexWhere((m) => !m.accepted);
      bool finished(TeamTask t) => t.status == 'merged' || t.status == 'done';
      final busy = p.tasks.where((t) => t.status == 'running').toList();
      final laneTotal = p.settings.mode == 'single' ? 1 : p.settings.maxLanes;
      final asked = {
        for (final r in p.requests.where((r) => !r.answered)) r.taskId,
      };
      final waiting = p.tasks
          .where(
            (t) =>
                t.status == 'queued' &&
                !asked.contains(t.id) &&
                t.dependsOn.every(
                  (id) => p.tasks.any((o) => o.id == id && finished(o)),
                ),
          )
          .take(laneTotal > busy.length ? laneTotal - busy.length : 0)
          .toList();
      final decisions = p.timeline
          .where((e) => e.kind == 'decision')
          .toList()
          .reversed
          .take(3)
          .toList();
      final open = p.status != 'stopped' && p.status != 'done';
      final interrupted =
          p.status == 'interrupted' &&
          TeamExecutionGate.allows(c, TeamExecutionNeed.resume);
      final menu = <KitMenuItem>[
        if (interrupted)
          KitMenuItem(
            label: l.teamProjectResume,
            onSelected: () => _command(c, p, TeamProjectAction.resumeProject),
          ),
        if (open &&
            (p.status != 'paused' ||
                TeamExecutionGate.allows(c, TeamExecutionNeed.lanes)))
          KitMenuItem(
            label: p.status == 'paused'
                ? l.teamProjectResume
                : l.teamProjectPause,
            onSelected: () => _command(
              c,
              p,
              p.status == 'paused'
                  ? TeamProjectAction.resumeProject
                  : TeamProjectAction.pauseProject,
            ),
          ),
        if (p.simulated && p.status == 'plan')
          KitMenuItem(
            label: l.teamProjectDemoPlanFailure,
            onSelected: () =>
                _command(c, p, TeamProjectAction.simulatePlanFailure),
          ),
        if (p.simulated)
          KitMenuItem(
            label: l.teamProjectAdvance,
            onSelected: () => _command(c, p, TeamProjectAction.advance),
          ),
        KitMenuItem(
          label: l.teamProjectStop,
          destructive: true,
          onSelected: () async {
            if (await showKitConfirm(
              context,
              title: l.teamProjectStopConfirmTitle,
              body: p.name.trim().isEmpty
                  ? l.teamProjectStopBody
                  : l.teamProjectStopNamedBody(p.name.trim()),
              confirmLabel: l.teamProjectStop,
              kind: KitConfirmKind.stop,
            )) {
              _command(c, p, TeamProjectAction.stopProject);
            }
          },
        ),
      ];
      return KitScreen(
        topBar: KitTopBar(
          title: p.name,
          subtitle: stale ? l.teamProjectTaskStale : _headline(l, p),
          menu: menu,
          menuLabel: l.teamProjectMenu,
          actions: [
            KitAction(
              label: l.teamProjectSpec,
              icon: Icons.description_outlined,
              onPressed: () => openTeamSpecEditor(context, c, p.id),
            ),
          ],
        ),
        body: ListView(
          padding: KitScreen.padding(context),
          children: [
            if (!embedded) TeamExecutionBlocked(controller: c),
            if (!embedded) TeamResumeUnavailable(controller: c),
            if (c.errorCode != null) _failure(context, c),
            for (final r in p.requests.where((r) => !r.answered))
              _needsYou(context, l, c, p, r),
            if ((p.status == 'failed' || p.status == 'planFailed') &&
                !p.planApproved)
              ..._planFailed(context, l, c, p),
            if (showDigest)
              KitDigest(
                title: l.teamProjectDigest,
                status: p.simulated ? l.teamProjectDemo : '',
                items: [
                  for (final e in events.take(6))
                    KitTeamItem(
                      title: teamTimelineWords(l, e.text),
                      meta: _age(context, e.at),
                    ),
                ],
                actions: [
                  if (digestModel)
                    KitAction(
                      key: const ValueKey('team-digest-model'),
                      label: l.teamRefusalModelNotConfiguredAction,
                      onPressed: () => openTeamDefaults(context, c),
                    ),
                  if (interrupted)
                    KitAction(
                      key: const ValueKey('team-digest-resume'),
                      label: l.teamUiControlResume,
                      onPressed: () =>
                          _command(c, p, TeamProjectAction.resumeProject),
                    ),
                  KitAction(
                    label: l.teamProjectDigestRead,
                    onPressed: () =>
                        _command(c, p, TeamProjectAction.acknowledgeDigest),
                  ),
                ],
              ),
            KitPlanCard(
              title: p.specDraft.goal,
              status: _goalStatus(context, l, p),
              actions: [
                KitAction(
                  label: l.teamProjectOpenSpec,
                  onPressed: () => openTeamSpecEditor(context, c, p.id),
                ),
                if (p.status == 'plan' || p.status == 'planned')
                  KitAction(
                    label: l.teamProjectPlan,
                    onPressed: () => openTeamPlanEditor(context, c, p.id),
                  ),
              ],
            ),
            if (milestones.isNotEmpty)
              KitSectionLabel.inline(l.teamProjectMilestones),
            for (var i = 0; i < milestones.length; i++)
              Builder(
                builder: (context) {
                  final m = milestones[i];
                  final phaseIds = p.phases
                      .where((ph) => ph.milestoneId == m.id)
                      .map((ph) => ph.id)
                      .toSet();
                  final tasks = p.tasks
                      .where((t) => phaseIds.contains(t.phaseId))
                      .toList();
                  final complete = tasks.where(finished).length;
                  final started =
                      i == current || tasks.any((t) => t.status != 'queued');
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      KitMilestoneRow(
                        title: '${i + 1} ${m.title}',
                        state: m.accepted
                            ? KitTeamState.done
                            : tasks.any((t) => t.status == 'running')
                            ? KitTeamState.running
                            : KitTeamState.empty,
                        status: m.accepted
                            ? l.teamProjectDone
                            : tasks.isEmpty
                            ? l.teamProjectMilestoneNoTasks
                            : started
                            ? l.teamProjectMilestoneTasks(
                                complete,
                                tasks.length,
                              )
                            : l.teamProjectMilestoneWaits(i),
                        completed: started && !m.accepted ? complete : null,
                        total: started && !m.accepted ? tasks.length : null,
                        onPressed: () =>
                            _openBoard(context, c, p.id, milestone: m.id),
                      ),
                      if (!m.accepted &&
                          tasks.isNotEmpty &&
                          tasks.every((task) => task.status == 'merged') &&
                          p.phases
                              .where((phase) => phase.milestoneId == m.id)
                              .every((phase) => phase.accepted))
                        KitButton(
                          role: KitButtonRole.tertiary,
                          label: l.teamProjectAccept,
                          onPressed: () => _command(
                            c,
                            p,
                            TeamProjectAction.acceptMilestone,
                            targetId: m.id,
                          ),
                        ),
                    ],
                  );
                },
              ),
            if (busy.isNotEmpty || waiting.isNotEmpty) ...[
              KitSectionLabel.inline(
                l.teamProjectLanesTitle(busy.length, laneTotal),
                trailing: KitButton.tertiary(
                  label: l.teamProjectLanesChange,
                  onPressed: () => openTeamProjectSettings(context, c, p.id),
                ),
              ),
              for (final t in [...busy, ...waiting])
                KitRow(
                  leading: KitStatusMark(
                    state: t.status == 'running' && !stale
                        ? KitMarkState.working
                        : KitMarkState.waiting,
                  ),
                  title: l.teamProjectLaneTitle(
                    _role(context, c, t.roleId),
                    t.title,
                  ),
                  supporting: TextSpan(
                    text: _laneLine(context, l, c, t, stale),
                  ),
                  trailing: const KitRowValue('', chevron: true),
                  onTap: () => task(t),
                ),
              KitText(_laneNote(l, p, laneTotal), role: KitTextRole.caption),
            ],
            for (final repo in p.repos)
              if (p.mergeQueue.any(
                (i) => i.repoId == repo.id && i.status != 'merged',
              ))
                KitMergeQueue(
                  title: '${l.teamProjectMerge} · ${repo.name}',
                  status: 'dev',
                  items: [
                    for (final i in p.mergeQueue.where(
                      (i) => i.repoId == repo.id && i.status != 'merged',
                    ))
                      KitTeamItem(
                        title:
                            p.tasks
                                .where((t) => t.id == i.taskId)
                                .firstOrNull
                                ?.title ??
                            repo.name,
                        detail: i.reason.isNotEmpty
                            ? i.reason
                            : _mergeWord(l, i),
                        state: _state(i.status),
                        onPressed: () => _openTask(context, c, p.id, i.taskId),
                      ),
                  ],
                  actions: [
                    if (TeamExecutionGate.allows(
                      c,
                      TeamExecutionNeed.mergeQueue,
                    ))
                      KitAction(
                        label: l.teamProjectMergeNext,
                        onPressed: () =>
                            confirmAndMergeToDev(context, c, p, repo),
                      ),
                  ],
                ),
            for (final repo in p.repos) ...[
              for (final item in teamReceiptItems(
                context,
                p,
                repo,
                age: (at) => _age(context, at),
              ))
                KitRow(
                  title: item.title,
                  supporting: TextSpan(text: item.detail),
                  trailing: KitRowValue(item.meta ?? ''),
                ),
              if (teamCanPromote(p, repo) &&
                  TeamExecutionGate.allows(c, TeamExecutionNeed.promotion))
                KitPromoteCard(
                  title: l.teamProjectPromoteTitle,
                  status: l.teamProjectPromoteStatus(repo.name),
                  state: KitTeamState.needsYou,
                  items: [
                    KitTeamItem(
                      title: repo.name,
                      detail: teamCommitChange(repo.mainCommit, repo.devCommit),
                    ),
                  ],
                  primary: KitAction(
                    label: l.teamProjectTaskPromote,
                    onPressed: () => confirmAndPromote(context, c, p, repo),
                  ),
                ),
            ],
            if (decisions.isNotEmpty) ...[
              KitSectionLabel.inline(l.teamProjectDecisions),
              for (final e in decisions)
                KitRow(
                  title: e.text,
                  supporting: TextSpan(
                    text: _who(context, l, c, e) == ''
                        ? _age(context, e.at)
                        : l.teamProjectDecisionBy(
                            _who(context, l, c, e),
                            _age(context, e.at),
                          ),
                  ),
                ),
            ],
            KitSectionLabel.inline(l.teamProjectPages),
            KitRow(
              leading: const KitIcon(AppIconography.kanban),
              title: l.teamProjectBoard,
              supporting: TextSpan(text: _boardSummary(l, p)),
              trailing: const KitRowValue('', chevron: true),
              onTap: () => _openBoard(context, c, p.id),
            ),
            KitRow(
              leading: const KitIcon(AppIconography.timeline),
              title: l.teamProjectTimeline,
              supporting: TextSpan(text: _timelineSummary(context, l, p)),
              trailing: const KitRowValue('', chevron: true),
              onTap: () => pushKitPage<void>(
                context,
                (_) => TeamProjectTimeline(controller: c, projectId: p.id),
              ),
            ),
            KitRow(
              leading: const KitIcon(AppIconography.server),
              title: l.teamProjectServers,
              supporting: TextSpan(
                text: l.teamProjectServersSummary(
                  {
                    for (final r in p.repos) r.serverId,
                    for (final t in p.tasks) t.serverId,
                  }.where((id) => id.isNotEmpty).length,
                ),
              ),
              trailing: const KitRowValue('', chevron: true),
              onTap: () => pushKitPage<void>(
                context,
                (_) => TeamProjectServers(controller: c, projectId: p.id),
              ),
            ),
            KitRow(
              leading: const KitIcon(AppIconography.settings),
              title: l.teamProjectSettings,
              supporting: TextSpan(
                text: p.settings.mode == 'single'
                    ? l.teamProjectSettingsSummarySingle
                    : l.teamProjectSettingsSummaryParallel(p.settings.maxLanes),
              ),
              trailing: const KitRowValue('', chevron: true),
              onTap: () => openTeamProjectSettings(context, c, p.id),
            ),
            KitSectionLabel.inline(l.teamProjectCost),
            if (p.budgetWarning) KitNotice(message: l.teamProjectBudgetNear),
            KitRow(
              leading: const KitIcon(AppIconography.usage),
              title: _costLine(l, p),
              trailing: const KitRowValue('', chevron: true),
              onTap: () => openTeamProjectSettings(context, c, p.id),
            ),
            if (TeamExecutionGate.allows(c, TeamExecutionNeed.removal) &&
                !p.simulated)
              KitRowGroup(
                children: [
                  KitRow(
                    key: const ValueKey('team-project-delete'),
                    leading: const KitIcon(AppIconography.delete),
                    title: l.teamProjectDelete(p.name),
                    titleMaxLines: 2,
                    destructive: true,
                    onTap: () => _confirmDelete(context, c, p),
                  ),
                ],
              ),
          ],
        ),
      );
    },
  );
}
