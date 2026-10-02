part of '../chat_screen.dart';

// The conversation transcript, drawn from the kit's chat parts only
// (KitTurn, KitMessage, KitWorkLine, KitQueuedMessage; STANDARDS STATE-16,
// KIT-41). This file draws one message ([_MessageView]) and the error a
// reply carries; transcript_turns.dart decides what a server message is (a
// prompt, a step, a notice) and which part draws it; the parts draw.

class _MessageView extends StatelessWidget {
  final MessageWithParts m;
  final _MessageMeta meta;
  final List<Part> parts;
  final bool reasoningExpanded;
  final Map<String, bool> expansionStore;
  final bool showTimestamp;
  final bool highlighted;
  final String searchQuery;
  final TranscriptMatch? searchMatch;
  final ValueChanged<BuildContext>? onSearchExcerptContext;

  /// The message's actions as menu entries (copy, fork, read aloud, revert,
  /// delete): the prompt's long-press and right-click menu, and the reply's
  /// More, long-press and right-click menu (without Copy, which the turn's
  /// footer shows beside More).
  final List<KitMenuItem> Function()? contextActions;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction? onAttachFile;
  final ToolOutputFileAction onDownloadFile;

  /// Recovery actions for typed assistant errors: compact the session after
  /// a context overflow, open the providers screen after an auth failure,
  /// send "Continue" after an output-length cut. Null hides the button.
  final VoidCallback? onCompact;
  final VoidCallback? onOpenProviders;
  final VoidCallback? onContinue;
  final VoidCallback? onChooseModel;

  /// This turn got no answer: [onResendPrompt] sends its prompt again, and
  /// a "model not found" names [suggestedModel], which
  /// [onUseSuggestedModel] switches to before resending (see
  /// [_AssistantErrorRow]). On the prompt, [unanswered] marks it "Not
  /// answered".
  final VoidCallback? onResendPrompt;

  /// The newest reply was cut off by a lost connection: sends the prompt
  /// again. Null hides the action.
  final VoidCallback? onSendInterruptedAgain;

  /// What the running turn is doing, written under the reply on the row
  /// that ends what has come back so far (the composer stays idle).
  final KitTurnLive? live;

  /// On the prompt of a turn that ended with nothing at all (no words, no
  /// steps, no error): "No reply came back" with Send again.
  final VoidCallback? onSendAgainNoReply;
  final String? suggestedModel;
  final VoidCallback? onUseSuggestedModel;
  final bool unanswered;

  /// Opens the child session a `task` tool call delegated to (its id is the
  /// argument); null hides the action on task cards.
  final ValueChanged<String>? onOpenSession;
  const _MessageView({
    super.key,
    required this.m,
    required this.meta,
    required this.parts,
    required this.reasoningExpanded,
    required this.expansionStore,
    required this.showTimestamp,
    this.highlighted = false,
    this.searchQuery = '',
    this.searchMatch,
    this.onSearchExcerptContext,
    this.contextActions,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
    this.onCompact,
    this.onOpenProviders,
    this.onContinue,
    this.onChooseModel,
    this.onResendPrompt,
    this.onSendInterruptedAgain,
    this.live,
    this.onSendAgainNoReply,
    this.suggestedModel,
    this.onUseSuggestedModel,
    this.unanswered = false,
    this.onOpenSession,
    this.queued = false,
    this.showActions = true,
    this.errorRecovered = false,
    this.onCopy,
  });

  /// The host's copy of the turn's reply. The footer copies through the
  /// kit (KitCopy); a non-null value says there is a reply to copy.
  final VoidCallback? onCopy;

  /// See [_AssistantErrorRow.recovered].
  final bool errorRecovered;

  /// Whether this message ends its turn and carries the turn's footer. A
  /// reply is usually several messages; only the one that ends it carries
  /// the footer, so it appears once per turn. Long-press and right-click
  /// stay on every message.
  final bool showActions;

  /// True for a user prompt the server has accepted but not started: it
  /// runs after the current turn (OpenCode 1 queues mid-turn sends).
  final bool queued;

  _ChatScreenState? _chat(BuildContext context) =>
      context.findAncestorStateOfType<_ChatScreenState>();

  List<KitMenuItem> _menuItems({bool withCopy = true}) => [
    for (final item in contextActions?.call() ?? const <KitMenuItem>[])
      if (withCopy || item.key != const ValueKey('message-menu-copy')) item,
  ];

