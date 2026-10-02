import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() => harvestedDynamicPack.value = null);

  test('the default pack is Graphite, the visual language palette', () {
    final dark = AppTheme.dark();
    expect(dark.colorScheme.primary, const Color(0xFF3DDC8A));
    expect(dark.colorScheme.onPrimary, const Color(0xFF03140B));
    expect(dark.colorScheme.surface, const Color(0xFF0B0C0E));
    expect(dark.colorScheme.onSurface, const Color(0xFFF3F3F1));
    expect(dark.colorScheme.surfaceContainerLow, const Color(0xFF141518));
    expect(dark.colorScheme.error, const Color(0xFFFF7A7A));
    expect(dark.scaffoldBackgroundColor, const Color(0xFF0B0C0E));
    expect(AppTheme.successOf(dark), const Color(0xFF3DDC8A));

    final light = AppTheme.light();
    expect(light.colorScheme.primary, const Color(0xFF087F43));
    expect(light.colorScheme.surfaceContainerLow, const Color(0xFFFFFFFF));
    expect(light.scaffoldBackgroundColor, const Color(0xFFF3F3F1));
    expect(AppTheme.successOf(light), const Color(0xFF087F43));
  });

  test('every static pack has complete, distinct dark and light palettes', () {
    for (final id in ThemePackId.values.where(
      (id) => id != ThemePackId.dynamic,
    )) {
      final pack = themePack(id);
      expect(pack.dark.scheme.brightness, Brightness.dark, reason: '$id');
      expect(pack.light.scheme.brightness, Brightness.light, reason: '$id');
      expect(pack.dark.background, isNot(pack.light.background), reason: '$id');
      // The pack's success, held to its floor, reaches the ThemeData
      // extension.
      expect(
        AppTheme.successOf(AppTheme.dark(pack)),
        pack.dark.themeRoles.success,
        reason: '$id',
      );
    }
  });

  test('every theme keeps text and controls readable in both modes', () {
    double contrast(Color a, Color b) {
      final la = a.computeLuminance();
      final lb = b.computeLuminance();
      final hi = la > lb ? la : lb;
      final lo = la > lb ? lb : la;
      return (hi + .05) / (lo + .05);
    }

    final failures = <String>[];
    void floor(String what, Color fg, Color bg, double min) {
      final ratio = contrast(fg, bg);
      if (ratio < min) {
        failures.add('$what ${ratio.toStringAsFixed(2)} < $min');
      }
    }

    // Every pack, the four hand-written ones included, reaches the app
    // through its role set, so the theme the app builds meets the visual
    // language's floors (LOOK-7, LOOK-8): text1 7:1 on every surface step,
    // text2 4.5:1, the accent 4.5:1 on the ground and surface1 (links are
    // text), its on-colour 4.5:1, and danger and success 4.5:1 on the ground.
    for (final id in ThemePackId.values.where(
      (id) => id != ThemePackId.dynamic,
    )) {
      for (final brightness in Brightness.values) {
        final pack = themePack(id);
        final theme = brightness == Brightness.dark
            ? AppTheme.dark(pack)
            : AppTheme.light(pack);
        final s = theme.colorScheme;
        final tag = '${id.name}/${brightness.name}';
        for (final surface in [
          theme.scaffoldBackgroundColor,
          s.surfaceContainerLow,
          s.surfaceContainer,
          s.surfaceContainerHigh,
          s.surfaceContainerHighest,
        ]) {
          floor('$tag text', s.onSurface, surface, 7);
          floor('$tag muted text', s.onSurfaceVariant, surface, 4.5);
        }
        floor('$tag on primary', s.onPrimary, s.primary, 4.5);
        floor('$tag accent', s.primary, theme.scaffoldBackgroundColor, 4.5);
        floor('$tag accent on surface1', s.primary, s.surfaceContainerLow, 4.5);
        floor(
          '$tag on primary container',
          s.onPrimaryContainer,
          s.primaryContainer,
          4.5,
        );
        floor(
          '$tag on secondary container',
          s.onSecondaryContainer,
          s.secondaryContainer,
          4.5,
        );
        floor(
          '$tag on error container',
          s.onErrorContainer,
          s.errorContainer,
          4.5,
        );
        floor('$tag error', s.error, theme.scaffoldBackgroundColor, 4.5);
        floor(
          '$tag success',
          AppTheme.successOf(theme),
          theme.scaffoldBackgroundColor,
          4.5,
        );
      }
    }
    expect(failures, isEmpty, reason: failures.join('\n'));
  });

  test('a stored theme from before the new ones still loads', () {
    // Stored by name, so adding packs in the middle of the enum is safe.
    expect(ThemePackId.values.byName('solarized'), ThemePackId.solarized);
    expect(ThemePackId.values.last, ThemePackId.dynamic);
    expect(ThemePackId.values.length, greaterThan(25));
    expect(themePackLabels.keys.toSet(), ThemePackId.values.toSet());
  });

  test('theme pack persistence round-trips through the store', () async {
    SharedPreferences.setMockInitialValues({});
    final preferences = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: preferences);
    expect(store.themePack, ThemePackId.opencode);
    await store.setThemePack(ThemePackId.gruvbox);
    expect(ProfileStore(prefs: preferences).themePack, ThemePackId.gruvbox);
  });

  testWidgets('switching packs restyles the app live', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      ListenableBuilder(
        listenable: Listenable.merge([
          controller.themePack,
          harvestedDynamicPack,
        ]),
        builder: (context, _) {
          final pack = effectiveThemePack(controller.themePack.value);
          return MaterialApp(
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            themeMode: ThemeMode.dark,
            theme: AppTheme.light(pack),
            darkTheme: AppTheme.dark(pack),
            home: const Scaffold(body: Text('themed')),
          );
        },
      ),
    );
    BuildContext context = tester.element(find.text('themed'));
    expect(Theme.of(context).colorScheme.primary, const Color(0xFF3DDC8A));

    await controller.setThemePack(ThemePackId.gruvbox);
    await tester.pumpAndSettle();
    context = tester.element(find.text('themed'));
    expect(Theme.of(context).colorScheme.primary, const Color(0xFFFE8019));
    expect(AppTheme.successOf(Theme.of(context)), const Color(0xFFB8BB26));
  });

  testWidgets('the appearance page picks packs and gates Material You', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        theme: AppTheme.light(),
        builder: (context, child) => MediaQuery(
          // Reduced motion: the preview sheet's working mark (KitThemePreview,
          // 063f4741) holds still, so the tree settles.
          data: MediaQuery.of(context).copyWith(
            textScaler: const TextScaler.linear(2),
            disableAnimations: true,
          ),
          child: child!,
        ),
        home: AppearanceSettingsScreen(controller: controller),
      ),
    );
    await tester.pump();

    // Unharvested Material You stays visible but disabled with truth.
    final dynamicTile = find.byKey(const ValueKey('theme-pack-dynamic'));
    await tester.dragUntilVisible(
      dynamicTile,
      find.byType(ListView),
      const Offset(0, -120),
    );
    expect(
      find.text('Material You colors are not available on this device.'),
      findsOneWidget,
    );
    await tester.tap(dynamicTile, warnIfMissed: false);
    await tester.pump();
    expect(controller.themePack.value, ThemePackId.opencode);

    final solarized = find.byKey(const ValueKey('theme-pack-solarized'));
    await tester.dragUntilVisible(
      solarized,
      find.byType(ListView),
      const Offset(0, -120),
    );
    // Centre the tile: at 2x its centre can still sit below the fold.
    await Scrollable.ensureVisible(tester.element(solarized), alignment: 0.5);
    await tester.pumpAndSettle();
    await tester.tap(solarized);
    await tester.pumpAndSettle();
    expect(controller.themePack.value, ThemePackId.opencode);
    // Apply ends the preview sheet's scrolling body; it is built before
    // it is on screen, so bring it into view rather than scroll until it
    // exists.
    await tester.ensureVisible(find.text('Apply'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Apply'));
    await tester.pumpAndSettle();
    expect(controller.themePack.value, ThemePackId.solarized);
    expect(tester.takeException(), isNull);
    // Apply offers Undo (063f4741); close its window.
    KitUndo.commitPending();
  });

  testWidgets('a harvested Material You pack becomes selectable', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    harvestedDynamicPack.value = dynamicThemePack(
      lightScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
      darkScheme: ColorScheme.fromSeed(
        seedColor: Colors.blue,
        brightness: Brightness.dark,
      ),
    );

    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        theme: AppTheme.light(),
        // Reduced motion: the preview sheet's working mark
        // (KitThemePreview, 063f4741) holds still, so the tree settles.
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: AppearanceSettingsScreen(controller: controller),
      ),
    );
    await tester.pump();

    final dynamicTile = find.byKey(const ValueKey('theme-pack-dynamic'));
    await tester.dragUntilVisible(
      dynamicTile,
      find.byType(ListView),
      const Offset(0, -120),
    );
    expect(
      find.text('Material You colors are not available on this device.'),
      findsNothing,
    );
    // Centre the swatch: the grid's last row can sit under the fold.
    await Scrollable.ensureVisible(tester.element(dynamicTile), alignment: 0.5);
    await tester.pumpAndSettle();
    await tester.tap(dynamicTile);
    await tester.pumpAndSettle();
    expect(controller.themePack.value, ThemePackId.opencode);
    // Apply ends the preview sheet's scrolling body; it is built before
    // it is on screen, so bring it into view rather than scroll until it
    // exists.
    await tester.ensureVisible(find.text('Apply'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Apply'));
    await tester.pumpAndSettle();
    expect(controller.themePack.value, ThemePackId.dynamic);
    // Apply offers Undo (063f4741); close its window.
    KitUndo.commitPending();
  });

  group('screen-settings-1: appearance', () {
    Widget page(ConnectionController controller) => MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: AppTheme.light(),
      home: AppearanceSettingsScreen(controller: controller),
    );

    testWidgets('light or dark is one tap on the page', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(page(controller));
      await tester.pumpAndSettle();

      await tester.tap(find.text('Light'));
      await tester.pumpAndSettle();
      expect(controller.appearance.value, AppAppearance.light);
      // No sheet opened on the way.
      expect(find.byKey(const Key('appearance-picker')), findsNothing);

      await tester.tap(find.text('Dark'));
      await tester.pumpAndSettle();
      expect(controller.appearance.value, AppAppearance.dark);
    });

    testWidgets('the language is a picker row with its value', (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(page(controller));
      await tester.pumpAndSettle();

      expect(find.text('Language'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('appearance-language')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('appearance-language-ar')));
      await tester.pumpAndSettle();
      expect(controller.appLocale.value, const Locale('ar'));
      // The row shows the value now in force.
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('appearance-language')),
          matching: find.textContaining('العربية'),
        ),
        findsOneWidget,
      );
    });
  });
}
