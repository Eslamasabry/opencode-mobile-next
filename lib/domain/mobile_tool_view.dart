/// App-owned declarative task-list schema. Server data supplies only plain
/// task text/status/priority; it cannot choose widgets, actions, URLs or code.
class MobileTaskView {
  const MobileTaskView._(this.tasks);
  static const rendererID = 'opencode.todo';
  static const version = 1;
  static const maxTasks = 64;
  static const maxTextLength = 1024;
  static const maxTotalTextLength = 32768;
  final List<MobileTaskItem> tasks;

  /// Adapter for the existing reviewed todos tool payload. This is not a
  /// discovered remote manifest or a declaration of plugin ownership.
  static MobileTaskView? fromTodos(Object? todos) => fromDeclaration({
    'renderer': rendererID,
    'version': version,
    'tasks': todos,
  });

  static MobileTaskView? fromDeclaration(Object? declaration) {
    if (declaration is! Map ||
        declaration['renderer'] != rendererID ||
        declaration['version'] != version ||
        declaration.keys.any(
          (k) => !const ['renderer', 'version', 'tasks'].contains(k),
        )) {
      return null;
    }
    final raw = declaration['tasks'];
    if (raw is! List || raw.isEmpty || raw.length > maxTasks) return null;
    final tasks = <MobileTaskItem>[];
    var total = 0;
    for (final item in raw) {
      if (item is! Map ||
          item['content'] is! String ||
          item['status'] is! String) {
        return null;
      }
      final text = item['content'] as String;
      final status = switch (item['status']) {
        'pending' => MobileTaskStatus.pending,
        'in_progress' => MobileTaskStatus.inProgress,
        'completed' => MobileTaskStatus.completed,
        'cancelled' => MobileTaskStatus.cancelled,
        _ => null,
      };
      // Priority is optional (older servers omit it) but, once present, only
      // the reviewed values render. Anything else falls back to plain text
      // exactly like an unknown status, so nothing is silently coerced.
      final rawPriority = item['priority'];
      final priority = switch (rawPriority) {
        'high' => MobileTaskPriority.high,
        'medium' => MobileTaskPriority.medium,
        'low' => MobileTaskPriority.low,
        _ => null,
      };
      total += text.length;
      if (text.trim().isEmpty ||
          text.length > maxTextLength ||
          total > maxTotalTextLength ||
          status == null ||
          (rawPriority != null && priority == null)) {
        return null;
      }
      tasks.add(MobileTaskItem(text, status, priority: priority));
    }
    return MobileTaskView._(List.unmodifiable(tasks));
  }

  /// Tasks that were finished, out of [trackedCount].
  int get completedCount =>
      tasks.where((t) => t.status == MobileTaskStatus.completed).length;

  /// Tasks still counting toward progress. Cancelled tasks are neither done
  /// nor outstanding, so they are left out of both numbers.
  int get trackedCount =>
      tasks.where((t) => t.status != MobileTaskStatus.cancelled).length;

  /// Deterministic, bounded plain rendering of the parsed view, in server
  /// order: `[status · priority] text` per line. Task text is copied as-is
  /// (it is already bounded by [maxTextLength]) so the clipboard receives
  /// exactly what the card shows. Uses the wire vocabulary, not UI labels.
  String toPlainText() => [
    for (final task in tasks)
      '[${task.status.wireName}'
          '${task.priority == null ? '' : ' · ${task.priority!.wireName}'}] '
          '${task.text}',
  ].join('\n');

  /// Bounded plain fallback when structured rendering is unsupported. It
  /// never turns unknown statuses or priorities into something else or makes
  /// text executable.
  static String fallback(Object? todos) {
    if (todos is! List) return '';
    final lines = <String>[];
    for (final item in todos.take(24)) {
      if (item is! Map) continue;
      final content = item['content'];
      final status = item['status'];
      final priority = item['priority'];
      if (content is String) {
        final text = content.length > 1024
            ? '${content.substring(0, 1024)}…'
            : content;
        final tags = [
          if (status is String && status.length <= 40) status,
          if (priority is String && priority.length <= 40) priority,
        ];
        final state = tags.isEmpty ? '' : '[${tags.join(' · ')}] ';
        lines.add('$state$text');
      }
    }
    if (todos.length > 24) lines.add('…');
    return lines.join('\n');
  }
}

enum MobileTaskStatus {
  pending('pending'),
  inProgress('in_progress'),
  completed('completed'),
  cancelled('cancelled');

  const MobileTaskStatus(this.wireName);

  /// The reviewed server vocabulary this status was parsed from.
  final String wireName;
}

enum MobileTaskPriority {
  high('high'),
  medium('medium'),
  low('low');

  const MobileTaskPriority(this.wireName);

  /// The reviewed server vocabulary this priority was parsed from.
  final String wireName;
}

class MobileTaskItem {
  const MobileTaskItem(this.text, this.status, {this.priority});
  final String text;
  final MobileTaskStatus status;

  /// Null when the server did not report a priority.
  final MobileTaskPriority? priority;
}
