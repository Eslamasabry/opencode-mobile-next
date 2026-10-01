part of '../library_screen.dart';

/// Provider sign-in rows and their live, saved and uncertain actions.
extension _IntegrationsSignIn on _IntegrationsScreenState {
  /// Every sign-in waiting on the person, one row each (one per provider),
  /// in urgency order: this screen's live one, then the saved ones, then
  /// the uncertain starts.
  List<_SignInRow> _signInRows(List<IntegrationInfo>? integrations) {
    final l10n = _l10n;
    final controller = widget.controller;
    final rows = <_SignInRow>[];
    final seen = <String>{};
    if (_pendingOAuth case final pending?) {
      seen.add(pending.integrationID);
      final state = pending.status?.state ?? IntegrationAuthState.pending;
      final name = pending.integrationName;
      rows.add(
        _SignInRow(
          rowKey: const ValueKey('pending-provider-oauth'),
          integrationID: pending.integrationID,
          name: name,
          word: _signInWord(state),
          mark: _IntegrationsScreenState._signInMark(state),
          busy: _checkingOAuth,
          onOpen: () => unawaited(_openLiveSignIn(pending)),
          menu: _signInMenu(
            _liveSignInActions(pending),
            (choice) => _runLiveSignIn(pending, choice),
          ),
        ),
      );
    }
    for (final entry in controller.pendingIntegrationAuth) {
      if (!seen.add(entry.integrationID)) continue;
      final name = _providerName(entry.integrationID, integrations);
      final status = entry.expired
          ? IntegrationAuthState.expired
          : _signInStatus[entry.key];
      rows.add(
        _SignInRow(
          integrationID: entry.integrationID,
          name: name,
          word: _signInWord(status ?? IntegrationAuthState.pending),
          mark: _IntegrationsScreenState._signInMark(
            status ?? IntegrationAuthState.pending,
          ),
          busy: _signInBusy.contains(entry.key),
          onOpen: () => unawaited(_openSavedSignIn(entry, name)),
          menu: _signInMenu(
            _savedSignInActions(entry, name),
            (choice) => _runSavedSignIn(entry, name, choice),
          ),
        ),
      );
    }
    for (final entry in controller.uncertainIntegrationAuth) {
      if (!seen.add(entry.integrationID)) continue;
      final name = _providerName(entry.integrationID, integrations);
      rows.add(
        _SignInRow(
          integrationID: entry.integrationID,
          name: name,
          word: l10n.integrationsSignInMayNotHaveStarted,
          // Neutral: nothing waits on the person in the browser; the way
          // forward is on the row.
          mark: null,
          next: l10n.integrationsSignInUncertainNext,
          busy: false,
          onOpen: () => unawaited(_openUncertainSignIn(entry, name)),
          menu: _signInMenu(
            _uncertainSignInActions(),
            (_) => _forgetUncertain(entry, name),
          ),
        ),
      );
    }
    return rows;
  }

  String _signInWord(IntegrationAuthState state) => switch (state) {
    IntegrationAuthState.pending => _l10n.integrationsSignInWaiting,
    IntegrationAuthState.complete => _l10n.integrationsSignInComplete,
    IntegrationAuthState.failed => _l10n.integrationsSignInFailed,
    IntegrationAuthState.expired => _l10n.integrationsSignInExpired,
  };

  List<KitMenuItem> _signInMenu(
    List<(_SignInChoice, KitAction)> actions,
    Future<void> Function(_SignInChoice choice) run,
  ) => [
    for (final (choice, action) in actions)
      KitMenuItem(
        label: action.label,
        icon: switch (choice) {
          _SignInChoice.finish => AppIconography.login,
          _SignInChoice.enterCode => AppIconography.permissions,
          _SignInChoice.cancel => AppIconography.close,
          _SignInChoice.forget => AppIconography.delete,
        },
        destructive: choice == _SignInChoice.forget,
        onSelected: () => unawaited(run(choice)),
      ),
  ];

  /// This screen's own sign-in (the server cannot resume it later).
  List<(_SignInChoice, KitAction)> _liveSignInActions(
    _PendingIntegrationOAuth pending,
  ) {
    final l10n = _l10n;
    final state = pending.status?.state ?? IntegrationAuthState.pending;
    final name = pending.integrationName;
    final terminal =
        state == IntegrationAuthState.failed ||
        state == IntegrationAuthState.expired;
    return [
      if (!terminal)
        (
          _SignInChoice.finish,
          KitAction(
            key: const ValueKey('continue-provider-oauth'),
            label: l10n.integrationsFinishSigningIn(name),
            onPressed: () {},
          ),
        ),
      (
        _SignInChoice.cancel,
        KitAction(
          key: const ValueKey('cancel-provider-oauth'),
          label: l10n.integrationsCancelSignInFor(name),
          onPressed: () {},
        ),
      ),
    ];
  }

