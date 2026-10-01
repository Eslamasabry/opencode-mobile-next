part of '../chat_screen.dart';

/// The body of "Saved prompts": prompts kept on this device for this
/// server, newest first. The host opens it with [showKitSheet], which draws
/// the title, the close button and [loading]. Tapping a row restores it
/// into the draft at once; the host offers Undo (P3.2). Delete sits in the
/// row's menu and also acts at once: the prompt leaves the device now and
/// "Saved prompt deleted · Undo" puts it back exactly, so no confirmation
/// sheet stacks on this one (map prompt-stash-delete-sheet: remove).
///
/// The older drafts (saved before drafts named their server) live here too:
/// opening the sheet moves any that are left ([ConnectionController
/// .migrateOlderDrafts]); one that cannot move yet is named with a retry.
class _PromptStashSheet extends StatefulWidget {
  const _PromptStashSheet({
    required this.controller,
    required this.location,
    required this.profile,
    required this.loading,
  });
  final ConnectionController controller;
  final int location;
  final String profile;

  /// The sheet's one loading bar: on while the store is read or a restore
  /// is on its way out.
  final ValueNotifier<bool> loading;
  @override
  State<_PromptStashSheet> createState() => _PromptStashSheetState();
}

class _PromptStashSheetState extends State<_PromptStashSheet> {
  final _search = TextEditingController();
  String _query = '';
  String? _error;
  bool _deleting = false;
  bool _preparing = false;
  bool _invalidated = false;
  bool _readFailed = false;
  bool _migrationPending = false;
  List<StashedPrompt> _prompts = const [];

  bool get _current =>
      !_invalidated &&
      widget.controller.canUsePromptShelf &&
      widget.profile == widget.controller.promptShelfProfileID &&
      widget.location == widget.controller.locationRevision;
  bool get _unsafe => !_current || _preparing || _deleting || _readFailed;

