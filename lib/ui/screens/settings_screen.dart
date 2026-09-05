import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../api/provider_presentation.dart';
import '../../background/live_background.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../../state/offline_queue.dart';
import '../../state/profiles.dart';
import '../../termux/bridge.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import '../theme_packs.dart';
import '../../voice/notices.dart';
import '../widgets/appearance_picker.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/product_states.dart';
import '../widgets/pickers.dart';
import 'about_screen.dart';
import 'app_diagnostics_screen.dart';
import 'guide_screen.dart';
import 'host_management_screen.dart';
import 'saved_permissions_screen.dart';
import 'usage_screen.dart';

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
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      final health = await api.health();
      if (mounted) setState(() => _health = health);
    } catch (error) {
      if (mounted) setState(() => _healthError = productErrorText(error));
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  /// The background category's state at a glance, so the hub says whether
  /// runs keep updating after the app closes without opening the page.
  static String _backgroundSummary(ConnectionController controller) {
    final live = controller.backgroundLive;
    if (live.stoppedByAndroidTimeout) return 'Stopped by Android';
    if (!controller.keepLiveInBackground) return 'Off';
    return live.active ? 'On · running now' : 'On · starting';
  }

  Future<void> _open(Widget screen) async {
    await Navigator.of(
      context,
    ).push(MaterialPageRoute<void>(builder: (_) => screen));
    if (mounted) setState(() {});
  }

  /// What disconnecting actually costs, counted rather than described in the
  /// abstract: queued prompts and unsent drafts for this server stop moving
  /// until it is connected again.
  String _disconnectDisclosure() {
    final controller = widget.controller;
    final id = controller.profile?.id;
    final queued = id == null ? 0 : controller.queuedPromptCountForProfile(id);
    final drafts = id == null ? 0 : controller.draftCountForProfile(id);
    final pending = [
      if (queued > 0) '$queued queued ${queued == 1 ? 'prompt' : 'prompts'}',
      if (drafts > 0) '$drafts unsent ${drafts == 1 ? 'draft' : 'drafts'}',
    ];
    return [
      'Live updates stop and you return to the server list. The server keeps '
          'running; nothing on it is changed.',
      pending.isEmpty
          ? 'Nothing is waiting to send.'
          : '${pending.join(' and ')} will not be sent until you connect to '
                'this server again.',
    ].join('\n\n');
  }

  Future<void> _disconnect() async {
    final confirmed = await showConfirmSheet(
      context,
      title:
          'Disconnect from ${widget.controller.profile?.name ?? 'this server'}?',
      message: _disconnectDisclosure(),
      confirmLabel: 'Disconnect',
      icon: Icons.link_off_rounded,
      destructive: true,
      sheetKey: const ValueKey('disconnect-confirm-sheet'),
      confirmKey: const ValueKey('confirm-disconnect'),
    );
    if (!confirmed || !mounted) return;
    await widget.controller.disconnect();
    if (!mounted) return;
    Navigator.of(context).pushNamedAndRemoveUntil('/servers', (_) => false);
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
        ? 'Health unavailable — $_healthError'
        : healthy
        ? 'Server healthy · ${_health?.version ?? controller.version ?? 'unknown'}'
        : 'Version ${controller.version ?? 'unknown'}';
    return Scaffold(
      appBar: AppBar(title: const Text('Settings and server')),
      body: DesktopScrollbarArea(
        builder: (scrollController) => ListView(
          controller: scrollController,
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
                      ? AppTheme.successOf(theme)
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
                  style: const TextStyle(
                    fontFamily: AppTheme.monoFamily,
                    fontSize: AppTheme.captionFontSize,
                  ),
                ),
                trailing: IconButton(
                  tooltip: 'Check again',
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
            // The live background service and its notifications are Android
            // platform features; the category hides elsewhere.
            if (platformCapabilities.supportsBackgroundService)
              _CategoryRow(
                rowKey: 'settings-category-background',
                icon: Icons.notifications_active_outlined,
                title: 'Notifications & background',
                subtitle: _backgroundSummary(controller),
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
            if (controller.supportsUsageStatistics)
              _CategoryRow(
                rowKey: 'settings-category-usage',
                icon: Icons.bar_chart_rounded,
                title: lookupAppLocalizations(
                  Localizations.localeOf(context),
                ).usageTitle,
                onTap: () => _open(UsageScreen(controller: controller)),
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
                key: const ValueKey('settings-disconnect'),
                onPressed: _disconnect,
                icon: const Icon(Icons.link_off_rounded),
                label: const Text('Disconnect'),
              ),
            ),
          ],
        ),
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
  final String? subtitle;
  final VoidCallback onTap;

  const _CategoryRow({
    required this.rowKey,
    required this.icon,
    required this.title,
    required this.onTap,
    this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      key: ValueKey(rowKey),
      minTileHeight: 56,
      leading: _CategoryIcon(icon: icon),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: subtitle == null
          ? null
          : Text(subtitle!, maxLines: 1, overflow: TextOverflow.ellipsis),
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
        borderRadius: BorderRadius.circular(AppTheme.radiusControl),
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
