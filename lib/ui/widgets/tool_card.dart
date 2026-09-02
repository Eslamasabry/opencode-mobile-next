import 'dart:convert';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../app_theme.dart';
import 'agent_color.dart';
import 'file_preview.dart';
import 'markdown.dart';

// The chat transcript (a `part` of chat_screen.dart) opens file diffs through
// this library, which it already imports; tool cards render diffs too.

typedef ToolOutputFileLoader =
    Future<FilePreviewData> Function(ToolOutputFile file);
typedef ToolOutputFileAction =
    Future<void> Function(ToolOutputFile file, FilePreviewData data);

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

/// The child session the OpenCode `task` tool spawned for its subagent, read
/// from the tool metadata. The server writes `sessionId` (alongside
/// `parentSessionId`, `model` and, for background jobs, `jobId`); the other
/// spellings cover older builds and hand-written fixtures.
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
String? _taskOutputState(String? output) {
  if (output == null) return null;
  final match = RegExp(r'<task\b[^>]*\bstate="([a-z_]+)"').firstMatch(output);
  return match?.group(1);
}

/// Text inside `<task_result>` / `<task_error>`, or the whole output when the
/// server did not wrap it.
String _taskResultText(String? output) {
  final raw = output ?? '';
  final match = RegExp(
    r'<task_(?:result|error)>\n?(.*?)\n?</task_(?:result|error)>',
    dotAll: true,
  ).firstMatch(raw);
  return (match?.group(1) ?? raw).trim();
}

/// Compact "Title · subtitle" line for the tool currently executing, used by
/// the chat tool-group header as a live ticker while a run is active.
String runningToolTicker(String rawName, ToolState state) {
  final contract = _ToolContract.from(rawName, state);
  final subtitle = contract.subtitle;
  return subtitle == null || subtitle.isEmpty
      ? contract.title
      : '${contract.title} · $subtitle';
}

class _ToolContract {
  final _ToolKind kind;
  final String title;
  final String? subtitle;
  final List<String> details;

  const _ToolContract({
    required this.kind,
    required this.title,
    this.subtitle,
    this.details = const [],
  });

  factory _ToolContract.from(String rawName, ToolState state) {
    final name = rawName.trim().toLowerCase();
    final input = state.input;
    final metadata = state.metadata ?? const <String, dynamic>{};
    final details = <String>[];

    _ToolKind kind;
    String title;
    String? subtitle;
    switch (name) {
      case 'read':
        kind = _ToolKind.read;
        title = 'Read';
        final path = _valueString(input['filePath']);
        subtitle = path == null ? null : _fileName(path);
        if (_valueNumber(input['offset']) case final value?) {
          details.add('from $value');
        }
        if (_valueNumber(input['limit']) case final value?) {
          details.add('$value lines');
        }
        final display = metadata['display'];
        if (display is Map) {
          final start = _valueNumber(display['lineStart']);
          final end = _valueNumber(display['lineEnd']);
          if (start != null && end != null) details.add('L$start–$end');
          final count = display['entries'] is List
              ? (display['entries'] as List).length
              : null;
          if (count != null) details.add('$count entries');
        }
        break;
      case 'list':
        kind = _ToolKind.list;
        title = 'List';
        subtitle = _valueString(input['path']) ?? state.title;
        break;
      case 'glob':
        kind = _ToolKind.glob;
        title = 'Find files';
        subtitle = _valueString(input['pattern']);
        if (_valueNumber(metadata['count']) case final value?) {
          details.add('$value found');
        }
        break;
      case 'grep':
        kind = _ToolKind.grep;
        title = 'Search text';
        subtitle = _valueString(input['pattern']);
        if (_valueNumber(metadata['matches']) case final value?) {
          details.add('$value matches');
        }
        if (_valueString(input['include']) case final value?) {
          details.add(value);
        }
        break;
      case 'bash':
      case 'shell':
        kind = _ToolKind.shell;
        title = 'Shell';
        subtitle = _valueString(input['command']);
        final exit = _valueNumber(metadata['exit']);
        if (exit != null) details.add('exit $exit');
        // v2 shell messages surface their terminal status when the command
        // did not run to completion.
        final shellStatus = _valueString(metadata['shellStatus']) ?? '';
        if (shellStatus == 'timeout' || shellStatus == 'killed') {
          details.add(shellStatus);
        }
        if (metadata['truncated'] == true) details.add('truncated');
        break;
      case 'edit':
        kind = _ToolKind.edit;
        title = 'Edit';
        final path = _valueString(input['filePath']);
        subtitle = path == null ? state.title : _fileName(path);
        _addDiffDetails(details, metadata['filediff']);
        break;
      case 'write':
        kind = _ToolKind.write;
        title = 'Write';
        final path = _valueString(input['filePath']);
        subtitle = path == null ? state.title : _fileName(path);
        details.add(metadata['exists'] == false ? 'new file' : 'updated');
        break;
      case 'patch':
      case 'apply_patch':
        kind = _ToolKind.patch;
        title = 'Apply patch';
        final files = metadata['files'];
        if (files is List) {
          subtitle = '${files.length} ${files.length == 1 ? 'file' : 'files'}';
          var additions = 0;
          var deletions = 0;
          for (final file in files.whereType<Map>()) {
            additions += (_valueNumber(file['additions']) ?? 0).toInt();
            deletions += (_valueNumber(file['deletions']) ?? 0).toInt();
          }
          if (additions > 0) details.add('+$additions');
          if (deletions > 0) details.add('−$deletions');
        }
        break;
      case 'webfetch':
        kind = _ToolKind.webFetch;
        title = 'Fetch page';
        subtitle = _valueString(input['url']);
        if (_valueString(input['format']) case final value?) details.add(value);
        break;
      case 'websearch':
        kind = _ToolKind.webSearch;
        title = _valueString(metadata['provider']) == null
            ? 'Web search'
            : '${metadata['provider']} search';
        subtitle = _valueString(input['query']);
        if (_valueNumber(metadata['numResults']) case final value?) {
          details.add('$value results');
        }
        break;
      case 'task':
        kind = _ToolKind.task;
        title = _valueString(input['subagent_type']) ?? 'Agent';
        subtitle = _valueString(input['description']);
        if (metadata['background'] == true || input['background'] == true) {
          details.add('background');
        }
        // The child session's own state, as the server stamps it on the
        // <task> wrapper; a background job reads "running" after the call
        // itself has completed.
        if (_taskOutputState(state.output) case final childState?) {
          details.add(childState);
        }
        break;
      case 'todowrite':
      case 'todo':
        kind = _ToolKind.todo;
        title = 'Tasks';
        final todos = metadata['todos'] ?? input['todos'];
        if (todos is List) {
          final done = todos
              .where((item) => item is Map && item['status'] == 'completed')
              .length;
          subtitle = '$done/${todos.length} completed';
        }
        break;
      case 'question':
        kind = _ToolKind.question;
        title = 'Questions';
        final questions = input['questions'];
        final answers = metadata['answers'];
        if (questions is List) {
          subtitle = answers is List && answers.isNotEmpty
              ? '${questions.length} answered'
              : '${questions.length} asked';
        }
        break;
      case 'lsp':
        kind = _ToolKind.lsp;
        title = _valueString(input['operation']) ?? 'Language server';
        final path = _valueString(input['filePath']);
        subtitle = path == null ? null : _fileName(path);
        break;
      case 'skill':
        kind = _ToolKind.skill;
        title = 'Skill';
        subtitle = _valueString(input['name']);
        break;
      default:
        kind = _ToolKind.generic;
        title = state.title?.trim().isNotEmpty == true ? state.title! : rawName;
        subtitle = null;
    }
    if (metadata['truncated'] == true && !details.contains('truncated')) {
      details.add('truncated');
    }
    return _ToolContract(
      kind: kind,
      title: title,
      subtitle: subtitle,
      details: details,
    );
  }

