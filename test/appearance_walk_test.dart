// Settings › Appearance walked through every choice the owner made before
// the page fell over on the phone (2026-10-23, APK 2900: a dark style on a
// dark theme, swiping the looks, the Glow options, then Theme › Auto and
// the theme cards): every live look, every glow option, every light-or-dark
// mode, every Accent and every theme, under Solid and Soft, at the owner's
// narrow width, with the looks painting.
// Nothing may throw, and no part may fall back to the error state. Walked
// on the guided page (2026-10-23): Background › Look with every look shown,
// Text & motion › Reply glow, then Colours › Theme with every theme.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/appearance_guide.dart';

/// Profiles in memory: `load` would otherwise wait on secure storage.
class _Mem extends ProfileStore {
  _Mem({required super.prefs});
  @override
  List<ServerProfile> get profiles => const [];
  @override
  String? get activeId => null;
}

void main() {
  for (final style in [KitSurfaceStyle.solid, KitSurfaceStyle.soft]) {
    for (final light in [false]) {
      testWidgets('${style.name} ${light ? 'light' : 'dark'}', (tester) async {
        KitMotion.loops = true;
        KitLiveBackground.paintWithoutLoops = true;
        addTearDown(() {
          KitMotion.loops = false;
          KitLiveBackground.paintWithoutLoops = false;
        });
        tester.view.physicalSize = const Size(326, 700);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.reset);
        SharedPreferences.setMockInitialValues({});
        final c = ConnectionController(
          _Mem(prefs: await SharedPreferences.getInstance()),
        );
        addTearDown(c.dispose);
        c.themePack.value = ThemePackId.midnight;
        c.appearance.value = light ? AppAppearance.light : AppAppearance.dark;
        c.effects.value = KitSurfaceLook.choose(KitEffects.defaults, style);
        await tester.pumpWidget(
          ListenableBuilder(
            listenable: Listenable.merge([
              c.themePack,
              c.effects,
              c.appearance,
              c.appearanceAccent,
            ]),
            builder: (context, _) {
              final pack = effectiveThemePack(c.themePack.value);
              final accent = appearanceAccentById(c.appearanceAccent.value);
              final saver = c.appearance.value == AppAppearance.saver;
              return MaterialApp(
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                themeMode: switch (c.appearance.value) {
                  AppAppearance.system => ThemeMode.system,
                  AppAppearance.light => ThemeMode.light,
                  _ => ThemeMode.dark,
                },
                theme: AppTheme.light(pack, accent?.light),
                darkTheme: saver
                    ? AppTheme.saver(pack, accent?.dark)
                    : AppTheme.dark(pack, accent?.dark),
                builder: (context, child) => KitEffectsScope(
                  effects: c.effects.value,
                  child: kitStyleTheme(child: child!),
                ),
                home: AppearanceSettingsScreen(controller: c),
              );
            },
          ),
        );
        final errors = <String>[];
        Future<void> frames(String what) async {
          await tester.runAsync(
            () => Future<void>.delayed(const Duration(milliseconds: 20)),
          );
          for (var i = 0; i < 2; i++) {
            await tester.pump(const Duration(milliseconds: 50));
            final e = tester.takeException();
            if (e != null) errors.add('$what: $e');
          }
        }

        Future<void> tap(String key) async {
          final finder = find.byKey(ValueKey(key));
          if (finder.evaluate().isEmpty) {
            // From the top of the page down.
            await tester.drag(appearanceScroll(), const Offset(0, 20000));
            await frames('top for $key');
          }
          if (finder.evaluate().isEmpty) {
            await tester.scrollUntilVisible(
              finder,
              150,
              scrollable: appearanceScroll(),
              maxScrolls: 30,
            );
          }
          await tester.ensureVisible(finder);
          await frames('scroll to $key');
          await tester.tap(finder);
          await frames(key);
        }

        // Scrolls the open question by [dy].
        Future<void> scroll(double dy, String what) async {
          await tester.drag(appearanceScroll(), Offset(0, dy));
          await frames(what);
        }

        await frames('open');
        await tap('appearance-group-background');
        await tap('appearance-see-all-looks');
        for (final look in kitLiveStyles) {
          c.effects.value = c.effects.value.copyWith(
            liveStyle: look.id,
            liveBackground: true,
          );
          await frames('look ${look.id}');
          await scroll(-120, 'scroll after ${look.id}');
        }
        await tap('appearance-guide-back');
        await tap('appearance-group-comfort');
        await tap('appearance-question-glow');
        for (final g in KitGlowStyle.values) {
          for (final s in KitGlowSpeed.values) {
            for (final col in KitGlowColours.values) {
              c.effects.value = c.effects.value.copyWith(
                glowStyle: g,
                glowSpeed: s,
                glowColours: col,
              );
              await frames('glow $g $s $col');
            }
          }
        }
        await tap('appearance-guide-back');
        await tap('appearance-group-colours');
        await tap('appearance-question-theme');
        await tap('appearance-see-all-themes');
        // The phone itself in dark, so Auto is dark too.
        tester.platformDispatcher.platformBrightnessTestValue = Brightness.dark;
        addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
        for (final mode in AppAppearance.values) {
          c.appearance.value = mode;
          await frames('mode $mode');
        }
        c.appearance.value = AppAppearance.system;
        for (final accent in appearanceAccents) {
          c.appearanceAccent.value = accent.id;
          await frames('accent ${accent.id}');
        }
        c.appearanceAccent.value = null;
        for (final id in ThemePackId.values) {
          c.themePack.value = id;
          await frames('theme ${id.name}');
          await scroll(-60, 'scroll after ${id.name}');
        }
        expect(errors, isEmpty, reason: errors.take(5).join('\n'));
        expect(find.byType(KitErrorFallback), findsNothing);
        await tester.pumpWidget(const SizedBox());
      });
    }
  }
}
