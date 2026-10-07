part of '../connection.dart';

// Staged session revert (OpenCode 2).

/// A review belongs to one connection, session, and observed staged boundary.
/// Reusing it after a remote stage/clear/commit is deliberately rejected.
class SessionRevertReview {
  final String sessionID;
  final Object scope;
  final int revision;
  final SessionRevert? revert;
  const SessionRevertReview(
    this.sessionID,
    this.scope,
    this.revision,
    this.revert,
  );
}

/// [ConnectionController]'s staged session revert.
mixin _ConnectionControllerRevert on ChangeNotifier {
  ConnectionController get _self;

  final Map<String, String> sessionRevertErrors = {};

  bool get supportsStagedRevert =>
      _self.repository is StagedRevertGateway ||
      (_self.repository == null && _self.serverFlavor == ServerFlavor.v2);

  int sessionHistoryRevision(String id) => _self._historyRevisions[id] ?? 0;
  bool sessionRevertSaving(String id) => _self._revertMutations.containsKey(id);

  SessionRevertReview reviewSessionRevert(String id) =>
      _self._reviewSessionRevert(id);

  bool isRevertReviewCurrent(SessionRevertReview review) =>
      _self._isRevertReviewCurrent(review);

  Future<void> stageSessionRevert(
    SessionRevertReview review,
    String messageID, {
    required bool applyFiles,
  }) => _self._stageSessionRevert(review, messageID, applyFiles: applyFiles);

  Future<void> clearSessionRevert(SessionRevertReview review) =>
      _self._mutateSessionRevert(review);

  Future<void> commitSessionRevert(SessionRevertReview review) =>
      _self._mutateSessionRevert(review, commit: true);
}

extension _ConnectionControllerRevertImpl on ConnectionController {
  Object get _revertScope => (api, repository, _generation, locationRevision);

  /// The body of [reviewSessionRevert].
  SessionRevertReview _reviewSessionRevert(String id) => SessionRevertReview(
    id,
    _revertScope,
    sessionHistoryRevision(id),
    sessionsById[id]?.stagedRevert,
  );

  /// The body of [isRevertReviewCurrent].
  bool _isRevertReviewCurrent(SessionRevertReview review) =>
      !_disposed &&
      review.scope == _revertScope &&
      !_deletedSessionIDs.contains(review.sessionID) &&
      review.revision == sessionHistoryRevision(review.sessionID) &&
      review.revert?.fingerprint ==
          sessionsById[review.sessionID]?.stagedRevert?.fingerprint;

  void _resetSessionHistory(String id, {String? removedFrom}) {
    final cardScope = _genUiScope;
    if (cardScope != null) _genUiState.stale(cardScope, id);
    _historyRevisions[id] = sessionHistoryRevision(id) + 1;
    final owner = _connectedProfile ?? profile;
    if (owner != null) unawaited(_sessionTailCache.removeSession(owner.id, id));
    _eventBus.add(
      EventEnvelope(
        type: 'session.history.reset',
        properties: {'sessionID': id, 'removedFrom': ?removedFrom},
      ),
    );
    _notifyListeners();
  }

  /// The body of [stageSessionRevert].
  Future<void> _stageSessionRevert(
    SessionRevertReview review,
    String messageID, {
    required bool applyFiles,
  }) => _mutateSessionRevert(
    review,
    messageID: messageID,
    applyFiles: applyFiles,
  );

  Future<void> _mutateSessionRevert(
    SessionRevertReview review, {
    String? messageID,
    bool applyFiles = false,
    bool commit = false,
  }) async {
    final id = review.sessionID;
    final currentApi = api;
    final operations = repository;
    if (currentApi == null || operations is! StagedRevertGateway) {
      throw const ProductException('OpenCode is reconnecting. Try again.');
    }
    if (!isRevertReviewCurrent(review)) {
      throw const ProductException(
        'The session changed. Review the revert again.',
      );
    }
    if (sessionRevertSaving(id) || busySessions.contains(id)) {
      throw const ProductException(
        'Wait for the current session action to finish.',
      );
    }
    final token = Object();
    _revertMutations[id] = token;
    sessionRevertErrors.remove(id);
    _notifyListeners();
    var dispatched = false;
    bool sameScope() => !_disposed && review.scope == _revertScope;
    try {
      final gateway = operations as StagedRevertGateway;
      if (messageID != null &&
          (messageID.startsWith('local-') ||
              inboxItemsFor(id).any((item) => item.id == messageID) ||
              await gateway.sessionRevertPrompt(id, messageID) == null)) {
        throw const ProductException(
          'This prompt is not in the saved conversation.',
        );
      }
      // Always re-read immediately before a mutation. The API has no
      // conditional commit; this prevents known stale reviews, not a server
      // race between this read and the POST.
      final fresh = await currentApi.session(id);
      if (!isRevertReviewCurrent(review)) {
        throw const ProductException(
          'The session changed. Review the revert again.',
        );
      }
      final changed =
          fresh.stagedRevert?.fingerprint != review.revert?.fingerprint;
      sessionsById[id] = fresh;
      _markSessionChanged(id, affectsStatus: false);
      sessionDetailsErrors.remove(id);
      if (changed) _resetSessionHistory(id);
      if (changed || (fresh.reverted && fresh.stagedRevert == null)) {
        throw const ProductException(
          'The staged revert changed. Review it again.',
        );
      }
      if (messageID == null && fresh.stagedRevert == null) {
        throw const ProductException('There is no staged revert to apply.');
      }
      if (busySessions.contains(id) ||
          ((messageID != null || commit) && inboxItemsFor(id).isNotEmpty)) {
        throw const ProductException(
          'Wait for the session and queued prompts to finish.',
        );
      }
      dispatched = true;
      SessionRevert? staged;
      if (messageID != null) {
        staged = await gateway.stageSessionRevert(
          id,
          messageID,
          applyFiles: applyFiles,
        );
      } else if (commit) {
        await gateway.commitSessionRevert(id);
      } else {
        await gateway.clearSessionRevert(id);
      }
      if (!sameScope()) return;
      if (sessionHistoryRevision(id) == review.revision &&
          !_deletedSessionIDs.contains(id)) {
        sessionsById[id] = (sessionsById[id] ?? fresh).copyWith(
          stagedRevert: staged,
        );
        _markSessionChanged(id, affectsStatus: false);
        _resetSessionHistory(
          id,
          removedFrom: commit ? review.revert?.messageID : null,
        );
      }
      // Stage's 200 response is immediately usable. Clear/commit return 204:
      // reconcile metadata/usage and invalidate all transcript continuations.
      if (messageID == null) {
        await _refreshOneSession(id);
        if (sameScope() && sessionDetailsErrors[id] != null) {
          throw ProductException(sessionDetailsErrors[id]!);
        }
      }
    } catch (error) {
      if (sameScope()) {
        if (dispatched) {
          // A timeout can mean the server applied the request. Reconcile
          // before enabling any retry, and reload history even if GET fails.
          _markSessionChanged(id, affectsStatus: false);
          await _refreshOneSession(id);
          if (sameScope()) _resetSessionHistory(id);
        }
        if (sameScope()) sessionRevertErrors[id] = error.toString();
      }
      rethrow;
    } finally {
      if (identical(_revertMutations[id], token)) {
        _revertMutations.remove(id);
        if (!_disposed) _notifyListeners();
      }
    }
  }
}
