part of '../connection.dart';

// The prompt shelf: stash, saved prompts and sent prompt history.

bool _isKeptQueuedDraft(String id) => id.startsWith('kept-');

void _checkPromptShelfScope(bool Function() current) {
  if (!current()) throw StateError('The prompt location changed');
}

/// [ConnectionController]'s prompt shelf.
mixin _ConnectionControllerPromptShelf on ChangeNotifier {
  ConnectionController get _self;

  final _promptShelfDeletionRevisions = <String, int>{};
  String get promptShelfProfileID =>
      _self._agentOwnerProfileId ??
      (_self._connectedProfile ?? _self.profile)?.id ??
      '';
  bool get canUsePromptShelf => _self.isProfileReadable(promptShelfProfileID);
  List<StashedPrompt> get promptStash => _self._promptStash;

  /// Queued prompts kept from removed servers, shown in every server's Saved
  /// prompts. Unreadable ones stay on disk and are left out of the list.
  List<StashedPrompt> get keptQueuedDrafts => _self._keptQueuedDrafts;
  List<String> get sentPromptHistory => canUsePromptShelf
      ? _self._promptShelf.history(promptShelfProfileID)
      : const [];

  /// Lazily migrate without consuming saved prompts. Empty shelves do not
  /// access a vault/root or write preferences, even on the live default path.
  Future<List<String>> preparePromptStash({required int locationRevision}) =>
      _self._preparePromptStash(locationRevision: locationRevision);

  Future<DraftAttachmentRecovery> restorePromptStashAttachments(
    String id, {
    required int locationRevision,
  }) => _self._restorePromptStashAttachments(
    id,
    locationRevision: locationRevision,
  );

  Future<void> savePromptStash(
    StashedPrompt prompt, {
    required int locationRevision,
  }) => _self._savePromptStash(prompt, locationRevision: locationRevision);

  Future<void> removePromptStash(String id, {required int locationRevision}) =>
      _self._removePromptStash(id, locationRevision: locationRevision);

  SavedPromptsController? _savedPrompts;

  /// Deletes a saved prompt now, without asking. The returned Undo puts it
  /// back exactly, attachments included, until its notice closes.
  Future<SavedPromptUndo> deleteSavedPrompt(
    String id, {
    required int locationRevision,
  }) => _self._deleteSavedPrompt(id, locationRevision: locationRevision);

  /// Runs a saved-prompt Undo and refreshes whoever lists saved prompts.
  Future<void> undoSavedPrompt(SavedPromptUndo undo) =>
      _self._undoSavedPrompt(undo);

  Future<void> rememberSentPrompt(String profileID, String text) =>
      _self._rememberSentPrompt(profileID, text);
}

extension _ConnectionControllerPromptShelfImpl on ConnectionController {
  /// The body of [promptStash].
  List<StashedPrompt> get _promptStash {
    if (!canUsePromptShelf) return const [];
    final prompts = [
      ..._promptShelf.stashes(promptShelfProfileID),
      ...keptQueuedDrafts,
    ]..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return List.unmodifiable(prompts);
  }

  /// The body of [keptQueuedDrafts].
  List<StashedPrompt> get _keptQueuedDrafts {
    try {
      return _keptQueued.savedDrafts;
    } on StateError {
      return const [];
    }
  }

  /// The shelf belongs to a profile; one operation also belongs to the selected
  /// location and that profile's deletion epoch. It does not belong to a server
  /// request/session or replaceable API/repository generation: these are local
  /// files, usable offline and across same-location transport recovery. A chat
  /// must separately guard its destination session/composer before insertion.
  bool Function() _promptShelfScope(int expectedLocation) {
    if (!canUsePromptShelf) throw StateError('The prompt location changed');
    final owner = promptShelfProfileID;
    final savedProfile = store.profiles.firstWhere(
      (candidate) => candidate.id == owner,
    );
    final savedOrigin = savedProfile.baseUrl;
    final activeID = store.activeId;
    final origin = (_connectedProfile ?? profile)?.baseUrl;
    final location = (directory, workspace);
    final deletion = _promptShelfDeletionRevisions[owner] ?? 0;
    bool current() =>
        canUsePromptShelf &&
        locationRevision == expectedLocation &&
        promptShelfProfileID == owner &&
        store.activeId == activeID &&
        store.profiles.any((candidate) => identical(candidate, savedProfile)) &&
        savedProfile.baseUrl == savedOrigin &&
        (_connectedProfile ?? profile)?.baseUrl == origin &&
        (directory, workspace) == location &&
        (_promptShelfDeletionRevisions[owner] ?? 0) == deletion;
    _checkPromptShelfScope(current);
    return current;
  }

