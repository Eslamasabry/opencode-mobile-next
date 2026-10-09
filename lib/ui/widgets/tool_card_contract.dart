part of 'tool_card.dart';

enum _ToolKind {
  read,
  list,
  glob,
  grep,
  shell,
  edit,
  write,
  patch,
  webFetch,
  webSearch,
  task,
  todo,
  question,
  lsp,
  skill,
  plan,
  worktreeSetup,
  connectorSearch,
  generic,
}

String? _valueString(dynamic value) {
  if (value == null) return null;
  final text = value.toString().trim();
  return text.isEmpty ? null : text;
}

String? _rawString(dynamic value) => value is String ? value : null;

num? _valueNumber(dynamic value) =>
    value is num ? value : num.tryParse('$value');

String _fileName(String value) {
  final normalized = value.replaceAll('\\', '/');
  final parts = normalized.split('/').where((part) => part.isNotEmpty);
  return parts.isEmpty ? value : parts.last;
}

/// The child session the OpenCode `task` or native v2 `subagent` tool spawned, read
/// from the tool metadata. The server writes `sessionId` (alongside
/// `parentSessionId`, `model` and, for background jobs, `jobId`); the other
/// spellings cover native v2 (`sessionID`) and older builds.
String? taskChildSessionId(ToolState state) {
  final metadata = state.metadata;
  if (metadata == null) return null;
  for (final key in const ['sessionId', 'sessionID', 'session_id']) {
    if (_valueString(metadata[key]) case final id?) return id;
  }
  return null;
}

/// `state="…"` of the `<task …>` wrapper the server puts around a subagent
/// result (running / completed / error); null when the output lacks one.
String? _taskOutputState(String? output, {bool nativeSubagent = false}) {
  if (output == null) return null;
  final tag = nativeSubagent ? 'subagent' : 'task';
  final match = RegExp('<$tag\\b[^>]*\\bstate="([a-z_]+)"').firstMatch(output);
  return match?.group(1);
}

/// Text inside `<task_result>` / `<task_error>`, or the whole output when the
/// server did not wrap it.
String _taskResultText(String? output, {bool nativeSubagent = false}) {
  final raw = output ?? '';
  if (nativeSubagent) {
    final wrapper = RegExp(
      r'^\s*<subagent\b[^>]*>\n?(.*?)\n?</subagent>\s*$',
      dotAll: true,
    ).firstMatch(raw);
    return (wrapper?.group(1) ?? raw).trim();
  }
  final match = RegExp(
    r'<task_(?:result|error)>\n?(.*?)\n?</task_(?:result|error)>',
    dotAll: true,
  ).firstMatch(raw);
  return (match?.group(1) ?? raw).trim();
}

/// The invocation finished by detaching its child, not by completing the
/// child's work. The later synthetic completion is a separate server message.
bool _nativeSubagentLaunched(ToolState state) =>
    state.executed &&
    state.status == 'completed' &&
    state.metadata?['status'] == 'running';

String? _subagentState(ToolState state, {required bool nativeSubagent}) {
  if (!state.executed) return null;
  if (nativeSubagent && state.status == 'error') return 'error';
  if (nativeSubagent) {
    final status = state.metadata?['status'];
    if (status == 'running' || status == 'completed') return status as String;
  }
  return _taskOutputState(state.output, nativeSubagent: nativeSubagent);
}

/// The one name a person sees for a tool call: the step row's title
/// ("Read", "Show card", "Background task finished", a sub-agent's name, or
/// an unknown id as words). Every surface that names a tool (the timeline,
/// find, Copy transcript, permission titles, a team agent's last step) uses
/// this, never the raw id ([toolIdWords] has the rules). [state] adds what
/// the call says about itself (a card's title is not part of the name); an
/// id alone (a permission request) passes none.
String toolLabel(String rawName, {ToolState? state, AppLocalizations? l10n}) =>
    _ToolContract.from(
      rawName,
      state ?? ToolState(status: 'completed'),
      l10n: l10n,
    ).title;

/// Compact "Title · subtitle" line for the tool currently executing, used by
/// the chat tool-group header as a live ticker while a run is active.
String runningToolTicker(
  String rawName,
  ToolState state, {
  AppLocalizations? l10n,
}) {
  final contract = _ToolContract.from(rawName, state, l10n: l10n);
  final subtitle = contract.subtitle;
  return subtitle == null || subtitle.isEmpty
      ? contract.title
      : '${contract.title} · $subtitle';
}

/// What a tool call says about itself, in words, before any widget: its
/// kind, its title ("Read", the sub-agent's name), what it touched
/// ([subtitle]; a technical value when [technical]), the other facts
/// ([details]: "From line 5", "3 matches") and an edit's +/− counts.
class _ToolContract {
  final _ToolKind kind;
  final String title;
  final String? subtitle;

