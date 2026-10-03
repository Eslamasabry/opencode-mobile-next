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

  /// Fraction of the model's context window consumed, from the newest
  /// assistant message that reported token usage and the catalog's limit for
  /// its exact model. Null when either side is unknown.
  double? _contextWindowUsage() {
    final catalog = _conn.catalog;
    if (catalog == null) return null;
    for (var index = _visibleHistory.length - 1; index >= 0; index -= 1) {
      final info = _messages[index].info;
      if (info.role != 'assistant' || info.tokens.total <= 0) continue;
      for (final model in catalog.models) {
        if (model.providerID == info.providerID && model.id == info.modelID) {
          return model.contextLimit > 0
              ? info.tokens.total / model.contextLimit
              : null;
        }
      }
      return null;
    }
    return null;
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

  /// The project this conversation lives in, as a chip under the title with
  /// a compact menu of what can be done there. Absent for a temporary folder
  /// (never shown as a project), a watched team worker and the demo.
  KitTopBarScope? _projectScope(Session? session, AppLocalizations l10n) {
    if (_watching || _conn.isIsolated) return null;
    final directory = session?.directory ?? _conn.directory;
    if (isTemporaryProjectDirectory(directory)) return null;
    final parts = directory!.split('/').where((part) => part.isNotEmpty);
    final name = parts.isEmpty ? directory : parts.last;
    final capabilities = _conn.capabilities;
    final items = <KitMenuItem>[
      if (capabilities.fileBrowsing)
        KitMenuItem(
          key: const ValueKey('chat-project-files'),
          label: l10n.readerUiFiles,
          icon: AppIconography.files,
          onSelected: () =>
              unawaited(_executeMobileCommand(_ChatCommandAction.files)),
        ),
      if (capabilities.terminal)
        KitMenuItem(
          key: const ValueKey('chat-project-terminal'),
          label: l10n.libraryTerminalTitle,
          icon: AppIconography.terminal,
          onSelected: () =>
              unawaited(_executeMobileCommand(_ChatCommandAction.terminal)),
        ),
      if (capabilities.sessionDiff)
        KitMenuItem(
          key: const ValueKey('chat-project-changes'),
          label: l10n.readerUiChanges,
          icon: AppIconography.review,
          onSelected: () => unawaited(_showDiff()),
        ),
    ];
    if (items.isEmpty) return null;
    return KitTopBarScope(
      chipKey: const ValueKey('chat-project-chip'),
      label: KitBidi.auto(name),
      semanticsLabel: l10n.chatProjectChipSemantics(name),
      icon: AppIconography.folderOpen,
      menuLabel: l10n.chatProjectMenuLabel,
      items: items,
    );
  }

  KitTopBar _chatTopBar({
    required Session? session,
    required String? serverName,
    required bool shared,
    required int runningWorkCount,
  }) {
    final l10n = _chatL10n(context);
    return KitTopBar(
      titleKey: const Key('chat-title'),
      // Watching a team worker: the task it is on, not its internal title.
      title: _topBarTitle(session, l10n),
      // Which server (and so which agent) this conversation is with, when
      // there is more than one to be with.
      subtitle: serverName,
      scope: _projectScope(session, l10n),
      actions: [
        if (_readAloudRequestBusy || _readAloud?.speaking == true)
          KitAction(
            icon: AppIconography.stopCircle,
            label: l10n.readAloudStop,
            onPressed: () => unawaited(_stopReading()),
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
        // One tap folds every open step and fold on the page, in any chat.
        if (_transcriptExpansion.anyOpen)
          KitAction(
            key: const Key('chat-collapse-all'),
            icon: AppIconography.unfoldLess,
            label: l10n.chatCollapseAllSteps,
            onPressed: _collapseAllSteps,
          ),
        // Watching: the worker's own page (its state and controls).
        if (widget.watch case final watch?) ?_watchDetailsAction(watch),
      ],
      // Watching: the conversation is the worker's; nothing in the menu
      // (share, fork, rename) is ours. The demo has none either.
      menu: _sessionMenu(shared: shared),
      menuKey: const ValueKey('session-actions-button'),
      menuLabel: l10n.chatUiSessionMenu,
    );
  }
}
