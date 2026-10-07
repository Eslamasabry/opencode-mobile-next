part of '../connection.dart';

// Cached session inventory and transcript tails for a fast first paint.

/// [ConnectionController]'s session inventory and transcript caches.
mixin _ConnectionControllerSessionCache on ChangeNotifier {
  ConnectionController get _self;

  /// Last-known read-only labels for Work/session opening shells. Separate
  /// from sessionsById: these rows never establish existence, running state,
  /// capabilities, permission decisions or a connected transport.
  SessionInventoryPreview? get cachedSessionInventory =>
      _self._cachedSessionInventory;
  final _sessionTailReads =
      <(int, String, int, String?), Future<ServerPage<MessageWithParts>>>{};

  /// A read-only text excerpt for the opening frame. It has no pagination or
  /// message-state authority; replace it with the normal live hydration result.
  SessionTailPreview? cachedSessionTail(String sessionID) =>
      _self._cachedSessionTail(sessionID);

  /// Whether [loadSessionTail] can read [sessionID] now. Chat hydration uses
  /// the shared read when it can and reads the gateway itself otherwise (an
  /// isolated or test host with a gateway but no saved, connected server).
  bool canLoadSessionTail(String sessionID) =>
      _self._canLoadSessionTail(sessionID);

  /// Authoritative newest page; prefetch and route hydration share an in-flight
  /// read. UI still applies its event-version merge and uses gateway.messagePage
  /// for older cursors. Failures retain existing product-error handling.
  Future<ServerPage<MessageWithParts>> loadSessionTail(String sessionID) =>
      _self._loadSessionTail(sessionID);

  /// Invoke only for the next intentional navigation, never every visible row.
  /// A failed speculative read cannot surface a toast or raw transport error.
  Future<void> prefetchSessionTail(String sessionID) async {
    try {
      await loadSessionTail(sessionID);
    } catch (_) {}
  }
}

extension _ConnectionControllerSessionCacheImpl on ConnectionController {
  /// The body of [cachedSessionInventory].
  SessionInventoryPreview? get _cachedSessionInventory {
    final owner = _connectedProfile ?? profile;
    if (owner == null || !isProfileReadable(owner.id)) return null;
    final saved = store.locationFor(owner.id);
    final restoring = api == null || _restoringSavedLocation;
    final previewDirectory = restoring
        ? (owner.usesAgentSocket ? owner.codexDirectory : saved?.directory)
        : directory;
    final previewWorkspace = restoring
        ? (owner.usesAgentSocket ? null : saved?.workspace)
        : workspace;
    return _sessionInventoryCache.read(
      owner.id,
      SessionInventoryCache.scopeFor(owner, previewDirectory, previewWorkspace),
    );
  }

  /// The body of [cachedSessionTail].
  SessionTailPreview? _cachedSessionTail(String sessionID) {
    final owner = _connectedProfile ?? profile;
    if (owner == null ||
        !isProfileReadable(owner.id) ||
        _deletedSessionIDs.contains(sessionID) ||
        sessionsById[sessionID]?.stagedRevert != null) {
      return null;
    }
    final saved = store.locationFor(owner.id);
    final restoring = api == null || _restoringSavedLocation;
    return _sessionTailCache.read(
      owner.id,
      SessionInventoryCache.scopeFor(
        owner,
        restoring
            ? (owner.usesAgentSocket ? owner.codexDirectory : saved?.directory)
            : directory,
        restoring
            ? (owner.usesAgentSocket ? null : saved?.workspace)
            : workspace,
      ),
      sessionID,
    );
  }

  /// The body of [canLoadSessionTail].
  bool _canLoadSessionTail(String sessionID) {
    final owner = _connectedProfile ?? profile;
    return owner != null &&
        api != null &&
        hasConnectedServer &&
        isProfileReadable(owner.id) &&
        !_deletedSessionIDs.contains(sessionID);
  }

  /// The body of [loadSessionTail].
  Future<ServerPage<MessageWithParts>> _loadSessionTail(String sessionID) {
    final owner = _connectedProfile ?? profile;
    final currentApi = api;
    final generation = _generation;
    if (owner == null || currentApi == null || !canLoadSessionTail(sessionID)) {
      return Future.error(const ProductException('OpenCode is reconnecting.'));
    }
    final historyRevision = sessionHistoryRevision(sessionID);
    final boundary = sessionsById[sessionID]?.stagedRevert?.messageID;
    final key = (generation, sessionID, historyRevision, boundary);
    final existing = _sessionTailReads[key];
    if (existing != null) return existing;
    final scope = SessionInventoryCache.scopeFor(owner, directory, workspace);
    final deletionRevision = _profileDeletionRevisions[owner.id];
    bool current() =>
        _isCurrent(generation, currentApi) &&
        isProfileReadable(owner.id) &&
        _profileDeletionRevisions[owner.id] == deletionRevision &&
        sessionHistoryRevision(sessionID) == historyRevision &&
        sessionsById[sessionID]?.stagedRevert?.messageID == boundary &&
        !_deletedSessionIDs.contains(sessionID);
    late final Future<ServerPage<MessageWithParts>> read;
    read = () async {
      try {
        final page = await readHistoryAtStagedBoundary(
          currentApi,
          sessionID,
          boundary: boundary,
          isCurrent: current,
        );
        if (!current()) throw const ProductException('The session changed.');
        _genUiObserve(sessionID, page.items, complete: boundary == null);
        // Disk caching is best effort and never delays authoritative hydration.
        if (boundary == null) {
          unawaited(
            _sessionTailCache.save(
              owner.id,
              scope,
              sessionID,
              page.items,
              isCurrent: current,
            ),
          );
        }
        return page;
      } finally {
        _sessionTailReads.remove(key);
      }
    }();
    _sessionTailReads[key] = read;
    return read;
  }

  void _saveSessionInventoryPreview() {
    final owner = _connectedProfile ?? profile;
    final currentApi = api;
    final generation = _generation;
    if (owner == null || currentApi == null || !isProfileReadable(owner.id)) {
      return;
    }
    final deletionRevision = _profileDeletionRevisions[owner.id];
    unawaited(
      _sessionInventoryCache.save(
        owner.id,
        SessionInventoryCache.scopeFor(owner, directory, workspace),
        sortedSessions(),
        isCurrent: () =>
            _isCurrent(generation, currentApi) &&
            isProfileReadable(owner.id) &&
            _profileDeletionRevisions[owner.id] == deletionRevision,
      ),
    );
  }
}