  static void _addDiffDetails(List<String> details, dynamic raw) {
    if (raw is! Map) return;
    final additions = _valueNumber(raw['additions']);
    final deletions = _valueNumber(raw['deletions']);
    if (additions != null && additions > 0) details.add('+$additions');
    if (deletions != null && deletions > 0) details.add('−$deletions');
  }
}

/// Wall-clock tool run time as the card shows it: tenths of a second under
/// a minute ("0.8s", "12.4s"), minutes and zero-padded seconds past it
/// ("1m 05s").
String formatToolDuration(Duration duration) {
  final clamped = duration.isNegative ? Duration.zero : duration;
  if (clamped.inMinutes >= 1) {
    final seconds = (clamped.inSeconds % 60).toString().padLeft(2, '0');
    return '${clamped.inMinutes}m ${seconds}s';
  }
  return '${(clamped.inMilliseconds / 1000).toStringAsFixed(1)}s';
}

/// Renders a single tool invocation as an expandable card with status,
/// title, input and output.
class ToolCard extends StatefulWidget {
  final String toolName;
  final ToolState state;
  final bool embedded;

  /// Optional longer-lived store (e.g. session-scoped) keyed by
  /// [expansionKey], so expansion survives list recycling in a virtualized
  /// transcript instead of resetting when the item State is rebuilt.
  final Map<String, bool>? expansionStore;
  final String? expansionKey;
  final ToolOutputFileLoader? filePreviewLoader;
  final ToolOutputFileAction? onAttachFile;
  final ToolOutputFileAction? onDownloadFile;

  /// Opens the child session a `task` tool call delegated to, given its id.
  /// Null hides the "Open subagent session" action.
  final ValueChanged<String>? onOpenSession;
  const ToolCard({
    super.key,
    required this.toolName,
    required this.state,
    this.embedded = false,
    this.expansionStore,
    this.expansionKey,
    this.filePreviewLoader,
    this.onAttachFile,
    this.onDownloadFile,
    this.onOpenSession,
  });

  @override
  State<ToolCard> createState() => _ToolCardState();
}

class _ToolCardState extends State<ToolCard> {
  bool _expanded = false;
  final Map<String, Future<FilePreviewData>> _previewLoads = {};

  List<ToolOutputFile> get _files => widget.state.outputFiles.take(8).toList();
  List<ToolOutputFile> get _images =>
      _files.where((file) => file.isImage).toList();

  /// The ordered v2 content segments, but only when their order carries
  /// information a joined string loses — a text run after a file. Trivial
  /// orders (all text, or text followed only by trailing files) keep the
  /// existing v1 rendering exactly.
  List<ToolResultSegment>? get _interleavedSegments {
    final segments = widget.state.segments;
    var seenFile = false;
    for (final segment in segments) {
      if (segment.isFile) {
        seenFile = true;
      } else if (seenFile) {
        return segments;
      }
    }
    return null;
  }

  bool? get _storedExpansion => widget.expansionKey == null
      ? null
      : widget.expansionStore?[widget.expansionKey!];

  void _toggleExpanded() {
    setState(() {
      _expanded = !_expanded;
      if (widget.expansionKey case final key?) {
        widget.expansionStore?[key] = _expanded;
      }
    });
  }

  @override
  void initState() {
    super.initState();
    _expanded = _storedExpansion ?? (widget.state.status == 'error');
    _syncPreviewLoads();
  }

