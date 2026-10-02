import 'dart:convert';

import 'package:flutter/widgets.dart';

import '../../api/models.dart';
import '../../domain/mobile_tool_view.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../kit/chat/kit_markdown.dart';
import '../kit/chat/kit_tool_row.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_code_block.dart';
import '../kit/kit_details_fold.dart';
import '../kit/kit_diff_view.dart';
import '../kit/kit_image.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_row.dart';
import '../kit/kit_row_parts.dart';
import '../kit/kit_status_mark.dart';
import '../kit/kit_tappable.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import 'file_preview.dart';
import 'mobile_task_view.dart';
import 'product_states.dart' show productErrorText;

part 'tool_card_contract.dart';
part 'tool_card_body.dart';
part 'tool_card_parts.dart';

// The host adapter for one tool call (embedded-tool-card; chat-2). It maps a
// server `ToolState` to a KitToolRow — kind, words, status, +/− and time —
// and fills the row's body with kit parts only: KitCodeBlock for output
// (capped at 12 lines, "Open full output" opens the reader), KitDiffView for
// an edit, KitImage and KitRow for produced files, MobileTaskList for the
// plan, KitDetailsFold for a sub-agent's prompt. A sub-agent the host can
// open is the plain agent line ("Delegated to explore · Done") that matches
// the team's worker card (STANDARDS STATE-16, KIT-41).

AppLocalizations _chatL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

// The chat transcript (a `part` of chat_screen.dart) opens file diffs through
// this library, which it already imports; tool cards render diffs too.

typedef ToolOutputFileLoader =
    Future<FilePreviewData> Function(ToolOutputFile file);
typedef ToolOutputFileAction =
    Future<void> Function(ToolOutputFile file, FilePreviewData data);

/// One tool invocation as one line of the reply (a [KitToolRow]): what it
/// did, where, and how it went, opening to its note and what it produced.
/// A sub-agent the host can open is the plain agent line instead.
class ToolCard extends StatefulWidget {
  final String toolName;
  final ToolState state;
  final bool embedded;

  /// The agent's own one-line name for this step ("Setting up persistence
  /// check"), said right before the call. It becomes the row's title and the
  /// tool's name moves into the detail, so the step is one line, not two.
  final String? heading;

  /// The rest of the thought [heading] was the first line of. Shown first
  /// when the row is opened: why, then what.
  final String? note;

  /// Optional longer-lived store (e.g. session-scoped) keyed by
  /// [expansionKey], so expansion survives list recycling in a virtualized
  /// transcript instead of resetting when the item State is rebuilt.
  final Map<String, bool>? expansionStore;
  final String? expansionKey;
  final ToolOutputFileLoader? filePreviewLoader;
  final ToolOutputFileAction? onAttachFile;
  final ToolOutputFileAction? onDownloadFile;

  /// Opens the child session a `task` tool call delegated to, given its id.
  /// Null keeps the delegation a foldable step (prompt and result inline)
  /// instead of the agent line that opens its conversation.
  final ValueChanged<String>? onOpenSession;

  /// The call is blocked on the person (a permission request whose tool is
  /// this call). The row then says "Waiting for you", never "Running"
  /// (AUTO-15). A running `question` tool is always waiting for the person.
  final bool waitingForYou;

  /// Runs a shell call's command again, given the command. Null hides
  /// "Run this command again".
  final ValueChanged<String>? onRerunCommand;