  /// Tells the sheet frame whether to show its loading bar. After the
  /// frame, since the frame is this body's ancestor.
  void _syncLoading() {
    final busy = _preparing || _deleting;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.loading.value = busy;
    });
  }

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
    widget.controller.profileDataChanges.addListener(_changed);
    _readPrompts();
    unawaited(_prepare());
  }

  void _readPrompts() {
    if (!_current) return;
    try {
      _prompts = widget.controller.promptStash;
    } catch (_) {
      _readFailed = true;
    }
  }

  void _changed() {
    if (!mounted) return;
    setState(() {
      if (!_current) _invalidated = true;
      if (_invalidated) {
        _prompts = const [];
      } else {
        _readPrompts();
      }
    });
  }

  Future<void> _prepare() async {
    if (!_current || _preparing || _deleting) return;
    setState(() {
      _preparing = true;
      _error = null;
    });
    try {
      await widget.controller.migrateOlderDrafts();
      if (!mounted || !_current) return;
      final deferred = await widget.controller.preparePromptStash(
        locationRevision: widget.location,
      );
      if (!mounted || !_current) return;
      setState(() {
        _readFailed = false;
        _migrationPending = deferred.isNotEmpty;
        _readPrompts();
      });
    } catch (_) {
      if (mounted && _current) setState(() => _readFailed = true);
    } finally {
      if (mounted) setState(() => _preparing = false);
    }
  }

  @override
  void dispose() {
    _search.dispose();
    widget.controller.removeListener(_changed);
    widget.controller.profileDataChanges.removeListener(_changed);
    super.dispose();
  }

  void _restore(StashedPrompt prompt) {
    if (_unsafe || !(ModalRoute.of(context)?.isCurrent ?? true)) return;
    setState(() => _deleting = true);
    Navigator.pop(context, prompt);
  }

  /// Delete at once, with Undo. The prompt leaves the device now; Undo
  /// puts it back exactly (text, attachments, references, date).
  Future<void> _delete(StashedPrompt prompt) async {
    if (_unsafe) return;
    final controller = widget.controller;
    final strings = _chatL10n(context);
    setState(() {
      _deleting = true;
      _error = null;
    });
    try {
      final undo = await controller.deleteSavedPrompt(
        prompt.id,
        locationRevision: widget.location,
      );
      if (!mounted) return;
      showKitUndo(
        context,
        message: strings.promptStashDeleted,
        key: ValueKey('stash-deleted-${prompt.id}'),
        onUndo: () => controller.undoSavedPrompt(undo),
      );
    } catch (_) {
      if (mounted && _current) {
        setState(() => _error = strings.promptStashDeleteFailed);
      }
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }

  bool _matches(StashedPrompt prompt, String query) {
    if (query.isEmpty) return true;
    final fields = <String>[
      prompt.text,
      ...prompt.attachmentNames,
      if (prompt.directory != null) prompt.directory!,
      if (prompt.workspace != null) prompt.workspace!,
      for (final reference in prompt.references) reference.description,
    ];
    return fields.any((field) => field.toLowerCase().contains(query));
  }

  String _supporting(BuildContext context, StashedPrompt prompt) {
    final l10n = _chatL10n(context);
    return [
      MaterialLocalizations.of(
        context,
      ).formatMediumDate(DateTime.fromMillisecondsSinceEpoch(prompt.createdAt)),
      if (prompt.attachmentCount > 0)
        l10n.promptStashAttachments(prompt.attachmentCount),
      if (prompt.references.isNotEmpty)
        l10n.promptStashReferences(prompt.references.length),
      if (prompt.locationBound)
        KitBidi.ltr(prompt.directory ?? l10n.promptDefaultLocation),
    ].join(' · ');
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: widget.controller,
    builder: (context, _) {
      final l10n = _chatL10n(context);
      final query = _query.trim().toLowerCase();
      final prompts = _prompts.where((p) => _matches(p, query)).toList()
        // Newest first.
        ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
      final current = _current;
      final error = !current
          ? l10n.promptStashScopeChanged
          : _readFailed
          ? l10n.promptStashReadFailed
          : _error;
      final unsafe = _unsafe;
      final busyReason = !current ? l10n.promptStashScopeChanged : null;
      _syncLoading();
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitSearchField(
            controller: _search,
            label: l10n.promptStashSearch,
            enabled: current,
            disabledReason: busyReason,
            resultCount: query.isEmpty ? null : prompts.length,
            onChanged: (value) => setState(() => _query = value),
          ),
          if (error != null)
            KitNotice(
              key: const Key('prompt-stash-error'),
              tone: AppStatusTone.failure,
              message: error,
              actions: [
                if (current && _readFailed)
                  KitAction(
                    label: l10n.commonRetry,
                    onPressed: _preparing ? null : () => unawaited(_prepare()),
                  ),
              ],
            ),
          if (widget.controller.olderDraftsBlocker case final blocker?
              when current)
            KitNotice(
              key: const Key('older-drafts-waiting'),
              message: blocker == DraftMigrationBlocker.full
                  ? l10n.promptStashOlderDraftsFull
                  : l10n.promptStashOlderDraftsWaiting,
              actions: [
                KitAction(
                  label: l10n.commonRetry,
                  onPressed: _preparing || _deleting
                      ? null
                      : () => unawaited(_prepare()),
                ),
              ],
            ),
          if (_migrationPending && current)
            KitNotice(
              message: l10n.promptStashMigrationPending,
              actions: [
                KitAction(
                  label: l10n.commonRetry,
                  onPressed: _preparing || _deleting
                      ? null
                      : () => unawaited(_prepare()),
                ),
              ],
            ),
          if (prompts.isEmpty && error == null && !_preparing)
            if (query.isNotEmpty)
              KitSearchNoMatch(
                query: _query.trim(),
                onClear: () {
                  _search.clear();
                  setState(() => _query = '');
                },
              )
            else
              KitStateView(
                key: const Key('prompt-stash-empty'),
                size: KitStateSize.inline,
                icon: AppIconography.bookmarks,
                title: l10n.promptStashEmptyTitle,
                body: l10n.promptStashEmptyBody,
              ),
          if (prompts.isNotEmpty)
            KitRowGroup(
              leadingIcons: false,
              children: [
                for (final prompt in prompts)
                  KitRow(
                    key: ValueKey('restore-stash-${prompt.id}'),
                    title: prompt.text.isEmpty
                        ? l10n.promptStashContextOnly
                        : prompt.text,
                    titleMaxLines: 2,
                    supporting: TextSpan(text: _supporting(context, prompt)),
                    supportingMaxLines: 2,
                    enabled: !unsafe,
                    disabledReason: unsafe
                        ? (busyReason ?? l10n.promptStashBusy)
                        : null,
                    onTap: unsafe ? null : () => _restore(prompt),
                    menuLabel: l10n.promptStashRowActions,
                    menu: unsafe
                        ? const []
                        : [
                            KitMenuItem(
                              key: ValueKey('restore-stash-menu-${prompt.id}'),
                              icon: AppIconography.unarchive,
                              label: l10n.promptStashRestoreToDraft,
                              onSelected: () => _restore(prompt),
                            ),
                            KitMenuItem(
                              key: ValueKey('delete-stash-${prompt.id}'),
                              icon: AppIconography.delete,
                              label: l10n.promptStashDeleteAction,
                              destructive: true,
                              onSelected: () => unawaited(_delete(prompt)),
                            ),
                          ],
                  ),
              ],
            ),
        ],
      );
    },
  );
}

