/// App-owned declarative task-list schema. Server data supplies only plain
/// task text/status; it cannot choose widgets, actions, URLs or code.
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
      total += text.length;
      if (text.trim().isEmpty ||
          text.length > maxTextLength ||
          total > maxTotalTextLength ||
          status == null) {
        return null;
      }
      tasks.add(MobileTaskItem(text, status));
    }
    return MobileTaskView._(List.unmodifiable(tasks));
  }

  /// Bounded plain fallback when structured rendering is unsupported. It
  /// never turns unknown statuses into completion or makes text executable.
  static String fallback(Object? todos) {
    if (todos is! List) return '';
    final lines = <String>[];
    for (final item in todos.take(24)) {
      if (item is! Map) continue;
      final content = item['content'];
      final status = item['status'];
      if (content is String) {
        final text = content.length > 1024
            ? '${content.substring(0, 1024)}…'
            : content;
        final state = status is String && status.length <= 40
            ? '[$status] '
            : '';
        lines.add('$state$text');
      }
    }
    if (todos.length > 24) lines.add('…');
    return lines.join('\n');
  }
}

enum MobileTaskStatus { pending, inProgress, completed, cancelled }

class MobileTaskItem {
  const MobileTaskItem(this.text, this.status);
  final String text;
  final MobileTaskStatus status;
}
