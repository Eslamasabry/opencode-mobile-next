import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/models.dart' show ModelRef;
import '../../api/mcp_oauth.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/file_preview.dart';
import '../widgets/product_states.dart';
import '../widgets/pickers.dart';
import 'mcp_setup_screen.dart';
import 'requests_screen.dart';
import 'settings_screen.dart';
import 'tools_screen.dart';

class LibraryScreen extends StatelessWidget {
  final ConnectionController controller;
  const LibraryScreen({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    final pending = controller.permissions.length + controller.questions.length;
    return ListView(
      padding: const EdgeInsets.only(top: 8, bottom: 24),
      children: [
        const SectionLabel('Configure'),
        _DestinationTile(
          icon: Icons.model_training_outlined,
          title: 'Models and agents',
          subtitle: 'Availability, capabilities, variants, and selection',
          onTap: () => _open(context, CatalogScreen(controller: controller)),
        ),
        _DestinationTile(
          icon: Icons.hub_outlined,
          title: 'MCP and integrations',
          subtitle: 'Connection status, authentication, and resources',
          onTap: () =>
              _open(context, IntegrationsScreen(controller: controller)),
        ),
        const SectionLabel('Discover'),
        _DestinationTile(
          icon: Icons.electric_bolt_outlined,
          title: 'Server commands',
          subtitle:
              'Configured commands, MCP prompts, and slash-capable skills',
          onTap: () => _open(context, CommandsScreen(controller: controller)),
        ),
        _DestinationTile(
          icon: Icons.build_circle_outlined,
          title: 'Tools and capabilities',
          subtitle: 'Callable tools for the active provider and model',
          onTap: () => _open(context, ToolsScreen(controller: controller)),
        ),
        _DestinationTile(
          icon: Icons.extension_outlined,
          title: 'Skills',
          subtitle: 'Inspect available project and global skills',
          onTap: () => _open(context, SkillsScreen(controller: controller)),
        ),
        _DestinationTile(
          icon: Icons.bookmarks_outlined,
          title: 'References',
          subtitle: 'Project context available to OpenCode',
          onTap: () => _open(context, ReferencesScreen(controller: controller)),
        ),
        const SectionLabel('Manage'),
        _DestinationTile(
          icon: Icons.notifications_active_outlined,
          title: 'Pending requests',
          subtitle: pending == 0
              ? 'Nothing needs attention'
              : '$pending awaiting response',
          badge: pending == 0 ? null : '$pending',
          onTap: () => _open(context, RequestsScreen(controller: controller)),
        ),
        _DestinationTile(
          icon: Icons.settings_outlined,
          title: 'Settings and server',
          subtitle: 'Connection, health, version, and experimental features',
          onTap: () => _open(context, SettingsScreen(controller: controller)),
        ),
      ],
    );
  }

  static void _open(BuildContext context, Widget screen) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
  }
}

class _DestinationTile extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String? badge;
  final VoidCallback onTap;

  const _DestinationTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
    this.badge,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListTile(
      minTileHeight: 68,
      leading: Icon(icon, color: theme.colorScheme.onSurfaceVariant),
      title: Text(title),
      subtitle: Text(subtitle, maxLines: 2, overflow: TextOverflow.ellipsis),
      trailing: badge == null
          ? const Icon(Icons.chevron_right_rounded)
          : Badge(label: Text(badge!)),
      onTap: onTap,
    );
  }
}

class CatalogScreen extends StatefulWidget {
  final ConnectionController controller;
  const CatalogScreen({super.key, required this.controller});

  @override
  State<CatalogScreen> createState() => _CatalogScreenState();
}

