part of '../library_screen.dart';

/// MCP server actions: remove, authenticate, add and set up.
extension _IntegrationsMcp on _IntegrationsScreenState {
  Future<void> _action(McpServerInfo server) async {
    if (_serversSource != _mcpSource ||
        _busy.contains(server.name) ||
        _removingMcp.contains(server.name)) {
      return;
    }
    // A wake-time reconnect may replace the repository while preserving the
    // user-selected profile and location. Reacquire that transport below.
    final source = _authSourceFor(widget.controller);
    if (server.status == 'connected') {
      final confirmed = await confirmDisconnectMcp(
        context,
        serverName: server.name,
      );
      // The list can reload or switch profile while the sheet is open.
      if (!confirmed ||
          !mounted ||
          source != _authSourceFor(widget.controller) ||
          _serversSource != _mcpSource ||
          _busy.contains(server.name) ||
          _removingMcp.contains(server.name)) {
        return;
      }
    }
    _set(() => _busy.add(server.name));
    try {
      final repository = await _requireActionRepository();
      if (!mounted ||
          source != _authSourceFor(widget.controller) ||
          !widget.controller.isProfileReadable(
            widget.controller.promptShelfProfileID,
          )) {
        return;
      }
      switch (server.status) {
        case 'connected':
          await repository.disconnectMcp(server.name);
          break;
        case 'needs_auth':
        case 'needs_client_registration':
          await _startMcpAuthentication(server, repository);
          return;
        default:
          await repository.connectMcp(server.name);
      }
      await _load();
    } catch (error) {
      if (mounted) {
        _say(productErrorText(error, l10n: _l10n), tone: AppStatusTone.failure);
      }
    } finally {
      if (mounted) _set(() => _busy.remove(server.name));
    }
  }

  /// "Remove {name} until restart": a runtime removal the server undoes on
  /// restart when the server is in its configuration, so it is a neutral
  /// confirmation, not a destructive one (map: integrations-remove-mcp-sheet).
  Future<void> _removeMcp(McpServerInfo server) async {
    final controller = widget.controller;
    final profile = controller.profile;
    final location = controller.locationRevision;
    final repository = controller.repository;
    final source = _mcpSource;
    final route = ModalRoute.of(context);
    var invalidated = false;
    bool currentScope() =>
        mounted &&
        !invalidated &&
        source == _mcpSource &&
        controller.isProfileReadable(controller.promptShelfProfileID) &&
        identical(widget.controller, controller) &&
        identical(controller.profile, profile) &&
        controller.locationRevision == location &&
        identical(controller.repository, repository);
    if (!_canRemoveMcp ||
        _busy.contains(server.name) ||
        _removingMcp.contains(server.name) ||
        _pendingMcpOAuth?.server.name == server.name) {
      return;
    }
    final l10n = _l10n;
    void changed() {
      if (!currentScope()) invalidated = true;
    }

    controller.addListener(changed);
    controller.profileDataChanges.addListener(changed);
    _set(() => _removingMcp.add(server.name));
    try {
      FocusManager.instance.primaryFocus?.unfocus();
      final confirmed = await showKitConfirm(
        context,
        icon: AppIconography.delete,
        title: l10n.integrationsMcpRemoveTitle(server.name),
        body: l10n.integrationsMcpRemoveBody,
        confirmLabel: l10n.integrationsMcpRemoveConfirm,
        cancelLabel: l10n.workCancel,
        sheetKey: const ValueKey('mcp-remove-confirm-sheet'),
        confirmKey: const ValueKey('confirm-mcp-remove'),
      );
      if (!confirmed ||
          !currentScope() ||
          !_canRemoveMcp ||
          !(route?.isCurrent ?? true)) {
        return;
      }
      _set(() => _removalError = null);
      try {
        await controller.removeMcpServer(
          server.name,
          locationRevision: location,
        );
      } catch (_) {
        if (!currentScope()) return;
        _set(() => _removalError = l10n.mcpRemoveFailed);
      }
      // DELETE may have reached the server even when its response was lost.
      // Keep the existing inventory until an authoritative refetch succeeds.
      if (!currentScope() ||
          !(route?.isCurrent ?? true) ||
          repository == null) {
        return;
      }
      await Future.wait([_loadServers(repository), _loadResources(repository)]);
    } catch (_) {
      if (currentScope()) _set(() => _removalError = l10n.mcpRemoveFailed);
    } finally {
      controller.removeListener(changed);
      controller.profileDataChanges.removeListener(changed);
      if (mounted) _set(() => _removingMcp.remove(server.name));
    }
  }

  bool get _canRemoveMcp =>
      _serversSource == _mcpSource &&
      widget.controller.isProfileReadable(
        widget.controller.promptShelfProfileID,
      ) &&
      _removalSupported &&
      _serversLocationRevision == widget.controller.locationRevision &&
      identical(_serversRepository, widget.controller.repository);

  bool get _removalSupported =>
      widget.controller.capabilities.mcpRuntimeRemovals &&
      widget.controller.repository is McpRemovalGateway;