  @override
  Widget build(BuildContext context) {
    final visibleParts = parts
        .where((p) => p.isRenderable || _isFoldedIntoWork(p))
        .toList();
    if (m.info.role == 'user') {
      final instructions = _teamInstructions(context, visibleParts);
      if (instructions != null) return _frame(context, instructions);
      return _frame(context, _promptTurn(context, visibleParts));
    }
    return _frame(context, _replyTurn(context, visibleParts));
  }

  /// Where a find match shows. In place when the words are on screen as
  /// prose: the find mark highlights them and the bar holds the count.
  /// Only a match the transcript does not show where it is (a thought, tool
  /// data, a file name, folded passing words, or far down a very long
  /// message) gets an excerpt above the turn, which says where it is found
  /// and never repeats the count (review board: find bar).
  bool _matchNeedsExcerpt(TranscriptMatch match) {
    if (match.kind != 'text') return true;
    if (match.start > _inPlaceMatchReach) return true;
    final index = match.partIndex;
    return index >= 0 &&
        index < m.parts.length &&
        _isFoldedIntoWork(m.parts[index]);
  }

  /// How far into a message (in characters) a match can sit and still be
  /// in view when the find brings the message's start on screen.
  static const _inPlaceMatchReach = 480;

  /// The find-in-conversation excerpt above the turn when the match is not
  /// visible in place, and the highlight of the words it matched.
  Widget _frame(BuildContext context, Widget turn) {
    final match = searchMatch;
    if (match == null) {
      return TranscriptHighlight(query: searchQuery, child: turn);
    }
    if (!_matchNeedsExcerpt(match)) {
      return TranscriptHighlight(
        query: searchQuery,
        child: Builder(
          builder: (context) {
            onSearchExcerptContext?.call(context);
            return turn;
          },
        ),
      );
    }
    return TranscriptHighlight(
      query: searchQuery,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Builder(
            builder: (context) {
              onSearchExcerptContext?.call(context);
              return TranscriptMatchExcerpt(match: match);
            },
          ),
          turn,
        ],
      ),
    );
  }

  /// Watching a team agent's session: a long user-role message was not
  /// typed by the person (the team's start-up instructions, mail, nudges),
  /// so it is one folded line that opens to its text, never a bubble.
  Widget? _teamInstructions(BuildContext context, List<Part> visibleParts) {
    if (_chat(context)?._watching != true) return null;
    var text = visibleParts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .where((value) => value.trim().isNotEmpty)
        .join('\n')
        .trim();
    final stamped = text.startsWith('[');
    if (!stamped && text.length < 240) return null;
    // Drop the "[phone] Test/gastown.refinery • 2026-09-29T09:08:07" line:
    // the time is shown in the app's own format.
    final newline = text.indexOf('\n');
    if (stamped && newline > 0 && text.substring(0, newline).contains('•')) {
      text = text.substring(newline + 1).trim();
    }
    final created = m.info.time?.created;
    final words = text.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length;
    return TranscriptNotice(
      key: ValueKey('team-instructions-${m.info.id}'),
      header: _chatL10n(context).chatWatchTeamInstructions(
        words,
        created == null ? '' : _fmtSessionTime(created, context),
      ),
      icon: AppIconography.agent,
      text: text,
    );
  }

  Widget _promptTurn(BuildContext context, List<Part> visibleParts) {
    final text = visibleParts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .where((value) => value.trim().isNotEmpty)
        .join('\n');
    final created = m.info.time?.created;
    final noReply = onSendAgainNoReply;
    return KitTurn(
      segment: KitTurnSegment.first,
      phase: KitTurnPhase.finished,
      live: live,
      highlighted: highlighted,
      prompt: KitMessage.prompt(
        bubbleKey: ValueKey('user-prompt-${m.info.id}'),
        body: _proseMarkdown(context, text),
        attachments: [
          for (final part in visibleParts)
            if (part.type == 'file') _attachment(context, part),
        ],
        time: showTimestamp && created != null
            ? DateTime.fromMillisecondsSinceEpoch(created)
            : null,
        menu: _menuItems(),
      ),
      blocks: [
        if (queued)
          Align(
            key: ValueKey('queued-message-${m.info.id}'),
            alignment: AlignmentDirectional.centerEnd,
            child: KitText(
              _chatL10n(context).chatUiQueuedRunsAfterThisTurn,
              role: KitTextRole.caption,
              tone: KitTextTone.tertiary,
            ),
          ),
        // The prompt got no answer: said on the prompt itself, so the
        // failure and the prompt it concerns read together (the error and
        // its Send again sit right under it).
        if (unanswered)
          Align(
            key: ValueKey('prompt-not-answered-${m.info.id}'),
            alignment: AlignmentDirectional.centerEnd,
            child: KitText(
              _chatL10n(context).chatUiPromptNotAnswered,
              role: KitTextRole.caption,
              tone: KitTextTone.secondary,
            ),
          ),
        // The turn ended and nothing came back, and the server gave no
        // reason: said plainly, with the way forward.
        if (noReply != null)
          KitNotice(
            key: ValueKey('prompt-no-reply-${m.info.id}'),
            message: _chatL10n(context).chatNoReplyCameBack,
            actions: [
              KitAction(
                key: const Key('no-reply-send-again'),
                label: _chatL10n(context).chatUiSendPromptAgain,
                onPressed: noReply,
              ),
            ],
          ),
      ],
    );
  }

  /// A sent file as a read-only chip under the prompt; a picture or file
  /// opens its preview, a project reference names the folder it points at.
  KitAttachment _attachment(BuildContext context, Part part) {
    final strings = _chatL10n(context);
    final name = part.filename?.trim().isNotEmpty == true
        ? part.filename!.trim()
        : strings.chatUiAttachment;
    final reference =
        part.mime == PromptAttachment.directoryReferenceMime &&
        Uri.tryParse(part.url ?? '')?.scheme == 'file';
    final image = part.mime?.startsWith('image/') ?? false;
    return KitAttachment(
      id: part.id ?? part.url ?? name,
      label: reference ? '@$name' : name,
      kind: reference
          ? KitAttachmentKind.reference
          : image
          ? KitAttachmentKind.image
          : KitAttachmentKind.file,
      detail: reference ? strings.chatUiProjectReference : null,
      onOpen: reference
          ? null
          : () => unawaited(
              showFilePreviewSheet(
                context,
                FilePreviewData.fromDataUrl(
                  name: name,
                  mimeType: part.mime,
                  url: part.url,
                ),
              ),
            ),
    );
  }

  Widget _replyTurn(BuildContext context, List<Part> visibleParts) {
    final strings = _chatL10n(context);
    final chat = _chat(context);
    final messages = chat?._messages;
    final index = messages?.indexWhere((item) => item.info.id == m.info.id);
    final createdAt = m.info.time?.created;

    // Usage rides on the same preference as timestamps: both are detail a
    // reader opts into, and the model label alone marks a switch.
    final metaParts = <String>[
      if (showTimestamp && createdAt != null)
        _fmtSessionTime(createdAt, context),
      ?meta.modelLabel,
      if (showTimestamp) ...[
        if (meta.turnTokens case final tokens?)
          strings.chatUiTokenCount(_fmtTokens(tokens)),
        if (meta.turnCost case final cost?) _fmtCost(cost),
      ],
    ];
    final raw = m.info.errorText;
    // A message whose parts are all non-renderable bookkeeping (`step-finish`,
    // `patch`, `snapshot`) has nothing to say.
    if (visibleParts.isEmpty &&
        metaParts.isEmpty &&
        raw == null &&
        m.info.finish != 'length') {
      // Nothing written yet: the composer's edge already says so.
      return const SizedBox.shrink();
    }

    final errorKind = raw == null
        ? null
        : MessageErrorKind.refineFromText(
            m.info.errorKind ?? MessageErrorKind.unknown,
            raw,
          );
    final stopped = errorKind == MessageErrorKind.aborted;
    final streaming = raw == null && m.info.time?.isDone == false;
    final waiting = streaming && _requestWaits(chat);
    final endsTurn =
        showActions ||
        (messages != null &&
            index != null &&
            index >= 0 &&
            _endsTurn(messages, index));

    // The conversation is still working on this turn: it has no footer yet,
    // even when its newest step is already written.
    final latest = _inLatestTurn(messages, index);
    // Busy by the server's word, or by this phone's until the server's
    // arrives: a reply that runs before its busy status came is not cut off.
    final busy =
        chat != null &&
        latest &&
        (chat._conn.busySessions.contains(chat.widget.sessionID) ||
            chat._localTurnSince != null);
    // The connection was lost mid-reply (or the server went quiet for good):
    // the turn stops "working" and says so. A refetch on reconnect brings
    // the finished reply back and this line goes with it.
    final connectionLost =
        streaming &&
        endsTurn &&
        latest &&
        chat != null &&
        !chat._conn.isIsolated &&
        !chat._conn.isConnected;
    // Still trying to get back: the reply may yet finish, so no resend.
    final reconnecting = connectionLost && chat._conn.connectionLoading;
    final interrupted =
        connectionLost ||
        (streaming &&
            endsTurn &&
            chat != null &&
            !chat._conn.isIsolated &&
            !busy &&
            createdAt != null &&
            DateTime.now().millisecondsSinceEpoch - createdAt >
                KitMotion.escalateAfter.inMilliseconds);
    final working = streaming && !interrupted;

    final runs = _groupAssistantParts(visibleParts);
    final blocks = <Widget>[
      for (final stretch in _stretches(runs))
        if (stretch.length > 1 || stretch.single.grouped)
          _WorkGroup(
            key: ValueKey(
              'work:${stretch.first.parts.first.id ?? stretch.first.parts.first.callID}',
            ),
            runs: stretch,
            expansionStore: expansionStore,
            waitingForYou: waiting,
            stopped: stopped,
            buildRun: (run) => _stepWidgets(run, runs, working, chat),
          )
        else
          _runWidget(stretch.single, runs, working, chat),
      if (raw != null && !stopped)
        _AssistantErrorRow(
          info: m.info,
          recovered: errorRecovered,
          onCompact: onCompact,
          onOpenProviders: onOpenProviders,
          onContinue: onContinue,
          onChooseModel: onChooseModel,
          onResend: onResendPrompt,
          suggestion: suggestedModel,
          onUseSuggestion: onUseSuggestedModel,
        ),
      if (m.info.finish == 'length' &&
          m.info.errorKind != MessageErrorKind.outputLength)
        KitMessage.notice(
          noticeKey: const Key('message-length-footer'),
          icon: AppIconography.textShort,
          text: strings.chatUiAnswerWasCutOffByTheLength,
        ),
    ];

    final phase = stopped
        ? KitTurnPhase.stopped
        : raw != null && !errorRecovered
        ? KitTurnPhase.failed
        : interrupted
        ? KitTurnPhase.interrupted
        : waiting
        ? KitTurnPhase.waitingForYou
        : streaming || (busy && endsTurn)
        ? KitTurnPhase.running
        : KitTurnPhase.finished;

    final footer = onCopy == null && contextActions == null
        ? null
        : KitTurnFooter(
            copyText: () =>
                chat?._messageCopy(m).text ?? _replyText(visibleParts),
            copyLabel: chat?._messageCopy(m).label,
            meta: metaParts.isEmpty ? null : metaParts.join(' · '),
            menu: _menuItems(withCopy: false),
          );

    final Widget turn = KitTurn(
      segment: endsTurn ? KitTurnSegment.last : KitTurnSegment.middle,
      phase: phase,
      since: createdAt == null
          ? null
          : DateTime.fromMillisecondsSinceEpoch(createdAt),
      blocks: blocks,
      footer: footer,
      // The live line stands for the running turn's end; an interrupted or
      // ended turn says so in its own line instead.
      live: interrupted || stopped || raw != null ? null : live,
      reconnecting: reconnecting,
      interruptedAction:
          connectionLost && !reconnecting && onSendInterruptedAgain != null
          ? KitAction(
              key: const Key('interrupted-send-again'),
              label: strings.chatUiSendPromptAgain,
              onPressed: onSendInterruptedAgain,
            )
          : null,
      latest: latest,
      highlighted: highlighted,
      footerKey: ValueKey('message-meta-${m.info.id}'),
      copyKey: ValueKey('message-copy-${m.info.id}'),
      moreKey: ValueKey('message-actions-${m.info.id}'),
    );
    return stopped
        ? KeyedSubtree(key: const Key('message-stopped'), child: turn)
        : turn;
  }

  /// A permission or question for this conversation waits for the person:
  /// the running work then says "Waiting for you", never that it is running
  /// (AUTO-15).
  static bool _requestWaits(_ChatScreenState? chat) {
    if (chat == null) return false;
    final session = chat.widget.sessionID;
    return chat._conn.permissionsForSession(session).isNotEmpty ||
        chat._conn.questionForSession(session) != null;
  }

  /// Whether no prompt follows this message: its footer then shows the
  /// turn's model, time and usage words (older turns keep a quiet footer).
  static bool _inLatestTurn(List<MessageWithParts>? messages, int? index) {
    if (messages == null || index == null || index < 0) return true;
    for (var next = index + 1; next < messages.length; next += 1) {
      if (_isPrompt(messages[next])) return false;
    }
    return true;
  }

  static String _replyText(List<Part> parts) => parts
      .where((part) => part.type == 'text' && part.text.trim().isNotEmpty)
      .map((part) => part.text)
      .join('\n\n');

  /// Splits a message's runs into what is said and what is done: each text
  /// block (or attachment) stands alone, and each unbroken stretch of
  /// thoughts and tool calls between them is one list.
  static List<List<_AssistantPartRun>> _stretches(
    List<_AssistantPartRun> runs,
  ) {
    final stretches = <List<_AssistantPartRun>>[];
    var work = <_AssistantPartRun>[];
    for (final run in runs) {
      final type = run.parts.first.type;
      if (type == 'tool' ||
          type == 'reasoning' ||
          _isFoldedIntoWork(run.parts.first)) {
        work.add(run);
        continue;
      }
      if (work.isNotEmpty) stretches.add(work);
      work = [];
      stretches.add([run]);
    }
    if (work.isNotEmpty) stretches.add(work);
    return stretches;
  }

  /// A run inside a work line: a run of tool calls is one step per call (the
  /// agent's heading and note on the first), anything else is its own step.
  List<Widget> _stepWidgets(
    _AssistantPartRun run,
    List<_AssistantPartRun> all,
    bool streaming,
    _ChatScreenState? chat,
  ) {
    if (!run.grouped) return [_runWidget(run, all, streaming, chat)];
    return [
      for (final (index, part) in run.parts.indexed)
        ToolCard(
          key: ValueKey(part.id ?? part.callID),
          toolName: part.toolName ?? 'tool',
          state: part.toolState,
          embedded: true,
          heading: index == 0 ? run.heading : null,
          note: index == 0 ? run.note : null,
          expansionStore: expansionStore,
          expansionKey: 'tool:${part.id ?? part.callID}',
          filePreviewLoader: filePreviewLoader,
          onAttachFile: onAttachFile,
          onDownloadFile: onDownloadFile,
          onOpenSession: onOpenSession,
          waitingForYou: chat?._toolWaitsForYou(part) ?? false,
          onRerunCommand: chat?._rerunShellCommand,
        ),
    ];
  }

  Widget _runWidget(
    _AssistantPartRun run,
    List<_AssistantPartRun> all,
    bool streaming,
    _ChatScreenState? chat,
  ) => run.parts.first.type == 'v2:notice'
      ? V2TranscriptRow(
          part: run.parts.first,
          messageId: run.parts.first.messageID ?? run.parts.first.id ?? '',
        )
      : run.grouped
      ? Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: _stepWidgets(run, all, streaming, chat),
        )
      : _AssistantMessagePart(
          part: run.parts.single,
          heading: run.heading,
          note: run.note,
          reasoningExpanded: reasoningExpanded,
          expansionStore: expansionStore,
          filePreviewLoader: filePreviewLoader,
          onAttachFile: onAttachFile,
          onDownloadFile: onDownloadFile,
          onOpenSession: onOpenSession,
          streaming: streaming && identical(run, all.last),
        );

  static String _fmtTokens(int n) {
    if (n >= 1000000) return '${(n / 1000000).toStringAsFixed(1)}M';
    if (n >= 1000) return '${(n / 1000).toStringAsFixed(1)}k';
    return '$n';
  }

  /// Three decimals is the finest a reader can act on; anything smaller is
  /// "essentially free" rather than a string of zeros.
  static String _fmtCost(double cost) =>
      cost < .001 ? '< \$0.001' : '\$${cost.toStringAsFixed(3)}';
}

