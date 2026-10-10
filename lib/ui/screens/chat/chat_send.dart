part of '../chat_screen.dart';

// Sending a prompt: the optimistic bubble, the send itself, @agent
// mentions, and typed !shell lines and /commands.

class _PendingSend {
  /// Authored before dispatch, never inferred from matching message contents.
  final String? dispatchedMessageID;
  final String localID;
  final String text;
  final List<PromptAttachment> attachments;
  final int createdAt;
  String? canonicalID;
  bool requestComplete = false;

  _PendingSend({
    this.dispatchedMessageID,
    required this.localID,
    required this.text,
    required this.attachments,
    required this.createdAt,
  });
}

bool _mentionBoundaryBefore(String value) =>
    RegExp(r'''[\s\(\[\{"']''').hasMatch(value);

bool _mentionBoundaryAfter(String value) =>
    RegExp(r'''[\s\.,!\?;:\)\}\]"']''').hasMatch(value);

({int start, int end, String query})? _activeAgentQuery(
  TextEditingValue value,
) {
  final selection = value.selection;
  if (!selection.isValid || !selection.isCollapsed) return null;
  final cursor = selection.baseOffset;
  if (cursor < 1 || cursor > value.text.length) return null;
  final at = value.text.lastIndexOf('@', cursor - 1);
  if (at < 0) return null;
  if (at > 0 && !_mentionBoundaryBefore(value.text.substring(at - 1, at))) {
    return null;
  }
  final query = value.text.substring(at + 1, cursor);
  if (query.contains(RegExp(r'\s'))) return null;
  return (start: at, end: cursor, query: query);
}

List<PromptAgentMention> _promptAgentMentions(
  String text,
  Iterable<CatalogAgent> agents,
) {
  final visible =
      agents
          .where((agent) => !agent.hidden && agent.mode == 'subagent')
          .map((agent) => agent.id)
          .where((name) => name.isNotEmpty)
          .toSet()
          .toList()
        ..sort((a, b) => b.length.compareTo(a.length));
  final mentions = <PromptAgentMention>[];
  for (final name in visible) {
    final value = '@$name';
    var offset = 0;
    while (offset < text.length) {
      final start = text.indexOf(value, offset);
      if (start < 0) break;
      final end = start + value.length;
      final validBefore =
          start == 0 ||
          _mentionBoundaryBefore(text.substring(start - 1, start));
      final validAfter =
          end == text.length ||
          _mentionBoundaryAfter(text.substring(end, end + 1));
      if (validBefore && validAfter) {
        mentions.add(
          PromptAgentMention(name: name, value: value, start: start, end: end),
        );
      }
      offset = end;
    }
  }
  mentions.sort((a, b) => a.start.compareTo(b.start));
  return mentions;
}

mixin _ChatSendFields {
  /// What Send does while a turn is running, on servers that support the
  /// inbox. Steer matches the server default; the visible delivery control
  /// in the composer both shows and sets this, and the Send long-press
  /// shortcut updates it too so the label never lies (UX-P0-04).
  PromptDelivery _delivery = PromptDelivery.queue;

  final List<_PendingSend> _pendingSends = [];
  bool _sending = false;

  /// When the newest turn started running as far as this phone knows: set
  /// the moment a send is accepted here, before the server says the
  /// conversation is busy (that can take a while on a slow server), and
  /// cleared when the server says it is idle again, the send fails, or
  /// the person stops it. The server's own busy state takes over as soon as
  /// it arrives; this only covers the time before it.
  DateTime? _localTurnSince;

  /// The prompt whose reply the person stopped: its turn ended on purpose,
  /// so it never reads as "No reply came back".
  String? _stoppedPromptID;

  /// The last send failed before the server took it; its text is back in
  /// the composer. Shown on the status line until dismissed or sent again.
  Object? _sendError;
}

extension _ChatSend on _ChatScreenState {
  /// Clears the composer and shows [pending] as the person's own bubble with
  /// the turn's live line, before the server says it is busy.
  void _showOptimisticBubble(_PendingSend pending) {
    _composer.clear();
    // On a phone the keyboard would keep three quarters of the screen from
    // the reply the person just asked for; tapping the field brings it back.
    // A desktop keeps focus for the next line.
    if (desktopInteractions) {
      _focus.requestFocus();
    } else {
      _focus.unfocus();
    }
    _setChatState(() {
      _localTurnSince = DateTime.fromMillisecondsSinceEpoch(pending.createdAt);
      _stoppedPromptID = null;
      _promptError = null;
      _sendError = null;
      _pendingSends.add(pending);
      _messages.add(
        MessageWithParts(
          info: MessageInfo(
            id: pending.localID,
            sessionID: widget.sessionID,
            role: 'user',
            time: MsgTime(created: pending.createdAt),
          ),
          parts: [
            if (pending.text.isNotEmpty) Part(type: 'text', text: pending.text),
            for (final attachment in pending.attachments)
              Part(
                type: 'file',
                mime: attachment.mime,
                filename: attachment.filename,
                url: attachment.url,
              ),
          ],
        ),
      );
      _attachments.clear();
    });
  }

