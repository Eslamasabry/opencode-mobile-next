part of '../chat_screen.dart';

// The transcript's rows besides a message: markers, notices, background
// results, OpenCode 2 variant rows, a turn's folded work and reasoning.

/// A quiet divider row for session-state changes (`model-switched`,
/// `agent-switched`, `location-switched`) and the compaction-running line:
/// hairline, centred words, hairline ([KitMessage.marker]).
class TranscriptMarker extends StatelessWidget {
  const TranscriptMarker({
    super.key,
    required this.label,
    this.icon,
    this.leading,
    this.detail,
  });

  final String label;
  final IconData? icon;

  /// Any non-null value draws the marker as working (compaction running);
  /// the kit's working mark replaces [icon].
  final Widget? leading;

  /// Retired by chat-1: the previous value is not shown (a switch says what
  /// it switched to). Kept so existing callers compile.
  final String? detail;

  @override
  Widget build(BuildContext context) =>
      KitMessage.marker(text: label, icon: icon, working: leading != null);
}

/// A background command that finished, as one line like the work lines:
/// the command as a person would say it and how it ended. The full command
/// and its output open under it. OpenCode 2 files these as notices whose
/// text is the raw `<shell …>` envelope; shown as it came, that was markup
/// and a wall of output after every background run.
class BackgroundShellResultRow extends StatefulWidget {
  const BackgroundShellResultRow({super.key, required this.result});

  final BackgroundShellResult result;

  @override
  State<BackgroundShellResultRow> createState() =>
      _BackgroundShellResultRowState();
}