  @override
  void didUpdateWidget(covariant ToolCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.state.outputFiles, widget.state.outputFiles) ||
        oldWidget.filePreviewLoader != widget.filePreviewLoader) {
      _syncPreviewLoads(reset: true);
    }
  }

  void _syncPreviewLoads({bool reset = false}) {
    if (reset) _previewLoads.clear();
    final identities = _files.map((file) => file.identity).toSet();
    _previewLoads.removeWhere((key, _) => !identities.contains(key));
    for (final file in _images) {
      _previewLoads.putIfAbsent(file.identity, () => _loadPreview(file));
    }
  }

  Future<FilePreviewData> _loadPreview(ToolOutputFile file) async {
    try {
      final url = file.url;
      if (url?.isNotEmpty == true) {
        return FilePreviewData.fromDataUrl(
          name: file.displayName,
          mimeType: file.mimeType,
          url: url,
        );
      }
      final loader = widget.filePreviewLoader;
      if (loader != null && file.path?.isNotEmpty == true) {
        return await loader(file);
      }
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: 'The generated file is not available from this server.',
      );
    } catch (error) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: 'Could not load this file from the OpenCode server: $error',
      );
    }
  }

  Future<FilePreviewData> _loadCached(ToolOutputFile file) =>
      _previewLoads.putIfAbsent(file.identity, () => _loadPreview(file));

  void _retryPreview(ToolOutputFile file) {
    setState(() => _previewLoads[file.identity] = _loadPreview(file));
  }

  IconData _iconFor(_ToolKind kind) {
    return switch (kind) {
      _ToolKind.shell => Icons.terminal_rounded,
      _ToolKind.edit ||
      _ToolKind.write ||
      _ToolKind.patch => Icons.edit_note_rounded,
      _ToolKind.read => Icons.description_rounded,
      _ToolKind.list || _ToolKind.glob => Icons.folder_open_rounded,
      _ToolKind.grep => Icons.search_rounded,
      _ToolKind.webFetch || _ToolKind.webSearch => Icons.public_rounded,
      _ToolKind.todo => Icons.checklist_rounded,
      _ToolKind.task => Icons.smart_toy_rounded,
      _ToolKind.question => Icons.help_outline_rounded,
      _ToolKind.lsp => Icons.description_rounded,
      _ToolKind.skill || _ToolKind.generic => Icons.build_rounded,
    };
  }

  Color get _statusColor {
    switch (widget.state.status) {
      case 'completed':
        return AppTheme.successOf(Theme.of(context));
      case 'error':
        return Theme.of(context).colorScheme.error;
      default:
        return Theme.of(context).colorScheme.primary;
    }
  }

  bool get _running =>
      widget.state.executed &&
      (widget.state.status == 'pending' || widget.state.status == 'running');

  /// Run time shown once the tool has finished; nothing while it runs or
  /// when the server sent no timestamps.
  String? get _durationLabel {
    if (_running || !widget.state.executed) return null;
    final duration = widget.state.duration;
    return duration == null ? null : formatToolDuration(duration);
  }

  /// One ordered v2 content segment: a mono text run, an image preview, or a
  /// file tile.
  Widget _segmentWidget(ToolResultSegment segment) {
    final file = segment.file;
    if (file == null) {
      return _Mono(text: segment.text!, name: 'tool-output.txt', maxLines: 220);
    }
    if (file.isImage) {
      return _ToolOutputPreview(
        file: file,
        load: _loadCached(file),
        onRetry: () => _retryPreview(file),
        onAttach: widget.onAttachFile,
        onDownload: widget.onDownloadFile,
      );
    }
    return _ToolOutputFileTile(
      file: file,
      load: () => _loadCached(file),
      onAttach: widget.onAttachFile,
      onDownload: widget.onDownloadFile,
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final contract = _ToolContract.from(widget.toolName, widget.state);
    final hasBody =
        (widget.state.output?.isNotEmpty ?? false) ||
        (widget.state.inputJson?.isNotEmpty ?? false) ||
        widget.state.input.isNotEmpty ||
        widget.state.metadata?.isNotEmpty == true ||
        _files.isNotEmpty;
    final reduceMotion = MediaQuery.disableAnimationsOf(context);

    return Container(
      key: widget.embedded ? const Key('embedded-tool-row') : null,
      margin: widget.embedded
          ? EdgeInsets.zero
          : const EdgeInsets.symmetric(vertical: 3),
      decoration: widget.embedded
          ? null
          : BoxDecoration(
              color: theme.colorScheme.surfaceContainerHighest.withValues(
                alpha: .35,
              ),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppTheme.hairline(theme)),
            ),
      child: _runningAccent(
        theme,
        reduceMotion,
        Column(
          children: [
            Semantics(
              button: hasBody,
              expanded: hasBody ? _expanded : null,
              label:
                  '${contract.title}, ${widget.state.executed ? widget.state.status : 'not run'}',
              child: InkWell(
                onTap: hasBody ? _toggleExpanded : null,
                borderRadius: widget.embedded
                    ? BorderRadius.zero
                    : BorderRadius.circular(8),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 48),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(10, 8, 8, 8),
                    child: Row(
                      children: [
                        Icon(
                          _iconFor(contract.kind),
                          size: 16,
                          color: AppTheme.mutedOf(
                            theme,
                          ).withValues(alpha: widget.state.executed ? 1 : .6),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Row(
                            children: [
                              Text(
                                contract.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.bodySmall!.copyWith(
                                  // A call the server never ran greys out
                                  // rather than striking through: the title
                                  // stays legible, the state carries the news.
                                  color: widget.state.executed
                                      ? theme.colorScheme.onSurface.withValues(
                                          alpha: .9,
                                        )
                                      : AppTheme.mutedOf(theme),
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                              if (contract.subtitle?.isNotEmpty == true) ...[
                                const SizedBox(width: 6),
                                Expanded(
                                  child: Text(
                                    contract.subtitle!,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                      fontFamily:
                                          contract.kind == _ToolKind.shell
                                          ? AppTheme.monoFamily
                                          : null,
                                    ),
                                  ),
                                ),
                              ] else
                                const Spacer(),
                            ],
                          ),
                        ),
                        if (contract.details.isNotEmpty) ...[
                          const SizedBox(width: 6),
                          ConstrainedBox(
                            constraints: const BoxConstraints(maxWidth: 92),
                            child: Text(
                              contract.details.join(' · '),
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ),
                        ],
                        if (!widget.state.executed) ...[
                          const SizedBox(width: 6),
                          Text(
                            'Not run',
                            key: const Key('tool-not-run'),
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: AppTheme.mutedOf(theme),
                            ),
                          ),
                        ] else if (_durationLabel case final duration?) ...[
                          const SizedBox(width: 6),
                          Text(
                            duration,
                            key: const Key('tool-duration'),
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: AppTheme.mutedOf(theme),
                              fontFeatures: const [
                                FontFeature.tabularFigures(),
                              ],
                            ),
                          ),
                        ],
                        const SizedBox(width: 6),
                        if (_running && !reduceMotion)
                          SizedBox(
                            width: 12,
                            height: 12,
                            child: CircularProgressIndicator(
                              strokeWidth: 1.6,
                              color: theme.colorScheme.primary,
                            ),
                          )
                        else if (!widget.state.executed)
                          Icon(
                            Icons.block_rounded,
                            size: 14,
                            color: AppTheme.statusColor(
                              theme,
                              AppStatusTone.neutral,
                            ),
                          )
                        else
                          Icon(
                            _running
                                ? Icons.hourglass_top_rounded
                                : widget.state.status == 'error'
                                ? Icons.error_outline_rounded
                                : Icons.check_circle_outline_rounded,
                            size: 14,
                            color: _statusColor,
                          ),
                        if (hasBody) ...[
                          const SizedBox(width: 4),
                          AnimatedRotation(
                            turns: _expanded ? .5 : 0,
                            duration: reduceMotion
                                ? Duration.zero
                                : const Duration(milliseconds: 150),
                            child: Icon(
                              Icons.expand_more_rounded,
                              size: 16,
                              color: AppTheme.mutedOf(theme),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
            if (widget.state.pruned) _PrunedNote(embedded: widget.embedded),
            if (_interleavedSegments case final segments?)
              Padding(
                key: const Key('tool-interleaved-output'),
                padding: const EdgeInsets.fromLTRB(10, 2, 10, 10),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    for (final segment in segments)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: _segmentWidget(segment),
                      ),
                  ],
                ),
              )
            else if (_files.isNotEmpty)
              Padding(
                padding: const EdgeInsets.fromLTRB(10, 2, 10, 10),
                child: Column(
                  children: [
                    for (final file in _files)
                      Padding(
                        padding: const EdgeInsets.only(top: 6),
                        child: file.isImage
                            ? _ToolOutputPreview(
                                file: file,
                                load: _previewLoads[file.identity]!,
                                onRetry: () => _retryPreview(file),
                                onAttach: widget.onAttachFile,
                                onDownload: widget.onDownloadFile,
                              )
                            : _ToolOutputFileTile(
                                file: file,
                                load: () => _loadCached(file),
                                onAttach: widget.onAttachFile,
                                onDownload: widget.onDownloadFile,
                              ),
                      ),
                  ],
                ),
              ),
            if (_expanded && hasBody)
              Container(
                width: double.infinity,
                padding: widget.embedded
                    ? const EdgeInsets.fromLTRB(34, 0, 10, 8)
                    : const EdgeInsets.fromLTRB(10, 4, 10, 10),
                child: _ToolContractBody(
                  contract: contract,
                  state: widget.state,
                  embedded: widget.embedded,
                  onOpenSession: widget.onOpenSession,
                  // Output already rendered in order above; the expanded body
                  // keeps input/metadata only.
                  suppressOutput: _interleavedSegments != null,
                ),
              ),
          ],
        ),
      ),
    );
  }

  /// A 2px primary rule down the card's left edge while the tool runs, so a
  /// glance down the transcript finds the live call without reading status
  /// glyphs. The rule keeps its width when idle (transparent) so content
  /// never shifts; it fades unless the platform asks for reduced motion.
  Widget _runningAccent(ThemeData theme, bool reduceMotion, Widget child) {
    final accent = AnimatedContainer(
      key: const Key('tool-card-accent'),
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 180),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(
            color: _running ? theme.colorScheme.primary : Colors.transparent,
            width: 2,
          ),
        ),
      ),
      child: child,
    );
    if (widget.embedded) return accent;
    // Straight rule inside a rounded card: clip so the corners stay round.
    return ClipRRect(borderRadius: BorderRadius.circular(8), child: accent);
  }
}