  /// [subtitle] is a value the app did not write (a path, a pattern, a
  /// command, a URL): the row draws it mono and left-to-right.
  final bool technical;
  final List<String> details;
  final int? added;
  final int? removed;

  /// A shell command's exit code, when the server reported one.
  final int? exitCode;
  final bool nativeSubagent;

  const _ToolContract({
    required this.kind,
    required this.title,
    this.subtitle,
    this.technical = false,
    this.details = const [],
    this.added,
    this.removed,
    this.exitCode,
    this.nativeSubagent = false,
  });

  factory _ToolContract.from(
    String rawName,
    ToolState state, {
    AppLocalizations? l10n,
  }) {
    final strings = l10n ?? lookupAppLocalizations(const Locale('en'));
    final name = rawName.trim().toLowerCase();
    final input = state.input;
    final metadata = state.metadata ?? const <String, dynamic>{};
    final details = <String>[];

    _ToolKind kind;
    String title;
    String? subtitle;
    var technical = false;
    int? added;
    int? removed;
    int? exitCode;
    switch (name) {
      case 'read':
        kind = _ToolKind.read;
        title = strings.chatUiRead;
        final path = _valueString(input['filePath']);
        subtitle = path == null ? null : _fileName(path);
        technical = true;
        if (_valueNumber(input['offset']) case final value?) {
          details.add(strings.chatUiFromLine(value));
        }
        if (_valueNumber(input['limit']) case final value?) {
          details.add(strings.chatUiLineCount(value));
        }
        final display = metadata['display'];
        if (display is Map) {
          final start = _valueNumber(display['lineStart']);
          final end = _valueNumber(display['lineEnd']);
          if (start != null && end != null) {
            details.add(strings.chatUiLineRange(start, end));
          }
          final count = display['entries'] is List
              ? (display['entries'] as List).length
              : null;
          if (count != null) details.add(strings.chatUiEntryCount(count));
        }
        break;
      case 'list':
        kind = _ToolKind.list;
        title = strings.chatUiList;
        subtitle = _valueString(input['path']) ?? state.title;
        technical = true;
        break;
      case 'glob':
        kind = _ToolKind.glob;
        title = strings.chatUiFindFiles;
        subtitle = _valueString(input['pattern']);
        technical = true;
        if (_valueNumber(metadata['count']) case final value?) {
          details.add(strings.chatUiFoundCount(value));
        }
        break;
      case 'grep':
        kind = _ToolKind.grep;
        title = strings.chatUiSearchText;
        subtitle = _valueString(input['pattern']);
        technical = true;
        if (_valueNumber(metadata['matches']) case final value?) {
          details.add(strings.chatUiMatchCount(value));
        }
        if (_valueNumber(metadata['count']) case final value? when value > 0) {
          details.add(strings.chatUiFileCount(value));
        }
        if (_valueString(input['include']) case final value?) {
          details.add(value);
        }
        break;
      case 'bash':
      case 'shell':
        kind = _ToolKind.shell;
        title = strings.activeContextShell;
        subtitle = _valueString(input['command']);
        technical = true;
        // OpenCode's bash tool says in words what the command is for.
        if (_valueString(input['description']) case final purpose?) {
          details.add(purpose);
        }
        exitCode = _valueNumber(metadata['exit'])?.toInt();
        break;
      case 'edit':
        kind = _ToolKind.edit;
        title = strings.chatUiEdit;
        final path = _valueString(input['filePath']);
        subtitle = path == null ? state.title : _fileName(path);
        technical = path != null;
        final filediff = metadata['filediff'];
        if (filediff is Map) {
          added = _positive(filediff['additions']);
          removed = _positive(filediff['deletions']);
        }
        break;
      case 'write':
        kind = _ToolKind.write;
        title = strings.chatUiWrite;
        final path = _valueString(input['filePath']);
        subtitle = path == null ? state.title : _fileName(path);
        technical = path != null;
        details.add(
          metadata['exists'] == false
              ? strings.chatUiNewFile
              : strings.chatUiUpdated,
        );
        break;
      case 'patch':
      case 'apply_patch':
        kind = _ToolKind.patch;
        title = strings.chatUiApplyPatch;
        final files = metadata['files'];
        if (files is List) {
          subtitle = strings.chatUiFileCount(files.length);
          var additions = 0;
          var deletions = 0;
          for (final file in files.whereType<Map>()) {
            additions += (_valueNumber(file['additions']) ?? 0).toInt();
            deletions += (_valueNumber(file['deletions']) ?? 0).toInt();
          }
          if (additions > 0) added = additions;
          if (deletions > 0) removed = deletions;
        }
        break;
      case 'webfetch':
        kind = _ToolKind.webFetch;
        title = strings.chatUiFetchPage;
        subtitle = _valueString(input['url']);
        technical = true;
        if (_valueString(input['format']) case final value?) details.add(value);
        if (_valueNumber(metadata['httpCode']) case final code?) {
          details.add(
            strings.toolCardPageStatus(
              [code.toInt(), ?_valueString(metadata['httpText'])].join(' '),
            ),
          );
        }
        break;
      case 'websearch':
        kind = _ToolKind.webSearch;
        title = _valueString(metadata['provider']) == null
            ? strings.chatUiWebSearch
            : strings.chatUiProviderSearch(metadata['provider'] ?? '');
        subtitle = _valueString(input['query']);
        if (_valueNumber(metadata['numResults']) case final value?) {
          details.add(strings.chatUiResultCount(value));
        }
        break;
      case 'task':
      case 'subagent':
        kind = _ToolKind.task;
        title =
            _valueString(
              input[name == 'subagent' ? 'agent' : 'subagent_type'],
            ) ??
            strings.chatUiAgent;
        subtitle = _valueString(input['description']);
        break;
      case 'todowrite':
      case 'todo':
        kind = _ToolKind.todo;
        title = strings.workTitle;
        final todos = metadata['todos'] ?? input['todos'];
        // One readout, the same as the opened list's: completed out of the
        // tasks still tracked (cancelled ones are not).
        final view = MobileTaskView.fromTodos(todos);
        if (view != null && view.trackedCount > 0) {
          subtitle = strings.mobileTasksProgress(
            view.completedCount,
            view.trackedCount,
          );
        } else if (todos is List) {
          final done = todos
              .where((item) => item is Map && item['status'] == 'completed')
              .length;
          subtitle = strings.chatUiCompletedCount(done, todos.length);
        }
        break;
      case 'question':
        kind = _ToolKind.question;
        title = strings.chatUiQuestions;
        final questions = input['questions'];
        final answers = metadata['answers'];
        if (questions is List) {
          subtitle = answers is List && answers.isNotEmpty
              ? strings.chatUiAnsweredCount(questions.length)
              : strings.chatUiAskedCount(questions.length);
        }
        break;
      case 'lsp':
        kind = _ToolKind.lsp;
        title =
            _valueString(input['operation']) ?? strings.chatUiLanguageServer;
        final path = _valueString(input['filePath']);
        subtitle = path == null ? null : _fileName(path);
        technical = true;
        break;
      case 'plan':
        // The agent's plan: the first line of it is the step's one line.
        kind = _ToolKind.plan;
        title = strings.toolCardPlan;
        subtitle = _firstWords(state.output);
      case 'worktree_setup':
        kind = _ToolKind.worktreeSetup;
        title = strings.toolCardWorktreeSetup;
        subtitle = _valueString(metadata['branchName']);
        technical = true;
      case 'skill':
        kind = _ToolKind.skill;
        title = strings.activeContextSkill;
        subtitle = _valueString(input['name']);
        break;
      case _ when isBackgroundTaskNotice(name):
        // Claude Code's notice that a background task it started ended.
        kind = _ToolKind.generic;
        title = switch (backgroundTaskEnd(input)) {
          BackgroundTaskEnd.failed => strings.chatUiToolBackgroundTaskFailed,
          BackgroundTaskEnd.stopped => strings.chatUiToolBackgroundTaskStopped,
          BackgroundTaskEnd.finished =>
            strings.chatUiToolBackgroundTaskFinished,
        };
        subtitle =
            _valueString(input['summary']) ??
            _valueString(input['description']);
      case 'toolsearch':
        // Claude Code looking up which of its tools to load.
        kind = _ToolKind.generic;
        title = strings.chatUiLoadTools;
        subtitle = _valueString(input['query']);
      default:
        kind = _ToolKind.generic;
        if (isConnectorSearchTool(rawName)) {
          // The agent searching the connector catalogue already on this
          // phone: read-only, in words, with what it found on the line.
          kind = _ToolKind.connectorSearch;
          title = strings.chatUiToolSearchedConnectors;
          subtitle = _valueString(input['query']);
          if (state.status == 'completed') {
            final result = parseConnectorSearchResult(
              value: state.outputValue,
              text: state.output,
            );
            if (result != null) {
              details.add(switch (result.status) {
                ConnectorSearchStatus.ok when result.matches.isEmpty =>
                  strings.chatUiConnectorSearchEmpty,
                ConnectorSearchStatus.ok => strings.chatUiFoundCount(
                  result.matches.length,
                ),
                ConnectorSearchStatus.catalogueNotLoaded =>
                  strings.chatUiConnectorSearchNotLoaded,
                ConnectorSearchStatus.catalogueOff =>
                  strings.chatUiConnectorSearchOff,
                ConnectorSearchStatus.unavailable =>
                  strings.chatUiConnectorSearchUnavailable,
              });
            }
          }
        } else if (isAgentCardTool(rawName)) {
          // The agent card tool, before its card is drawn (still running) or
          // where the connection draws none: in words, with the card's own
          // title. Its server sets the raw name as the call's title, so
          // that never wins here.
          title = strings.chatUiToolShowCard;
          subtitle = _valueString(input['title']);
        } else {
          // The server's own title for the call, unless it only repeats
          // the tool's id; an id is never shown as it is.
          final own = state.title?.trim();
          title = own != null && own.isNotEmpty && own != rawName.trim()
              ? own
              : _mcpTitle(rawName, strings) ?? _toolWords(rawName, strings);
          subtitle = null;
        }
    }
    if (metadata['truncated'] == true &&
        !details.contains(strings.chatUiTruncated)) {
      details.add(strings.chatUiTruncated);
    }
    return _ToolContract(
      kind: kind,
      title: title,
      subtitle: subtitle,
      technical: technical && subtitle != null,
      details: details,
      added: added,
      removed: removed,
      exitCode: exitCode,
      nativeSubagent: name == 'subagent',
    );
  }