  /// Takes a bubble shown before the transport was ready back out and gives
  /// the person their text and attachments again.
  void _rollbackOptimisticBubble(_PendingSend pending) {
    _setChatState(() {
      _localTurnSince = null;
      _pendingSends.remove(pending);
      _messages.removeWhere((message) => message.info.id == pending.localID);
      _attachments.insertAll(0, pending.attachments);
    });
    final currentText = _composer.text;
    if (pending.text.isNotEmpty && currentText.trim() != pending.text) {
      _composer.text = currentText.isEmpty
          ? pending.text
          : '${pending.text}\n$currentText';
      _composer.selection = TextSelection.collapsed(
        offset: _composer.text.length,
      );
    }
    _persistDraft();
  }

  /// [delivery] rides only on OpenCode 2 sends made while a turn runs. When
  /// it is omitted the composer's current delivery choice applies; the
  /// long-press shortcut passes an explicit steer or queue.
  Future<void> _send({PromptDelivery? delivery}) async {
    final tapMicros = PerfTrace.nowMicros;
    final strings = _chatL10n(context);
    final conversationSend = _voiceConversation;
    final voiceEpoch = _voiceEpoch.value;
    final voiceScope = _speechScopeNow;
    bool voiceSendCurrent() =>
        !conversationSend ||
        (mounted &&
            _voiceConversation &&
            voiceEpoch == _voiceEpoch.value &&
            voiceScope == _speechScopeNow &&
            (ModalRoute.of(context)?.isCurrent ?? true));
    if (_voiceConversation && !_conversationCanSend) {
      _showComposerNote(_conversationPauseCopy);
      return;
    }
    if (_voiceConversation && _composer.text.trimLeft().startsWith('/')) {
      _showComposerNote(strings.voiceConversationCommandsOnly);
      return;
    }
    delivery ??= _activeDelivery;
    await _voice?.cancel();
    if (!mounted || !voiceSendCurrent()) return;
    if (conversationSend && !_conversationCanSend) return;
    // UX-103 review handoff: the command grammar is matched against the text
    // the *user* typed, before any staged reference is folded in. Folding
    // first appended a multi-line reference block that `_typedChatCommand`
    // could never match, so a composer holding `/new` plus a staged reference
    // silently sent the command as a chat message.
    final hasStagedReferences = _handoff.references.isNotEmpty;
    if (_sending ||
        _promptShelfBusy ||
        (_composer.text.trim().isEmpty &&
            _attachments.isEmpty &&
            !hasStagedReferences)) {
      return;
    }
    // The send tick belongs to the composer's Send (KitComposer), which
    // already gave it for this tap: one send, one haptic.
    if (!_conn.isIsolated &&
        _attachments.isEmpty &&
        _composer.text.trimLeft().startsWith('/') &&
        _serverCommands == null) {
      await _loadServerCommands();
      if (!mounted) return;
    }
    final typedCommand = _conn.isIsolated
        ? null
        : _typedChatCommand(_composer.text.trim());
    if (_attachments.isEmpty && typedCommand != null) {
      // A command is not a prompt: a server command's arguments feed its own
      // template and a mobile command takes none, so references cannot ride
      // along. They stay staged for the next prompt rather than being
      // rewritten into arguments the command never asked for — and the user
      // is told, so nothing looks lost.
      if (hasStagedReferences) _noteReferencesKeptForNextPrompt();
      await _submitTypedCommand(typedCommand);
      return;
    }
    if (!_conn.isIsolated && _attachments.isEmpty) {
      final typed = _composer.text.trim();
      // "!command" runs in this conversation's shell; its output joins the
      // transcript as the shell step's card (P10.1).
      if (_shellLine.firstMatch(typed) case final shell?) {
        if (hasStagedReferences) _noteReferencesKeptForNextPrompt();
        await _submitShellLine(shell.group(1)!.trim());
        return;
      }
      // An agent that does not share its commands never gets "/compact" as
      // a plain message pretending to be a command: say so, send nothing.
      final slash = _conn.capabilities.slashCommands
          ? null
          : _slashWord.firstMatch(typed);
      if (slash != null) {
        _showComposerNote(
          strings.commandSheetAgentCommandNotSent(
            '/${slash.group(1)}',
            _agentWord(strings),
          ),
        );
        return;
      }
    }
    if (_conn.supportsStagedRevert &&
        (_conn.sessionsById[widget.sessionID]?.reverted == true ||
            _conn.sessionRevertSaving(widget.sessionID))) {
      _showComposerNote(strings.revertResolveBeforeSending);
      return;
    }
    _applyStagedReferences(); // UX-103 review handoff
    if (_composer.text.trim().isEmpty && _attachments.isEmpty) return;
    if (!_supportsPromptAttachments && _attachments.isNotEmpty) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return;
    }
    if (_conn.status != StreamStatus.connected) {
      // Offline compose: the draft queues instead of failing, and flushes
      // through the same send path when the connection returns.
      if (!_supportsOfflinePromptQueue) {
        final persisted = await _persistDraft();
        if (mounted) {
          _showComposerNote(
            persisted && _draftSaveFailure == null
                ? strings.codexOfflineDraftSaved
                : strings.codexReconnectBeforeSending,
          );
        }
        return;
      }
      final draftText = _composer.text.trim();
      final draftAttachments = List<PromptAttachment>.from(_attachments);
      final draftMentions = _supportsPromptAgentMentions
          ? _promptAgentMentions(draftText, _subagents)
          : const <PromptAgentMention>[];
      if (await _queueDraft(draftText, draftAttachments, draftMentions)) {
        if (!mounted) return;
        _setChatState(() => _attachments.clear());
        _composer.clear();
        _persistDraft();
        _focus.requestFocus();
      }
      return;
    }
    _setChatState(() => _sending = true);
    // The bubble is on screen before the transport is awaited: after a phone
    // wake `prepareActionTransport` can wait out the whole connect timeout,
    // and the person must not stare at a composer that looks unsent. Steering
    // (takes back earlier items first) and voice conversation (needs the
    // gateway for the exact message id) keep the transport-first order.
    final bubbleFirst = delivery != PromptDelivery.steer && !conversationSend;
    _PendingSend? early;
    if (bubbleFirst) {
      final now = DateTime.now();
      early = _PendingSend(
        localID:
            'local-${now.millisecondsSinceEpoch}-${now.microsecondsSinceEpoch}',
        text: _composer.text.trim(),
        attachments: List<PromptAttachment>.from(_attachments),
        createdAt: now.millisecondsSinceEpoch,
      );
      _showOptimisticBubble(early);
      PerfTrace.recordSince(
        'chat.send_to_bubble',
        tapMicros,
        attrs: const {'order': 'bubble_first'},
      );
    }
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (!voiceSendCurrent() || (conversationSend && !_conversationCanSend)) {
      _setChatState(() => _sending = false);
      return;
    }
    if (actionApi == null) {
      if (early != null) _rollbackOptimisticBubble(early);
      _setChatState(() => _sending = false);
      final detail = _conn.connectionError;
      _showActionError(
        detail == null || detail.isEmpty
            ? (_conn.isAgentBackend
                  ? _reconnectingShortlyWords(context)
                  : strings.chatUiOpenCodeIsReconnectingTryAgainWhenThe)
            : detail,
      );
      return;
    }
    // Several steering messages in a row are one thought, typed in pieces.
    // Left as separate inbox items they reach the agent as separate
    // interruptions; taken back and sent together they are one.
    final earlier = delivery == PromptDelivery.steer
        ? await _takeBackWaitingSteers()
        : const <String>[];
    if (!mounted) return;
    final text =
        early?.text ??
        [
          ...earlier,
          _composer.text.trim(),
        ].where((piece) => piece.isNotEmpty).join('\n\n');
    if (text.isEmpty && _attachments.isEmpty && early == null) {
      _setChatState(() => _sending = false);
      return;
    }
    final attachments =
        early?.attachments ?? List<PromptAttachment>.from(_attachments);
    final agentMentions = _supportsPromptAgentMentions
        ? _promptAgentMentions(text, _subagents)
        : const <PromptAgentMention>[];
    var selection = _conn.selectionForSession(widget.sessionID);
    final selectionProfileID = _conn.profile?.id;
    var promptStarted = false;
    final pending =
        early ??
        _PendingSend(
          dispatchedMessageID:
              conversationSend &&
                  _voiceSpeakReplies &&
                  actionApi.capabilities.clientPromptMessageID &&
                  actionApi is CorrelatedPromptGateway
              ? (actionApi as CorrelatedPromptGateway).createPromptMessageID()
              : null,
          localID:
              'local-${DateTime.now().millisecondsSinceEpoch}-${DateTime.now().microsecondsSinceEpoch}',
          text: text,
          attachments: attachments,
          createdAt: DateTime.now().millisecondsSinceEpoch,
        );
    if (early == null) {
      _showOptimisticBubble(pending);
      PerfTrace.recordSince(
        'chat.send_to_bubble',
        tapMicros,
        attrs: const {'order': 'transport_first'},
      );
    }
    _persistDraft();
    try {
      await _conn.waitForSessionSelection(
        widget.sessionID,
        expectedApi: actionApi,
      );
      if (!voiceSendCurrent() || (conversationSend && !_conversationCanSend)) {
        throw StateError(strings.chatUiVoiceConversationWasInterrupted);
      }
      selection = _conn.selectionForSession(widget.sessionID);
      promptStarted = true;
      if (conversationSend && voiceSendCurrent()) {
        _setChatState(() => _watchVoiceReply(pending));
      }
      _conn.noteLocalTurn(widget.sessionID);
      final exactMessageID = pending.dispatchedMessageID;
      if (exactMessageID != null && actionApi is CorrelatedPromptGateway) {
        await (actionApi as CorrelatedPromptGateway).promptWithMessageID(
          widget.sessionID,
          messageID: exactMessageID,
          text: text,
          model: selection.model,
          agent: selection.agent?.isNotEmpty == true ? selection.agent : null,
          variant: selection.variant.isEmpty ? null : selection.variant,
          attachments: attachments,
          agentMentions: agentMentions,
          delivery: delivery,
        );
      } else {
        await actionApi.promptAsync(
          widget.sessionID,
          text: text,
          model: selection.model,
          agent: selection.agent?.isNotEmpty == true ? selection.agent : null,
          variant: selection.variant.isEmpty ? null : selection.variant,
          attachments: attachments,
          agentMentions: agentMentions,
          delivery: delivery,
        );
      }
      if (!conversationSend) {
        unawaited(_rememberSentPrompt(selectionProfileID ?? '', text));
      }
      PerfTrace.recordSince('chat.send_to_ack', tapMicros);
      if (!mounted) return;
      _setChatState(() {
        _sending = false;
        pending.requestComplete = true;
        if (pending.canonicalID != null) _pendingSends.remove(pending);
      });
      _checkVoiceReply();
      if (!conversationSend && _composer.text.isEmpty) _restoreHistoryDraft();
    } catch (e) {
      if (!mounted) return;
      _setChatState(() {
        _sending = false;
        _localTurnSince = null;
        if (identical(_voiceReplyWatch?.pending, pending)) {
          _voiceReplyWatch = null;
          _voiceReplyState = _VoiceReplyState.reviewNeeded;
        }
        _pendingSends.remove(pending);
        _messages.removeWhere(
          (message) =>
              message.info.id == pending.localID ||
              message.info.id == pending.canonicalID,
        );
      });
      // A transport-level failure (no HTTP response) means the server became
      // unreachable mid-send: queue the draft rather than erroring.
      if (!voiceSendCurrent()) return;
      if (!conversationSend &&
          promptStarted &&
          _supportsOfflinePromptQueue &&
          e is ApiException &&
          e.statusCode == null) {
        if (await _queueDraft(
          text,
          attachments,
          agentMentions,
          selection: selection,
          profileID: selectionProfileID,
        )) {
          return;
        }
      }
      if (!mounted) return;
      if (e is ApiException && e.errorTag == 'SessionRevertPending') {
        unawaited(_conn.ensureSession(widget.sessionID));
      }
      _setChatState(() => _attachments.insertAll(0, attachments));
      final currentText = _composer.text;
      if (text.isNotEmpty && currentText.trim() != text) {
        _composer.text = currentText.isEmpty ? text : '$text\n$currentText';
        _composer.selection = TextSelection.collapsed(
          offset: _composer.text.length,
        );
      }
      // Said on the status line, where it stays until dismissed or sent
      // again, not in a snackbar that leaves while the person reads it.
      _setChatState(() => _sendError = e);
    }
  }

  void _insertAgentMention(CatalogAgent agent) {
    if (!_supportsPromptAgentMentions) return;
    final current = _composer.value;
    final query = _activeAgentQuery(current);
    final selection = current.selection;
    final fallback = selection.isValid
        ? selection.start.clamp(0, current.text.length)
        : current.text.length;
    final start = query?.start ?? fallback;
    final end =
        query?.end ??
        (selection.isValid
            ? selection.end.clamp(start, current.text.length)
            : start);
    final needsLeadingSpace =
        query == null &&
        start > 0 &&
        !RegExp(r'\s').hasMatch(current.text.substring(start - 1, start));
    final needsTrailingSpace =
        end == current.text.length ||
        !RegExp(r'\s').hasMatch(current.text.substring(end, end + 1));
    final replacement =
        '${needsLeadingSpace ? ' ' : ''}@${agent.id}${needsTrailingSpace ? ' ' : ''}';
    final nextText = current.text.replaceRange(start, end, replacement);
    _composer.value = TextEditingValue(
      text: nextText,
      selection: TextSelection.collapsed(offset: start + replacement.length),
    );
    _focus.requestFocus();
  }

  ({_ChatCommand command, String arguments})? _typedChatCommand(String text) {
    final match = RegExp(r'^/(\S+)(?:\s+(.*))?$').firstMatch(text);
    if (match == null) return null;
    final name = match.group(1)!.toLowerCase();
    for (final command in _chatCommands) {
      if (command.matches(name)) {
        return (command: command, arguments: match.group(2)?.trim() ?? '');
      }
    }
    return null;
  }

  static final _shellLine = RegExp(r'^!([^\s!][\s\S]*)$');
  static final _slashWord = RegExp(r'^/(\S+)');

  /// The agent's name for copy: "Codex", "Claude Code", else the server's.
  String _agentWord(AppLocalizations strings) =>
      commandSheetAgentName(_conn) ??
      _conn.profile?.name ??
      strings.commandSheetAgentFallback;

  /// A composer line that starts with "!": the rest runs as a shell
  /// command in this conversation, the same call as Run shell command. A
  /// server with no shell for the conversation says so and sends nothing.
  Future<void> _submitShellLine(String command) async {
    final strings = _chatL10n(context);
    if (!_conn.capabilities.conversationShellOn) {
      _showComposerNote(
        strings.commandSheetShellNotSent('!$command', _agentWord(strings)),
      );
      return;
    }
    final original = _composer.text;
    _setChatState(() => _sending = true);
    try {
      await _runShellCommand(command);
      if (!mounted) return;
      _composer.clear();
      _focus.requestFocus();
    } catch (error) {
      if (!mounted) return;
      _composer.text = original;
      _composer.selection = TextSelection.collapsed(offset: original.length);
      _showActionError(error);
    } finally {
      if (mounted) _setChatState(() => _sending = false);
    }
  }

  Future<void> _submitTypedCommand(
    ({_ChatCommand command, String arguments}) typed,
  ) async {
    final command = typed.command;
    if (!command.enabled) {
      _showActionError(
        _chatL10n(context).chatUiCommandUnavailable(command.slash),
      );
      return;
    }
    if (command.serverCommand == null) {
      _composer.clear();
      await _runMobileCommand(command.action! as _ChatCommandAction);
      return;
    }
    if (_conn.supportsStagedRevert &&
        (_conn.sessionsById[widget.sessionID]?.reverted == true ||
            _conn.sessionRevertSaving(widget.sessionID))) {
      _showComposerNote(_chatL10n(context).revertResolveBeforeSending);
      return;
    }
    _setChatState(() => _sending = true);
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (actionApi == null) {
      _setChatState(() => _sending = false);
      _showActionError(
        _conn.connectionError ?? _reconnectingShortlyWords(context),
      );
      return;
    }
    final original = _composer.text;
    try {
      await _conn.waitForSessionSelection(
        widget.sessionID,
        expectedApi: actionApi,
      );
      await actionApi.slashCommand(
        widget.sessionID,
        command.serverCommand!.name,
        typed.arguments,
        model: _conn.modelForSession(widget.sessionID),
        variant: _conn.variantForSession(widget.sessionID).isEmpty
            ? null
            : _conn.variantForSession(widget.sessionID),
      );
      if (!mounted) return;
      _composer.clear();
      _focus.requestFocus();
      _setChatState(() => _sending = false);
    } catch (error) {
      if (!mounted) return;
      if (error is ApiException && error.errorTag == 'SessionRevertPending') {
        unawaited(_conn.ensureSession(widget.sessionID));
      }
      _composer.text = original;
      _composer.selection = TextSelection.collapsed(offset: original.length);
      _setChatState(() => _sending = false);
      _showActionError(error);
    }
  }
}