class _ToolContractBody extends StatelessWidget {
  const _ToolContractBody({
    required this.contract,
    required this.state,
    required this.embedded,
    this.suppressOutput = false,
    this.onOpenSession,
  });

  final _ToolContract contract;
  final ToolState state;
  final bool embedded;
  final ValueChanged<String>? onOpenSession;

  /// True when the ordered segment rendering already shows the output; the
  /// expanded body then only adds the input JSON.
  final bool suppressOutput;

  Map<String, dynamic> get _metadata =>
      state.metadata ?? const <String, dynamic>{};

  @override
  Widget build(BuildContext context) {
    // A failed delegation still shows who was asked to do what; the error
    // lands in the Result section instead of replacing the whole body.
    if (contract.kind == _ToolKind.task && !suppressOutput) {
      return _taskBody(context);
    }
    if (state.status == 'error') {
      return _ErrorOutput(
        message: state.output ?? 'Tool failed.',
        embedded: embedded,
      );
    }
    if (suppressOutput) return _genericInput();
    return switch (contract.kind) {
      _ToolKind.read => _readBody(),
      _ToolKind.shell => _shellBody(),
      _ToolKind.edit => _editBody(),
      _ToolKind.write => _writeBody(),
      _ToolKind.patch => _patchBody(),
      _ToolKind.todo => _todoBody(),
      _ToolKind.question => _questionBody(),
      _ToolKind.webFetch || _ToolKind.webSearch => _richOutputBody(),
      _ToolKind.task => _taskBody(context),
      _ToolKind.list || _ToolKind.glob || _ToolKind.grep => _searchBody(),
      _ToolKind.lsp => _lspBody(),
      _ToolKind.skill => _skillBody(),
      _ToolKind.generic => _genericBody(),
    };
  }

  Widget _readBody() {
    final display = _metadata['display'];
    if (display is Map) {
      final map = Map<String, dynamic>.from(display);
      final type = _valueString(map['type']);
      final path =
          _valueString(map['path']) ??
          _valueString(state.input['filePath']) ??
          'read-output.txt';
      if (type == 'directory' && map['entries'] is List) {
        return _PathList(
          path: path,
          entries: (map['entries'] as List).map((item) => '$item').toList(),
          footer: map['truncated'] == true
              ? '${map['totalEntries'] ?? ''} total · more available'
              : '${map['totalEntries'] ?? (map['entries'] as List).length} entries',
        );
      }
      final text = _rawString(map['text']);
      if (type == 'file' && text?.trim().isNotEmpty == true) {
        return _Mono(text: text!, name: path, maxLines: 240);
      }
    }

    final output = state.output ?? '';
    final directory = RegExp(
      r'<entries>\n?(.*?)\n?</entries>',
      dotAll: true,
    ).firstMatch(output);
    if (directory != null) {
      final entries = directory
          .group(1)!
          .split('\n')
          .where((line) => line.trim().isNotEmpty && !line.startsWith('('))
          .toList();
      return _PathList(
        path: _valueString(state.input['filePath']) ?? 'Directory',
        entries: entries,
      );
    }
    final content = RegExp(
      r'<content>\n?(.*?)\n?</content>',
      dotAll: true,
    ).firstMatch(output);
    if (content != null) {
      final text = content
          .group(1)!
          .split('\n')
          .map((line) => line.replaceFirst(RegExp(r'^\d+: '), ''))
          .where((line) => !line.startsWith('(End of file'))
          .join('\n');
      return _Mono(
        text: text,
        name: _valueString(state.input['filePath']) ?? 'read-output.txt',
        maxLines: 240,
      );
    }
    return _plainOutput('read-output.txt');
  }

  Widget _shellBody() {
    final command = _valueString(state.input['command']) ?? '';
    final streamed = _rawString(_metadata['output']);
    var output = state.output?.trim().isNotEmpty == true
        ? state.output!
        : streamed ?? '';
    output = output.replaceAll(
      RegExp(r'\n*<shell_metadata>.*?</shell_metadata>', dotAll: true),
      '',
    );
    output = output.replaceAll(
      RegExp(r'\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])'),
      '',
    );
    final text = [
      if (command.isNotEmpty) '\$ $command',
      if (output.isNotEmpty) output,
    ].join('\n\n');
    return _Mono(text: text, name: 'terminal.log', maxLines: 260);
  }

  Widget _editBody() {
    final filediff = _metadata['filediff'];
    final patch = filediff is Map ? _rawString(filediff['patch']) : null;
    final diff = patch ?? _rawString(_metadata['diff']);
    if (diff?.trim().isNotEmpty == true) return _DiffPreview(diff: diff!);
    final oldText = _rawString(state.input['oldString']);
    final newText = _rawString(state.input['newString']);
    if (oldText != null || newText != null) {
      return _DiffPreview(
        diff: [
          if (oldText != null) ...oldText.split('\n').map((line) => '-$line'),
          if (newText != null) ...newText.split('\n').map((line) => '+$line'),
        ].join('\n'),
      );
    }
    return _plainOutput('edit-output.txt');
  }

