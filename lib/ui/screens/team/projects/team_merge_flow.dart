// The two writes that change the person's code, as one flow each: Merge
// checked work into dev, and Promote dev to main. Both ask first, naming
// what will happen, show their progress inside the question, and leave a
// receipt row behind (R-61, R-64). Shared by the project overview and the
// task page so the two cannot drift apart.
import 'package:flutter/widgets.dart';

import '../../../../l10n/app_localizations.dart';
import '../../../../state/team_project_controller.dart';
import '../../../kit/kit.dart';

String _short(String v) => v.length > 7 ? v.substring(0, 7) : v;

/// "abc1234 → def5678", the before and after of one receipt.
String teamCommitChange(String before, String after) =>
    '${_short(before)} → ${_short(after)}';

/// Whether [repo]'s dev work may be promoted to main right now.
bool teamCanPromote(TeamProject project, TeamRepo? repo) {
  if (repo == null ||
      !project.planApproved ||
      repo.devCommit == repo.mainCommit) {
    return false;
  }
  final tasks = project.tasks.where((task) => task.repoId == repo.id).toList();
  final merges = project.mergeQueue
      .where((item) => item.repoId == repo.id)
      .toList();
  if (tasks.isEmpty ||
      tasks.any(
        (task) =>
            task.status != 'merged' ||
            task.findings.any((finding) => finding.status == 'open'),
      )) {
    return false;
  }
  if (merges.isEmpty ||
      merges.any((item) => item.status != 'merged' || !item.checksPassed)) {
    return false;
  }
  final phases = tasks.map((task) => task.phaseId).toSet();
  return !project.phases.any(
    (phase) =>
        phases.contains(phase.id) &&
        (phase.risky || project.settings.reviewLevel == 'everyStep') &&
        !phase.accepted,
  );
}

/// Asks, then merges every checked task of [repo] into dev. Always asks:
/// it writes the person's dev branch; progress shows inside the question.
Future<void> confirmAndMergeToDev(
  BuildContext context,
  TeamProjectController c,
  TeamProject p,
  TeamRepo repo,
) async {
  final l = lookupAppLocalizations(Localizations.localeOf(context));
  final waiting = p.mergeQueue
      .where((i) => i.repoId == repo.id && i.status == 'queued')
      .length;
  await showKitConfirm(
    context,
    title: l.teamProjectMergeConfirmTitle,
    body: l.teamProjectMergeConfirmBody,
    confirmLabel: l.teamProjectMergeNext,
    consequences: [
      l.teamProjectMergeEffectDev(repo.name, waiting),
      l.teamProjectMergeEffectMain,
    ],
    action: () async {
      await c.execute(
        TeamProjectCommand(
          requestId: c.newRequestId(),
          action: TeamProjectAction.processMergeQueue,
          projectId: p.id,
          expectedRevision: p.revision,
          targetId: repo.id,
          confirmed: true,
        ),
      );
    },
  );
}

/// Asks, then promotes [repo]'s dev to main, naming both commits.
Future<void> confirmAndPromote(
  BuildContext context,
  TeamProjectController c,
  TeamProject p,
  TeamRepo repo,
) async {
  final l = lookupAppLocalizations(Localizations.localeOf(context));
  await showKitConfirm(
    context,
    title: l.teamProjectTaskPromoteConfirmTitle,
    body: l.teamProjectTaskPromoteBody,
    confirmLabel: l.teamProjectTaskPromote,
    consequences: ['${repo.name}: ${repo.mainCommit} → ${repo.devCommit}'],
    action: () async {
      await c.execute(
        TeamProjectCommand(
          requestId: c.newRequestId(),
          action: TeamProjectAction.promote,
          projectId: p.id,
          expectedRevision: p.revision,
          targetId: repo.id,
          confirmed: true,
          expectedDevCommit: repo.devCommit,
          expectedMainCommit: repo.mainCommit,
        ),
      );
    },
  );
}

/// The newest receipt rows for [repo] (merges into dev and promotions),
/// oldest first, each naming its commits before and after.
List<KitTeamItem> teamReceiptItems(
  BuildContext context,
  TeamProject p,
  TeamRepo repo, {
  required String Function(String at) age,
  int limit = 3,
}) {
  final l = lookupAppLocalizations(Localizations.localeOf(context));
  final mine = p.receipts
      .where(
        (r) =>
            r.repoId == repo.id &&
            const ['merge', 'promote', 'promotion'].contains(r.kind),
      )
      .toList();
  final shown = mine.length > limit ? mine.sublist(mine.length - limit) : mine;
  return [
    for (final r in shown)
      KitTeamItem(
        title: r.kind == 'merge'
            ? l.teamProjectReceiptMerged(repo.name)
            : l.teamProjectReceiptPromoted(repo.name),
        detail: teamCommitChange(r.before, r.after),
        meta: age(r.at),
        state: KitTeamState.done,
      ),
  ];
}
