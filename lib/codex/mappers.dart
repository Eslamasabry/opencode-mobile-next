import '../api/models.dart';
import 'transport.dart';

Map<String, dynamic> codexObject(Object? value) {
  if (value is! Map<String, dynamic>) {
    throw CodexFailure(CodexFailureKind.invalidResponse);
  }
  return value;
}

String codexString(Object? value, {int max = 4096, bool empty = false}) {
  if (value is! String || value.length > max || (!empty && value.isEmpty)) {
    throw CodexFailure(CodexFailureKind.invalidResponse);
  }
  return value;
}

List<dynamic> codexList(Object? value, {int max = 10000}) {
  if (value is! List || value.length > max) {
    throw CodexFailure(CodexFailureKind.invalidResponse);
  }
  return value;
}

int? codexMillis(Object? value) {
  if (value == null) return null;
  if (value is! int || value < 0 || value > 8640000000000) {
    throw CodexFailure(CodexFailureKind.invalidResponse);
  }
  return value * 1000;
}

Session codexSession(Map<String, dynamic> thread, {required String directory}) {
  final cwd = codexString(thread['cwd']);
  if (cwd != directory) throw CodexFailure(CodexFailureKind.scopeMismatch);
  final id = codexString(thread['id'], max: 256);
  final name = thread['name'];
  return Session(
    id: id,
    title: name is String && name.trim().isNotEmpty
        ? codexString(name, max: 4096)
        : 'Codex conversation',
    directory: cwd,
    projectID: thread['projectId'] is String
        ? thread['projectId'] as String
        : null,
    parentID: thread['parentThreadId'] is String
        ? thread['parentThreadId'] as String
        : null,
    time: SessionTime(
      created: codexMillis(thread['createdAt']),
      updated: codexMillis(thread['updatedAt']),
    ),
    model: thread['model'] is String ? 'codex/${thread['model']}' : null,
  );
}

String codexSessionStatus(Map<String, dynamic> thread) {
  final status = codexObject(thread['status']);
  return switch (status['type']) {
    'active' => 'busy',
    'idle' || 'notLoaded' || 'systemError' => 'idle',
    _ => throw CodexFailure(CodexFailureKind.invalidResponse),
  };
}

/// IDs come from persisted items, so streamed and resumed messages coalesce.
MessageWithParts codexItemMessage(
  String threadID,
  Map<String, dynamic> item, {
  int? created,
  int? completed,
  String? turnStatus,
}) {
  final id = codexString(item['id'], max: 256);
  final type = codexString(item['type'], max: 128);
  final parts = <Part>[];
  switch (type) {
    case 'userMessage':
      final content = codexList(item['content'], max: 1000);
      for (var i = 0; i < content.length; i++) {
        final value = codexObject(content[i]);
        parts.add(
          Part(
            id: '$id:$i',
            messageID: id,
            type: 'text',
            text: value['type'] == 'text'
                ? codexString(value['text'], max: 1024 * 1024, empty: true)
                : 'This Codex input is available on the server.',
          ),
        );
      }
    case 'agentMessage':
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          type: 'text',
          text: codexString(item['text'], max: 1024 * 1024, empty: true),
        ),
      );
    case 'reasoning':
      final summary = codexList(item['summary'] ?? const [], max: 1000);
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          type: 'reasoning',
          text: summary
              .map((s) => codexString(s, max: 65536, empty: true))
              .join('\n'),
        ),
      );
    case 'commandExecution':
      final state = codexString(item['status'], max: 32);
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          callID: id,
          type: 'tool',
          toolName: 'shell',
          toolState: ToolState.fromJson({
            'status': switch (state) {
              'inProgress' => 'running',
              'completed'
                  when item['exitCode'] == 0 || item['exitCode'] == null =>
                'completed',
              _ => 'error',
            },
            'input': {
              'command': codexString(item['command'], max: 65536, empty: true),
            },
            'output': item['aggregatedOutput'] is String
                ? codexString(
                    item['aggregatedOutput'],
                    max: 1024 * 1024,
                    empty: true,
                  )
                : '',
          }),
        ),
      );
    case 'fileChange':
      final changes = codexList(item['changes'], max: 1000);
      final text = changes
          .map((raw) {
            final change = codexObject(raw);
            final path = codexString(change['path'], max: 4096);
            final diff = change['diff'] is String
                ? codexString(change['diff'], max: 1024 * 1024, empty: true)
                : '';
            return '$path\n$diff';
          })
          .join('\n');
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          callID: id,
          type: 'tool',
          toolName: 'patch',
          toolState: ToolState.fromJson({
            'status': switch (item['status']) {
              'inProgress' => 'running',
              'completed' => 'completed',
              _ => 'error',
            },
            'input': <String, dynamic>{},
            'output': text,
          }),
        ),
      );
    default:
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          type: 'text',
          text:
              'Codex recorded an item that this mobile client cannot display yet.',
        ),
      );
  }
  return MessageWithParts(
    info: MessageInfo(
      id: id,
      sessionID: threadID,
      role: type == 'userMessage' ? 'user' : 'assistant',
      providerID: type == 'userMessage' ? null : 'codex',
      time: MsgTime(created: created, completed: completed),
      finish: switch (turnStatus) {
        'completed' => 'stop',
        'interrupted' => 'cancelled',
        'failed' => 'error',
        _ => null,
      },
      errorText: turnStatus == 'failed' && type != 'userMessage'
          ? 'The Codex turn failed. Review the server before retrying.'
          : null,
    ),
    parts: parts,
  );
}

List<MessageWithParts> codexMessages(Map<String, dynamic> thread) {
  final threadID = codexString(thread['id'], max: 256);
  final result = <MessageWithParts>[];
  final seen = <String>{};
  for (final rawTurn in codexList(thread['turns'])) {
    final turn = codexObject(rawTurn);
    final status = codexString(turn['status'], max: 32);
    if (!{
      'inProgress',
      'completed',
      'interrupted',
      'failed',
    }.contains(status)) {
      throw CodexFailure(CodexFailureKind.invalidResponse);
    }
    for (final rawItem in codexList(turn['items'])) {
      final message = codexItemMessage(
        threadID,
        codexObject(rawItem),
        created: codexMillis(turn['startedAt']),
        completed: codexMillis(turn['completedAt']),
        turnStatus: status,
      );
      if (!seen.add(message.info.id)) {
        throw CodexFailure(CodexFailureKind.invalidResponse);
      }
      result.add(message);
    }
  }
  return result;
}

Map<String, dynamic> codexMessageJson(MessageInfo info) => {
  'id': info.id,
  'sessionID': info.sessionID,
  'role': info.role,
  'providerID': info.providerID,
  'time': {'created': info.time?.created, 'completed': info.time?.completed},
  if (info.finish != null) 'finish': info.finish,
  if (info.errorText != null) 'error': {'message': info.errorText},
};

Map<String, dynamic> codexPartJson(Part part) => {
  'id': part.id,
  'messageID': part.messageID,
  'type': part.type,
  'text': part.text,
  if (part.callID != null) 'callID': part.callID,
  if (part.toolName != null) 'tool': part.toolName,
  if (part.type == 'tool')
    'state': {
      'status': part.toolState.status,
      'input': part.toolState.input,
      'output': part.toolState.output,
    },
};
