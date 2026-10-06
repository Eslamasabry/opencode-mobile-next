/// Pure mappers from Paseo daemon payloads to the app's chat models.
///
/// A Paseo *agent* is one provider conversation and maps to a [Session]. Its
/// *timeline* maps to messages: one message per user or assistant
/// `messageId`, per reasoning run, and per tool `callId`, so that a streamed
/// item and the same item read back from history carry the same id.
library;

import '../api/models.dart';
import '../domain/server_gateway.dart' show ProductException;
import 'transport.dart';

/// The prompt's pictures as Paseo's `images` (base64 data and its type).
/// Other files can't go to an agent through its helper: refused in words.
List<Map<String, String>> paseoImages(List<PromptAttachment> attachments) {
  if (attachments.length > 8) {
    throw const ProductException('Attach up to 8 pictures at a time.');
  }
  final images = <Map<String, String>>[];
  for (final attachment in attachments) {
    final match = RegExp(
      r'^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$',
      dotAll: true,
    ).firstMatch(attachment.url);
    if (!attachment.mime.startsWith('image/') || match == null) {
      throw const ProductException(
        'This agent takes pictures only. Remove the other files and send again.',
      );
    }
    final data = match.group(2)!;
    if (data.length > 14 * 1024 * 1024) {
      throw const ProductException(
        'That picture is too large to send. Choose a smaller one.',
      );
    }
    images.add({'data': data, 'mimeType': match.group(1)!});
  }
  return images;
}

Map<String, dynamic> paseoObject(Object? value) {
  if (value is! Map<String, dynamic>) {
    throw PaseoFailure(PaseoFailureKind.invalidResponse);
  }
  return value;
}

String paseoString(Object? value, {int max = 4096, bool empty = false}) {
  if (value is! String || value.length > max || (!empty && value.isEmpty)) {
    throw PaseoFailure(PaseoFailureKind.invalidResponse);
  }
  return value;
}

List<dynamic> paseoList(Object? value, {int max = 10000}) {
  if (value is! List || value.length > max) {
    throw PaseoFailure(PaseoFailureKind.invalidResponse);
  }
  return value;
}

const _textMax = 4 * 1024 * 1024;

/// Lenient text: agent output is display-only, so an oversized or missing
/// value is clipped or blanked instead of failing the whole conversation.
String paseoText(Object? value, {int max = _textMax}) {
  if (value == null) return '';
  final text = value is String ? value : value.toString();
  return text.length > max ? text.substring(0, max) : text;
}

int? paseoMillis(Object? value) {
  if (value is! String || value.length > 64) return null;
  return DateTime.tryParse(value)?.millisecondsSinceEpoch;
}

/// Display names for the agent runtimes the daemon can drive.
String paseoProviderName(String provider) => switch (provider) {
  'claude' => 'Claude Code',
  'codex' => 'Codex',
  'opencode' => 'OpenCode',
  'copilot' => 'GitHub Copilot',
  'pi' => 'Pi',
  _ => provider,
};

Session paseoSession(Map<String, dynamic> agent) {
  final id = paseoString(agent['id'], max: 256);
  final cwd = paseoString(agent['cwd']);
  final provider = paseoString(agent['provider'], max: 128);
  final title = agent['title'];
  final model = agent['model'];
  final usage = agent['lastUsage'];
  final cost = usage is Map ? usage['totalCostUsd'] ?? usage['costUsd'] : null;
  return Session(
    id: id,
    title: title is String && title.trim().isNotEmpty
        ? paseoText(title, max: 4096)
        : '${paseoProviderName(provider)} conversation',
    directory: cwd,
    time: SessionTime(
      created: paseoMillis(agent['createdAt']),
      updated: paseoMillis(agent['updatedAt']),
    ),
    agent: agent['currentModeId'] is String
        ? agent['currentModeId'] as String
        : null,
    model: model is String && model.isNotEmpty ? '$provider/$model' : null,
    cost: cost is num ? cost.toDouble() : null,
  );
}

Map<String, dynamic> paseoSessionJson(Session session) => {
  'id': session.id,
  'title': session.title,
  'directory': session.directory,
  'time': {'created': session.time?.created, 'updated': session.time?.updated},
};

/// `busy` while the agent is starting or running a turn, otherwise `idle`.
String paseoSessionStatus(Map<String, dynamic> agent) =>
    switch (agent['status']) {
      'initializing' || 'running' => 'busy',
      _ => 'idle',
    };

/// The OpenCode tool vocabulary the chat UI already renders well.
String paseoToolName(String name, Map<String, dynamic> detail) =>
    switch (detail['type']) {
      'shell' => 'bash',
      'read' => 'read',
      'edit' => 'edit',
      'write' => 'write',
      'search' => 'grep',
      'fetch' => 'webfetch',
      _ => name.isEmpty ? 'tool' : name.toLowerCase(),
    };

String paseoToolStatus(Object? status) => switch (status) {
  'completed' => 'completed',
  'failed' || 'error' || 'canceled' || 'cancelled' => 'error',
  _ => 'running',
};

