part of '../library_screen.dart';

/// Connecting and disconnecting providers by key or OAuth.
extension _IntegrationsConnect on _IntegrationsScreenState {
  /// Host first (the reference pattern): the sheet names where the person
  /// is going before anything opens, says what to do there, and shows any
  /// one-time device code the server sent (displayed for this launch only;
  /// never persisted or logged).
  Future<bool> _confirmAuthorizationLaunch(
    Uri destination, {
    String instructions = '',
  }) async {
    final l10n = _l10n;
    final host = destination.hasPort
        ? '${destination.host}:${destination.port}'
        : destination.host;
    return showKitConfirm(
      context,
      icon: AppIconography.browser,
      title: l10n.integrationsSignInAtHost(host),
      body: l10n.integrationsSignInBody,
      consequences: [
        if (instructions.trim().isNotEmpty)
          l10n.integrationsSignInInstructions(instructions.trim()),
      ],
      confirmLabel: l10n.e7LibraryOpenBrowser,
      cancelLabel: l10n.workCancel,
      sheetKey: const ValueKey('authorization-launch-sheet'),
      confirmKey: const ValueKey('confirm-authorization-launch'),
    );
  }

  Future<void> _disconnectIntegration(PresentedIntegration presented) async {
    final l10n = _l10n;
    final integration = presented.integration;
    final environmentRemains = integration.hasEnvironmentConnection;
    if (_busy.contains(integration.id)) return;
    Object? failure;
    final confirmed = await showKitConfirm(
      context,
      icon: AppIconography.unlink,
      title: l10n.e7LibraryDisconnect2(presented.name),
      body: l10n.integrationsDisconnectBody(presented.name),
      consequences: [
        if (environmentRemains) l10n.e7LibraryEnvironmentRemainsAfterDisconnect,
      ],
      confirmLabel: l10n.integrationsDisconnectNamed(presented.name),
      kind: KitConfirmKind.destructive,
      confirmKey: const ValueKey('confirm-provider-disconnect'),
      action: () async {
        try {
          final repository = await _requireActionRepository();
          await repository.disconnectIntegration(integration);
        } catch (error) {
          failure = error;
        }
      },
    );
    if (!confirmed || !mounted) return;
    if (failure != null) {
      _showError(failure!);
      return;
    }
    await _runIntegrationAction(integration.id, () async {
      final repository = await _requireActionRepository();
      await Future.wait([
        _loadIntegrations(repository),
        widget.controller.refreshCatalog(),
      ]);
      if (!mounted) return;
      _say(
        environmentRemains
            ? _l10n.e7LibraryCredentialRemovedServerEnvironmentRemainsActive(
                presented.name,
              )
            : _l10n.e7LibraryDisconnected2(presented.name),
      );
    });
  }

  /// "Connect {name}": one method goes straight to it; several open a
  /// titled sheet whose rows say where each one goes. [onlyCommand] runs
  /// the server sign-in command from the row menu.
  Future<bool> _connectIntegration(
    IntegrationInfo integration, {
    bool onlyCommand = false,
  }) async {
    final actionL10n = _l10n;
    final source = _mcpSource;
    final commandSupported =
        widget.controller.capabilities.integrationCommandAuth &&
        widget.controller.repository is IntegrationCommandGateway;
    final name =
        presentIntegrations([integration]).firstOrNull?.name ??
        integration.name;
    final methods = orderConnectMethods(
      keyLedConnectMethods(
        integration.id,
        integration.methods
            .where(
              (method) =>
                  (!onlyCommand &&
                      (method.type == 'key' || method.type == 'oauth')) ||
                  (commandSupported &&
                      method.type == 'command' &&
                      method.id != null),
            )
            .toList(),
      ),
    );
    if (methods.isEmpty) return false;
    final method = methods.length == 1
        ? methods.single
        : await showKitChoiceSheet<IntegrationMethodInfo>(
            context,
            title: actionL10n.e7LibraryConnect2(name),
            subtitle: actionL10n.integrationsConnectMethodSubtitle,
            sheetKey: const ValueKey('connect-method-sheet'),
            choices: [
              for (final method in methods)
                KitChoice<IntegrationMethodInfo>(
                  value: method,
                  title: method.label,
                  leading: KitRow.icon(
                    context,
                    method.type == 'key'
                        ? AppIconography.permissions
                        : method.type == 'command'
                        ? AppIconography.terminal
                        : AppIconography.browser,
                  ),
                  supporting: method.type == 'command'
                      ? actionL10n.commandAuthMethodHint
                      : connectMethodHint(method, actionL10n),
                ),
            ],
          );
    if (method == null) return true;
    if (!mounted || source != _mcpSource) return false;
    if (method.type == 'command') {
      await showKitSheet<void>(
        context,
        title: actionL10n.integrationsServerSignIn(name),
        icon: AppIconography.terminal,
        height: KitSheetHeight.full,
        body: (_) => _CommandAuthSheet(
          controller: widget.controller,
          integration: integration,
          method: method,
          name: name,
        ),
      );
      if (mounted && source == _mcpSource) await _retryIntegrations();
      return true;
    } else if (method.type == 'key') {
      await _connectWithKey(integration, method, name);
      return true;
    }
    await _connectWithOAuth(integration, method, name);
    return false;
  }

