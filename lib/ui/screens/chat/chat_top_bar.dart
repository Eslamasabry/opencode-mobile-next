part of '../chat_screen.dart';

// The chat's top bar and the display choices it offers (reasoning,
// timestamps, context window).

extension _ChatTopBar on _ChatScreenState {
  /// The slash commands' display toggles: the transcript changes at once,
  /// and the bar says which way and offers the way back.
  Future<void> _toggleReasoningDisplay() async {
    final expanded = await _setReasoningDisplay(
      !_conn.transcriptReasoningExpanded,
    );
    if (!mounted || expanded == null) return;
    showKitUndo(
      _undoHost,
      message: expanded
          ? _chatL10n(context).chatUiReasoningExpandedInTheTranscript
          : _chatL10n(context).chatUiLongReasoningCollapsedInTheTranscript,
      onUndo: () => _setReasoningDisplay(!expanded),
    );
  }

  /// Folds every step, reasoning line and work fold that is open.
  void _collapseAllSteps() {
    _setChatState(_transcriptExpansion.collapseAll);
  }

  Future<bool?> _setReasoningDisplay(bool expanded) async {
    if (!mounted) return null;
    // The transcript-wide choice is the new default for every reasoning
    // block, so per-part overrides are dropped instead of being silently
    // rewritten with the toggle's value. Rewriting them meant one flip
    // erased the session's per-part choices, and an off-screen block kept
    // resisting the toggle because its stale override outlived it.
    _setChatState(
      () => _transcriptExpansion.removeWhere(
        (key, _) => key.startsWith('reasoning:'),
      ),
    );
    await _conn.setTranscriptReasoningExpanded(expanded);
    return mounted ? expanded : null;
  }

  Future<void> _toggleTimestampDisplay() async {
    final visible = !_conn.transcriptTimestampsVisible;
    await _conn.setTranscriptTimestampsVisible(visible);
    if (!mounted) return;
    showKitUndo(
      _undoHost,
      message: visible
          ? _chatL10n(context).chatUiMessageTimestampsShown
          : _chatL10n(context).chatUiMessageTimestampsHidden,
      onUndo: () => _conn.setTranscriptTimestampsVisible(!visible),
    );
  }

  /// The newest assistant reply's tokens against its model's window, with
  /// the per-reply history (null when either side is unknown).
  ContextReading? _contextReading() =>
      contextReadingFor(_visibleHistory.toList(), _conn.catalog);

  /// Fraction of the model's context window consumed: from the newest
  /// assistant message that reported token usage and the catalog's limit for
  /// its exact model; else, for an agent that reports its own usage (Paseo),
  /// the session record's. Null when unknown.
  double? _contextWindowUsage() =>
      _contextReading()?.fraction ??
      _conn.sessionsById[widget.sessionID]?.contextUse?.fraction;

  /// Whether this conversation is being compacted right now: OpenCode 1
  /// stamps the session while it compacts; OpenCode 2 and Paseo put a
  /// running compaction notice in the transcript.
  bool _contextCompacting() {
    if (_conn.sessionsById[widget.sessionID]?.compactingSince != null) {
      return true;
    }
    for (var i = _messages.length - 1; i >= 0; i -= 1) {
      final part = v2VariantPart(_messages[i]);
      if (part?.type == 'v2:compaction') return part?.toolName == 'running';
    }
    return false;
  }

  /// The ring appears once the window is half used; below that the bar
  /// stays quiet.
  static const double _contextRingFrom = 0.5;

  /// The ring in the bar, absent while the use is unknown. It also leaves
  /// the value behind for the chats list.
  Widget? _contextRing(double? used) {
    if (_conn.isIsolated || _watching || used == null) return null;
    _conn.contextUsageMemory?.remember(widget.sessionID, used);
    return KitContextRing(
      key: const Key('chat-context-ring'),
      used: used,
      compacting: _contextCompacting(),
      openDetails: _showContextDetails,
    );
  }

  /// The details sheet: the numbers, the per-reply chart, and Compact now
  /// when this server can compact on request.
  Future<void> _showContextDetails() async {
    final reading = _contextReading();
    final own = _conn.sessionsById[widget.sessionID]?.contextUse;
    final tokens = reading?.tokens ?? own?.tokens;
    final limit = reading?.limit ?? own?.limit;
    if (tokens == null || limit == null || limit <= 0) return;
    final l10n = _chatL10n(context);
    final compact = _supportsSessionCompact && !_contextCompacting();
    final modelName =
        reading?.modelName ??
        _conn.sessionsById[widget.sessionID]?.model?.split('/').last ??
        '';
    await showKitSheet<void>(
      context,
      sheetKey: const Key('context-details-sheet'),
      title: l10n.kitContextSheetTitle,
      primary: compact
          ? KitAction(
              key: const Key('context-compact-now'),
              label: l10n.kitContextSheetCompactNow,
              icon: AppIconography.collapse,
              // The sheet closes first; compacting asks its own question.
              onPressed: () => unawaited(
                Navigator.of(context).maybePop().then((_) {
                  if (mounted) return _compact();
                }),
              ),
            )
          : null,
      body: (_) => KitContextSheet(
        tokens: tokens,
        limit: limit,
        modelName: modelName,
        compacting: _contextCompacting(),
        bars: [
          for (final reply in reading?.replies ?? const <ContextReply>[])
            KitContextBar(
              fraction: reply.fraction,
              compactedBefore: reply.afterCompaction,
            ),
        ],
      ),
    );
  }