class _CatalogScreenState extends State<CatalogScreen> {
  CatalogSnapshot? _catalog;
  // ignore: unused_field
  String? _error;
  String _query = '';
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    if (widget.controller.catalog == null) {
      widget.controller.refreshCatalog();
    }
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw StateError('OpenCode is reconnecting.');
      }
      final value = await repository.loadCatalog();
      if (mounted && generation == _loadGeneration) {
        setState(() => _catalog = value);
      }
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = error.toString());
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Models and agents')),
      body: ModelCatalogView(controller: widget.controller, showHeader: false),
    );
  }

  // ignore: unused_element
  Widget _models() {
    final models = _catalog!.models.where((model) {
      final query = _query.toLowerCase();
      return query.isEmpty ||
          model.name.toLowerCase().contains(query) ||
          model.id.toLowerCase().contains(query) ||
          model.providerID.toLowerCase().contains(query);
    }).toList();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(12),
          child: TextField(
            decoration: const InputDecoration(
              hintText: 'Search models',
              prefixIcon: Icon(Icons.search_rounded),
              border: OutlineInputBorder(),
              isDense: true,
            ),
            onChanged: (value) => setState(() => _query = value),
          ),
        ),
        Expanded(
          child: models.isEmpty
              ? RefreshIndicator(
                  onRefresh: _load,
                  child: const ProductEmptyState(
                    icon: Icons.search_off_rounded,
                    title: 'No matching models',
                    message: 'Try another provider or model name.',
                  ),
                )
              : RefreshIndicator(
                  onRefresh: _load,
                  child: ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    itemCount: models.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final model = models[index];
                      final selected =
                          widget.controller.selectedModel?.providerID ==
                              model.providerID &&
                          widget.controller.selectedModel?.modelID == model.id;
                      return ListTile(
                        enabled: model.enabled,
                        title: Text(model.name),
                        subtitle: Text(
                          '${model.providerID}/${model.id}\n${_compactNumber(model.contextLimit)} context - ${_compactNumber(model.outputLimit)} output',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 12),
                        ),
                        isThreeLine: true,
                        trailing: selected
                            ? Icon(
                                Icons.check_circle_rounded,
                                color: Theme.of(context).colorScheme.primary,
                              )
                            : null,
                        onTap: () => _modelDetails(model),
                      );
                    },
                  ),
                ),
        ),
      ],
    );
  }

  // ignore: unused_element
  Widget _providers() {
    if (_catalog!.providers.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: const ProductEmptyState(
          icon: Icons.cloud_off_outlined,
          title: 'No providers connected',
          message: 'Connect a provider on the OpenCode server to use models.',
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: _catalog!.providers.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final provider = _catalog!.providers[index];
          final count = _catalog!.models
              .where((model) => model.providerID == provider.id)
              .length;
          return ListTile(
            leading: Icon(
              provider.enabled
                  ? Icons.cloud_done_outlined
                  : Icons.cloud_off_outlined,
            ),
            title: Text(provider.name),
            subtitle: Text(
              '$count available models\nAuthentication is managed under MCP and integrations.',
              maxLines: 2,
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) =>
                    IntegrationsScreen(controller: widget.controller),
              ),
            ),
          );
        },
      ),
    );
  }

  // ignore: unused_element
  Widget _agents() {
    final agents = _catalog!.agents.where((agent) => !agent.hidden).toList();
    if (agents.isEmpty) {
      return RefreshIndicator(
        onRefresh: _load,
        child: const ProductEmptyState(
          icon: Icons.support_agent_outlined,
          title: 'No agents available',
          message: 'No visible agents were returned for this workspace.',
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: agents.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final agent = agents[index];
          final selected = widget.controller.selectedAgent == agent.id;
          return ListTile(
            leading: const Icon(Icons.support_agent_outlined),
            title: Text(agent.id),
            subtitle: Text(
              [
                agent.mode,
                if (agent.description?.isNotEmpty == true) agent.description!,
              ].join(' - '),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: selected ? const Icon(Icons.check_rounded) : null,
            onTap: () => widget.controller.selectAgent(agent.id).then((_) {
              if (mounted) setState(() {});
            }),
          );
        },
      ),
    );
  }

  void _modelDetails(CatalogModel model) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .82,
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(model.name, style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 4),
                SelectableText(
                  '${model.providerID}/${model.id}',
                  style: const TextStyle(fontFamily: 'monospace'),
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    _CapabilityChip(
                      label: '${_compactNumber(model.contextLimit)} context',
                    ),
                    _CapabilityChip(
                      label: '${_compactNumber(model.outputLimit)} output',
                    ),
                    if (model.reasoning)
                      const _CapabilityChip(label: 'Reasoning'),
                    if (model.attachments)
                      const _CapabilityChip(label: 'Attachments'),
                    if (model.tools) const _CapabilityChip(label: 'Tools'),
                    for (final variant in model.variants)
                      _CapabilityChip(label: variant.id),
                  ],
                ),
                const SizedBox(height: 20),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton(
                    onPressed: model.enabled
                        ? () async {
                            await widget.controller.selectModel(
                              ModelRef(
                                providerID: model.providerID,
                                modelID: model.id,
                              ),
                            );
                            if (context.mounted) Navigator.pop(context);
                            if (mounted) setState(() {});
                          }
                        : null,
                    child: const Text('Use this model'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  static String _compactNumber(int value) {
    if (value >= 1000000) return '${(value / 1000000).toStringAsFixed(1)}M';
    if (value >= 1000) return '${(value / 1000).toStringAsFixed(0)}k';
    return '$value';
  }
}

class _CapabilityChip extends StatelessWidget {
  final String label;
  const _CapabilityChip({required this.label});

  @override
  Widget build(BuildContext context) =>
      Chip(label: Text(label), visualDensity: VisualDensity.compact);
}

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

class _McpServerTile extends StatelessWidget {
  final McpServerInfo server;
  final String subtitle;
  final String actionLabel;
  final bool busy;
  final VoidCallback? onAction;

  const _McpServerTile({
    required this.server,
    required this.subtitle,
    required this.actionLabel,
    required this.busy,
    required this.onAction,
  });

  Widget _action() => busy
      ? const Padding(
          padding: EdgeInsets.all(14),
          child: SizedBox.square(
            dimension: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        )
      : TextButton(onPressed: onAction, child: Text(actionLabel));

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final scaledBody = MediaQuery.textScalerOf(context).scale(14);
      final stackAction = constraints.maxWidth < 420 || scaledBody > 20;
      if (!stackAction) {
        return ListTile(
          leading: _McpStatus(status: server.status),
          title: Text(server.name),
          subtitle: Text(
            subtitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: _action(),
        );
      }
      return Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 12, 8),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: _McpStatus(status: server.status),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    server.name,
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    subtitle,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  _action(),
                ],
              ),
            ),
          ],
        ),
      );
    },
  );
}