  Widget _writeBody() {
    final content = _rawString(state.input['content']);
    if (content?.trim().isNotEmpty != true) {
      return _plainOutput('write-output.txt');
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _Mono(
          text: content!,
          name: _valueString(state.input['filePath']) ?? 'written-file.txt',
          maxLines: 240,
        ),
        if (_hasDiagnostics) ...[const SizedBox(height: 8), _diagnostics()],
      ],
    );
  }

  Widget _patchBody() {
    final rawFiles = _metadata['files'];
    if (rawFiles is List && rawFiles.isNotEmpty) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final raw in rawFiles.whereType<Map>())
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _PatchFileSection(file: Map<String, dynamic>.from(raw)),
            ),
          if (_hasDiagnostics) _diagnostics(),
        ],
      );
    }
    final diff =
        _rawString(_metadata['diff']) ?? _rawString(state.input['patchText']);
    return diff?.trim().isNotEmpty != true
        ? _plainOutput('patch-output.txt')
        : _DiffPreview(diff: diff!);
  }

  Widget _searchBody() {
    final path = _valueString(state.input['path']);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (path != null) _PathCaption(label: 'Scope', value: path),
        if (_valueString(state.input['include']) case final include?)
          _PathCaption(label: 'Files', value: include),
        if (path != null || state.input['include'] != null)
          const SizedBox(height: 6),
        _plainOutput('search-results.txt'),
      ],
    );
  }

  Widget _richOutputBody() {
    final output = state.output ?? '';
    if (output.trim().isEmpty) return _genericInput();
    final name = switch (contract.kind) {
      _ToolKind.webFetch =>
        _valueString(state.input['format']) == 'html'
            ? 'response.html'
            : 'response.md',
      _ToolKind.webSearch => 'search-results.md',
      _ => 'agent-result.md',
    };
    return SmartTextPreview(
      data: FilePreviewData(name: name, text: output),
    );
  }

  /// `task`: the parent agent delegating to a subagent. Who was asked
  /// (agent chip + description), what they were told (the prompt, collapsed),
  /// and what came back (the `<task_result>`), plus a jump to the child
  /// session when the host can open one.
  Widget _taskBody(BuildContext context) {
    final agent = _valueString(state.input['subagent_type']) ?? 'agent';
    final description =
        _valueString(state.input['description']) ?? _valueString(state.title);
    final background =
        _metadata['background'] == true || state.input['background'] == true;
    final prompt = _rawString(state.input['prompt']);
    final model = _metadata['model'];
    final modelLabel = model is Map
        ? _valueString(model['modelID'])
        : _valueString(model);
    final sessionId = taskChildSessionId(state);
    final resultText = _taskResultText(state.output);
    final childState = _taskOutputState(state.output);
    final working =
        state.executed &&
        state.status != 'error' &&
        (state.status == 'running' ||
            state.status == 'pending' ||
            childState == 'running');

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _TaskHeader(
          agent: agent,
          description: description,
          background: background,
        ),
        if (modelLabel != null) ...[
          const SizedBox(height: 4),
          _PathCaption(label: 'Model', value: modelLabel),
        ],
        if (prompt?.trim().isNotEmpty == true) ...[
          const SizedBox(height: 10),
          _TaskPrompt(prompt: prompt!),
        ],
        const SizedBox(height: 10),
        const _SectionCaption(label: 'Result'),
        const SizedBox(height: 4),
        if (state.status == 'error')
          _ErrorOutput(
            message: resultText.isEmpty ? 'Subagent failed.' : resultText,
            embedded: embedded,
          )
        else if (resultText.isNotEmpty)
          SmartTextPreview(
            key: const Key('task-result'),
            data: FilePreviewData(name: 'agent-result.md', text: resultText),
          ),
        if (working) ...[
          if (resultText.isNotEmpty) const SizedBox(height: 6),
          const _TaskWorking(),
        ] else if (resultText.isEmpty && state.status != 'error')
          Text(
            '(no result)',
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
              color: AppTheme.mutedOf(Theme.of(context)),
            ),
          ),
        if (sessionId != null && onOpenSession != null) ...[
          const SizedBox(height: 4),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: const ValueKey('task-open-session'),
              onPressed: () => onOpenSession!(sessionId),
              style: TextButton.styleFrom(
                visualDensity: VisualDensity.compact,
                padding: const EdgeInsets.symmetric(horizontal: 8),
              ),
              icon: const Icon(Icons.open_in_new_rounded, size: 14),
              label: Text(
                'Open subagent session',
                style: Theme.of(context).textTheme.labelSmall,
              ),
            ),
          ),
        ],
      ],
    );
  }

  Widget _todoBody() {
    final raw = _metadata['todos'] ?? state.input['todos'];
    if (raw is! List || raw.isEmpty) return _plainOutput('tasks.json');
    return Column(
      children: [
        for (final item in raw.whereType<Map>())
          _TodoRow(todo: Map<String, dynamic>.from(item)),
      ],
    );
  }

  Widget _questionBody() {
    final questions = state.input['questions'];
    final answers = _metadata['answers'];
    if (questions is! List || questions.isEmpty) {
      return _plainOutput('question-output.txt');
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var index = 0; index < questions.length; index += 1)
          _QuestionAnswer(
            question: questions[index] is Map
                ? Map<String, dynamic>.from(questions[index] as Map)
                : {'question': '${questions[index]}'},
            answer: answers is List && index < answers.length
                ? answers[index]
                : null,
          ),
      ],
    );
  }

  Widget _lspBody() {
    final result = _metadata['result'] ?? state.outputValue ?? state.output;
    return _Mono(
      text: result is String
          ? result
          : const JsonEncoder.withIndent('  ').convert(result),
      name: 'language-server.json',
      maxLines: 200,
    );
  }

  Widget _skillBody() {
    final output = state.output;
    return output?.trim().isNotEmpty == true
        ? SmartTextPreview(
            data: FilePreviewData(name: 'skill.md', text: output),
          )
        : _genericInput();
  }

  Widget _genericBody() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (state.input.isNotEmpty || state.inputJson?.isNotEmpty == true)
          _genericInput(),
        if (state.output?.trim().isNotEmpty == true) ...[
          if (state.input.isNotEmpty || state.inputJson?.isNotEmpty == true)
            const SizedBox(height: 8),
          SmartTextPreview(
            data: FilePreviewData(
              name: state.outputValue is Map || state.outputValue is List
                  ? 'tool-output.json'
                  : 'tool-output.txt',
              text: state.output,
            ),
          ),
        ],
      ],
    );
  }

  Widget _genericInput() {
    final input = state.input.isNotEmpty
        ? const JsonEncoder.withIndent('  ').convert(state.input)
        : state.inputJson ?? '';
    return _Mono(text: input, name: 'tool-input.json', maxLines: 80);
  }

  Widget _plainOutput(String name) => _Mono(
    text: state.output?.trim().isNotEmpty == true
        ? state.output!
        : '(no output)',
    name: name,
    maxLines: 220,
  );

  bool get _hasDiagnostics {
    final diagnostics = _metadata['diagnostics'];
    return diagnostics is Map && diagnostics.isNotEmpty;
  }

  Widget _diagnostics() => _Mono(
    text: const JsonEncoder.withIndent(' ').convert(_metadata['diagnostics']),
    name: 'diagnostics.json',
    maxLines: 100,
  );
}

/// Agent chip, description and badges for a delegated task.
class _TaskHeader extends StatelessWidget {
  const _TaskHeader({
    required this.agent,
    required this.description,
    required this.background,
  });

