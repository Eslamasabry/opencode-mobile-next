part of '../connection.dart';

// Integration commands, credentials and MCP servers.

/// An in-memory reservation becomes uncertain as soon as start is dispatched.
/// Never retain command text, launch URLs, status messages, or raw errors here.
class _IntegrationCommandAttempt {
  final String methodID;
  final int deletionRevision;
  String? attemptID;
  Future<({IntegrationAuthLaunch launch, void Function() check})>? starting;

  _IntegrationCommandAttempt(this.methodID, this.deletionRevision);
}

/// [ConnectionController]'s integration commands, credentials and MCP servers.
mixin _ConnectionControllerIntegrationCommands on ChangeNotifier {
  ConnectionController get _self;

  /// Starts executable authentication on the selected server. The UI MUST obtain
  /// explicit user confirmation before calling this; discovery is not consent.
  /// Identical concurrent taps share a launch. An uncertain dispatch blocks
  /// retries rather than risking a second server-side process.
  Future<IntegrationAuthLaunch> startIntegrationCommand(
    String integrationID,
    String methodID, {
    String? label,
    required int locationRevision,
  }) => _self._startIntegrationCommand(
    integrationID,
    methodID,
    label: label,
    locationRevision: locationRevision,
  );

  String? pendingIntegrationCommand(
    String integrationID, {
    required int locationRevision,
  }) => _self._pendingIntegrationCommand(
    integrationID,
    locationRevision: locationRevision,
  );

  Future<IntegrationAuthStatus> integrationCommandStatus(
    String integrationID,
    String attemptID, {
    required int locationRevision,
  }) => _self._integrationCommandStatus(
    integrationID,
    attemptID,
    locationRevision: locationRevision,
  );

  Future<void> cancelIntegrationCommand(
    String integrationID,
    String attemptID, {
    required int locationRevision,
  }) => _self._cancelIntegrationCommand(
    integrationID,
    attemptID,
    locationRevision: locationRevision,
  );

  Future<void> activateIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
  }) => _self._activateIntegrationCredential(
    integrationID,
    credentialID,
    locationRevision: locationRevision,
  );

  Future<void> renameIntegrationCredential(
    String integrationID,
    String credentialID,
    String label, {
    required int locationRevision,
  }) => _self._renameIntegrationCredential(
    integrationID,
    credentialID,
    label,
    locationRevision: locationRevision,
  );

  Future<void> removeIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
  }) => _self._removeIntegrationCredential(
    integrationID,
    credentialID,
    locationRevision: locationRevision,
  );

  /// Removes only a currently listed runtime entry. The caller owns refetching
  /// its inventory; this never changes saved configuration or publishes state.
  Future<void> removeMcpServer(String name, {required int locationRevision}) =>
      _self._removeMcpServer(name, locationRevision: locationRevision);

  /// Rebuilds the selected location after a configuration patch invalidates
  /// the OpenCode instance that served it.
  Future<void> reloadAfterConfigurationChange() =>
      _self._reloadAfterConfigurationChange();
}

extension _ConnectionControllerIntegrationCommandsImpl on ConnectionController {
  /// Captures a request's selected source, not its session. Attempt ownership
  /// survives transport replacement and leaving/revisiting the same location;
  /// each request still pins the current profile objects and location revision.
  ({Object key, int deletion, void Function() check}) _integrationCommandScope(
    String integrationID,
    int expectedLocationRevision,
  ) {
    final saved = profile;
    final connected = _connectedProfile;
    if (saved == null || connected == null || integrationID.trim().isEmpty) {
      throw StateError('Command sign-in is unavailable.');
    }
    final owner = saved.id;
    final origin = saved.baseUrl;
    final connectedOrigin = connected.baseUrl;
    final location = (directory, workspace);
    final deletion = _profileDeletionRevisions[owner] ?? 0;
    final identity = (saved.username, saved.flavor);
    final connectedIdentity = (connected.username, connected.flavor);
    void check() {
      if (_disposed ||
          !isProfileReadable(owner) ||
          store.activeId != owner ||
          !identical(profile, saved) ||
          !identical(_connectedProfile, connected) ||
          connected.id != owner ||
          saved.baseUrl != origin ||
          connected.baseUrl != connectedOrigin ||
          origin != connectedOrigin ||
          (saved.username, saved.flavor) != identity ||
          (connected.username, connected.flavor) != connectedIdentity ||
          identity != connectedIdentity ||
          validateServerProfileUrl(origin) != null ||
          locationRevision != expectedLocationRevision ||
          (directory, workspace) != location ||
          (_profileDeletionRevisions[owner] ?? 0) != deletion) {
        throw StateError('The command sign-in location changed.');
      }
    }

    check();
    return (
      key: (owner, origin, identity, location, integrationID),
      deletion: deletion,
      check: check,
    );
  }

