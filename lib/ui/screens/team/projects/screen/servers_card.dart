part of '../team_projects_screen.dart';

class TeamProjectServers extends StatelessWidget {
  const TeamProjectServers({
    super.key,
    required this.controller,
    required this.projectId,
  });
  final TeamProjectController controller;
  final String projectId;
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
      return KitScreen(
        topBar: KitTopBar(title: l.teamProjectServers, subtitle: p.name),
        body: ListView(
          padding: KitScreen.padding(context),
          children: [
            if (controller.snapshot!.servers.any((s) => s.phone && !s.online))
              TeamPhoneServerProblem(controller: controller)
            else
              TeamExecutionBlocked(controller: controller),
            if (controller.errorCode != null) _failure(context, controller),
            if (p.simulated)
              KitText(l.teamProjectCostDemo, role: KitTextRole.secondary),
            for (final s in controller.snapshot!.servers) ...[
              KitServerLane(
                title: s.name,
                status: controller.errorCode == 'unavailable'
                    ? l.teamProjectTaskStale
                    : s.online
                    ? l.teamProjectOnline
                    : l.teamProjectOffline,
                state: controller.errorCode == 'unavailable'
                    ? KitTeamState.stale
                    : s.online
                    ? KitTeamState.done
                    : KitTeamState.stale,
                summary: l.teamProjectLaneCount(
                  p.tasks
                      .where((t) => t.serverId == s.id && t.status == 'running')
                      .length,
                  // The project's own limit applies on this server too.
                  _lanesHere(p, s.laneCap),
                ),
                items: [
                  for (final t in p.tasks.where((t) => t.serverId == s.id))
                    KitTeamItem(
                      title: t.title,
                      detail: controller.errorCode == 'unavailable'
                          ? l.teamProjectTaskStale
                          : _word(l, t.status),
                      onPressed: () =>
                          _openTask(context, controller, p.id, t.id),
                    ),
                ],
              ),
              KitButton(
                role: KitButtonRole.tertiary,
                label: l.teamProjectEditorMaxLanes,
                onPressed: () async {
                  final revision = controller.snapshot!.revision;
                  await showKitInputDialog(
                    context,
                    title: s.name,
                    label: l.teamProjectEditorMaxLanes,
                    initial: s.laneCap.toString(),
                    kind: KitFieldKind.number,
                    confirmLabel: l.teamProjectEditorSave,
                    validate: (value) => (int.tryParse(value) ?? 0) > 0
                        ? null
                        : l.teamProjectEditorPositiveLanes,
                    onSubmit: (value) async {
                      final result = await controller.execute(
                        TeamProjectCommand(
                          requestId: controller.newRequestId(),
                          action: TeamProjectAction.updateServer,
                          expectedRevision: revision,
                          server: s.copyWith(laneCap: int.parse(value)),
                        ),
                      );
                      return result.accepted ? null : l.teamProjectError;
                    },
                  );
                },
              ),
              for (final t in p.tasks.where(
                (t) =>
                    TeamExecutionGate.allows(
                      controller,
                      TeamExecutionNeed.placement,
                    ) &&
                    t.serverId == s.id &&
                    t.status != 'merged' &&
                    t.status != 'done',
              ))
                KitPickerRow<String>(
                  title: '${l.teamProjectMove} · ${t.title}',
                  choices: [
                    for (final target in controller.snapshot!.servers)
                      KitChoice(value: target.id, title: target.name),
                  ],
                  selected: t.serverId,
                  onSelected: (target) async {
                    if (target == t.serverId) return;
                    final note = await showKitInputDialog(
                      context,
                      title: l.teamProjectMoveTo,
                      label: l.teamProjectHandoff,
                      confirmLabel: l.teamProjectMove,
                    );
                    if (note == null) return;
                    final latest = controller.snapshot!.projects.firstWhere(
                      (value) => value.id == p.id,
                    );
                    final result = await _command(
                      controller,
                      latest,
                      TeamProjectAction.moveTask,
                      targetId: t.id,
                      serverId: target,
                      text: note,
                    );
                    if ((result.code == 'branchUnavailable' ||
                            result.code == 'sharedRemoteRequired') &&
                        context.mounted) {
                      final restart = await showKitConfirm(
                        context,
                        title: l.teamProjectRestartElsewhereConfirmTitle,
                        body: l.teamProjectRestartElsewhereBody,
                        confirmLabel: l.teamProjectRestartElsewhere,
                        cancelLabel: l.teamProjectWaitForServer,
                      );
                      if (restart) {
                        await _command(
                          controller,
                          latest,
                          TeamProjectAction.moveTask,
                          targetId: t.id,
                          serverId: target,
                          text: note,
                          confirmed: true,
                        );
                      }
                    }
                  },
                ),
            ],
          ],
        ),
      );
    },
  );
}

class _Repeat {
  _Repeat(this.first) : count = 1;
  final TeamTimelineEvent first;
  int count;
}

/// Newest-first events with back-to-back rows of identical words and task
/// folded into one row that says how many there were.
List<_Repeat> _foldRepeats(List<TeamTimelineEvent> events) {
  final out = <_Repeat>[];
  for (final e in events) {
    final last = out.lastOrNull;
    if (last != null &&
        last.first.text == e.text &&
        last.first.taskId == e.taskId &&
        last.first.actor == e.actor) {
      last.count++;
    } else {
      out.add(_Repeat(e));
    }
  }
  return out;
}
