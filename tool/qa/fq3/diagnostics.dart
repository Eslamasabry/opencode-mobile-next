/// Summarizes a disposable OC2 reply using the app's type/content wire shape.
/// Raw message contents and arbitrary errors never leave this projection.
Map<String, Object> oc2DiagnosticReplyFacts(
  List<Map<String, dynamic>> messages, {
  required String sessionID,
  required String expectedToken,
}) {
  var completed = 0;
  var contains = false;
  var foreign = 0;
  for (final message in messages) {
    if (message.containsKey('sessionID') && message['sessionID'] != sessionID) {
      foreign++;
      continue;
    }
    final time = message['time'];
    final end = time is Map ? time['completed'] : null;
    if (message['type'] != 'assistant' ||
        message['error'] != null ||
        end is! num ||
        !end.isFinite ||
        end <= 0 ||
        message['finish'] == null) {
      continue;
    }
    completed++;
    final content = message['content'];
    if (content is! List) continue;
    final text = content
        .whereType<Map>()
        .where(
          (part) =>
              part['type'] == 'text' &&
              part['synthetic'] != true &&
              part['text'] is String,
        )
        .map((part) => part['text'] as String)
        .join('\n');
    contains = contains || text.contains(expectedToken);
  }
  return {
    'completedReply': completed > 0,
    'expectedTokenInCompletedReply': contains,
    'completedAssistants': completed,
    'foreignMessages': foreign,
  };
}
