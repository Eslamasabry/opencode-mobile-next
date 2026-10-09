/// Pure mappers from Paseo daemon payloads to the app's chat models.
///
/// A Paseo *agent* is one provider conversation and maps to a [Session]. Its
/// *timeline* maps to messages: one message per user or assistant
/// `messageId`, per reasoning run, and per tool `callId`, so that a streamed
/// item and the same item read back from history carry the same id.
library;

import 'dart:convert';

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
    // The agent's model and mode are its own state (the daemon applies a
    // change to the running agent): the conversation shows and keeps them.
    selection: SessionSelection(
      variant: agent['thinkingOptionId'] is String
          ? agent['thinkingOptionId'] as String
          : '',
      model: model is String && model.isNotEmpty
          ? ModelRef(providerID: provider, modelID: model)
          : null,
      agent: agent['currentModeId'] is String
          ? agent['currentModeId'] as String
          : null,
    ),
  );
}

Map<String, dynamic> paseoSessionJson(Session session) {
  final selection = session.selection;
  final model = selection?.model;
  return {
    'id': session.id,
    'title': session.title,
    'directory': session.directory,
    if (session.parentID != null) 'parentID': session.parentID,
    'time': {
      'created': session.time?.created,
      'updated': session.time?.updated,
    },
    // The agent's model and mode travel with every update: without them a
    // conversation's chip falls back to "Loading" after each turn.
    if (selection != null) ...{
      'serverSelection': true,
      if (selection.modelKnown)
        'model': model == null
            ? null
            : {
                'providerID': model.providerID,
                'id': model.modelID,
                if (selection.variant.isNotEmpty) 'variant': selection.variant,
              },
      if (selection.agentKnown) 'agent': selection.agent,
    },
    if (session.cost != null) 'cost': session.cost,
  };
}

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
      'search' => switch (detail['toolName']) {
        'glob' => 'glob',
        'web_search' => 'websearch',
        _ => 'grep',
      },
      'fetch' => 'webfetch',
      // Claude Code's sub-agent (its Agent tool, Task before): the app's
      // sub-agent card, which says what the sub-agent was asked to do.
      'sub_agent' => 'task',
      'plan' => 'plan',
      'worktree_setup' => 'worktree_setup',
      'plain_text' when name.toLowerCase() == 'skill' => 'skill',
      _ when const {'agent', 'task'}.contains(name.toLowerCase()) => 'task',
      _ => name.isEmpty ? 'tool' : name.toLowerCase(),
    };

String paseoToolStatus(Object? status) => switch (status) {
  'completed' => 'completed',
  'failed' || 'error' || 'canceled' || 'cancelled' => 'error',
  _ => 'running',
};

/// Paseo's detail fields under the names the tool card already reads for the
/// same tool in OpenCode (`pattern`, `subagent_type`, `name`), so one card
/// serves both. A step type this app has no words for keeps its plain fields.
Object _toolInput(Map<String, dynamic> detail) {
  Map<String, dynamic> pick(Map<String, String> names) => {
    for (final entry in names.entries)
      if (detail[entry.key] != null &&
          detail[entry.key] is! Map &&
          detail[entry.key] is! List)
        entry.value: detail[entry.key],
  };
  switch (detail['type']) {
    case 'shell':
      return pick({'command': 'command'});
    case 'read':
      return pick({
        'filePath': 'filePath',
        'offset': 'offset',
        'limit': 'limit',
      });
    case 'edit':
      return pick({
        'filePath': 'filePath',
        'oldString': 'oldString',
        'newString': 'newString',
      });
    case 'write':
      return pick({'filePath': 'filePath', 'content': 'content'});
    case 'search':
      return pick({
        'query': detail['toolName'] == 'web_search' ? 'query' : 'pattern',
      });
    case 'fetch':
      return pick({'url': 'url', 'prompt': 'prompt'});
    case 'sub_agent':
      return pick({
        'subAgentType': 'subagent_type',
        'description': 'description',
      });
    case 'plain_text':
      return pick({'label': 'name'});
    case 'plan' || 'worktree_setup':
      return const <String, dynamic>{};
    case 'unknown':
      final raw = detail['input'];
      if (raw is Map<String, dynamic>) return raw;
      return raw is String ? raw : const <String, dynamic>{};
  }
  return {
    for (final entry in detail.entries)
      if (entry.key != 'type' &&
          entry.key != 'output' &&
          entry.value is! Map &&
          entry.value is! List)
        entry.key: entry.value,
  };
}

