part of '../library_screen.dart';

/// Which of the two integration domains this screen shows: LLM provider
/// connections, MCP servers and their resources, or the legacy combined
/// surface.
enum IntegrationsMode { providers, mcp, all }

class IntegrationsScreen extends StatefulWidget {
  final ConnectionController controller;
  final Future<bool> Function(Uri destination)? authorizationLauncher;
  final IntegrationsMode mode;

  const IntegrationsScreen({
    super.key,
    required this.controller,
    this.authorizationLauncher,
    this.mode = IntegrationsMode.all,
  });

  @override
  State<IntegrationsScreen> createState() => _IntegrationsScreenState();
}

class _IntegrationsScreenState extends State<IntegrationsScreen>
    with WidgetsBindingObserver {
  List<McpServerInfo>? _servers;
  List<McpResourceInfo>? _resources;
  List<IntegrationInfo>? _integrations;
  String? _serverError;
  String? _resourceError;
  String? _integrationError;
  final Set<String> _busy = {};
  _PendingMcpOAuth? _pendingMcpOAuth;
  bool _finishingMcpOAuth = false;
  _PendingIntegrationOAuth? _pendingOAuth;
  bool _checkingOAuth = false;
  bool _requestCodeOnResume = false;
  int _serverLoadGeneration = 0;
  int _resourceLoadGeneration = 0;
  int _integrationLoadGeneration = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) return;
    final pending = _pendingOAuth;
    if (pending != null) {
      if (pending.launch.mode == IntegrationAuthMode.auto) {
        unawaited(_checkOAuth());
      } else if (_requestCodeOnResume) {
        _requestCodeOnResume = false;
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) unawaited(_enterOAuthCode());
        });
      }
    }
  }

  Future<void> _load() async {
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) {
      const message = 'OpenCode is reconnecting. Try again shortly.';
      setState(() {
        _serverError = message;
        _resourceError = message;
        _integrationError = message;
      });
      return;
    }
    await Future.wait([
      if (_showMcp) _loadServers(repository),
      if (_showMcp) _loadResources(repository),
      if (_showProviders) _loadIntegrations(repository),
    ]);
  }

  Future<void> _loadServers(ServerOperationsGateway repository) async {
    final generation = ++_serverLoadGeneration;
    setState(() => _serverError = null);
    try {
      final servers = await repository.listMcpServers();
      if (mounted && generation == _serverLoadGeneration) {
        setState(() => _servers = servers);
      }
    } catch (error) {
      if (mounted && generation == _serverLoadGeneration) {
        setState(() => _serverError = productErrorText(error));
      }
    }
  }

  Future<void> _loadResources(ServerOperationsGateway repository) async {
    final generation = ++_resourceLoadGeneration;
    setState(() => _resourceError = null);
    try {
      final resources = await repository.listMcpResources();
      if (mounted && generation == _resourceLoadGeneration) {
        setState(() => _resources = resources);
      }
    } catch (error) {
      if (mounted && generation == _resourceLoadGeneration) {
        setState(() => _resourceError = productErrorText(error));
      }
    }
  }

  Future<void> _loadIntegrations(ServerOperationsGateway repository) async {
    final generation = ++_integrationLoadGeneration;
    setState(() => _integrationError = null);
    try {
      final integrations = await repository.listIntegrations();
      if (mounted && generation == _integrationLoadGeneration) {
        setState(() => _integrations = integrations);
      }
    } catch (error) {
      if (mounted && generation == _integrationLoadGeneration) {
        setState(() => _integrationError = productErrorText(error));
      }
    }
  }

  Future<void> _action(McpServerInfo server) async {
    if (_busy.contains(server.name)) return;
    setState(() => _busy.add(server.name));
    try {
      final repository = await _requireActionRepository();
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
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) setState(() => _busy.remove(server.name));
    }
  }

  Future<void> _startMcpAuthentication(
    McpServerInfo server,
    ServerOperationsGateway repository,
  ) async {
    if (_pendingMcpOAuth != null) {
      throw const ProductException(
        'Finish or cancel the current MCP authorization first.',
      );
    }
    final launch = await repository.startMcpAuthentication(server.name);
    final destination = parseAuthorizationUrl(
      launch.authorizationUrl.toString(),
    );
    if (!mounted || !await _confirmAuthorizationLaunch(destination)) {
      await repository.cancelMcpAuthentication(server.name);
      return;
    }

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
        // a manual code/URL path in the pending row below.
      }
    }
    if (!mounted) {
      await listener?.close();
      await repository.cancelMcpAuthentication(server.name);
      return;
    }

    final pending = _PendingMcpOAuth(
      server: server,
      launch: launch,
      listener: listener,
    );
    setState(() => _pendingMcpOAuth = pending);
    if (listener != null) unawaited(_watchMcpCallback(pending));
    try {
      final opened = widget.authorizationLauncher == null
          ? await launchUrl(destination, mode: LaunchMode.externalApplication)
          : await widget.authorizationLauncher!(destination);
      if (!opened) {
        throw const ProductException('Could not open the authorization page');
      }
    } catch (_) {
      if (mounted && _pendingMcpOAuth == pending) {
        setState(() => _pendingMcpOAuth = null);
      }
      await listener?.close();
      await repository.cancelMcpAuthentication(server.name);
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
    final code = await showDialog<String>(
      context: context,
      builder: (context) =>
          _McpOAuthCodeDialog(expectedState: pending.launch.oauthState),
    );
    if (code == null || !mounted || _pendingMcpOAuth != pending) return;
    await _completeMcpAuthentication(pending, code);
  }

  Future<void> _completeMcpAuthentication(
    _PendingMcpOAuth pending,
    String code,
  ) async {
    if (_finishingMcpOAuth || _pendingMcpOAuth != pending) return;
    setState(() => _finishingMcpOAuth = true);
    try {
      final repository = await _requireActionRepository();
      final status = await repository.completeMcpAuthentication(
        pending.server.name,
        code,
      );
      await pending.listener?.close();
      if (!mounted || _pendingMcpOAuth != pending) return;
      setState(() {
        _pendingMcpOAuth = null;
        _servers = [
          for (final server in _servers ?? const <McpServerInfo>[])
            if (server.name == status.name) status else server,
        ];
      });
      await Future.wait([_loadServers(repository), _loadResources(repository)]);
      if (!mounted) return;
      final connected = status.status == 'connected';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            connected
                ? '${pending.server.name} authenticated'
                : status.error?.isNotEmpty == true
                ? status.error!
                : '${pending.server.name}: ${_statusLabel(status.status)}',
          ),
        ),
      );
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) setState(() => _finishingMcpOAuth = false);
    }
  }

  Future<void> _cancelMcpAuthentication() async {
    final pending = _pendingMcpOAuth;
    if (pending == null || _finishingMcpOAuth) return;
    setState(() => _finishingMcpOAuth = true);
    await pending.listener?.close();
    try {
      final repository = await _requireActionRepository();
      await repository.cancelMcpAuthentication(pending.server.name);
      if (!mounted || _pendingMcpOAuth != pending) return;
      setState(() => _pendingMcpOAuth = null);
      await _loadServers(repository);
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) setState(() => _finishingMcpOAuth = false);
    }
  }

  Future<void> _openMcpSetup() async {
    final saved = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => McpSetupScreen(controller: widget.controller),
      ),
    );
    if (!mounted || saved != true) return;
    await _load();
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('MCP server saved in OpenCode')),
    );
  }

  Future<ServerOperationsGateway> _requireActionRepository() async {
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw const ProductException(
      'OpenCode is reconnecting. Try again shortly.',
    );
  }

  Future<void> _retryServers() async {
    try {
      await _loadServers(await _requireActionRepository());
    } catch (error) {
      if (mounted) setState(() => _serverError = productErrorText(error));
    }
  }

  Future<void> _retryIntegrations() async {
    try {
      await _loadIntegrations(await _requireActionRepository());
    } catch (error) {
      if (mounted) setState(() => _integrationError = productErrorText(error));
    }
  }

  Future<void> _retryResources() async {
    try {
      await _loadResources(await _requireActionRepository());
    } catch (error) {
      if (mounted) setState(() => _resourceError = productErrorText(error));
    }
  }

  bool get _showMcp => widget.mode != IntegrationsMode.providers;
  bool get _showProviders => widget.mode != IntegrationsMode.mcp;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(switch (widget.mode) {
          IntegrationsMode.providers => 'Providers',
          IntegrationsMode.mcp => 'MCP',
          IntegrationsMode.all => 'MCP and integrations',
        }),
        actions: [
          if (_showMcp)
            IconButton(
              key: const ValueKey('add-mcp-server'),
              tooltip: 'Add MCP server',
              onPressed: _openMcpSetup,
              icon: const Icon(Icons.add_rounded),
            ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 24),
          children: [
            // Providers lead: a new user needs a model before anything else.
            if (_showProviders) ..._providerSection(),
            if (_showMcp) ...[..._mcpSection(), ..._resourceSection()],
          ],
        ),
      ),
    );
  }

  List<Widget> _providerSection() {
    final loaded = _integrations;
    final integrations = loaded == null
        ? null
        : _withConfiguredProviders(loaded);
    return [
      const _SectionHeader(
        text: 'Providers',
        description:
            'The model providers this OpenCode server can use. Connect one to start chatting.',
      ),
      if (_pendingOAuth case final pending?)
        _PendingOAuthTile(
          pending: pending,
          checking: _checkingOAuth,
          onContinue: pending.launch.mode == IntegrationAuthMode.code
              ? _enterOAuthCode
              : _checkOAuth,
          onCancel: _cancelOAuth,
        ),
      if (_integrationError != null)
        _SectionLoadError(
          message: _integrationError!,
          onRetry: _retryIntegrations,
        )
      else if (integrations == null)
        const _SectionLoading(label: 'Loading providers')
      else if (integrations.isEmpty)
        ProductInlineEmpty(
          icon: Icons.link_off_rounded,
          title: 'No provider connections available',
          message: 'This server did not return any provider integrations.',
          actionLabel: 'Refresh',
          onAction: _retryIntegrations,
        )
      else ...[
        _ProviderSummaryRow(
          connected: integrations
              .where((integration) => integration.connectionCount > 0)
              .length,
          total: integrations.length,
        ),
        for (final presented in _sortedIntegrations(integrations))
          _ProviderIntegrationTile(
            presented: presented,
            subtitle: _integrationSubtitle(presented.integration),
            modelCount: _modelCount(presented.integration.id),
            busy: _busy.contains(presented.integration.id),
            onConnect: () => _connectIntegration(presented.integration),
            onDisconnect: () => _disconnectIntegration(presented),
          ),
      ],
    ];
  }

  List<Widget> _mcpSection() {
    final servers = _servers;
    return [
      _SectionHeader(
        label: Builder(
          builder: (context) {
            final style = _SectionHeader.labelStyle(Theme.of(context));
            return Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                InfoLabel.glossary(Glossary.mcp, style: style, iconSize: 13),
                Text(' SERVERS', style: style),
              ],
            );
          },
        ),
        description:
            'Add-on servers that give the agent extra tools, like a browser or a database.',
      ),
      if (_serverError != null)
        _SectionLoadError(message: _serverError!, onRetry: _retryServers)
      else if (servers == null)
        const _SectionLoading(label: 'Loading MCP servers')
      else if (servers.isEmpty)
        ProductInlineEmpty(
          icon: Icons.hub_outlined,
          title: 'No MCP servers configured',
          message: 'Save one for this project or every project on the server.',
          actionLabel: 'Add an MCP server',
          onAction: _openMcpSetup,
        )
      else
        for (final server in servers) ...[
          _McpServerTile(
            server: server,
            subtitle: server.error?.isNotEmpty == true
                ? server.error!
                : _statusLabel(server.status),
            actionLabel: _pendingMcpOAuth?.server.name == server.name
                ? 'Authorizing'
                : _actionLabel(server.status),
            busy:
                _busy.contains(server.name) ||
                (_pendingMcpOAuth?.server.name == server.name &&
                    _finishingMcpOAuth),
            authGated: _mcpAuthGated(server.status),
            onAction:
                _pendingMcpOAuth?.server.name == server.name ||
                    _mcpAuthGated(server.status)
                ? null
                : () => _action(server),
          ),
          if (_pendingMcpOAuth case final pending?
              when pending.server.name == server.name)
            _PendingMcpOAuthTile(
              pending: pending,
              busy: _finishingMcpOAuth,
              onEnterCode: _enterMcpAuthorizationCode,
              onCancel: _cancelMcpAuthentication,
            ),
        ],
    ];
  }

  List<Widget> _resourceSection() {
    final resources = _resources;
    return [
      const _SectionHeader(
        text: 'Resources',
        description:
            'Files and data that connected MCP servers expose to the agent.',
      ),
      if (_resourceError != null)
        _SectionLoadError(message: _resourceError!, onRetry: _retryResources)
      else if (resources == null)
        const _SectionLoading(label: 'Loading available resources')
      else if (resources.isEmpty)
        ProductInlineEmpty(
          icon: Icons.description_outlined,
          title: 'No resources available',
          message: 'Connected MCP servers have not exposed any resources.',
          actionLabel: _servers?.isEmpty == true ? 'Add an MCP server' : null,
          onAction: _servers?.isEmpty == true ? _openMcpSetup : null,
        )
      else
        for (final resource in resources)
          ListTile(
            leading: const BrandTile(
              size: 28,
              child: Icon(Icons.description_outlined, size: 16),
            ),
            title: Text(resource.name),
            subtitle: Text(
              '${resource.server} - ${resource.uri}',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
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
          configuredProviderIntegration(provider),
    ];
  }

  /// Connected providers first, then alphabetical, so the ones a user can
  /// already use sit at the top of the list.
  static List<PresentedIntegration> _sortedIntegrations(
    List<IntegrationInfo> integrations,
  ) {
    final presented = presentIntegrations(integrations);
    presented.sort((a, b) {
      if (a.connected != b.connected) return a.connected ? -1 : 1;
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
    return presented;
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

  Future<bool> _confirmAuthorizationLaunch(Uri destination) async {
    final host = destination.hasPort
        ? '${destination.host}:${destination.port}'
        : destination.host;
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            scrollable: true,
            title: const Text('Open authorization page?'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'You are leaving this app to authenticate in your browser.',
                ),
                const SizedBox(height: 12),
                Text(
                  'Destination host',
                  style: Theme.of(context).textTheme.labelMedium,
                ),
                const SizedBox(height: 4),
                SelectableText(host),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton.icon(
                onPressed: () => Navigator.pop(context, true),
                icon: const Icon(Icons.open_in_browser_rounded),
                label: const Text('Open browser'),
              ),
            ],
          ),
        ) ??
        false;
  }

  /// True when the server needs interactive MCP authorization that this
  /// connection has no endpoints to run (§7 row 9).
  bool _mcpAuthGated(String status) =>
      !widget.controller.capabilities.mcpOAuth &&
      (status == 'needs_auth' || status == 'needs_client_registration');

  static String _statusLabel(String status) => switch (status) {
    'connected' => 'Connected and tools are available',
    'disabled' => 'Disconnected',
    'failed' => 'Connection failed',
    'needs_auth' => 'Authentication required',
    'needs_client_registration' => 'Client registration required',
    _ => status.replaceAll('_', ' '),
  };

  static String _actionLabel(String status) => switch (status) {
    'connected' => 'Disconnect',
    'needs_auth' || 'needs_client_registration' => 'Authenticate',
    'failed' => 'Try again',
    _ => 'Connect',
  };

  String _integrationSubtitle(IntegrationInfo integration) {
    if (integration.connections.isNotEmpty) {
      return integration.connections
          .map((connection) {
            return switch (connection.type) {
              'credential' => 'Stored credential: ${connection.label}',
              'env' => 'Server environment: ${connection.label}',
              _ => connection.label,
            };
          })
          .join(' - ');
    }
    if (integration.methods.isEmpty) return 'No connection methods available';
    return integration.methods
        .map((method) {
          if (method.type == 'env') {
            final names = method.environmentNames.join(', ');
            return names.isEmpty
                ? 'Configured on the server'
                : 'Server environment: $names';
          }
          return method.label;
        })
        .join(' - ');
  }

  Future<void> _disconnectIntegration(PresentedIntegration presented) async {
    final integration = presented.integration;
    final environmentRemains = integration.hasEnvironmentConnection;
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.link_off_rounded,
      title: 'Disconnect ${presented.name}?',
      message:
          'The stored credential will be removed from this OpenCode server. '
          'New prompts will stop using it after the provider runtime refreshes. '
          'An active response is not stopped.'
          '${environmentRemains ? '\n\nThis provider also uses the server environment, which mobile cannot remove and which will remain active.' : ''}',
      confirmLabel: 'Disconnect provider',
      confirmKey: const ValueKey('confirm-provider-disconnect'),
      destructive: true,
    );
    if (!confirmed || !mounted) return;

    await _runIntegrationAction(integration.id, () async {
      final repository = await _requireActionRepository();
      await repository.disconnectIntegration(integration);
      await Future.wait([
        _loadIntegrations(repository),
        widget.controller.refreshCatalog(),
      ]);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            environmentRemains
                ? '${presented.name} credential removed; server environment remains active'
                : '${presented.name} disconnected',
          ),
        ),
      );
    });
  }

  Future<void> _connectIntegration(IntegrationInfo integration) async {
    final methods = orderConnectMethods(
      integration.methods
          .where((method) => method.type == 'key' || method.type == 'oauth')
          .toList(),
    );
    if (methods.isEmpty) return;
    final method = methods.length == 1
        ? methods.single
        : await showModalBottomSheet<IntegrationMethodInfo>(
            context: context,
            showDragHandle: true,
            builder: (context) => SafeArea(
              child: ListView(
                shrinkWrap: true,
                children: [
                  for (final method in methods)
                    ListTile(
                      minTileHeight: 56,
                      leading: Icon(
                        method.type == 'key'
                            ? Icons.key_outlined
                            : Icons.open_in_browser_rounded,
                      ),
                      title: Text(method.label),
                      subtitle: Text(connectMethodHint(method)),
                      isThreeLine: connectMethodHint(method).length > 40,
                      onTap: () => Navigator.pop(context, method),
                    ),
                ],
              ),
            ),
          );
    if (method == null || !mounted) return;
    if (method.type == 'key') {
      await _connectWithKey(integration, method);
    } else {
      await _connectWithOAuth(integration, method);
    }
  }

  Future<void> _connectWithKey(
    IntegrationInfo integration,
    IntegrationMethodInfo method,
  ) async {
    final key = TextEditingController();
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Connect ${integration.name}'),
        content: TextField(
          controller: key,
          autofocus: true,
          obscureText: true,
          decoration: InputDecoration(labelText: method.label),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, key.text.trim()),
            child: const Text('Connect'),
          ),
        ],
      ),
    );
    key.dispose();
    if (value?.isNotEmpty != true) return;
    await _runIntegrationAction(integration.id, () async {
      final repository = await _requireActionRepository();
      await repository.connectIntegrationKey(integration.id, value!);
      await Future.wait([_load(), widget.controller.refreshCatalog()]);
    });
  }

  Future<void> _connectWithOAuth(
    IntegrationInfo integration,
    IntegrationMethodInfo method,
  ) async {
    if (method.id == null) return;
    final inputs = await _oauthInputs(method);
    if (inputs == null) return;
    await _runIntegrationAction(integration.id, () async {
      final repository = await _requireActionRepository();
      final launch = await repository.startIntegrationOAuth(
        integration.id,
        method.id!,
        inputs: inputs,
      );
      if (!mounted) return;
      setState(() {
        _pendingOAuth = _PendingIntegrationOAuth(
          integrationID: integration.id,
          integrationName: integration.name,
          launch: launch,
        );
      });
      try {
        final destination = parseAuthorizationUrl(launch.url);
        if (!await _confirmAuthorizationLaunch(destination)) {
          await _cancelOAuth();
          return;
        }
        final opened = widget.authorizationLauncher == null
            ? await launchUrl(destination, mode: LaunchMode.externalApplication)
            : await widget.authorizationLauncher!(destination);
        if (!opened) throw const ProductException('Could not open OAuth');
        _requestCodeOnResume = launch.mode == IntegrationAuthMode.code;
      } catch (_) {
        await _cancelOAuth(showError: false);
        rethrow;
      }
    });
  }

  Future<void> _checkOAuth() async {
    final pending = _pendingOAuth;
    if (pending == null || _checkingOAuth) return;
    setState(() => _checkingOAuth = true);
    try {
      final repository = await _requireActionRepository();
      final status = await repository.integrationOAuthStatus(
        pending.launch.attemptID,
      );
      if (!mounted || _pendingOAuth != pending) return;
      if (status.state == IntegrationAuthState.complete) {
        await _finishOAuth(pending);
        return;
      }
      setState(() => _pendingOAuth = pending.copyWith(status: status));
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) setState(() => _checkingOAuth = false);
    }
  }

  Future<void> _enterOAuthCode() async {
    final pending = _pendingOAuth;
    if (pending == null || pending.launch.mode != IntegrationAuthMode.code) {
      return;
    }
    final code = await showDialog<String>(
      context: context,
      builder: (context) => _OAuthCodeDialog(
        integrationName: pending.integrationName,
        instructions: pending.launch.instructions,
      ),
    );
    if (code == null || !mounted || _pendingOAuth != pending) return;
    setState(() => _checkingOAuth = true);
    try {
      final repository = await _requireActionRepository();
      await repository.completeIntegrationOAuth(
        pending.launch.attemptID,
        code: code,
      );
      final status = await repository.integrationOAuthStatus(
        pending.launch.attemptID,
      );
      if (!mounted || _pendingOAuth != pending) return;
      if (status.state == IntegrationAuthState.complete) {
        await _finishOAuth(pending);
      } else {
        setState(() => _pendingOAuth = pending.copyWith(status: status));
      }
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) setState(() => _checkingOAuth = false);
    }
  }

  Future<void> _finishOAuth(_PendingIntegrationOAuth pending) async {
    final repository = await _requireActionRepository();
    // §7 row 25: v2 hot-reloads its provider config, so the explicit runtime
    // refresh is skipped rather than failing a connect that already worked.
    if (widget.controller.capabilities.providerRuntimeRefresh) {
      await repository.refreshProviderRuntime();
    }
    await Future.wait([_load(), widget.controller.refreshCatalog()]);
    if (!mounted || _pendingOAuth != pending) return;
    setState(() => _pendingOAuth = null);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('${pending.integrationName} is connected')),
    );
  }

  Future<void> _cancelOAuth({bool showError = true}) async {
    final pending = _pendingOAuth;
    if (pending == null) return;
    try {
      final repository = await _requireActionRepository();
      await repository.cancelIntegrationOAuth(pending.launch.attemptID);
    } catch (error) {
      if (showError && mounted) _showError(error);
    } finally {
      if (mounted && _pendingOAuth == pending) {
        setState(() {
          _pendingOAuth = null;
          _requestCodeOnResume = false;
        });
      }
    }
  }

  void _showError(Object error) => showProductError(context, error);

  Future<Map<String, String>?> _oauthInputs(
    IntegrationMethodInfo method,
  ) async {
    if (method.prompts.isEmpty) return const {};
    return showDialog<Map<String, String>>(
      context: context,
      builder: (context) => _OAuthInputsDialog(method: method),
    );
  }

  Future<void> _runIntegrationAction(
    String id,
    Future<void> Function() action,
  ) async {
    if (_busy.contains(id)) return;
    setState(() => _busy.add(id));
    try {
      await action();
    } catch (error) {
      if (mounted) showProductError(context, error);
    } finally {
      if (mounted) setState(() => _busy.remove(id));
    }
  }

  @override
  void dispose() {
    _serverLoadGeneration++;
    _resourceLoadGeneration++;
    _integrationLoadGeneration++;
    if (_pendingMcpOAuth case final pending?) {
      unawaited(_disposePendingMcpAuthentication(pending));
    }
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> _disposePendingMcpAuthentication(
    _PendingMcpOAuth pending,
  ) async {
    await pending.listener?.close();
    try {
      final repository = await widget.controller.prepareActionRepository();
      await repository?.cancelMcpAuthentication(pending.server.name);
    } catch (_) {
      // Route disposal cannot present recovery UI. A later auth start replaces
      // any abandoned server-side pending transport.
    }
  }
}

/// A provider the server configured itself (an `opencode.json` entry) shown
/// as an integration: connected through the server, nothing to connect from
/// here, and no credential mobile could remove.
IntegrationInfo configuredProviderIntegration(CatalogProvider provider) =>
    IntegrationInfo(
      id: provider.id,
      name: provider.name.isEmpty ? provider.id : provider.name,
      methods: const [],
      connections: const [
        IntegrationConnectionInfo(
          type: 'config',
          label: 'Configured on the server',
        ),
      ],
      connectionCount: 1,
    );
