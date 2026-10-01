part of '../connection.dart';

// Integration sign-in: OAuth starts, pending auth and its recovery.

String? _authKeyProfile(Object key) => switch (key) {
  (String owner, _, _, _, _) => owner,
  (String owner, _, _, _, _, _, _) => owner,
  _ => null,
};

/// [ConnectionController]'s integration sign-in.
mixin _ConnectionControllerIntegrationAuth on ChangeNotifier {
  ConnectionController get _self;

  final _mcpRemovals = <Object, Future<void>>{};

  final _integrationCredentialMutations = <Object, Future<void>>{};

  final _integrationCommandAttempts = <Object, _IntegrationCommandAttempt>{};
  final _oauthStarts = <Object>{};
  final _oauthRequestsInFlight = <Object>{};
  final _authRecoveryActions = <Object>{};
  final _authStartReservations = <String, int>{};

  /// Dispatches whose response supplied no recoverable attempt ID. These are
  /// local uncertainty markers, never evidence that the server did not start.
  List<({String integrationID, PendingAuthKind kind})>
  get uncertainIntegrationAuth => _self._uncertainIntegrationAuth;

  /// Explicitly dismisses only an unknown local dispatch outcome. The UI must
  /// explain that this neither cancels the server attempt nor starts another.
  void forgetUncertainIntegrationAuth(
    String integrationID,
    PendingAuthKind kind, {
    required int locationRevision,
  }) => _self._forgetUncertainIntegrationAuth(
    integrationID,
    kind,
    locationRevision: locationRevision,
  );

  bool get integrationAuthRecoverySupported =>
      _self.repository is IntegrationAuthRecoveryGateway;

  bool get pendingAuthPersistenceUncertain =>
      _self.profile != null && _self._pendingAuth.uncertain(_self.profile!.id);

  bool get hasPendingAuthAtOtherSource => _self._hasPendingAuthAtOtherSource;

  Future<void> retryPendingAuthPersistence() =>
      _self._retryPendingAuthPersistence();

  Future<void> prunePendingIntegrationAuth() =>
      _self._prunePendingIntegrationAuth();

  /// Local discovery only: opening Providers never checks or restarts an attempt.
  /// Other locations stay on disk until the user returns to that exact source.
  List<PendingAuthAttempt> get pendingIntegrationAuth =>
      _self._pendingIntegrationAuth;

  /// Explicit OAuth start only. An uncertain dispatch is never auto-retried.
  Future<IntegrationAuthLaunch> startRecoverableIntegrationOAuth(
    String integrationID,
    String methodID, {
    Map<String, String> inputs = const {},
    required int locationRevision,
  }) => _self._startRecoverableIntegrationOAuth(
    integrationID,
    methodID,
    inputs: inputs,
    locationRevision: locationRevision,
  );

  /// Reacquires the action transport and restores only routing, never consent.
  /// Every network operation and completion is guarded by request scope and the
  /// current transport generation. Session selection is deliberately irrelevant.
  Future<IntegrationAuthStatus> recoverIntegrationAuth(
    PendingAuthAttempt entry, {
    bool cancel = false,
    String? code,
    required int locationRevision,
  }) => _self._recoverIntegrationAuth(
    entry,
    cancel: cancel,
    code: code,
    locationRevision: locationRevision,
  );

  /// Explicit local dismissal does not claim server cancellation or revocation.
  Future<void> forgetIntegrationAuth(
    PendingAuthAttempt entry, {
    required int locationRevision,
  }) => _self._forgetIntegrationAuth(entry, locationRevision: locationRevision);
}

extension _ConnectionControllerIntegrationAuthImpl on ConnectionController {
  void Function() _reserveAuthStart() {
    final owner = profile!.id;
    _pendingAuth.ensureCapacity(owner);
    final reserved = _authStartReservations[owner] ?? 0;
    if (_pendingAuth.entries(owner).length + reserved >=
        PendingAuthStore.maxCount) {
      throw StateError('Sign-in recovery storage is full.');
    }
    _authStartReservations[owner] = reserved + 1;
    return () {
      final remaining = (_authStartReservations[owner] ?? 1) - 1;
      if (remaining <= 0) {
        _authStartReservations.remove(owner);
      } else {
        _authStartReservations[owner] = remaining;
      }
    };
  }

