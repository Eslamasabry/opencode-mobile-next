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
    this.onEnableConnectorCatalogue,
  });

  final _ToolContract contract;
  final ToolState state;
  final bool embedded;
  final List<String> facts;
  final ValueChanged<String>? onRerunCommand;
  final Future<bool> Function()? onEnableConnectorCatalogue;

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
        // What was asked stays visible next to why it failed.
        if (contract.kind == _ToolKind.generic &&
            (state.input.isNotEmpty || state.inputJson?.isNotEmpty == true))
          _genericInput(),
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
      _ToolKind.webFetch || _ToolKind.webSearch => _richOutputBody(context),
      _ToolKind.plan => [_planBody(context)],
      _ToolKind.worktreeSetup => _setupBody(context),
      _ToolKind.task => _taskBody(context),
      _ToolKind.list ||
      _ToolKind.glob ||
      _ToolKind.grep => [_searchBody(context)],
      _ToolKind.lsp => [_lspBody()],
      _ToolKind.skill => [_skillBody()],
      _ToolKind.connectorSearch => _connectorSearchBody(context),
      _ToolKind.generic => _genericBody(context),
    };
  }

  /// An error in the tool's own words, as capped output (LOOK-5: no danger
  /// colour; the row already says Failed).
  Widget _error(BuildContext context, String message) {
    final words = message.replaceFirst(RegExp(r'^Error:\s*'), '').trim();
    final failed = _chatL10n(context).chatUiToolFailed;
    // With no words of its own the failure is said once, not as a caption
    // over a second sentence that says the same.
    return _Output(
      key: Key(
        embedded
            ? 'embedded-tool-error-output'
            : 'standalone-tool-error-output',
      ),
      text: words.isEmpty ? failed : words,
      name: 'tool-error.txt',
      caption: words.isEmpty ? null : failed,
    );
  }

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

  List<Widget> _richOutputBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final output = state.output ?? '';
    final asked = _valueString(state.input['prompt']);
    final address = _valueString(state.input['url']);
    final lead = [
      // The line cuts a long address in the middle; opened, it is whole.
      if (contract.kind == _ToolKind.webFetch && address != null)
        KitText(
          address,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
      if (contract.kind == _ToolKind.webFetch && asked != null)
        KitText(
          l10n.toolCardAsked(asked),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
    ];
    // A page that answered with only a status (a 404) has nothing more to
    // show than the line the step already says.
    if (output.trim().isEmpty) {
      return contract.kind == _ToolKind.webFetch &&
              state.metadata?['httpCode'] != null
          ? lead
          : [...lead, _genericInput()];
    }
    final name = switch (contract.kind) {
      _ToolKind.webFetch =>
        _valueString(state.input['format']) == 'html'
            ? 'response.html'
            : 'response.md',
      _ToolKind.webSearch => 'search-results.md',
      _ => 'agent-result.md',
    };
    return [
      ...lead,
      name.endsWith('.html')
          ? _Output(text: output, name: name)
          : _Prose(text: output, name: name),
    ];
  }

  /// The agent's plan, read as Markdown.
  Widget _planBody(BuildContext context) =>
      state.output?.trim().isNotEmpty == true
      ? _Prose(
          key: const Key('plan-text'),
          text: state.output!,
          name: 'plan.md',
        )
      : _plainOutput(context, 'plan.md');

  /// Preparing a separate copy of the project: where it is, then each setup
  /// command with how it went, then the setup's own log.
  List<Widget> _setupBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final folder = _valueString(_metadata['worktreePath']);
    final branch = _valueString(_metadata['branchName']);
    final commands = _metadata['commands'];
    String? outcome(Map command) {
      final exit = _valueNumber(command['exitCode'])?.toInt();
      if (exit != null) {
        return exit == 0
            ? l10n.toolCardExitPassed
            : l10n.toolCardExitFailed(exit);
      }
      return switch (command['status']) {
        'running' => l10n.kitToolRunning,
        'failed' => l10n.kitToolFailed,
        _ => null,
      };
    }

    return [
      if (folder != null || branch != null)
        KitKeyValue(
          rows: [
            if (folder != null)
              KitKeyValueRow(label: l10n.workspaceContextFolder, value: folder),
            if (branch != null)
              KitKeyValueRow(
                label: l10n.managedWorkspacesBranchLabel,
                value: branch,
              ),
          ],
        ),
      if (commands is List)
        for (final command in commands.whereType<Map>()) ...[
          if (_valueString(command['command']) case final text?)
            KitCodeBlock(
              text: text,
              kind: KitCodeKind.command,
              copyLabel: l10n.toolCardCopyCommand,
            ),
          if (_valueString(command['log']) != null || outcome(command) != null)
            _Output(
              text: _valueString(command['log']) ?? l10n.chatUiNoOutput,
              name: 'setup-output.txt',
              caption: outcome(command),
            ),
        ],
      if (state.output?.trim().isNotEmpty == true)
        _Output(text: state.output!, name: 'setup-log.txt'),
    ];
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
      ..._actionsTaken(context),
    ];
  }

  /// What a sub-agent did on the way, one row per step ("Search text" and
  /// what it looked for).
  List<Widget> _actionsTaken(BuildContext context) {
    final l10n = _chatL10n(context);
    final actions = _metadata['actions'];
    if (actions is! List) return const [];
    final rows = [
      for (final action in actions.whereType<Map>())
        if (_valueString(action['tool']) case final tool?)
          KitKeyValueRow(
            label: toolLabel(tool, l10n: l10n),
            value: _valueString(action['summary']) ?? '',
          ),
    ].take(KitKeyValue.maxRows).toList();
    if (rows.isEmpty) return const [];
    return [
      KitText(
        l10n.toolCardWhatItDid,
        role: KitTextRole.caption,
        tone: KitTextTone.secondary,
      ),
      KitKeyValue(rows: rows),
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

  /// What a connector search found, in words: one row per connector with
  /// where it runs and whether it is connected on this server, or the fixed
  /// line for an empty, unloaded or unavailable search. Anything else is the
  /// plain output.
  List<Widget> _connectorSearchBody(BuildContext context) {
    final l10n = _chatL10n(context);
    final result = parseConnectorSearchResult(
      value: state.outputValue,
      text: state.output,
    );
    if (result == null) return _genericBody(context);
    String runs(String runtime) => switch (runtime) {
      'hosted' => l10n.connectorCardRunsHosted,
      'npx' => l10n.connectorCardRunsNode,
      'uvx' => l10n.connectorCardRunsPython,
      'docker' => l10n.connectorCardRunsDocker,
      _ => l10n.connectorCardRunsNone,
    };
    if (result.status == ConnectorSearchStatus.ok &&
        result.matches.isNotEmpty) {
      return [
        KitKeyValue(
          key: const Key('connector-search-matches'),
          rows: [
            for (final match in result.matches.take(KitKeyValue.maxRows))
              KitKeyValueRow(
                label: KitBidi.auto(match.name),
                value: [
                  runs(match.runtime),
                  ?switch (match.connected) {
                    true => l10n.connectorCardConnected,
                    false => l10n.chatUiConnectorSearchNotConnected,
                    null => null,
                  },
                ].join(' · '),
              ),
          ],
        ),
      ];
    }
    return [
      KitText(
        switch (result.status) {
          ConnectorSearchStatus.ok => l10n.chatUiConnectorSearchEmpty,
          ConnectorSearchStatus.catalogueNotLoaded =>
            l10n.chatUiConnectorSearchNotLoaded,
          ConnectorSearchStatus.catalogueOff =>
            l10n.chatUiConnectorSearchOffBody,
          ConnectorSearchStatus.unavailable =>
            l10n.chatUiConnectorSearchUnavailable,
        },
        key: const Key('connector-search-message'),
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
      if (result.status == ConnectorSearchStatus.catalogueOff &&
          onEnableConnectorCatalogue != null)
        _CatalogueOffAction(enable: onEnableConnectorCatalogue!),
    ];
  }

  List<Widget> _genericBody(BuildContext context) {
    final hasInput =
        state.input.isNotEmpty || state.inputJson?.isNotEmpty == true;
    final value = state.outputValue;
    // Two technical folds would read the same: name them.
    final both =
        hasInput &&
        _Readable.hasNested(state.input) &&
        _Readable.hasNested(value);
    final l10n = _chatL10n(context);
    return [
      if (hasInput)
        _genericInput(foldLabel: both ? l10n.toolCardWhatWasSent : null),
      if (value is Map || value is List)
        _Readable(
          value: value,
          name: 'tool-output.json',
          foldLabel: both ? l10n.toolCardWhatCameBack : null,
        )
      else if (state.output?.trim().isNotEmpty == true)
        _Output(text: state.output!, name: 'tool-output.txt'),
    ];
  }

  Widget _genericInput({String? foldLabel}) => state.input.isNotEmpty
      ? _Readable(
          value: state.input,
          name: 'tool-input.json',
          foldLabel: foldLabel,
        )
      : _Output(text: state.inputJson ?? '', name: 'tool-input.json');

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

/// The one action in a "catalogue is off" connector search step: turn the
/// catalogue on for this profile, then say so in plain words.
class _CatalogueOffAction extends StatefulWidget {
  const _CatalogueOffAction({required this.enable});

  final Future<bool> Function() enable;

  @override
  State<_CatalogueOffAction> createState() => _CatalogueOffActionState();
}

class _CatalogueOffActionState extends State<_CatalogueOffAction> {
  bool _working = false;
  bool? _done;

  Future<void> _turnOn() async {
    if (_working) return;
    setState(() => _working = true);
    var ok = false;
    try {
      ok = await widget.enable();
    } catch (_) {}
    if (!mounted) return;
    setState(() {
      _working = false;
      _done = ok;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    if (_done == true) {
      return KitText(
        l10n.chatUiConnectorSearchTurnedOn,
        key: const Key('connector-search-turned-on'),
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      );
    }
    return _Stack(
      children: [
        if (_done == false)
          KitText(
            l10n.chatUiConnectorSearchTurnOnFailed,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitButton.secondary(
            key: const Key('connector-search-turn-on'),
            label: l10n.chatUiConnectorSearchTurnOn,
            expand: false,
            working: _working,
            onPressed: _turnOn,
          ),
        ),
      ],
    );
  }
}