Map<String, dynamic> _toolInput(Map<String, dynamic> detail) {
  final raw = detail['input'];
  if (detail['type'] == 'unknown' && raw is Map<String, dynamic>) return raw;
  return {
    for (final entry in detail.entries)
      if (entry.key != 'type' &&
          entry.key != 'output' &&
          entry.value is! Map &&
          entry.value is! List)
        entry.key: entry.value,
  };
}

String _toolOutput(Map<String, dynamic> item, Map<String, dynamic> detail) {
  final error = item['error'];
  if (error is String && error.isNotEmpty) return 'The tool could not finish.';
  if (error is Map && error['message'] is String) {
    return 'The tool could not finish.';
  }
  final output = detail['output'];
  if (output is String) return paseoText(output);
  if (output is Map && output['text'] is String) {
    return paseoText(output['text']);
  }
  final content = detail['content'];
  if (detail['type'] == 'read' && content is String) return paseoText(content);
  return '';
}

/// An agent's own words for a sign-in it can no longer use. Not the
/// transient "another process is refreshing" (that one passes on retry).
final paseoSignInFailure = RegExp(
  r'failed to authenticate|oauth (session|token) (has )?expired|'
  r'could not be refreshed|please run /login|invalid api key|'
  r'authentication_error|not logged in',
  caseSensitive: false,
);

/// A stable message id for a timeline item, or null when it has none of its
/// own and the caller must derive one from its position ([fallbackSeq]).
String paseoItemID(Map<String, dynamic> item, {required int? fallbackSeq}) {
  final type = item['type'];
  final own = switch (type) {
    'user_message' => item['messageId'] ?? item['clientMessageId'],
    'assistant_message' => item['messageId'],
    'tool_call' => item['callId'],
    _ => null,
  };
  if (own is String && own.isNotEmpty && own.length <= 256) return own;
  final prefix = switch (type) {
    'user_message' => 'u',
    'assistant_message' => 'a',
    'reasoning' => 'r',
    'tool_call' => 't',
    _ => 'i',
  };
  return '$prefix:${fallbackSeq ?? 0}';
}

/// Maps one timeline item. Returns null for item kinds with nothing to show.
MessageWithParts? paseoItemMessage(
  String agentID,
  Map<String, dynamic> item, {
  required String id,
  required String provider,
  int? created,
  int? completed,
}) {
  final type = item['type'];
  if (type is! String) return null;
  final parts = <Part>[];
  // An agent that can't sign in answers with its own error text ("Failed to
  // authenticate: OAuth session expired…"): a sign-in failure, said in the
  // app's words with the way to sign in again; the text goes to Details.
  if (type == 'assistant_message' &&
      paseoSignInFailure.hasMatch(paseoText(item['text']))) {
    return MessageWithParts(
      info: MessageInfo(
        id: id,
        sessionID: agentID,
        role: 'assistant',
        providerID: provider,
        time: MsgTime(created: created, completed: completed ?? created),
        errorText: paseoText(item['text'], max: 2000),
        errorKind: MessageErrorKind.providerAuth,
      ),
      parts: const [],
    );
  }
  switch (type) {
    case 'user_message':
    case 'assistant_message':
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          type: 'text',
          text: paseoText(item['text']),
        ),
      );
    case 'reasoning':
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          type: 'reasoning',
          text: paseoText(item['text']),
        ),
      );
    case 'tool_call':
      final detail = item['detail'] is Map<String, dynamic>
          ? item['detail'] as Map<String, dynamic>
          : const <String, dynamic>{};
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          callID: id,
          type: 'tool',
          toolName: paseoToolName(paseoText(item['name'], max: 256), detail),
          toolState: ToolState.fromJson({
            'status': paseoToolStatus(item['status']),
            'input': _toolInput(detail),
            'output': _toolOutput(item, detail),
          }),
        ),
      );
    case 'todo':
      final todos = item['items'] is List ? item['items'] as List : const [];
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          type: 'text',
          text: [
            for (final raw in todos.take(200))
              if (raw is Map)
                '${raw['completed'] == true || raw['status'] == 'completed' ? '[x]' : '[ ]'} '
                    '${paseoText(raw['text'], max: 2000)}',
          ].join('\n'),
        ),
      );
    case 'compaction':
      // The same notice OpenCode 2's compaction draws: a line in the
      // transcript, not an assistant reply.
      final done = item['status'] == 'completed';
      return MessageWithParts(
        info: MessageInfo(
          id: id,
          sessionID: agentID,
          role: 'user',
          time: MsgTime(created: created, completed: completed),
        ),
        parts: [
          Part(
            id: '$id:0',
            messageID: id,
            type: 'v2:compaction',
            toolName: done ? 'completed' : 'running',
            text: done ? 'Conversation compacted.' : 'Compacting conversation…',
          ),
        ],
      );
    case 'error':
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          type: 'text',
          text:
              'The agent could not finish this reply. Check it on your computer.',
        ),
      );
    default:
      return null;
  }
  final user = type == 'user_message';
  return MessageWithParts(
    info: MessageInfo(
      id: id,
      sessionID: agentID,
      role: user ? 'user' : 'assistant',
      providerID: user ? null : provider,
      time: MsgTime(created: created, completed: completed),
    ),
    parts: parts,
  );
}