  Future<void> _openLiveSignIn(_PendingIntegrationOAuth pending) async {
    final l10n = _l10n;
    final state = pending.status?.state ?? IntegrationAuthState.pending;
    final terminal =
        state == IntegrationAuthState.failed ||
        state == IntegrationAuthState.expired;
    final choice = await _showSignInSheet(
      context,
      name: pending.integrationName,
      word: _signInWord(state),
      message: switch (state) {
        IntegrationAuthState.failed => l10n.e7LibraryAuthenticationFailed,
        IntegrationAuthState.expired =>
          l10n.e7LibraryAuthenticationAttemptExpired,
        IntegrationAuthState.complete => l10n.e7LibraryAuthenticationComplete,
        IntegrationAuthState.pending =>
          pending.launch.mode == IntegrationAuthMode.code
              ? l10n.e7LibraryReturnFromTheBrowserAndEnterThe
              : l10n.e7LibraryFinishAuthenticationInTheBrowserThenCheck,
      },
      notes: [
        if (!widget.controller.integrationAuthRecoverySupported && !terminal)
          l10n.integrationsPendingNotRecoverable,
      ],
      actions: _liveSignInActions(pending),
      primary: terminal ? null : _SignInChoice.finish,
    );
    if (choice == null || !mounted || _pendingOAuth != pending) return;
    await _runLiveSignIn(pending, choice);
  }

  Future<void> _runLiveSignIn(
    _PendingIntegrationOAuth pending,
    _SignInChoice choice,
  ) async {
    if (_pendingOAuth != pending || _checkingOAuth) return;
    if (choice == _SignInChoice.cancel) return _cancelOAuth();
    if (pending.status?.state == IntegrationAuthState.complete) {
      _set(() => _checkingOAuth = true);
      try {
        await _finishOAuth(pending);
      } catch (error) {
        if (mounted) _showError(error);
      } finally {
        if (mounted) _set(() => _checkingOAuth = false);
      }
      return;
    }
    if (pending.launch.mode == IntegrationAuthMode.code) {
      return _enterOAuthCode();
    }
    return _checkOAuth();
  }

  /// A sign-in this device saved and can pick up again.
  List<(_SignInChoice, KitAction)> _savedSignInActions(
    PendingAuthAttempt entry,
    String name,
  ) {
    final l10n = _l10n;
    final supported = widget.controller.integrationAuthRecoverySupported;
    final expired =
        entry.expired ||
        _signInStatus[entry.key] == IntegrationAuthState.expired;
    final canResume = supported && !expired;
    final codeEntry =
        entry.kind == PendingAuthKind.oauth &&
        entry.mode == IntegrationAuthMode.code;
    return [
      if (canResume)
        (
          _SignInChoice.finish,
          KitAction(
            key: const ValueKey('pending-auth-resume'),
            label: l10n.integrationsFinishSigningIn(name),
            onPressed: () {},
          ),
        ),
      if (canResume && codeEntry)
        (
          _SignInChoice.enterCode,
          KitAction(
            key: const ValueKey('pending-auth-enter-code'),
            label: l10n.integrationsEnterCodeFor(name),
            onPressed: () {},
          ),
        ),
      if (supported)
        (
          _SignInChoice.cancel,
          KitAction(
            key: const ValueKey('pending-auth-cancel'),
            label: l10n.integrationsCancelSignInFor(name),
            onPressed: () {},
          ),
        ),
      (
        _SignInChoice.forget,
        KitAction(
          key: const ValueKey('pending-auth-forget'),
          label: l10n.integrationsForgetSignInOnPhone,
          onPressed: () {},
        ),
      ),
    ];
  }

  Future<void> _openSavedSignIn(PendingAuthAttempt entry, String name) async {
    final l10n = _l10n;
    final status = entry.expired
        ? IntegrationAuthState.expired
        : _signInStatus[entry.key] ?? IntegrationAuthState.pending;
    final actions = _savedSignInActions(entry, name);
    final choice = await _showSignInSheet(
      context,
      name: name,
      word: _signInWord(status),
      message: entry.kind == PendingAuthKind.command
          ? l10n.commandAuthPending
          : l10n.pendingAuthDetail,
      notes: [
        if (!widget.controller.integrationAuthRecoverySupported)
          l10n.pendingAuthUnsupported,
        if (status == IntegrationAuthState.expired) l10n.pendingAuthExpired,
        if (status == IntegrationAuthState.failed) l10n.pendingAuthServerFailed,
      ],
      actions: actions,
      primary: actions.any((a) => a.$1 == _SignInChoice.finish)
          ? _SignInChoice.finish
          : null,
    );
    if (choice == null || !mounted) return;
    await _runSavedSignIn(entry, name, choice);
  }