/// The assistant error, keyed on the server's typed error: overflow, auth,
/// length and model errors get the one action that fixes them, named for
/// what it acts on; anything else says what happened. Every error offers
/// its exact server words ("Error details"), which can be copied (P8.3).
/// A stop is not an error: the turn says "You stopped this reply."
class _AssistantErrorRow extends StatelessWidget {
  const _AssistantErrorRow({
    required this.info,
    this.recovered = false,
    this.onCompact,
    this.onOpenProviders,
    this.onContinue,
    this.onChooseModel,
    this.onResend,
    this.suggestion,
    this.onUseSuggestion,
  });

  final MessageInfo info;

  /// True when the turn went on after this error (the server retried, or the
  /// agent took another step). It is then a line in the story, not an alarm.
  final bool recovered;
  final VoidCallback? onCompact;
  final VoidCallback? onOpenProviders;
  final VoidCallback? onContinue;
  final VoidCallback? onChooseModel;

  /// Sends the turn's prompt again as it was: given only for the newest
  /// turn when it got no answer and the prompt is words alone.
  final VoidCallback? onResend;

  /// The model the server suggested in a "model not found" error, by name,
  /// when this server has it; [onUseSuggestion] switches to it and resends.
  final String? suggestion;
  final VoidCallback? onUseSuggestion;

