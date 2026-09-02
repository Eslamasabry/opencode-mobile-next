import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

/// Screenshot tests for the component system across every theme pack and
/// both brightnesses. One image per pack/mode of the primitives every screen
/// is built from, so a palette or component-theme change shows up as a diff
/// instead of a surprise on a phone.
///
/// Regenerate deliberately with `flutter test --update-goldens test/goldens`.
void main() {
  setUpAll(() async {
    await _loadFont('AppMono', const [
      'assets/fonts/JetBrainsMono-Regular.ttf',
      'assets/fonts/JetBrainsMono-Medium.ttf',
      'assets/fonts/JetBrainsMono-SemiBold.ttf',
      'assets/fonts/JetBrainsMono-Bold.ttf',
    ]);
    await _loadFont('AppDisplay', const [
      'assets/fonts/SpaceGrotesk-Regular.ttf',
      'assets/fonts/SpaceGrotesk-Medium.ttf',
      'assets/fonts/SpaceGrotesk-SemiBold.ttf',
      'assets/fonts/SpaceGrotesk-Bold.ttf',
    ]);
  });

  for (final id in ThemePackId.values.where((p) => p != ThemePackId.dynamic)) {
    for (final brightness in Brightness.values) {
      final mode = brightness == Brightness.dark ? 'dark' : 'light';
      testWidgets('component gallery · ${id.name} · $mode', (tester) async {
        final theme = AppTheme.fromPalette(themePack(id).palette(brightness));
        tester.view.physicalSize = const Size(420, 900);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);

        await tester.pumpWidget(
          MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: theme,
            home: const _Gallery(),
          ),
        );
        await tester.pumpAndSettle();

        await expectLater(
          find.byType(_Gallery),
          matchesGoldenFile('gallery_${id.name}_$mode.png'),
        );
      });
    }
  }
}

Future<void> _loadFont(String family, List<String> assets) async {
  final loader = FontLoader(family);
  for (final asset in assets) {
    loader.addFont(rootBundle.load(asset));
  }
  await loader.load();
}

class _Gallery extends StatelessWidget {
  const _Gallery();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Gallery'),
        actions: [
          IconButton(icon: const Icon(Icons.tune_rounded), onPressed: () {}),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Headline', style: theme.textTheme.headlineSmall),
          Text('Title large', style: theme.textTheme.titleLarge),
          Text(
            'Body text at the default size.',
            style: theme.textTheme.bodyMedium,
          ),
          Text(
            'Supporting caption',
            style: theme.textTheme.bodySmall?.copyWith(
              color: AppTheme.mutedOf(theme),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              FilledButton(onPressed: () {}, child: const Text('Filled')),
              const SizedBox(width: 8),
              OutlinedButton(onPressed: () {}, child: const Text('Outlined')),
              const SizedBox(width: 8),
              TextButton(onPressed: () {}, child: const Text('Text')),
            ],
          ),
          const SizedBox(height: 16),
          const TextField(
            decoration: InputDecoration(hintText: 'Ask OpenCode…'),
          ),
          const SizedBox(height: 16),
          Card(
            child: ListTile(
              leading: const Icon(Icons.chat_bubble_outline_rounded),
              title: const Text('Session title'),
              subtitle: const Text('Updated 2 minutes ago'),
              trailing: const Icon(Icons.chevron_right_rounded),
            ),
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            children: [
              for (final tone in AppStatusTone.values)
                Chip(
                  avatar: CircleAvatar(
                    backgroundColor: AppTheme.statusColor(theme, tone),
                  ),
                  label: Text(tone.name),
                ),
            ],
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(AppTheme.radiusControl),
              border: Border.all(color: AppTheme.hairline(theme)),
            ),
            child: Text(
              'final answer = await agent.run();',
              style: TextStyle(
                fontFamily: AppTheme.monoFamily,
                fontSize: AppTheme.codeFontSize,
                color: scheme.onSurface,
              ),
            ),
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(AppTheme.radiusCard),
              border: Border.all(color: AppTheme.hairline(theme)),
              boxShadow: AppTheme.raised(theme),
            ),
            child: Row(
              children: [
                Icon(Icons.shield_outlined, color: scheme.primary),
                const SizedBox(width: 10),
                const Expanded(child: Text('Raised surface with shadow')),
              ],
            ),
          ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: 0,
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.workspaces_outline),
            label: 'Workspace',
          ),
          NavigationDestination(
            icon: Icon(Icons.folder_outlined),
            label: 'Files',
          ),
          NavigationDestination(
            icon: Icon(Icons.notifications_outlined),
            label: 'Activity',
          ),
          NavigationDestination(
            icon: Icon(Icons.more_horiz_rounded),
            label: 'More',
          ),
        ],
      ),
    );
  }
}
