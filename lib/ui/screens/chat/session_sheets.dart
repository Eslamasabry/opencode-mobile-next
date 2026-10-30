part of '../chat_screen.dart';

/// The agent's plan has one home: the tasks chip in the line above the field
/// and the Tasks sheet it opens. Each `todowrite` call in the transcript is
/// only a one-line "Updated tasks" step, so the list is never drawn twice.
/// The command sheet's Tasks (/plan) opens the same sheet.
extension _ChatPlan on _ChatScreenState {
  static const _planTools = {'todowrite', 'todo'};

  /// The latest plan in the loaded transcript: the reply that holds it and
  /// the tool call itself. Null when the agent has not planned here yet.
  ({MessageWithParts message, Part part})? get _latestPlan {
    for (final message in _visibleHistory.toList().reversed) {
      if (message.info.role != 'assistant') continue;
      for (final part in message.parts.reversed) {
        if (part.type == 'tool' && _planTools.contains(part.toolName)) {
          return (message: message, part: part);
        }
      }
    }
    return null;
  }

  /// The agent's latest task list, parsed; null when it has none (or none
  /// that counts: every task cancelled).
  MobileTaskView? get _latestTasks {
    final plan = _latestPlan;
    if (plan == null) return null;
    final state = plan.part.toolState;
    final view = MobileTaskView.fromTodos(
      state.metadata?['todos'] ?? state.input['todos'],
    );
    return view == null || view.trackedCount == 0 ? null : view;
  }

  /// "Step 3 of 5" under the running turn's live line, only when the agent
  /// wrote a todo list that still has unfinished tasks (owner decision 8).
  String? _planStep(BuildContext context) {
    final view = _latestTasks;
    if (view == null || view.completedCount >= view.trackedCount) return null;
    return _chatL10n(
      context,
    ).kitProgressStep(view.completedCount + 1, view.trackedCount);
  }

  /// The "Plan" row of the composer's + sheet: how far the agent's task list
  /// is, and what opens it. Null when the agent wrote none.
  ({String progress, String? current, VoidCallback open})? get _planRow {
    final view = _latestTasks;
    if (view == null) return null;
    final working = view.tasks
        .where((task) => task.status == MobileTaskStatus.inProgress)
        .firstOrNull;
    return (
      progress: '${view.completedCount}/${view.trackedCount}',
      current: working?.text,
      open: _openPlan,
    );
  }

  /// Opens the whole list in the Tasks sheet.
  void _openPlan() {
    final view = _latestTasks;
    if (view == null) return;
    unawaited(
      showKitTaskSheet(
        context,
        done: view.completedCount,
        total: view.trackedCount,
        running:
            _conn.busySessions.contains(widget.sessionID) ||
            _sending ||
            _localTurnSince != null,
        items: [
          for (final task in view.tasks)
            KitTaskItem(task.text, switch (task.status) {
              MobileTaskStatus.pending => KitTaskItemState.todo,
              MobileTaskStatus.inProgress => KitTaskItemState.inProgress,
              MobileTaskStatus.completed => KitTaskItemState.done,
              MobileTaskStatus.cancelled => KitTaskItemState.cancelled,
            }),
        ],
      ),
    );
  }
}