  /// The body of [preparePromptStash].
  Future<List<String>> _preparePromptStash({
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    final owner = promptShelfProfileID;
    List<StashedPrompt>? before;
    try {
      before = _promptShelf.stashes(owner);
    } on StateError {
      // A previous refused write may need the serialized metadata reload below.
    }
    final deferred = await _promptShelf.migrateAttachments(
      owner,
      checkCurrent: () => _checkPromptShelfScope(current),
    );
    _checkPromptShelfScope(current);
    if (!listEquals(before, _promptShelf.stashes(owner))) _notifyListeners();
    return deferred;
  }

  /// The body of [restorePromptStashAttachments].
  Future<DraftAttachmentRecovery> _restorePromptStashAttachments(
    String id, {
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    if (_isKeptQueuedDraft(id)) {
      // Only embedded bytes travel: a removed server's file or link is not
      // this server's, so it is named as unavailable instead.
      final prompt = keptQueuedDrafts.firstWhere((p) => p.id == id);
      return DraftAttachmentRecovery(
        [
          for (final attachment in prompt.attachments)
            if (attachment.url.startsWith('data:')) attachment,
        ],
        [
          for (final attachment in prompt.attachments)
            if (!attachment.url.startsWith('data:')) attachment.filename,
        ],
      );
    }
    final owner = promptShelfProfileID;
    final prompt = _promptShelf.stashes(owner).firstWhere((p) => p.id == id);
    final sameLocation =
        ConnectionController.sameDirectoryPath(prompt.directory, directory) &&
        prompt.workspace == workspace;
    if (prompt.locationBound && !sameLocation) {
      throw StateError('The saved prompt belongs to another location');
    }
    void checkCurrent() {
      _checkPromptShelfScope(current);
      // IDs may be removed and reused while this read waits in the file queue.
      if (!_promptShelf.stashes(owner).any((p) => identical(p, prompt))) {
        throw StateError('The saved prompt changed');
      }
    }

    final recovery = await _promptShelf.restoreAttachments(
      owner,
      id,
      sameLocation: sameLocation,
      checkCurrent: checkCurrent,
    );
    checkCurrent();
    return recovery;
  }

  /// The body of [savePromptStash].
  Future<void> _savePromptStash(
    StashedPrompt prompt, {
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    final owner = promptShelfProfileID;
    try {
      await _promptShelf.stash(
        owner,
        prompt,
        checkCurrent: () => _checkPromptShelfScope(current),
      );
      _checkPromptShelfScope(current);
    } finally {
      if (current()) _notifyListeners();
    }
  }

  /// The body of [removePromptStash].
  Future<void> _removePromptStash(
    String id, {
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    final owner = promptShelfProfileID;
    try {
      await _promptShelf.remove(
        owner,
        id,
        checkCurrent: () => _checkPromptShelfScope(current),
      );
      _checkPromptShelfScope(current);
    } finally {
      // Removal may have committed metadata before file cleanup failed.
      if (current()) _notifyListeners();
    }
  }

  /// One Saved prompts controller for the server in use, sharing its shelf.
  SavedPromptsController get _savedPromptsForShelf {
    final owner = promptShelfProfileID;
    final current = _savedPrompts;
    if (current != null && current.profileID == owner) return current;
    current?.dispose();
    return _savedPrompts = SavedPromptsController(
      profileID: owner,
      shelf: _promptShelf,
      profileExists: isProfileReadable,
    );
  }

  /// The body of [deleteSavedPrompt].
  Future<SavedPromptUndo> _deleteSavedPrompt(
    String id, {
    required int locationRevision,
  }) async {
    _promptShelfScope(locationRevision);
    try {
      if (_isKeptQueuedDraft(id)) {
        final removed = await _keptQueued.forgetDraft(id);
        if (removed == null) throw StateError('The saved prompt is gone');
        return SavedPromptUndo.kept(() => _keptQueued.rememberDraft(removed));
      }
      return await _savedPromptsForShelf.delete(id);
    } finally {
      if (!_disposed) _notifyListeners();
    }
  }

  /// The body of [undoSavedPrompt].
  Future<void> _undoSavedPrompt(SavedPromptUndo undo) async {
    try {
      await undo.undo();
    } finally {
      if (!_disposed) _notifyListeners();
    }
  }

  /// The body of [rememberSentPrompt].
  Future<void> _rememberSentPrompt(String profileID, String text) async {
    // A network send can finish after its server profile has been deleted.
    // Never recreate the removed profile's local history in that callback.
    final deletion = _promptShelfDeletionRevisions[profileID] ?? 0;
    bool current() =>
        isProfileReadable(profileID) &&
        (_promptShelfDeletionRevisions[profileID] ?? 0) == deletion;
    if (!current()) return;
    try {
      await _promptShelf.recordSent(
        profileID,
        text,
        checkCurrent: () => _checkPromptShelfScope(current),
      );
    } catch (_) {
      if (current()) rethrow;
    }
  }
}