mixin _ChatPromptStashFields {
  bool _promptShelfOperationBusy = false;

  int _promptContentRevision = 0;
}

extension _ChatPromptStash on _ChatScreenState {
  bool get _promptShelfBusy =>
      _photoBusy ||
      _promptShelfOperationBusy ||
      _restoringDraftAttachments ||
      _draftRecoveryBlocked;

  StashedPrompt _snapshotPrompt() => StashedPrompt(
    id: DateTime.now().microsecondsSinceEpoch.toString(),
    text: _composer.text,
    createdAt: DateTime.now().millisecondsSinceEpoch,
    directory: _conn.directory,
    workspace: _conn.workspace,
    attachments: List.of(_attachments),
    references: List.of(_stagedReferences),
  );

  bool _promptUnchanged(StashedPrompt snapshot, int location) =>
      mounted &&
      location == _conn.locationRevision &&
      snapshot.text == _composer.text &&
      listEquals(snapshot.attachments, _attachments) &&
      listEquals(snapshot.references, _stagedReferences);

  Future<void> _stashCurrentPrompt() async {
    if (_sending || _promptShelfBusy || !_conn.canUsePromptShelf) return;
    final snapshot = _snapshotPrompt();
    if (snapshot.isEmpty) return;
    final location = _conn.locationRevision;
    final session = widget.sessionID;
    final profile = _conn.promptShelfProfileID;
    final revision = _promptContentRevision;
    final route = ModalRoute.of(context);
    _setChatState(() => _promptShelfOperationBusy = true);
    try {
      if (_conn.promptStash.length >= PromptShelfStore.capacity) {
        _showComposerNote(_chatL10n(context).promptStashFull);
        return;
      }
      await _conn.savePromptStash(snapshot, locationRevision: location);
      if (!_promptUnchanged(snapshot, location) ||
          widget.sessionID != session ||
          profile != _conn.promptShelfProfileID ||
          !_conn.canUsePromptShelf ||
          revision != _promptContentRevision ||
          !(route?.isCurrent ?? true)) {
        return;
      }
      _setChatState(() {
        _composer.clear();
        _attachments.clear();
        _handoff.store.clear(widget.sessionID);
      });
      _restoreHistoryDraft();
      final clearedRevision = _promptContentRevision;
      final persisted = await _persistDraft();
      if (mounted &&
          widget.sessionID == session &&
          profile == _conn.promptShelfProfileID &&
          location == _conn.locationRevision &&
          _conn.canUsePromptShelf &&
          clearedRevision == _promptContentRevision &&
          (route?.isCurrent ?? true)) {
        _showComposerNote(
          persisted
              ? _chatL10n(context).promptStashed
              : _chatL10n(context).promptStashedDraftPending,
        );
      }
    } catch (_) {
      if (mounted) _showActionError(_chatL10n(context).promptStashSaveFailed);
    } finally {
      if (mounted) _setChatState(() => _promptShelfOperationBusy = false);
    }
  }