  final String agent;
  final String? description;
  final bool background;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        _AgentChip(name: agent),
        if (description?.isNotEmpty == true) ...[
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              description!,
              key: const Key('task-description'),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ] else
          const Spacer(),
        if (background) ...[
          const SizedBox(width: 8),
          const _TaskBadge(
            key: Key('task-background-badge'),
            label: 'background',
            icon: Icons.schedule_rounded,
          ),
        ],
      ],
    );
  }
}

/// Small pill naming the subagent, tinted with its agent colour.
class _AgentChip extends StatelessWidget {
  const _AgentChip({required this.name});

  final String name;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = agentColorFor(context, name);
    return Container(
      key: const Key('task-agent-chip'),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(
        color: color.withValues(alpha: .14),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: .45)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.smart_toy_rounded, size: 12, color: color),
          const SizedBox(width: 4),
          Text(
            name,
            style: theme.textTheme.labelSmall?.copyWith(
              color: color,
              fontWeight: FontWeight.w600,
              letterSpacing: .2,
            ),
          ),
        ],
      ),
    );
  }
}

/// Muted outline pill for task flags ("background").
class _TaskBadge extends StatelessWidget {
  const _TaskBadge({super.key, required this.label, required this.icon});

  final String label;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AppTheme.hairline(theme)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 11, color: muted),
          const SizedBox(width: 3),
          Text(
            label,
            style: theme.textTheme.labelSmall?.copyWith(color: muted),
          ),
        ],
      ),
    );
  }
}

/// Mono section caption for tool bodies, in the style of [_PathCaption].
class _SectionCaption extends StatelessWidget {
  const _SectionCaption({required this.label, this.detail});

  final String label;
  final String? detail;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = theme.textTheme.labelSmall?.copyWith(
      fontFamily: AppTheme.monoFamily,
      color: AppTheme.mutedOf(theme),
    );
    return Row(
      children: [
        Text(label, style: style),
        if (detail?.isNotEmpty == true) ...[
          const SizedBox(width: 6),
          Expanded(
            child: Text(
              detail!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: style,
            ),
          ),
        ],
      ],
    );
  }
}

/// The instructions the parent agent handed the subagent, rendered as
/// markdown. Prompts run long, so only the first [_previewLines] lines show
/// until "Show full prompt" opens the rest inline.
class _TaskPrompt extends StatefulWidget {
  const _TaskPrompt({required this.prompt});

  final String prompt;

  static const _previewLines = 8;

  @override
  State<_TaskPrompt> createState() => _TaskPromptState();
}

class _TaskPromptState extends State<_TaskPrompt> {
  bool _showFull = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final text = widget.prompt.trimRight();
    final lines = text.split('\n');
    final truncatable = lines.length > _TaskPrompt._previewLines;
    final visible = truncatable && !_showFull
        ? lines.take(_TaskPrompt._previewLines).join('\n')
        : text;
    return Column(
      key: const Key('task-prompt'),
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _SectionCaption(
          label: 'Prompt from parent agent',
          detail: '${lines.length} ${lines.length == 1 ? 'line' : 'lines'}',
        ),
        const SizedBox(height: 4),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.fromLTRB(10, 8, 10, 8),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerLow,
            borderRadius: BorderRadius.circular(6),
            border: Border(
              left: BorderSide(
                width: 2,
                color: theme.colorScheme.primary.withValues(alpha: .45),
              ),
            ),
          ),
          child: ClipRect(
            child: ShaderMask(
              // Fade the last preview line so the cut reads as "continues".
              shaderCallback: (bounds) => LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                stops: const [0, .8, 1],
                colors: [
                  Colors.white,
                  Colors.white,
                  truncatable && !_showFull
                      ? Colors.white.withValues(alpha: .25)
                      : Colors.white,
                ],
              ).createShader(bounds),
              blendMode: BlendMode.dstIn,
              child: MarkdownText(
                visible,
                baseStyle: theme.textTheme.bodySmall,
              ),
            ),
          ),
        ),
        if (truncatable)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              key: const Key('task-prompt-toggle'),
              onPressed: () => setState(() => _showFull = !_showFull),
              style: TextButton.styleFrom(
                visualDensity: VisualDensity.compact,
                padding: const EdgeInsets.symmetric(horizontal: 8),
              ),
              icon: Icon(
                _showFull
                    ? Icons.unfold_less_rounded
                    : Icons.unfold_more_rounded,
                size: 14,
              ),
              label: Text(
                _showFull ? 'Show less' : 'Show full prompt',
                style: theme.textTheme.labelSmall,
              ),
            ),
          ),
      ],
    );
  }
}

/// "Subagent working…" placeholder while the child session has not reported
/// back yet.
class _TaskWorking extends StatelessWidget {
  const _TaskWorking();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    return Row(
      key: const Key('task-working'),
      children: [
        if (reduceMotion)
          Icon(Icons.hourglass_top_rounded, size: 12, color: muted)
        else
          SizedBox.square(
            dimension: 11,
            child: CircularProgressIndicator(
              strokeWidth: 1.4,
              color: theme.colorScheme.primary,
            ),
          ),
        const SizedBox(width: 7),
        Text(
          'Subagent working…',
          style: theme.textTheme.labelSmall?.copyWith(
            color: muted,
            fontStyle: FontStyle.italic,
          ),
        ),
      ],
    );
  }
}

class _ErrorOutput extends StatelessWidget {
  const _ErrorOutput({required this.message, required this.embedded});

  final String message;
  final bool embedded;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = SelectableText(
      message.replaceFirst(RegExp(r'^Error:\s*'), ''),
      style: Theme.of(context).textTheme.bodySmall?.copyWith(
        color: scheme.onErrorContainer,
        fontFamily: AppTheme.monoFamily,
      ),
    );
    if (embedded) {
      return Container(
        key: const Key('embedded-tool-error-output'),
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(8, 5, 0, 5),
        decoration: BoxDecoration(
          border: Border(
            left: BorderSide(
              width: 2,
              color: scheme.error.withValues(alpha: .72),
            ),
          ),
        ),
        child: text,
      );
    }
    return Container(
      key: const Key('standalone-tool-error-output'),
      width: double.infinity,
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: scheme.errorContainer.withValues(alpha: .28),
        borderRadius: BorderRadius.circular(AppTheme.radiusControl),
        border: Border.all(color: scheme.error.withValues(alpha: .28)),
      ),
      child: text,
    );
  }
}

class _PathCaption extends StatelessWidget {
  const _PathCaption({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Text('$label: ', style: Theme.of(context).textTheme.labelSmall),
      Expanded(
        child: Text(
          value,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: Theme.of(
            context,
          ).textTheme.labelSmall?.copyWith(fontFamily: AppTheme.monoFamily),
        ),
      ),
    ],
  );
}

class _PathList extends StatelessWidget {
  const _PathList({required this.path, required this.entries, this.footer});

