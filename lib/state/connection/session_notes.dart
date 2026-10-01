part of '../connection.dart';

// Usage statistics, session skills and session notes.

/// [ConnectionController]'s usage statistics, skills and session notes.
mixin _ConnectionControllerSessionNotes on ChangeNotifier {
  ConnectionController get _self;

  // ---------------- Forms (OpenCode 2) ----------------

  /// True when the connected server speaks the v2 forms contract.
  bool get supportsUsageStatistics => _self._supportsUsageStatistics;

  bool get supportsSessionNotes =>
      _self.repository is SessionNoteGateway &&
      (_self.repository as SessionNoteGateway).sessionNotesSupported;

  bool get supportsSessionSkills =>
      _self.repository is SessionSkillGateway &&
      (_self.repository as SessionSkillGateway).sessionSkillsSupported;
  final _skillWrites = <(int, String)>{};

  Future<void> activateSessionSkill(
    String sessionID,
    String skillID, {
    required bool resume,
    required int expectedLocation,
  }) => _self._activateSessionSkill(
    sessionID,
    skillID,
    resume: resume,
    expectedLocation: expectedLocation,
  );

  final _noteWrites = <(int, String)>{};
  final _noteRevisions = <(int, String), int>{};
  final _noteReceipts = <(int, String), bool>{};
  bool? sessionNoteReceipt(String id) =>
      _noteReceipts[(_self.locationRevision, id)];
  void dismissSessionNoteReceipt(String id) {
    _noteReceipts.remove((_self.locationRevision, id));
    notifyListeners();
  }

  bool isSessionNoteReviewCurrent(SessionNoteReview review) =>
      _self._isSessionNoteReviewCurrent(review);

  Future<SessionNoteReview> loadSessionNote(String id) =>
      _self._loadSessionNote(id);

  Future<void> saveSessionNote(SessionNoteReview review, String? value) =>
      _self._saveSessionNote(review, value);
}

extension _ConnectionControllerSessionNotesImpl on ConnectionController {
  /// The body of [supportsUsageStatistics].
  bool get _supportsUsageStatistics =>
      repository is UsageStatisticsGateway &&
      (repository as UsageStatisticsGateway).usageStatisticsSupported;

  /// The body of [activateSessionSkill].
  Future<void> _activateSessionSkill(
    String sessionID,
    String skillID, {
    required bool resume,
    required int expectedLocation,
  }) async {
    bool current() =>
        !_disposed &&
        locationRevision == expectedLocation &&
        !_deletedSessionIDs.contains(sessionID);
    if (!current()) {
      throw const SessionSkillException(SessionSkillFailure.changed);
    }
    final key = (expectedLocation, sessionID);
    if (!_skillWrites.add(key)) {
      throw const SessionSkillException(SessionSkillFailure.busy);
    }
    try {
      final transport = await prepareActionRepository();
      final currentApi = api;
      if (!current()) {
        throw const SessionSkillException(SessionSkillFailure.changed);
      }
      if (transport == null ||
          transport is! SessionSkillGateway ||
          !(transport as SessionSkillGateway).sessionSkillsSupported) {
        throw const SessionSkillException(SessionSkillFailure.unsupported);
      }
      await waitForSessionSelection(sessionID, expectedApi: currentApi);
      if (!current() || !identical(transport, repository)) {
        throw const SessionSkillException(SessionSkillFailure.changed);
      }
      final revision = sessionHistoryRevision(sessionID);
      final fresh = await transport.getSessionDetails(sessionID);
      if (!current() ||
          !identical(transport, repository) ||
          !identical(currentApi, api) ||
          revision != sessionHistoryRevision(sessionID)) {
        throw const SessionSkillException(SessionSkillFailure.changed);
      }
      if (fresh.reverted ||
          fresh.stagedRevert != null ||
          sessionsById[sessionID]?.stagedRevert != null ||
          sessionRevertSaving(sessionID)) {
        throw const SessionSkillException(SessionSkillFailure.staged);
      }
      await (transport as SessionSkillGateway).activateSessionSkill(
        sessionID,
        skillID,
        resume: resume,
      );
      if (current() && identical(transport, repository)) {
        _eventBus.add(
          EventEnvelope(
            type: 'session.skill.changed',
            properties: {'sessionID': sessionID},
          ),
        );
      }
    } finally {
      _skillWrites.remove(key);
    }
  }