  Future<void> _startMcpAuthentication(
    McpServerInfo server,
    ServerOperationsGateway repository,
  ) async {
    final actionL10n = _l10n;
    final source = _mcpSource;
    if (_pendingMcpOAuth != null) {
      throw ProductException(
        actionL10n.e7LibraryFinishOrCancelTheCurrentMCPAuthorization,
      );
    }
    final launch = await repository.startMcpAuthentication(server.name);
    if (!mounted || source != _mcpSource) return;
    final destination = parseAuthorizationUrl(
      launch.authorizationUrl.toString(),
      l10n: actionL10n,
    );
    if (!mounted || !await _confirmAuthorizationLaunch(destination)) {
      if (mounted && source == _mcpSource) {
        await repository.cancelMcpAuthentication(server.name);
      }
      return;
    }
    if (!mounted || source != _mcpSource) return;

    McpOAuthLoopbackListener? listener;
    final redirect = mcpLoopbackRedirect(destination);
    if (redirect != null) {
      try {
        listener = await McpOAuthLoopbackListener.bind(
          redirect: redirect,
          expectedState: launch.oauthState,
        );
      } catch (_) {
        // A custom callback, occupied port, or Android network policy still has
        // a manual code/URL path in the pending notice.
      }
    }
    if (!mounted || source != _mcpSource) {
      await listener?.close();
      return;
    }

    final pending = _PendingMcpOAuth(
      source: source,
      server: server,
      launch: launch,
      listener: listener,
    );
    _set(() => _pendingMcpOAuth = pending);
    if (listener != null) unawaited(_watchMcpCallback(pending));
    try {
      final opened = await _openAuthorization(destination);
      if (!opened) {
        throw ProductException(
          actionL10n.e7LibraryCouldNotOpenTheAuthorizationPage,
        );
      }
    } catch (_) {
      if (mounted && _pendingMcpOAuth == pending) {
        _set(() => _pendingMcpOAuth = null);
      }
      await listener?.close();
      if (mounted && source == _mcpSource) {
        await repository.cancelMcpAuthentication(server.name);
      }
      rethrow;
    }
  }

  Future<void> _watchMcpCallback(_PendingMcpOAuth pending) async {
    try {
      final code = await pending.listener!.code;
      if (!mounted || _pendingMcpOAuth != pending) return;
      await _completeMcpAuthentication(pending, code);
    } catch (error) {
      if (!mounted || _pendingMcpOAuth != pending || _finishingMcpOAuth) {
        return;
      }
      _showError(error);
    }
  }

  Future<void> _enterMcpAuthorizationCode() async {
    final pending = _pendingMcpOAuth;
    if (pending == null || _finishingMcpOAuth) return;
    final l10n = _l10n;
    final code = await _showFinishSignInDialog(
      context,
      label: l10n.e7LibraryCallbackURLOrCode,
      helper: l10n.integrationsFinishSignInMcpHelper,
      fieldKey: const ValueKey('mcp-oauth-code-input'),
      confirmKey: const ValueKey('complete-mcp-oauth'),
      parse: (raw) => parseMcpAuthorizationCode(
        raw,
        expectedState: pending.launch.oauthState,
      ),
    );
    if (code == null || !mounted || _pendingMcpOAuth != pending) return;
    await _completeMcpAuthentication(pending, code);
  }

