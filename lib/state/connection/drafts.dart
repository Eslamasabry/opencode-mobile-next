part of '../connection.dart';

// Session drafts, their attachments and prompt photos.

/// [ConnectionController]'s session drafts.
mixin _ConnectionControllerDrafts on ChangeNotifier {
  ConnectionController get _self;

  /// Composer text typed in a chat but never sent, kept per session so
  /// navigating between sessions loses nothing. Loaded lazily from
  /// [SessionDraftStore] and kept in memory afterward.
  Map<String, SessionDraft>? _sessionDrafts;
  SessionDraftStore? _sessionDraftStore;
  Future<void> _draftChanges = Future.value();

  /// The unsent composer text for this server and session. Old unattributed
  /// drafts can be recovered automatically only with an unambiguous server.
  String? sessionDraft(String sessionID) {
    return savedSessionDraft(sessionID)?.text;
  }

  /// Bootstrap step, before any conversation reads its draft: a photo the
  /// camera handed back after Android stopped the app goes into the draft of
  /// the conversation that asked for it (P3.2). Also used when a new photo is
  /// picked while an older one from another conversation is still waiting.
  /// The conversation that is open handles its own photo in its composer.
  Future<DraftPhotoRecoveryResult> recoverPendingPhoto() =>
      _self._recoverPendingPhoto();

  /// Why the older drafts have not moved into Saved prompts yet, or null.
  /// Set by [migrateOlderDrafts]; Saved prompts shows it with a retry.
  DraftMigrationBlocker? get olderDraftsBlocker => _olderDraftsBlocker;
  DraftMigrationBlocker? _olderDraftsBlocker;

  /// Moves the older drafts (saved before drafts named their server) into
  /// the Saved prompts of the server in use, once (P3.2; see
  /// [MigrationRunner]). Runs in the draft lane so no draft write overlaps
  /// it. Safe to call again: a finished migration returns at once.
  Future<DraftMigrationResult> migrateOlderDrafts() =>
      _self._migrateOlderDrafts();

  SessionDraft? savedSessionDraft(String sessionID, {String? profileID}) =>
      _self._savedSessionDraft(sessionID, profileID: profileID);

  Future<DraftAttachmentRecovery> restoreDraftAttachments(
    String sessionID, {
    required String profileID,
    required String? directory,
    required String? workspace,
  }) => _self._restoreDraftAttachments(
    sessionID,
    profileID: profileID,
    directory: directory,
    workspace: workspace,
  );

  /// Remembers (or, when [text] is blank, forgets) the composer draft for
  /// one session. No [notifyListeners]: drafts drive nothing outside the
  /// chat screen that saved them.
  Future<void> saveSessionDraft(
    String sessionID,
    String text, {
    String? profileID,
    List<PromptAttachment>? attachments,
    String? attachmentDirectory,
    String? attachmentWorkspace,
  }) => _self._saveSessionDraft(
    sessionID,
    text,
    profileID: profileID,
    attachments: attachments,
    attachmentDirectory: attachmentDirectory,
    attachmentWorkspace: attachmentWorkspace,
  );
}

extension _ConnectionControllerDraftsImpl on ConnectionController {
  SessionDraftStore get _draftStore =>
      _sessionDraftStore ??= SessionDraftStore(prefs: store.prefs);

  Map<String, SessionDraft> get _drafts =>
      _sessionDrafts ??= _draftStore.load();

  Future<T> _serializeDraftChange<T>(Future<T> Function() change) {
    final operation = _draftChanges.then((_) => change());
    _draftChanges = operation.then<void>((_) {}, onError: (Object _) {});
    return operation;
  }

  /// The body of [recoverPendingPhoto].
  Future<DraftPhotoRecoveryResult> _recoverPendingPhoto() =>
      _serializeDraftChange(() async {
        try {
          return await DraftPhotoRecovery(
            photos: promptPhotos,
            drafts: _draftStore,
            vault: _draftAttachmentVault,
            profileExists: isProfileReadable,
          ).recover();
        } finally {
          // Recovery writes the draft index directly; read it again.
          _sessionDrafts = null;
        }
      });

  /// The body of [migrateOlderDrafts].
  Future<DraftMigrationResult> _migrateOlderDrafts() {
    final owner = promptShelfProfileID;
    return _serializeDraftChange(() async {
      final result = await MigrationRunner(
        prefs: store.prefs,
        shelf: _promptShelf,
        draftVault: _draftAttachmentVault,
        draftStore: _draftStore,
        profileExists: isProfileReadable,
      ).runForProfile(owner);
      _sessionDrafts = null;
      final blocker = result.blocker == DraftMigrationBlocker.unknownOwner
          ? null
          : result.blocker;
      if (result.migrated > 0 || blocker != _olderDraftsBlocker) {
        _olderDraftsBlocker = blocker;
        if (!_disposed) _notifyListeners();
      }
      return result;
    });
  }

