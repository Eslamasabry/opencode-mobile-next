part of 'tool_card.dart';

/// Inline output is cut to this before it reaches the block, so a huge
/// result never lays out on the scrolling transcript (PERF-2); the block
/// itself shows 12 lines and "Open full output" opens all of it.
const _inlineLineCap = 200;
const _inlineCharCap = 20000;

/// Prose results (a sub-agent's answer, a fetched page, a skill) show this
/// many lines as Markdown before "Open full output".
const _proseLineCap = 12;
const _proseCharCap = 4000;

String _inline(String text) {
  var out = text;
  final lines = out.split('\n');
  if (lines.length > _inlineLineCap) {
    out = lines.take(_inlineLineCap).join('\n');
  }
  if (out.length > _inlineCharCap) out = out.substring(0, _inlineCharCap);
  return out;
}

/// Output as a capped [KitCodeBlock]: 12 lines, copy of the whole text, and
/// "Open full output" into the reader.
class _Output extends StatelessWidget {
  const _Output({
    super.key,
    required this.text,
    required this.name,
    this.kind = KitCodeKind.output,
    this.caption,
    this.fileName,
  });

  final String text;
  final String name;
  final KitCodeKind kind;
  final String? caption;
  final String? fileName;

  @override
  Widget build(BuildContext context) => KitCodeBlock(
    text: _inline(text),
    kind: kind,
    caption: caption,
    fileName: fileName,
    copyText: text,
    onOpenFull: () =>
        showFilePreviewSheet(context, FilePreviewData(name: name, text: text)),
  );
}

/// A prose result as Markdown, capped at [_proseLineCap] lines with "Open
/// full output" into the reader.
class _Prose extends StatelessWidget {
  const _Prose({super.key, required this.text, required this.name});

  final String text;
  final String name;

  @override
  Widget build(BuildContext context) {
    final lines = text.split('\n');
    var shown = lines.length > _proseLineCap
        ? lines.take(_proseLineCap).join('\n')
        : text;
    if (shown.length > _proseCharCap) {
      shown = shown.substring(0, _proseCharCap);
    }
    final capped = shown.length < text.length;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        KitMarkdown(shown, role: KitTextRole.secondary),
        if (capped)
          KitButton.tertiary(
            key: const Key('tool-body-see-all'),
            label: _chatL10n(context).kitCodeOpenFull,
            onPressed: () => showFilePreviewSheet(
              context,
              FilePreviewData(name: name, text: text),
            ),
          ),
      ],
    );
  }
}

/// What an opened call shows, built only once it is open: the facts the
/// line had no room for, then the kind's own blocks.
class _ToolBody extends StatelessWidget {
  const _ToolBody({
    required this.contract,
    required this.state,
    required this.embedded,
    this.facts = const [],
    this.suppressOutput = false,
    this.onRerunCommand,
  });

  final _ToolContract contract;
  final ToolState state;
  final bool embedded;
  final List<String> facts;
  final ValueChanged<String>? onRerunCommand;

  /// True when the ordered segment rendering already shows the output; the
  /// body then only adds the input JSON.
  final bool suppressOutput;

  Map<String, dynamic> get _metadata =>
      state.metadata ?? const <String, dynamic>{};

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    return _Stack(
      children: [
        if (facts.isNotEmpty)
          KitText(
            facts.join(' · '),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        if (state.pruned)
          KitNotice(
            key: const Key('tool-pruned'),
            message: l10n.chatUiOutputPruned,
            icon: AppIconography.cut,
            liveRegion: false,
          ),
        ..._blocks(context),
      ],
    );
  }

  List<Widget> _blocks(BuildContext context) {
    // A failed delegation still shows who was asked to do what; the error
    // lands in the result instead of replacing the whole body.
    if (contract.kind == _ToolKind.task && !suppressOutput) {
      return _taskBody(context);
    }
    if (state.status == 'error') {
      return [
        _error(context, state.output ?? _chatL10n(context).chatUiToolFailed),
      ];
    }
    if (suppressOutput) return [_genericInput()];
    return switch (contract.kind) {
      _ToolKind.read => [_readBody(context)],
      _ToolKind.shell => _shellBody(context),
      _ToolKind.edit => [_editBody(context)],
      _ToolKind.write => _writeBody(context),
      _ToolKind.patch => _patchBody(context),
      _ToolKind.todo => [_todoBody(context)],
      _ToolKind.question => _questionBody(context),
      _ToolKind.webFetch || _ToolKind.webSearch => [_richOutputBody()],
      _ToolKind.task => _taskBody(context),
      _ToolKind.list ||
      _ToolKind.glob ||
      _ToolKind.grep => [_searchBody(context)],
      _ToolKind.lsp => [_lspBody()],
      _ToolKind.skill => [_skillBody()],
      _ToolKind.generic => _genericBody(),
    };
  }

