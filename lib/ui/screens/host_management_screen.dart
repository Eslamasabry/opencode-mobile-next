import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import '../app_theme.dart';

/// Host-side management for a remote OpenCode server.
///
/// The app cannot execute commands on the host, so this surface is truthful
/// by construction: it shows the server facts the app already knows and
/// provides exact, copyable commands for the documented Ubuntu helper
/// script. It never claims the app performed a host action.
class HostManagementScreen extends StatelessWidget {
  const HostManagementScreen({super.key, required this.controller});

  final ConnectionController controller;

  static const scriptUrl =
      'https://raw.githubusercontent.com/Eslamasabry/oc_app/'
      'master/scripts/host/ubuntu-opencode.sh';

  int _serverPort() {
    final uri = Uri.tryParse(controller.profile?.baseUrl ?? '');
    if (uri == null || uri.host.isEmpty) return 4096;
    return uri.hasPort ? uri.port : (uri.scheme == 'https' ? 443 : 80);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final profile = controller.profile;
    final port = _serverPort();
    return Scaffold(
      appBar: AppBar(title: const Text('Ubuntu host management')),
      body: ListenableBuilder(
        listenable: controller,
        builder: (context, _) => ListView(
          padding: const EdgeInsets.only(bottom: 28),
          children: [
            const SectionLabel('This server'),
            ListTile(
              leading: const Icon(Icons.dns_outlined),
              title: Text(profile?.name ?? 'OpenCode server'),
              subtitle: SelectableText(
                profile?.baseUrl ?? 'Not connected',
                style: const TextStyle(
                  fontFamily: AppTheme.monoFamily,
                  fontSize: AppTheme.codeFontSize,
                ),
              ),
            ),
            ListTile(
              leading: const Icon(Icons.info_outline_rounded),
              title: Text('Server version ${controller.version ?? 'unknown'}'),
              subtitle: const Text(
                'These commands run on the computer that hosts this server — '
                'the app cannot run them for you. Copy each one into a '
                'terminal on that machine.',
              ),
            ),
            const SectionLabel('First-time setup — run on your computer'),
            _HostCommandTile(
              label: 'Install OpenCode as a background service',
              detail:
                  'Official installer plus a systemd user service that '
                  'survives closed terminals and reboots.',
              command:
                  'curl -fsSL $scriptUrl -o ubuntu-opencode.sh && '
                  'OPENCODE_PORT=$port bash ubuntu-opencode.sh install',
            ),
            _HostCommandTile(
              label: 'Keep it running after logout',
              command: 'loginctl enable-linger "\$USER"',
            ),
            _HostCommandTile(
              label: 'Read the server password for this app',
              command: 'bash ubuntu-opencode.sh password',
            ),
            // `adb reverse` forwards a port to an attached *Android* device.
            // On desktop the app and the server share a machine, so the tile
            // described a cable that is not there.
            if (platformCapabilities.supportsUsbHostBridge)
              _HostCommandTile(
                key: const Key('host-command-adb-reverse'),
                label: 'Reach it from this phone over USB',
                command: 'adb reverse tcp:$port tcp:$port',
              ),
            const SectionLabel('Day-to-day — run on your computer'),
            _HostCommandTile(
              label: 'Service status',
              command: 'bash ubuntu-opencode.sh status',
            ),
            _HostCommandTile(
              label: 'Restart the server',
              command: 'bash ubuntu-opencode.sh restart',
            ),
            _HostCommandTile(
              label: 'Follow the server log',
              command: 'bash ubuntu-opencode.sh logs',
            ),
            _HostCommandTile(
              label: 'Update OpenCode on the host',
              detail:
                  'When the server reports an update, Settings offers the '
                  'native upgrade first; this is the host-side equivalent.',
              command: 'bash ubuntu-opencode.sh update',
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
              child: Text(
                'Full walkthrough: docs/ubuntu-host.md in the app repository.',
                style: theme.textTheme.bodySmall?.copyWith(
                  color: AppTheme.mutedOf(theme),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _HostCommandTile extends StatelessWidget {
  const _HostCommandTile({
    super.key,
    required this.label,
    required this.command,
    this.detail,
  });

  final String label;
  final String? detail;
  final String command;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 6, 16, 6),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(14, 10, 6, 10),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: AppTheme.hairline(theme)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  if (detail != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                        detail!,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: AppTheme.mutedOf(theme),
                        ),
                      ),
                    ),
                  const SizedBox(height: 6),
                  SelectableText(
                    command,
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontFamily: AppTheme.monoFamily,
                      fontSize: AppTheme.codeFontSize,
                    ),
                  ),
                ],
              ),
            ),
            Semantics(
              button: true,
              label: 'Copy command: $label',
              child: IconButton(
                key: ValueKey('copy-host-command-$label'),
                tooltip: 'Copy command',
                onPressed: () async {
                  await Clipboard.setData(ClipboardData(text: command));
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text(
                          'Copied. Run it on the server\'s computer.',
                        ),
                        duration: Duration(seconds: 2),
                      ),
                    );
                  }
                },
                icon: const Icon(AppIcons.copy, size: 19),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