class _PendingMcpOAuth {
  final McpServerInfo server;
  final McpAuthLaunch launch;
  final McpOAuthLoopbackListener? listener;

  const _PendingMcpOAuth({
    required this.server,
    required this.launch,
    required this.listener,
  });
}

class _PendingMcpOAuthTile extends StatelessWidget {
  final _PendingMcpOAuth pending;
  final bool busy;
  final VoidCallback onEnterCode;
  final VoidCallback onCancel;

  const _PendingMcpOAuthTile({
    required this.pending,
    required this.busy,
    required this.onEnterCode,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) => Semantics(
    container: true,
    label: 'MCP authorization pending for ${pending.server.name}',
    child: Padding(
      key: const ValueKey('pending-mcp-oauth'),
      padding: const EdgeInsets.fromLTRB(16, 4, 12, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.only(top: 4),
            child: Icon(Icons.phonelink_lock_outlined),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Waiting for browser authorization',
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                Text(
                  pending.listener == null
                      ? 'Automatic callback capture is unavailable. Paste the callback URL or authorization code.'
                      : 'The phone is securely listening for this authorization callback. You can also enter it manually.',
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 4,
                  runSpacing: 4,
                  children: [
                    TextButton(
                      key: const ValueKey('enter-mcp-oauth-code'),
                      onPressed: busy ? null : onEnterCode,
                      child: const Text('Enter code'),
                    ),
                    TextButton.icon(
                      key: const ValueKey('cancel-mcp-oauth'),
                      onPressed: busy ? null : onCancel,
                      icon: const Icon(Icons.close_rounded),
                      label: const Text('Cancel'),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );
}

class _McpOAuthCodeDialog extends StatefulWidget {
  final String expectedState;

  const _McpOAuthCodeDialog({required this.expectedState});

  @override
  State<_McpOAuthCodeDialog> createState() => _McpOAuthCodeDialogState();
}

class _McpOAuthCodeDialogState extends State<_McpOAuthCodeDialog> {
  final _controller = TextEditingController();
  String? _error;

  void _submit() {
    try {
      final code = parseMcpAuthorizationCode(
        _controller.text,
        expectedState: widget.expectedState,
      );
      Navigator.pop(context, code);
    } catch (error) {
      setState(() => _error = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    scrollable: true,
    title: const Text('Complete MCP authorization'),
    content: TextField(
      key: const ValueKey('mcp-oauth-code-input'),
      controller: _controller,
      autofocus: true,
      minLines: 1,
      maxLines: 4,
      autocorrect: false,
      enableSuggestions: false,
      decoration: InputDecoration(
        labelText: 'Callback URL or code',
        helperText:
            'Paste the complete callback URL when available so its security state can be verified.',
        errorText: _error,
      ),
      onSubmitted: (_) => _submit(),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Back'),
      ),
      FilledButton(
        key: const ValueKey('complete-mcp-oauth'),
        onPressed: _submit,
        child: const Text('Complete'),
      ),
    ],
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

class _ProviderIntegrationTile extends StatelessWidget {
  final PresentedIntegration presented;
  final String subtitle;
  final bool busy;
  final VoidCallback onConnect;
  final VoidCallback onDisconnect;

  const _ProviderIntegrationTile({
    required this.presented,
    required this.subtitle,
    required this.busy,
    required this.onConnect,
    required this.onDisconnect,
  });

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final textScale = MediaQuery.textScalerOf(context).scale(1);
        final stackAction = constraints.maxWidth < 400 && textScale > 1.3;
        final action = _action();
        return ListTile(
          leading: const Icon(Icons.link_rounded),
          title: Text(presented.name),
          subtitle: stackAction
              ? Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(subtitle),
                    if (action != null) ...[
                      const SizedBox(height: 4),
                      Align(
                        alignment: AlignmentDirectional.centerEnd,
                        child: action,
                      ),
                    ],
                  ],
                )
              : Text(subtitle),
          trailing: stackAction ? null : action,
        );
      },
    );
  }

  Widget? _action() {
    final integration = presented.integration;
    if (integration.credentialIDs.isNotEmpty) {
      if (busy) {
        return const SizedBox.square(
          dimension: 20,
          child: CircularProgressIndicator(strokeWidth: 2),
        );
      }
      return TextButton(
        key: ValueKey('disconnect-provider-${integration.id}'),
        onPressed: onDisconnect,
        child: const Text('Disconnect'),
      );
    }
    if (presented.connected) {
      return Text(
        integration.hasEnvironmentConnection
            ? 'Server\nenvironment'
            : 'Connected\n(server-managed)',
        textAlign: TextAlign.end,
      );
    }
    final canConnect = integration.methods.any(
      (method) => method.type == 'key' || method.type == 'oauth',
    );
    return TextButton(
      onPressed: !busy && canConnect ? onConnect : null,
      child: const Text('Connect'),
    );
  }
}

class _PendingIntegrationOAuth {
  final String integrationID;
  final String integrationName;
  final IntegrationAuthLaunch launch;
  final IntegrationAuthStatus? status;

  const _PendingIntegrationOAuth({
    required this.integrationID,
    required this.integrationName,
    required this.launch,
    this.status,
  });

  _PendingIntegrationOAuth copyWith({required IntegrationAuthStatus status}) =>
      _PendingIntegrationOAuth(
        integrationID: integrationID,
        integrationName: integrationName,
        launch: launch,
        status: status,
      );
}

class _PendingOAuthTile extends StatelessWidget {
  final _PendingIntegrationOAuth pending;
  final bool checking;
  final Future<void> Function() onContinue;
  final Future<void> Function() onCancel;

  const _PendingOAuthTile({
    required this.pending,
    required this.checking,
    required this.onContinue,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    final state = pending.status?.state ?? IntegrationAuthState.pending;
    final terminal =
        state == IntegrationAuthState.failed ||
        state == IntegrationAuthState.expired;
    final message = switch (state) {
      IntegrationAuthState.failed =>
        pending.status?.message ?? 'Authentication failed',
      IntegrationAuthState.expired => 'Authentication attempt expired',
      IntegrationAuthState.complete => 'Authentication complete',
      IntegrationAuthState.pending =>
        pending.launch.instructions.trim().isEmpty
            ? pending.launch.mode == IntegrationAuthMode.code
                  ? 'Return from the browser and enter the authorization code.'
                  : 'Finish authentication in the browser, then check its status.'
            : pending.launch.instructions.trim(),
    };
    final actionLabel = terminal
        ? 'Dismiss'
        : pending.launch.mode == IntegrationAuthMode.code
        ? 'Enter code'
        : 'Check';

    return ListTile(
      key: const ValueKey('pending-provider-oauth'),
      leading: checking
          ? const SizedBox.square(
              dimension: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(terminal ? Icons.error_outline_rounded : Icons.login_rounded),
      title: Text('Connecting ${pending.integrationName}'),
      subtitle: Text(message, maxLines: 3, overflow: TextOverflow.ellipsis),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextButton(
            onPressed: checking
                ? null
                : terminal
                ? onCancel
                : onContinue,
            child: Text(actionLabel),
          ),
          if (!terminal)
            PopupMenuButton<void>(
              tooltip: 'Authentication options',
              itemBuilder: (context) => [
                PopupMenuItem<void>(
                  onTap: onCancel,
                  child: const ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(Icons.close_rounded),
                    title: Text('Cancel attempt'),
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _OAuthCodeDialog extends StatefulWidget {
  final String integrationName;
  final String instructions;

  const _OAuthCodeDialog({
    required this.integrationName,
    required this.instructions,
  });

  @override
  State<_OAuthCodeDialog> createState() => _OAuthCodeDialogState();
}

class _OAuthCodeDialogState extends State<_OAuthCodeDialog> {
  final _controller = TextEditingController();

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text('Finish ${widget.integrationName}'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (widget.instructions.trim().isNotEmpty) ...[
            Text(widget.instructions.trim()),
            const SizedBox(height: 16),
          ],
          TextField(
            key: const ValueKey('oauth-completion-code'),
            controller: _controller,
            autofocus: true,
            autocorrect: false,
            enableSuggestions: false,
            decoration: const InputDecoration(labelText: 'Authorization code'),
            onSubmitted: (_) => _complete(),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Not yet'),
      ),
      FilledButton(onPressed: _complete, child: const Text('Complete')),
    ],
  );

  void _complete() {
    final value = _controller.text.trim();
    if (value.isNotEmpty) Navigator.pop(context, value);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

bool _isPromptVisible(Map<String, dynamic> prompt, Map<String, String> values) {
  final when = prompt['when'];
  if (when is! Map) return true;
  final key = when['key']?.toString();
  final expected = when['value']?.toString();
  final operation = when['op']?.toString();
  if (key == null || expected == null) return true;
  return switch (operation) {
    'eq' => values[key] == expected,
    'neq' => values[key] != expected,
    _ => true,
  };
}

bool _isPromptRequired(Map<String, dynamic> prompt) =>
    prompt['required'] != false;

class _OAuthInputsDialog extends StatefulWidget {
  final IntegrationMethodInfo method;
  const _OAuthInputsDialog({required this.method});

  @override
  State<_OAuthInputsDialog> createState() => _OAuthInputsDialogState();
}

class _OAuthInputsDialogState extends State<_OAuthInputsDialog> {
  final _formKey = GlobalKey<FormState>();
  final _textControllers = <String, TextEditingController>{};
  final _values = <String, String>{};

  @override
  void initState() {
    super.initState();
    for (final prompt in widget.method.prompts) {
      if (prompt['type'] == 'text') {
        _textControllers[prompt['key'].toString()] = TextEditingController();
      }
    }
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text(widget.method.label),
    content: SingleChildScrollView(
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final prompt in widget.method.prompts)
              if (_isPromptVisible(prompt, _values))
                Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: prompt['type'] == 'select'
                      ? DropdownButtonFormField<String>(
                          key: ValueKey('oauth-prompt-${prompt['key']}'),
                          initialValue: _values[prompt['key'].toString()],
                          decoration: InputDecoration(
                            labelText: prompt['message']?.toString(),
                            border: const OutlineInputBorder(),
                          ),
                          items: [
                            for (final option
                                in (prompt['options'] as List? ?? const []))
                              if (option is Map && option.containsKey('value'))
                                DropdownMenuItem(
                                  value: option['value'].toString(),
                                  child: Text(
                                    option['label']?.toString() ?? '',
                                  ),
                                ),
                          ],
                          validator: (value) =>
                              _isPromptRequired(prompt) && value == null
                              ? 'Select an option'
                              : null,
                          onChanged: (value) => setState(() {
                            final key = prompt['key'].toString();
                            if (value == null) {
                              _values.remove(key);
                            } else {
                              _values[key] = value;
                            }
                          }),
                        )
                      : TextFormField(
                          key: ValueKey('oauth-prompt-${prompt['key']}'),
                          controller:
                              _textControllers[prompt['key'].toString()],
                          decoration: InputDecoration(
                            labelText: prompt['message']?.toString(),
                            hintText: prompt['placeholder']?.toString(),
                            border: const OutlineInputBorder(),
                          ),
                          validator: (value) =>
                              _isPromptRequired(prompt) &&
                                  (value == null || value.trim().isEmpty)
                              ? 'Enter a value'
                              : null,
                          onChanged: (value) => setState(
                            () => _values[prompt['key'].toString()] = value,
                          ),
                        ),
                ),
          ],
        ),
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(onPressed: _submit, child: const Text('Continue')),
    ],
  );

  void _submit() {
    if (_formKey.currentState?.validate() != true) return;
    final visibleValues = <String, String>{};
    for (final prompt in widget.method.prompts) {
      if (!_isPromptVisible(prompt, _values)) continue;
      final key = prompt['key'].toString();
      if (prompt['type'] == 'text') {
        visibleValues[key] = _textControllers[key]?.text ?? '';
      } else if (_values[key] case final value?) {
        visibleValues[key] = value;
      }
    }
    Navigator.pop(context, visibleValues);
  }

  @override
  void dispose() {
    for (final controller in _textControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }
}

/// Parses a server-provided authorization URL using the app's external-launch
/// policy. Authentication is allowed only on HTTPS origins without embedded
/// credentials.
Uri parseAuthorizationUrl(String value) {
  final uri = Uri.tryParse(value.trim());
  if (uri == null ||
      uri.scheme.toLowerCase() != 'https' ||
      uri.host.trim().isEmpty ||
      uri.userInfo.isNotEmpty) {
    throw const ProductException(
      'The server returned an unsafe authorization link. '
      'Only HTTPS links with a valid host and no embedded credentials are allowed.',
    );
  }
  return uri;
}

class _SectionLoading extends StatelessWidget {
  final String label;
  const _SectionLoading({required this.label});

  @override
  Widget build(BuildContext context) => ListTile(
    leading: const SizedBox.square(
      dimension: 20,
      child: CircularProgressIndicator(strokeWidth: 2),
    ),
    title: Text(label),
  );
}

class _SectionLoadError extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const _SectionLoadError({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) => ListTile(
    leading: Icon(
      Icons.error_outline_rounded,
      color: Theme.of(context).colorScheme.error,
    ),
    title: const Text('Could not load this section'),
    subtitle: Text(message, maxLines: 3, overflow: TextOverflow.ellipsis),
    trailing: TextButton(onPressed: onRetry, child: const Text('Retry')),
  );
}

class _McpStatus extends StatelessWidget {
  final String status;
  const _McpStatus({required this.status});

  @override
  Widget build(BuildContext context) {
    final color = switch (status) {
      'connected' => Colors.green.shade400,
      'failed' => Theme.of(context).colorScheme.error,
      'needs_auth' || 'needs_client_registration' => Colors.orange.shade400,
      _ => Theme.of(context).hintColor,
    };
    return Semantics(
      label: status.replaceAll('_', ' '),
      child: Container(
        width: 10,
        height: 10,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      ),
    );
  }
}

class CommandsScreen extends StatefulWidget {
  final ConnectionController controller;
  const CommandsScreen({super.key, required this.controller});

  @override
  State<CommandsScreen> createState() => _CommandsScreenState();
}

class _CommandsScreenState extends State<CommandsScreen> {
  List<CommandInfo>? _commands;
  String? _error;
  String _query = '';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw StateError('OpenCode is reconnecting.');
      }
      final commands = await repository.listCommands();
      if (mounted) setState(() => _commands = commands);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final commands = (_commands ?? const <CommandInfo>[]).where((command) {
      final query = _query.toLowerCase();
      return query.isEmpty ||
          command.name.toLowerCase().contains(query) ||
          (command.description ?? '').toLowerCase().contains(query);
    }).toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Server commands')),
      body: _commands == null && _error == null
          ? const LoadingList()
          : _error != null && _commands == null
          ? ProductErrorState(message: _error!, onRetry: _load)
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: TextField(
                    decoration: const InputDecoration(
                      hintText: 'Search server commands',
                      prefixIcon: Icon(Icons.search_rounded),
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                    onChanged: (value) => setState(() => _query = value),
                  ),
                ),
                Expanded(
                  child: commands.isEmpty
                      ? RefreshIndicator(
                          onRefresh: _load,
                          child: const ProductEmptyState(
                            icon: Icons.electric_bolt_outlined,
                            title: 'No server commands found',
                            message:
                                'Commands from your project and skills appear here.',
                          ),
                        )
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: ListView.separated(
                            physics: const AlwaysScrollableScrollPhysics(),
                            itemCount: commands.length,
                            separatorBuilder: (_, _) =>
                                const Divider(height: 1),
                            itemBuilder: (context, index) {
                              final command = commands[index];
                              return ListTile(
                                leading: const Icon(
                                  Icons.electric_bolt_outlined,
                                ),
                                title: Text('/${command.name}'),
                                subtitle: Text(
                                  command.description ?? 'No description',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                trailing: const Icon(Icons.play_arrow_rounded),
                                onTap: () => _run(command),
                              );
                            },
                          ),
                        ),
                ),
              ],
            ),
    );
  }

  Future<void> _run(CommandInfo command) async {
    final sessions = widget.controller.sortedSessions();
    if (sessions.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Create a session before running a command.'),
        ),
      );
      return;
    }
    var sessionID = sessions.first.id;
    final arguments = TextEditingController();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text('Run /${command.name}'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              DropdownButtonFormField<String>(
                initialValue: sessionID,
                decoration: const InputDecoration(labelText: 'Session'),
                items: [
                  for (final session in sessions)
                    DropdownMenuItem(
                      value: session.id,
                      child: Text(
                        session.title?.isNotEmpty == true
                            ? session.title!
                            : 'Untitled session',
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
                onChanged: (value) {
                  if (value != null) setDialogState(() => sessionID = value);
                },
              ),
              const SizedBox(height: 14),
              TextField(
                controller: arguments,
                decoration: const InputDecoration(
                  labelText: 'Arguments',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Run'),
            ),
          ],
        ),
      ),
    );
    final argumentText = arguments.text.trim();
    arguments.dispose();
    if (confirmed != true) return;
    try {
      final api = await widget.controller.prepareActionTransport();
      if (api == null) throw StateError('OpenCode is reconnecting.');
      await api.slashCommand(
        sessionID,
        command.name,
        argumentText,
        model: widget.controller.selectedModel,
      );
      if (mounted) Navigator.of(context).pushNamed('/chat/$sessionID');
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    }
  }
}

class SkillsScreen extends StatefulWidget {
  final ConnectionController controller;
  const SkillsScreen({super.key, required this.controller});

  @override
  State<SkillsScreen> createState() => _SkillsScreenState();
}

class _SkillsScreenState extends State<SkillsScreen> {
  List<SkillInfo>? _skills;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw StateError('OpenCode is reconnecting.');
      }
      final skills = await repository.listSkills();
      if (mounted) setState(() => _skills = skills);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Skills')),
    body: _skills == null && _error == null
        ? const LoadingList()
        : _error != null && _skills == null
        ? ProductErrorState(message: _error!, onRetry: _load)
        : _skills!.isEmpty
        ? RefreshIndicator(
            onRefresh: _load,
            child: const ProductEmptyState(
              icon: Icons.extension_off_outlined,
              title: 'No skills available',
              message: 'Project and global OpenCode skills appear here.',
            ),
          )
        : RefreshIndicator(
            onRefresh: _load,
            child: ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              itemCount: _skills!.length,
              separatorBuilder: (_, _) => const Divider(height: 1),
              itemBuilder: (context, index) {
                final skill = _skills![index];
                return ListTile(
                  leading: const Icon(Icons.extension_outlined),
                  title: Text(skill.name),
                  subtitle: Text(
                    skill.description ?? skill.location,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => _showSkill(skill),
                );
              },
            ),
          ),
  );

  void _showSkill(SkillInfo skill) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: SizedBox(
          height: MediaQuery.sizeOf(context).height * .82,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20),
                child: Text(
                  skill.name,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
              ),
              Padding(
                padding: const EdgeInsets.fromLTRB(20, 4, 20, 12),
                child: SelectableText(
                  skill.location,
                  style: TextStyle(
                    color: Theme.of(context).hintColor,
                    fontFamily: 'monospace',
                    fontSize: 11,
                  ),
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: FilePreviewBody(
                  key: const Key('skill-content-preview'),
                  data: FilePreviewData(
                    name: 'SKILL.md',
                    mimeType: 'text/markdown',
                    text: skill.content,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class ReferencesScreen extends StatefulWidget {
  final ConnectionController controller;
  final ValueChanged<ReferenceInfo>? onSelected;

  const ReferencesScreen({
    super.key,
    required this.controller,
    this.onSelected,
  });

  @override
  State<ReferencesScreen> createState() => _ReferencesScreenState();
}

class _ReferencesScreenState extends State<ReferencesScreen> {
  List<ReferenceInfo>? _references;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw StateError('OpenCode is reconnecting.');
      }
      final references = await repository.listReferences();
      if (mounted) setState(() => _references = references);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('References')),
    body: _references == null && _error == null
        ? const LoadingList()
        : _error != null && _references == null
        ? ProductErrorState(message: _error!, onRetry: _load)
        : _references!.isEmpty
        ? RefreshIndicator(
            onRefresh: _load,
            child: const ProductEmptyState(
              icon: Icons.bookmarks_outlined,
              title: 'No references configured',
              message: 'References attached to this project appear here.',
            ),
          )
        : RefreshIndicator(
            onRefresh: _load,
            child: ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              itemCount: _references!.length,
              separatorBuilder: (_, _) => const Divider(height: 1),
              itemBuilder: (context, index) {
                final reference = _references![index];
                return ListTile(
                  key: ValueKey('reference-${reference.name}'),
                  leading: const Icon(Icons.bookmark_outline_rounded),
                  title: Text(reference.name),
                  subtitle: Text(
                    reference.description?.isNotEmpty == true
                        ? '${reference.description}\n${reference.path}'
                        : reference.path,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 12),
                  ),
                  trailing: Icon(
                    widget.onSelected == null
                        ? Icons.copy_rounded
                        : Icons.add_comment_outlined,
                  ),
                  onTap: () => _useReference(reference),
                );
              },
            ),
          ),
  );

  Future<void> _useReference(ReferenceInfo reference) async {
    final onSelected = widget.onSelected;
    if (onSelected != null) {
      onSelected(reference);
      if (mounted) Navigator.of(context).pop();
      return;
    }
    await Clipboard.setData(ClipboardData(text: '@${reference.name}'));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('@${reference.name} copied'),
        duration: const Duration(seconds: 2),
      ),
    );
  }
}
