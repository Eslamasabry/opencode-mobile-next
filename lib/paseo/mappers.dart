/// Pure mappers from Paseo daemon payloads to the app's chat models.
///
/// A Paseo *agent* is one provider conversation and maps to a [Session]. Its
/// *timeline* maps to messages: one message per user or assistant
/// `messageId`, per reasoning run, and per tool `callId`, so that a streamed
/// item and the same item read back from history carry the same id.
library;

import 'dart:convert';
import 'dart:isolate';

import '../domain/background_tasks.dart' show backgroundTaskMetadataKey;
import '../domain/models.dart';
import '../domain/server_gateway.dart' show ProductException;
import '../domain/tool_label.dart' show isBackgroundTaskNotice;
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
    // Paseo's Claude adapter forwards an image to Claude only when its type is
    // one of these four (providers/claude/agent.js, isImageMimeType) and drops
    // any other without a word. Claude also refuses bytes whose real format
    // differs from the declared type. So the type sent is the one the bytes
    // really are, and an unsupported picture is refused here, in words.
    final declared = match.group(1)!.toLowerCase();
    final type =
        _sniffImageType(data) ??
        (declared == 'image/jpg' ? 'image/jpeg' : declared);
    if (!_claudeImageTypes.contains(type)) {
      throw const ProductException(
        'That picture type can\'t be sent. Use a PNG, JPEG, GIF or WebP picture.',
      );
    }
    images.add({'data': data, 'mimeType': type});
  }
  return images;
}

const _claudeImageTypes = {
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/webp',
};

/// The picture type the first bytes of [base64Data] show, or null if unknown.
String? _sniffImageType(String base64Data) {
  final head = base64Data.length < 32
      ? base64Data
      : base64Data.substring(0, 32);
  final List<int> b;
  try {
    // 32 base64 characters are 24 bytes, enough for every signature below.
    b = base64.decode(head);
  } catch (_) {
    return null;
  }
  bool starts(List<int> sig, [int at = 0]) {
    if (b.length < at + sig.length) return false;
    for (var i = 0; i < sig.length; i++) {
      if (b[at + i] != sig[i]) return false;
    }
    return true;
  }

  if (starts(const [137, 80, 78, 71, 13, 10, 26, 10])) return 'image/png';
  if (starts(const [255, 216, 255])) return 'image/jpeg';
  if (starts('GIF8'.codeUnits)) return 'image/gif';
  if (starts('RIFF'.codeUnits) && starts('WEBP'.codeUnits, 8)) {
    return 'image/webp';
  }
  return null;
}

