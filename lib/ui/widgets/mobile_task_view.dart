import 'package:flutter/material.dart';

import '../../domain/mobile_tool_view.dart';
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';

/// Bundled presentation only. Filtering changes the local view, never tasks.
/// There is deliberately no transport, URL, callback or credential form API.
class MobileTaskList extends StatefulWidget {
  const MobileTaskList({super.key, required this.view});
  final MobileTaskView view;
  @override
  State<MobileTaskList> createState() => _MobileTaskListState();
}

class _MobileTaskListState extends State<MobileTaskList> {
  bool _unfinishedOnly = false;
  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final tasks = widget.view.tasks.where(
      (task) =>
          !_unfinishedOnly ||
          task.status == MobileTaskStatus.pending ||
          task.status == MobileTaskStatus.inProgress,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.mobileTasksDescription, style: theme.textTheme.labelSmall),
        FilterChip(
          label: Text(l10n.mobileTasksUnfinished),
          selected: _unfinishedOnly,
          onSelected: (value) => setState(() => _unfinishedOnly = value),
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
                      Text(
                        switch (task.status) {
                          MobileTaskStatus.pending => l10n.mobileTaskPending,
                          MobileTaskStatus.inProgress =>
                            l10n.mobileTaskInProgress,
                          MobileTaskStatus.completed =>
                            l10n.mobileTaskCompleted,
                          MobileTaskStatus.cancelled =>
                            l10n.mobileTaskCancelled,
                        },
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