  /// The body of [uncertainIntegrationAuth].
  List<({String integrationID, PendingAuthKind kind})>
  get _uncertainIntegrationAuth {
    final result = <({String integrationID, PendingAuthKind kind})>[];
    void add(Object key, PendingAuthKind kind) {
      if (key case (_, _, _, _, String integrationID)) {
        try {
          final scope = _integrationCommandScope(
            integrationID,
            locationRevision,
          );
          if (scope.key == key &&
              !pendingIntegrationAuth.any(
                (entry) =>
                    entry.integrationID == integrationID && entry.kind == kind,
              )) {
            result.add((integrationID: integrationID, kind: kind));
          }
        } catch (_) {
          // Inactive/deleted profiles and other locations have no actions here.
        }
      }
    }

    for (final key in _oauthStarts) {
      if (!_oauthRequestsInFlight.contains(key)) {
        add(key, PendingAuthKind.oauth);
      }
    }
    for (final entry in _integrationCommandAttempts.entries) {
      if (entry.value.starting == null && entry.value.attemptID == null) {
        add(entry.key, PendingAuthKind.command);
      }
    }
    return List.unmodifiable(result);
  }

  /// The body of [forgetUncertainIntegrationAuth].
  void _forgetUncertainIntegrationAuth(
    String integrationID,
    PendingAuthKind kind, {
    required int locationRevision,
  }) {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    if (!uncertainIntegrationAuth.any(
      (entry) => entry.integrationID == integrationID && entry.kind == kind,
    )) {
      throw StateError('The uncertain sign-in is no longer available.');
    }
    if (kind == PendingAuthKind.oauth) {
      _oauthStarts.remove(scope.key);
    } else {
      _integrationCommandAttempts.remove(scope.key);
    }
    if (!_disposed) _notifyListeners();
  }

  /// The body of [hasPendingAuthAtOtherSource].
  bool get _hasPendingAuthAtOtherSource {
    final owner = profile;
    if (owner == null || !isProfileReadable(owner.id)) return false;
    return _pendingAuth
        .entries(owner.id)
        .any(
          (entry) =>
              entry.origin != owner.baseUrl ||
              entry.directory != directory ||
              entry.workspace != workspace,
        );
  }

  /// The body of [retryPendingAuthPersistence].
  Future<void> _retryPendingAuthPersistence() async {
    final owner = profile?.id;
    if (owner == null ||
        !isProfileReadable(owner) ||
        !await _pendingAuth.retry(owner)) {
      throw StateError('Could not save pending sign-in recovery.');
    }
    if (!_disposed) _notifyListeners();
  }

  /// The body of [prunePendingIntegrationAuth].
  Future<void> _prunePendingIntegrationAuth() async {
    final owner = profile?.id;
    if (owner == null || !isProfileReadable(owner)) return;
    await _pendingAuth.prune(owner);
    if (!_disposed) _notifyListeners();
  }

  /// The body of [pendingIntegrationAuth].
  List<PendingAuthAttempt> get _pendingIntegrationAuth {
    final owner = profile;
    if (owner == null || !isProfileReadable(owner.id)) return const [];
    return _pendingAuth
        .entries(owner.id)
        .where(
          (entry) =>
              entry.origin == owner.baseUrl &&
              entry.directory == directory &&
              entry.workspace == workspace,
        )
        .toList(growable: false);
  }

