part of '../library_screen.dart';

/// The page body: provider, MCP and resource sections.
extension _IntegrationsPage on _IntegrationsScreenState {
  Widget _buildScreen(BuildContext context) {
    final l10n = _l10n;
    final tokens = KitTokens.of(context);
    final rails = EdgeInsets.symmetric(horizontal: tokens.gutter);
    final notice = _notice;
    return KitScreen(
      width: KitScreenWidth.list,
      topBar: KitTopBar(
        title: switch (widget.mode) {
          IntegrationsMode.providers => l10n.usageProviders,
          IntegrationsMode.mcp => l10n.integrationsMcpTitle,
          IntegrationsMode.all => l10n.e7LibraryMCPAndIntegrations,
        },
        actions: [
          if (_showMcp && _catalogAvailable)
            KitAction(
              key: const ValueKey('add-mcp-server'),
              label: l10n.mcpAdd,
              icon: AppIconography.add,
              onPressed: _openMcpAdd,
            ),
        ],
      ),
      body: KitRefresh(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            if (!_catalogAvailable)
              Padding(
                padding: rails,
                // Codex and Paseo keep their providers and tools to
                // themselves: say so, and which servers can, instead of a
                // load error (P7.4, explain instead of vanish).
                child: KitCapabilityExplainer.state(
                  key: const ValueKey('integrations-unavailable'),
                  capability: 'flag:serverCatalog',
                  serverName: widget.controller.profile?.name,
                  source: 'integrations',
                ),
              )
            else ...[
              if (notice != null)
                Padding(
                  padding: rails.add(
                    EdgeInsetsDirectional.only(bottom: tokens.space3),
                  ),
                  child: KitNotice(
                    key: const ValueKey('integrations-notice'),
                    message: notice.message,
                    tone: notice.tone,
                    icon: notice.tone == AppStatusTone.failure
                        ? AppIconography.warning
                        : AppIconography.checkCircle,
                    onDismiss: () => _set(() => _notice = null),
                    dismissLabel: l10n.workspaceDismissNotice,
                  ),
                ),
              // Providers lead: a new user needs a model before anything else.
              if (_showProviders) ..._providerSection(context),
              if (_showMcp) ...[
                ..._mcpSection(context),
                ..._resourceSection(context),
              ],
            ],
          ],
        ),
      ),
    );
  }

  /// A block on the list's rails with the gap below it.
  Widget _railed(Widget child) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        bottom: tokens.space3,
      ),
      child: child,
    );
  }

  List<Widget> _providerSection(BuildContext context) {
    final l10n = _l10n;
    final loaded = _integrations;
    final staleSource = loaded != null && _integrationsSource != _mcpSource;
    final integrations = loaded == null
        ? null
        : _withConfiguredProviders(loaded);
    final models = widget.controller.catalog?.models ?? const <CatalogModel>[];
    final matching = integrations == null
        ? const <PresentedIntegration>[]
        : _IntegrationsScreenState._sortedIntegrations(integrations)
              .where(
                (presented) => _providerMatchesSearch(
                  presented,
                  _providerQuery,
                  models: models.where(
                    (model) => model.providerID == presented.integration.id,
                  ),
                ),
              )
              .toList();
    final commandAuthSupported =
        widget.controller.capabilities.integrationCommandAuth &&
        widget.controller.repository is IntegrationCommandGateway;
    final credentialsSupported =
        widget.controller.capabilities.integrationCredentials &&
        widget.controller.repository is IntegrationCredentialGateway;
    // Sign-ins waiting on the person are rows of the one provider list,
    // sorted first (owner rule 2026-09-27: no state sections), never cards
    // above it.
    final signIns = _signInRows(integrations);
    final signInIDs = {for (final row in signIns) row.integrationID};
    final others = [
      for (final presented in matching)
        if (!signInIDs.contains(presented.integration.id)) presented,
    ];
    final label = widget.mode == IntegrationsMode.all
        ? l10n.usageProviders
        : null;
    final labelTerm = label == null
        ? null
        : l10n.integrationsProvidersExplanation;
    return [
      SizedBox(
        height: widget.mode == IntegrationsMode.all
            ? KitTokens.of(context).space2
            : KitTokens.of(context).space3,
      ),
      if (widget.controller.pendingAuthPersistenceUncertain)
        _railed(
          KitNotice(
            key: const ValueKey('pending-auth-save-uncertain'),
            tone: AppStatusTone.failure,
            icon: AppIconography.warning,
            message: l10n.pendingAuthSaveUncertain,
            actions: [
              KitAction(
                label: l10n.pendingAuthRetrySave,
                onPressed: () async {
                  try {
                    await widget.controller.retryPendingAuthPersistence();
                  } catch (_) {
                    if (mounted) {
                      _say(
                        _l10n.e7LibraryCouldNotSaveSignInRecovery,
                        tone: AppStatusTone.failure,
                      );
                    }
                  }
                },
              ),
            ],
          ),
        ),
      if (_pendingOAuth != null && _pendingOAuth!.source != _mcpSource)
        _railed(
          KitNotice(
            icon: AppIconography.info,
            message: l10n.e7LibraryTheSignInSourceChanged,
          ),
        ),
      if (widget.controller.hasPendingAuthAtOtherSource)
        _railed(
          KitNotice(
            key: const ValueKey('pending-auth-other-source'),
            icon: AppIconography.info,
            message: l10n.pendingAuthOtherSource,
          ),
        ),
      // Until the list itself shows, the sign-ins still lead on their own.
      if (signIns.isNotEmpty &&
          (staleSource ||
              _integrationError != null ||
              integrations == null ||
              integrations.isEmpty)) ...[
        KitRowGroup(label: label, labelTerm: labelTerm, children: signIns),
        SizedBox(height: KitTokens.of(context).space3),
      ],
      if (staleSource || _integrationError != null) ...[
        ?_loadError(
          _Section.providers,
          key: const ValueKey('providers-load-failed'),
          body: staleSource ? l10n.credentialScopeChanged : _integrationError,
        ),
      ] else if (integrations == null)
        const KitSkeletonRows(key: ValueKey('providers-loading'), count: 3)
      else if (integrations.isEmpty)
        _railed(
          KitStateView(
            key: const ValueKey('providers-empty'),
            size: KitStateSize.inline,
            icon: AppIconography.unlink,
            title: l10n.e7LibraryNoProviderConnectionsAvailable,
            body: l10n.e7LibraryThisServerDidNotReturnAnyProvider,
            tertiary: [
              KitAction(
                label: l10n.globalSessionsRefresh,
                onPressed: _retryIntegrations,
              ),
            ],
          ),
        )
      else ...[
        _railed(
          KitSearchField(
            label: l10n.e7LibrarySearchProvidersOrModels,
            controller: _providerSearch,
            fieldKey: const ValueKey('providers-search'),
            clearKey: const ValueKey('providers-search-clear'),
            resultCount: _providerQuery.trim().isEmpty ? null : matching.length,
            onChanged: (value) => _set(() => _providerQuery = value),
          ),
        ),
        if (others.isEmpty && signIns.isEmpty)
          _railed(
            KitSearchNoMatch(
              key: const ValueKey('providers-search-empty'),
              query: _providerQuery.trim(),
              what: l10n.usageProviders,
              onClear: _clearProviderSearch,
            ),
          )
        else
          KitRowGroup(
            label: label,
            labelTerm: labelTerm,
            children: [
              ...signIns,
              for (final presented in others)
                _ProviderRow(
                  commandAuthSupported: commandAuthSupported,
                  credentialsSupported: credentialsSupported,
                  presented: presented,
                  subtitle: _integrationSubtitle(presented.integration),
                  modelCount: _modelCount(presented.integration.id),
                  notLoaded: widget.controller.unloadedProviderIDs.contains(
                    presented.integration.id,
                  ),
                  notUsable: widget.controller.unloadedProvidersUnusable,
                  onAddKey: () => _addKeyFor(presented),
                  busy: _busy.contains(presented.integration.id),
                  onConnect: () => _connectIntegration(presented.integration),
                  onDisconnect: () => _disconnectIntegration(presented),
                  onManageAccounts: () =>
                      _openCredentials(presented.integration, presented.name),
                  onServerSignIn: () => _connectIntegration(
                    presented.integration,
                    onlyCommand: true,
                  ),
                ),
            ],
          ),
      ],
    ];
  }

  /// The provider's name for a sign-in row: the listed one when the
  /// provider is in the list, otherwise its presented name.
  String _providerName(String id, List<IntegrationInfo>? integrations) {
    final listed = integrations?.where((i) => i.id == id).firstOrNull;
    if (listed != null) {
      return presentIntegrations([listed]).firstOrNull?.name ?? listed.name;
    }
    return presentedProviderName(
      id,
      widget.controller.catalog?.providers ?? const <CatalogProvider>[],
    );
  }

  List<Widget> _mcpSection(BuildContext context) {
    final l10n = _l10n;
    final servers = _servers == null
        ? null
        : ([..._servers!]..sort((a, b) {
            final urgency = _mcpUrgency(
              a.status,
            ).compareTo(_mcpUrgency(b.status));
            if (urgency != 0) return urgency;
            return a.name.toLowerCase().compareTo(b.name.toLowerCase());
          }));
    final pending = _pendingMcpOAuth;
    final scopeChanged = _serversSource != null && _serversSource != _mcpSource;
    final all = widget.mode == IntegrationsMode.all;
    // The explained "MCP servers" label keeps the section gap itself; a
    // spacer before it would push the section apart by the label's target.
    final labelLeads =
        all &&
        pending == null &&
        _removalError == null &&
        _serverError == null &&
        !scopeChanged &&
        servers != null &&
        servers.isNotEmpty;
    return [
      if (!labelLeads)
        SizedBox(
          height: all
              ? KitTokens.of(context).sectionGap
              : KitTokens.of(context).space3,
        ),
      if (pending != null)
        _railed(
          _PendingMcpOAuthNotice(
            pending: pending,
            busy: _finishingMcpOAuth,
            onEnterCode: _enterMcpAuthorizationCode,
            onCancel: _cancelMcpAuthentication,
          ),
        ),
      if (_removalError != null)
        _railed(
          KitNotice.error(
            key: const ValueKey('mcp-remove-failed'),
            message: _removalError!,
            retry: KitAction(label: l10n.commonRetry, onPressed: _retryServers),
          ),
        ),
      if (_serverError != null || scopeChanged)
        ?_loadError(
          _Section.servers,
          key: const ValueKey('mcp-load-failed'),
          body: _serverError ?? l10n.mcpScopeChanged,
        ),
      if (servers == null && _serverError == null)
        const KitSkeletonRows(key: ValueKey('mcp-loading'), count: 2)
      else if (servers != null && servers.isEmpty)
        _railed(
          KitStateView(
            key: const ValueKey('mcp-empty'),
            size: KitStateSize.inline,
            icon: AppIconography.network,
            title: l10n.e7LibraryNoMCPServersConfigured,
            body: widget.controller.capabilities.mcpConfigWrites
                ? l10n.e7LibrarySaveOneForThisProjectOrEvery
                : l10n.mcpRuntimeEmpty,
            secondary: KitAction(
              key: const ValueKey('mcp-empty-add'),
              label: l10n.e7LibraryAddAnMCPServer,
              icon: AppIconography.add,
              onPressed: _openMcpAdd,
            ),
          ),
        )
      else if (servers != null)
        KitRowGroup(
          label: widget.mode == IntegrationsMode.all
              ? l10n.integrationsMcpServersLabel
              : null,
          labelTerm: widget.mode == IntegrationsMode.all
              ? l10n.e7GlossaryMcpExplanation
              : null,
          children: [
            for (final server in servers)
              _McpServerRow(
                server: server,
                statusLabel: _statusLabel(server.status),
                busy:
                    _busy.contains(server.name) ||
                    _removingMcp.contains(server.name) ||
                    (pending?.server.name == server.name && _finishingMcpOAuth),
                authorizing: pending?.server.name == server.name,
                authGated: _mcpAuthGated(server.status),
                actionsAllowed: !scopeChanged,
                canRemove: _canRemoveMcp,
                onAct: () => _action(server),
                onRemove: () => _removeMcp(server),
              ),
          ],
        ),
    ];
  }

  List<Widget> _resourceSection(BuildContext context) {
    final l10n = _l10n;
    final resources = _resources;
    // As for MCP servers: the explained label keeps its own section gap.
    final labelLeads =
        _resourceError == null && resources != null && resources.isNotEmpty;
    return [
      if (!labelLeads) SizedBox(height: KitTokens.of(context).sectionGap),
      if (_resourceError != null)
        ?_loadError(
          _Section.resources,
          key: const ValueKey('resources-load-failed'),
          body: _resourceError,
        ),
      if (resources == null && _resourceError == null)
        const KitSkeletonRows(key: ValueKey('resources-loading'), count: 2)
      else if (resources != null && resources.isEmpty)
        _railed(
          KitStateView(
            key: const ValueKey('resources-empty'),
            size: KitStateSize.inline,
            icon: AppIconography.fileText,
            title: l10n.e7LibraryNoResourcesAvailable,
            body: l10n.e7LibraryConnectedMCPServersHaveNotExposedAny,
          ),
        )
      else if (resources != null)
        KitRowGroup(
          label: l10n.e7LibraryResources,
          labelTerm: l10n.integrationsResourcesExplanation,
          children: [
            for (final resource in resources)
              KitRow(
                leading: KitRow.icon(context, AppIconography.fileText),
                title: resource.name,
                supporting: TextSpan(
                  children: [
                    TextSpan(text: '${resource.server} · '),
                    TextSpan(
                      text: resource.uri,
                      style: KitText.styleOf(context, KitTextRole.mono),
                    ),
                  ],
                ),
                supportingMaxLines: 2,
                menuLabel: resource.name,
                menu: [
                  KitMenuItem.copy(
                    label: l10n.integrationsCopyResourceAddress,
                    text: () => resource.uri,
                  ),
                ],
              ),
          ],
        ),
    ];
  }

  /// The integrations list only knows providers with a connection method;
  /// a custom provider declared in `opencode.json` (catalog source
  /// "config") never appears there, yet it is enabled and serves models.
  /// Surface every enabled catalog provider the list omits as a
  /// server-configured entry, so the Providers section matches what the
  /// model picker offers.
  List<IntegrationInfo> _withConfiguredProviders(
    List<IntegrationInfo> integrations,
  ) {
    final catalog = widget.controller.catalog;
    if (catalog == null) return integrations;
    final known = {
      for (final integration in integrations) integration.id,
      for (final integration in integrations)
        for (final connection in integration.connections) ?connection.id,
    };
    return [
      ...integrations,
      for (final provider in catalog.providers)
        if (provider.enabled &&
            provider.id.isNotEmpty &&
            !known.contains(provider.id) &&
            !known.contains(provider.integrationID))
          configuredProviderIntegration(provider, _l10n),
    ];
  }

  /// Models the catalog lists for this provider, or null until the catalog
  /// has loaded.
  int? _modelCount(String providerID) {
    final catalog = widget.controller.catalog;
    if (catalog == null) return null;
    return catalog.models
        .where((model) => model.providerID == providerID)
        .length;
  }

  /// True when the server needs interactive MCP authorization that this
  /// connection has no endpoints to run (§7 row 9).
  bool _mcpAuthGated(String status) =>
      !widget.controller.capabilities.mcpOAuth &&
      (status == 'needs_auth' || status == 'needs_client_registration');

  String _statusLabel(String status) => switch (status) {
    'connected' => _l10n.e7LibraryConnectedAndToolsAreAvailable,
    'disabled' => _l10n.e7LibraryDisconnected,
    'failed' => _l10n.e7LibraryConnectionFailed,
    'needs_auth' => _l10n.e7LibraryAuthenticationRequired,
    'needs_client_registration' => _l10n.e7LibraryClientRegistrationRequired,
    _ => status.replaceAll('_', ' '),
  };

  String _integrationSubtitle(IntegrationInfo integration) {
    final l10n = _l10n;
    final credentials = integration.connections
        .where((connection) => connection.type == 'credential')
        .length;
    if (credentials > 1) {
      // "2 accounts · Server environment": the accounts are counted, their
      // names live in Manage accounts.
      return <String>{
        l10n.integrationsAccountCount(credentials),
        for (final connection in integration.connections)
          if (connection.type == 'env')
            l10n.e7LibraryServerEnvironment
          else if (connection.type != 'credential')
            connection.label,
      }.join(' · ');
    }
    // Variable names (ANTHROPIC_API_KEY) are technical: they live in the
    // row's Details, never on its line (emulator QA B10).
    if (integration.connections.isNotEmpty) {
      return <String>{
        for (final connection in integration.connections)
          switch (connection.type) {
            'credential' => l10n.e7LibraryStoredCredential(connection.label),
            'env' => l10n.e7LibraryServerEnvironment,
            _ => connection.label,
          },
      }.join(' · ');
    }
    if (integration.methods.isEmpty) {
      return l10n.e7LibraryNoConnectionMethodsAvailable;
    }
    // How to connect, in the person's words: add a key, or the server's
    // own sign-in names ("Claude Pro/Max"); a key the server reads from
    // its environment is set up there.
    final ways = <String>{
      for (final method in keyLedConnectMethods(
        integration.id,
        integration.methods,
      ))
        if (method.type == 'key')
          l10n.integrationsConnectWithKey
        else if (method.type != 'env')
          method.label,
    };
    if (ways.isEmpty) return l10n.integrationsConnectOnServer;
    return ways.join(' · ');
  }
}