  Future<void> _openPromptStash() async {
    if (_sending || _promptShelfBusy || !_conn.canUsePromptShelf) return;
    final location = _conn.locationRevision;
    final profile = _conn.promptShelfProfileID;
    final session = widget.sessionID;
    final route = ModalRoute.of(context);
    var invalidated = false;
    void checkScope() {
      if (!_conn.canUsePromptShelf ||
          profile != _conn.promptShelfProfileID ||
          location != _conn.locationRevision) {
        invalidated = true;
      }
    }

    bool currentScope() =>
        mounted &&
        !invalidated &&
        widget.sessionID == session &&
        _conn.canUsePromptShelf &&
        profile == _conn.promptShelfProfileID &&
        location == _conn.locationRevision &&
        (route?.isCurrent ?? true);
    final current = _snapshotPrompt();
    final revision = _promptContentRevision;
    bool unchanged() =>
        currentScope() &&
        revision == _promptContentRevision &&
        _promptUnchanged(current, location);
    _conn.addListener(checkScope);
    _conn.profileDataChanges.addListener(checkScope);
    _setChatState(() => _promptShelfOperationBusy = true);
    // Not disposed here: the sheet's body can still report to it while the
    // sheet slides away; nothing holds it after that.
    final loading = ValueNotifier<bool>(true);
    try {
      final l10n = _chatL10n(context);
      final selected = await showKitSheet<StashedPrompt>(
        context,
        sheetKey: const Key('prompt-stash-sheet'),
        title: l10n.promptStashTitle,
        subtitle: l10n.promptStashIntro,
        icon: AppIconography.bookmarks,
        loading: loading,
        body: (_) => _PromptStashSheet(
          controller: _conn,
          location: location,
          profile: profile,
          loading: loading,
        ),
      );
      if (!mounted || selected == null || !unchanged()) {
        return;
      }
      if (selected.locationBound &&
          (selected.directory != _conn.directory ||
              selected.workspace != _conn.workspace)) {
        _showActionError(
          _chatL10n(context).promptStashLocation(
            selected.directory ?? _chatL10n(context).promptDefaultLocation,
          ),
        );
        return;
      }
      final recovered = await _conn.restorePromptStashAttachments(
        selected.id,
        locationRevision: location,
      );
      if (!mounted || !unchanged()) return;
      // Restoring acts at once, with Undo (P3.2): no question first. The
      // draft it replaces is what Undo brings back; it is also kept in Saved
      // prompts until then, so closing the app mid-way loses nothing. While
      // arrow keys browse sent prompts, the draft is the one put aside.
      final historyOriginal = _promptHistory.original;
      final previousValue = historyOriginal ?? _composer.value;
      final previousAttachments = List<PromptAttachment>.of(_attachments);
      final previousReferences = List<ReviewReference>.of(_stagedReferences);
      final previous = StashedPrompt(
        id: current.id,
        text: previousValue.text,
        createdAt: current.createdAt,
        directory: current.directory,
        workspace: current.workspace,
        attachments: previousAttachments,
        references: previousReferences,
      );
      String? keptID;
      if (!previous.isEmpty) {
        if (_conn.promptStash.length >= PromptShelfStore.capacity) {
          _showComposerNote(_chatL10n(context).promptStashFull);
          return;
        }
        await _conn.savePromptStash(previous, locationRevision: location);
        keptID = previous.id;
      }
      if (!mounted || !unchanged()) return;
      if (historyOriginal != null) _promptHistory.restore();
      void stageAll(Iterable<ReviewReference> references, String prefix) {
        for (final reference in references) {
          _handoff.stage(
            ReviewReference(
              id: _handoff.nextID(prefix),
              kind: reference.kind,
              path: reference.path,
              scope: reference.scope,
              lineLabel: reference.lineLabel,
              snippet: reference.snippet,
              comment: reference.comment,
              added: reference.added,
              removed: reference.removed,
              status: reference.status,
            ),
          );
        }
      }

      _setChatState(() {
        _composer.value = TextEditingValue(
          text: selected.text,
          selection: TextSelection.collapsed(offset: selected.text.length),
        );
        _attachments.clear();
        _attachments.addAll(recovered.attachments);
        _handoff.store.clear(session);
        stageAll(selected.references, 'stash-${selected.id}');
      });
      final restored = _snapshotPrompt();
      final restoredRevision = _promptContentRevision;
      await _persistDraft();
      if (!mounted ||
          !currentScope() ||
          restoredRevision != _promptContentRevision ||
          !_promptUnchanged(restored, location)) {
        return;
      }
      _focus.requestFocus();
      final unavailable = recovered.unavailable;
      showKitUndo(
        _undoHost,
        key: const Key('prompt-restored-undo'),
        message: unavailable.isEmpty
            ? l10n.promptRestored
            : l10n.promptRestoredWithout(unavailable.join(', ')),
        onUndo: () async {
          // Only over the restored prompt itself: newer typing wins.
          if (!currentScope() || !_promptUnchanged(restored, location)) {
            throw StateError('The draft changed after the restore');
          }
          _setChatState(() {
            _composer.value = previousValue;
            _attachments
              ..clear()
              ..addAll(previousAttachments);
            _handoff.store.clear(session);
            stageAll(previousReferences, 'draft');
          });
          if (!await _persistDraft()) {
            throw StateError('The draft could not be saved');
          }
          // The draft is back in the composer; its safety copy may go. If
          // that fails, a spare copy in Saved prompts is harmless.
          if (keptID != null) {
            try {
              await _conn.removePromptStash(keptID, locationRevision: location);
            } catch (_) {}
          }
        },
      );
    } catch (_) {
      if (mounted && currentScope()) {
        _showActionError(_chatL10n(context).promptStashRestoreFailed);
      }
    } finally {
      _conn.removeListener(checkScope);
      _conn.profileDataChanges.removeListener(checkScope);
      if (mounted) _setChatState(() => _promptShelfOperationBusy = false);
    }
  }
}
