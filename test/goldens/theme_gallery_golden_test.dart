import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
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
      'assets/fonts/geist/GeistMono-Variable.ttf',
    ]);
    await _loadFont('AppSans', const ['assets/fonts/geist/Geist-Variable.ttf']);
    final body = FontLoader('Roboto');
    for (final weight in ['Regular', 'Medium', 'Bold']) {
      body.addFont(
        File(
          'tool/capture/fonts/Roboto-$weight.ttf',
        ).readAsBytes().then(ByteData.sublistView),
      );
    }
    await body.load();
    // The families AppTheme.forLocale names for Arabic, as the kit gallery
    // loads them, so the Arabic goldens read like a device.
    const noto = [
      'test/fixtures/fonts/NotoSansArabic-Regular.ttf',
      'test/fixtures/fonts/NotoSansArabic-Bold.ttf',
    ];
    for (final family in const ['Noto Sans Arabic', 'sans-serif']) {
      final loader = FontLoader(family);
      final files = family == 'sans-serif'
          ? [
              'tool/capture/fonts/Roboto-Regular.ttf',
              'tool/capture/fonts/Roboto-Medium.ttf',
              'tool/capture/fonts/Roboto-Bold.ttf',
            ]
          : noto;
      for (final path in files) {
        loader.addFont(File(path).readAsBytes().then(ByteData.sublistView));
      }
      await loader.load();
    }
    await _loadFont('AppPhosphorRegular', [
      'assets/fonts/phosphor/Phosphor.ttf',
    ]);
    await _loadFont('AppPhosphorFill', [
      'assets/fonts/phosphor/Phosphor-Fill.ttf',
    ]);
    await _loadFont('AppPhosphorDuotone', [
      'assets/fonts/phosphor/Phosphor-Duotone.ttf',
    ]);
  });

  // Goldens for the hand-written packs. The generated ones share one recipe
  // and are held to a contrast floor in theme_packs_test.dart instead.
  for (final id in curatedThemePacks) {
    for (final brightness in Brightness.values) {
      final mode = brightness == Brightness.dark ? 'dark' : 'light';
      for (final locale in const [Locale('en'), Locale('ar')]) {
        final arabic = locale.languageCode == 'ar';
        final suffix = arabic ? '_ar' : '';
        testWidgets(
          'component gallery · ${id.name} · $mode${arabic ? ' · ar' : ''}',
          (tester) async {
            final theme = AppTheme.forLocale(
              AppTheme.fromPalette(themePack(id).palette(brightness)),
              locale,
            );
            tester.view.physicalSize = const Size(420, 900);
            tester.view.devicePixelRatio = 1;
            addTearDown(tester.view.reset);

            await tester.pumpWidget(
              MaterialApp(
                debugShowCheckedModeBanner: false,
                theme: theme,
                locale: locale,
                supportedLocales: const [Locale('en'), Locale('ar')],
                localizationsDelegates: GlobalMaterialLocalizations.delegates,
                home: _Gallery(arabic: arabic),
              ),
            );
            await tester.pumpAndSettle();

            // Right to left comes from the locale, not from the test.
            expect(
              Directionality.of(tester.element(find.byType(_Gallery))),
              arabic ? TextDirection.rtl : TextDirection.ltr,
            );
            await expectLater(
              find.byType(_Gallery),
              matchesGoldenFile('gallery_${id.name}_$mode$suffix.png'),
            );
          },
        );
      }
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
  const _Gallery({this.arabic = false});

  /// The same rows with Arabic copy, laid out right to left.
  final bool arabic;

  String _t(String en, String ar) => arabic ? ar : en;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: Text(_t('Gallery', 'المعرض')),
        actions: [
          IconButton(
            icon: const Icon(AppIconography.settings),
            onPressed: () {},
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text(
            _t('Headline', 'عنوان رئيسي'),
            style: theme.textTheme.headlineSmall,
          ),
          Text(
            _t('Title large', 'عنوان كبير'),
            style: theme.textTheme.titleLarge,
          ),
          Text(
            _t('Body text at the default size.', 'نص أساسي بالحجم الافتراضي.'),
            style: theme.textTheme.bodyMedium,
          ),
          Text(
            _t('Supporting caption', 'وصف مساند'),
            style: theme.textTheme.bodySmall?.copyWith(
              color: AppTheme.mutedOf(theme),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              FilledButton(
                onPressed: () {},
                child: Text(_t('Filled', 'مملوء')),
              ),
              const SizedBox(width: 8),
              OutlinedButton(
                onPressed: () {},
                child: Text(_t('Outlined', 'محدد')),
              ),
              const SizedBox(width: 8),
              TextButton(onPressed: () {}, child: Text(_t('Text', 'نص'))),
            ],
          ),
          const SizedBox(height: 16),
          TextField(
            decoration: InputDecoration(
              hintText: _t('Ask OpenCode…', 'اسأل OpenCode…'),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: ListTile(
              leading: const Icon(AppIconography.chat),
              title: Text(_t('Session title', 'عنوان الجلسة')),
              subtitle: Text(
                _t('Updated 2 minutes ago', 'تم التحديث قبل دقيقتين'),
              ),
              trailing: const Icon(AppIconography.chevronRight),
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
              borderRadius: BorderRadius.circular(KitTokens.fieldRadius),
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
              borderRadius: BorderRadius.circular(
                theme.extension<KitTokens>()!.panelCornerRadius,
              ),
              border: Border.all(color: AppTheme.hairline(theme)),
            ),
            child: Row(
              children: [
                Icon(AppIconography.shield, color: scheme.primary),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    _t(
                      'Panel on surface1, no shadow',
                      'لوحة على السطح الأول بلا ظل',
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      // No navigation bar: the app has none to theme since 2026-10-20 (the
      // kit draws the rail itself, and a phone has no bar).
    );
  }
}
