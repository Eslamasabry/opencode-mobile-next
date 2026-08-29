import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
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

  test('the default OpenCode pack is byte-identical to the pre-pack theme', () {
    final dark = AppTheme.dark();
    expect(dark.colorScheme.primary, const Color(0xFF83CDAA));
    expect(dark.colorScheme.onPrimary, const Color(0xFF052117));
    expect(dark.colorScheme.surface, const Color(0xFF151A17));
    expect(dark.colorScheme.onSurface, const Color(0xFFE3E8E4));
    expect(dark.colorScheme.surfaceContainerLow, const Color(0xFF171C19));
    expect(dark.colorScheme.error, const Color(0xFFFFB4AB));
    expect(dark.scaffoldBackgroundColor, const Color(0xFF101310));
    expect(AppTheme.successOf(dark), const Color(0xFF86D8A5));

    final light = AppTheme.light();
    expect(light.colorScheme.primary, const Color(0xFF176B4B));
    expect(light.colorScheme.surface, const Color(0xFFFFFFFF));
    expect(light.colorScheme.surfaceContainerLow, const Color(0xFFF0F5F1));
    expect(light.scaffoldBackgroundColor, const Color(0xFFF6F9F6));
    expect(AppTheme.successOf(light), const Color(0xFF1E7A44));
  });

  test('every static pack has complete, distinct dark and light palettes', () {
    for (final id in ThemePackId.values.where(
      (id) => id != ThemePackId.dynamic,
    )) {
      final pack = themePack(id);
      expect(pack.dark.scheme.brightness, Brightness.dark, reason: '$id');
      expect(pack.light.scheme.brightness, Brightness.light, reason: '$id');
      expect(
        pack.dark.background,
        isNot(pack.light.background),
        reason: '$id',
      );
      // Pack-owned success reaches the ThemeData extension.
      expect(
        AppTheme.successOf(AppTheme.dark(pack)),
        pack.dark.success,
        reason: '$id',
      );
    }
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
            themeMode: ThemeMode.dark,
            theme: AppTheme.light(pack),
            darkTheme: AppTheme.dark(pack),
            home: const Scaffold(body: Text('themed')),
          );
        },
      ),
    );
    BuildContext context = tester.element(find.text('themed'));
    expect(Theme.of(context).colorScheme.primary, const Color(0xFF83CDAA));

    await controller.setThemePack(ThemePackId.gruvbox);
    await tester.pumpAndSettle();
    context = tester.element(find.text('themed'));
    expect(Theme.of(context).colorScheme.primary, const Color(0xFFFE8019));
    expect(
      AppTheme.successOf(Theme.of(context)),
      const Color(0xFFB8BB26),
    );
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
        theme: AppTheme.light(),
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2)),
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
    expect(find.text('Needs Android 12 or newer'), findsOneWidget);
    await tester.tap(dynamicTile, warnIfMissed: false);
    await tester.pump();
    expect(controller.themePack.value, ThemePackId.opencode);

    final solarized = find.byKey(const ValueKey('theme-pack-solarized'));
    await tester.dragUntilVisible(
      solarized,
      find.byType(ListView),
      const Offset(0, -120),
    );
    await tester.tap(solarized);
    await tester.pumpAndSettle();
    expect(controller.themePack.value, ThemePackId.solarized);
    expect(tester.takeException(), isNull);
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
        theme: AppTheme.light(),
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
    expect(find.text('Needs Android 12 or newer'), findsNothing);
    await tester.tap(dynamicTile);
    await tester.pumpAndSettle();
    expect(controller.themePack.value, ThemePackId.dynamic);
  });
}
