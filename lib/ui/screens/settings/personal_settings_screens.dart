part of '../settings_screen.dart';

/// Appearance category: light/dark mode plus the theme-pack picker with
/// live swatch previews.
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
              leading: const Icon(Icons.brightness_6_outlined),
              title: const Text('Light or dark'),
              subtitle: Text(appearanceLabel(appearance)),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () =>
                  showAppearancePicker(context, controller: controller),
            ),
          ),
          const SectionLabel('Theme'),
          ListenableBuilder(
            listenable: Listenable.merge([
              controller.themePack,
              harvestedDynamicPack,
            ]),
            builder: (context, _) {
              final selected = controller.themePack.value;
              final brightness = Theme.of(context).brightness;
              return Column(
                children: [
                  for (final id in ThemePackId.values)
                    _ThemePackTile(
                      id: id,
                      selected: selected == id,
                      brightness: brightness,
                      available:
                          id != ThemePackId.dynamic ||
                          harvestedDynamicPack.value != null,
                      onSelect: () async {
                        try {
                          await controller.setThemePack(id);
                        } catch (error) {
                          if (!context.mounted) return;
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text('Could not save the theme: $error'),
                            ),
                          );
                        }
                      },
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}

class _ThemePackTile extends StatelessWidget {
  final ThemePackId id;
  final bool selected;
  final bool available;
  final Brightness brightness;
  final VoidCallback onSelect;

  const _ThemePackTile({
    required this.id,
    required this.selected,
    required this.available,
    required this.brightness,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    final pack = id == ThemePackId.dynamic
        ? harvestedDynamicPack.value
        : themePack(id);
    final palette = pack?.palette(brightness);
    final swatches = palette == null
        ? const <Color>[]
        : [
            palette.background,
            palette.scheme.surfaceContainerHigh,
            palette.scheme.primary,
            palette.success,
          ];
    return ListTile(
      key: ValueKey('theme-pack-${id.name}'),
      enabled: available,
      leading: SizedBox(
        width: 56,
        child: palette == null
            ? const Icon(Icons.auto_awesome_outlined)
            : Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  for (final color in swatches)
                    Container(
                      width: 12,
                      height: 24,
                      margin: const EdgeInsets.only(right: 2),
                      decoration: BoxDecoration(
                        color: color,
                        borderRadius: BorderRadius.circular(3),
                        border: Border.all(
                          color: Theme.of(context).colorScheme.outlineVariant,
                        ),
                      ),
                    ),
                ],
              ),
      ),
      title: Text(themePackLabels[id]!),
      subtitle: Text(
        !available
            ? 'Needs Android 12 or newer'
            : pack?.tagline ?? 'This phone’s Material You colors',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: selected ? const Icon(Icons.check_rounded) : null,
      onTap: available ? onSelect : null,
    );
  }
}

/// Privacy & permissions category: durable OpenCode grants, and the unsent
/// work this device is holding on the user's behalf.
class PrivacySettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const PrivacySettingsScreen({super.key, required this.controller});

  @override
  State<PrivacySettingsScreen> createState() => _PrivacySettingsScreenState();
}

class _PrivacySettingsScreenState extends State<PrivacySettingsScreen> {
  bool _busy = false;

  ConnectionController get _controller => widget.controller;

  /// Rounded the way a phone's storage screens round: one decimal past a
  /// kilobyte, and never "0 B" for something that exists.
  static String formatBytes(int bytes) {
    if (bytes <= 0) return '0 B';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    }
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  Future<void> _confirmAndClear({
    required String title,
    required String body,
    required Future<bool> Function() clear,
    required String cleared,
    required String failed,
  }) async {
    final ok = await showConfirmSheet(
      context,
      title: title,
      message: body,
      confirmLabel: 'Delete',
      icon: Icons.delete_outline_rounded,
      destructive: true,
    );
    if (!ok || !mounted) return;
    setState(() => _busy = true);
    final succeeded = await clear();
    if (!mounted) return;
    setState(() => _busy = false);
    final messenger = ScaffoldMessenger.of(context)..hideCurrentSnackBar();
    messenger.showSnackBar(
      SnackBar(
        content: Text(succeeded ? cleared : failed),
        backgroundColor: succeeded ? null : Theme.of(context).colorScheme.error,
      ),
    );
  }

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
                builder: (_) => SavedPermissionsScreen(controller: _controller),
              ),
            ),
          ),
          const SectionLabel('On this device'),
          // Queued prompts carry attachment data URLs and drafts carry
          // whatever was typed but never sent. Both are the user's content,
          // held indefinitely until a server answers, so both get a size and
          // a way out that does not require deleting the server.
          ListenableBuilder(
            listenable: _controller,
            builder: (context, _) {
              final queued = _controller.totalQueuedPromptCount;
              final drafts = _controller.totalSessionDraftCount;
              final queuedBytes = _controller.queuedPromptBytes;
              final draftBytes = _controller.sessionDraftBytes;
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ListTile(
                    key: const ValueKey('local-storage-usage'),
                    leading: const Icon(Icons.sd_storage_outlined),
                    title: const Text('Storage used'),
                    subtitle: Text(
                      '${formatBytes(queuedBytes + draftBytes)} of unsent '
                      'work — $queued queued '
                      '${queued == 1 ? 'prompt' : 'prompts'} '
                      '(${formatBytes(queuedBytes)}) and $drafts '
                      '${drafts == 1 ? 'draft' : 'drafts'} '
                      '(${formatBytes(draftBytes)}). Queued prompts are '
                      'discarded after '
                      '${OfflineQueueStore.maxAge.inDays} days.',
                    ),
                  ),
                  ListTile(
                    key: const ValueKey('clear-queued-prompts'),
                    leading: const Icon(Icons.outbox_outlined),
                    title: const Text('Clear queued prompts'),
                    subtitle: Text(
                      queued == 0
                          ? 'Nothing is waiting to send'
                          : 'Deletes all $queued unsent '
                                '${queued == 1 ? 'prompt' : 'prompts'} and '
                                'their attachments, for every server',
                    ),
                    enabled: queued > 0 && !_busy,
                    onTap: () => _confirmAndClear(
                      title: 'Delete queued prompts?',
                      body:
                          'This deletes $queued unsent '
                          '${queued == 1 ? 'prompt' : 'prompts'} and any '
                          'attachments they carry, for every server. They '
                          'will never be sent. Nothing on the server is '
                          'affected.',
                      clear: _controller.clearAllQueuedPrompts,
                      cleared: 'Queued prompts deleted',
                      failed:
                          'Could not delete the queued prompts. Check device '
                          'storage and try again.',
                    ),
                  ),
                  ListTile(
                    key: const ValueKey('clear-session-drafts'),
                    leading: const Icon(Icons.edit_note_outlined),
                    title: const Text('Clear drafts'),
                    subtitle: Text(
                      drafts == 0
                          ? 'No saved composer text'
                          : 'Deletes composer text saved for $drafts '
                                '${drafts == 1 ? 'session' : 'sessions'}',
                    ),
                    enabled: drafts > 0 && !_busy,
                    onTap: () => _confirmAndClear(
                      title: 'Delete drafts?',
                      body:
                          'This deletes the composer text saved for $drafts '
                          '${drafts == 1 ? 'session' : 'sessions'}. Nothing '
                          'on the server is affected.',
                      clear: _controller.clearAllSessionDrafts,
                      cleared: 'Drafts deleted',
                      failed:
                          'Could not delete the drafts. Check device storage '
                          'and try again.',
                    ),
                  ),
                ],
              );
            },
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
          // The voice notices cover models this build can neither download
          // nor run off Android; the general notices below still list every
          // component that ships here.
          if (platformCapabilities.supportsVoice)
            ListTile(
              key: const Key('settings-voice-notices'),
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
        ],
      ),
    );
  }
}
