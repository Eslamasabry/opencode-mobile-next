part of '../chat_screen.dart';

// The conversation menu: rename, copy and export the transcript, notes,
// continue elsewhere, context, subagents, related sessions and changes.

mixin _ChatSessionMenuFields {
  /// A Work row's "Go to" or "Do" pick for this conversation, run once
  /// after the first history arrives (the transcript, find bar and prompts
  /// it needs are there by then).
  bool _menuActionRun = false;
}

extension _ChatSessionMenu on _ChatScreenState {
  /// "Rename conversation" (map chat-rename-session-dialog). The rename
  /// runs from inside the dialog, so a failure stays under the field with
  /// the new title kept.
  Future<void> _renameCurrentSession() async {
    final strings = _chatL10n(context);
    final current = _conn.sessionsById[widget.sessionID]?.title ?? '';
    await showKitInputDialog(
      context,
      title: strings.chatUiRenameSession,
      label: strings.chatUiTitle,
      confirmLabel: strings.chatUiRename,
      initial: current,
      dialogKey: const ValueKey('rename-session-dialog'),
      fieldKey: const ValueKey('rename-session-title'),
      validate: (value) =>
          value.trim().isEmpty ? strings.chatRenameEmpty : null,
      onSubmit: (value) async {
        try {
          await _conn.renameSession(widget.sessionID, value.trim());
          await _conn.refreshSessions();
          return null;
        } catch (error) {
          return productErrorText(error, l10n: strings);
        }
      },
    );
  }

  /// The conversation as Markdown for Copy transcript (SEC-13, G12): the
  /// person's own prompts stay as they typed them; everything else (the
  /// title, replies, reasoning, tool output, attachments' names and error
  /// text) is masked through [KitRedact] first, so a key a tool printed
  /// never reaches the clipboard.
  String _transcriptMarkdown() {
    final l10n = _chatL10n(context);
    final title = _conn.sessionsById[widget.sessionID]?.title;
    final out = StringBuffer();
    void put(String text) => out.write(KitRedact.text(text));
    put(
      '# ${title?.isNotEmpty == true ? title : l10n.chatUiOpenCodeSession}\n',
    );
    if (_olderCursor != null) {
      out.write('\n> ${l10n.historyLoadedOnly}\n');
    }
    for (final message in _visibleHistory) {
      if (message.info.id.startsWith('local-')) continue;
      final own = message.info.role == 'user';
      out.write(
        '\n## ${message.info.role == 'assistant' ? l10n.chatUiAssistant : l10n.chatUiUser}\n\n',
      );
      for (final part in message.parts) {
        if (part.type == 'text' && part.text.trim().isNotEmpty) {
          // The person's own words, verbatim; a reply is masked.
          if (own && !part.synthetic) {
            out.write('${part.text.trim()}\n\n');
          } else {
            put('${part.text.trim()}\n\n');
          }
        } else if (part.type == 'reasoning' && part.text.trim().isNotEmpty) {
          put(
            '<details><summary>${l10n.transcriptFindReasoning}</summary>\n\n${part.text.trim()}\n\n</details>\n\n',
          );
        } else if (part.type == 'file') {
          put(
            '- ${l10n.chatUiAttachment}: ${part.filename ?? part.url ?? l10n.chatUiFile}\n',
          );
        } else if (part.type == 'tool') {
          put(
            '### ${l10n.chatUiTool}: ${part.toolName ?? l10n.chatUiTool}\n\n',
          );
          final output = part.toolState.output?.trim();
          if (output?.isNotEmpty == true) {
            put('```text\n$output\n```\n\n');
          }
        }
      }
      if (message.info.errorText case final error?) {
        put('> ${l10n.chatUiError}: $error\n');
      }
    }
    return out.toString().trimRight();
  }

  /// Already masked where it is not the person's own ([_transcriptMarkdown]);
  /// copied as built so their prompts stay verbatim (SEC-13).
  Future<void> _copyTranscript() => KitCopy.copy(
    context,
    _transcriptMarkdown(),
    redact: false,
    announcement: _chatL10n(context).chatUiTranscriptCopiedAsMarkdown,
  );

  Future<void> _exportTranscript() async {
    final repository = _conn.repository;
    if (repository is SessionExportGateway &&
        (repository as SessionExportGateway).sessionExportSupported) {
      await Navigator.of(context).push(
        KitPageRoute<void>(
          builder: (_) => SessionExportScreen(
            controller: _conn,
            sessionID: widget.sessionID,
            markdown: () =>
                Uint8List.fromList(utf8.encode(_transcriptMarkdown())),
          ),
        ),
      );
      return;
    }
    final path = await FilePicker.saveFile(
      dialogTitle: _chatL10n(context).chatUiExportSessionTranscript,
      fileName:
          'opencode-${widget.sessionID.substring(0, widget.sessionID.length.clamp(0, 8))}.md',
      bytes: Uint8List.fromList(utf8.encode(_transcriptMarkdown())),
    );
    if (mounted && path != null) {
      _showComposerNote(_chatL10n(context).chatUiTranscriptSaved);
    }
  }

