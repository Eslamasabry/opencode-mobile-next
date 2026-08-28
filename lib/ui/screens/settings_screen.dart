import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../api/provider_presentation.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';
import '../../voice/notices.dart';
import '../widgets/appearance_picker.dart';
import '../widgets/product_states.dart';
import '../widgets/pickers.dart';
import 'about_screen.dart';
import 'app_diagnostics_screen.dart';
import 'host_management_screen.dart';
import 'saved_permissions_screen.dart';

class SettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const SettingsScreen({super.key, required this.controller});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen>
    with WidgetsBindingObserver {
  Health? _health;
  String? _healthError;
  bool _checking = false;
  TerminalShellSettings? _shellSettings;
  String? _shellError;
  bool _loadingShell = false;
  bool _savingShell = false;
  int _shellLoadGeneration = 0;
  bool _upgradingServer = false;
  String? _serverUpgradeError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_connectionChanged);
    widget.controller.backgroundLive.addListener(_backgroundChanged);
    _checkHealth();
    _loadShellSettings();
    widget.controller.backgroundLive.refreshStatus();
  }

  void _backgroundChanged() {
    if (mounted) setState(() {});
  }

  void _connectionChanged() {
    if (mounted) setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      widget.controller.backgroundLive.refreshStatus();
      unawaited(_loadShellSettings());
    }
  }

  Future<void> _loadShellSettings() async {
    final generation = ++_shellLoadGeneration;
    setState(() {
      _loadingShell = true;
      _shellError = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final settings = await repository.loadTerminalShellSettings();
      if (mounted && generation == _shellLoadGeneration) {
        setState(() => _shellSettings = settings);
      }
    } catch (error) {
      if (mounted && generation == _shellLoadGeneration) {
        setState(() => _shellError = error.toString());
      }
    } finally {
      if (mounted && generation == _shellLoadGeneration) {
        setState(() => _loadingShell = false);
      }
    }
  }

  Future<void> _chooseShell() async {
    final settings = _shellSettings;
    if (_savingShell) return;
    if (settings == null) {
      await _loadShellSettings();
      return;
    }
    final choices = _shellChoices(settings);
    final selected = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(
            maxHeight: MediaQuery.sizeOf(context).height * 0.8,
          ),
          child: ListView(
            shrinkWrap: true,
            children: [
              const ListTile(
                leading: Icon(Icons.terminal_rounded),
                title: Text('Default shell'),
                subtitle: Text(
                  'Used by new terminals and compatible shell commands on this OpenCode server.',
                ),
              ),
              for (final choice in choices)
                ListTile(
                  key: ValueKey('server-shell-${choice.id}'),
                  leading: Icon(
                    choice.value == settings.selected
                        ? Icons.radio_button_checked_rounded
                        : Icons.radio_button_off_rounded,
                  ),
                  title: Text(choice.label),
                  subtitle: choice.terminalOnly
                      ? const Text(
                          'Terminal only; OpenCode uses a compatible fallback for shell tools.',
                        )
                      : null,
                  onTap: () => Navigator.pop(context, choice.value),
                ),
            ],
          ),
        ),
      ),
    );
    if (selected == null || selected == settings.selected || !mounted) return;

    setState(() => _savingShell = true);
    final locationRevision = widget.controller.locationRevision;
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      await repository.selectTerminalShell(selected);
      if (!mounted) return;
      if (locationRevision == widget.controller.locationRevision) {
        setState(
          () => _shellSettings = TerminalShellSettings(
            selected: selected,
            options: settings.options,
          ),
        );
      }
      await _loadShellSettings();
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Default shell updated')));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _savingShell = false);
    }
  }

  List<_ShellChoice> _shellChoices(TerminalShellSettings settings) {
    final nameCounts = <String, int>{};
    for (final option in settings.options) {
      nameCounts.update(option.name, (count) => count + 1, ifAbsent: () => 1);
    }
    final choices = <_ShellChoice>[
      const _ShellChoice(
        id: 'automatic',
        value: '',
        label: 'Automatic (server default)',
        terminalOnly: false,
      ),
    ];
    final values = <String>{''};
    for (final option in settings.options) {
      final ambiguous = nameCounts[option.name] != 1;
      final value = ambiguous ? option.path : option.name;
      if (!values.add(value)) continue;
      choices.add(
        _ShellChoice(
          id: option.path,
          value: value,
          label: ambiguous ? option.path : option.name,
          terminalOnly: !option.acceptable,
        ),
      );
    }
    if (settings.selected.isNotEmpty && values.add(settings.selected)) {
      choices.add(
        _ShellChoice(
          id: settings.selected,
          value: settings.selected,
          label: settings.selected,
          terminalOnly: false,
        ),
      );
    }
    return choices;
  }

  String _selectedShellLabel(TerminalShellSettings settings) {
    if (settings.selected.isEmpty) return 'Automatic (server default)';
    final choices = _shellChoices(settings);
    for (final choice in choices) {
      if (choice.value == settings.selected) return choice.label;
    }
    return settings.selected;
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
    final managedLocally = TermuxBridge.managesServerUrl(profile?.baseUrl);
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
      appBar: AppBar(title: const Text('Settings and server')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          const SectionLabel('Connection'),
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
                  ? AppTheme.success(Theme.of(context).colorScheme)
                  : Theme.of(context).colorScheme.error,
            ),
            title: Text(
              _health?.healthy == true
                  ? 'Server healthy'
                  : 'Health unavailable',
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
                  builder: (_) =>
                      HostManagementScreen(controller: controller),
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
          const SectionLabel('Background connection'),
          SwitchListTile(
            secondary: const Icon(Icons.sync_lock_rounded),
            title: const Text('Keep coding session live'),
            subtitle: const Text(
              'Keeps server events and terminals connected while this app is in the background. '
              'Uses more battery and shows a persistent Android notification.',
            ),
            value: controller.keepLiveInBackground,
            onChanged: controller.backgroundLive.busy
                ? null
                : (value) async {
                    final enabled = await controller.setKeepLiveInBackground(
                      value,
                    );
                    if (!context.mounted) return;
                    final error = controller.backgroundLive.lastError;
                    if (error != null || enabled != value) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            error ?? 'Android did not enable background mode.',
                          ),
                        ),
                      );
                    }
                  },
          ),
          if (controller.keepLiveInBackground)
            ListTile(
              leading: Icon(
                controller.backgroundLive.batteryOptimizationIgnored
                    ? Icons.battery_charging_full_rounded
                    : Icons.battery_alert_outlined,
              ),
              title: Text(
                controller.backgroundLive.batteryOptimizationIgnored
                    ? 'Unrestricted battery access allowed'
                    : 'Allow unrestricted battery access',
              ),
              subtitle: Text(
                controller.backgroundLive.batteryOptimizationIgnored
                    ? 'Android may still apply its foreground-service time limit.'
                    : 'Optional. Helps preserve the live connection during Doze. '
                          'Android 15+ limits data-sync background work to six hours per 24 hours.',
              ),
              trailing: controller.backgroundLive.batteryOptimizationIgnored
                  ? const Icon(Icons.check_rounded)
                  : const Icon(Icons.open_in_new_rounded),
              onTap: controller.backgroundLive.batteryOptimizationIgnored
                  ? null
                  : () async {
                      await controller.backgroundLive
                          .requestBatteryOptimizationExemption();
                    },
            ),
          const SectionLabel('Access control'),
          ListTile(
            key: const ValueKey('saved-permissions-entry'),
            leading: const Icon(Icons.admin_panel_settings_outlined),
            title: const Text('Always allowed actions'),
            subtitle: const Text(
              'Review or revoke durable OpenCode permissions for this project',
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => SavedPermissionsScreen(controller: controller),
              ),
            ),
          ),
          const SectionLabel('Appearance'),
          ValueListenableBuilder<AppAppearance>(
            valueListenable: controller.appearance,
            builder: (context, appearance, _) => ListTile(
              key: const ValueKey('appearance-settings-entry'),
              leading: const Icon(Icons.palette_outlined),
              title: const Text('Theme'),
              subtitle: Text(appearanceLabel(appearance)),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () =>
                  showAppearancePicker(context, controller: controller),
            ),
          ),
          const SectionLabel('Defaults'),
          ListTile(
            key: const ValueKey('default-shell-settings-entry'),
            leading: const Icon(Icons.terminal_rounded),
            title: const Text('Default shell'),
            subtitle: Text(
              _shellError != null
                  ? '${_shellError!} Tap to retry.'
                  : _shellSettings == null
                  ? 'Loading shells from OpenCode…'
                  : _selectedShellLabel(_shellSettings!),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: _loadingShell || _savingShell
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.chevron_right_rounded),
            onTap: _loadingShell || _savingShell ? null : _chooseShell,
          ),
          ListTile(
            leading: const Icon(Icons.model_training_outlined),
            title: const Text('Selected model'),
            subtitle: Text(
              controller.selectedModel == null
                  ? 'Server default'
                  : [
                      '${presentedProviderName(controller.selectedModel!.providerID, controller.catalog?.providers ?? const [])} · ${controller.selectedModel!.modelID}',
                      if (controller.selectedVariant.isNotEmpty)
                        controller.selectedVariant,
                    ].join(' · '),
              style: const TextStyle(fontFamily: 'AppMono', fontSize: 12),
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => showModelPicker(context),
          ),
          ListTile(
            leading: const Icon(Icons.support_agent_outlined),
            title: const Text('Selected agent'),
            subtitle: Text(
              controller.selectedAgent.isEmpty
                  ? 'Server default'
                  : controller.selectedAgent,
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => showModelPicker(context),
          ),
          const SectionLabel('Experimental'),
          const ListTile(
            leading: Icon(Icons.science_outlined),
            title: Text('Workspaces'),
            subtitle: Text(
              'Workspace and worktree switching is available from the Workspace tab. '
              'Availability depends on the connected server.',
            ),
          ),
          const SectionLabel('Diagnostics'),
          ListenableBuilder(
            listenable: controller.diagnostics,
            builder: (context, _) {
              final count = controller.diagnostics.count;
              return ListTile(
                key: const ValueKey('app-diagnostics-entry'),
                leading: const Icon(Icons.health_and_safety_outlined),
                title: const Text('App diagnostics'),
                subtitle: Text(
                  count == 0
                      ? 'No captured errors'
                      : '$count handled error${count == 1 ? '' : 's'} kept in memory',
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) =>
                        AppDiagnosticsScreen(controller: controller),
                  ),
                ),
              );
            },
          ),
          const SectionLabel('About'),
          ListTile(
            leading: const Icon(Icons.privacy_tip_outlined),
            title: const Text('Privacy and data use'),
            subtitle: const Text(
              'Servers, providers, voice, files, Termux, and updates',
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(builder: (_) => const AboutScreen()),
            ),
          ),
          ListTile(
            leading: const Icon(Icons.policy_outlined),
            title: const Text('Voice licenses and provenance'),
            subtitle: const Text(
              'Whisper models, sherpa-onnx, ONNX Runtime, and record',
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => showVoiceNotices(context),
          ),
          ListTile(
            leading: const Icon(Icons.info_outline_rounded),
            title: const Text('About and open source notices'),
            subtitle: const Text(
              'App details, components, and license notices',
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => const AboutScreen(initialTab: 1),
              ),
            ),
          ),
          const SizedBox(height: 20),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: Theme.of(context).colorScheme.error,
              ),
              onPressed: () async {
                await controller.disconnect();
                if (context.mounted) {
                  Navigator.of(
                    context,
                  ).pushNamedAndRemoveUntil('/servers', (_) => false);
                }
              },
              icon: const Icon(Icons.link_off_rounded),
              label: const Text('Disconnect'),
            ),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _shellLoadGeneration++;
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_connectionChanged);
    widget.controller.backgroundLive.removeListener(_backgroundChanged);
    super.dispose();
  }
}

class _ShellChoice {
  final String id;
  final String value;
  final String label;
  final bool terminalOnly;

  const _ShellChoice({
    required this.id,
    required this.value,
    required this.label,
    required this.terminalOnly,
  });
}