/// What the card reads beside the input and output: exit code, counts,
/// the diff, a sub-agent's actions, a worktree setup's steps.
Map<String, dynamic>? _toolMetadata(Map<String, dynamic> detail) {
  final meta = <String, dynamic>{};
  num? number(String key) => detail[key] is num ? detail[key] as num : null;
  switch (detail['type']) {
    case 'shell':
      meta['exit'] = number('exitCode');
    case 'edit':
      meta['diff'] = detail['unifiedDiff'];
    case 'search':
      final web = detail['webResults'];
      meta['matches'] = number('numMatches');
      meta['count'] = number('numFiles');
      meta['truncated'] = detail['truncated'];
      if (web is List) meta['numResults'] = web.length;
    case 'fetch':
      meta['httpCode'] = number('code');
      meta['httpText'] = detail['codeText'];
    case 'sub_agent':
      final actions = detail['actions'];
      if (actions is List) {
        meta['actions'] = [
          for (final action in actions.whereType<Map>().take(50))
            {
              'tool': paseoText(action['toolName'], max: 128),
              'summary': paseoText(action['summary'], max: 300),
            },
        ];
      }
    case 'worktree_setup':
      final commands = detail['commands'];
      meta['worktreePath'] = detail['worktreePath'];
      meta['branchName'] = detail['branchName'];
      meta['truncated'] = detail['truncated'];
      if (commands is List) {
        meta['commands'] = [
          for (final command in commands.whereType<Map>().take(30))
            {
              'command': paseoText(command['command'], max: 2000),
              'log': paseoText(command['log']),
              'status': command['status'],
              'exitCode': command['exitCode'],
            },
        ];
      }
  }
  meta.removeWhere((_, value) => value == null);
  return meta.isEmpty ? null : meta;
}

/// What a tool answered, as text or as its structured value (a map or list
/// the tool card can read, such as a connector search result).
///
/// Paseo puts the answer in a different field per detail type: `output` for
/// shell, `{output: <text or parsed JSON>}` for every tool it has no parser
/// for (MCP tools, ToolSearch), `content`/`filePaths` for grep and glob,
/// `webResults` for web search, `result` for fetch, `text` for skills and
/// plans, `log` for sub-agents and worktree setups.
Object _toolOutput(Map<String, dynamic> item, Map<String, dynamic> detail) {
  // A failed step says Failed once, in the card's own words; the provider's
  // error text is raw and is not the copy.
  final error = item['error'];
  if (error is String && error.isNotEmpty) return '';
  if (error is Map && error['message'] is String) return '';
  final output = detail['output'];
  if (output is String) return paseoText(output);
  if (output is Map) {
    if (output['text'] is String) return paseoText(output['text']);
    final inner = output['output'];
    if (inner is String) return paseoText(inner);
    if (inner is Map || inner is List) return _structured(inner);
    if (output.isNotEmpty) return _structured(output);
  }
  if (output is List) return _structured(output);
  final content = detail['content'];
  final type = detail['type'];
  if (type == 'read' && content is String) return paseoText(content);
  if (type == 'search') {
    if (content is String && content.isNotEmpty) return paseoText(content);
    final paths = detail['filePaths'];
    if (paths is List && paths.isNotEmpty) {
      return paseoText(paths.whereType<String>().join('\n'));
    }
    return _webResultsText(detail);
  }
  if (type == 'fetch' && detail['result'] is String) {
    return paseoText(detail['result']);
  }
  if ((type == 'plain_text' || type == 'plan') && detail['text'] is String) {
    return paseoText(detail['text']);
  }
  if ((type == 'sub_agent' || type == 'worktree_setup') &&
      detail['log'] is String) {
    return paseoText(detail['log']);
  }
  return '';
}

