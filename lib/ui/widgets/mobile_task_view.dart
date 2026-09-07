import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../domain/mobile_tool_view.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';

/// Bundled presentation only. Filtering changes the local view, never tasks.
/// Copying writes the parsed list to the local clipboard and nothing else.
/// There is deliberately no transport, URL, callback or credential form API.
class MobileTaskList extends StatefulWidget {
  const MobileTaskList({super.key, required this.view});
  final MobileTaskView view;
  @override
  State<MobileTaskList> createState() => _MobileTaskListState();
}

class _MobileTaskListState extends State<MobileTaskList> {
  bool _unfinishedOnly = false;
  bool _copying = false;

  /// Copies the FULL server-reported list, regardless of the local filter:
  /// the filter is a viewing aid and the button label says "all tasks".
  /// A clipboard failure is reported in place instead of surfacing as an
  /// unhandled async error.
  Future<void> _copyAll(AppLocalizations l10n) async {
    if (_copying) return;
    setState(() => _copying = true);
    final messenger = ScaffoldMessenger.maybeOf(context);
    String message;
    try {
      await Clipboard.setData(ClipboardData(text: widget.view.toPlainText()));
      message = l10n.mobileTasksCopied;
    } catch (_) {
      message = l10n.mobileTasksCopyFailed;
    }
    if (!mounted) return;
    setState(() => _copying = false);
    messenger?.showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final view = widget.view;
    final tasks = view.tasks.where(
      (task) =>
          !_unfinishedOnly ||
          task.status == MobileTaskStatus.pending ||
          task.status == MobileTaskStatus.inProgress,
    );
    final tracked = view.trackedCount;
    final done = view.completedCount;
    final progressText = l10n.mobileTasksProgress(done, tracked);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.mobileTasksDescription, style: theme.textTheme.labelSmall),
        // Progress counts only tracked (non-cancelled) tasks and ignores the
        // local filter. The indicator carries the accessible label and value
        // so the visible caption is not announced twice.
        if (tracked > 0) ...[
          const SizedBox(height: 4),
          ExcludeSemantics(
            child: Text(
              progressText,
              key: const Key('mobile-tasks-progress'),
              style: theme.textTheme.labelSmall?.copyWith(
                color: AppTheme.mutedOf(theme),
              ),
            ),
          ),
          const SizedBox(height: 4),
          ClipRRect(
            borderRadius: BorderRadius.circular(3),
            child: LinearProgressIndicator(
              key: const Key('mobile-tasks-progress-bar'),
              value: done / tracked,
              minHeight: 6,
              semanticsLabel: progressText,
              semanticsValue: '${(done * 100 / tracked).round()}%',
            ),
          ),
        ],
        const SizedBox(height: 6),
        Wrap(
          spacing: 8,
          runSpacing: 4,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            _filterToggle(l10n),
            TextButton.icon(
              key: const Key('mobile-tasks-copy-all'),
              onPressed: _copying ? null : () => _copyAll(l10n),
              icon: const Icon(Icons.copy_rounded, size: 16),
              label: Text(l10n.mobileTasksCopyAll),
            ),
          ],
        ),
        if (tasks.isEmpty) Text(l10n.mobileTasksNoUnfinished),
        for (final task in tasks)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 5),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(
                  switch (task.status) {
                    MobileTaskStatus.pending => Icons.radio_button_unchecked,
                    MobileTaskStatus.inProgress => Icons.hourglass_top_rounded,
                    MobileTaskStatus.completed =>
                      Icons.check_circle_outline_rounded,
                    MobileTaskStatus.cancelled => Icons.cancel_outlined,
                  },
                  size: 18,
                  color: task.status == MobileTaskStatus.completed
                      ? AppTheme.successOf(theme)
                      : AppTheme.mutedOf(theme),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(task.text, style: theme.textTheme.bodySmall),
                      _caption(l10n, theme, task),
                    ],
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }

  /// The local "unfinished only" filter. A button rather than a FilterChip:
  /// chips lay their label out on a single line and fade it once it no
  /// longer fits (visible at 320 px with 2x text), whereas a button label
  /// wraps, so the full wording stays readable at any text scale. The
  /// selected state is still announced like a chip's.
  Widget _filterToggle(AppLocalizations l10n) {
    final label = Text(
      l10n.mobileTasksUnfinished,
      key: const Key('mobile-tasks-filter-label'),
    );
    void toggle() => setState(() => _unfinishedOnly = !_unfinishedOnly);
    return MergeSemantics(
      child: Semantics(
        selected: _unfinishedOnly,
        child: _unfinishedOnly
            ? FilledButton.tonalIcon(
                key: const Key('mobile-tasks-filter'),
                onPressed: toggle,
                icon: const Icon(Icons.check_rounded, size: 16),
                label: label,
              )
            : OutlinedButton.icon(
                key: const Key('mobile-tasks-filter'),
                onPressed: toggle,
                icon: const Icon(Icons.filter_list_rounded, size: 16),
                label: label,
              ),
      ),
    );
  }

  /// `Status · Priority` on one wrapping line. High priority is tinted so it
  /// stands out at a glance; the words carry the meaning for everyone else.
  Widget _caption(AppLocalizations l10n, ThemeData theme, MobileTaskItem task) {
    final muted = theme.textTheme.labelSmall?.copyWith(
      color: AppTheme.mutedOf(theme),
    );
    final status = switch (task.status) {
      MobileTaskStatus.pending => l10n.mobileTaskPending,
      MobileTaskStatus.inProgress => l10n.mobileTaskInProgress,
      MobileTaskStatus.completed => l10n.mobileTaskCompleted,
      MobileTaskStatus.cancelled => l10n.mobileTaskCancelled,
    };
    final priority = switch (task.priority) {
      null => null,
      MobileTaskPriority.high => l10n.mobileTaskPriorityHigh,
      MobileTaskPriority.medium => l10n.mobileTaskPriorityMedium,
      MobileTaskPriority.low => l10n.mobileTaskPriorityLow,
    };
    if (priority == null) return Text(status, style: muted);
    return Text.rich(
      TextSpan(
        style: muted,
        children: [
          TextSpan(text: '$status · '),
          TextSpan(
            text: priority,
            style: task.priority == MobileTaskPriority.high
                ? TextStyle(
                    color: theme.colorScheme.tertiary,
                    fontWeight: FontWeight.w600,
                  )
                : null,
          ),
        ],
      ),
    );
  }
}
