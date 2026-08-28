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
import 'guide_screen.dart';
import 'host_management_screen.dart';
import 'saved_permissions_screen.dart';

part 'settings/server_settings_screen.dart';
part 'settings/coding_settings_screen.dart';
part 'settings/background_settings_screen.dart';
part 'settings/personal_settings_screens.dart';

/// Settings hub: a connection summary plus one row per category, following
/// the hub-and-spoke pattern in docs/design-inspiration.md. Every detail
/// lives one level deeper in a focused sub-page.
class SettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const SettingsScreen({super.key, required this.controller});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  Health? _health;
  String? _healthError;
  bool _checking = false;

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

  void _open(Widget screen) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => screen),
    );
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final theme = Theme.of(context);
    final profile = controller.profile;
    final healthy = _health?.healthy == true;
    final healthLine = _checking
        ? 'Checking server health…'
        : _healthError != null
        ? 'Health unavailable'
        : healthy
        ? 'Server healthy · ${_health?.version ?? controller.version ?? 'unknown'}'
        : 'Version ${controller.version ?? 'unknown'}';
    return Scaffold(
      appBar: AppBar(title: const Text('Settings and server')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 24),
        children: [
          Card(
            key: const ValueKey('settings-connection-summary'),
            margin: const EdgeInsets.symmetric(horizontal: 4),
            clipBehavior: Clip.antiAlias,
            child: ListTile(
              minTileHeight: 72,
              leading: _CategoryIcon(
                icon: Icons.dns_outlined,
                color: healthy
                    ? AppTheme.success(theme.colorScheme)
                    : _healthError != null
                    ? theme.colorScheme.error
                    : null,
              ),
              title: Text(
                profile?.name ?? 'OpenCode server',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Text(
                '${profile?.baseUrl ?? 'Not connected'}\n$healthLine',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontFamily: 'AppMono', fontSize: 11),
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
          ),
          const SizedBox(height: 8),
          _CategoryRow(
            rowKey: 'settings-category-server',
            icon: Icons.dns_outlined,
            title: 'Server',
            onTap: () => _open(ServerSettingsScreen(controller: controller)),
          ),
          _CategoryRow(
            rowKey: 'settings-category-coding',
            icon: Icons.terminal_rounded,
            title: 'Coding defaults',
            onTap: () => _open(CodingSettingsScreen(controller: controller)),
          ),
          _CategoryRow(
            rowKey: 'settings-category-background',
            icon: Icons.notifications_active_outlined,
            title: 'Notifications & background',
            onTap: () =>
                _open(BackgroundSettingsScreen(controller: controller)),
          ),
          _CategoryRow(
            rowKey: 'settings-category-appearance',
            icon: Icons.palette_outlined,
            title: 'Appearance',
            onTap: () =>
                _open(AppearanceSettingsScreen(controller: controller)),
          ),
          _CategoryRow(
            rowKey: 'settings-category-privacy',
            icon: Icons.admin_panel_settings_outlined,
            title: 'Privacy & permissions',
            onTap: () => _open(PrivacySettingsScreen(controller: controller)),
          ),
          _CategoryRow(
            rowKey: 'settings-category-diagnostics',
            icon: Icons.health_and_safety_outlined,
            title: 'Diagnostics',
            onTap: () =>
                _open(DiagnosticsSettingsScreen(controller: controller)),
          ),
          _CategoryRow(
            rowKey: 'settings-category-about',
            icon: Icons.info_outline_rounded,
            title: 'About',
            onTap: () => _open(AboutSettingsScreen(controller: controller)),
          ),
          const SizedBox(height: 20),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: OutlinedButton.icon(
              style: OutlinedButton.styleFrom(
                foregroundColor: theme.colorScheme.error,
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
    widget.controller.removeListener(_connectionChanged);
    super.dispose();
  }
}

class _CategoryRow extends StatelessWidget {
  final String rowKey;
  final IconData icon;
  final String title;
  final VoidCallback onTap;

  const _CategoryRow({
    required this.rowKey,
    required this.icon,
    required this.title,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      key: ValueKey(rowKey),
      minTileHeight: 56,
      leading: _CategoryIcon(icon: icon),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
  }
}

class _CategoryIcon extends StatelessWidget {
  final IconData icon;
  final Color? color;

  const _CategoryIcon({required this.icon, this.color});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final tint = color ?? theme.colorScheme.onSurfaceVariant;
    return Container(
      width: 36,
      height: 36,
      decoration: BoxDecoration(
        color: tint.withValues(alpha: .12),
        borderRadius: BorderRadius.circular(11),
      ),
      child: Icon(icon, size: 20, color: tint),
    );
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