  Future<void> _completeMcpAuthentication(
    _PendingMcpOAuth pending,
    String code,
  ) async {
    if (_finishingMcpOAuth || _pendingMcpOAuth != pending) return;
    _set(() => _finishingMcpOAuth = true);
    try {
      final repository = await _requireMcpOAuthRepository(pending);
      final status = await repository.completeMcpAuthentication(
        pending.server.name,
        code,
      );
      await pending.listener?.close();
      if (!mounted ||
          _pendingMcpOAuth != pending ||
          pending.source != _mcpSource) {
        return;
      }
      _set(() {
        _pendingMcpOAuth = null;
        _servers = [
          for (final server in _servers ?? const <McpServerInfo>[])
            if (server.name == status.name) status else server,
        ];
      });
      await Future.wait([_loadServers(repository), _loadResources(repository)]);
      if (!mounted) return;
      final connected = status.status == 'connected';
      _say(
        connected
            ? _l10n.e7LibraryAuthenticated(pending.server.name)
            : _l10n.e7LibraryCouldNotConfirmMCPAuthentication,
        tone: connected ? AppStatusTone.ok : AppStatusTone.failure,
      );
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) _set(() => _finishingMcpOAuth = false);
    }
  }

  Future<void> _cancelMcpAuthentication() async {
    final pending = _pendingMcpOAuth;
    if (pending == null || _finishingMcpOAuth) return;
    _set(() => _finishingMcpOAuth = true);
    await pending.listener?.close();
    try {
      final repository = await _requireMcpOAuthRepository(pending);
      await repository.cancelMcpAuthentication(pending.server.name);
      if (!mounted || _pendingMcpOAuth != pending) return;
      _set(() => _pendingMcpOAuth = null);
      await _loadServers(repository);
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) _set(() => _finishingMcpOAuth = false);
    }
  }

  /// MCP › Add (P2.4): the add sheet, then the catalogue or the form.
  Future<void> _openMcpAdd() async {
    final path = await showMcpAddSheet(
      context,
      capabilities: widget.controller.capabilities,
    );
    if (!mounted || path == null) return;
    switch (path) {
      case McpAddPath.manual:
        await _openMcpSetup();
      case McpAddPath.catalog:
        final location = widget.controller.locationRevision;
        await Navigator.of(context).push<void>(
          KitPageRoute<void>(
            builder: (_) => McpCatalogScreen(controller: widget.controller),
          ),
        );
        // Whatever the catalogue turned on or off: read the list again.
        if (mounted && widget.controller.locationRevision == location) {
          await _load();
        }
    }
  }

  Future<void> _openMcpSetup() async {
    final location = widget.controller.locationRevision;
    final runtime =
        widget.controller.capabilities.mcpRuntimeAdds &&
        !widget.controller.capabilities.mcpConfigWrites;
    final saved = await Navigator.of(context).push<bool>(
      KitPageRoute<bool>(
        builder: (_) => McpSetupScreen(controller: widget.controller),
      ),
    );
    if (!mounted ||
        saved != true ||
        widget.controller.locationRevision != location) {
      return;
    }
    await _load();
    if (!mounted || widget.controller.locationRevision != location) return;
    _say(
      runtime ? _l10n.mcpRuntimeAdded : _l10n.e7LibraryMCPServerSavedInOpenCode,
    );
  }

  Future<ServerOperationsGateway> _requireActionRepository() async {
    final actionL10n = _l10n;
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw ProductException(
      actionL10n.e7LibraryOpenCodeIsReconnectingTryAgainShortly,
    );
  }

  Future<void> _retryServers() async {
    final actionL10n = _l10n;
    final controller = widget.controller;
    final profile = controller.profile;
    final location = controller.locationRevision;
    bool currentScope() =>
        mounted &&
        identical(widget.controller, controller) &&
        identical(controller.profile, profile) &&
        controller.locationRevision == location;
    try {
      _set(() => _removalError = null);
      final repository = await controller.prepareActionRepository();
      if (!currentScope()) return;
      if (repository == null) {
        throw StateError(actionL10n.e7LibraryMCPUnavailable);
      }
      await _loadServers(repository);
    } catch (_) {
      if (currentScope()) {
        _set(() => _serverError = actionL10n.mcpLoadFailed);
      }
    }
  }

  Future<void> _retryIntegrations() async {
    try {
      await _loadIntegrations(await _requireActionRepository());
    } catch (error) {
      if (mounted) _set(() => _integrationError = productErrorText(error));
    }
  }

  Future<void> _retryResources() async {
    try {
      await _loadResources(await _requireActionRepository());
    } catch (error) {
      if (mounted) _set(() => _resourceError = productErrorText(error));
    }
  }

  bool get _providersFailed =>
      _showProviders &&
      (_integrationError != null ||
          (_integrations != null && _integrationsSource != _mcpSource));
  bool get _serversFailed =>
      _showMcp &&
      (_serverError != null ||
          (_serversSource != null && _serversSource != _mcpSource));
  bool get _resourcesFailed => _showMcp && _resourceError != null;

  /// One Try again for the page (one primary per screen, LAY-12): the
  /// first section that failed carries it, and it reloads every section
  /// that failed, so a server that could not answer at all does not ask
  /// for three separate retries.
  Future<void> _retryFailed() => Future.wait([
    if (_providersFailed) _retryIntegrations(),
    if (_serversFailed) _retryServers(),
    if (_resourcesFailed) _retryResources(),
  ]);

  /// The first section that failed, in page order; null when none did.
  _Section? get _firstFailed => _providersFailed
      ? _Section.providers
      : _serversFailed
      ? _Section.servers
      : _resourcesFailed
      ? _Section.resources
      : null;

  /// The one load error for [section]'s failure, or nothing: when several
  /// sections failed (a server that could not answer at all) the page says
  /// so once, at the first of them, with the one Try again that reloads
  /// every failed section (one primary per screen, LAY-12; nothing shown
  /// twice).
  Widget? _loadError(_Section section, {required Key key, String? body}) {
    if (_firstFailed != section) return null;
    final l10n = _l10n;
    final failed = [
      _providersFailed,
      _serversFailed,
      _resourcesFailed,
    ].where((failed) => failed).length;
    return _railed(
      KitStateView.error(
        key: key,
        title: failed > 1
            ? l10n.integrationsPageLoadFailed
            : l10n.e7LibraryCouldNotLoadThisSection,
        body: body,
        size: KitStateSize.inline,
        retry: KitAction(label: l10n.commonRetry, onPressed: _retryFailed),
      ),
    );
  }
}