  const ToolCard({
    super.key,
    required this.toolName,
    required this.state,
    this.embedded = false,
    this.heading,
    this.note,
    this.expansionStore,
    this.expansionKey,
    this.filePreviewLoader,
    this.onAttachFile,
    this.onDownloadFile,
    this.onOpenSession,
    this.waitingForYou = false,
    this.onRerunCommand,
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

  void _setExpanded(bool value) {
    setState(() {
      _expanded = value;
      if (widget.expansionKey case final key?) {
        widget.expansionStore?[key] = value;
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
    // The host may open a step from outside (the conversation menu's Tasks
    // lands on the plan open): the stored choice wins.
    if (_storedExpansion case final stored? when stored != _expanded) {
      _expanded = stored;
    }
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
        error: _chatL10n(context).chatUiTheGeneratedFileIsNotAvailableFrom,
      );
    } catch (error) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: _chatL10n(context).chatUiFileLoadFailed(productErrorText(error)),
      );
    }
  }

  Future<FilePreviewData> _loadCached(ToolOutputFile file) =>
      _previewLoads.putIfAbsent(file.identity, () => _loadPreview(file));

  void _retryPreview(ToolOutputFile file) {
    setState(() => _previewLoads[file.identity] = _loadPreview(file));
  }

  bool get _backgroundLaunch =>
      widget.toolName.trim().toLowerCase() == 'subagent' &&
      _nativeSubagentLaunched(widget.state);

  bool get _background =>
      widget.state.metadata?['background'] == true ||
      widget.state.input['background'] == true;

  /// How the call went, in the row's terms. A command that exited non-zero
  /// or timed out failed; one the server killed was stopped; a sub-agent
  /// reads its child's state; a call blocked on the person is waiting for
  /// them, never running.
  KitToolStatus _statusOf(_ToolContract contract) {
    final state = widget.state;
    if (!state.executed) return KitToolStatus.notRun;
    if (_backgroundLaunch) return KitToolStatus.background;
    final live = state.status == 'pending' || state.status == 'running';
    if (live && (widget.waitingForYou || contract.kind == _ToolKind.question)) {
      return KitToolStatus.waitingForYou;
    }
    switch (state.status) {
      case 'pending':
        return KitToolStatus.pending;
      case 'running':
        return KitToolStatus.running;
      case 'error':
        return KitToolStatus.failed;
      case 'cancelled' || 'killed':
        return KitToolStatus.stopped;
    }
    if (contract.kind == _ToolKind.task) {
      final child = _subagentState(
        state,
        nativeSubagent: contract.nativeSubagent,
      );
      if (child == 'error') return KitToolStatus.failed;
      if (child == 'running') {
        return _background ? KitToolStatus.background : KitToolStatus.running;
      }
    }
    if (contract.kind == _ToolKind.shell) {
      final shellStatus = _valueString(state.metadata?['shellStatus']);
      if (shellStatus == 'killed') return KitToolStatus.stopped;
      if (shellStatus == 'timeout') return KitToolStatus.failed;
      final exit = contract.exitCode;
      if (exit != null && exit != 0) return KitToolStatus.failed;
    }
    return KitToolStatus.done;
  }

  /// One ordered v2 content segment: capped output, an image preview, or a
  /// file row.
  Widget _segmentWidget(ToolResultSegment segment) {
    final file = segment.file;
    if (file == null) {
      return _Output(text: segment.text!, name: 'tool-output.txt');
    }
    return _fileWidget(file);
  }

  Widget _fileWidget(ToolOutputFile file) => file.isImage
      ? _ImagePreview(
          file: file,
          load: _loadCached(file),
          onRetry: () => _retryPreview(file),
          onAttach: widget.onAttachFile,
          onDownload: widget.onDownloadFile,
        )
      : _FileRow(
          file: file,
          load: () => _loadCached(file),
          onAttach: widget.onAttachFile,
          onDownload: widget.onDownloadFile,
        );

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final contract = _ToolContract.from(
      widget.toolName,
      widget.state,
      l10n: l10n,
    );
    final status = _statusOf(contract);
    final rowKey = widget.embedded ? const Key('embedded-tool-row') : null;

    // A sub-agent the host can open: the plain agent line that matches the
    // team's worker card. It opens the sub-agent's own conversation.
    final sessionId = taskChildSessionId(widget.state);
    final onOpenSession = widget.onOpenSession;
    if (contract.kind == _ToolKind.task &&
        widget.state.executed &&
        sessionId != null &&
        onOpenSession != null) {
      return KitToolRow.agent(
        rowKey: rowKey ?? const ValueKey('task-open-session'),
        title: l10n.toolCardDelegatedTo(contract.title),
        status: status,
        task: contract.subtitle,
        startedAt: status == KitToolStatus.running
            ? widget.state.startedAt
            : null,
        onOpen: () => onOpenSession(sessionId),
        openLabel: l10n.chatUiOpenSubagentSession,
      );
    }

    final hasBody =
        (widget.state.output?.isNotEmpty ?? false) ||
        (widget.state.inputJson?.isNotEmpty ?? false) ||
        widget.state.input.isNotEmpty ||
        widget.state.metadata?.isNotEmpty == true ||
        widget.state.pruned ||
        _files.isNotEmpty;

    final heading = widget.heading;
    final title = contract.kind == _ToolKind.task
        ? l10n.toolCardDelegatedTo(contract.title)
        : contract.title;
    final isShell = contract.kind == _ToolKind.shell;
    final rawPath = contract.technical ? contract.subtitle : null;
    // A command reads from its first word, on one line, cut once at the end
    // (a middle cut glued its head to its last path).
    final path = isShell && rawPath != null
        ? rawPath.trim().split('\n').first.replaceAll(RegExp(r'\s+'), ' ')
        : rawPath;
    final String? detail;
    if (path != null) {
      detail = null;
    } else if (heading != null) {
      detail = [title, ?contract.subtitle].join(' ');
    } else {
      final words = [?contract.subtitle, ...contract.details];
      detail = words.isEmpty ? null : words.join(' · ');
    }

    final interleaved = _interleavedSegments;
    final Widget row = KitToolRow(
      rowKey: rowKey,
      kind: contract.rowKind,
      title: heading ?? title,
      status: status,
      path: path,
      pathCut: isShell ? KitMonoCut.end : KitMonoCut.middle,
      detail: detail,
      added: contract.added,
      removed: contract.removed,
      // The row shows it only once finished (and reads it for a failure).
      duration: widget.state.duration,
      note: (widget.note?.isNotEmpty ?? false)
          ? KitMarkdown(
              widget.note!,
              key: const Key('step-note'),
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
              selectable: false,
            )
          : null,
      body: hasBody
          ? [
              _ToolBody(
                contract: contract,
                state: widget.state,
                embedded: widget.embedded,
                // Facts the line had no room for (it shows the path).
                facts: path == null ? const [] : contract.details,
                // Output already shown in order under the line; the body
                // keeps the input only.
                suppressOutput: interleaved != null,
                onRerunCommand: widget.onRerunCommand,
              ),
            ]
          : const [],
      expanded: _expanded,
      onExpansionChanged: _setExpanded,
    );

    // What the call produced (images, files, or text and files in order)
    // stays visible under the line, one indent level in, folded or not.
    final Widget? produced = interleaved != null
        ? KeyedSubtree(
            key: const Key('tool-interleaved-output'),
            child: _Stack(
              children: [for (final s in interleaved) _segmentWidget(s)],
            ),
          )
        : _files.isNotEmpty
        ? _Stack(children: [for (final file in _files) _fileWidget(file)])
        : null;
    if (produced == null) return row;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        row,
        Padding(
          padding: EdgeInsetsDirectional.only(
            start: tokens.space3,
            bottom: tokens.space2,
          ),
          child: produced,
        ),
      ],
    );
  }
}