  Widget _transcriptSelectionArea({required Widget child}) => _conn.isIsolated
      ? child
      : KitSelectable(mode: KitSelectMode.finePointer, child: child);

  Widget _composerDropTarget({required Widget child}) => _conn.isIsolated
      ? child
      : !_supportsPromptAttachments
      ? child
      : DesktopFileDropTarget(onDrop: _handleDroppedFiles, child: child);

  /// The conversation's bar (kit-v2.md §1.18): the title, the server when
  /// more than one could be meant, and the few actions, most urgent first.
  /// On a phone the first shows and the rest wait in the overflow; on a PC
  /// they carry their words (§8.2).
  String _topBarTitle(Session? session, AppLocalizations l10n) {
    final own = presentedSessionTitle(
      session,
      fallback: l10n.commandDestination,
    );
    final watch = widget.watch;
    if (watch == null) return own;
    watch.sessionTitle?.value = own;
    return watch.title?.call() ?? own;
  }

  /// The project this conversation lives in, as a quiet line under the title
  /// (with the server after it when several are connected) and a compact menu of what can be done there. Absent for a temporary folder
  /// (never shown as a project), a watched team worker and the demo.
  void _gitChanged() {
    if (mounted) _setChatState(() {});
  }

  /// The Git chip: the project's branch (or "Git" until one is known),
  /// opening the Git sheet.
  KitTopBarBranch _gitChip(String? directory, AppLocalizations l10n) {
    _git.bind(profile: _conn.profile, gateway: _conn.api, directory: directory);
    final branch = _git.branchName;
    return KitTopBarBranch(
      chipKey: const ValueKey('chat-git-chip'),
      label: branch == null ? l10n.gitSheetTitle : KitBidi.auto(branch),
      semanticsLabel: branch == null
          ? l10n.gitSheetChipSemanticsPlain
          : l10n.gitSheetChipSemantics(branch),
      onTap: _openGitSheet,
    );
  }

  Future<void> _openGitSheet() async {
    final page = _git.page;
    if (page == null) return;
    await showGitSheet(
      context,
      page: page,
      account: _git.account,
      hostName: _conn.profile?.name,
      // "Ask the agent to fix conflicts" goes through the normal send path;
      // "Write it for me" fills the commit message with the agent's own
      // words for its last turn.
      onAskAgent: _conn.isIsolated || _watching
          ? null
          : (prompt) => unawaited(_sendPhrase(prompt)),
      summary: _turnSummary(),
    );
  }

  /// The agent's own one-line account of its latest turn ("Added a combo
  /// counter and screen shake."): the first sentence of its last reply's
  /// words, at most a commit subject's length. Null when it said nothing.
  String? _turnSummary() {
    for (var i = _messages.length - 1; i >= 0; i--) {
      final message = _messages[i];
      if (message.info.role != 'assistant') continue;
      final words = _ChatScreenState._messageText(message).trim();
      if (words.isEmpty) continue;
      final line = words
          .split(RegExp(r'\n'))
          .map((l) => l.replaceAll(RegExp(r'[#*`>_]'), '').trim())
          .firstWhere((l) => l.isNotEmpty, orElse: () => '');
      final end = RegExp(r'[.!?](\s|$)').firstMatch(line);
      var first = end == null ? line : line.substring(0, end.start + 1);
      if (first.characters.length > 72) {
        final cut = first.characters.take(72).toString();
        final space = cut.lastIndexOf(' ');
        first = '${space > 40 ? cut.substring(0, space) : cut}\u2026';
      }
      return first.isEmpty ? null : first;
    }
    return null;
  }