/// A web search's results as Markdown: each title with its address under it,
/// then the search's own notes.
String _webResultsText(Map<String, dynamic> detail) {
  final web = detail['webResults'];
  final notes = detail['annotations'];
  final blocks = [
    if (web is List)
      for (final result in web.whereType<Map>().take(50))
        [
          if (result['title'] is String) '**${result['title']}**',
          if (result['url'] is String) result['url'],
        ].join('  \n'),
    if (notes is List) ...notes.whereType<String>(),
  ].where((block) => block.isNotEmpty);
  return paseoText(blocks.join('\n\n'));
}

/// A structured answer kept as a value when it fits, otherwise its text cut
/// to the usual bound.
Object _structured(Object value) {
  try {
    final text = jsonEncode(value);
    return text.length > _textMax ? paseoText(text) : value;
  } catch (_) {
    return '';
  }
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
            // A failed step reads its words from `error`.
            if (paseoToolStatus(item['status']) == 'error')
              'error': _toolOutput(item, detail),
            'metadata': _toolMetadata(detail),
          }),
        ),
      );
    case 'todo':
      // The agent's task list is the same Tasks step OpenCode's todo tool
      // draws: a count, and one row per task with its state.
      final todos = item['items'] is List ? item['items'] as List : const [];
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          callID: id,
          type: 'tool',
          toolName: 'todowrite',
          toolState: ToolState.fromJson({
            'status': 'completed',
            'input': {
              'todos': [
                for (final raw in todos.take(200))
                  if (raw is Map && paseoText(raw['text']).trim().isNotEmpty)
                    {
                      'content': paseoText(raw['text'], max: 2000),
                      'status': switch (raw['status']) {
                        'pending' ||
                        'in_progress' ||
                        'completed' => raw['status'],
                        _ => raw['completed'] == true ? 'completed' : 'pending',
                      },
                    },
              ],
            },
            'output': '',
          }),
        ),
      );
    case 'compaction':
      // The same notice OpenCode 2's compaction draws: a line in the
      // transcript, not an assistant reply. What started it and how long the
      // conversation was travel with the part; the notice words them.
      final done = item['status'] == 'completed';
      final pre = item['preTokens'];
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
            toolState: ToolState(
              status: 'completed',
              metadata: {
                if (item['trigger'] == 'auto' || item['trigger'] == 'manual')
                  'trigger': item['trigger'],
                if (done && pre is num && pre > 0) 'preTokens': pre.round(),
              },
            ),
          ),
        ],
      );
    case 'notification':
      // A line from the agent or its host: a note, a warning or a problem.
      final level = switch (item['level']) {
        'warning' => 'warning',
        'error' => 'error',
        _ => 'info',
      };
      final message = paseoText(item['message'], max: 4000);
      if (message.trim().isEmpty) return null;
      return MessageWithParts(
        info: MessageInfo(
          id: id,
          sessionID: agentID,
          role: 'user',
          time: MsgTime(created: created, completed: completed ?? created),
        ),
        parts: [
          Part(
            id: '$id:0',
            messageID: id,
            type: 'v2:notice',
            toolName: 'agent-$level',
            text: message,
          ),
        ],
      );
    case 'error':
      // The agent's own words go to the error row's details; the row says
      // what happened in the app's words.
      return MessageWithParts(
        info: MessageInfo(
          id: id,
          sessionID: agentID,
          role: 'assistant',
          providerID: provider,
          time: MsgTime(created: created, completed: completed ?? created),
          errorText: paseoText(item['message'], max: 4000).trim().isEmpty
              ? 'The agent could not finish this reply.'
              : paseoText(item['message'], max: 4000),
        ),
        parts: const [],
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
  if (part.type == 'v2:compaction' && part.toolState.metadata != null)
    'state': {'status': 'completed', 'metadata': part.toolState.metadata},
  if (part.type == 'tool')
    'state': {
      'status': part.toolState.status,
      'input': part.toolState.input,
      'output': part.toolState.output,
      if (part.toolState.metadata != null) 'metadata': part.toolState.metadata,
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
  // What "Always allow" would cover; defaults to the patterns.
  List<String>? covers;
  final metadata = <String, dynamic>{};
  // Labelled rows for what the agent asks, for the card to draw as facts.
  final facts = <Map<String, String>>[];
  String text(String key, {int max = 4096}) => paseoText(detail[key], max: max);
  switch (detail['type']) {
    case 'write' || 'edit':
      permission = 'edit';
      final path = text('filePath');
      if (path.isNotEmpty) {
        patterns = [path];
        metadata['filePath'] = path;
      }
      final unified = text('unifiedDiff', max: 1024 * 1024);
      metadata['diff'] = detail['type'] == 'write'
          ? _unifiedPatch(path, null, text('content', max: 1024 * 1024))
          : unified.isNotEmpty
          ? unified
          : _unifiedPatch(
              path,
              text('oldString', max: 512 * 1024),
              text('newString', max: 512 * 1024),
            );
    case 'shell':
      permission = 'bash';
      final command = text('command', max: 65536);
      patterns = [command];
      metadata['command'] = command;
      if (text('cwd').isNotEmpty) metadata['cwd'] = text('cwd');
    case 'read':
      permission = 'read';
      final path = text('filePath');
      if (path.isNotEmpty) {
        patterns = [path];
        metadata['filePath'] = path;
      }
      if (detail['offset'] is num) metadata['offset'] = detail['offset'];
      if (detail['limit'] is num) metadata['limit'] = detail['limit'];
    case 'fetch':
      permission = 'webfetch';
      final url = text('url');
      if (url.isNotEmpty) patterns = [url];
      metadata['url'] = url;
      if (text('prompt').isNotEmpty) metadata['prompt'] = text('prompt');
      covers = [permission];
    case 'search':
      permission = switch (detail['toolName']) {
        'web_search' => 'websearch',
        'glob' => 'glob',
        _ => 'grep',
      };
      final query = text('query');
      if (query.isNotEmpty) patterns = [query];
      metadata['query'] = query;
      facts.add({'key': 'query', 'value': query});
      covers = [permission];
    case 'sub_agent':
      permission = 'task';
      final description = text('description');
      if (description.isNotEmpty) patterns = [description];
      metadata['description'] = description;
      if (description.isNotEmpty) {
        facts.add({'key': 'task', 'value': description});
      }
      if (text('subAgentType').isNotEmpty) {
        metadata['subagent_type'] = text('subAgentType', max: 256);
      }
      covers = [permission];
    case 'plain_text' when name.toLowerCase() == 'skill':
      permission = 'skill';
      final label = text('label', max: 512);
      if (label.isNotEmpty) patterns = [label];
      metadata['label'] = label;
      if (label.isNotEmpty) facts.add({'key': 'skill', 'value': label});
      covers = [permission];
    default:
      permission = switch (request['kind']) {
        'plan' => 'plan',
        'question' => 'question',
        'mode' => 'mode',
        _ => name.isEmpty ? 'tool' : _toolId(name),
      };
      final shown = detail['type'] == 'unknown' && detail['input'] is Map
          ? Map<String, dynamic>.from(detail['input'] as Map)
          : input;
      for (final entry in shown.entries.take(32)) {
        if (entry.value is String ||
            entry.value is num ||
            entry.value is bool) {
          metadata[entry.key] = entry.value is String
              ? paseoText(entry.value, max: 65536)
              : entry.value;
        }
      }
      // What the agent asked to do, one plain line per value, so the
      // request can be read in full rather than as "all matching requests".
      patterns = [
        for (final entry in metadata.entries.take(6))
          '${_askLabel(entry.key)}: ${entry.value}',
      ];
      for (final entry in metadata.entries.take(6)) {
        facts.add({'key': entry.key, 'value': '${entry.value}'});
      }
      covers = [permission];
  }
  covers ??= List.of(patterns);
  // What the agent passed besides what its detail already says (the folder
  // a search runs in, the note on a command): one plain line each.
  if (detail['type'] != null && detail['type'] != 'unknown') {
    final said = {
      ...patterns,
      for (final value in metadata.values)
        if (value is String) value,
    };
    for (final entry in input.entries.take(16)) {
      final value = entry.value;
      if (patterns.length >= 6) break;
      if (value is! String ||
          value.trim().isEmpty ||
          value.length > 300 ||
          value.contains('\n') ||
          _inputShownElsewhere.contains(entry.key) ||
          said.contains(value)) {
        continue;
      }
      patterns.add('${_askLabel(entry.key)}: $value');
      facts.add({'key': entry.key, 'value': value});
    }
  }
  if (facts.isNotEmpty) metadata['facts'] = facts;
  final toolUse = request['metadata'] is Map
      ? (request['metadata'] as Map)['toolUseId']
      : null;
  final title = request['title'];
  final description = request['description'];
  final suggestions = request['suggestions'];
  final words = [
    if (title is String && title.trim().isNotEmpty) title.trim(),
    if (description is String &&
        description.trim().isNotEmpty &&
        description.trim() != (title is String ? title.trim() : null))
      description.trim(),
  ];
  final grantable = suggestions is List && suggestions.isNotEmpty;
  return PermissionRequest(
    id: id,
    sessionID: agentID,
    permission: permission,
    patterns: patterns,
    metadata: metadata,
    always: grantable ? (covers.isEmpty ? [permission] : covers) : const [],
    // Paseo keeps a standing rule only when the provider suggested one.
    canAlwaysAllow: grantable,
    message: words.isEmpty ? null : paseoText(words.join('\n'), max: 4096),
    tool: toolUse is String && toolUse.isNotEmpty && toolUse.length <= 256
        ? PermissionTool(messageID: toolUse, callID: toolUse)
        : null,
  );
}

/// A tool's name as the lowercase id the app words ("NotebookEdit" as
/// "notebook_edit"; a connector's "mcp__drive__search" is kept).
String _toolId(String name) {
  final lower = name.toLowerCase();
  // Names the app already words as one id keep it.
  if (const {
    'webfetch',
    'websearch',
    'todowrite',
    'todoread',
    'multiedit',
  }.contains(lower)) {
    return lower;
  }
  return name
      .replaceAllMapped(RegExp('([a-z0-9])([A-Z])'), (m) => '${m[1]}_${m[2]}')
      .toLowerCase();
}

/// Input values the card shows another way (the change as a diff, the plan).
const _inputShownElsewhere = {
  'old_string',
  'new_string',
  'content',
  'command',
  'new_source',
  'plan',
};

/// "maxResults" and "max_results" as "Max results".
String _askLabel(String key) {
  final spaced = key
      .replaceAllMapped(RegExp('([a-z0-9])([A-Z])'), (m) => '${m[1]} ${m[2]}')
      .replaceAll(RegExp('[_-]+'), ' ')
      .trim()
      .toLowerCase();
  return spaced.isEmpty
      ? key
      : '${spaced[0].toUpperCase()}${spaced.substring(1)}';
}

/// A unified patch for a change given as before and after text (a missing
/// [before] is a new file), so the change reads as the diff view draws any
/// other change.
String _unifiedPatch(String path, String? before, String after) {
  List<String> lines(String text) => text.isEmpty ? const [] : text.split('\n');
  final removed = before == null ? const <String>[] : lines(before);
  final added = lines(after);
  return [
    '--- ${before == null ? '/dev/null' : 'a/$path'}',
    '+++ b/$path',
    '@@ -${removed.isEmpty ? 0 : 1},${removed.length} '
        '+${added.isEmpty ? 0 : 1},${added.length} @@',
    for (final line in removed) '-$line',
    for (final line in added) '+$line',
  ].join('\n');
}
