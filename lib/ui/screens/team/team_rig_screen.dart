/// A project of the host's team (Gas City "rig") on its own page: where it
/// stands, and what a person can do with it — pause it, resume it, take it
/// out of the team. Every act names the project, and the ones that stop work
/// or delete ask first, saying what goes and what stays.
///
/// Reached from the team home's Projects list. Controls show only when the
/// host offers them ([OrchestrationCapabilities.controlProject],
/// [controlProjectRemove]).
library;

import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/orchestration_gateway.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/orchestration.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/team_controls.dart' show teamControlReceipt;
import '../../widgets/team_job_words.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Opens [projectId]'s page over the current one.
Future<void> openTeamRig(
  BuildContext context,
  OrchestrationController controller,
  String projectId,
) => Navigator.of(context).push(
  KitPageRoute<void>(
    builder: (_) => TeamRigScreen(controller: controller, projectId: projectId),
  ),
);

class TeamRigScreen extends StatefulWidget {
  const TeamRigScreen({
    super.key,
    required this.controller,
    required this.projectId,
  });

  final OrchestrationController controller;
  final String projectId;

  @override
  State<TeamRigScreen> createState() => _TeamRigScreenState();
}

class _TeamRigScreenState extends State<TeamRigScreen> {
  bool _busy = false;

  OrchestrationController get _controller => widget.controller;

