import 'dart:async';

import 'package:flutter/material.dart';

import '../../../builtin/git_link_repair.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../app_theme.dart' show AppStatusTone;
import '../../kit/kit.dart';

/// Repairs [project]'s Git history in a sheet: it starts at once, says how
/// it goes, then what happened and what to do next. True when Git can read
/// the project again (the caller starts the conversation then).
Future<bool> showGitRepairSheet(
  BuildContext context, {
  required String project,
  required GitRepairActions actions,
}) async {
  final l10n = AppLocalizations.of(context);
  final repaired = await showKitSheet<bool>(
    context,
    sheetKey: const ValueKey('git-repair-sheet'),
    title: l10n.gitRepairTitle,
    subtitle: project,
    icon: AppIconography.branch,
    body: (_) => _GitRepairBody(project: project, actions: actions),
  );
  return repaired ?? false;
}

class _GitRepairBody extends StatefulWidget {
  const _GitRepairBody({required this.project, required this.actions});

  final String project;
  final GitRepairActions actions;

  @override
  State<_GitRepairBody> createState() => _GitRepairBodyState();
}

class _GitRepairBodyState extends State<_GitRepairBody> {
  GitRepairProgress _progress = const GitRepairProgress(GitRepairStage.looking);
  GitRepairOutcome? _outcome;
  bool _running = false;

  @override
  void initState() {
    super.initState();
    unawaited(_run());
  }

  Future<void> _run() async {
    if (_running) return;
    setState(() {
      _running = true;
      _outcome = null;
      _progress = const GitRepairProgress(GitRepairStage.looking);
    });
    GitRepairOutcome outcome;
    try {
      outcome = await widget.actions.run((progress) {
        if (mounted) setState(() => _progress = progress);
      });
    } catch (error) {
      outcome = GitRepairOutcome(
        GitRepairResult.cannotRepair,
        details: '${error.runtimeType}',
      );
    }
    if (!mounted) return;
    setState(() {
      _running = false;
      _outcome = outcome;
    });
  }

  void _close(bool repaired) => Navigator.of(context).pop(repaired);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final outcome = _outcome;
    if (outcome == null) return _working(l10n);
    final project = widget.project;
    final details = outcome.details.trim().isEmpty ? null : outcome.details;
    return switch (outcome.result) {
      GitRepairResult.repaired || GitRepairResult.healthy => KitStateView(
        key: const ValueKey('git-repair-done'),
        size: KitStateSize.inline,
        icon: AppIconography.checkCircle,
        tone: AppStatusTone.ok,
        title: outcome.result == GitRepairResult.repaired
            ? l10n.gitRepairDoneTitle(project)
            : l10n.gitRepairHealthyTitle,
        body: outcome.result == GitRepairResult.repaired
            ? l10n.gitRepairDoneBody(outcome.restored)
            : l10n.gitRepairHealthyBody(project),
        primary: KitAction(
          key: const ValueKey('git-repair-start'),
          label: l10n.gitRepairStart,
          onPressed: () => _close(true),
        ),
        details: details,
      ),
      GitRepairResult.cannotRepair => KitStateView(
        key: const ValueKey('git-repair-cannot'),
        size: KitStateSize.inline,
        icon: AppIconography.warning,
        tone: AppStatusTone.neutral,
        title: l10n.gitRepairCannotTitle(project),
        body: outcome.missing.isEmpty
            ? l10n.gitRepairStillBrokenBody
            : l10n.gitRepairMissingBody(outcome.missing.length),
        primary: KitAction(
          label: l10n.gitRepairClose,
          onPressed: () => _close(false),
        ),
        details: details,
      ),
      GitRepairResult.notRepository => KitStateView(
        key: const ValueKey('git-repair-not-repository'),
        size: KitStateSize.inline,
        icon: AppIconography.warning,
        title: l10n.gitRepairNotRepositoryTitle,
        body: l10n.gitRepairNotRepositoryBody,
        primary: KitAction(
          label: l10n.gitRepairClose,
          onPressed: () => _close(false),
        ),
      ),
    };
  }

  Widget _working(AppLocalizations l10n) {
    final progress = _progress;
    final line = switch (progress.stage) {
      GitRepairStage.looking => l10n.gitRepairLooking,
      GitRepairStage.restoring => l10n.gitRepairRestoring(
        progress.done,
        progress.total,
      ),
      GitRepairStage.checking => l10n.gitRepairChecking,
    };
    final counted =
        progress.total > 0 && progress.stage == GitRepairStage.restoring;
    return KitStateView(
      key: const ValueKey('git-repair-working'),
      size: KitStateSize.inline,
      icon: AppIconography.branch,
      tone: AppStatusTone.progress,
      title: l10n.gitRepairWorking(widget.project),
      progress: counted
          ? KitProgress.known(
              (progress.done / progress.total).clamp(0, 1).toDouble(),
              caption: line,
              key: ValueKey('git-repair-progress-${progress.stage.name}'),
            )
          : KitProgress.waiting(caption: line),
    );
  }
}
