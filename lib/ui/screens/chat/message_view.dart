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

  /// The sign-in action's words where it is not "Open providers" (an agent
  /// on this phone signs in on its own: "Sign in with Claude Code").
  final String? providersLabel;
  final VoidCallback? onContinue;
  final VoidCallback? onChooseModel;

  /// This turn got no answer: [onResendPrompt] sends its prompt again, and
  /// a "model not found" names [suggestedModel], which
  /// [onUseSuggestedModel] switches to before resending (see
  /// [_AssistantErrorRow]). On the prompt, [unanswered] marks it "Not
  /// answered".
  final VoidCallback? onResendPrompt;

  /// After a provider failure on the newest reply: a quiet way to carry the
  /// work to another agent. Never opened by itself.
  final VoidCallback? onContinueWithAnother;

  /// The newest reply was cut off by a lost connection: sends the prompt
  /// again. Null hides the action.
  final VoidCallback? onSendInterruptedAgain;

  /// What the running turn is doing, written under the reply on the row
  /// that ends what has come back so far (the composer stays idle).
  final KitTurnLive? live;

  /// With [live]: the running turn has gone quiet, and this line says why
  /// with the way forward (see [_ChatTranscript._turnStallLine]).
  final KitTurnStall? stall;

  /// This row ends a turn the person stopped from this phone: it carries
  /// the turn's stopped line even when the server reported no "aborted"
  /// error (see [_stoppedTurnRow]).
  final bool stoppedHere;

  /// On the prompt of a turn that ended with nothing at all (no words, no
  /// steps, no error): "No reply came back" with Send again.
  final VoidCallback? onSendAgainNoReply;

  /// Why no reply came, in a few words, when the agent said so on the last
  /// send ("Kilo needs you to sign in"), and the fix the notice leads with
  /// (sign in, another model) before Send again.
  final String? noReplyReason;
  final List<KitAction>? noReplyActions;
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
    this.providersLabel,
    this.onContinue,
    this.onChooseModel,
    this.onResendPrompt,
    this.onContinueWithAnother,
    this.onSendInterruptedAgain,
    this.live,
    this.stall,
    this.stoppedHere = false,
    this.onSendAgainNoReply,
    this.noReplyReason,
    this.noReplyActions,
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
    final answer = genUiAnswerEnvelope(m);
    final text =
        answer?.summary ??
        visibleParts
            .where((part) => part.type == 'text')
            .map((part) => part.text)
            .where((value) => value.trim().isNotEmpty)
            .join('\n');
    final created = m.info.time?.created;
    final noReply = onSendAgainNoReply;
    final steer = _chat(context)?._steerWaitNote(context, m.info.id);
    final chat = stoppedHere ? _chat(context) : null;
    final messages = chat?._messages;
    final latest =
        stoppedHere &&
        _inLatestTurn(
          messages,
          messages?.indexWhere((item) => item.info.id == m.info.id),
        );
    final (stoppedNote, stoppedAction) = chat == null
        ? (null, null)
        : chat._stoppedLine(latest: latest, onContinue: onContinue);
    // Stopped before anything of the reply drew: the prompt is the whole
    // turn, and it ends on the stopped line.
    final turn = KitTurn(
      segment: stoppedHere ? KitTurnSegment.whole : KitTurnSegment.first,
      phase: stoppedHere ? KitTurnPhase.stopped : KitTurnPhase.finished,
      live: live,
      stall: stall,
      stoppedNote: stoppedNote,
      stoppedAction: stoppedAction,
      latest: latest,
      highlighted: highlighted,
      prompt: KitMessage.prompt(
        bubbleKey: ValueKey('user-prompt-${m.info.id}'),
        // The bubble just sent lifts out of the composer into its place.
        liftFrom: _chat(context)?._claimLift(m.info.id),
        body: _proseMarkdown(context, text),
        attachments: [
          for (final part in visibleParts)
            if (part.type == 'file') _attachment(context, part),
        ],
        time: showTimestamp && created != null
            ? DateTime.fromMillisecondsSinceEpoch(created)
            : null,
        menu: _menuItems(),
        note: steer?.text,
        noteAction: steer?.stop,
        noteSince: steer?.since,
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
        // The turn ended and nothing came back: said plainly, with the way
        // forward (and why, when the last send was told: _noReplyNotice).
        if (noReply != null)
          _noReplyNotice(
            context,
            messageID: m.info.id,
            reason: noReplyReason,
            actions: noReplyActions ?? const [],
          ),
      ],
    );
    return stoppedHere
        ? KeyedSubtree(key: const Key('message-stopped'), child: turn)
        : turn;
  }

  /// A sent file under the prompt: a photo as its thumbnail (FC4), any
  /// other file as a read-only chip. A picture or file opens its preview, a
  /// project reference names the folder it points at.
  KitAttachment _attachment(BuildContext context, Part part) {
    final strings = _chatL10n(context);
    final name = part.filename?.trim().isNotEmpty == true
        ? part.filename!.trim()
        : strings.chatUiAttachment;
    final reference =
        part.mime == PromptAttachment.directoryReferenceMime &&
        Uri.tryParse(part.url ?? '')?.scheme == 'file';
    final image = part.mime?.startsWith('image/') ?? false;
    final photoBytes = reference ? null : _sentPhotoDataBytes(part);
    final serverPath = reference || photoBytes != null
        ? null
        : _sentPhotoServerPath(part);
    Future<FilePreviewData> loadServerFile(String path) => filePreviewLoader(
      ToolOutputFile(path: path, mimeType: part.mime, filename: name),
    );
    final KitImageSource? thumbnail = photoBytes != null
        ? KitImageSource.memory(photoBytes)
        : serverPath != null && _isDrawablePicture(part.mime)
        ? KitImageSource.provider(
            _SentFileImage(
              scope: _chat(context)?._conn.profile?.id ?? '',
              path: serverPath,
              load: () async => (await loadServerFile(serverPath)).bytes,
            ),
          )
        : null;
    final id = part.id ?? part.url ?? name;
    return KitAttachment(
      id: id,
      label: reference ? '@$name' : name,
      kind: reference
          ? KitAttachmentKind.reference
          : image || thumbnail != null
          ? KitAttachmentKind.image
          : KitAttachmentKind.file,
      detail: reference ? strings.chatUiProjectReference : null,
      thumbnail: thumbnail,
      chipKey: thumbnail == null ? null : ValueKey('sent-photo-open-$id'),
      thumbnailKey: thumbnail == null ? null : ValueKey('sent-photo-$id'),
      onOpen: reference
          ? null
          : _chat(context) != null && thumbnail != null
          ? () => unawaited(
              _chat(context)!._openPictureGallery(
                _pictureKeyOfPart(m.info.id, m.parts.indexOf(part)),
              ),
            )
          : serverPath != null
          ? () => unawaited(
              showFilePreviewSheetLoading(
                context,
                name: name,
                load: () => loadServerFile(serverPath),
              ),
            )
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
    // The server ends a step the person declined as "aborted"; that is their
    // answer to a request, not a Stop and not a failure, so the turn carries
    // no error line unless they also pressed Stop here.
    final lastTool = visibleParts
        .where((part) => part.type == 'tool')
        .lastOrNull;
    final declinedEnd =
        !stoppedHere &&
        m.info.errorKind == MessageErrorKind.aborted &&
        lastTool != null &&
        lastTool.toolState.declined;
    final raw = declinedEnd ? null : m.info.errorText;
    // A message whose parts are all non-renderable bookkeeping (`step-finish`,
    // `patch`, `snapshot`) has nothing to say.
    if (visibleParts.isEmpty &&
        metaParts.isEmpty &&
        raw == null &&
        m.info.finish != 'length' &&
        !stoppedHere) {
      // Nothing written yet: the turn's live line says so.
      return const SizedBox.shrink();
    }

    final errorKind = raw == null
        ? null
        : MessageErrorKind.refineFromText(
            m.info.errorKind ?? MessageErrorKind.unknown,
            raw,
          );
    // The conversation is still working on this turn: it has no footer yet,
    // even when its newest step is already written.
    final latest = _inLatestTurn(messages, index, chat?._waitingPromptIDs);
    // Stopped: the server said so ("aborted", or its interrupt of the newest
    // turn), or the person stopped this turn from here, with no error.
    final serverStopped = latest && (chat?._serverStoppedTurn ?? false);
    final stopped =
        errorKind == MessageErrorKind.aborted ||
        ((stoppedHere || serverStopped) && raw == null);
    final streaming = raw == null && m.info.time?.isDone == false;
    final waiting = streaming && _requestWaits(chat);
    final endsTurn =
        showActions ||
        stoppedHere ||
        (messages != null &&
            index != null &&
            index >= 0 &&
            _endsTurn(messages, index));

    // Busy by the server's word, or by this phone's until the server's
    // arrives: a reply that runs before its busy status came is not cut off.
    // A message added to the running turn (a Paseo agent that steers)
    // starts a prompt row of its own, but the steps above it are still that
    // turn's work: unfinished, they run, they are not cut off.
    final joined = streaming && chat != null && chat._sendJoinsRunningTurn;
    final busy =
        chat != null &&
        (latest || joined) &&
        (chat._conn.busySessions.contains(chat.widget.sessionID) ||
            chat._localTurnSince != null);
    // A brief link handoff keeps the current turn running. Only the shared
    // deadline exposes interruption; recovery refetches the agent's truth.
    final deferInterruption = chat?._conn.defersTurnInterruption ?? false;
    final lostCandidate =
        streaming &&
        !stopped &&
        endsTurn &&
        latest &&
        chat != null &&
        !chat._conn.isIsolated &&
        !chat._conn.isConnected &&
        !chat._conn.localServerBusy &&
        !deferInterruption;
    final looksCut =
        streaming &&
        !stopped &&
        !deferInterruption &&
        endsTurn &&
        chat != null &&
        !chat._conn.isIsolated &&
        !busy &&
        createdAt != null &&
        DateTime.now().millisecondsSinceEpoch - createdAt >
            KitMotion.escalateAfter.inMilliseconds;
    // The newest turn is not called cut off while the server, asked, still
    // runs it (a long build read "dropped" while the server ran it).
    final serverRuns =
        (lostCandidate || (looksCut && latest)) && chat._serverStillRunsTurn();
    final connectionLost = lostCandidate && !serverRuns;
    // Still trying to get back: the reply may yet finish, so no resend.
    final reconnecting = connectionLost && chat._conn.connectionLoading;
    final interrupted = connectionLost || (looksCut && !serverRuns);
    // The block being written right now: only the running turn's newest
    // reply, never one stopped, cut off or left behind by a later prompt,
    // nor while the agent waits for the person (the request card is then
    // the turn's end, though it hides the step that asks).
    final writing =
        streaming && !interrupted && !stopped && !waiting && (latest || joined);

    // The request card below says what is asked: while it shows, the step
    // that asks (the spinner on the same command) is not repeated above it.
    // Nor is the thought in hand: while the card waits, "Thinking…" above
    // it would say the agent is busy when it is waiting for the person (the
    // card is the state). The thought comes back once the card is answered.
    final shown = waiting
        ? _withoutThoughtInHand([
            for (final part in visibleParts)
              if (!(chat?._toolWaitsForYou(part) ?? false)) part,
          ])
        : visibleParts;
    final runs = _withCards(_groupAssistantParts(shown), chat);
    // While the turn's live line names the step in hand, that step is not a
    // second line above it; once the turn ends it folds into the one work
    // line (owner decision 5, 20 Oct 2026).
    // A tap on the live line shows the steps: then they are drawn, opened.
    final stepsShown = chat?._liveStepsShown ?? false;
    final liveNamesStep =
        live != null && !(interrupted || stopped || waiting) && !stepsShown;
    bool runsStep(_AssistantPartRun run) =>
        run.card == null &&
        run.parts.any(
          (part) =>
              part.type == 'tool' &&
              part.toolState.executed &&
              (part.toolState.status == 'running' ||
                  part.toolState.status == 'pending'),
        );
    final blocks = <Widget>[
      for (final stretch in _stretches(runs))
        if (stretch.length > 1 || stretch.single.grouped)
          _WorkGroup(
            key: ValueKey(
              'work:${stretch.first.parts.first.id ?? stretch.first.parts.first.callID}',
            ),
            runs: stretch,
            expansionStore: _openedForStep(stretch, chat),
            waitingForYou: waiting,
            stopped: stopped,
            hideWhileRunning: liveNamesStep,
            openWhileRunning: live != null && stepsShown,
            buildRun: (run) => _stepWidgets(run, runs, writing, chat),
          )
        else if (!(liveNamesStep && runsStep(stretch.single)))
          _runWidget(stretch.single, runs, writing, chat),
      if (raw != null && !stopped)
        _AssistantErrorRow(
          info: m.info,
          recovered: errorRecovered,
          onCompact: onCompact,
          onOpenProviders: onOpenProviders,
          providersLabel: providersLabel,
          onContinue: onContinue,
          onChooseModel: onChooseModel,
          onResend: onResendPrompt,
          onContinueWithAnother: onContinueWithAnother,
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

    // A finished turn names the files it changed, each one tap from its
    // contents, and the project's Files under them (concept 9).
    if (phase == KitTurnPhase.finished &&
        endsTurn &&
        chat != null &&
        messages != null &&
        index != null &&
        index >= 0) {
      final changed = chat._turnChangedFiles(context, messages, index);
      if (changed != null) blocks.add(changed);
    }

    // Who or what stopped it: the person's own Stop keeps its line.
    final (stoppedNote, stoppedAction) = stopped && chat != null
        ? chat._stoppedLine(latest: latest, onContinue: onContinue)
        : (null, null);

    final footer = onCopy == null && contextActions == null
        ? null
        : KitTurnFooter(
            copyText: () =>
                chat?._messageCopy(m).text ?? _replyText(visibleParts),
            copyLabel: chat?._messageCopy(m).label,
            canCopy: (chat?._messageCopy(m).text ?? _replyText(visibleParts))
                .trim()
                .isNotEmpty,
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
      // Waiting for the person: the request card says it, so the turn adds
      // no "Waiting for you…" line of its own.
      live: interrupted || stopped || raw != null || waiting ? null : live,
      stall: stall,
      stoppedNote: stoppedNote,
      stoppedAction: stoppedAction,
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

  /// [parts] without a thought still being written at their end (the
  /// unfinished turn's newest part).
  static List<Part> _withoutThoughtInHand(List<Part> parts) =>
      parts.isNotEmpty && parts.last.type == 'reasoning'
      ? parts.sublist(0, parts.length - 1)
      : parts;

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
      if (run.card == null &&
          (type == 'tool' ||
              type == 'reasoning' ||
              _isFoldedIntoWork(run.parts.first))) {
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

  Widget _runWidget(
    _AssistantPartRun run,
    List<_AssistantPartRun> all,
    bool streaming,
    _ChatScreenState? chat,
  ) => run.card != null && chat != null
      ? chat._agentCardRow(run.card!)
      : run.parts.first.type == 'v2:notice'
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