  @override
  Widget build(BuildContext context) {
    final strings = _chatL10n(context);
    final raw = info.errorText ?? '';
    final words = agentErrorWords(raw, strings);
    final kind = MessageErrorKind.refineFromText(
      info.errorKind ?? MessageErrorKind.unknown,
      raw,
    );
    // Words, never the server's text: that is under Error details.
    final text = _plainErrorHeadline(words, kind, strings);
    final useSuggestion = onUseSuggestion;
    final named = suggestion;
    final (String id, KitAction? fix) = switch (kind) {
      MessageErrorKind.modelNotFound => (
        'model-not-found',
        // The server named a model it has: one tap switches to it and sends
        // the prompt again (review board: prompt error).
        useSuggestion != null && named != null && !recovered
            ? KitAction(
                key: const Key('error-action-use-suggestion'),
                label: strings.chatUiUseModelAndResend(named),
                onPressed: useSuggestion,
              )
            : onChooseModel == null
            ? null
            : KitAction(
                key: const Key('error-action-choose-model'),
                label: strings.chatUiChooseModel,
                onPressed: onChooseModel,
              ),
      ),
      MessageErrorKind.contextOverflow => (
        'context-overflow',
        onCompact == null
            ? null
            : KitAction(
                key: const Key('error-action-compact'),
                label: strings.chatUiCompactSession,
                onPressed: onCompact,
              ),
      ),
      MessageErrorKind.providerAuth => (
        'provider-auth',
        onOpenProviders == null
            ? null
            : KitAction(
                key: const Key('error-action-providers'),
                label: strings.chatUiOpenProviders,
                onPressed: onOpenProviders,
              ),
      ),
      MessageErrorKind.outputLength => (
        'output-length',
        onContinue == null
            ? null
            : KitAction(
                key: const Key('error-action-continue'),
                label: strings.messageViewContinueReply,
                onPressed: onContinue,
              ),
      ),
      // Nothing to fix first: the same prompt can simply go again.
      _ => (
        'generic',
        onResend == null || recovered || kind == MessageErrorKind.contentFilter
            ? null
            : KitAction(
                key: const Key('error-action-resend'),
                label: strings.chatUiSendPromptAgain,
                onPressed: onResend,
              ),
      ),
    };
    final hint =
        kind == MessageErrorKind.contentFilter ||
            kind == MessageErrorKind.unknown
        ? (recovered ? strings.agentErrorRecovered : words.hint)
        : null;
    return KeyedSubtree(
      key: Key('error-card-$id'),
      child: KitNotice(
        // The kit's one error glyph for every kind (map Fix: no per-kind
        // icon in the error tone).
        tone: recovered ? AppStatusTone.neutral : AppStatusTone.failure,
        message: text,
        liveRegion: false,
        notes: [?hint],
        actions: [
          ?fix,
          KitAction(
            key: const Key('error-action-details'),
            label: strings.chatUiErrorDetails,
            onPressed: () => unawaited(
              _showChatErrorDetails(
                context,
                title: strings.chatUiErrorDetails,
                text: raw.trim().isEmpty ? text : raw,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
