// Golden renders of shared-settings-1's pages (wave 2a), rebuilt from kit
// parts: the Language sheet (seven choices, each in its own name, with
// "Partly translated (N %)" under the five FG5 languages), the theme
// preview sheet (another theme with Apply, the theme in use, Material You
// unavailable). Phone 412x915 and one wide
// window (1280x800), dark and light (owner decision 2026-09-27: no Arabic),
// with the app's real fonts at DPR 1 and reduced motion (a still frame).
//
// The light-or-dark sheet (appearance-picker-sheet) has no goldens: its map
// proposal is remove (slice-P3.1), so MAP-1 adds none.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/shared_settings_1_golden_test.dart
// and look at every changed image before committing it.
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';
import 'package:opencode_mobile/ui/widgets/appearance_picker.dart';
import 'package:opencode_mobile/ui/widgets/language_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../support/fg5_locale_screens.dart' show loadFg5CjkFonts;

const _phone = Size(412, 915);
const _arabicFallback = 'Noto Sans Arabic';
// The Japanese and Chinese names in the sheet fall back, as on Android, to a
// Noto Sans CJK face; the test engine has none, so these fixture subsets
// stand in (test/fixtures/fonts, SIL OFL).
const _cjkFallback = ['Fg5NotoSansCjkJp', 'Fg5NotoSansCjkSc'];
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  shot,
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 20; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

/// Mounts [home] at [size] in the capture theme, runs [open] (which opens a
/// sheet, or nothing) and compares the whole window with the golden [shot].
Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget Function(BuildContext context) home,
  Size size = _phone,
  Future<void> Function()? open,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  try {
    tester.platformDispatcher.platformBrightnessTestValue = light
        ? Brightness.light
        : Brightness.dark;
    addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
    await tester.runAsync(() async {
      await loadCaptureFonts();
      // A phone draws "العربية" through its system font fallback; the test
      // engine has none, so the shot gives the prose the same Noto Sans
      // Arabic fallback (TEST-8).
      final arabic = FontLoader(_arabicFallback);
      for (final weight in ['Regular', 'Bold']) {
        final bytes = File(
          'test/fixtures/fonts/NotoSansArabic-$weight.ttf',
        ).readAsBytesSync();
        arabic.addFont(Future.value(ByteData.sublistView(bytes)));
      }
      await arabic.load();
      await loadFg5CjkFonts();
    });
    final theme = captureTheme(light: light);
    final boundary = GlobalKey();
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme.copyWith(
            textTheme: theme.textTheme.apply(
              fontFamilyFallback: const [_arabicFallback, ..._cjkFallback],
            ),
          ),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(disableAnimations: true),
            child: child!,
          ),
          home: Scaffold(body: Builder(builder: home)),
        ),
      ),
    );
    await _settle(tester);
    if (open != null) await open();
    await _settle(tester);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    debugDefaultTargetPlatformOverride = null;
  }
}

Future<ConnectionController> _controller({ThemePackId? pack}) async {
  SharedPreferences.setMockInitialValues({
    'oc.appearance': 'system',
    if (pack != null) 'oc.themePack': pack.name,
  });
  return ConnectionController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
    isIsolated: true,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    harvestedDynamicPack.value = null;
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in ['oc/background', 'oc/shortcut']) {
      messenger.setMockMethodCallHandler(
        MethodChannel(channel),
        (_) async => null,
      );
      addTearDown(
        () => messenger.setMockMethodCallHandler(MethodChannel(channel), null),
      );
    }
  });

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final size in [_phone, _wide]) {
      testWidgets('language sheet $mode ${size.width.toInt()}', (tester) async {
        final controller = await _controller();
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'settings_language_sheet_loaded',
          light: light,
          size: size,
          home: (_) => ListView(
            children: [LanguageSettingsTile(controller: controller)],
          ),
          open: () => tester.tap(find.text('Language')),
        );
      });

      testWidgets('theme preview, another theme $mode ${size.width.toInt()}', (
        tester,
      ) async {
        final controller = await _controller();
        addTearDown(controller.dispose);
        await _shot(
          tester,
          'settings_theme_pack_preview_sheet_other',
          light: light,
          size: size,
          home: (context) => Center(
            child: GestureDetector(
              onTap: () => showThemePackPreview(
                context,
                controller: controller,
                pack: ThemePackId.catppuccin,
              ),
              child: const Text('Open'),
            ),
          ),
          open: () => tester.tap(find.text('Open')),
        );
      });
    }

    testWidgets('theme preview, the theme in use $mode', (tester) async {
      final controller = await _controller(pack: ThemePackId.catppuccin);
      addTearDown(controller.dispose);
      await _shot(
        tester,
        'settings_theme_pack_preview_sheet_in_use',
        light: light,
        home: (context) => Center(
          child: GestureDetector(
            onTap: () => showThemePackPreview(
              context,
              controller: controller,
              pack: ThemePackId.catppuccin,
            ),
            child: const Text('Open'),
          ),
        ),
        open: () => tester.tap(find.text('Open')),
      );
    });

    testWidgets('theme preview, Material You unavailable $mode', (
      tester,
    ) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      await _shot(
        tester,
        'settings_theme_pack_preview_sheet_unavailable',
        light: light,
        home: (context) => Center(
          child: GestureDetector(
            onTap: () => showThemePackPreview(
              context,
              controller: controller,
              pack: ThemePackId.dynamic,
            ),
            child: const Text('Open'),
          ),
        ),
        open: () => tester.tap(find.text('Open')),
      );
    });
  }
}