  /// The conversation menu (slice-P10.2): "Go to" (Changes, Timeline,
  /// Find, Subagents, Details) and "Do" (Share, Compact, Fork, Rename,
  /// Continue on computer, Open on another phone), one [KitMenuItem] list in
  /// the title bar's overflow. A Work row's menu is built by the same
  /// [sessionMenuItems] and hands its conversation acts to this screen
  /// ([ChatScreen.menuAction]). The app's other actions are commands, in
  /// the command sheet.
  List<KitMenuItem> _sessionMenu({required bool shared}) {
    if (_conn.isIsolated || _watching) return const [];
    final hasPrompt = _visibleHistory.any(
      (message) =>
          message.info.role == 'user' && !message.info.id.startsWith('local-'),
    );
    return sessionMenuItems(
      _chatL10n(context),
      SessionMenuOffer.of(
        _conn.capabilities,
        shared: shared,
        // An agent on this phone is reachable from this phone only.
        savedServer: _conn.profile != null && !_conn.isAgentBackend,
        compact: _supportsSessionCompact,
        timeline: _messages.isNotEmpty,
        hasPrompt: hasPrompt,
      ),
      shortcuts: true,
      onSelected: (action) => unawaited(_runSessionMenuAction(action)),
    );
  }

  Future<void> _runSessionMenuAction(SessionMenuAction action) async {
    if (!mounted || _conn.isIsolated) return;
    switch (action) {
      case SessionMenuAction.changes:
        if (_conn.capabilities.sessionDiff) _showDiff();
      case SessionMenuAction.timeline:
        await _openTimeline();
      case SessionMenuAction.find:
        _openFind();
      case SessionMenuAction.subagents:
        await _openRunningWork();
      case SessionMenuAction.details:
        await _showContext();
      case SessionMenuAction.share:
        if (_conn.capabilities.sessionShare) await _share();
      case SessionMenuAction.unshare:
        if (_conn.capabilities.sessionShare) await _stopSharing();
      case SessionMenuAction.compact:
        if (_supportsSessionCompact) await _compact();
      case SessionMenuAction.fork:
        await _fork();
      case SessionMenuAction.rename:
        await _renameCurrentSession();
      case SessionMenuAction.continueOnComputer:
        if (_conn.capabilities.cliSessionResume) await _continueOnComputer();
      case SessionMenuAction.continueOnPhone:
        if (_conn.profile != null) await _continueOnPhone();
    }
  }

