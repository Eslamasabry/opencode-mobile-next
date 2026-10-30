// Settings › Appearance, final design (2026-10-21, guided since 2026-10-23):
// Saver as a mode that overrides the effects in force while the stored
// choices stay as they are, the Accent (a second colour, stored app-wide)
// and the Style choice. There is no preview: the page itself wears every
// choice as it is made.
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
class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs});

  @override
  List<ServerProfile> get profiles => const [];

  @override
  String? get activeId => null;
}

Future<(ConnectionController, _MemoryProfileStore)> _controller([
  Map<String, Object> saved = const {},
]) async {
  SharedPreferences.setMockInitialValues(saved);
  final store = _MemoryProfileStore(
    prefs: await SharedPreferences.getInstance(),
  );
  return (ConnectionController(store), store);
}

final _en = lookupAppLocalizations(const Locale('en'));

Widget _page(ConnectionController controller) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  theme: AppTheme.dark(),
  home: AppearanceSettingsScreen(controller: controller),
);

Future<void> _phone(WidgetTester tester) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

/// The effects in force on the page itself: the person's choices.
KitEffects _inForce(WidgetTester tester) => KitEffects.of(
  tester.element(find.byKey(const ValueKey('appearance-settings'))),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Saver', () {
    test(
      'is stored as the light-or-dark choice and survives a restart',
      () async {
        final (controller, store) = await _controller();
        await controller.setAppearance(AppAppearance.saver);
        expect(store.prefs.getString('oc.appearance'), 'saver');
        controller.dispose();
        final restarted = ConnectionController(
          ProfileStore(prefs: store.prefs),
        );
        addTearDown(restarted.dispose);
        expect(restarted.appearance.value, AppAppearance.saver);
      },
    );

    test('turns motion, the live background and the style off, never the '
        'stored choices', () {
      const stored = KitEffects(
        motion: KitMotionLevel.full,
        liveBackground: true,
        liveStyle: 'circuit',
        surfaceStyle: KitSurfaceStyle.soft,
      );
      final saver = stored.underSaver;
      expect(saver.motion, KitMotionLevel.off);
      expect(saver.liveBackground, isFalse);
      expect(saver.surfaceStyle, KitSurfaceStyle.solid);
      expect(saver.celebrations, isFalse);
      expect(saver.saver, isTrue);
      // What Saver leaves alone stays the person's.
      expect(saver.liveStyle, 'circuit');
      expect(stored.surfaceStyle, KitSurfaceStyle.soft);
    });

    test('is a true black ground with every text role still reading', () {
      for (final id in ThemePackId.values.where(
        (id) => id != ThemePackId.dynamic,
      )) {
        final roles = AppTheme.rolesOf(AppTheme.saver(themePack(id)));
        expect(roles.ground, const Color(0xFF000000), reason: id.name);
        expect(roles.isDark, isTrue);
        for (final surface in [
          roles.ground,
          roles.surface1,
          roles.surface2,
          roles.surface3,
        ]) {
          expect(
            contrastRatio(roles.text3, surface),
            greaterThanOrEqualTo(4.5),
            reason: '${id.name} text3',
          );
        }
      }
    });

    testWidgets('chosen on the page: one quiet line, the page goes still, '
        'Style says why it rests and turns Saver off', (tester) async {
      await _phone(tester);
      final (controller, store) = await _controller({
        'oc.effectsSurfaceStyle': 'soft',
        'oc.effectsBackgroundOn': true,
      });
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      expect(find.text(_en.appearanceSaverNote), findsNothing);
      expect(_inForce(tester).liveBackground, isTrue);

      await openAppearance(tester, 'colours');
      await _tap(tester, find.byKey(const ValueKey('appearance-mode-saver')));
      expect(store.prefs.getString('oc.appearance'), 'saver');
      expect(find.text(_en.appearanceSaverNote), findsOneWidget);
      final inForce = _inForce(tester);
      expect(inForce.motion, KitMotionLevel.off);
      expect(inForce.liveBackground, isFalse);
      expect(inForce.surfaceStyle, KitSurfaceStyle.solid);
      // The stored choices are untouched.
      expect(store.prefs.getString('oc.effectsSurfaceStyle'), 'soft');
      expect(controller.effects.value.liveBackground, isTrue);

      // Style: held, with the reason and the way out; no style cards.
      await _tap(tester, find.byKey(const ValueKey('appearance-guide-back')));
      await openAppearance(tester, 'style');
      expect(
        find.byKey(const ValueKey('appearance-surface-glass')),
        findsNothing,
      );
      expect(find.text(_en.appearanceSaverHoldsStyle), findsOneWidget);
      expect(store.prefs.getString('oc.effectsSurfaceStyle'), 'soft');

      await _tap(
        tester,
        find.byKey(const ValueKey('appearance-turn-off-saver')),
      );
      expect(controller.appearance.value, AppAppearance.dark);
      expect(find.text(_en.appearanceSaverHoldsStyle), findsNothing);
      expect(_inForce(tester).surfaceStyle, KitSurfaceStyle.soft);
      expect(
        find.byKey(const ValueKey('appearance-surface-soft')),
        findsOneWidget,
      );
    });
  });

  group('Accent', () {
    test('is stored app-wide, survives a restart, and Match theme clears '
        'it', () async {
      final (controller, store) = await _controller();
      expect(controller.appearanceAccent.value, isNull);
      await controller.setAppearanceAccent('pink');
      expect(store.prefs.getString('oc.appearanceAccent'), 'pink');
      expect(
        store
            .profileScopedPreferenceKeys('3f2a9c1e-7d4b-4e21-9a0f-5c6d7e8f9a0b')
            .contains('oc.appearanceAccent'),
        isFalse,
      );
      controller.dispose();
      final restarted = ConnectionController(ProfileStore(prefs: store.prefs));
      addTearDown(restarted.dispose);
      expect(restarted.appearanceAccent.value, 'pink');
      await restarted.setAppearanceAccent(null);
      expect(store.prefs.containsKey('oc.appearanceAccent'), isFalse);
    });

    test('every theme has a partner that reads, carries its words and keeps '
        'away from amber', () {
      for (final id in ThemePackId.values.where(
        (id) => id != ThemePackId.dynamic,
      )) {
        for (final brightness in Brightness.values) {
          final roles = themePack(id).palette(brightness).themeRoles;
          final where = '${id.name} ${brightness.name}';
          expect(
            contrastRatio(roles.partner, roles.ground),
            greaterThanOrEqualTo(4.5),
            reason: where,
          );
          expect(
            contrastRatio(roles.onPartner, roles.partner),
            greaterThanOrEqualTo(4.5),
            reason: where,
          );
          expect(
            accentKeepsMeaning(roles.partner, roles),
            isTrue,
            reason: where,
          );
        }
      }
    });

    test('every swatch, in every theme, is guarded to AA and keeps away from '
        'amber', () {
      for (final id in ThemePackId.values.where(
        (id) => id != ThemePackId.dynamic,
      )) {
        for (final brightness in Brightness.values) {
          final base = themePack(id).palette(brightness).themeRoles;
          for (final accent in appearanceAccents) {
            final roles = base.withPartner(accent.of(brightness));
            final where = '${id.name} ${brightness.name} ${accent.id}';
            expect(
              contrastRatio(roles.partner, roles.ground),
              greaterThanOrEqualTo(4.5),
              reason: where,
            );
            expect(
              contrastRatio(roles.partner, roles.surface1),
              greaterThanOrEqualTo(4.5),
              reason: where,
            );
            expect(
              contrastRatio(roles.onPartner, roles.partner),
              greaterThanOrEqualTo(4.5),
              reason: where,
            );
            expect(
              accentKeepsMeaning(accent.of(brightness), base),
              isTrue,
              reason: where,
            );
          }
        }
      }
    });

    test(
      'the theme is built with the chosen swatch; the main colour stays',
      () {
        final pink = appearanceAccentById('pink')!;
        final plain = AppTheme.rolesOf(AppTheme.dark());
        final picked = AppTheme.rolesOf(AppTheme.dark(null, pink.dark));
        expect(picked.partner, isNot(plain.partner));
        expect(picked.accent, plain.accent);
        expect(appearanceAccentById('nope'), isNull);
      },
    );

    testWidgets('a swatch is chosen on the page and Match theme puts it back', (
      tester,
    ) async {
      await _phone(tester);
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      final semantics = tester.ensureSemantics();
      await openAppearance(tester, 'colours', 'accentColour');
      await _tap(tester, find.byKey(const ValueKey('appearance-accent-teal')));
      expect(store.prefs.getString('oc.appearanceAccent'), 'teal');
      expect(
        tester.getSemantics(
          find.byKey(const ValueKey('appearance-accent-teal')),
        ),
        isSemantics(isSelected: true, label: _en.appearanceAccentTeal),
      );
      await _tap(tester, find.byKey(const ValueKey('appearance-accent-match')));
      expect(store.prefs.containsKey('oc.appearanceAccent'), isFalse);
      semantics.dispose();
    });
  });

  group('Surfaces', () {
    testWidgets('a card saves the surface style', (tester) async {
      await _phone(tester);
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await openAppearance(tester, 'style');
      await _tap(tester, find.byKey(const ValueKey('appearance-surface-soft')));
      expect(store.prefs.getString('oc.effectsSurfaceStyle'), 'soft');
      expect(controller.effects.value.surfaceStyle, KitSurfaceStyle.soft);
      expect(_inForce(tester).surfaceStyle, KitSurfaceStyle.soft);
    });

    // Brutal and Paper were built but not offered: the picker held three.
    // Signal joined them on 2026-10-30 (concept CD).
    testWidgets('Solid, Soft, Brutal, Paper and Signal are offered (no '
        'Glass, no Crystal), and Paper turns the live look off as it is '
        'chosen', (tester) async {
      await _phone(tester);
      final (controller, store) = await _controller({
        'oc.effectsBackgroundOn': true,
      });
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await openAppearance(tester, 'style');
      expect(KitSurfaceStyle.values.map((s) => s.name), [
        'solid',
        'soft',
        'brutal',
        'paper',
        'signal',
      ]);
      for (final style in KitSurfaceStyle.values) {
        final card = find.byKey(ValueKey('appearance-surface-${style.name}'));
        await bringIntoView(tester, card);
        expect(card, findsOneWidget, reason: style.name);
      }
      for (final gone in ['glass', 'crystal']) {
        expect(find.byKey(ValueKey('appearance-surface-$gone')), findsNothing);
      }
      await _tap(
        tester,
        find.byKey(const ValueKey('appearance-surface-brutal')),
      );
      expect(store.prefs.getString('oc.effectsSurfaceStyle'), 'brutal');
      await _tap(
        tester,
        find.byKey(const ValueKey('appearance-surface-paper')),
      );
      expect(store.prefs.getString('oc.effectsSurfaceStyle'), 'paper');
      expect(_inForce(tester).surfaceStyle, KitSurfaceStyle.paper);
      expect(controller.effects.value.liveBackground, isFalse);
    });
  });

  group('header', () {
    testWidgets('no preview and no "Live" mark: the chips sit right under '
        'the bar', (tester) async {
      await _phone(tester);
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('appearance-preview')), findsNothing);
      expect(find.byKey(const ValueKey('appearance-live')), findsNothing);
      final title = find.text(_en.e7AppearanceTitle);
      final tabs = find.byKey(const ValueKey('appearance-group-colours'));
      expect(
        tester.getTopLeft(tabs).dy - tester.getBottomLeft(title).dy,
        lessThan(48),
      );
    });

    testWidgets('the four modes have their words', (tester) async {
      await _phone(tester);
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await openAppearance(tester, 'colours');
      final modes = find.byKey(const ValueKey('appearance-settings-entry'));
      for (final word in [
        _en.appearanceModeAuto,
        _en.e7AppearanceLight,
        _en.e7AppearanceDark,
        _en.appearanceModeSaver,
      ]) {
        expect(
          find.descendant(of: modes, matching: find.text(word)),
          findsOneWidget,
          reason: word,
        );
      }
    });
  });

  group('the page wears the choices', () {
    // As main.dart hosts the app: the stored effects above the navigator
    // and the Style's own roles over the theme.
    Widget app(ConnectionController controller, {required bool light}) =>
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          theme: light ? AppTheme.light() : AppTheme.dark(),
          builder: (context, child) => KitEffectsScope(
            effects: controller.effects.value,
            child: kitStyleTheme(child: child!),
          ),
          home: AppearanceSettingsScreen(controller: controller),
        );

    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';
      for (final style in KitSurfaceStyle.values) {
        testWidgets('${style.name} · $mode: the controls take the chosen '
            'style on the plain page colour', (tester) async {
          await _phone(tester);
          final (controller, _) = await _controller();
          addTearDown(controller.dispose);
          controller.effects.value = KitSurfaceLook.choose(
            KitEffects.defaults,
            style,
          );
          controller.appearance.value = light
              ? AppAppearance.light
              : AppAppearance.dark;
          await tester.pumpWidget(app(controller, light: light));
          await tester.pumpAndSettle();
          final tabs = tester.element(
            find.byKey(const ValueKey('appearance-group-colours')),
          );
          final shown = KitEffects.of(tabs);
          expect(shown.surfaceStyle, style);
          // The background is the page's plain colour by default.
          expect(shown.liveBackground, isFalse);
          final expected = ThemeRoles.resolve(
            kitStyleRestyled(light ? AppTheme.light() : AppTheme.dark(), style),
          );
          expect(ThemeRoles.of(tabs).ground, expected.ground);
          // Nothing is blurred anywhere.
          expect(find.byType(BackdropFilter), findsNothing);
        });
      }
    }
  });
}