  Future<T> _withIntegrationCommandTransport<T>(
    void Function() checkScope,
    Future<T> Function(
      ServerOperationsGateway repository,
      IntegrationCommandGateway gateway,
      void Function() checkTransport,
    )
    action,
  ) async {
    try {
      checkScope();
      final actionRepository = await prepareActionRepository();
      checkScope();
      final actionApi = api;
      final generation = _generation;
      void checkTransport() {
        checkScope();
        if (_lifecycleSuspended ||
            actionApi == null ||
            actionRepository == null ||
            generation != _generation ||
            !identical(api, actionApi) ||
            !identical(repository, actionRepository) ||
            !capabilities.integrationCommandAuth) {
          throw StateError('The command sign-in connection changed.');
        }
      }

      checkTransport();
      if (actionRepository == null ||
          actionRepository is! IntegrationCommandGateway) {
        throw StateError('Command sign-in is unavailable.');
      }
      final result = await action(
        actionRepository,
        actionRepository as IntegrationCommandGateway,
        checkTransport,
      );
      checkTransport();
      return result;
    } catch (_) {
      // Command output and server errors may contain provider credentials.
      throw StateError(
        'Could not confirm command sign-in. Refresh and try again.',
      );
    }
  }

  /// The body of [startIntegrationCommand].
  Future<IntegrationAuthLaunch> _startIntegrationCommand(
    String integrationID,
    String methodID, {
    String? label,
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    if (methodID.trim().isEmpty ||
        (label != null &&
            (label.trim().isEmpty ||
                label.trim().length > 128 ||
                RegExp(r'[\x00-\x1f\x7f-\x9f]').hasMatch(label)))) {
      throw StateError(
        'Enter a valid command method and optional sign-in label.',
      );
    }
    final existing = _integrationCommandAttempts[scope.key];
    if (existing != null) {
      final starting = existing.starting;
      if (existing.methodID == methodID &&
          existing.deletionRevision == scope.deletion &&
          starting != null) {
        final result = await starting;
        scope.check();
        result.check();
        return result.launch;
      }
      throw StateError(
        'A command sign-in may already be running at this location.',
      );
    }

    if (pendingIntegrationAuth.any(
      (entry) =>
          entry.integrationID == integrationID &&
          entry.kind == PendingAuthKind.command,
    )) {
      throw StateError('Resume or cancel the existing command sign-in first.');
    }
    final record = _authRecorder(integrationID, PendingAuthKind.command);
    final releaseReservation = _reserveAuthStart();
    final pending = _IntegrationCommandAttempt(methodID, scope.deletion);
    _integrationCommandAttempts[scope.key] = pending;
    var dispatched = false;
    final operation =
        _withIntegrationCommandTransport<
          ({IntegrationAuthLaunch launch, void Function() check})
        >(scope.check, (actionRepository, gateway, checkTransport) async {
          final integrations = await actionRepository.listIntegrations();
          checkTransport();
          final valid = integrations.any(
            (integration) =>
                integration.id == integrationID &&
                integration.methods.any(
                  (method) => method.id == methodID && method.type == 'command',
                ),
          );
          if (!valid) {
            throw StateError('The command sign-in method is unavailable.');
          }
          dispatched = true;
          final launch = await gateway.startIntegrationCommand(
            integrationID,
            methodID,
            label: label?.trim(),
          );
          final recoverySaved = await record(launch);
          checkTransport();
          if (launch.attemptID.trim().isEmpty) {
            throw StateError('The command sign-in attempt is unavailable.');
          }
          // A stale completion leaves the original reservation uncertain. It
          // cannot attach an attempt to the newly selected profile/location.
          pending.attemptID = launch.attemptID;
          if (!recoverySaved) {
            throw StateError(
              'Command sign-in started, but recovery could not be saved.',
            );
          }
          return (launch: launch, check: checkTransport);
        });
    pending.starting = operation;
    try {
      final result = await operation;
      scope.check();
      result.check();
      return result.launch;
    } finally {
      releaseReservation();
      pending.starting = null;
      // Before dispatch there is only a reservation, not a possible attempt.
      if (!dispatched &&
          identical(_integrationCommandAttempts[scope.key], pending)) {
        _integrationCommandAttempts.remove(scope.key);
      }
      if (!_disposed) _notifyListeners();
    }
  }

  /// The body of [pendingIntegrationCommand].
  String? _pendingIntegrationCommand(
    String integrationID, {
    required int locationRevision,
  }) {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    scope.check();
    for (final entry in pendingIntegrationAuth) {
      if (entry.integrationID == integrationID &&
          entry.kind == PendingAuthKind.command) {
        return entry.attemptID;
      }
    }
    return _integrationCommandAttempts[scope.key]?.attemptID;
  }

  /// The body of [integrationCommandStatus].
  Future<IntegrationAuthStatus> _integrationCommandStatus(
    String integrationID,
    String attemptID, {
    required int locationRevision,
  }) async {
    for (final entry in pendingIntegrationAuth) {
      if (entry.integrationID == integrationID &&
          entry.attemptID == attemptID &&
          entry.kind == PendingAuthKind.command) {
        return recoverIntegrationAuth(
          entry,
          locationRevision: locationRevision,
        );
      }
    }
    return _integrationCommandAttemptAction<IntegrationAuthStatus>(
      integrationID,
      attemptID,
      locationRevision: locationRevision,
      action: (gateway) =>
          gateway.integrationCommandStatus(integrationID, attemptID),
      terminal: (status) => status.state != IntegrationAuthState.pending,
    );
  }

  /// The body of [cancelIntegrationCommand].
  Future<void> _cancelIntegrationCommand(
    String integrationID,
    String attemptID, {
    required int locationRevision,
  }) async {
    for (final entry in pendingIntegrationAuth) {
      if (entry.integrationID == integrationID &&
          entry.attemptID == attemptID &&
          entry.kind == PendingAuthKind.command) {
        await recoverIntegrationAuth(
          entry,
          cancel: true,
          locationRevision: locationRevision,
        );
        return;
      }
    }
    await _integrationCommandAttemptAction<bool>(
      integrationID,
      attemptID,
      locationRevision: locationRevision,
      action: (gateway) async {
        await gateway.cancelIntegrationCommand(integrationID, attemptID);
        return true;
      },
      terminal: (_) => true,
    );
  }

  Future<T> _integrationCommandAttemptAction<T>(
    String integrationID,
    String attemptID, {
    required int locationRevision,
    required Future<T> Function(IntegrationCommandGateway) action,
    required bool Function(T) terminal,
  }) async {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    final pending = _integrationCommandAttempts[scope.key];
    void checkOwnership() {
      scope.check();
      if (attemptID.trim().isEmpty ||
          pending == null ||
          pending.attemptID != attemptID ||
          pending.deletionRevision != scope.deletion ||
          !identical(_integrationCommandAttempts[scope.key], pending)) {
        throw StateError(
          'The command sign-in attempt is unavailable at this location.',
        );
      }
    }

    checkOwnership();
    void Function()? checkCompletion;
    final result = await _withIntegrationCommandTransport<T>(checkOwnership, (
      _,
      gateway,
      checkTransport,
    ) async {
      checkCompletion = checkTransport;
      final result = await action(gateway);
      checkTransport();
      return result;
    });
    checkCompletion?.call();
    if (terminal(result)) _integrationCommandAttempts.remove(scope.key);
    return result;
  }

  /// The body of [activateIntegrationCredential].
  Future<void> _activateIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
  }) => _mutateIntegrationCredential(
    integrationID,
    credentialID,
    locationRevision: locationRevision,
    mutate: (gateway) => gateway.activateCredential(credentialID),
  );

  /// The body of [renameIntegrationCredential].
  Future<void> _renameIntegrationCredential(
    String integrationID,
    String credentialID,
    String label, {
    required int locationRevision,
  }) async {
    final trimmed = label.trim();
    if (trimmed.isEmpty ||
        trimmed.length > 128 ||
        RegExp(r'[\x00-\x1f\x7f-\x9f]').hasMatch(label)) {
      throw StateError(
        'Enter a label of 1–128 characters without control characters.',
      );
    }
    return _mutateIntegrationCredential(
      integrationID,
      credentialID,
      locationRevision: locationRevision,
      mutate: (gateway) => gateway.renameCredential(credentialID, trimmed),
    );
  }

  /// The body of [removeIntegrationCredential].
  Future<void> _removeIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
  }) => _mutateIntegrationCredential(
    integrationID,
    credentialID,
    locationRevision: locationRevision,
    allowAbsent: true,
    mutate: (gateway) => gateway.removeCredential(credentialID),
  );

  /// Credential metadata is server-owned. Serialize intent, not inferred active
  /// state; neither a successful response nor removal selects a replacement.
  Future<void> _mutateIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
    bool allowAbsent = false,
    required Future<void> Function(IntegrationCredentialGateway) mutate,
  }) async {
    final saved = profile;
    final connected = _connectedProfile;
    if (saved == null ||
        connected == null ||
        integrationID.trim().isEmpty ||
        credentialID.trim().isEmpty) {
      throw StateError('Credential management is unavailable.');
    }
    final owner = saved.id;
    final savedOrigin = saved.baseUrl;
    final connectedOrigin = connected.baseUrl;
    final location = (directory, workspace);
    final deletion = _profileDeletionRevisions[owner] ?? 0;
    void checkScope() {
      if (!isProfileReadable(owner) ||
          store.activeId != owner ||
          !identical(profile, saved) ||
          !identical(_connectedProfile, connected) ||
          connected.id != owner ||
          saved.baseUrl != savedOrigin ||
          connected.baseUrl != connectedOrigin ||
          savedOrigin != connectedOrigin ||
          this.locationRevision != locationRevision ||
          (directory, workspace) != location ||
          (_profileDeletionRevisions[owner] ?? 0) != deletion) {
        throw StateError(
          'The credential location changed. Refresh and try again.',
        );
      }
    }

    checkScope();
    // Integration is deliberately not part of the key: the same credential
    // cannot be mutated concurrently through two claimed integration owners.
    // Labels are not coalesced; each queued rename keeps its own intent.
    final key = (
      owner,
      saved,
      connected,
      savedOrigin,
      locationRevision,
      location,
      deletion,
      credentialID,
    );
    final previous = _integrationCredentialMutations[key];
    void Function()? checkCompletion;
    final operation = () async {
      try {
        if (previous != null) {
          try {
            await previous;
          } catch (_) {
            // A failed predecessor does not authorize or discard this intent.
          }
          checkScope();
        }
        final actionRepository = await prepareActionRepository();
        checkScope();
        final actionApi = api;
        final generation = _generation;
        void checkTransport() {
          checkScope();
          if (_lifecycleSuspended ||
              actionApi == null ||
              actionRepository == null ||
              generation != _generation ||
              !identical(api, actionApi) ||
              !identical(repository, actionRepository)) {
            throw StateError(
              'The credential connection changed. Refresh and try again.',
            );
          }
        }

        checkCompletion = checkTransport;
        checkTransport();
        if (actionRepository == null ||
            !capabilities.integrationCredentials ||
            actionRepository is! IntegrationCredentialGateway) {
          throw StateError('Credential management is unavailable.');
        }
        final integrations = await actionRepository.listIntegrations();
        checkTransport();
        if (!capabilities.integrationCredentials) {
          throw StateError('Credential management is unavailable.');
        }
        final belongs = integrations.any(
          (integration) =>
              integration.id == integrationID &&
              integration.credentialIDs.contains(credentialID),
        );
        if (!belongs) {
          // Absence is idempotent only when the credential is absent everywhere,
          // not when it is present under a different integration.
          final presentElsewhere = integrations.any(
            (integration) => integration.credentialIDs.contains(credentialID),
          );
          if (allowAbsent && !presentElsewhere) return;
          throw StateError(
            'The credential is unavailable. Refresh and try again.',
          );
        }
        await mutate(actionRepository as IntegrationCredentialGateway);
        checkTransport();
      } catch (_) {
        checkScope();
        throw StateError(
          'Could not confirm the credential change. Refresh and try again.',
        );
      }
    }();
    _integrationCredentialMutations[key] = operation;
    try {
      await operation;
      checkScope();
      checkCompletion?.call();
    } finally {
      if (identical(_integrationCredentialMutations[key], operation)) {
        _integrationCredentialMutations.remove(key);
      }
    }
  }

  /// The body of [removeMcpServer].
  Future<void> _removeMcpServer(
    String name, {
    required int locationRevision,
  }) async {
    final saved = profile;
    final connected = _connectedProfile;
    if (saved == null || connected == null || name.trim().isEmpty) {
      throw StateError('MCP removal is unavailable.');
    }
    final owner = saved.id;
    final savedOrigin = saved.baseUrl;
    final connectedOrigin = connected.baseUrl;
    final location = (directory, workspace);
    final deletion = _profileDeletionRevisions[owner] ?? 0;
    bool currentScope() =>
        isProfileReadable(owner) &&
        store.activeId == owner &&
        identical(profile, saved) &&
        identical(_connectedProfile, connected) &&
        connected.id == owner &&
        saved.baseUrl == savedOrigin &&
        connected.baseUrl == connectedOrigin &&
        savedOrigin == connectedOrigin &&
        this.locationRevision == locationRevision &&
        (directory, workspace) == location &&
        (_profileDeletionRevisions[owner] ?? 0) == deletion;
    void checkScope() {
      if (!currentScope()) {
        throw StateError('The MCP location changed. Refresh and try again.');
      }
    }

    checkScope();
    // A request is profile/location/name-scoped, not session-scoped. Keep its
    // slot across transport recovery so a second tap cannot dispatch twice.
    final key = (
      owner,
      saved,
      connected,
      savedOrigin,
      locationRevision,
      location,
      deletion,
      name,
    );
    final pending = _mcpRemovals[key];
    if (pending != null) return pending;
    final operation = () async {
      try {
        final actionRepository = await prepareActionRepository();
        checkScope();
        final actionApi = api;
        final generation = _generation;
        void checkTransport() {
          checkScope();
          if (_lifecycleSuspended ||
              actionApi == null ||
              actionRepository == null ||
              generation != _generation ||
              !identical(api, actionApi) ||
              !identical(repository, actionRepository)) {
            throw StateError(
              'The MCP connection changed. Refresh and try again.',
            );
          }
        }

        checkTransport();
        if (actionRepository == null ||
            !capabilities.mcpRuntimeRemovals ||
            actionRepository is! McpRemovalGateway) {
          throw StateError('MCP removal is unavailable.');
        }
        final inventory = await actionRepository.listMcpServers();
        checkTransport();
        if (!capabilities.mcpRuntimeRemovals) {
          throw StateError('MCP removal is unavailable.');
        }
        if (!inventory.any((entry) => entry.name == name)) return;
        await (actionRepository as McpRemovalGateway).removeMcpServer(name);
        checkTransport();
      } catch (_) {
        // Never propagate inventory/configuration contents or raw server errors.
        checkScope();
        throw StateError(
          'Could not confirm MCP removal. Refresh and try again.',
        );
      } finally {
        _mcpRemovals.remove(key);
      }
    }();
    _mcpRemovals[key] = operation;
    return operation;
  }

  /// The body of [reloadAfterConfigurationChange].
  Future<void> _reloadAfterConfigurationChange() async {
    final currentProfile = _connectedProfile;
    if (currentProfile == null) {
      throw StateError('OpenCode is not connected.');
    }
    await _resumeLifecycleTransport(
      currentProfile,
      directory: directory,
      workspace: workspace,
    );
  }
}
