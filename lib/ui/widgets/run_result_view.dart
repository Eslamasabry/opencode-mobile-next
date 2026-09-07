import 'package:flutter/material.dart';

import '../../api/models.dart' show ToolState;
import '../../domain/run_result.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'tool_card.dart';

/// Pure presentation of one [RunResult]. Every line is either copied from a
/// server record or an explicit "unknown"; the only actions are opening the
/// conversation and opening a tool's own recorded output.
class RunResultView extends StatelessWidget {
  const RunResultView({
    super.key,
    required this.result,
    required this.observedLive,
    required this.onOpenConversation,
    this.sessionTitle,
  });

  final RunResult result;

  /// True only when the controller received the completion of
  /// [RunResult.lastStepID] itself as a live event on this connection.
  final bool observedLive;
  final VoidCallback onOpenConversation;
  final String? sessionTitle;

  static String shortID(String id) =>
      id.length <= 10 ? id : id.substring(id.length - 8);

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final muted = theme.textTheme.bodySmall?.copyWith(
      color: AppTheme.mutedOf(theme),
    );
    return ListView(
      key: const Key('run-result-view'),
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        if (sessionTitle?.trim().isNotEmpty == true)
          Text(sessionTitle!, style: theme.textTheme.titleMedium),
        const SizedBox(height: 4),
        _identity(context, l10n, muted),
        if (!result.boundaryKnown) ...[
          const SizedBox(height: 8),
          _notice(
            context,
            key: const Key('run-result-partial'),
            icon: Icons.history_toggle_off_rounded,
            text: l10n.runResultsPartialHistory,
          ),
        ],
        const SizedBox(height: 12),
        _outcome(context, l10n, muted),
        const SizedBox(height: 6),
        Text(
          observedLive
              ? l10n.runResultsObservedLive
              : l10n.runResultsFromHistory,
          key: Key(observedLive ? 'run-result-observed' : 'run-result-history'),
          style: muted,
        ),
        const SizedBox(height: 16),
        if (!result.hasToolEvidence)
          _notice(
            context,
            key: const Key('run-result-no-tools'),
            icon: Icons.help_outline_rounded,
            text: l10n.runResultsNoToolEvidence,
          )
        else ...[
          _sectionTitle(context, l10n.runResultsChangedFilesTitle),
          Text(l10n.runResultsChangedFilesSource, style: muted),
          const SizedBox(height: 6),
          if (result.changedFiles.isEmpty)
            Text(
              l10n.runResultsNoChangedFiles,
              key: const Key('run-result-no-files'),
            )
          else
            for (final file in result.changedFiles)
              _fileRow(context, l10n, file),
          const SizedBox(height: 16),
          _sectionTitle(context, l10n.runResultsCommandsTitle),
          Text(l10n.runResultsCommandsSource, style: muted),
          const SizedBox(height: 6),
          if (result.commands.isEmpty)
            Text(
              l10n.runResultsNoCommands,
              key: const Key('run-result-no-commands'),
            )
          else
            for (final command in result.commands)
              _commandRow(context, l10n, command),
          if (result.prunedToolCount > 0) ...[
            const SizedBox(height: 6),
            Text(
              l10n.runResultsPrunedTools(result.prunedToolCount),
              style: muted,
            ),
          ],
          if (result.truncated) ...[
            const SizedBox(height: 6),
            Text(l10n.runResultsTruncated, style: muted),
          ],
        ],
        const SizedBox(height: 16),
        Text(l10n.runResultsSourceNote, style: muted),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: FilledButton.tonalIcon(
            key: const Key('run-result-open-conversation'),
            onPressed: onOpenConversation,
            icon: const Icon(Icons.chat_bubble_outline_rounded, size: 18),
            label: Text(l10n.runResultsOpenConversation),
          ),
        ),
      ],
    );
  }

  Widget _identity(
    BuildContext context,
    AppLocalizations l10n,
    TextStyle? muted,
  ) {
    final theme = Theme.of(context);
    final steps = result.boundaryKnown
        ? l10n.runResultsSteps(result.stepCount)
        : l10n.runResultsStepsAtLeast(result.stepCount);
    final who = [
      result.agent,
      result.model,
    ].whereType<String>().where((s) => s.trim().isNotEmpty).join(' · ');
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.runResultsRunLabel(shortID(result.runID)),
          key: const Key('run-result-id'),
          style: theme.textTheme.titleSmall,
        ),
        Text(steps, style: muted),
        if (who.isNotEmpty) Text(who, style: muted),
        Text(
          result.startedAt == null
              ? l10n.runResultsStartedUnknown
              : l10n.runResultsStarted(_when(context, result.startedAt!)),
          style: muted,
        ),
        Text(
          result.finishedAt == null
              ? l10n.runResultsFinishedUnknown
              : l10n.runResultsFinished(_when(context, result.finishedAt!)),
          style: muted,
        ),
      ],
    );
  }

  Widget _outcome(
    BuildContext context,
    AppLocalizations l10n,
    TextStyle? muted,
  ) {
    final theme = Theme.of(context);
    final outcome = result.outcome;
    final (label, icon, color) = switch (outcome.kind) {
      RunOutcomeKind.completed => (
        l10n.runResultsOutcomeCompleted,
        Icons.check_circle_outline_rounded,
        AppTheme.successOf(theme),
      ),
      RunOutcomeKind.cutOff => (
        l10n.runResultsOutcomeCutOff,
        Icons.content_cut_rounded,
        theme.colorScheme.tertiary,
      ),
      RunOutcomeKind.failed => (
        l10n.runResultsOutcomeFailed,
        Icons.error_outline_rounded,
        theme.colorScheme.error,
      ),
      RunOutcomeKind.aborted => (
        l10n.runResultsOutcomeAborted,
        Icons.block_rounded,
        theme.colorScheme.error,
      ),
      RunOutcomeKind.running => (
        l10n.runResultsOutcomeRunning,
        Icons.hourglass_top_rounded,
        theme.colorScheme.primary,
      ),
      RunOutcomeKind.notReported => (
        l10n.runResultsOutcomeNotReported,
        Icons.help_outline_rounded,
        AppTheme.mutedOf(theme),
      ),
    };
    final finish = outcome.finish?.trim();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, size: 20, color: color),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                label,
                key: const Key('run-result-outcome'),
                style: theme.textTheme.titleMedium?.copyWith(color: color),
              ),
            ),
          ],
        ),
        if (outcome.errorHeadline != null)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              outcome.errorHeadline!,
              key: const Key('run-result-error'),
              style: theme.textTheme.bodyMedium,
            ),
          ),
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(
            finish == null || finish.isEmpty
                ? l10n.runResultsFinishReasonMissing
                : l10n.runResultsFinishReason(finish),
            style: muted,
          ),
        ),
        if (result.earlierStepErrors > 0)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(
              l10n.runResultsEarlierErrors(result.earlierStepErrors),
              key: const Key('run-result-earlier-errors'),
              style: muted,
            ),
          ),
      ],
    );
  }

  Widget _sectionTitle(BuildContext context, String text) => Padding(
    padding: const EdgeInsets.only(bottom: 2),
    child: Text(text, style: Theme.of(context).textTheme.titleSmall),
  );

  Widget _notice(
    BuildContext context, {
    required Key key,
    required IconData icon,
    required String text,
  }) {
    final theme = Theme.of(context);
    return Material(
      key: key,
      color: theme.colorScheme.surfaceContainerHigh,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, size: 18, color: AppTheme.mutedOf(theme)),
            const SizedBox(width: 8),
            Expanded(child: Text(text, style: theme.textTheme.bodySmall)),
          ],
        ),
      ),
    );
  }

  Widget _fileRow(
    BuildContext context,
    AppLocalizations l10n,
    RunChangedFile file,
  ) {
    final theme = Theme.of(context);
    final change = switch (file.change) {
      RunFileChange.edited => l10n.runResultsChangeEdited,
      RunFileChange.written => l10n.runResultsChangeWritten,
      RunFileChange.patched => l10n.runResultsChangePatched,
    };
    return ListTile(
      key: Key('run-result-file-${file.path}'),
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: const Icon(Icons.edit_note_rounded, size: 20),
      title: Text(file.path, style: theme.textTheme.bodyMedium),
      subtitle: Text(
        file.state.pruned ? '$change · ${l10n.runResultsOutputPruned}' : change,
      ),
      trailing: file.state.pruned
          ? null
          : const Icon(Icons.open_in_new_rounded, size: 16),
      onTap: file.state.pruned
          ? null
          : () => _openOutput(context, l10n, file.toolName, file.state),
    );
  }

  Widget _commandRow(
    BuildContext context,
    AppLocalizations l10n,
    RunCommand command,
  ) {
    final theme = Theme.of(context);
    final exit = command.exitCode == null
        ? l10n.runResultsExitUnknown
        : l10n.runResultsExit(command.exitCode!);
    final failed = command.failed || (command.exitCode ?? 0) != 0;
    final caption = [
      exit,
      if (command.failed) l10n.runResultsCommandFailed,
      if (command.looksLikeTest) l10n.runResultsLooksLikeTest,
      if (command.outputPruned) l10n.runResultsOutputPruned,
    ].join(' · ');
    return ListTile(
      key: Key('run-result-command-${command.partID ?? command.command}'),
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: Icon(
        failed ? Icons.error_outline_rounded : Icons.terminal_rounded,
        size: 20,
        color: failed ? theme.colorScheme.error : null,
      ),
      title: Text(
        command.command.isEmpty ? l10n.runResultsCommandEmpty : command.command,
        style: theme.textTheme.bodyMedium?.copyWith(
          fontFamily: AppTheme.monoFamily,
        ),
      ),
      subtitle: Text(caption),
      trailing: command.outputPruned
          ? null
          : const Icon(Icons.open_in_new_rounded, size: 16),
      onTap: command.outputPruned
          ? null
          : () => _openOutput(context, l10n, command.toolName, command.state),
    );
  }

  /// The underlying record, rendered by the same ToolCard the transcript
  /// uses. Nothing is re-fetched or re-summarised.
  Future<void> _openOutput(
    BuildContext context,
    AppLocalizations l10n,
    String toolName,
    ToolState state,
  ) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        minChildSize: 0.3,
        maxChildSize: 0.95,
        builder: (_, scrollController) => ListView(
          key: const Key('run-result-output-sheet'),
          controller: scrollController,
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
          children: [
            Text(
              l10n.runResultsOutputTitle,
              style: Theme.of(sheetContext).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            ToolCard(toolName: toolName, state: state),
          ],
        ),
      ),
    );
  }

  static String _when(BuildContext context, DateTime time) {
    final local = MaterialLocalizations.of(context);
    final now = DateTime.now();
    final sameDay =
        time.year == now.year && time.month == now.month && time.day == now.day;
    final clock = local.formatTimeOfDay(TimeOfDay.fromDateTime(time));
    return sameDay ? clock : '${local.formatMediumDate(time)} $clock';
  }
}
