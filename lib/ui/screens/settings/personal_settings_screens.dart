part of '../settings_screen.dart';

/// Appearance category: the theme choice, with room for future theme packs.
class AppearanceSettingsScreen extends StatelessWidget {
  final ConnectionController controller;
  const AppearanceSettingsScreen({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Appearance')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
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
        ],
      ),
    );
  }
}

/// Privacy & permissions category: durable OpenCode grants.
class PrivacySettingsScreen extends StatelessWidget {
  final ConnectionController controller;
  const PrivacySettingsScreen({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Privacy & permissions')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
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
        ],
      ),
    );
  }
}

/// Diagnostics category: the process-local error ring.
class DiagnosticsSettingsScreen extends StatelessWidget {
  final ConnectionController controller;
  const DiagnosticsSettingsScreen({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Diagnostics')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
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
        ],
      ),
    );
  }
}

/// About category: guide, privacy, licenses, and app details.
class AboutSettingsScreen extends StatelessWidget {
  final ConnectionController controller;
  const AboutSettingsScreen({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('About')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          ListTile(
            key: const ValueKey('settings-setup-guide'),
            leading: const Icon(Icons.menu_book_outlined),
            title: const Text('Setup guide'),
            subtitle: const Text(
              'Connect a computer or run OpenCode on this phone',
            ),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => GuideScreen(embedded: false),
              ),
            ),
          ),
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
            subtitle: const Text('App details, components, and license notices'),
            trailing: const Icon(Icons.chevron_right_rounded),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => const AboutScreen(initialTab: 1),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