  /// An error in the tool's own words, as capped output (LOOK-5: no danger
  /// colour; the row already says Failed).
  Widget _error(BuildContext context, String message) => _Output(
    key: Key(
      embedded ? 'embedded-tool-error-output' : 'standalone-tool-error-output',
    ),
    text: message.replaceFirst(RegExp(r'^Error:\s*'), ''),
    name: 'tool-error.txt',
    caption: _chatL10n(context).chatUiToolFailed,
  );

  Widget _readBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final display = _metadata['display'];
    if (display is Map) {
      final map = Map<String, dynamic>.from(display);
      final type = _valueString(map['type']);
      final path =
          _valueString(map['path']) ??
          _valueString(state.input['filePath']) ??
          'read-output.txt';
      if (type == 'directory' && map['entries'] is List) {
        final entries = (map['entries'] as List).map((item) => '$item');
        final footer = map['truncated'] == true
            ? l10n.chatUiMoreEntries(map['totalEntries'] ?? '')
            : l10n.chatUiEntryTotal(
                map['totalEntries'] ?? (map['entries'] as List).length,
              );
        return _Output(
          text: entries.take(200).join('\n'),
          name: 'directory.txt',
          caption: '${l10n.chatUiDirectory}: $path · $footer',
        );
      }
      final text = _rawString(map['text']);
      if (type == 'file' && text?.trim().isNotEmpty == true) {
        return _Output(
          text: text!,
          name: _fileName(path),
          kind: KitCodeKind.code,
          fileName: _fileName(path),
        );
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
          .where((line) => line.trim().isNotEmpty && !line.startsWith('('));
      final path =
          _valueString(state.input['filePath']) ?? l10n.chatUiDirectory;
      return _Output(
        text: entries.join('\n'),
        name: 'directory.txt',
        caption: '${l10n.chatUiDirectory}: $path',
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
      final name = _fileName(
        _valueString(state.input['filePath']) ?? 'read-output.txt',
      );
      return _Output(
        text: text,
        name: name,
        kind: KitCodeKind.code,
        fileName: name,
      );
    }
    return _plainOutput(context, 'read-output.txt');
  }

  /// The command (copyable, never wrapped), its output with the exit code
  /// in words, and "Run this command again" when the host offers it.
  List<Widget> _shellBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final command = _valueString(state.input['command']) ?? '';
    final streamed = _rawString(_metadata['output']);
    var output = state.output?.trim().isNotEmpty == true
        ? state.output!
        : streamed ?? '';
    output = output
        .replaceAll(
          RegExp(r'\n*<shell_metadata>.*?</shell_metadata>', dotAll: true),
          '',
        )
        .trimRight();
    final shellStatus = _valueString(_metadata['shellStatus']);
    final exit = contract.exitCode;
    final outcome = [
      if (shellStatus == 'timeout')
        l10n.chatUiTimedOut
      else if (shellStatus == 'killed')
        l10n.chatUiKilled
      else if (exit == 0)
        l10n.toolCardExitPassed
      else if (exit != null)
        l10n.toolCardExitFailed(exit),
      if (_metadata['truncated'] == true) l10n.chatUiTruncated,
    ];
    final rerun = onRerunCommand;
    return [
      if (command.isNotEmpty)
        KitCodeBlock(
          key: const Key('tool-shell-command'),
          text: command,
          kind: KitCodeKind.command,
          copyLabel: l10n.toolCardCopyCommand,
        ),
      if (output.isNotEmpty || outcome.isNotEmpty)
        _Output(
          key: const Key('tool-shell-output'),
          text: output.isEmpty ? l10n.chatUiNoOutput : output,
          name: 'command-output.txt',
          caption: outcome.isEmpty ? null : outcome.join(' · '),
        ),
      if (rerun != null && command.isNotEmpty && state.executed)
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitButton.tertiary(
            key: const Key('tool-shell-rerun'),
            label: l10n.toolCardRunCommandAgain,
            icon: AppIconography.retry,
            onPressed: () => rerun(command),
          ),
        ),
    ];
  }

  Widget _editBody(BuildContext context) {
    final path = _valueString(state.input['filePath']) ?? 'changes.diff';
    final filediff = _metadata['filediff'];
    final patch = filediff is Map ? _rawString(filediff['patch']) : null;
    final diff = patch ?? _rawString(_metadata['diff']);
    if (diff?.trim().isNotEmpty == true) {
      return _Diff(files: [KitDiffFile.fromPatch(path, diff!)]);
    }
    final oldText = _rawString(state.input['oldString']);
    final newText = _rawString(state.input['newString']);
    if (oldText != null || newText != null) {
      return _Diff(
        files: [
          KitDiffFile.fromTexts(
            path,
            before: oldText ?? '',
            after: newText ?? '',
            status: KitDiffFileStatus.modified,
          ),
        ],
      );
    }
    return _plainOutput(context, 'edit-output.txt');
  }

  List<Widget> _writeBody(BuildContext context) {
    final content = _rawString(state.input['content']);
    if (content?.trim().isNotEmpty != true) {
      return [_plainOutput(context, 'write-output.txt')];
    }
    final name = _fileName(
      _valueString(state.input['filePath']) ?? 'written-file.txt',
    );
    return [
      _Output(
        text: content!,
        name: name,
        kind: KitCodeKind.code,
        fileName: name,
      ),
      if (_hasDiagnostics) _diagnostics(),
    ];
  }

  List<Widget> _patchBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final rawFiles = _metadata['files'];
    if (rawFiles is List && rawFiles.isNotEmpty) {
      final diffs = <KitDiffFile>[];
      final plain = <String>[];
      for (final raw in rawFiles.whereType<Map>()) {
        final file = Map<String, dynamic>.from(raw);
        final path =
            _valueString(file['relativePath']) ??
            _valueString(file['movePath']) ??
            _valueString(file['filePath']) ??
            l10n.chatUiChangedFile;
        if (_rawString(file['patch']) case final patch?) {
          diffs.add(KitDiffFile.fromPatch(path, patch));
        } else {
          plain.add('$path · ${_valueString(file['type']) ?? 'changed'}');
        }
      }
      return [
        if (diffs.isNotEmpty) _Diff(files: diffs),
        if (plain.isNotEmpty)
          _Output(text: plain.join('\n'), name: 'changed-files.txt'),
        if (_hasDiagnostics) _diagnostics(),
      ];
    }
    final diff =
        _rawString(_metadata['diff']) ?? _rawString(state.input['patchText']);
    return [
      diff?.trim().isNotEmpty != true
          ? _plainOutput(context, 'patch-output.txt')
          : _Diff(files: [KitDiffFile.fromPatch('changes.diff', diff!)]),
    ];
  }

  Widget _searchBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final path = _valueString(state.input['path']);
    final include = _valueString(state.input['include']);
    final caption = [
      if (path != null) '${l10n.chatUiScope}: $path',
      if (include != null) '${l10n.chatUiFiles}: $include',
    ];
    return _plainOutput(
      context,
      'search-results.txt',
      caption: caption.isEmpty ? null : caption.join(' · '),
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
    return name.endsWith('.html')
        ? _Output(text: output, name: name)
        : _Prose(text: output, name: name);
  }

  /// `task` as a step (no conversation to open): what the sub-agent was
  /// asked (description, model, the prompt folded), and what came back.
  List<Widget> _taskBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final launched = contract.nativeSubagent && _nativeSubagentLaunched(state);
    final description =
        _valueString(state.input['description']) ?? _valueString(state.title);
    final prompt = _rawString(state.input['prompt']);
    final model = _metadata['model'];
    final modelLabel = model is Map
        ? _valueString(model['modelID'])
        : _valueString(model);
    final resultText = launched
        ? ''
        : _taskResultText(
            state.output,
            nativeSubagent: contract.nativeSubagent,
          );
    final childState = _subagentState(
      state,
      nativeSubagent: contract.nativeSubagent,
    );
    final working =
        !launched &&
        state.executed &&
        state.status != 'error' &&
        (state.status == 'running' ||
            state.status == 'pending' ||
            childState == 'running');

    return [
      if (description?.isNotEmpty == true)
        KitText(
          description!,
          key: const Key('task-description'),
          role: KitTextRole.secondary,
          tone: KitTextTone.primary,
        ),
      if (modelLabel != null)
        KitText(
          '${l10n.chatUiModel} · $modelLabel',
          role: KitTextRole.caption,
          tone: KitTextTone.secondary,
        ),
      if (prompt?.trim().isNotEmpty == true)
        KitDetailsFold(
          key: const Key('task-prompt'),
          foldKey: const Key('task-prompt-toggle'),
          label: l10n.chatUiPromptFromParentAgent,
          child: KitMarkdown(prompt!.trimRight(), role: KitTextRole.secondary),
        ),
      if (launched)
        KitText(
          l10n.workStartedInBackground,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        )
      else if (state.status == 'error')
        _error(
          context,
          resultText.isEmpty ? l10n.chatUiSubagentFailed : resultText,
        )
      else if (resultText.isNotEmpty)
        _Prose(
          key: const Key('task-result'),
          text: resultText,
          name: 'agent-result.md',
        )
      else if (working)
        KitText(
          l10n.chatUiSubagentWorking,
          key: const Key('task-working'),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        )
      else
        KitText(
          l10n.chatUiNoResult,
          role: KitTextRole.secondary,
          tone: KitTextTone.tertiary,
        ),
    ];
  }

  Widget _todoBody(BuildContext context) {
    final raw = _metadata['todos'] ?? state.input['todos'];
    final view = MobileTaskView.fromTodos(raw);
    if (view != null) return MobileTaskList(view: view);
    if (state.output?.isNotEmpty == true) {
      return _plainOutput(context, 'tasks.json');
    }
    final fallback = MobileTaskView.fallback(raw);
    return fallback.isEmpty
        ? _plainOutput(context, 'tasks.json')
        : _Output(text: fallback, name: 'tasks.txt');
  }

  List<Widget> _questionBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final questions = state.input['questions'];
    final answers = _metadata['answers'];
    if (questions is! List || questions.isEmpty) {
      return [_plainOutput(context, 'question-output.txt')];
    }
    return [
      for (var index = 0; index < questions.length; index += 1)
        _QuestionAnswer(
          question: questions[index] is Map
              ? _valueString((questions[index] as Map)['question']) ??
                    l10n.chatUiQuestion
              : '${questions[index]}',
          answer: answers is List && index < answers.length
              ? answers[index]
              : null,
        ),
    ];
  }

  Widget _lspBody() {
    final result = _metadata['result'] ?? state.outputValue ?? state.output;
    return _Output(
      text: result is String
          ? result
          : const JsonEncoder.withIndent('  ').convert(result),
      name: 'language-server.json',
    );
  }

  Widget _skillBody() {
    final output = state.output;
    return output?.trim().isNotEmpty == true
        ? _Prose(text: output!, name: 'skill.md')
        : _genericInput();
  }

  List<Widget> _genericBody() {
    final hasInput =
        state.input.isNotEmpty || state.inputJson?.isNotEmpty == true;
    return [
      if (hasInput) _genericInput(),
      if (state.output?.trim().isNotEmpty == true)
        _Output(
          text: state.output!,
          name: state.outputValue is Map || state.outputValue is List
              ? 'tool-output.json'
              : 'tool-output.txt',
        ),
    ];
  }

  Widget _genericInput() {
    final input = state.input.isNotEmpty
        ? const JsonEncoder.withIndent('  ').convert(state.input)
        : state.inputJson ?? '';
    return _Output(text: input, name: 'tool-input.json');
  }

  Widget _plainOutput(BuildContext context, String name, {String? caption}) =>
      _Output(
        text: state.output?.trim().isNotEmpty == true
            ? state.output!
            : _chatL10n(context).chatUiNoOutput,
        name: name,
        caption: caption,
      );

  bool get _hasDiagnostics {
    final diagnostics = _metadata['diagnostics'];
    return diagnostics is Map && diagnostics.isNotEmpty;
  }

  Widget _diagnostics() => _Output(
    text: const JsonEncoder.withIndent(' ').convert(_metadata['diagnostics']),
    name: 'diagnostics.json',
  );
}
