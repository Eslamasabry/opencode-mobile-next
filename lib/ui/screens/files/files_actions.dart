part of '../files_screen.dart';

/// Staging files and references into the chat, and reviews.
extension _FilesActions on _FilesScreenState {
  /// A plain project file staged as a reference: the agent is pointed at the
  /// path (and the line the user was reading), nothing is uploaded.
  void _stageProjectFile(String path, int? line) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    final change = _fileStatuses[path];
    _stageReference(
      ReviewReference(
        id: handoff.nextID('project-file'),
        kind: change == null
            ? ReviewReferenceKind.file
            : ReviewReferenceKind.changedFile,
        path: path,
        lineLabel: line == null ? null : readerL10n(context).readerUiLine(line),
        added: change?.additions,
        removed: change?.deletions,
        status: change?.status,
      ),
    );
  }

  void _openSymbol(WorkspaceSymbol symbol) {
    _openFile(
      FileNode(
        name: symbol.path.split('/').last,
        path: symbol.path,
        isDir: false,
      ),
      initialLine: symbol.line,
    );
  }

  /// Shared by every Files add-to-prompt affordance: a staged reference is
  /// done with Undo; a duplicate or a full tray is said in place.
  void _stageReference(ReviewReference reference) {
    final handoff = widget.handoff;
    if (handoff == null) return;
    final outcome = handoff.stage(reference);
    if (!mounted) return;
    final l10n = readerL10n(context);
    switch (outcome) {
      case ReviewStageOutcome.staged:
        showKitUndo(
          context,
          key: const Key('files-staged-notice'),
          message: l10n.readerUiReferenceAdded(reference.label),
          onUndo: () => handoff.store.remove(handoff.sessionID, reference.id),
        );
      case ReviewStageOutcome.duplicate:
        _set(
          () => _notice = _FilesNotice(
            l10n.readerUiReferenceDuplicate(reference.label),
          ),
        );
      case ReviewStageOutcome.full:
        _set(
          () => _notice = _FilesNotice(
            l10n.readerUiReferenceFull(ReviewHandoffStore.maxPerSession),
          ),
        );
    }
  }

  Future<void> _reviewChanges() async {
    final prompt = await pushWorkingTreeReview(
      context,
      widget.controller,
      handoff: widget.handoff,
    );
    if (mounted) await _deliver(prompt);
  }

  Future<void> _reviewFileChange(FileNode node) async {
    final prompt = await pushWorkingTreeReview(
      context,
      widget.controller,
      handoff: widget.handoff,
      initialFile: node.path,
    );
    if (mounted) await _deliver(prompt);
  }

  Future<void> _deliver(String? prompt) async {
    final said = await deliverReviewPrompt(
      context,
      prompt,
      widget.onReviewPrompt,
    );
    if (said != null && mounted) {
      _set(() => _notice = _FilesNotice(said));
    }
  }

  /// Attaches a file straight from the tree. The viewer's Attach does the
  /// same thing once it has the content; this fetches the content first so
  /// the menu does not need the viewer open.
  Future<void> _attachFile(String path) async {
    final action = widget.onAttachFile;
    if (action == null || _attaching) return;
    _set(() => _attaching = true);
    try {
      final content = await _fetchScoped(path);
      if (content == null) return;
      await action(path, _FilesViewer._exportData(path, content));
      if (!mounted) return;
      _set(
        () => _notice = _FilesNotice(
          readerL10n(context).readerUiAttached(path.split('/').last),
        ),
      );
    } catch (error) {
      if (mounted) _fail(error);
    } finally {
      if (mounted) _set(() => _attaching = false);
    }
  }

  // --- Layout -----------------------------------------------------------------
}
