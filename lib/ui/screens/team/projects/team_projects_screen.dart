import 'dart:async';
import 'package:flutter/material.dart';
import '../../../../domain/relative_age.dart';
import '../../../../l10n/app_localizations.dart';
import '../../../../state/team_project_controller.dart';
import '../../../app_iconography.dart';
import '../../../kit/kit.dart';
import 'team_execution_gate.dart';
import 'team_merge_flow.dart';
import 'team_project_conversation.dart';
import 'team_digest.dart';
import 'team_project_editors.dart';
import 'team_refusal.dart';

part 'screen/overview.dart';
part 'screen/overview_helpers.dart';
part 'screen/board_timeline.dart';
part 'screen/servers_card.dart';

// Finish line: a person can steer a persisted simulated project from goal to
// reviewed work, with every action crossing the project controller.
// Non-goal: selecting or connecting a production execution engine.
class TeamProjectsScreen extends StatefulWidget {
  const TeamProjectsScreen({
    super.key,
    required this.controller,
    this.initialProjectId,
    this.initialTaskId,
    this.onLeaveDemo,
  });
  final String? initialProjectId;
  final String? initialTaskId;
  final VoidCallback? onLeaveDemo;
  final TeamProjectController controller;
  @override
  State<TeamProjectsScreen> createState() => _TeamProjectsScreenState();
}

class _TeamProjectsScreenState extends State<TeamProjectsScreen> {
  String? _selected;
  String? _task;
  bool _initialOpened = false;
  @override
  void initState() {
    super.initState();
    _selected = widget.initialProjectId;
    _task = widget.initialTaskId;
    if (widget.controller.snapshot == null) unawaited(widget.controller.load());
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final l = lookupAppLocalizations(Localizations.localeOf(context));
      final c = widget.controller;
      final projects = [...?c.snapshot?.projects]
        ..sort((a, b) => _urgency(a).compareTo(_urgency(b)));
      final selected =
          projects
              .where(
                (p) =>
                    p.id == _selected ||
                    (widget.initialTaskId != null &&
                        p.tasks.any((t) => t.id == widget.initialTaskId)),
              )
              .firstOrNull ??
          projects.firstOrNull;
      final selectedTask =
          selected?.tasks.where((t) => t.id == _task).firstOrNull ??
          selected?.tasks.firstOrNull;
      if (!_initialOpened &&
          selected != null &&
          (widget.initialProjectId != null || widget.initialTaskId != null)) {
        _initialOpened = true;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (!mounted) return;
          if (widget.initialTaskId != null &&
              KitLayout.windowOf(context) != KitWindow.large) {
            _openTask(context, c, selected.id, widget.initialTaskId!);
          } else if (!KitScreen.showsDetail(context)) {
            unawaited(
              pushKitPage<void>(
                context,
                (_) =>
                    TeamProjectOverview(controller: c, projectId: selected.id),
              ),
            );
          }
        });
      }
      return KitScreen.threePane(
        topBar: KitTopBar(
          title: l.teamProjectHome,
          subtitle: c.snapshot?.simulated == true
              ? l.teamProjectDemoChip
              : null,
          actions: [
            KitAction(
              label: l.teamProjectEditorDefaults,
              icon: Icons.settings_outlined,
              onPressed: () => openTeamDefaults(context, c),
            ),
            if (widget.onLeaveDemo != null)
              KitAction(
                label: l.teamProjectOff,
                icon: Icons.logout,
                onPressed: widget.onLeaveDemo,
              ),
            KitAction(
              label: l.teamProjectRoles,
              icon: Icons.groups_outlined,
              onPressed: () => openTeamRoles(context, c),
            ),
          ],
        ),
        loading: c.loading,
        loadingLabel: l.teamProjectLoad,
        list: ListView(
          padding: KitScreen.padding(context),
          children: [
            TeamExecutionBlocked(controller: c),
            TeamProtectionLine(controller: c),
            TeamResumeUnavailable(controller: c),
            if (c.errorCode != null) _failure(context, c),
            if (projects.isEmpty && !c.loading)
              KitStateView(icon: Icons.work_outline, title: l.teamProjectEmpty),
            for (final p in projects) ...[
              if (p.status == 'interrupted' &&
                  TeamExecutionGate.allows(c, TeamExecutionNeed.resume))
                KitRow(
                  key: ValueKey('team-resume-row-${p.id}'),
                  title: l.teamProjectInterruptedRow(p.name),
                  onTap: () => _command(c, p, TeamProjectAction.resumeProject),
                ),
              for (final request in p.requests.where((r) => !r.answered))
                KitRow(
                  title: request.title,
                  supporting: TextSpan(text: p.name),
                  onTap: () => _answer(context, c, p, request),
                ),
              KitProjectRow(
                title: p.name,
                detail: p.budgetWarning
                    ? l.teamProjectBudgetNear
                    : p.usageReported
                    ? _todayCost(l, p)
                    : null,
                status: c.errorCode == 'unavailable'
                    ? l.teamProjectTaskStale
                    : _headline(l, p),
                state: c.errorCode == 'unavailable'
                    ? KitTeamState.stale
                    : _state(p.status),
                meta: _age(context, p.updatedAt),
                onPressed: () => KitScreen.openDetail<void>(
                  context,
                  select: () => setState(() {
                    _selected = p.id;
                    _task = null;
                  }),
                  page: (_) =>
                      TeamProjectOverview(controller: c, projectId: p.id),
                ),
              ),
            ],
          ],
        ),
        detail: selected == null
            ? null
            : TeamProjectOverview(
                controller: c,
                projectId: selected.id,
                embedded: true,
                onOpenTask: (task) {
                  if (KitLayout.windowOf(context) == KitWindow.large) {
                    setState(() => _task = task.id);
                  } else {
                    _openTask(context, c, selected.id, task.id);
                  }
                },
              ),
        emptyDetail: KitStateView(
          icon: Icons.work_outline,
          title: l.teamProjectSelect,
        ),
        side: selected != null && selectedTask != null
            ? TeamProjectConversation(
                key: ValueKey(selectedTask.id),
                controller: c,
                projectId: selected.id,
                taskId: selectedTask.id,
                embedded: true,
              )
            : KitStateView(
                icon: Icons.chat_bubble_outline,
                title: l.teamProjectSelectTask,
              ),
        bottom: TeamExecutionGate.allows(c, TeamExecutionNeed.lanes)
            ? KitActionBlock(
                primary: KitAction(
                  label: l.teamProjectNew,
                  onPressed: () => openTeamNewProject(context, c),
                ),
                tertiary: [
                  KitAction(
                    label: l.teamProjectQuick,
                    onPressed: () =>
                        openTeamNewProject(context, c, quick: true),
                  ),
                ],
              )
            : null,
      );
    },
  );
}
