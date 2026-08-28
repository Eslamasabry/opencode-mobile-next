part of '../library_screen.dart';

class IntegrationsScreen extends StatefulWidget {
  final ConnectionController controller;
  final Future<bool> Function(Uri destination)? authorizationLauncher;

  const IntegrationsScreen({
    super.key,
    required this.controller,
    this.authorizationLauncher,
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
      _loadServers(repository),
      _loadResources(repository),
      _loadIntegrations(repository),
    ]);
  }

  Future<void> _loadServers(ProductRepository repository) async {
    final generation = ++_serverLoadGeneration;
    setState(() => _serverError = null);
    try {
      final servers = await repository.listMcpServers();
      if (mounted && generation == _serverLoadGeneration) {
        setState(() => _servers = servers);
      }
    } catch (error) {
      if (mounted && generation == _serverLoadGeneration) {
        setState(() => _serverError = error.toString());
      }
    }
  }

  Future<void> _loadResources(ProductRepository repository) async {
    final generation = ++_resourceLoadGeneration;
    setState(() => _resourceError = null);
    try {
      final resources = await repository.listMcpResources();
      if (mounted && generation == _resourceLoadGeneration) {
        setState(() => _resources = resources);
      }
    } catch (error) {
      if (mounted && generation == _resourceLoadGeneration) {
        setState(() => _resourceError = error.toString());
      }
    }
  }

  Future<void> _loadIntegrations(ProductRepository repository) async {
    final generation = ++_integrationLoadGeneration;
    setState(() => _integrationError = null);
    try {
      final integrations = await repository.listIntegrations();
      if (mounted && generation == _integrationLoadGeneration) {
        setState(() => _integrations = integrations);
      }
    } catch (error) {
      if (mounted && generation == _integrationLoadGeneration) {
        setState(() => _integrationError = error.toString());
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
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _busy.remove(server.name));
    }
  }

  Future<void> _startMcpAuthentication(
    McpServerInfo server,
    ProductRepository repository,
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

  Future<ProductRepository> _requireActionRepository() async {
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
      if (mounted) setState(() => _serverError = error.toString());
    }
  }

  Future<void> _retryIntegrations() async {
    try {
      await _loadIntegrations(await _requireActionRepository());
    } catch (error) {
      if (mounted) setState(() => _integrationError = error.toString());
    }
  }

  Future<void> _retryResources() async {
    try {
      await _loadResources(await _requireActionRepository());
    } catch (error) {
      if (mounted) setState(() => _resourceError = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('MCP and integrations'),
        actions: [
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
            const SectionLabel('MCP servers'),
            if (_serverError != null)
              _SectionLoadError(message: _serverError!, onRetry: _retryServers)
            else if (_servers == null)
              const _SectionLoading(label: 'Loading MCP servers')
            else if (_servers!.isEmpty)
              ListTile(
                leading: const Icon(Icons.hub_outlined),
                title: const Text('No MCP servers configured'),
                subtitle: const Text(
                  'Save one for this project or every project on the server.',
                ),
                trailing: TextButton(
                  onPressed: _openMcpSetup,
                  child: const Text('Add'),
                ),
              )
            else
              for (final server in _servers!) ...[
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
                  onAction: _pendingMcpOAuth?.server.name == server.name
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
            const SectionLabel('Provider connections'),
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
            else if (_integrations == null)
              const _SectionLoading(label: 'Loading provider connections')
            else if (_integrations!.isEmpty)
              const ListTile(
                leading: Icon(Icons.link_off_rounded),
                title: Text('No provider connections available'),
                subtitle: Text(
                  'This server did not return any provider integrations.',
                ),
              )
            else
              for (final presented in presentIntegrations(_integrations!))
                _ProviderIntegrationTile(
                  presented: presented,
                  subtitle: _integrationSubtitle(presented.integration),
                  busy: _busy.contains(presented.integration.id),
                  onConnect: () => _connectIntegration(presented.integration),
                  onDisconnect: () => _disconnectIntegration(presented),
                ),
            const SectionLabel('Available resources'),
            if (_resourceError != null)
              _SectionLoadError(
                message: _resourceError!,
                onRetry: _retryResources,
              )
            else if (_resources == null)
              const _SectionLoading(label: 'Loading available resources')
            else if (_resources!.isEmpty)
              const ListTile(
                leading: Icon(Icons.description_outlined),
                title: Text('No resources available'),
                subtitle: Text(
                  'Connected MCP servers have not exposed any resources.',
                ),
              )
            else
              for (final resource in _resources!)
                ListTile(
                  leading: const Icon(Icons.description_outlined),
                  title: Text(resource.name),
                  subtitle: Text(
                    '${resource.server} - ${resource.uri}',
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
          ],
        ),
      ),
    );
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
    'failed' => 'Retry',
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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        title: Text('Disconnect ${presented.name}?'),
        content: Text(
          'The stored credential will be removed from this OpenCode server. '
          'New prompts will stop using it after the provider runtime refreshes. '
          'An active response is not stopped.'
          '${environmentRemains ? '\n\nThis provider also uses the server environment, which mobile cannot remove and which will remain active.' : ''}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            key: const ValueKey('confirm-provider-disconnect'),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Disconnect provider'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

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
    final methods = integration.methods
        .where((method) => method.type == 'key' || method.type == 'oauth')
        .toList();
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
                      subtitle: Text(
                        method.type == 'key' ? 'API key' : 'OAuth',
                      ),
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
    await repository.refreshProviderRuntime();
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

  void _showError(Object error) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(error.toString())));
  }

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
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
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

