part of '../settings_screen.dart';

/// Server category: connection identity, health, profiles, host management,
/// and the server update flow.
class ServerSettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const ServerSettingsScreen({super.key, required this.controller});

  @override
  State<ServerSettingsScreen> createState() => _ServerSettingsScreenState();
}

class _ServerSettingsScreenState extends State<ServerSettingsScreen> {
  Health? _health;
  String? _healthError;
  bool _checking = false;
  bool _upgradingServer = false;
  String? _serverUpgradeError;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_connectionChanged);
    _checkHealth();
  }

  void _connectionChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _checkHealth() async {
    if (_checking) return;
    setState(() {
      _checking = true;
      _healthError = null;
    });
    try {
      final api = await widget.controller.prepareActionTransport();
      if (api == null) throw StateError('OpenCode is reconnecting.');
      final health = await api.health();
      if (mounted) setState(() => _health = health);
    } catch (error) {
      if (mounted) setState(() => _healthError = error.toString());
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  Future<void> _copyRemoteUpdateCommands() async {
    await Clipboard.setData(
      const ClipboardData(text: 'opencode upgrade\nopencode models --refresh'),
    );
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Server update commands copied'),
        duration: Duration(seconds: 2),
      ),
    );
  }

  Future<void> _showRemoteRestartNotice(String version) => showDialog<void>(
    context: context,
    builder: (context) => AlertDialog(
      title: const Text('Restart OpenCode on its host'),
      content: Text(
        'OpenCode $version is installed, but this server process is still '
        'running ${widget.controller.version ?? 'the previous version'}. '
        'Restart that process on the server host; mobile will reconnect and '
        'confirm the running version.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Done'),
        ),
      ],
    ),
  );

  Future<void> _upgradeRemoteServer(String target) async {
    if (_upgradingServer || !isExactServerVersion(target)) return;
    final profile = widget.controller.profile;
    if (profile == null) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        scrollable: true,
        title: const Text('Update remote OpenCode?'),
        content: Text(
          'Install OpenCode $target on ${profile.name} using the server\'s '
          'detected installation method. The current process is running '
          '${widget.controller.version ?? 'an unknown version'}.\n\n'
          'The install keeps server data in place, but the OpenCode process '
          'must be restarted on its host before the new version takes effect.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            key: const Key('confirm-server-upgrade'),
            onPressed: () => Navigator.pop(context, true),
            child: Text('Install $target'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _upgradingServer = true;
      _serverUpgradeError = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final installed = await repository.upgradeServer(target);
      if (!mounted) return;
      if (widget.controller.profile?.id != profile.id) {
        throw const ProductException(
          'The active server changed before the upgrade completed',
        );
      }
      widget.controller.recordServerUpgradeInstalled(installed);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'OpenCode $installed installed. Restart its server process to use it.',
          ),
          duration: const Duration(seconds: 5),
        ),
      );
    } catch (error) {
      if (mounted) setState(() => _serverUpgradeError = error.toString());
    } finally {
      if (mounted) setState(() => _upgradingServer = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final profile = controller.profile;
    // Termux management only exists on Android; desktop loopback servers
    // follow the ordinary remote-update path.
    final managedLocally =
        defaultTargetPlatform == TargetPlatform.android &&
        TermuxBridge.managesServerUrl(profile?.baseUrl);
    final availableVersion = managedLocally
        ? null
        : controller.availableServerVersion;
    final installedVersion = managedLocally
        ? null
        : controller.installedServerVersion;
    late final String serverUpdateTitle;
    late final String serverUpdateSubtitle;
    late final IconData serverUpdateIcon;
    VoidCallback? serverUpdateAction;
    if (managedLocally) {
      serverUpdateTitle = 'Update managed OpenCode';
      serverUpdateSubtitle =
          'Install the latest stable server, refresh models, restart safely, and reconnect.';
      serverUpdateIcon = Icons.chevron_right_rounded;
      serverUpdateAction = () =>
          Navigator.of(context).pushNamed('/termux-setup');
    } else if (installedVersion != null) {
      serverUpdateTitle = 'Restart OpenCode to use $installedVersion';
      serverUpdateSubtitle = _serverUpgradeError != null
          ? '${_serverUpgradeError!} Tap to retry.'
          : '$installedVersion is installed. The current process is still ${controller.version ?? 'the previous version'}.';
      serverUpdateIcon = Icons.restart_alt_rounded;
      serverUpdateAction = () => _showRemoteRestartNotice(installedVersion);
    } else if (availableVersion != null) {
      serverUpdateTitle = 'Update OpenCode to $availableVersion';
      serverUpdateSubtitle = _serverUpgradeError != null
          ? '${_serverUpgradeError!} Tap to retry.'
          : 'Current server: ${controller.version ?? 'unknown'}. Uses OpenCode\'s official installer; host restart required.';
      serverUpdateIcon = Icons.download_rounded;
      serverUpdateAction = () => _upgradeRemoteServer(availableVersion);
    } else {
      serverUpdateTitle = 'Server updates are managed externally';
      serverUpdateSubtitle =
          'Copy the official upgrade and model-refresh commands to run on the server host.';
      serverUpdateIcon = Icons.copy_rounded;
      serverUpdateAction = _copyRemoteUpdateCommands;
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Server')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          ListTile(
            leading: const Icon(Icons.dns_outlined),
            title: Text(profile?.name ?? 'OpenCode server'),
            subtitle: SelectableText(
              profile?.baseUrl ?? 'Not connected',
              style: const TextStyle(fontFamily: 'AppMono', fontSize: 12),
            ),
            trailing: IconButton(
              tooltip: 'Check server health',
              onPressed: _checking ? null : _checkHealth,
              icon: _checking
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.refresh_rounded),
            ),
          ),
          ListTile(
            leading: Icon(
              _health?.healthy == true
                  ? Icons.check_circle_outline_rounded
                  : Icons.error_outline_rounded,
              color: _health?.healthy == true
                  ? AppTheme.successOf(Theme.of(context))
                  : Theme.of(context).colorScheme.error,
            ),
            title: Text(
              _health?.healthy == true ? 'Server healthy' : 'Health unavailable',
            ),
            subtitle: Text(
              _healthError ??
                  'Version ${_health?.version ?? controller.version ?? 'unknown'}',
            ),
          ),
          ListTile(
            leading: const Icon(Icons.person_outline_rounded),
            title: const Text('Authentication'),
            subtitle: Text(
              profile?.password.isNotEmpty == true
                  ? 'Basic authentication enabled as ${profile?.username.isNotEmpty == true ? profile!.username : 'opencode'}'
                  : 'No server password saved',
            ),
          ),
          ListTile(
            leading: const Icon(Icons.storage_outlined),
            title: const Text('Manage server profiles'),
            subtitle: const Text('Add, edit, or switch OpenCode servers'),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).pushNamed('/servers'),
          ),
          if (!managedLocally)
            ListTile(
              key: const Key('host-management-entry'),
              leading: const Icon(Icons.terminal_outlined),
              title: const Text('Ubuntu host management'),
              subtitle: const Text(
                'Run OpenCode as a service on the host; copy setup, status, '
                'restart, log, and update commands',
              ),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) => HostManagementScreen(controller: controller),
                ),
              ),
            ),
          ListTile(
            key: const Key('server-updates-tile'),
            leading: const Icon(Icons.system_update_alt_rounded),
            title: Text(serverUpdateTitle),
            subtitle: Text(serverUpdateSubtitle),
            trailing: _upgradingServer
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(serverUpdateIcon),
            onTap: _upgradingServer ? null : serverUpdateAction,
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    widget.controller.removeListener(_connectionChanged);
    super.dispose();
  }
}
