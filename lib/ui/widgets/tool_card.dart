import 'dart:convert';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../app_theme.dart';
import 'file_preview.dart';

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

/// Renders a single tool invocation as an expandable card with status,
/// title, input and output.
class ToolCard extends StatefulWidget {
  final String toolName;
  final ToolState state;
  final bool embedded;
  final ToolOutputFileLoader? filePreviewLoader;
  final ToolOutputFileAction? onAttachFile;
  final ToolOutputFileAction? onDownloadFile;
  const ToolCard({
    super.key,
    required this.toolName,
    required this.state,
    this.embedded = false,
    this.filePreviewLoader,
    this.onAttachFile,
    this.onDownloadFile,
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

  @override
  void initState() {
    super.initState();
    _expanded = widget.state.status == 'error';
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
        return AppTheme.success(Theme.of(context).colorScheme);
      case 'error':
        return Theme.of(context).colorScheme.error;
      default:
        return Theme.of(context).colorScheme.primary;
    }
  }

  bool get _running =>
      widget.state.status == 'pending' || widget.state.status == 'running';

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
              border: Border.all(
                color: theme.dividerColor.withValues(alpha: .4),
              ),
            ),
      child: Column(
        children: [
          Semantics(
            button: hasBody,
            expanded: hasBody ? _expanded : null,
            label: '${contract.title}, ${widget.state.status}',
            child: InkWell(
              onTap: hasBody
                  ? () => setState(() => _expanded = !_expanded)
                  : null,
              borderRadius: widget.embedded
                  ? BorderRadius.zero
                  : BorderRadius.circular(8),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 7, 8, 7),
                  child: Row(
                    children: [
                      Icon(
                        _iconFor(contract.kind),
                        size: 16,
                        color: theme.hintColor,
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
                                color: theme.colorScheme.onSurface.withValues(
                                  alpha: .9,
                                ),
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
                                    fontFamily: contract.kind == _ToolKind.shell
                                        ? 'AppMono'
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
                              fontSize: 10,
                            ),
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
                            color: theme.hintColor,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (_files.isNotEmpty)
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
              ),
            ),
        ],
      ),
    );
  }
}

class _ToolContractBody extends StatelessWidget {
  const _ToolContractBody({
    required this.contract,
    required this.state,
    required this.embedded,
  });

  final _ToolContract contract;
  final ToolState state;
  final bool embedded;

  Map<String, dynamic> get _metadata =>
      state.metadata ?? const <String, dynamic>{};

  @override
  Widget build(BuildContext context) {
    if (state.status == 'error') {
      return _ErrorOutput(
        message: state.output ?? 'Tool failed.',
        embedded: embedded,
      );
    }
    return switch (contract.kind) {
      _ToolKind.read => _readBody(),
      _ToolKind.shell => _shellBody(),
      _ToolKind.edit => _editBody(),
      _ToolKind.write => _writeBody(),
      _ToolKind.patch => _patchBody(),
      _ToolKind.todo => _todoBody(),
      _ToolKind.question => _questionBody(),
      _ToolKind.webFetch ||
      _ToolKind.webSearch ||
      _ToolKind.task => _richOutputBody(),
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
    var output = state.output ?? '';
    if (contract.kind == _ToolKind.task) {
      final match = RegExp(
        r'<task_(?:result|error)>\n?(.*?)\n?</task_(?:result|error)>',
        dotAll: true,
      ).firstMatch(output);
      if (match != null) output = match.group(1)!;
    }
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
        fontFamily: 'AppMono',
      ),
    );
    if (embedded) {
      return Container(
        key: const Key('embedded-tool-error-output'),
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(9, 5, 0, 5),
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
        borderRadius: BorderRadius.circular(7),
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
          ).textTheme.labelSmall?.copyWith(fontFamily: 'AppMono'),
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
              fontFamily: 'AppMono',
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
                  fontFamily: 'AppMono',
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            if (additions > 0)
              Text(
                '+$additions',
                style: TextStyle(
                  color: AppTheme.success(Theme.of(context).colorScheme),
                ),
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final lines = diff.split('\n');
    final visible = lines.take(500).toList();
    return Container(
      constraints: const BoxConstraints(maxHeight: 420),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerLowest,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: theme.dividerColor.withValues(alpha: .35)),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) => SingleChildScrollView(
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: IntrinsicWidth(
              child: ConstrainedBox(
                constraints: BoxConstraints(minWidth: constraints.maxWidth),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (final line in visible) _DiffPreviewLine(line: line),
                    if (lines.length > visible.length)
                      const Padding(
                        padding: EdgeInsets.all(8),
                        child: Text('… diff truncated in preview'),
                      ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
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
    final success = AppTheme.success(theme.colorScheme);
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
            fontFamily: 'AppMono',
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
            color: done ? AppTheme.success(theme.colorScheme) : theme.hintColor,
          ),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              _valueString(todo['content']) ?? '(untitled task)',
              style: theme.textTheme.bodySmall?.copyWith(
                decoration: done ? TextDecoration.lineThrough : null,
                color: done ? theme.hintColor : null,
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
    final answerText = answer is List
        ? (answer as List).join(', ')
        : _valueString(answer);
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
            answerText ?? 'No answer',
            style: theme.textTheme.bodySmall?.copyWith(
              color: answerText == null
                  ? theme.hintColor
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
                    child: Image.memory(
                      imageBytes,
                      key: const Key('tool-output-image'),
                      fit: BoxFit.contain,
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

class _Mono extends StatelessWidget {
  final String text;
  final String name;
  final int maxLines;
  const _Mono({required this.text, required this.name, this.maxLines = 100});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(8),
      constraints: BoxConstraints(maxHeight: maxLines * 18.0),
      decoration: BoxDecoration(
        color: theme.brightness == Brightness.dark
            ? Colors.black.withValues(alpha: .4)
            : Colors.black.withValues(alpha: .04),
        borderRadius: BorderRadius.circular(6),
      ),
      child: SingleChildScrollView(
        child: SmartTextPreview(
          data: FilePreviewData(
            name: name,
            text: text.length > 8000
                ? '${text.substring(0, 8000)}\n… truncated'
                : text,
          ),
        ),
      ),
    );
  }
}