  /// "mcp__github__create_issue" → "create issue · github": a tool an MCP
  /// server adds, in words; null for any other name.
  static String? _mcpTitle(String name, AppLocalizations strings) {
    final match = RegExp(r'^mcp__(.+?)__(.+)$').firstMatch(name);
    if (match == null) return null;
    String words(String raw) => raw.replaceAll(RegExp(r'[_]+'), ' ').trim();
    return strings.chatUiToolFrom(
      KitBidi.auto(words(match.group(2)!)),
      KitBidi.auto(words(match.group(1)!)),
    );
  }

  /// "render_mermaid_diagram" → "Render mermaid diagram", "ExitWorktree"
  /// → "Exit worktree": a tool id the app has no words for, as words
  /// ([toolIdWords]); "Tool" when the id has none.
  static String _toolWords(String name, AppLocalizations strings) {
    final words = toolIdWords(name);
    return words.isEmpty ? strings.chatUiTool : KitText.sentenceCase(words);
  }

  /// The first line of Markdown as plain words ("# Add login" → "Add
  /// login"); null when there is none.
  static String? _firstWords(String? markdown) {
    for (final line in (markdown ?? '').split('\n')) {
      final words = line.replaceFirst(RegExp(r'^[\s#>*\-]+'), '').trim();
      if (words.isNotEmpty) return words;
    }
    return null;
  }