/// Reads the daemon's [reply] with [read], naming the reply in the failure's
/// reason when a part of it has a shape the app doesn't read, so Details say
/// which message it was (never its content).
T paseoReply<T>(String reply, T Function() read) {
  try {
    return read();
  } on PaseoFailure catch (error) {
    if (error.kind != PaseoFailureKind.invalidResponse ||
        error.reason != null) {
      rethrow;
    }
    throw PaseoFailure(PaseoFailureKind.invalidResponse, reason: reply);
  }
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

/// Tokens in the runtime's context window and its size, from an agent's
/// `lastUsage` (`contextWindowUsedTokens` over `contextWindowMaxTokens`,
/// Paseo protocol 0.11.2). Null when either number is missing or unusable,
/// which is also what a runtime that reports none looks like.
SessionContextUse? paseoContextUse(Object? usage) {
  if (usage is! Map) return null;
  final used = usage['contextWindowUsedTokens'];
  final max = usage['contextWindowMaxTokens'];
  if (used is! num || max is! num) return null;
  if (!used.isFinite || !max.isFinite || used <= 0 || max <= 0) return null;
  return SessionContextUse(tokens: used.round(), limit: max.round());
}

/// Paseo names a new agent after its first prompt, cut hard at 60 characters
/// (create-agent-title.js) with no sign that words were left out, so a long
/// first message reads as a title that stops mid-word. A title that long
/// which does not end like a sentence is such a cut: it gets its ellipsis.
String paseoClippedTitle(String title) {
  final text = title.trim();
  if (text.length < 58 || text.length > 60) return title;
  if (RegExp(r'''[.!?…)"'`]$''').hasMatch(text)) return title;
  return '$text…';
}

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
        ? paseoClippedTitle(paseoText(title, max: 4096))
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
    contextUse: paseoContextUse(usage),
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

/// How long a running status read from an agent *record* is believed.
///
/// The daemon answers for agents it has not loaded (every one, after its
/// helper restarts) from their saved record, whose status is the one saved
/// last (`lastStatus`). An agent whose helper was stopped mid-turn (Android
/// stopping it with the app, a phone restart) keeps `running` there for good:
/// nothing runs it, and nothing rewrites the record until it is opened. Only
/// a loaded agent's snapshot carries `activeTurn` (its turn, or null), so a
/// running status without it is believed for this long after the record's
/// last change, then reads idle. The same bound as a sent prompt's
/// "turn about to start" grace: a lost turn end cannot hold Working forever.
const paseoRecordBusyFreshFor = Duration(minutes: 3);

/// `busy` while the agent is starting or running a turn, otherwise `idle`.
///
/// Busy needs live evidence: a loaded agent (its snapshot names
/// `activeTurn`, a turn or null), or a record that changed within
/// [paseoRecordBusyFreshFor] of [now]. See [paseoRecordBusyUntil].
String paseoSessionStatus(Map<String, dynamic> agent, {DateTime? now}) {
  if (!_paseoBusyWord(agent['status'])) return 'idle';
  // A loaded agent "initializing" with no turn is the daemon loading it
  // (the person opened its chat), not a run: no Working, and no Done when
  // the load ends (jitter J10, 2026-10-28).
  if (agent['status'] == 'initializing' &&
      agent.containsKey('activeTurn') &&
      agent['activeTurn'] == null) {
    return 'idle';
  }
  final until = paseoRecordBusyUntil(agent);
  if (until == null) return 'busy';
  return (now ?? DateTime.now()).isBefore(until) ? 'busy' : 'idle';
}

/// When a running status from [agent]'s saved record stops counting; null
/// when the status is not running or the snapshot is a loaded agent's (it is
/// live and needs no bound). A record without a readable `updatedAt` has
/// already expired.
DateTime? paseoRecordBusyUntil(Map<String, dynamic> agent) {
  if (!_paseoBusyWord(agent['status']) || agent.containsKey('activeTurn')) {
    return null;
  }
  final updated = paseoMillis(agent['updatedAt']);
  if (updated == null) return DateTime.fromMillisecondsSinceEpoch(0);
  return DateTime.fromMillisecondsSinceEpoch(
    updated,
  ).add(paseoRecordBusyFreshFor);
}

bool _paseoBusyWord(Object? status) =>
    status == 'initializing' || status == 'running';

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

/// A stopped step reads Stopped, never Failed: the tool card draws
/// `cancelled` as stopped and keeps the step's own body (its command, its
/// file) in view.
String paseoToolStatus(Object? status) => switch (status) {
  'completed' => 'completed',
  'canceled' || 'cancelled' => 'cancelled',
  'failed' || 'error' => 'error',
  _ => 'running',
};

/// Claude's words for a tool call it was told to stop ("[Request interrupted
/// by user for tool use]"), as Paseo forwards them in a failed call's error.
final _interruptReport = RegExp(r'^\[Request interrupted by user[^\]]*\]$');

/// A failed tool call whose only error is Claude saying it was stopped. When
/// a message sent mid-turn interrupts the turn, Paseo 0.9.1 reports the
/// stopped step as `canceled`, then once more, inside the next turn, as a
/// failed call named "tool" with no input (claude/agent.js handleToolResult
/// after flushPendingToolCalls dropped the call). It is a stop, not a failure.
bool paseoInterruptedToolCall(Map<String, dynamic> item) {
  if (item['status'] != 'failed') return false;
  return _interruptReport.hasMatch(_toolErrorText(item).trim());
}

/// The words of a failed call's error, whichever shape carries them.
String _toolErrorText(Map<String, dynamic> item) {
  final error = item['error'];
  final content = error is Map ? (error['content'] ?? error['message']) : error;
  return switch (content) {
    String text => text,
    List blocks =>
      blocks
          .whereType<Map>()
          .map((block) => block['text'])
          .whereType<String>()
          .join(),
    _ => '',
  };
}

/// What Paseo and Claude Code say when the person answered a permission
/// card with Reject: Paseo records the call as failed with its own words
/// ("Permission denied", "Permission request denied", the steer message) and
/// Claude's SDK follows with "The user doesn't want to proceed with this
/// tool use". The person said no; nothing went wrong.
final _declinedReport = RegExp(
  r"^(permission (request )?(denied|canceled)|"
  r"the user doesn't want to proceed|"
  r"the user answered with a message instead of approving|"
  r"user (denied|rejected|declined)|"
  r"permission to use .* has been denied)",
  caseSensitive: false,
);

/// A failed tool call the person declined at its permission card.
bool paseoDeclinedToolCall(Map<String, dynamic> item) {
  if (item['status'] != 'failed') return false;
  // The app knows the person rejected this call's card: whatever reason they
  // typed comes back as the error text, and is not a failure.
  if (item['declinedByPerson'] == true) return true;
  return _declinedReport.hasMatch(_toolErrorText(item).trim());
}

/// [later], a tool call reported again under the call id of [earlier],
/// merged the way the daemon's own projection merges one
/// (timeline-projection.js mergeToolCallItems): a detail it could not parse
/// keeps the parsed one, and metadata accumulates. A report that names no
/// tool ("tool") keeps the step's name, and Claude's stop report reads as
/// the stop it is.
Map<String, dynamic> paseoMergeToolCall(
  Map<String, dynamic>? earlier,
  Map<String, dynamic> later,
) {
  if (earlier == null ||
      earlier['type'] != 'tool_call' ||
      later['type'] != 'tool_call') {
    return later;
  }
  String name(Map<String, dynamic> item) =>
      item['name'] is String ? (item['name'] as String).trim() : '';
  bool parsed(Object? detail) =>
      detail is Map && detail['type'] is String && detail['type'] != 'unknown';
  final laterName = name(later);
  final earlierName = name(earlier);
  final earlierMeta = earlier['metadata'];
  final laterMeta = later['metadata'];
  final interrupted = paseoInterruptedToolCall(later);
  return {
    ...earlier,
    ...later,
    if ((laterName.isEmpty || laterName.toLowerCase() == 'tool') &&
        earlierName.isNotEmpty)
      'name': earlier['name'],
    if (!parsed(later['detail']) && parsed(earlier['detail']))
      'detail': earlier['detail'],
    if (earlierMeta is Map || laterMeta is Map)
      'metadata': {
        if (earlierMeta is Map) ...earlierMeta,
        if (laterMeta is Map) ...laterMeta,
      },
    if (interrupted) ...{'status': 'canceled', 'error': null},
    // A step that has finished does not start again: a late report that
    // still says it runs (a replay after compaction, a reconnect) is the
    // older news, so the spinner it would bring back stays off.
    if (!interrupted &&
        paseoToolStatus(earlier['status']) != 'running' &&
        paseoToolStatus(later['status']) == 'running') ...{
      'status': earlier['status'],
      'error': earlier['error'],
    },
  };
}

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

/// A background task's notice (Paseo's synthetic `task_notification` call)
/// as the card reads it: Claude's sentence about the task, and how the task
/// ended. Paseo keeps that end in the call's metadata (`stopped`, `killed`);
/// the call itself reads completed for any end but failed or canceled, so
/// without it every stopped task said only "ended".
Map<String, dynamic> _taskNoticeInput(
  Map<String, dynamic> item,
  Map<String, dynamic> detail,
) {
  final meta = item['metadata'];
  final status = meta is Map ? meta['status'] : null;
  final label = paseoText(detail['label'], max: 2000).trim();
  String? id(String key) {
    final value = meta is Map ? meta[key] : null;
    return value is String && value.trim().isNotEmpty && value.length <= 256
        ? value.trim()
        : null;
  }

  return {
    // Paseo's own stand-in when Claude gave no summary is not a summary.
    if (label.isNotEmpty && !_paseoNoticeLabel.hasMatch(label))
      'summary': label,
    if (status is String && status.trim().isNotEmpty)
      'status': paseoText(status, max: 64),
    // Which work ended: the call that started it (Claude's tool use id, the
    // same as that step's call id) and Claude's own task id.
    'toolUseId': ?id('toolUseId'),
    'taskId': ?id('taskId'),
  };
}

/// Claude Code's answer to a command it ran with "run in the background"
/// (Paseo 0.9.2 and 0.11.2 pass it through as the shell step's output): the
/// command goes on after the step ends, and a `task_notification` says when
/// it stops.
final _backgroundCommandReply = RegExp(
  r'Command running in background with ID:\s*([A-Za-z0-9_-]+)',
);

/// The step's metadata with the background marker
/// ([backgroundTaskMetadataKey]) when [toolName]'s finished step started
/// work that goes on in the background.
Map<String, dynamic>? _withBackgroundTask(
  Map<String, dynamic>? meta, {
  required String toolName,
  required String status,
  required Map<String, dynamic> detail,
}) {
  if (toolName != 'bash' || status != 'completed') return meta;
  final output = detail['output'];
  if (output is! String) return meta;
  final match = _backgroundCommandReply.firstMatch(output);
  if (match == null) return meta;
  // Claude's Bash call carries a one-line `description` of the command
  // ("Wait for the build to finish"). Paseo 0.9.1's shell detail does not
  // pass it on (command, cwd, output, exitCode only); read it if one does.
  final description = detail['description'];
  return {
    ...?meta,
    backgroundTaskMetadataKey: {
      'id': match.group(1),
      if (description is String && description.trim().isNotEmpty)
        'label': description.trim(),
    },
  };
}

final _paseoNoticeLabel = RegExp(r'^Background task (notification|\w+)$');

/// What a notice says beyond its sentence: Paseo repeats the sentence as the
/// call's text, or hands over Claude's raw `<task-notification>` envelope,
/// whose only words for a person are a finished agent's `<result>`.
String _taskNoticeOutput(Map<String, dynamic> detail) {
  final text = paseoText(detail['text']).trim();
  if (text.isEmpty || text == paseoText(detail['label']).trim()) return '';
  if (!text.contains('<task-notification>')) return text;
  final result = RegExp(
    r'<result>\s*([\s\S]*?)\s*</result>',
  ).firstMatch(text)?.group(1);
  return result ?? '';
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

/// How Paseo's ACP bridge words an agent's RPC failure in the reply text
/// ("[System Error] Internal error: … code: -32603 …"): an error, never prose.
bool _paseoSystemError(String text) =>
    text.trimLeft().startsWith('[System Error]');

final _signInAsk = RegExp(r'\b(sign|log) ?in\b', caseSensitive: false);

/// Claude Code's own line when a call to Anthropic failed and the turn
/// stopped, which Paseo forwards as the reply's text: "API Error: Can't reach
/// the API server — check your internet or DNS (EAI_AGAIN)", "API Error: 529
/// {…overloaded_error…}", "API Error: Request timed out.", "Claude AI usage
/// limit reached|1761836400", "Credit balance is too low". A failed turn,
/// never prose; the line goes to the error row's Details.
final _claudeTurnFailure = RegExp(
  r'^\s*(?:API Error\b|Claude AI usage limit reached\b'
  r'|Credit balance is too low\b)',
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
  // Other failures the bridge or Claude Code write as reply text are error
  // rows too.
  if (type == 'assistant_message') {
    final text = paseoText(item['text']);
    final system = _paseoSystemError(text);
    // Inside the bridge's error report, any ask to sign in is one ("You need
    // to sign in to use this model."); in a reply it could be advice.
    final signIn =
        paseoSignInFailure.hasMatch(text) ||
        (system && _signInAsk.hasMatch(text));
    if (signIn || system || _claudeTurnFailure.hasMatch(text)) {
      return MessageWithParts(
        info: MessageInfo(
          id: id,
          sessionID: agentID,
          role: 'assistant',
          providerID: provider,
          time: MsgTime(created: created, completed: completed ?? created),
          errorText: paseoText(item['text'], max: 2000),
          errorKind: signIn ? MessageErrorKind.providerAuth : null,
        ),
        parts: const [],
      );
    }
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
      final status = paseoInterruptedToolCall(item)
          ? 'cancelled'
          : paseoToolStatus(item['status']);
      final detail = item['detail'] is Map<String, dynamic>
          ? item['detail'] as Map<String, dynamic>
          : const <String, dynamic>{};
      final toolName = paseoToolName(paseoText(item['name'], max: 256), detail);
      final notice = isBackgroundTaskNotice(toolName);
      parts.add(
        Part(
          id: '$id:0',
          messageID: id,
          callID: id,
          type: 'tool',
          toolName: toolName,
          toolState: ToolState.fromJson({
            'status': status,
            'input': notice
                ? _taskNoticeInput(item, detail)
                : _toolInput(detail),
            'output': status == 'cancelled'
                ? ''
                : notice
                ? _taskNoticeOutput(detail)
                : _toolOutput(item, detail),
            // A failed step reads its words from `error`.
            if (status == 'error')
              'error': paseoDeclinedToolCall(item)
                  // The card reads this as the person's choice, not a failure.
                  ? 'The user declined this tool call'
                  : _toolOutput(item, detail),
            'metadata': _withBackgroundTask(
              _toolMetadata(detail),
              toolName: toolName,
              status: status,
              detail: detail,
            ),
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
/// assistant deltas arrive as one item. Entries that share an id collapse to
/// the latest. A tool call the projection kept twice (reported again in a
/// later turn, as Claude does for a step a mid-turn message stopped) is
/// merged into its first report and stays where the step ran.
/// [toolCalls], when given, receives each tool call's merged item by id.
List<MessageWithParts> paseoTimelineMessages(
  String agentID,
  Map<String, dynamic> payload, {
  required bool busy,
  Map<String, Map<String, dynamic>>? toolCalls,
}) {
  final result = <String, MessageWithParts>{};
  final tools = toolCalls ?? <String, Map<String, dynamic>>{};
  final toolCreated = <String, int?>{};
  final entries = paseoList(payload['entries'], max: 20000);
  for (var i = 0; i < entries.length; i++) {
    final entry = entries[i];
    if (entry is! Map<String, dynamic>) continue;
    var item = entry['item'];
    if (item is! Map<String, dynamic>) continue;
    final seq = entry['seqStart'];
    final id = paseoItemID(item, fallbackSeq: seq is int ? seq : i);
    final at = paseoMillis(entry['timestamp']);
    final last = i == entries.length - 1;
    final earlier = item['type'] == 'tool_call' ? tools[id] : null;
    if (item['type'] == 'tool_call') {
      item = tools[id] = paseoMergeToolCall(earlier, item);
      toolCreated.putIfAbsent(id, () => at);
    }
    final message = paseoItemMessage(
      agentID,
      item,
      id: id,
      provider: paseoText(entry['provider'], max: 128),
      created: earlier != null ? toolCreated[id] : at,
      // Only the newest item of a running agent can still be streaming.
      completed: busy && last ? null : at,
    );
    if (message == null) continue;
    if (earlier == null || !result.containsKey(id)) result.remove(id);
    result[id] = message;
  }
  return result.values.toList();
}

/// Timelines with this many entries or more are mapped on a background
/// isolate: a whole history has thousands, which takes a phone hundreds of
/// milliseconds; a short one maps faster than the isolate hop
/// (docs/qa/json-decode-2026-10-28).
const paseoBackgroundTimelineEntries = 100;

/// [paseoTimelineMessages] for a `fetch_agent_timeline_response`, with each
/// tool call's merged item by id. A long timeline maps off the UI isolate;
/// a payload that does not map is read again here, so it fails exactly as
/// [paseoReply] reports it.
Future<(List<MessageWithParts>, Map<String, Map<String, dynamic>>)>
paseoTimelineReply(
  String agentID,
  Map<String, dynamic> payload, {
  required bool busy,
}) async {
  final entries = payload['entries'];
  if (entries is List && entries.length >= paseoBackgroundTimelineEntries) {
    try {
      final mapped = await _timelineInBackground(agentID, payload, busy);
      if (mapped != null) return mapped;
    } catch (_) {
      // No isolate could run: map here.
    }
  }
  final tools = <String, Map<String, dynamic>>{};
  final messages = paseoReply(
    'fetch_agent_timeline_response',
    () => paseoTimelineMessages(agentID, payload, busy: busy, toolCalls: tools),
  );
  return (messages, tools);
}

Future<(List<MessageWithParts>, Map<String, Map<String, dynamic>>)?>
_timelineInBackground(
  String agentID,
  Map<String, dynamic> payload,
  bool busy,
) => Isolate.run(() {
  try {
    final tools = <String, Map<String, dynamic>>{};
    final messages = paseoTimelineMessages(
      agentID,
      payload,
      busy: busy,
      toolCalls: tools,
    );
    return (messages, tools);
  } catch (_) {
    return null;
  }
});

Map<String, dynamic> paseoMessageJson(MessageInfo info) => {
  'id': info.id,
  'sessionID': info.sessionID,
  'role': info.role,
  'providerID': info.providerID,
  'time': {'created': info.time?.created, 'completed': info.time?.completed},
  if (info.finish != null) 'finish': info.finish,
  if (info.errorText != null)
    'error': {
      // The kind, in a name MessageErrorKind.fromName reads back: without it
      // a live sign-in failure arrived as an unknown error.
      'name': ?_paseoErrorName(info.errorKind),
      'message': info.errorText,
    },
};

String? _paseoErrorName(MessageErrorKind? kind) => switch (kind) {
  MessageErrorKind.providerAuth => 'ProviderAuthError',
  MessageErrorKind.contextOverflow => 'ContextOverflowError',
  MessageErrorKind.outputLength => 'OutputLengthError',
  MessageErrorKind.contentFilter => 'ContentFilterError',
  MessageErrorKind.aborted => 'AbortedError',
  MessageErrorKind.modelNotFound => 'ModelNotFoundError',
  MessageErrorKind.unknown || null => null,
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