  Future<void> _runSavedSignIn(
    PendingAuthAttempt entry,
    String name,
    _SignInChoice choice,
  ) async {
    if (_signInBusy.contains(entry.key)) return;
    final l10n = _l10n;
    final controller = widget.controller;
    final source = _authSourceFor(controller);
    final location = controller.locationRevision;
    final route = ModalRoute.of(context);
    bool current() =>
        mounted &&
        source == _authSourceFor(controller) &&
        controller.isProfileReadable(entry.profileID) &&
        (route?.isCurrent ?? true);
    if (choice == _SignInChoice.forget) {
      final confirmed = await _confirmForgetSignIn(name);
      if (!confirmed || !current()) return;
      try {
        await controller.forgetIntegrationAuth(
          entry,
          locationRevision: location,
        );
      } catch (_) {
        if (current()) {
          _say(l10n.pendingAuthFailed, tone: AppStatusTone.failure);
        }
      }
      return;
    }
    String? code;
    if (choice == _SignInChoice.enterCode) {
      // The one finish-sign-in dialog: the code is parsed in place, entered
      // as a secret and never echoed.
      code = await _showFinishSignInDialog(
        context,
        label: l10n.e7LibraryAuthorizationCode,
        helper: l10n.integrationsFinishSignInProviderHelper,
        parse: providerOAuthCompletionCode,
        fieldKey: const ValueKey('oauth-completion-code'),
      );
      if (code == null || !current()) return;
    }
    _set(() => _signInBusy.add(entry.key));
    try {
      final result = await controller.recoverIntegrationAuth(
        entry,
        cancel: choice == _SignInChoice.cancel,
        code: code,
        locationRevision: location,
      );
      if (!current()) return;
      _set(() => _signInStatus[entry.key] = result.state);
      switch (result.state) {
        case IntegrationAuthState.complete:
          await Future.wait([_load(), controller.refreshCatalog()]);
          if (mounted) _say(l10n.e7LibraryIsConnected(name));
        case IntegrationAuthState.pending:
          _say(l10n.pendingAuthStillPending);
        case IntegrationAuthState.failed:
          _say(l10n.pendingAuthServerFailed, tone: AppStatusTone.failure);
        case IntegrationAuthState.expired:
          _say(l10n.pendingAuthExpired, tone: AppStatusTone.failure);
      }
    } catch (_) {
      if (current()) _say(l10n.pendingAuthFailed, tone: AppStatusTone.failure);
    } finally {
      if (mounted) _set(() => _signInBusy.remove(entry.key));
    }
  }

  /// A start the server may have taken without confirming it: the app
  /// blocks a second start until the person clears it here.
  List<(_SignInChoice, KitAction)> _uncertainSignInActions() => [
    (
      _SignInChoice.forget,
      KitAction(
        key: const ValueKey('uncertain-auth-forget'),
        label: _l10n.integrationsForgetSignInOnPhone,
        onPressed: () {},
      ),
    ),
  ];

  Future<void> _openUncertainSignIn(
    ({String integrationID, PendingAuthKind kind}) entry,
    String name,
  ) async {
    final l10n = _l10n;
    final choice = await _showSignInSheet(
      context,
      name: name,
      word: l10n.integrationsSignInMayNotHaveStarted,
      message: l10n.uncertainAuthDetail,
      actions: _uncertainSignInActions(),
    );
    if (choice == null || !mounted) return;
    await _forgetUncertain(entry, name);
  }

  /// The one "forget this sign-in" question (slice-P3.11a: the uncertain
  /// start's own sheet merged into it), for a saved sign-in and for a
  /// start the server never confirmed alike: forgetting only stops this
  /// device tracking it.
  Future<bool> _confirmForgetSignIn(String name) {
    final l10n = _l10n;
    return showKitConfirm(
      context,
      icon: AppIconography.delete,
      title: l10n.pendingAuthRecoveryForgetTitle(name),
      body: l10n.pendingAuthRecoveryForgetBody,
      confirmLabel: l10n.pendingAuthForget,
      confirmKey: const ValueKey('pending-auth-forget-confirm'),
    );
  }

  Future<void> _forgetUncertain(
    ({String integrationID, PendingAuthKind kind}) entry,
    String name,
  ) async {
    final controller = widget.controller;
    final l10n = _l10n;
    final source = _authSourceFor(controller);
    final location = controller.locationRevision;
    final confirmed = await _confirmForgetSignIn(name);
    if (!confirmed || !mounted || source != _authSourceFor(controller)) {
      return;
    }
    try {
      controller.forgetUncertainIntegrationAuth(
        entry.integrationID,
        entry.kind,
        locationRevision: location,
      );
    } catch (_) {
      if (mounted) {
        _say(l10n.e7LibraryTheSignInSourceChanged, tone: AppStatusTone.failure);
      }
    }
  }

  Future<void> _openCredentials(
    IntegrationInfo integration,
    String name,
  ) async {
    if (_integrationsSource != _mcpSource) return;
    final source = _mcpSource;
    await showKitSheet<void>(
      context,
      title: _l10n.integrationsManageAccounts(name),
      icon: AppIconography.manageAccount,
      height: KitSheetHeight.full,
      body: (_) => _CredentialManagementSheet(
        controller: widget.controller,
        integrationID: integration.id,
        integrationName: name,
      ),
    );
    if (mounted && source == _mcpSource) await _retryIntegrations();
  }

  void _clearProviderSearch() {
    _providerSearch.clear();
    _set(() => _providerQuery = '');
  }
}