  static int? _positive(dynamic raw) {
    final value = _valueNumber(raw)?.toInt();
    return value != null && value > 0 ? value : null;
  }

  /// The row's glyph (one per verb, COPY-18).
  KitToolKind get rowKind => switch (kind) {
    _ToolKind.read || _ToolKind.lsp => KitToolKind.read,
    _ToolKind.list || _ToolKind.glob => KitToolKind.list,
    _ToolKind.grep => KitToolKind.search,
    _ToolKind.shell => KitToolKind.shell,
    _ToolKind.edit || _ToolKind.write || _ToolKind.patch => KitToolKind.edit,
    _ToolKind.webFetch || _ToolKind.webSearch => KitToolKind.web,
    _ToolKind.task => KitToolKind.agent,
    _ToolKind.todo || _ToolKind.plan => KitToolKind.todo,
    _ToolKind.question => KitToolKind.question,
    _ToolKind.skill => KitToolKind.skill,
    _ToolKind.connectorSearch => KitToolKind.search,
    _ToolKind.generic || _ToolKind.worktreeSetup => KitToolKind.other,
  };
}

/// Wall-clock tool run time as the card shows it: tenths of a second under
/// a minute ("0.8s", "12.4s"), minutes and zero-padded seconds past it
/// ("1m 05s").
String formatToolDuration(Duration duration, {AppLocalizations? l10n}) {
  final strings = l10n ?? lookupAppLocalizations(const Locale('en'));
  final clamped = duration.isNegative ? Duration.zero : duration;
  if (clamped.inMinutes >= 1) {
    final seconds = (clamped.inSeconds % 60).toString().padLeft(2, '0');
    return strings.chatUiDurationMinutesSeconds(clamped.inMinutes, seconds);
  }
  return strings.chatUiDurationSeconds(
    (clamped.inMilliseconds / 1000).toStringAsFixed(1),
  );
}