  /// The key goes straight to the server inside the dialog: it shows it is
  /// working, a rejected key keeps the dialog open with the reason under
  /// the field, and the key is never shown, logged or kept (SEC-3).
  /// The working fix for a provider signed in with an account the server
  /// cannot load (Anthropic and Google subscription sign-ins have no loader
  /// on the server): an API key replaces that sign-in.
  Future<void> _addKeyFor(PresentedIntegration presented) async {
    final method = presented.integration.methods
        .where((method) => method.type == 'key')
        .firstOrNull;
    if (method == null) return;
    await _connectWithKey(presented.integration, method, presented.name);
  }

  Future<void> _connectWithKey(
    IntegrationInfo integration,
    IntegrationMethodInfo method,
    String name,
  ) async {
    final l10n = _l10n;
    if (_busy.contains(integration.id)) return;
    final source = _mcpSource;
    final keyPage = providerKeyPageUrl(integration.id);
    // The dialog closes for the key page's confirmation and comes back
    // after it, so the person returns to where they were.
    var wantsKeyPage = false;
    final value = await showKitInputDialog(
      context,
      title: l10n.e7LibraryConnect2(name),
      label: method.label,
      helper: keyPage == null
          ? l10n.integrationsKeyHelper
          : l10n.integrationsKeyOnlyHelper(name),
      alternative: keyPage == null
          ? null
          : KitAction(
              key: const ValueKey('provider-get-key'),
              label: l10n.integrationsGetKey(name),
              icon: AppIconography.browser,
              onPressed: () => wantsKeyPage = true,
            ),
      kind: KitFieldKind.secret,
      confirmLabel: l10n.e7LibraryConnect,
      cancelLabel: l10n.workCancel,
      fieldKey: const ValueKey('provider-key-field'),
      confirmKey: const ValueKey('confirm-provider-key'),
      validate: (value) =>
          value.trim().isEmpty ? l10n.integrationsKeyEmpty : null,
      onSubmit: (value) async {
        try {
          final repository = await _requireActionRepository();
          await repository.connectIntegrationKey(integration.id, value.trim());
          return null;
        } catch (_) {
          // The server's reply is not echoed: it could quote the key.
          return l10n.integrationsKeyRejected;
        }
      },
    );
    if (!mounted || source != _mcpSource) return;
    if (value == null && wantsKeyPage && keyPage != null) {
      await openExternalLink(context, keyPage);
      if (!mounted || source != _mcpSource) return;
      await _connectWithKey(integration, method, name);
      return;
    }
    if (value == null) return;
    await _runIntegrationAction(integration.id, () async {
      await Future.wait([_load(), widget.controller.refreshCatalog()]);
      if (!mounted || source != _mcpSource) return;
      _sayKeySaved(integration.id, name);
    });
  }

  /// What the person can rely on after a key was saved: only a provider the
  /// server reports as loaded, with a model in the catalog, is called ready.
  void _sayKeySaved(String id, String name) {
    final controller = widget.controller;
    final l10n = _l10n;
    if (controller.unloadedProviderIDs.contains(id)) {
      if (controller.providerReloadWaitingOn > 0) {
        _say(l10n.integrationsKeySavedWaiting(name));
      } else if (controller.unloadedProvidersUnusable) {
        _say(
          l10n.integrationsKeySavedUnusable(name),
          tone: AppStatusTone.failure,
        );
      } else {
        _say(l10n.integrationsKeySavedPending(name));
      }
      return;
    }
    final hasModel =
        controller.catalog?.models.any((m) => m.providerID == id) ?? false;
    _say(
      hasModel
          ? l10n.integrationsKeySavedReady(name)
          : l10n.integrationsKeySavedPending(name),
    );
  }