/// Maps a `fetch_agent_timeline_response` payload, oldest first.
///
/// Entries are already merged by the daemon's projection, so consecutive
/// assistant deltas arrive as one item. Entries that share an id (a tool call
/// reported while running and again when done) collapse to the latest.
List<MessageWithParts> paseoTimelineMessages(
  String agentID,
  Map<String, dynamic> payload, {
  required bool busy,
}) {
  final result = <String, MessageWithParts>{};
  final entries = paseoList(payload['entries'], max: 20000);
  for (var i = 0; i < entries.length; i++) {
    final entry = entries[i];
    if (entry is! Map<String, dynamic>) continue;
    final item = entry['item'];
    if (item is! Map<String, dynamic>) continue;
    final seq = entry['seqStart'];
    final id = paseoItemID(item, fallbackSeq: seq is int ? seq : i);
    final at = paseoMillis(entry['timestamp']);
    final last = i == entries.length - 1;
    final message = paseoItemMessage(
      agentID,
      item,
      id: id,
      provider: paseoText(entry['provider'], max: 128),
      created: at,
      // Only the newest item of a running agent can still be streaming.
      completed: busy && last ? null : at,
    );
    if (message == null) continue;
    result.remove(id);
    result[id] = message;
  }
  return result.values.toList();
}

Map<String, dynamic> paseoMessageJson(MessageInfo info) => {
  'id': info.id,
  'sessionID': info.sessionID,
  'role': info.role,
  'providerID': info.providerID,
  'time': {'created': info.time?.created, 'completed': info.time?.completed},
  if (info.finish != null) 'finish': info.finish,
  if (info.errorText != null) 'error': {'message': info.errorText},
};

Map<String, dynamic> paseoPartJson(Part part) => {
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

/// Maps a daemon permission request to the app's permission card.
///
/// The permission name follows the OpenCode vocabulary (`edit`, `bash`) so
/// the existing card copy and icons apply. `always` is offered only when the
/// provider suggested a standing rule the daemon can apply.
PermissionRequest paseoPermission(
  String agentID,
  Map<String, dynamic> request,
) {
  final id = paseoString(request['id'], max: 512);
  final name = paseoText(request['name'], max: 256);
  final detail = request['detail'] is Map<String, dynamic>
      ? request['detail'] as Map<String, dynamic>
      : const <String, dynamic>{};
  final input = request['input'] is Map<String, dynamic>
      ? request['input'] as Map<String, dynamic>
      : const <String, dynamic>{};
  String permission;
  var patterns = <String>[];
  final metadata = <String, dynamic>{};
  switch (detail['type']) {
    case 'write' || 'edit':
      permission = 'edit';
      final path = paseoText(detail['filePath'], max: 4096);
      if (path.isNotEmpty) {
        patterns = [path];
        metadata['filePath'] = path;
      }
      if (detail['type'] == 'write') {
        metadata['diff'] = paseoText(detail['content'], max: 1024 * 1024);
      } else {
        metadata['diff'] =
            '- ${paseoText(detail['oldString'], max: 512 * 1024)}\n'
            '+ ${paseoText(detail['newString'], max: 512 * 1024)}';
      }
    case 'shell':
      permission = 'bash';
      final command = paseoText(detail['command'], max: 65536);
      patterns = [command];
      metadata['command'] = command;
    default:
      permission = switch (request['kind']) {
        'plan' => 'plan',
        'question' => 'question',
        'mode' => 'mode',
        _ => name.isEmpty ? 'tool' : name.toLowerCase(),
      };
      for (final entry in input.entries.take(32)) {
        if (entry.value is String ||
            entry.value is num ||
            entry.value is bool) {
          metadata[entry.key] = entry.value is String
              ? paseoText(entry.value, max: 65536)
              : entry.value;
        }
      }
  }
  final toolUse = request['metadata'] is Map
      ? (request['metadata'] as Map)['toolUseId']
      : null;
  final title = request['title'];
  final description = request['description'];
  final suggestions = request['suggestions'];
  return PermissionRequest(
    id: id,
    sessionID: agentID,
    permission: permission,
    patterns: patterns,
    metadata: metadata,
    always: suggestions is List && suggestions.isNotEmpty
        ? (patterns.isEmpty ? [permission] : patterns)
        : const [],
    message: description is String && description.isNotEmpty
        ? paseoText(description, max: 4096)
        : title is String && title.isNotEmpty
        ? paseoText(title, max: 4096)
        : null,
    tool: toolUse is String && toolUse.isNotEmpty && toolUse.length <= 256
        ? PermissionTool(messageID: toolUse, callID: toolUse)
        : null,
  );
}