  final String path;
  final List<String> entries;
  final String? footer;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _PathCaption(label: 'Directory', value: path),
        const SizedBox(height: 6),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerLow,
            borderRadius: BorderRadius.circular(6),
          ),
          child: SelectableText(
            entries.take(200).join('\n'),
            style: theme.textTheme.bodySmall?.copyWith(
              fontFamily: AppTheme.monoFamily,
              height: 1.4,
            ),
          ),
        ),
        if (footer?.isNotEmpty == true) ...[
          const SizedBox(height: 4),
          Text(footer!, style: theme.textTheme.labelSmall),
        ],
      ],
    );
  }
}

class _PatchFileSection extends StatelessWidget {
  const _PatchFileSection({required this.file});

  final Map<String, dynamic> file;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final path =
        _valueString(file['relativePath']) ??
        _valueString(file['movePath']) ??
        _valueString(file['filePath']) ??
        'Changed file';
    final additions = (_valueNumber(file['additions']) ?? 0).toInt();
    final deletions = (_valueNumber(file['deletions']) ?? 0).toInt();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: Text(
                path,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelMedium?.copyWith(
                  fontFamily: AppTheme.monoFamily,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            if (additions > 0)
              Text(
                '+$additions',
                style: TextStyle(color: AppTheme.successOf(Theme.of(context))),
              ),
            if (additions > 0 && deletions > 0) const SizedBox(width: 6),
            if (deletions > 0)
              Text(
                '−$deletions',
                style: TextStyle(color: theme.colorScheme.error),
              ),
          ],
        ),
        const SizedBox(height: 4),
        if (_rawString(file['patch']) case final patch?)
          _DiffPreview(diff: patch)
        else
          Text(
            _valueString(file['type']) ?? 'changed',
            style: theme.textTheme.bodySmall,
          ),
      ],
    );
  }
}

class _DiffPreview extends StatelessWidget {
  const _DiffPreview({required this.diff});

  final String diff;

  /// Inline rows before "See all" takes over; small enough (~240px) that the
  /// old IntrinsicWidth-over-500-lines layout cost is gone and the body
  /// never becomes a nested vertical scroll trap.
  static const _inlineLineCap = 13;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lines = diff.split('\n');
    final visible = lines.take(_inlineLineCap).toList();
    final truncated = lines.length > visible.length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: double.infinity,
          decoration: BoxDecoration(
            color: theme.colorScheme.surfaceContainerLowest,
            borderRadius: BorderRadius.circular(6),
            border: Border.all(color: AppTheme.hairline(theme)),
          ),
          child: LayoutBuilder(
            builder: (context, constraints) => SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: IntrinsicWidth(
                child: ConstrainedBox(
                  constraints: BoxConstraints(minWidth: constraints.maxWidth),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      for (final line in visible) _DiffPreviewLine(line: line),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
        if (truncated)
          _SeeAllButton(
            label: 'See all · ${lines.length} lines',
            onPressed: () => showFilePreviewSheet(
              context,
              FilePreviewData(name: 'changes.diff', text: diff),
            ),
          ),
      ],
    );
  }
}

class _DiffPreviewLine extends StatelessWidget {
  const _DiffPreviewLine({required this.line});

  final String line;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final added = line.startsWith('+') && !line.startsWith('+++');
    final removed = line.startsWith('-') && !line.startsWith('---');
    final header = line.startsWith('@@') || line.startsWith('diff ');
    final success = AppTheme.successOf(theme);
    final background = added
        ? success.withValues(alpha: .14)
        : removed
        ? theme.colorScheme.error.withValues(alpha: .12)
        : header
        ? theme.colorScheme.primary.withValues(alpha: .1)
        : Colors.transparent;
    final foreground = added
        ? success
        : removed
        ? theme.colorScheme.error
        : header
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurface;
    return ColoredBox(
      color: background,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 1.5),
        child: SelectableText(
          line.isEmpty ? ' ' : line,
          style: theme.textTheme.bodySmall?.copyWith(
            color: foreground,
            fontFamily: AppTheme.monoFamily,
            height: 1.35,
          ),
        ),
      ),
    );
  }
}

class _TodoRow extends StatelessWidget {
  const _TodoRow({required this.todo});

  final Map<String, dynamic> todo;

  @override
  Widget build(BuildContext context) {
    final status = _valueString(todo['status']) ?? 'pending';
    final done = status == 'completed';
    final active = status == 'in_progress';
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            done
                ? Icons.check_circle_outline_rounded
                : active
                ? Icons.hourglass_top_rounded
                : Icons.checklist_rounded,
            size: 17,
            color: done ? AppTheme.successOf(theme) : AppTheme.mutedOf(theme),
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              _valueString(todo['content']) ?? '(untitled task)',
              style: theme.textTheme.bodySmall?.copyWith(
                decoration: done ? TextDecoration.lineThrough : null,
                color: done ? AppTheme.mutedOf(theme) : null,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _QuestionAnswer extends StatelessWidget {
  const _QuestionAnswer({required this.question, this.answer});

  final Map<String, dynamic> question;
  final dynamic answer;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final joined = answer is List
        ? (answer as List).map((item) => '$item').join(', ')
        : _valueString(answer);
    final answerText = joined == null || joined.trim().isEmpty ? null : joined;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _valueString(question['question']) ?? 'Question',
            style: theme.textTheme.bodySmall?.copyWith(
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            answerText == null ? 'No answer' : 'Answered: $answerText',
            style: theme.textTheme.bodySmall?.copyWith(
              color: answerText == null
                  ? AppTheme.mutedOf(theme)
                  : theme.colorScheme.primary,
            ),
          ),
        ],
      ),
    );
  }
}

class _ToolOutputPreview extends StatelessWidget {
  const _ToolOutputPreview({
    required this.file,
    required this.load,
    required this.onRetry,
    this.onAttach,
    this.onDownload,
  });