  /// The body of [isSessionNoteReviewCurrent].
  bool _isSessionNoteReviewCurrent(SessionNoteReview review) =>
      !_disposed &&
      review.scope == (this, locationRevision) &&
      !_deletedSessionIDs.contains(review.sessionID) &&
      review.revision ==
          (_noteRevisions[(locationRevision, review.sessionID)] ?? 0);

  /// The body of [loadSessionNote].
  Future<SessionNoteReview> _loadSessionNote(String id) async {
    final scope = (this, locationRevision);
    final revision = _noteRevisions[(locationRevision, id)] ?? 0;
    final transport = await prepareActionRepository();
    if (scope != (this, locationRevision) ||
        _disposed ||
        _deletedSessionIDs.contains(id)) {
      throw const SessionNoteException(SessionNoteFailure.changed);
    }
    if (transport is! SessionNoteGateway ||
        !(transport as SessionNoteGateway).sessionNotesSupported) {
      throw const SessionNoteException(SessionNoteFailure.unsupported);
    }
    final value = await (transport as SessionNoteGateway).loadSessionNote(id);
    final review = SessionNoteReview(
      scope: scope,
      sessionID: id,
      value: value,
      revision: revision,
    );
    if (!isSessionNoteReviewCurrent(review) ||
        !identical(transport, repository)) {
      throw const SessionNoteException(SessionNoteFailure.changed);
    }
    return review;
  }

  /// The body of [saveSessionNote].
  Future<void> _saveSessionNote(SessionNoteReview review, String? value) async {
    if (!isSessionNoteReviewCurrent(review)) {
      throw const SessionNoteException(SessionNoteFailure.changed);
    }
    if (value != null &&
        SessionNoteGateway.encodedBytes(value) > SessionNoteGateway.maxBytes) {
      throw const SessionNoteException(SessionNoteFailure.tooLarge);
    }
    final key = (locationRevision, review.sessionID);
    if (!_noteWrites.add(key)) {
      throw const SessionNoteException(SessionNoteFailure.busy);
    }
    try {
      final transport = await prepareActionRepository();
      if (!isSessionNoteReviewCurrent(review)) {
        throw const SessionNoteException(SessionNoteFailure.changed);
      }
      if (transport is! SessionNoteGateway ||
          !(transport as SessionNoteGateway).sessionNotesSupported) {
        throw const SessionNoteException(SessionNoteFailure.unsupported);
      }
      final notes = transport as SessionNoteGateway;
      final current = await notes.loadSessionNote(review.sessionID);
      if (!isSessionNoteReviewCurrent(review) ||
          !identical(transport, repository) ||
          current != review.value) {
        throw const SessionNoteException(SessionNoteFailure.changed);
      }
      if (value == null) {
        await notes.removeSessionNote(review.sessionID);
      } else {
        await notes.saveSessionNote(review.sessionID, value);
      }
      // A receipt belongs only to the session/location that accepted the write.
      if (review.scope == (this, locationRevision) &&
          !_disposed &&
          !_deletedSessionIDs.contains(review.sessionID)) {
        _noteRevisions[key] = (_noteRevisions[key] ?? 0) + 1;
        _noteReceipts[key] = value != null;
        while (_noteReceipts.length > 128) {
          _noteReceipts.remove(_noteReceipts.keys.first);
        }
        _notifyListeners();
      }
    } finally {
      _noteWrites.remove(key);
    }
  }
}