  Future<bool> Function(IntegrationAuthLaunch) _authRecorder(
    String integrationID,
    PendingAuthKind kind,
  ) {
    final owner = profile!;
    final origin = owner.baseUrl;
    final originalDirectory = directory;
    final originalWorkspace = workspace;
    final deletion = _profileDeletionRevisions[owner.id] ?? 0;
    final deadline = DateTime.now()
        .add(PendingAuthStore.retention)
        .millisecondsSinceEpoch;
    if (PendingAuthAttempt.parse(
          PendingAuthAttempt(
            attemptID: 'pending',
            integrationID: integrationID,
            kind: kind,
            mode: IntegrationAuthMode.auto,
            profileID: owner.id,
            origin: origin,
            directory: originalDirectory,
            workspace: originalWorkspace,
            expiresAt: deadline,
          ).toJson(),
          owner.id,
        ) ==
        null) {
      throw StateError('This sign-in source cannot be safely retained.');
    }
    return (launch) async {
      // A late response may retain recovery at its ORIGINAL location, never at
      // the currently selected one. Deletion closes admission before any await.
      if (_disposed ||
          !isProfileReadable(owner.id) ||
          (_profileDeletionRevisions[owner.id] ?? 0) != deletion) {
        return false;
      }
      final expiry = launch.expiresAt;
      final saved = await _pendingAuth.save(
        PendingAuthAttempt(
          attemptID: launch.attemptID,
          integrationID: integrationID,
          kind: kind,
          mode: launch.mode,
          profileID: owner.id,
          origin: origin,
          directory: originalDirectory,
          workspace: originalWorkspace,
          expiresAt: expiry != null && expiry > 0 && expiry < deadline
              ? expiry
              : deadline,
        ),
      );
      if (!_disposed) _notifyListeners();
      return saved;
    };
  }