  @override
  void initState() {
    super.initState();
    // After the first frame: reading tells the controller's listeners
    // (the page under this one), which must not happen while building.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _controller.capabilities.scheduledJobs) {
        unawaited(_controller.scheduledJobs());
      }
    });
  }

  OrchestrationProject? get _project {
    for (final project in _controller.snapshot.projects) {
      if (project.id == widget.projectId) return project;
    }
    return null;
  }

  Future<void> _send(
    OrchestrationProject project,
    ProjectControlAction action,
  ) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final record = await _controller.controlProject(project.id, action);
      if (!mounted) return;
      // A removed project has no page left to show.
      if (action == ProjectControlAction.remove &&
          record.status == MutationStatus.confirmed) {
        Navigator.of(context).maybePop();
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _pause(OrchestrationProject project) async {
    final l10n = _copy(context);
    final ok = await showKitConfirm(
      context,
      title: l10n.teamRigPauseTitle(project.name),
      body: l10n.teamRigPauseBody(project.name),
      confirmLabel: l10n.teamRigPauseConfirm,
      kind: KitConfirmKind.stop,
      sheetKey: const ValueKey('team-rig-pause-confirm'),
      confirmKey: const ValueKey('team-rig-pause-confirm-action'),
    );
    if (!ok || !mounted) return;
    await _send(project, ProjectControlAction.suspend);
  }

  Future<void> _delete(OrchestrationProject project) async {
    final l10n = _copy(context);
    final ok = await showKitConfirm(
      context,
      title: l10n.teamRigDeleteTitle(project.name),
      body: l10n.teamRigDeleteBody(project.name),
      confirmLabel: l10n.teamRigDeleteConfirm,
      kind: KitConfirmKind.destructive,
      consequenceItems: [
        KitConsequence(l10n.teamRigDeleteLost, mark: KitConsequenceMark.lost),
        KitConsequence(
          l10n.teamRigDeleteKeptFiles,
          mark: KitConsequenceMark.kept,
        ),
        KitConsequence(
          l10n.teamRigDeleteKeptHistory,
          mark: KitConsequenceMark.kept,
        ),
      ],
      sheetKey: const ValueKey('team-rig-delete-confirm'),
      confirmKey: const ValueKey('team-rig-delete-confirm-action'),
    );
    if (!ok || !mounted) return;
    await _send(project, ProjectControlAction.remove);
  }

  /// The project's scheduled jobs, each a switch with its plain "when"; the
  /// team's own (no project) follow under their own label. Loading, failed
  /// and empty are each said in words.
  List<Widget> _jobs(
    BuildContext context,
    AppLocalizations l10n,
    OrchestrationProject project,
  ) {
    final caps = _controller.capabilities;
    if (!caps.scheduledJobs) return const [];
    final all = _controller.scheduledJobList;
    if (all == null) {
      if (_controller.scheduledJobsError == null) return const [];
      return [
        KitSectionLabel(l10n.teamJobsTitle),
        KitNotice(
          key: const ValueKey('team-jobs-failed'),
          tone: AppStatusTone.failure,
          icon: AppIconography.warning,
          title: l10n.teamJobsFailedTitle,
          message: l10n.teamJobsFailedBody,
          actions: [
            KitAction(
              key: const ValueKey('team-jobs-retry'),
              label: l10n.commonRetry,
              onPressed: () =>
                  unawaited(_controller.scheduledJobs(force: true)),
            ),
          ],
        ),
      ];
    }
    final mine = [
      for (final job in all)
        if (job.projectId == project.id) job,
    ];
    final team = [
      for (final job in all)
        if (job.projectId == null || job.projectId!.isEmpty) job,
    ];
    Widget row(ScheduledJob job) {
      final record = _controller.latestMutation(
        kind: MutationKind.controlScheduledJob,
        targetId: job.id,
      );
      return KitSwitchRow(
        key: ValueKey('team-job-${job.id}'),
        switchKey: ValueKey('team-job-switch-${job.id}'),
        title: job.name,
        supporting: teamJobWhen(l10n, job),
        value: job.enabled,
        onChanged: caps.controlScheduledJobs
            ? (on) => unawaited(
                _controller.controlScheduledJob(job.id, enabled: on),
              )
            : null,
        below: record == null || record.status == MutationStatus.confirmed
            ? null
            : teamControlReceipt(
                context,
                record,
                key: ValueKey('team-job-receipt-${job.id}'),
                onRetry: record.canRetry
                    ? () async {
                        await _controller.retryMutation(record.key);
                      }
                    : null,
                retryKey: ValueKey('team-job-receipt-retry-${job.id}'),
              ),
      );
    }

    return [
      KitSectionLabel(l10n.teamJobsTitle),
      if (mine.isEmpty)
        KitStateView(
          key: const ValueKey('team-jobs-empty'),
          size: KitStateSize.inline,
          liveRegion: false,
          icon: AppIconography.clock,
          title: l10n.teamJobsEmptyTitle,
          body: l10n.teamJobsEmptyBody,
        )
      else
        KitRowGroup(
          key: const ValueKey('team-jobs'),
          children: [for (final job in mine) row(job)],
        ),
      if (team.isNotEmpty) ...[
        KitSectionLabel(l10n.teamJobsWholeTeamTitle),
        KitRowGroup(
          key: const ValueKey('team-jobs-team'),
          children: [for (final job in team) row(job)],
        ),
      ],
    ];
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _controller,
    builder: (context, _) => _page(context),
  );

  Widget _page(BuildContext context) {
    final l10n = _copy(context);
    final project = _project;
    if (project == null) {
      return KitScreen(
        key: const ValueKey('team-rig'),
        topBar: KitTopBar(title: widget.projectId),
        body: KitStateView(
          key: const ValueKey('team-rig-gone'),
          icon: AppIconography.projects,
          title: l10n.teamRigNotFoundTitle,
          body: l10n.teamRigNotFoundBody,
        ),
      );
    }
    final caps = _controller.capabilities;
    final paused = project.suspended;
    final record = _controller.latestMutation(
      kind: MutationKind.controlProject,
      targetId: project.id,
    );
    final canControl = caps.controlProject;
    final canRemove = caps.controlProjectRemove;
    return KitScreen(
      key: const ValueKey('team-rig'),
      topBar: KitTopBar(
        title: project.name,
        subtitle: paused ? l10n.teamRigStatePaused : l10n.teamRigStateActive,
      ),
      loading:
          caps.scheduledJobs &&
          _controller.scheduledJobsLoading &&
          _controller.scheduledJobList == null,
      loadingLabel: l10n.teamJobsLoading,
      body: ListView(
        padding: KitScreen.padding(context),
        children: [
          if (record != null)
            teamControlReceipt(
              context,
              record,
              key: const ValueKey('team-rig-receipt'),
              onRetry: record.canRetry
                  ? () async {
                      await _controller.retryMutation(record.key);
                    }
                  : null,
              retryKey: const ValueKey('team-rig-receipt-retry'),
            ),
          if (paused)
            KitNotice(
              key: const ValueKey('team-rig-paused'),
              icon: AppIconography.pause,
              title: l10n.teamRigPausedTitle(project.name),
              message: l10n.teamRigPausedBody,
            ),
          if (canControl || canRemove)
            KitRowGroup(
              children: [
                if (canControl)
                  KitRow(
                    key: ValueKey(
                      paused ? 'team-rig-resume' : 'team-rig-pause',
                    ),
                    leading: KitRow.icon(
                      context,
                      paused ? AppIconography.play : AppIconography.pause,
                    ),
                    title: paused
                        ? l10n.teamRigResume(project.name)
                        : l10n.teamRigPause(project.name),
                    titleMaxLines: 2,
                    onTap: _busy
                        ? null
                        : paused
                        ? () => unawaited(
                            _send(project, ProjectControlAction.resume),
                          )
                        : () => unawaited(_pause(project)),
                  ),
                if (canRemove)
                  KitRow(
                    key: const ValueKey('team-rig-delete'),
                    leading: KitRow.icon(context, AppIconography.delete),
                    title: l10n.teamRigDelete(project.name),
                    titleMaxLines: 2,
                    destructive: true,
                    onTap: _busy ? null : () => unawaited(_delete(project)),
                  ),
              ],
            ),
          ..._jobs(context, l10n, project),
          KitDetailsFold(
            foldKey: const ValueKey('team-rig-technical'),
            label: l10n.teamUiTechnicalDetails,
            values: [
              if (project.directory != null && project.directory!.isNotEmpty)
                KitTechnicalValue(l10n.teamRigFolderLabel, project.directory!),
              KitTechnicalValue(l10n.teamAgentScreenLabelId, project.id),
            ],
          ),
        ],
      ),
    );
  }
}
