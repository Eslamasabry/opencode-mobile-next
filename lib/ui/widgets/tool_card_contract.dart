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
  backgroundNotice,
  agentMessage,
  card,
  generic,
}

/// The file a read, edit or write touches: OpenCode says `filePath`, other
/// agents `file_path` or `path`. Without it a running row read a bare
/// "Read" or "Edit".
String? _inputPath(Map<String, dynamic> input) =>
    _valueString(input['filePath']) ??
    _valueString(input['file_path']) ??
    _valueString(input['path']);

/// The server's own title for the call, when it names something other than
/// the tool itself (its title starts as the tool's name before the input
/// arrives).
String? _titleNamingAFile(ToolState state, String toolName) {
  final title = _valueString(state.title);
  return title == null || title.toLowerCase() == toolName ? null : title;
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

/// Claude Code's notice that a background task ended, in the app's words.
/// Claude names the task in quotes inside one English sentence
/// (`Background agent "Fix login" didn't finish before the previous session
/// ended`), which Paseo hands over as the call's label and again as its
/// text: the row says the name and how it ended once, and the sentence
/// shows only when it says more than that.
class _TaskNotice {
  const _TaskNotice({
    required this.end,
    this.summary,
    this.name,
    this.cutOff = false,
  });

  final BackgroundTaskEnd end;

  /// Claude's sentence about the task.
  final String? summary;

  /// The task's name, from the quotes in [summary].
  final String? name;

  /// The task never finished: Claude's previous session (its process) ended
  /// under it.
  final bool cutOff;

  factory _TaskNotice.of(Map<String, dynamic> input) {
    final summary =
        _valueString(input['summary']) ??
        _valueString(input['description']) ??
        _valueString(input['name']);
    final cutOff =
        summary != null &&
        _previousSession.hasMatch(summary) &&
        _unfinished.hasMatch(summary) &&
        !_restarted.hasMatch(summary);
    final name = summary == null
        ? null
        : _quoted.firstMatch(summary)?.group(1)?.trim();
    return _TaskNotice(
      end: cutOff ? BackgroundTaskEnd.stopped : backgroundTaskEnd(input),
      summary: summary,
      name: name == null || name.isEmpty ? null : name,
      cutOff: cutOff,
    );
  }

  static final _quoted = RegExp(r'["\u201C]([^"\u201D\n]{1,120})["\u201D]');
  static final _previousSession = RegExp(
    r'previous (claude code )?(session|process)',
    caseSensitive: false,
  );
  static final _unfinished = RegExp(
    r"(didn't|did not|couldn't|could not) (finish|complete|be)|"
    r'no completion record|was interrupted',
    caseSensitive: false,
  );
  static final _restarted = RegExp(
    r'automatically restarted from|running in the background again',
    caseSensitive: false,
  );

  String title(AppLocalizations strings) {
    final raw = name;
    final named = raw == null ? null : KitBidi.auto(raw);
    if (cutOff) {
      return named == null
          ? strings.chatUiToolBackgroundTaskCutOff
          : strings.chatUiToolBackgroundNamedCutOff(named);
    }
    if (named != null) {
      return switch (end) {
        BackgroundTaskEnd.finished => strings.chatUiToolBackgroundNamedFinished(
          named,
        ),
        BackgroundTaskEnd.failed => strings.chatUiToolBackgroundNamedFailed(
          named,
        ),
        BackgroundTaskEnd.stopped => strings.chatUiToolBackgroundNamedStopped(
          named,
        ),
        BackgroundTaskEnd.ended => strings.chatUiToolBackgroundNamedEnded(
          named,
        ),
      };
    }
    return switch (end) {
      BackgroundTaskEnd.failed => strings.chatUiToolBackgroundTaskFailed,
      BackgroundTaskEnd.stopped => strings.chatUiToolBackgroundTaskStopped,
      BackgroundTaskEnd.finished => strings.chatUiToolBackgroundTaskFinished,
      BackgroundTaskEnd.ended => strings.chatUiToolBackgroundTaskEnded,
    };
  }

  /// The task's own words on the row's line, when the title did not say
  /// them already ("Background task finished · Release build").
  String? get lineDetail => cutOff || name != null ? null : summary;

  /// Whether [summary] belongs in the opened step too: not when the title
  /// already says all of it, nor when the line shows it whole.
  bool get summaryInBody {
    final text = summary;
    if (text == null || cutOff) return false;
    if (name == null) return text.characters.length > 40;
    // What the sentence says besides the name: "completed" adds nothing;
    // "failed: the build ran out of memory" does.
    final rest = text
        .replaceFirst(RegExp('^.*?${RegExp.escape(name!)}["\u201D]?'), '')
        .trim();
    return rest.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length > 3;
  }
}

/// An agent-to-agent message (Claude Code's SendMessage). Claude writes the
/// same message as `to`/`message` and as `recipient`/`content`, in the call
/// and again in its answer: each fact is read once, from the first place
/// that has it.
class _AgentMessage {
  const _AgentMessage({
    this.recipient,
    this.summary,
    this.text,
    this.structured,
    this.broadcast = false,
  });

  /// Who it goes to, as Claude wrote it: a teammate's name, `main`, `*`,
  /// or an agent's id.
  final String? recipient;

  /// Claude's few words for it ("assign task 1").
  final String? summary;

  /// What it says.
  final String? text;

  /// A message that is a request rather than words (a shutdown request):
  /// technical detail only.
  final Map<dynamic, dynamic>? structured;

  /// To every teammate at once.
  final bool broadcast;

  factory _AgentMessage.of(ToolState state) {
    final input = state.input;
    final answer = state.outputValue;
    final sources = [input, if (answer is Map) answer];
    String? pick(List<String> keys) {
      for (final source in sources) {
        for (final key in keys) {
          final value = source[key];
          if (value is String && _valueString(value) != null) {
            return value.trim();
          }
        }
      }
      return null;
    }

    final recipient = pick(const ['to', 'recipient']);
    final message = input['message'] ?? input['content'];
    return _AgentMessage(
      recipient: recipient,
      summary: pick(const ['summary']),
      // An answer's `message` is Claude's note to itself ("Message sent"),
      // never the message.
      text:
          _valueString(_rawString(message)) ??
          (answer is Map ? _valueString(_rawString(answer['content'])) : null),
      structured: message is Map ? message : null,
      broadcast:
          recipient == '*' ||
          pick(const ['type'])?.toLowerCase() == 'broadcast',
    );
  }

  /// [recipient] as a name a person reads; null for an id, `*` and none.
  String? get name {
    final value = recipient;
    if (value == null || value == '*' || _looksLikeId(value)) return null;
    return value;
  }

  /// [recipient] when it is an id: technical detail, never the title.
  String? get recipientId {
    final value = recipient;
    return value != null && value != '*' && _looksLikeId(value) ? value : null;
  }

  /// "a1b2c3d4e5f6", "agent-7f3e9a01", a UUID: a run of eight or more hex
  /// characters with a digit in it, and no spaces.
  static bool _looksLikeId(String value) =>
      !value.contains(RegExp(r'\s')) &&
      RegExp(
        r'(?=[0-9a-f]*[0-9])[0-9a-f]{8,}',
        caseSensitive: false,
      ).hasMatch(value);
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

/// Whether the call runs a shell command (Bash, a shell step): what a
/// person calls "the command".
bool isShellTool(String rawName, ToolState state) =>
    _ToolContract.from(rawName, state).kind == _ToolKind.shell;

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

/// The live line's words for the tool currently executing (concept C,
/// 20 Oct 2026): what the agent is doing, as a person would say it,
/// "Writing index.html", "Running npm test", "Reading game.js". A tool with
/// no everyday verb keeps [runningToolTicker]'s "Title · subtitle".
///
/// [named] false leaves out what it touches ("Running a command") for a
/// line whose step is drawn right above it with its command or path: the
/// command is said once (owner, 2026-10-27).
String runningToolPhrase(
  String rawName,
  ToolState state, {
  AppLocalizations? l10n,
  bool named = true,
}) {
  final strings = l10n ?? lookupAppLocalizations(const Locale('en'));
  final contract = _ToolContract.from(rawName, state, l10n: strings);
  final raw = contract.subtitle?.trim();
  String? name;
  if (named && raw != null && raw.isNotEmpty) {
    // One short line: a long command or pattern is cut, never wrapped.
    var first = raw.split('\n').first.trim();
    // A shell step shows the command, not the folder it starts in.
    if (contract.kind == _ToolKind.shell) {
      first = first
          .replaceFirst(RegExp(r'^cd\s+("[^"]*"|\S+)\s*(;|&&)\s*'), '')
          .trim();
    }
    name = first.characters.length > 40
        ? '${first.characters.take(39)}\u2026'
        : first;
    if (contract.technical) name = KitBidi.ltr(name);
  }
  return switch (contract.kind) {
    _ToolKind.read || _ToolKind.webFetch =>
      name == null ? strings.chatLiveReadingAny : strings.chatLiveReading(name),
    _ToolKind.edit || _ToolKind.patch =>
      name == null ? strings.chatLiveEditingAny : strings.chatLiveEditing(name),
    _ToolKind.write =>
      name == null ? strings.chatLiveWritingAny : strings.chatLiveWriting(name),
    _ToolKind.shell =>
      name == null ? strings.chatLiveRunningAny : strings.chatLiveRunning(name),
    _ToolKind.grep || _ToolKind.glob || _ToolKind.list =>
      name == null
          ? strings.chatLiveSearchingAny
          : strings.chatLiveSearching(name),
    _ToolKind.webSearch => strings.chatLiveSearchingWeb,
    _ToolKind.todo || _ToolKind.plan => strings.chatLivePlanning,
    _ => runningToolTicker(rawName, state, l10n: strings),
  };
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

  /// A background task's notice: how the task ended.
  final BackgroundTaskEnd? noticeEnd;

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
    this.noticeEnd,
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
    BackgroundTaskEnd? noticeEnd;
    switch (name) {
      case 'read':
        kind = _ToolKind.read;
        title = strings.chatUiRead;
        final path = _inputPath(input);
        final named = path == null ? _titleNamingAFile(state, name) : null;
        subtitle = path != null ? _fileName(path) : named;
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
        final path = _inputPath(input);
        subtitle = path == null
            ? _titleNamingAFile(state, name)
            : _fileName(path);
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
        final path = _inputPath(input);
        subtitle = path == null
            ? _titleNamingAFile(state, name)
            : _fileName(path);
        technical = path != null;
        // Only what the host said: no "Updated" guess when it is silent.
        if (metadata['exists'] == false) details.add(strings.chatUiNewFile);
        if (metadata['exists'] == true) details.add(strings.chatUiUpdated);
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
        // One line: the list itself is the tasks chip and its sheet, so the
        // transcript never draws it a second time.
        title = strings.toolCardUpdatedTasks;
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
        // One plain line: the task's name and how it ended, said once.
        kind = _ToolKind.backgroundNotice;
        final notice = _TaskNotice.of(input);
        noticeEnd = notice.end;
        title = notice.title(strings);
        subtitle = notice.lineDetail;
      case 'sendmessage':
      case 'send_message':
        // Claude Code writing to another agent: who it goes to, in words,
        // and its one-line summary; the text itself is the body.
        kind = _ToolKind.agentMessage;
        final message = _AgentMessage.of(state);
        final to = message.name;
        title = message.broadcast
            ? strings.chatUiToolMessageToEveryone
            : message.recipient == 'main'
            ? strings.chatUiToolMessageToMain
            : to != null
            ? strings.chatUiToolMessageTo(KitBidi.auto(to))
            : message.summary ?? strings.chatUiToolMessageToAgent;
        subtitle = title == message.summary ? null : message.summary;
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
          // The call is one quiet line: its input and its reply (written
          // for the model) are never shown as rows.
          kind = _ToolKind.card;
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
      noticeEnd: noticeEnd,
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
    _ToolKind.task || _ToolKind.agentMessage => KitToolKind.agent,
    _ToolKind.todo || _ToolKind.plan => KitToolKind.todo,
    _ToolKind.question => KitToolKind.question,
    _ToolKind.skill => KitToolKind.skill,
    _ToolKind.connectorSearch => KitToolKind.search,
    _ToolKind.generic ||
    _ToolKind.card ||
    _ToolKind.worktreeSetup ||
    _ToolKind.backgroundNotice => KitToolKind.other,
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