  Future<void> _connectWithOAuth(
    IntegrationInfo integration,
    IntegrationMethodInfo method,
    String name,
  ) async {
    final actionL10n = _l10n;
    if (method.id == null) return;
    final source = _authSourceFor(widget.controller);
    final location = widget.controller.locationRevision;
    final inputs = await _oauthInputs(method, name);
    if (inputs == null ||
        !mounted ||
        source != _authSourceFor(widget.controller)) {
      return;
    }
    if (widget.controller.integrationAuthRecoverySupported) {
      await _runIntegrationAction(integration.id, () async {
        final launch = await widget.controller.startRecoverableIntegrationOAuth(
          integration.id,
          method.id!,
          inputs: inputs,
          locationRevision: location,
        );
        if (!mounted || source != _authSourceFor(widget.controller)) return;
        // Persisted before handing off to the browser. Declining/open failure
        // retains the row: only an explicit Cancel contacts the server again.
        final destination = parseAuthorizationUrl(launch.url, l10n: actionL10n);
        final confirmed = await _confirmAuthorizationLaunch(
          destination,
          instructions: launch.instructions,
        );
        if (!confirmed ||
            !mounted ||
            source != _authSourceFor(widget.controller)) {
          return;
        }
        final opened = await _openAuthorization(destination);
        if (!opened && mounted && source == _authSourceFor(widget.controller)) {
          _say(
            actionL10n.e7LibraryAuthorizationWasNotOpenedThePendingAttempt,
            tone: AppStatusTone.failure,
          );
        }
      });
      return;
    }
    await _runIntegrationAction(integration.id, () async {
      final legacySource = _mcpSource;
      final repository = await _requireActionRepository();
      if (!mounted || legacySource != _mcpSource) return;
      final launch = await repository.startIntegrationOAuth(
        integration.id,
        method.id!,
        inputs: inputs,
      );
      if (!mounted || legacySource != _mcpSource) return;
      _set(() {
        _pendingOAuth = _PendingIntegrationOAuth(
          integrationID: integration.id,
          integrationName: name,
          launch: launch,
          source: legacySource,
        );
      });
      try {
        final destination = parseAuthorizationUrl(launch.url, l10n: actionL10n);
        if (!await _confirmAuthorizationLaunch(
          destination,
          instructions: launch.instructions,
        )) {
          await _cancelOAuth();
          return;
        }
        if (!mounted || legacySource != _mcpSource) return;
        final opened = await _openAuthorization(destination);
        if (!opened) {
          throw ProductException(actionL10n.e7LibraryCouldNotOpenOAuth);
        }
      } catch (_) {
        await _cancelOAuth(showError: false);
        rethrow;
      }
    });
  }

