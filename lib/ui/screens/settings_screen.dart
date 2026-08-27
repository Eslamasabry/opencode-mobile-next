import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/provider_presentation.dart';
import '../../state/connection.dart';
import '../../termux/bridge.dart';
import '../../voice/notices.dart';
import '../widgets/product_states.dart';
import '../widgets/pickers.dart';

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

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.backgroundLive.addListener(_backgroundChanged);
    _checkHealth();
    widget.controller.backgroundLive.refreshStatus();
  }

  void _backgroundChanged() {
    if (mounted) setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      widget.controller.backgroundLive.refreshStatus();
    }
  }

  Future<void> _checkHealth() async {
    final api = widget.controller.api;
    if (api == null || _checking) return;
    setState(() {
      _checking = true;
      _healthError = null;
    });
    try {
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

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final profile = controller.profile;
    final managedLocally = TermuxBridge.managesServerUrl(profile?.baseUrl);
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
              style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
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
                  ? Colors.green.shade400
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
          ListTile(
            key: const Key('server-updates-tile'),
            leading: const Icon(Icons.system_update_alt_rounded),
            title: Text(
              managedLocally
                  ? 'Update managed OpenCode'
                  : 'Server updates are managed externally',
            ),
            subtitle: Text(
              managedLocally
                  ? 'Install the latest stable server, refresh models, restart safely, and reconnect.'
                  : 'Copy the official upgrade and model-refresh commands to run on the server host.',
            ),
            trailing: Icon(
              managedLocally ? Icons.chevron_right_rounded : Icons.copy_rounded,
            ),
            onTap: managedLocally
                ? () => Navigator.of(context).pushNamed('/termux-setup')
                : _copyRemoteUpdateCommands,
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
          const SectionLabel('Defaults'),
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
              style: const TextStyle(fontFamily: 'monospace', fontSize: 12),
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
          const SectionLabel('About'),
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
            onTap: () => Navigator.of(context).pushNamed('/about'),
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
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.backgroundLive.removeListener(_backgroundChanged);
    super.dispose();
  }
}