  /// The body of [savedSessionDraft].
  SessionDraft? _savedSessionDraft(String sessionID, {String? profileID}) {
    final owner = profileID ?? profile?.id ?? store.activeId ?? '';
    return (_drafts[SessionDraft.keyFor(owner, sessionID)] ??
        (store.profiles.length <= 1 ? _drafts[sessionID] : null));
  }

  Future<bool> _collectDraftAttachments({String? owner}) async {
    if (!_draftStore.readable) return false;
    if (!(store.prefs.getBool('oc.draftAttachmentVault') ?? false)) return true;
    final retained = <String, List<DraftAttachmentRef>>{};
    for (final draft in _drafts.values) {
      retained.putIfAbsent(draft.profileID, () => []).addAll(draft.attachments);
    }
    return _draftAttachmentVault.collect(retained, owner: owner);
  }

  /// The body of [restoreDraftAttachments].
  Future<DraftAttachmentRecovery> _restoreDraftAttachments(
    String sessionID, {
    required String profileID,
    required String? directory,
    required String? workspace,
  }) => _serializeDraftChange(() async {
    final draft = savedSessionDraft(sessionID, profileID: profileID);
    if (draft == null) return const DraftAttachmentRecovery([], []);
    return _draftAttachmentVault.restore(
      draft.profileID,
      draft.attachments,
      sameLocation:
          draft.directory == directory && draft.workspace == workspace,
    );
  });

  /// The body of [saveSessionDraft].
  Future<void> _saveSessionDraft(
    String sessionID,
    String text, {
    String? profileID,
    List<PromptAttachment>? attachments,
    String? attachmentDirectory,
    String? attachmentWorkspace,
  }) {
    final owner = profileID ?? profile?.id ?? store.activeId ?? '';
    final snapshot = attachments == null
        ? null
        : List<PromptAttachment>.of(attachments);
    return _serializeDraftChange(() async {
      if (!_draftStore.readable) {
        throw const SessionDraftWriteException(SessionDraftFailure.storage);
      }
      if (_deletingReadProfiles.contains(owner) ||
          (owner.isNotEmpty && !_isKnownProfile(owner))) {
        if (text.trim().isEmpty && (snapshot?.isEmpty ?? true)) return;
        throw const SessionDraftWriteException(
          SessionDraftFailure.profileRemoved,
        );
      }
      final key = SessionDraft.keyFor(owner, sessionID);
      if (snapshot == null &&
          (_drafts[key]?.text == text ||
              (text.trim().isEmpty &&
                  !_drafts.containsKey(key) &&
                  (store.profiles.length > 1 ||
                      !_drafts.containsKey(sessionID))))) {
        return;
      }
      var refs = text.trim().isEmpty
          ? <DraftAttachmentRef>[]
          : (_drafts[key]?.attachments ?? const <DraftAttachmentRef>[]);
      if (snapshot != null) {
        try {
          if (snapshot.any((a) => a.url.startsWith('data:'))) {
            if (!(store.prefs.getBool('oc.draftAttachmentVault') ?? false) &&
                !await store.prefs.setBool('oc.draftAttachmentVault', true)) {
              await store.prefs.reload();
              throw const SessionDraftWriteException(
                SessionDraftFailure.attachments,
              );
            }
            await _collectDraftAttachments();
          }
          refs = await _draftAttachmentVault.store(owner, snapshot);
        } catch (_) {
          await _collectDraftAttachments();
          throw const SessionDraftWriteException(
            SessionDraftFailure.attachments,
          );
        }
      }
      final next = Map<String, SessionDraft>.of(_drafts);
      if (store.profiles.length <= 1) next.remove(sessionID);
      if (text.trim().isEmpty && refs.isEmpty) {
        next.remove(key);
      } else {
        next[key] = SessionDraft(
          sessionID: sessionID,
          profileID: owner,
          text: text,
          updatedAt: DateTime.now().millisecondsSinceEpoch,
          attachments: refs,
          directory: snapshot == null
              ? _drafts[key]?.directory
              : attachmentDirectory,
          workspace: snapshot == null
              ? _drafts[key]?.workspace
              : attachmentWorkspace,
        );
      }
      if (next.length > SessionDraftStore.maxDrafts) {
        await _collectDraftAttachments();
        throw const SessionDraftWriteException(SessionDraftFailure.full);
      }
      if (!await _draftStore.save(next)) {
        await _collectDraftAttachments();
        throw const SessionDraftWriteException(SessionDraftFailure.storage);
      }
      _sessionDrafts = next;
      await _collectDraftAttachments();
    });
  }
}