  Future<void> _checkOAuth() async {
    final pending = _pendingOAuth;
    if (pending == null || _checkingOAuth) return;
    _set(() => _checkingOAuth = true);
    try {
      final repository = await _requireOAuthRepository(pending);
      final status = await repository.integrationOAuthStatus(
        pending.launch.attemptID,
      );
      if (!mounted ||
          _pendingOAuth != pending ||
          pending.source != _mcpSource) {
        return;
      }
      if (status.state == IntegrationAuthState.complete) {
        final completed = pending.copyWith(status: status);
        _set(() => _pendingOAuth = completed);
        await _finishOAuth(completed);
        return;
      }
      _set(() => _pendingOAuth = pending.copyWith(status: status));
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) _set(() => _checkingOAuth = false);
    }
  }

  Future<void> _enterOAuthCode() async {
    final pending = _pendingOAuth;
    if (pending == null || pending.launch.mode != IntegrationAuthMode.code) {
      return;
    }
    final l10n = _l10n;
    final instructions = pending.launch.instructions.trim();
    final code = await _showFinishSignInDialog(
      context,
      label: l10n.e7LibraryAuthorizationCode,
      helper: instructions.isNotEmpty
          ? instructions
          : l10n.integrationsFinishSignInProviderHelper,
      fieldKey: const ValueKey('oauth-completion-code'),
      confirmKey: const ValueKey('complete-provider-oauth'),
      parse: providerOAuthCompletionCode,
    );
    if (code == null || !mounted || _pendingOAuth != pending) return;
    _set(() => _checkingOAuth = true);
    try {
      final repository = await _requireOAuthRepository(pending);
      await repository.completeIntegrationOAuth(
        pending.launch.attemptID,
        code: code,
      );
      if (!mounted || pending.source != _mcpSource) return;
      final status = await repository.integrationOAuthStatus(
        pending.launch.attemptID,
      );
      if (!mounted ||
          _pendingOAuth != pending ||
          pending.source != _mcpSource) {
        return;
      }
      if (status.state == IntegrationAuthState.complete) {
        final completed = pending.copyWith(status: status);
        _set(() => _pendingOAuth = completed);
        await _finishOAuth(completed);
      } else {
        _set(() => _pendingOAuth = pending.copyWith(status: status));
      }
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) _set(() => _checkingOAuth = false);
    }
  }

  Future<void> _finishOAuth(_PendingIntegrationOAuth pending) async {
    final repository = await _requireOAuthRepository(pending);
    // §7 row 25: v2 hot-reloads its provider config, so the explicit runtime
    // refresh is skipped rather than failing a connect that already worked.
    if (widget.controller.capabilities.providerRuntimeRefresh) {
      try {
        await repository.refreshProviderRuntime();
      } on ProviderRuntimeBusyException {
        // Replies are running and a refresh would stop them; the model
        // picker loads the new provider once they finish.
      }
    }
    if (!mounted || pending.source != _mcpSource) return;
    await Future.wait([_load(), widget.controller.refreshCatalog()]);
    if (!mounted || _pendingOAuth != pending) return;
    _set(() => _pendingOAuth = null);
    if (widget.controller.unloadedProviderIDs.contains(pending.integrationID)) {
      // Saved is not loaded: say which, never "connected".
      _sayKeySaved(pending.integrationID, pending.integrationName);
    } else {
      _say(_l10n.e7LibraryIsConnected(pending.integrationName));
    }
  }

  Future<void> _cancelOAuth({bool showError = true}) async {
    final pending = _pendingOAuth;
    if (pending == null) return;
    try {
      final repository = await _requireOAuthRepository(pending);
      await repository.cancelIntegrationOAuth(pending.launch.attemptID);
      if (mounted && _pendingOAuth == pending) {
        _set(() {
          _pendingOAuth = null;
        });
      }
    } catch (error) {
      if (showError && mounted) _showError(error);
    }
  }

  Future<ServerOperationsGateway> _requireOAuthRepository(
    _PendingIntegrationOAuth pending,
  ) async {
    final actionL10n = _l10n;
    if (pending.source != _mcpSource) {
      throw ProductException(actionL10n.e7LibraryTheSignInSourceChanged);
    }
    final repository = await _requireActionRepository();
    if (!mounted || pending.source != _mcpSource) {
      throw ProductException(actionL10n.e7LibraryTheSignInSourceChanged);
    }
    return repository;
  }

  Future<ServerOperationsGateway> _requireMcpOAuthRepository(
    _PendingMcpOAuth pending,
  ) async {
    final actionL10n = _l10n;
    if (pending.source != _mcpSource) {
      throw ProductException(actionL10n.e7LibraryTheSignInSourceChanged);
    }
    final repository = await _requireActionRepository();
    if (!mounted || pending.source != _mcpSource) {
      throw ProductException(actionL10n.e7LibraryTheSignInSourceChanged);
    }
    return repository;
  }

  Future<bool> _openAuthorization(Uri destination) async {
    final source = _authSourceFor(widget.controller);
    final route = ModalRoute.of(context);
    final validated = parseAuthorizationUrl(
      destination.toString(),
      l10n: _l10n,
    );
    final result = await openExternalLink(
      context,
      validated.toString(),
      launcher: (uri) async {
        if (!mounted ||
            source != _authSourceFor(widget.controller) ||
            !(route?.isCurrent ?? true)) {
          return false;
        }
        try {
          return await (widget.authorizationLauncher?.call(uri) ??
              launchExternalUri(uri));
        } catch (_) {
          // The shared policy's generic launcher error could include a URL.
          // Auth URLs are sensitive: never forward platform exception text.
          return false;
        }
      },
    );
    return result == ExternalLinkOutcome.opened;
  }

  /// A sign-in failure in words that never quote the server's reply (it
  /// can carry a code or an address with a token).
  void _showError(Object error) => _say(
    _l10n.e7LibraryCouldNotConfirmAuthenticationReturnToThe,
    tone: AppStatusTone.failure,
  );

  Future<Map<String, String>?> _oauthInputs(
    IntegrationMethodInfo method,
    String providerName,
  ) async {
    if (method.prompts.isEmpty) return const {};
    return _showOAuthInputsSheet(
      context,
      method: method,
      providerName: providerName,
    );
  }

  Future<void> _runIntegrationAction(
    String id,
    Future<void> Function() action,
  ) async {
    if (_busy.contains(id)) return;
    _set(() => _busy.add(id));
    try {
      await action();
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) _set(() => _busy.remove(id));
    }
  }
}