  KitTopBarScope? _projectScope(
    Session? session,
    AppLocalizations l10n, {
    String? serverName,
  }) {
    final origin = _handoffOrigin();
    // No project to name: the line is the server (when several are
    // connected) and where this work came from.
    KitTopBarScope? plain() => origin == null
        ? null
        : KitTopBarScope(
            label: serverName ?? '',
            semanticsLabel: serverName ?? '',
            origin: origin,
          );
    if (_watching || _conn.isIsolated) return plain();
    final directory = session?.directory ?? _conn.directory;
    if (isTemporaryProjectDirectory(directory)) return plain();
    final parts = directory!.split('/').where((part) => part.isNotEmpty);
    final name = parts.isEmpty ? directory : parts.last;
    final capabilities = _conn.capabilities;
    // The project's Files page (concept 9, owner 2026-10-20): one tap from
    // the chip to the project's files and tools, and from there to another
    // project. A server without project tools keeps the small menu of what
    // it does offer.
    if (ProjectHub.isAvailable(capabilities)) {
      return KitTopBarScope(
        chipKey: const ValueKey('chat-project-chip'),
        label: KitBidi.auto(name),
        suffix: serverName,
        semanticsLabel: serverName == null
            ? l10n.chatProjectChipFilesSemantics(name)
            : l10n.chatProjectServerChipFilesSemantics(name, serverName),
        icon: AppIconography.folderOpen,
        onTap: () => unawaited(_openProjectFilesPage(directory)),
        branch: _gitChip(directory, l10n),
        origin: origin,
      );
    }
    // (A terminal alone already makes the Files page available.)
    final items = <KitMenuItem>[
      if (capabilities.sessionDiff)
        KitMenuItem(
          key: const ValueKey('chat-project-changes'),
          label: l10n.readerUiChanges,
          icon: AppIconography.review,
          onSelected: () => unawaited(_showDiff()),
        ),
    ];
    if (items.isEmpty) return plain();
    return KitTopBarScope(
      chipKey: const ValueKey('chat-project-chip'),
      label: KitBidi.auto(name),
      suffix: serverName,
      semanticsLabel: serverName == null
          ? l10n.chatProjectChipSemantics(name)
          : l10n.chatProjectServerChipSemantics(name, serverName),
      icon: AppIconography.folderOpen,
      menuLabel: l10n.chatProjectMenuLabel,
      items: items,
      branch: _gitChip(directory, l10n),
      origin: origin,
    );
  }

  /// This conversation's project, as the Files page: attaching a file or a
  /// review comment from there goes to this composer.
  Future<void> _openProjectFilesPage(String directory) => openProjectFiles(
    context,
    _conn,
    directory: directory,
    onAttachFile: _attachProjectFile,
    onReviewPrompt: _addReviewPrompt,
    handoff: _handoff,
  );

  KitTopBar _chatTopBar({
    required Session? session,
    required String? serverName,
    required bool shared,
    required int runningWorkCount,
  }) {
    final l10n = _chatL10n(context);
    // One line under the title: the project (and the server when several
    // are connected). Without a project line the server stands alone.
    final scope = _projectScope(session, l10n, serverName: serverName);
    final used = _contextWindowUsage();
    return KitTopBar(
      titleKey: const Key('chat-title'),
      // Watching a team worker: the task it is on, not its internal title.
      title: _topBarTitle(session, l10n),
      // Which server (and so which agent) this conversation is with, when
      // there is more than one to be with.
      subtitle: scope == null ? serverName : null,
      scope: scope,
      actions: [
        if (_readAloudRequestBusy || _readAloud?.speaking == true)
          KitAction(
            icon: AppIconography.stopCircle,
            label: l10n.readAloudStop,
            onPressed: () => unawaited(_stopReading()),
          ),
        // One tap folds every open step and fold on the page, in any chat. It comes before the
        // running-work count: on a phone the bar shows one action, and this one
        // is the page's own.
        KitAction(
          visible: _transcriptExpansion.anyOpen,
          key: const Key('chat-collapse-all'),
          icon: AppIconography.unfoldLess,
          label: l10n.chatCollapseAllSteps,
          onPressed: _collapseAllSteps,
        ),
        if (!_conn.isIsolated &&
            !_watching &&
            _conn.capabilities.projectManagement &&
            runningWorkCount > 0)
          KitAction(
            key: const Key('running-work-indicator'),
            icon: AppIconography.branch,
            label: l10n.workCount(runningWorkCount),
            onPressed: _openRunningWork,
          ),
        if (_conn.isIsolated)
          KitAction(
            icon: AppIconography.review,
            label: l10n.demoReviewChanges,
            onPressed: _showDiff,
          ),
        // Watching: the worker's own page (its state and controls).
        if (widget.watch case final watch?) ?_watchDetailsAction(watch),
      ],
      // Watching: the conversation is the worker's; nothing in the menu
      // (share, fork, rename) is ours. The demo has none either.
      // The context ring stays in the bar at every width, left of the ⋯.
      accessory: _contextRing(used),
      accessoryShown: (used ?? 0) >= _contextRingFrom,
      menu: _sessionMenu(shared: shared),
      menuKey: const ValueKey('session-actions-button'),
      menuLabel: l10n.chatUiSessionMenu,
    );
  }
}