  void _runRouteMenuAction() {
    final action = widget.menuAction;
    if (action == null || _menuActionRun || !mounted) return;
    _menuActionRun = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_runSessionMenuAction(action));
    });
  }

  /// "Note for the agent" (the command sheet's /note).
  Future<void> _openSessionNote() async {
    final location = _conn.locationRevision;
    await Navigator.of(context).push(
      KitPageRoute<void>(
        builder: (_) =>
            SessionNoteScreen(controller: _conn, sessionID: widget.sessionID),
      ),
    );
    if (mounted && _conn.locationRevision == location) _setChatState(() {});
  }

  /// F4-S1: the terminal command that resumes this session on the computer
  /// running the server. The CLI name follows the server's product
  /// generation (the only thing the flavor is used for here — copy), the
  /// availability follows [ServerCapabilities.cliSessionResume]. The sheet
  /// only offers a copy; Export lives in the conversation menu. When the
  /// server did not report the folder, the sheet offers a reload: the
  /// conversation and its details are fetched again and the sheet reopens
  /// with what the server says now.
  Future<void> _continueOnComputer() async {
    final session = _conn.sessionsById[widget.sessionID];
    final command = SessionResumeCommand.build(
      cli: _conn.serverFlavor == ServerFlavor.v2
          ? SessionResumeCli.openCode2
          : SessionResumeCli.openCode1,
      sessionID: widget.sessionID,
      directory: session?.directory ?? _conn.directory,
      workspaceID: session?.workspaceID ?? _conn.workspace,
    );
    final action = await showContinueOnComputerSheet(
      context,
      command: command,
      offerReload: true,
    );
    if (!mounted || action != continueOnComputerReload) return;
    await _conn.refreshSessions();
    await _load();
    if (!mounted) return;
    await _continueOnComputer();
  }

  /// F4-S2: the QR / link that opens this exact session in the app on
  /// another phone. Route identifiers only, built from the saved server's
  /// id and the session id; nothing is sent.
  Future<void> _continueOnPhone() async {
    final link = SessionLink.tryCreate(
      profileID: _conn.profile?.id,
      sessionID: widget.sessionID,
    );
    await showContinueOnPhoneSheet(
      context,
      link: link,
      // P3.9: "Include this server's address", offered only where the
      // server and the address coordinator allow it (gated off for now).
      address: SessionAddressOffer.of(
        context,
        connection: _conn,
        sessionID: widget.sessionID,
      ),
    );
  }

  Future<void> _showContext() async {
    await Navigator.of(context).push(
      KitPageRoute<void>(
        builder: (_) => SessionContextScreen(
          controller: _conn,
          sessionID: widget.sessionID,
          initialMessages: List.unmodifiable(_visibleHistory),
          initialHasOlder: _olderCursor != null,
        ),
      ),
    );
  }

  Future<void> _showSubagents() async {
    if (!_conn.capabilities.projectManagement) return;
    final target = await Navigator.of(context).push<Session>(
      KitPageRoute<Session>(
        builder: (_) => SessionRelationsScreen(
          controller: _conn,
          sessionID: widget.sessionID,
        ),
      ),
    );
    if (!mounted || target == null || target.id == widget.sessionID) return;
    await _openRelatedSession(target);
  }

  /// Opens the child session a Task tool card points at (its metadata
  /// carries the subagent's session id), fetching it when the list has not
  /// caught up with a freshly spawned subagent yet.
  Future<void> _openSubagentSession(
    String sessionID, {
    bool requireChild = false,
  }) async {
    if (!_conn.capabilities.projectManagement ||
        sessionID == widget.sessionID) {
      return;
    }
    final origin = widget.sessionID;
    final location = _conn.locationRevision;
    final profileID = _conn.profile?.id;
    final scope = (_conn.profile?.baseUrl, _conn.directory, _conn.workspace);
    bool current() =>
        mounted &&
        origin == widget.sessionID &&
        location == _conn.locationRevision &&
        profileID == _conn.profile?.id &&
        scope == (_conn.profile?.baseUrl, _conn.directory, _conn.workspace);
    try {
      final repository = await _requireActionRepository();
      if (!current()) return;
      final target =
          _conn.sessionsById[sessionID] ??
          await repository.getSessionDetails(sessionID);
      if (!current() || repository != _conn.repository) return;
      if (requireChild && target.parentID != origin) return;
      await _openRelatedSession(target);
    } catch (error) {
      if (current()) _showActionError(error);
    }
  }

  Future<void> _openParentSession() async {
    if (!_conn.capabilities.projectManagement) return;
    final parentID = _conn.sessionsById[widget.sessionID]?.parentID;
    if (parentID != null) await _openSubagentSession(parentID);
  }

  Future<void> _openRelatedSession(Session target) async {
    if (_conn.isIsolated || !_conn.capabilities.projectManagement) return;
    final location = _conn.locationRevision;
    final origin = widget.sessionID;
    final identity = (_conn.profile?.id, _conn.profile?.baseUrl);
    final text = _composer.text;
    final attachments = List.of(_attachments);
    bool currentDraft() =>
        mounted &&
        origin == widget.sessionID &&
        identity == (_conn.profile?.id, _conn.profile?.baseUrl) &&
        text == _composer.text &&
        listEquals(attachments, _attachments);
    if (!await _persistDraft() ||
        !mounted ||
        location != _conn.locationRevision ||
        !currentDraft()) {
      return;
    }
    if (_conn.directory != target.directory ||
        _conn.workspace != target.workspaceID) {
      await _conn.selectLocationForExistingSession(
        directory: target.directory,
        workspace: target.workspaceID,
      );
    }
    // A competing location selection can supersede the awaited operation.
    // Its completion alone does not establish the target scope.
    if (!mounted ||
        !currentDraft() ||
        _conn.directory != target.directory ||
        _conn.workspace != target.workspaceID) {
      return;
    }
    Navigator.of(context).pushReplacementNamed('/chat/${target.id}');
  }

  Future<void> _showDiff() async {
    final reconnecting = _reconnectingWords(context);
    if (!_conn.capabilities.sessionDiff) return;
    if (_conn.isIsolated) {
      final api = await _conn.prepareActionTransport();
      if (api == null) return;
      final diffs = await api.diff(widget.sessionID);
      if (!mounted) return;
      await Navigator.of(context).push<void>(
        KitPageRoute<void>(
          builder: (_) => DiffPage(diffs: diffs, allowCopy: false),
        ),
      );
      return;
    }
    final prompt = await Navigator.of(context).push<String>(
      KitPageRoute<String>(
        builder: (_) => ReviewWorkspace(
          // P6.6a: the view picked for the person is said once per server.
          profileId: _conn.profile?.id,
          handoff: _handoff, // UX-103 review handoff
          cacheKey:
              '${_conn.profile?.id}|${_conn.directory}|${widget.sessionID}',
          // OpenCode 2 has no per-conversation diff: what it answers for
          // "this chat" is the uncommitted changes, which is its own view
          // already. Two views showing the same thing is one too many.
          loadDiffs: _conn.serverFlavor == ServerFlavor.v2
              ? null
              : () async {
                  final api = await _conn.prepareActionTransport();
                  if (api == null) {
                    throw ProductException(reconnecting);
                  }
                  return api.diff(widget.sessionID);
                },
          loadWorkingTreeDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw ProductException(reconnecting);
            }
            return repository.listVcsDiffs(VcsDiffMode.workingTree);
          },
          loadBranchDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw ProductException(reconnecting);
            }
            return repository.listVcsDiffs(VcsDiffMode.branch);
          },
        ),
      ),
    );
    if (!mounted || prompt == null || prompt.trim().isEmpty) return;
    _addReviewPrompt(prompt);
  }
}
