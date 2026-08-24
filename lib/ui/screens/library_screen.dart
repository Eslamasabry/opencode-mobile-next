import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../api/models.dart' show ModelRef;
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import 'requests_screen.dart';
import 'settings_screen.dart';

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
          title: 'Commands',
          subtitle: 'Find and run commands in a session',
          onTap: () => _open(context, CommandsScreen(controller: controller)),
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
  String? _error;
  String _query = '';
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    setState(() => _error = null);
    try {
      final value = await widget.controller.repository?.loadCatalog();
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
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Models and agents'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'Models'),
              Tab(text: 'Providers'),
              Tab(text: 'Agents'),
            ],
          ),
        ),
        body: _catalog == null && _error == null
            ? const LoadingList()
            : _error != null && _catalog == null
            ? ProductErrorState(message: _error!, onRetry: _load)
            : TabBarView(children: [_models(), _providers(), _agents()]),
      ),
    );
  }

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
                      _CapabilityChip(label: variant),
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
  const IntegrationsScreen({super.key, required this.controller});

  @override
  State<IntegrationsScreen> createState() => _IntegrationsScreenState();
}

class _IntegrationsScreenState extends State<IntegrationsScreen>
    with WidgetsBindingObserver {
  List<McpServerInfo>? _servers;
  List<McpResourceInfo> _resources = const [];
  List<IntegrationInfo> _integrations = const [];
  String? _error;
  String? _resourceError;
  final Set<String> _busy = {};
  bool _refreshOnResume = false;
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && _refreshOnResume) {
      _refreshOnResume = false;
      _load();
    }
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final repository = widget.controller.repository;
    if (repository == null) return;
    setState(() {
      _error = null;
      _resourceError = null;
    });
    try {
      final servers = await repository.listMcpServers();
      if (mounted && generation == _loadGeneration) {
        setState(() => _servers = servers);
      }
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _error = error.toString());
      }
    }
    try {
      final values = await Future.wait([
        repository.listMcpResources(),
        repository.listIntegrations(),
      ]);
      if (mounted && generation == _loadGeneration) {
        setState(() {
          _resources = values[0] as List<McpResourceInfo>;
          _integrations = values[1] as List<IntegrationInfo>;
        });
      }
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _resourceError = error.toString());
      }
    }
  }

  Future<void> _action(McpServerInfo server) async {
    final repository = widget.controller.repository;
    if (repository == null || _busy.contains(server.name)) return;
    setState(() => _busy.add(server.name));
    try {
      switch (server.status) {
        case 'connected':
          await repository.disconnectMcp(server.name);
          break;
        case 'needs_auth':
        case 'needs_client_registration':
          final url = await repository.startMcpAuthentication(server.name);
          final launched = await launchUrl(
            Uri.parse(url),
            mode: LaunchMode.externalApplication,
          );
          if (!launched) {
            throw const ProductException(
              'Could not open the authorization page',
            );
          }
          _refreshOnResume = true;
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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('MCP and integrations')),
      body: _servers == null && _error == null
          ? const LoadingList()
          : _error != null && _servers == null
          ? ProductErrorState(message: _error!, onRetry: _load)
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.only(bottom: 24),
                children: [
                  const SectionLabel('MCP servers'),
                  if (_servers!.isEmpty)
                    const ListTile(
                      leading: Icon(Icons.hub_outlined),
                      title: Text('No MCP servers configured'),
                      subtitle: Text(
                        'Add MCP servers in your OpenCode configuration.',
                      ),
                    )
                  else
                    for (final server in _servers!)
                      ListTile(
                        leading: _McpStatus(status: server.status),
                        title: Text(server.name),
                        subtitle: Text(
                          server.error?.isNotEmpty == true
                              ? server.error!
                              : _statusLabel(server.status),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        trailing: _busy.contains(server.name)
                            ? const SizedBox.square(
                                dimension: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                            : TextButton(
                                onPressed: () => _action(server),
                                child: Text(_actionLabel(server.status)),
                              ),
                      ),
                  if (_integrations.isNotEmpty) ...[
                    const SectionLabel('Provider connections'),
                    for (final integration in _integrations)
                      ListTile(
                        leading: const Icon(Icons.link_rounded),
                        title: Text(integration.name),
                        subtitle: Text(_integrationSubtitle(integration)),
                        trailing: integration.connectionCount > 0
                            ? const Text(
                                'Connected\n(server-managed)',
                                textAlign: TextAlign.end,
                              )
                            : TextButton(
                                onPressed:
                                    integration.methods.any(
                                      (method) =>
                                          method.type == 'key' ||
                                          method.type == 'oauth',
                                    )
                                    ? () => _connectIntegration(integration)
                                    : null,
                                child: const Text('Connect'),
                              ),
                      ),
                  ],
                  if (_resources.isNotEmpty) ...[
                    const SectionLabel('Available resources'),
                    for (final resource in _resources)
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
                  if (_resourceError != null)
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Text(
                        _resourceError!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                    ),
                ],
              ),
            ),
    );
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
      await widget.controller.repository!.connectIntegrationKey(
        integration.id,
        value!,
      );
      await _load();
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
      final launch = await widget.controller.repository!.startIntegrationOAuth(
        integration.id,
        method.id!,
        inputs: inputs,
      );
      final opened = await launchUrl(
        Uri.parse(launch.url),
        mode: LaunchMode.externalApplication,
      );
      if (!opened) throw const ProductException('Could not open OAuth');
      _refreshOnResume = true;
      if (launch.instructions.isNotEmpty && mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(launch.instructions)));
      }
    });
  }

  Future<Map<String, String>?> _oauthInputs(
    IntegrationMethodInfo method,
  ) async {
    if (method.prompts.isEmpty) return const {};
    final textControllers = <String, TextEditingController>{};
    final values = <String, String>{};
    for (final prompt in method.prompts) {
      if (prompt['type'] == 'text') {
        textControllers[prompt['key'].toString()] = TextEditingController();
      }
    }
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(method.label),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final prompt in method.prompts)
                  if (prompt['type'] == 'select')
                    DropdownButtonFormField<String>(
                      decoration: InputDecoration(
                        labelText: prompt['message']?.toString(),
                      ),
                      items: [
                        for (final option
                            in (prompt['options'] as List? ?? const []))
                          if (option is Map)
                            DropdownMenuItem(
                              value: option['value']?.toString(),
                              child: Text(option['label']?.toString() ?? ''),
                            ),
                      ],
                      onChanged: (value) => setDialogState(() {
                        if (value != null) {
                          values[prompt['key'].toString()] = value;
                        }
                      }),
                    )
                  else if (prompt['type'] == 'text')
                    TextField(
                      controller: textControllers[prompt['key'].toString()],
                      decoration: InputDecoration(
                        labelText: prompt['message']?.toString(),
                        hintText: prompt['placeholder']?.toString(),
                      ),
                    ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () {
                for (final entry in textControllers.entries) {
                  values[entry.key] = entry.value.text;
                }
                Navigator.pop(context, values);
              },
              child: const Text('Continue'),
            ),
          ],
        ),
      ),
    );
    for (final controller in textControllers.values) {
      controller.dispose();
    }
    return result;
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
    _loadGeneration++;
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
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
      final commands = await widget.controller.repository?.listCommands();
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
      appBar: AppBar(title: const Text('Commands')),
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
                      hintText: 'Search commands',
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
                            title: 'No commands found',
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
      await widget.controller.api?.slashCommand(
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
      final skills = await widget.controller.repository?.listSkills();
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
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(20),
                  child: SelectableText(skill.content),
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
  const ReferencesScreen({super.key, required this.controller});

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
      final references = await widget.controller.repository?.listReferences();
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
                );
              },
            ),
          ),
  );
}