  /// The body of [startRecoverableIntegrationOAuth].
  Future<IntegrationAuthLaunch> _startRecoverableIntegrationOAuth(
    String integrationID,
    String methodID, {
    Map<String, String> inputs = const {},
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    _pendingAuth.ensureCapacity(profile!.id);
    if (_oauthStarts.contains(scope.key) ||
        pendingIntegrationAuth.any(
          (e) =>
              e.integrationID == integrationID &&
              e.kind == PendingAuthKind.oauth,
        )) {
      throw StateError('Resume or cancel the existing sign-in first.');
    }
    final record = _authRecorder(integrationID, PendingAuthKind.oauth);
    final releaseReservation = _reserveAuthStart();
    _oauthStarts.add(scope.key);
    _oauthRequestsInFlight.add(scope.key);
    var dispatched = false;
    try {
      final actionRepository = await prepareActionRepository();
      scope.check();
      if (actionRepository == null ||
          actionRepository is! IntegrationAuthRecoveryGateway) {
        throw StateError('Sign-in recovery is unavailable.');
      }
      final generation = _generation;
      dispatched = true;
      final launch = await actionRepository.startIntegrationOAuth(
        integrationID,
        methodID,
        inputs: inputs,
      );
      final recoverySaved = await record(launch);
      scope.check();
      if (_lifecycleSuspended ||
          _generation != generation ||
          !identical(repository, actionRepository)) {
        throw StateError('The sign-in connection changed.');
      }
      if (!recoverySaved) {
        throw StateError('Sign-in started, but recovery could not be saved.');
      }
      _oauthStarts.remove(scope.key);
      return launch;
    } catch (_) {
      throw StateError(
        'Could not confirm sign-in. Use pending sign-in recovery; do not start again.',
      );
    } finally {
      releaseReservation();
      _oauthRequestsInFlight.remove(scope.key);
      if (!dispatched) _oauthStarts.remove(scope.key);
      if (!_disposed) _notifyListeners();
    }
  }

  /// The body of [recoverIntegrationAuth].
  Future<IntegrationAuthStatus> _recoverIntegrationAuth(
    PendingAuthAttempt entry, {
    bool cancel = false,
    String? code,
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(
      entry.integrationID,
      locationRevision,
    );
    void check() {
      scope.check();
      if (!pendingIntegrationAuth.any(
        (e) =>
            e.key == entry.key &&
            e.mode == entry.mode &&
            e.expiresAt == entry.expiresAt,
      )) {
        throw StateError('The sign-in source changed.');
      }
    }

    check();
    if (!_authRecoveryActions.add(entry.key)) {
      throw StateError('Sign-in action is already running.');
    }
    try {
      final actionRepository = await prepareActionRepository();
      check();
      if (actionRepository == null ||
          actionRepository is! IntegrationAuthRecoveryGateway) {
        throw StateError('This server does not support sign-in recovery.');
      }
      final generation = _generation;
      final actionApi = api;
      void checkTransport() {
        check();
        if (_lifecycleSuspended ||
            generation != _generation ||
            !identical(repository, actionRepository) ||
            !identical(api, actionApi)) {
          throw StateError('The sign-in connection changed.');
        }
      }

      checkTransport();
      (actionRepository as IntegrationAuthRecoveryGateway)
          .restoreIntegrationAuthAttempt(
            integrationID: entry.integrationID,
            attemptID: entry.attemptID,
            command: entry.kind == PendingAuthKind.command,
            directory: entry.directory,
            workspace: entry.workspace,
          );
      // Expiration is local recovery retention, not evidence of server cancel.
      if (entry.expired && !cancel) {
        return const IntegrationAuthStatus(state: IntegrationAuthState.expired);
      }
      IntegrationAuthStatus result;
      if (entry.kind == PendingAuthKind.command) {
        if (actionRepository is! IntegrationCommandGateway ||
            !capabilities.integrationCommandAuth ||
            code != null) {
          throw StateError('Command recovery is unavailable.');
        }
        final gateway = actionRepository as IntegrationCommandGateway;
        if (cancel) {
          await gateway.cancelIntegrationCommand(
            entry.integrationID,
            entry.attemptID,
          );
          result = const IntegrationAuthStatus(
            state: IntegrationAuthState.expired,
          );
        } else {
          result = await gateway.integrationCommandStatus(
            entry.integrationID,
            entry.attemptID,
          );
        }
      } else {
        if (cancel) {
          await actionRepository.cancelIntegrationOAuth(entry.attemptID);
          result = const IntegrationAuthStatus(
            state: IntegrationAuthState.expired,
          );
        } else {
          if (code != null) {
            if (entry.mode != IntegrationAuthMode.code ||
                code.trim().isEmpty ||
                code.length > 8192) {
              throw StateError('Enter a valid authorization code.');
            }
            await actionRepository.completeIntegrationOAuth(
              entry.attemptID,
              code: code,
            );
            checkTransport();
          }
          result = await actionRepository.integrationOAuthStatus(
            entry.attemptID,
          );
        }
      }
      checkTransport();
      if (cancel || result.state == IntegrationAuthState.complete) {
        if (!await _pendingAuth.remove(entry)) {
          throw StateError('Could not save sign-in recovery.');
        }
        scope.check();
        if (_lifecycleSuspended ||
            generation != _generation ||
            !identical(repository, actionRepository) ||
            !identical(api, actionApi)) {
          throw StateError('The sign-in connection changed.');
        }
        _integrationCommandAttempts.remove(scope.key);
        _oauthStarts.remove(scope.key);
      }
      if (!_disposed) _notifyListeners();
      // Never return provider-controlled messages to recovery UI.
      return IntegrationAuthStatus(state: result.state);
    } catch (_) {
      throw StateError(
        'Could not confirm sign-in. Check pending recovery before trying again.',
      );
    } finally {
      _authRecoveryActions.remove(entry.key);
      if (!_disposed) _notifyListeners();
    }
  }

  /// The body of [forgetIntegrationAuth].
  Future<void> _forgetIntegrationAuth(
    PendingAuthAttempt entry, {
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(
      entry.integrationID,
      locationRevision,
    );
    if (!pendingIntegrationAuth.any((e) => e.key == entry.key) ||
        _authRecoveryActions.contains(entry.key)) {
      throw StateError('Sign-in is unavailable.');
    }
    if (!await _pendingAuth.remove(entry)) {
      throw StateError('Could not remove sign-in recovery.');
    }
    scope.check();
    _integrationCommandAttempts.remove(scope.key);
    _oauthStarts.remove(scope.key);
    if (!_disposed) _notifyListeners();
  }
}