class _BackgroundShellResultRowState extends State<BackgroundShellResultRow> {
  late bool _open = widget.result.outcome == BackgroundShellOutcome.failed;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final result = widget.result;
    final (status, detail) = switch (result.outcome) {
      BackgroundShellOutcome.finished => (KitToolStatus.done, null),
      BackgroundShellOutcome.stopped => (KitToolStatus.stopped, null),
      BackgroundShellOutcome.failed => (
        KitToolStatus.failed,
        result.exitCode == null ? null : strings.workExitCode(result.exitCode!),
      ),
    };
    return KeyedSubtree(
      key: const Key('background-shell-result'),
      child: KitToolRow(
        kind: KitToolKind.shell,
        title: shortCommand(result.command),
        status: status,
        detail: detail,
        expanded: _open,
        onExpansionChanged: (open) => setState(() => _open = open),
        body: [
          KitTerminalView.output(
            key: const Key('background-shell-output'),
            command: result.command,
            output: result.output,
            onOpenFull: (text) => unawaited(
              showFilePreviewSheet(
                context,
                FilePreviewData(name: 'terminal.txt', text: text),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// Something that happened during a turn and that nobody typed (context
/// added, instructions updated, a compaction), as one line
/// ([KitMessage.notice]) that opens to its text.
class TranscriptNotice extends StatefulWidget {
  const TranscriptNotice({
    super.key,
    required this.header,
    required this.icon,
    required this.text,
    this.headerMono,
    this.markdown = false,
    this.error = false,
    this.actionLabel,
    this.onAction,
  });

  /// The one thing to do about this notice, on its line ("Compact again" on
  /// a failed compaction). Absent when there is nothing to do.
  final String? actionLabel;
  final VoidCallback? onAction;

  final String header;

  /// Appended to [header] in mono, isolated left-to-right (a skill name).
  final String? headerMono;
  final IconData icon;
  final String text;

  /// Retired by chat-1: the opened text is always Markdown now.
  final bool markdown;

  /// A failure: said in words ("Failed"), never in red, and opened at first
  /// so its reason is in view.
  final bool error;

  @override
  State<TranscriptNotice> createState() => _TranscriptNoticeState();
}

class _TranscriptNoticeState extends State<TranscriptNotice> {
  late bool _open = widget.error;

  @override
  Widget build(BuildContext context) {
    final body = widget.text.trim();
    final label = widget.actionLabel;
    final onAction = widget.onAction;
    return KitMessage.notice(
      text: widget.header,
      icon: widget.icon,
      technical: widget.headerMono,
      failed: widget.error,
      detail: body.isEmpty ? null : _proseMarkdown(context, body),
      expanded: body.isEmpty ? null : _open,
      onExpansionChanged: (open) => setState(() => _open = open),
      action: label == null || onAction == null
          ? null
          : KitAction(
              key: const Key('transcript-notice-action'),
              label: label,
              onPressed: onAction,
            ),
    );
  }
}

/// Dispatches a mapper-tagged v2-only message (`v2:switch` / `v2:notice` /
/// `v2:compaction`) to its transcript treatment. Unknown tags degrade to a
/// generic notice — never crash, never drop silently.
class V2TranscriptRow extends StatelessWidget {
  const V2TranscriptRow({
    super.key,
    required this.part,
    required this.messageId,
    this.parentSessionID,
    this.knownSessions = const {},
    this.onOpenChild,
    this.onCompactAgain,
  });

  /// Retries a failed compaction. Given only for the newest failure while
  /// the conversation is idle; older ones are history.
  final VoidCallback? onCompactAgain;
  final Part part;
  final String messageId;
  final String? parentSessionID;
  final Map<String, Session> knownSessions;
  final ValueChanged<String>? onOpenChild;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final result = BackgroundAgentResult.fromPart(part);
    if (result != null) {
      return BackgroundAgentResultCard(
        key: ValueKey('background-result-$messageId'),
        result: result,
        rawText: part.text,
        onOpenChild: result.isKnownChild(parentSessionID, knownSessions)
            ? onOpenChild
            : null,
      );
    }
    if (BackgroundShellResult.fromPart(part) case final shell?) {
      return BackgroundShellResultRow(
        key: ValueKey('background-shell-$messageId'),
        result: shell,
      );
    }
    final kind = part.toolName ?? '';
    switch (part.type) {
      case 'v2:switch':
        final (icon, prefix) = switch (kind) {
          'model' => (AppIconography.processor, strings.chatUiModel),
          'agent' => (AppIconography.support, strings.chatUiAgent),
          _ => (AppIconography.folderOpen, strings.chatUiMoved),
        };
        return TranscriptMarker(
          key: ValueKey('transcript-marker-$kind-switched-$messageId'),
          icon: icon,
          label: '$prefix → ${part.text}',
        );
      case 'v2:compaction':
        return switch (kind) {
          'running' => KitMessage.marker(
            key: ValueKey('compaction-running-$messageId'),
            text: part.text.isEmpty
                ? strings.chatUiCompactingConversation
                : part.text,
            working: true,
          ),
          'failed' => TranscriptNotice(
            key: ValueKey('compaction-failed-$messageId'),
            icon: AppIconography.collapse,
            header: strings.chatUiCompactionFailed,
            // What it means for the reader, then the reason when the app
            // knows it (the server's own text is never the copy).
            text: [
              strings.chatUiCompactionFailedHint,
              if (agentErrorWords(part.text, strings) case (
                :final headline,
                humanized: true,
                hint: _,
              ))
                headline,
            ].join(' '),
            error: true,
            actionLabel: strings.chatUiCompactAgain,
            onAction: onCompactAgain,
          ),
          _ => TranscriptNotice(
            key: ValueKey('compaction-completed-$messageId'),
            icon: AppIconography.collapse,
            header: strings.chatUiContextCompacted,
            text: part.text,
          ),
        };
      default:
        // A message type this app does not know carries nothing but its
        // name ("idle"): a row saying "Server message · idle" told the
        // reader nothing and repeated after every turn.
        if (kind == 'unknown' &&
            RegExp(r'^[a-z][a-z0-9._-]*$').hasMatch(part.text.trim())) {
          return const SizedBox.shrink();
        }
        final (icon, header, mono) = switch (kind) {
          'instructions' => (
            AppIconography.note,
            strings.sessionInstructionsUpdated,
            null,
          ),
          'synthetic' => (
            AppIconography.sparkle,
            part.filename ?? strings.chatUiContextAdded,
            null,
          ),
          'system' => (
            AppIconography.settingsAdvanced,
            part.filename ?? strings.chatUiSystemUpdate,
            null,
          ),
          'skill' => (
            AppIcons.run,
            strings.chatUiSkill,
            part.filename ?? part.text,
          ),
          _ => (
            AppIconography.server,
            part.filename ??
                _noticeTitle(part.text) ??
                strings.chatUiServerMessage,
            null,
          ),
        };
        final titledByText =
            part.filename == null &&
            !const {
              'instructions',
              'synthetic',
              'system',
              'skill',
            }.contains(kind) &&
            _noticeTitle(part.text) != null;
        return TranscriptNotice(
          key: ValueKey('transcript-notice-$messageId'),
          icon: icon,
          header: header,
          headerMono: mono,
          text: kind == 'instructions'
              ? strings.sessionInstructionsApplied
              : kind == 'skill' && part.filename == null
              ? ''
              : titledByText
              ? _noticeRest(part.text)
              : part.text,
        );
    }
  }
}

/// Everything the agent did between two things it said: thoughts, tool calls
/// and runs of them, folded under one [KitWorkLine]. Closed, the line says
/// how much was done; while work is going on it names the step in hand, and
/// while a request waits for the person it says "Waiting for you" (never a
/// spinner, AUTO-15). Open, the steps hang off a rule in the order they
/// happened.
class _WorkGroup extends StatefulWidget {
  const _WorkGroup({
    super.key,
    required this.runs,
    required this.expansionStore,
    required this.buildRun,
    this.waitingForYou = false,
    this.stopped = false,
  });

  final List<_AssistantPartRun> runs;
  final Map<String, bool> expansionStore;
  final List<Widget> Function(_AssistantPartRun run) buildRun;

  /// A permission or question for this conversation waits for the person.
  final bool waitingForYou;

  /// The person stopped the turn this work belongs to.
  final bool stopped;

  @override
  State<_WorkGroup> createState() => _WorkGroupState();
}

class _WorkGroupState extends State<_WorkGroup> {
  Iterable<Part> get _tools => widget.runs
      .expand((run) => run.parts)
      .where((part) => part.type == 'tool');

  String get _storeKey {
    final first = widget.runs.first.parts.first;
    return 'work:${first.id ?? first.callID ?? first.messageID}';
  }

  /// Whether the work ended on a failure. Agents fail and retry all the
  /// time (a patch that did not apply, then one that did); a failure they got
  /// past is a detail of the steps, not the state of the work.
  bool get _endedFailed =>
      _tools.isNotEmpty && _tools.last.toolState.status == 'error';

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    _AssistantPartRun? liveRun;
    Part? livePart;
    for (final run in widget.runs.reversed) {
      for (final part in run.parts.reversed) {
        final status = part.toolState.status;
        if (part.type == 'tool' &&
            part.toolState.executed &&
            (status == 'running' || status == 'pending')) {
          liveRun = run;
          livePart = part;
          break;
        }
      }
      if (livePart != null) break;
    }
    final state = livePart != null
        ? (widget.waitingForYou
              ? KitWorkState.waitingForYou
              : KitWorkState.running)
        : _endedFailed
        ? KitWorkState.endedFailed
        : widget.stopped
        ? KitWorkState.stopped
        : KitWorkState.done;
    final now = state == KitWorkState.running
        ? liveRun!.heading ??
              runningToolTicker(
                livePart!.toolName ?? 'tool',
                livePart.toolState,
                l10n: strings,
              )
        : null;
    // Only an unrecovered failure or a produced file is worth opening
    // unasked; progress is already named on the closed line.
    final opensByDefault =
        KitWorkLine.opensByDefault(state) ||
        _tools.any((part) => part.toolState.outputFiles.isNotEmpty);
    return KeyedSubtree(
      key: const Key('work-group'),
      child: KitWorkLine(
        counts: _workCounts(_tools, steps: widget.runs.length),
        state: state,
        now: now,
        expanded: widget.expansionStore[_storeKey] ?? opensByDefault,
        onExpansionChanged: (open) =>
            setState(() => widget.expansionStore[_storeKey] = open),
        lineKey: const Key('work-group-header'),
        stepsKey: const Key('work-group-steps'),
        // A long running turn keeps its newest steps in view; the finished
        // ones fold behind "Show N earlier steps".
        tail: state == KitWorkState.running ? 3 : null,
        steps: [for (final run in widget.runs) ...widget.buildRun(run)],
      ),
    );
  }
}

class _AssistantMessagePart extends StatelessWidget {
  const _AssistantMessagePart({
    required this.part,
    required this.reasoningExpanded,
    required this.expansionStore,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
    this.streaming = false,
    this.onOpenSession,
    this.heading,
    this.note,
  });

  /// See [_AssistantPartRun.heading] and [_AssistantPartRun.note].
  final String? heading;
  final String? note;
  final Part part;
  final bool reasoningExpanded;

  /// Opens a subagent's child session from a `task` card; null hides it.
  final ValueChanged<String>? onOpenSession;

  /// True while this is the block the assistant is still writing.
  final bool streaming;
  final Map<String, bool> expansionStore;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction? onAttachFile;
  final ToolOutputFileAction onDownloadFile;

  @override
  Widget build(BuildContext context) {
    if (part.type == 'text') {
      // The reply's prose: plain body text at the prose's start edge, capped
      // at a reading width on wide windows ([KitMessage.reply]'s look).
      // Selectable on touch; desktop keeps the transcript-wide selection.
      // Inside a work line's timeline the prose is a sentence with a dot on
      // the rail; anywhere else this is the child unchanged.
      return KitStepTimeline.sentence(
        context,
        child: KeyedSubtree(
          key: const Key('assistant-text-block'),
          child: Align(
            alignment: AlignmentDirectional.topStart,
            child: ConstrainedBox(
              constraints: const BoxConstraints(
                maxWidth: KitLayout.readingWidth,
              ),
              child: MarkdownText(
                part.text,
                selectable: !desktopInteractions,
                onChoice: (option) => _insertChoice(context, option),
              ),
            ),
          ),
        ),
      );
    }
    if (part.type == 'reasoning') {
      return _Reasoning(
        text: part.text,
        expanded: reasoningExpanded,
        working: streaming,
        expansionStore: expansionStore,
        expansionKey: 'reasoning:${part.id ?? part.messageID}',
      );
    }
    if (part.type == 'tool') {
      // v2 shell messages carry their shellID in metadata; the design's key
      // list names their card `shell-card-<shellID>`.
      String? shellID;
      if (part.toolName == 'shell') {
        final raw = part.toolState.metadata?['shellID'];
        if (raw != null) shellID = raw.toString();
      }
      final chat = context.findAncestorStateOfType<_ChatScreenState>();
      return ToolCard(
        key: shellID != null
            ? ValueKey('shell-card-$shellID')
            : ValueKey(part.id ?? part.callID),
        toolName: part.toolName ?? 'tool',
        state: part.toolState,
        heading: heading,
        note: note,
        expansionStore: expansionStore,
        expansionKey: 'tool:${part.id ?? part.callID}',
        filePreviewLoader: filePreviewLoader,
        onAttachFile: onAttachFile,
        onDownloadFile: onDownloadFile,
        onOpenSession: onOpenSession,
        waitingForYou: chat?._toolWaitsForYou(part) ?? false,
        onRerunCommand: chat?._rerunShellCommand,
      );
    }
    if (part.type == 'file') {
      return Align(
        alignment: AlignmentDirectional.centerStart,
        child: KitChip(
          icon: AppIconography.attach,
          label: part.filename ?? _chatL10n(context).chatUiAttachment,
        ),
      );
    }
    return const SizedBox.shrink();
  }

  /// Routes a tapped ```choices option into the composer through the same
  /// path the empty-transcript suggestion chips use. Hosts without the chat
  /// screen (isolated previews) copy it instead (the kit says "Copied").
  static void _insertChoice(BuildContext context, String option) {
    final chat = context.findAncestorStateOfType<_ChatScreenState>();
    if (chat != null) {
      chat._insertSuggestion(option);
      return;
    }
    unawaited(KitCopy.copy(context, option));
  }
}

/// A thought, folded under its first line ([KitMessage.thought]). The
/// transcript-wide "show reasoning" setting is the default; a per-thought
/// choice outlives only the default it was made under.
class _Reasoning extends StatefulWidget {
  final String text;
  final bool expanded;
  final bool working;
  final Map<String, bool>? expansionStore;
  final String? expansionKey;
  const _Reasoning({
    required this.text,
    required this.expanded,
    this.working = false,
    this.expansionStore,
    this.expansionKey,
  });

  @override
  State<_Reasoning> createState() => _ReasoningState();
}

class _ReasoningState extends State<_Reasoning> {
  late bool _open;

  /// The thought's first line, as its title: what it was thinking about.
  static String? _preview(String text) {
    final line = text.trim().split('\n').first;
    final plain = line.replaceAll(RegExp(r'[*_`#]+'), '').trim();
    if (plain.isEmpty) return null;
    return plain.length <= 80
        ? plain
        : '${plain.substring(0, 80).trimRight()}…';
  }

  bool? get _stored => widget.expansionKey == null
      ? null
      : widget.expansionStore?[widget.expansionKey!];

  /// The transcript-wide default that was in force when the user last
  /// toggled this block by hand. A per-part choice only outlives the default
  /// it was made under: once the global toggle flips, a stale override from
  /// an off-screen block must not resist the new default.
  String? get _defaultKey =>
      widget.expansionKey == null ? null : '${widget.expansionKey}@default';

  void _persist(bool open) {
    if (widget.expansionKey case final key?) {
      widget.expansionStore?[key] = open;
      widget.expansionStore?[_defaultKey!] = widget.expanded;
    }
  }

  @override
  void initState() {
    super.initState();
    final stored = _stored;
    final storedDefault = _defaultKey == null
        ? null
        : widget.expansionStore?[_defaultKey!];
    if (stored != null &&
        storedDefault != null &&
        storedDefault != widget.expanded) {
      widget.expansionStore?.remove(widget.expansionKey);
      widget.expansionStore?.remove(_defaultKey);
      _open = widget.expanded;
    } else {
      _open = stored ?? widget.expanded;
    }
  }

  @override
  void didUpdateWidget(covariant _Reasoning oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.expanded != widget.expanded) {
      // The transcript-wide toggle sets a new default. Per-part overrides are
      // dropped rather than overwritten with the toggle's value.
      if (widget.expansionKey case final key?) {
        widget.expansionStore?.remove(key);
        widget.expansionStore?.remove(_defaultKey);
      }
      _open = widget.expanded;
    }
  }

  @override
  Widget build(BuildContext context) => KeyedSubtree(
    key: const Key('assistant-reasoning-block'),
    child: KitMessage.thought(
      thoughtKey: const Key('reasoning-toggle'),
      heading: widget.working ? null : _preview(widget.text),
      working: widget.working,
      body: _proseMarkdown(context, widget.text, role: KitTextRole.secondary),
      expanded: _open,
      onExpansionChanged: (open) => setState(() {
        _open = open;
        _persist(open);
      }),
    ),
  );
}

/// A server-authored background result remains separate from the parent's own
/// reply. The readable outcome is primary; exact protocol text is inspectable
/// behind its details fold.
class BackgroundAgentResultCard extends StatelessWidget {
  const BackgroundAgentResultCard({
    super.key,
    required this.result,
    required this.rawText,
    this.onOpenChild,
  });

  final BackgroundAgentResult result;
  final String rawText;
  final ValueChanged<String>? onOpenChild;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final (status, mark) = switch (result.state) {
      'completed' => (strings.chatUiBackgroundComplete, KitMarkState.done),
      'error' => (strings.chatUiBackgroundError, KitMarkState.failed),
      _ => (strings.chatUiBackgroundCancelled, KitMarkState.waiting),
    };
    final open = onOpenChild;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitText(
          strings.chatUiBackgroundResult,
          role: KitTextRole.caption,
          tone: KitTextTone.secondary,
        ),
        SizedBox(height: tokens.space1),
        KitText(
          result.description.isEmpty ? result.agent : result.description,
          role: KitTextRole.rowTitle,
        ),
        SizedBox(height: tokens.space1),
        Row(
          children: [
            KitStatusMark(state: mark, label: status),
            SizedBox(width: tokens.space2),
            Expanded(
              child: KitText(
                '${result.agent} · $status',
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
            ),
          ],
        ),
        SizedBox(height: tokens.space3),
        MarkdownText(
          result.body.isEmpty ? strings.chatUiNoResultText : result.body,
          selectable: false,
        ),
        if (open != null)
          Align(
            alignment: AlignmentDirectional.centerStart,
            child: KitButton.tertiary(
              icon: AppIconography.branch,
              label: strings.chatUiResultOpenChild,
              onPressed: () => open(result.childID),
            ),
          ),
        KitDetailsFold(label: strings.chatUiResultSourceDetails, text: rawText),
      ],
    );
  }
}