  final ToolOutputFile file;
  final Future<FilePreviewData> load;
  final VoidCallback onRetry;
  final ToolOutputFileAction? onAttach;
  final ToolOutputFileAction? onDownload;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return FutureBuilder<FilePreviewData>(
      future: load,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return Container(
            key: const Key('tool-output-image-loading'),
            height: 112,
            width: double.infinity,
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const SizedBox.square(
                  dimension: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(height: 8),
                Text(
                  'Loading ${file.displayName}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.labelSmall,
                ),
              ],
            ),
          );
        }
        final data = snapshot.data;
        final error = snapshot.error?.toString() ?? data?.error;
        if (error != null || data?.bytes?.isNotEmpty != true) {
          return Container(
            key: const Key('tool-output-image-error'),
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(10, 8, 4, 8),
            decoration: BoxDecoration(
              color: theme.colorScheme.errorContainer.withValues(alpha: .22),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                color: theme.colorScheme.error.withValues(alpha: .28),
              ),
            ),
            child: Row(
              children: [
                Icon(
                  Icons.broken_image_outlined,
                  size: 18,
                  color: theme.colorScheme.error,
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        file.displayName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        error ?? 'Image data is unavailable.',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.labelSmall,
                      ),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Retry image preview',
                  onPressed: onRetry,
                  icon: const Icon(Icons.refresh_rounded, size: 18),
                ),
              ],
            ),
          );
        }

        final previewData = data!;
        final imageBytes = previewData.bytes!;
        void openPreview() => showFilePreviewSheet(
          context,
          previewData,
          onAttach: onAttach == null
              ? null
              : () => onAttach!(file, previewData),
          onDownload: onDownload == null
              ? null
              : () => onDownload!(file, previewData),
        );
        return Semantics(
          button: true,
          label: 'Preview generated image ${file.displayName}',
          child: InkWell(
            onTap: openPreview,
            borderRadius: BorderRadius.circular(8),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Stack(
                children: [
                  Container(
                    width: double.infinity,
                    constraints: const BoxConstraints(
                      minHeight: 120,
                      maxHeight: 260,
                    ),
                    color: theme.colorScheme.surfaceContainerLowest,
                    child: LayoutBuilder(
                      builder: (context, imageConstraints) {
                        // Decode at the preview's own pixel size: a full
                        // screenshot otherwise decodes at native resolution
                        // (tens of MB) for a ~260px-tall thumbnail.
                        final dpr = MediaQuery.devicePixelRatioOf(context);
                        final cacheWidth = imageConstraints.maxWidth.isFinite
                            ? (imageConstraints.maxWidth * dpr).round()
                            : null;
                        return Image.memory(
                          imageBytes,
                          key: const Key('tool-output-image'),
                          fit: BoxFit.contain,
                          cacheWidth: cacheWidth,
                          gaplessPlayback: true,
                        );
                      },
                    ),
                  ),
                  Positioned(
                    left: 8,
                    right: 8,
                    bottom: 8,
                    child: DecoratedBox(
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surface.withValues(alpha: .9),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 5,
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.image_outlined, size: 14),
                            const SizedBox(width: 6),
                            Expanded(
                              child: Text(
                                file.displayName,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: theme.textTheme.labelSmall,
                              ),
                            ),
                            const Icon(Icons.visibility_outlined, size: 13),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

class _ToolOutputFileTile extends StatefulWidget {
  const _ToolOutputFileTile({
    required this.file,
    required this.load,
    this.onAttach,
    this.onDownload,
  });

  final ToolOutputFile file;
  final Future<FilePreviewData> Function() load;
  final ToolOutputFileAction? onAttach;
  final ToolOutputFileAction? onDownload;

  @override
  State<_ToolOutputFileTile> createState() => _ToolOutputFileTileState();
}

class _ToolOutputFileTileState extends State<_ToolOutputFileTile> {
  bool _opening = false;

  Future<void> _open() async {
    if (_opening) return;
    setState(() => _opening = true);
    try {
      final data = await widget.load();
      if (!mounted) return;
      setState(() => _opening = false);
      await showFilePreviewSheet(
        context,
        data,
        onAttach: widget.onAttach == null
            ? null
            : () => widget.onAttach!(widget.file, data),
        onDownload: widget.onDownload == null
            ? null
            : () => widget.onDownload!(widget.file, data),
      );
    } finally {
      if (mounted && _opening) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Semantics(
      button: true,
      label: 'Open generated file ${widget.file.displayName}',
      child: Material(
        color: theme.colorScheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(8),
        child: InkWell(
          key: const Key('tool-output-file'),
          onTap: _opening ? null : _open,
          borderRadius: BorderRadius.circular(8),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 56),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: Row(
                children: [
                  Icon(
                    Icons.insert_drive_file_outlined,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.file.displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall?.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                        Text(
                          widget.file.mimeType ?? 'Generated file',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (_opening)
                    const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  else
                    const Icon(Icons.open_in_new_rounded, size: 18),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Inline caps for expanded tool bodies (~240px of monospace) so they never
/// become nested vertical scroll traps inside the transcript; anything
/// longer routes through "See all" to the file preview sheet.
const _monoInlineLineCap = 13;
const _monoInlineCharCap = 4000;

class _SeeAllButton extends StatelessWidget {
  const _SeeAllButton({required this.label, required this.onPressed});

  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => Align(
    alignment: Alignment.centerLeft,
    child: TextButton.icon(
      key: const Key('tool-body-see-all'),
      onPressed: onPressed,
      style: TextButton.styleFrom(
        visualDensity: VisualDensity.compact,
        padding: const EdgeInsets.symmetric(horizontal: 8),
      ),
      icon: const Icon(Icons.open_in_full_rounded, size: 14),
      label: Text(label, style: Theme.of(context).textTheme.labelSmall),
    ),
  );
}

class _Mono extends StatelessWidget {
  final String text;
  final String name;
  final int maxLines;
  const _Mono({required this.text, required this.name, this.maxLines = 100});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lines = text.split('\n');
    final lineCap = maxLines < _monoInlineLineCap
        ? maxLines
        : _monoInlineLineCap;
    var visible = lines.length > lineCap
        ? lines.take(lineCap).join('\n')
        : text;
    if (visible.length > _monoInlineCharCap) {
      visible = visible.substring(0, _monoInlineCharCap);
    }
    final truncated = visible.length < text.length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: theme.brightness == Brightness.dark
                ? Colors.black.withValues(alpha: .4)
                : Colors.black.withValues(alpha: .04),
            borderRadius: BorderRadius.circular(6),
          ),
          child: SmartTextPreview(
            data: FilePreviewData(
              name: name,
              text: truncated ? '$visible\n…' : visible,
            ),
          ),
        ),
        if (truncated)
          _SeeAllButton(
            label: 'See all · ${lines.length} lines',
            onPressed: () => showFilePreviewSheet(
              context,
              FilePreviewData(name: name, text: text),
            ),
          ),
      ],
    );
  }
}

/// "Output pruned": the server dropped this tool's output from the model's
/// context. A hatched wash marks the gap so it reads as removed content, not
/// an empty result.
class _PrunedNote extends StatelessWidget {
  const _PrunedNote({required this.embedded});

  final bool embedded;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = AppTheme.mutedOf(theme);
    return Padding(
      padding: embedded
          ? const EdgeInsets.fromLTRB(34, 0, 10, 8)
          : const EdgeInsets.fromLTRB(10, 0, 10, 8),
      child: Container(
        key: const Key('tool-pruned'),
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(6),
          border: Border.all(color: AppTheme.hairline(theme)),
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: const Alignment(-0.9, -0.55),
            tileMode: TileMode.repeated,
            stops: const [0, .5, .5, 1],
            colors: [
              muted.withValues(alpha: .10),
              muted.withValues(alpha: .10),
              Colors.transparent,
              Colors.transparent,
            ],
          ),
        ),
        child: Row(
          children: [
            Icon(Icons.content_cut_rounded, size: 13, color: muted),
            const SizedBox(width: 6),
            Text(
              'Output pruned',
              style: theme.textTheme.labelSmall?.copyWith(color: muted),
            ),
          ],
        ),
      ),
    );
  }
}