/// Blocks one under another with the kit's block gap.
class _Stack extends StatelessWidget {
  const _Stack({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final gap = KitTokens.of(context).space2;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) SizedBox(height: gap),
          children[i],
        ],
      ],
    );
  }
}

/// A row's title and the detail after it, in whatever room the row really
/// has. The title keeps its natural width up to two thirds of that room and
/// the detail takes the rest, so neither can run over the other however
/// deeply the row is nested. (A cap taken from the screen's width let a long
/// title paint across the detail inside a nested group.)
class TitleWithDetail extends StatelessWidget {
  const TitleWithDetail({
    super.key,
    required this.title,
    this.detail,
    this.gap = 6,
  });

  final Widget title;
  final Widget? detail;
  final double gap;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final room = constraints.maxWidth;
      final detail = this.detail;
      if (detail == null || !room.isFinite) {
        return Align(alignment: AlignmentDirectional.centerStart, child: title);
      }
      return Row(
        children: [
          ConstrainedBox(
            constraints: BoxConstraints(maxWidth: room * .66),
            child: title,
          ),
          SizedBox(width: gap),
          Expanded(child: detail),
        ],
      );
    },
  );
}

/// The agent's reasoning for a step, shown inside the opened step: secondary
/// Markdown in the secondary tone.
class StepNote extends StatelessWidget {
  const StepNote(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => KitMarkdown(
    text,
    role: KitTextRole.secondary,
    tone: KitTextTone.secondary,
    selectable: false,
  );
}
