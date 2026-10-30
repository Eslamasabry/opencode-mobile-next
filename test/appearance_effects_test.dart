// Settings › Appearance › Text & motion (design standard §10): the one choice
// persists app-wide (older installs' separate animations and celebrations
// keys fold into it), the app provides it above its navigator, and it changes
// what the app does — Calm never loops and never celebrates, Off shows
// finished frames. Glass and vibration are no longer choices.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'support/appearance_guide.dart';

/// Profiles in memory: `load` would otherwise wait on secure storage.
class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs});

  bool refuseEffects = false;

  @override
  List<ServerProfile> get profiles => const [];

  @override
  String? get activeId => null;

  @override
  Future<void> setEffects(KitEffects effects) async {
    if (refuseEffects) throw StateError('disk full');
    await super.setEffects(effects);
  }
}

class _NoUpdateService implements AppUpdateService {
  @override
  bool get isAvailable => false;
  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;
  @override
  Future<void> downloadUpdate() async {}
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

Widget _page(ConnectionController controller, {bool reduce = false}) =>
    MaterialApp(
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      theme: AppTheme.dark(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reduce),
        child: child!,
      ),
      home: AppearanceSettingsScreen(controller: controller),
    );

Future<void> _show(WidgetTester tester, Finder finder) async {
  await Scrollable.ensureVisible(tester.element(finder), alignment: .5);
  await tester.pumpAndSettle();
}

/// Opens one of the page's questions (text, motion, glow, look, strength).
Future<void> _openTab(WidgetTester tester, String name) => switch (name) {
  'look' => openAppearance(tester, 'background'),
  'strength' => openAppearance(tester, 'background', 'strength'),
  'text' => openAppearance(tester, 'comfort'),
  _ => openAppearance(tester, 'comfort', name),
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  tearDown(() {
    KitHaptics.enabled = true;
    KitMotion.loops = false;
    harvestedDynamicPack.value = null;
  });

  group('stored', () {
    test('everything is on until the person chooses', () async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      expect(store.effects, KitEffects.defaults);
      expect(controller.effects.value, KitEffects.defaults);
    });

    test('a choice survives a restart and is app-wide', () async {
      final (controller, store) = await _controller();
      const chosen = KitEffects(
        motion: KitMotionLevel.calm,
        celebrations: false,
      );
      await controller.setEffects(chosen);
      controller.dispose();

      // A new store over the same preferences: the next launch.
      final next = ProfileStore(prefs: store.prefs);
      expect(next.effects, chosen);
      final restarted = ConnectionController(next);
      addTearDown(restarted.dispose);
      expect(restarted.effects.value, chosen);

      // Not scoped to a server: deleting one never resets it.
      expect(
        store
            .profileScopedPreferenceKeys('3f2a9c1e-7d4b-4e21-9a0f-5c6d7e8f9a0b')
            .contains('oc.effectsMotion'),
        isFalse,
      );
      expect(store.prefs.getString('oc.effectsMotion'), 'calm');
    });

    test('the background is the plain page colour until a look is chosen; '
        'the look survives a restart', () async {
      final (controller, store) = await _controller();
      expect(controller.effects.value.liveBackground, isFalse);
      expect(controller.effects.value.liveStyle, 'aurora');
      await controller.setEffects(
        controller.effects.value.copyWith(
          liveBackground: true,
          liveStyle: 'topo',
        ),
      );
      controller.dispose();
      final restarted = ConnectionController(ProfileStore(prefs: store.prefs));
      addTearDown(restarted.dispose);
      expect(restarted.effects.value.liveBackground, isTrue);
      expect(restarted.effects.value.liveStyle, 'topo');
      // Per device, never per server.
      expect(
        store
            .profileScopedPreferenceKeys('3f2a9c1e-7d4b-4e21-9a0f-5c6d7e8f9a0b')
            .contains('oc.effectsLiveStyle'),
        isFalse,
      );
    });

    test('older installs map onto the one Motion choice', () async {
      for (final (saved, expected, celebrates) in [
        (<String, Object>{}, KitMotionLevel.full, true),
        (
          <String, Object>{'oc.effectsMotion': 'calm'},
          KitMotionLevel.calm,
          false,
        ),
        (
          <String, Object>{'oc.effectsMotion': 'off'},
          KitMotionLevel.off,
          false,
        ),
        // Animations Full with Celebrations off reads as Calm.
        (
          <String, Object>{
            'oc.effectsMotion': 'full',
            'oc.effectsCelebrations': false,
          },
          KitMotionLevel.calm,
          false,
        ),
        // Glass and Vibration were switched off: both are fixed now.
        (
          <String, Object>{
            'oc.effectsGlass': false,
            'oc.effectsHaptics': false,
          },
          KitMotionLevel.full,
          true,
        ),
        // A value of the wrong type never crashes the read.
        (
          <String, Object>{'oc.effectsCelebrations': 'no'},
          KitMotionLevel.full,
          true,
        ),
      ]) {
        final (controller, store) = await _controller(saved);
        addTearDown(controller.dispose);
        expect(store.effects.motion, expected, reason: '$saved');
        expect(store.effects.celebrations, celebrates, reason: '$saved');
        expect(store.effects.haptics, isTrue, reason: '$saved');
      }
    });

    test('choosing Full again clears the old celebrations switch', () async {
      final (controller, store) = await _controller({
        'oc.effectsMotion': 'full',
        'oc.effectsCelebrations': false,
      });
      addTearDown(controller.dispose);
      expect(store.effects.motion, KitMotionLevel.calm);
      await controller.setEffects(KitEffects.defaults);
      expect(store.effects.motion, KitMotionLevel.full);
      expect(store.effects.celebrations, isTrue);
    });

    test('an unknown stored motion reads as Full', () async {
      final (controller, store) = await _controller({
        'oc.effectsMotion': 'wobbly',
      });
      addTearDown(controller.dispose);
      expect(store.effects.motion, KitMotionLevel.full);
    });
  });

  group('the Motion section', () {
    testWidgets('has one Motion choice and no glass, celebration or vibration '
        'switch', (tester) async {
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();

      await _openTab(tester, 'motion');
      expect(
        find.text(
          'Drawings move, waiting screens breathe and finished moments celebrate',
        ),
        findsOneWidget,
      );
      expect(find.text('Glass effects'), findsNothing);
      expect(find.text('Celebrations'), findsNothing);
      expect(find.text('Vibration'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('Motion: Calm and Off are chosen and saved', (tester) async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();

      await _openTab(tester, 'motion');
      await _show(tester, find.text('Calm'));
      await tester.tap(find.text('Calm'));
      await tester.pumpAndSettle();
      expect(controller.effects.value.motion, KitMotionLevel.calm);
      expect(store.prefs.getString('oc.effectsMotion'), 'calm');
      expect(controller.effects.value.celebrations, isFalse);
      expect(
        find.text(
          'Drawings appear, nothing keeps moving and nothing celebrates',
        ),
        findsOneWidget,
      );

      await tester.tap(find.text('Off'));
      await tester.pumpAndSettle();
      expect(controller.effects.value.motion, KitMotionLevel.off);
      expect(find.text('Everything shows at once'), findsOneWidget);
    });

    testWidgets('the system Remove animations wins and the page says so', (
      tester,
    ) async {
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller, reduce: true));
      await tester.pumpAndSettle();
      await _openTab(tester, 'motion');
      expect(
        find.textContaining('Remove animations is on, so nothing moves'),
        findsOneWidget,
      );
    });

    testWidgets('a refused save puts the choice back and says so', (
      tester,
    ) async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      store.refuseEffects = true;
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await _openTab(tester, 'motion');
      await _show(tester, find.text('Calm'));
      await tester.tap(find.text('Calm'));
      await tester.pumpAndSettle();
      expect(controller.effects.value.motion, KitMotionLevel.full);
      expect(
        find.text('Could not save this choice on this device. Try again.'),
        findsOneWidget,
      );
    });
  });

  group('the app obeys the stored choice', () {
    Widget app(ConnectionController controller) => ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: OcApp(updateService: _NoUpdateService()),
    );

    BuildContext underNavigator(WidgetTester tester) =>
        tester.element(find.byType(Scaffold).first);

    testWidgets('every route reads the choices and follows a change', (
      tester,
    ) async {
      KitMotion.loops = true;
      final (controller, _) = await _controller({'oc.effectsMotion': 'calm'});
      addTearDown(controller.dispose);
      await tester.pumpWidget(app(controller));
      await tester.pump();

      var context = underNavigator(tester);
      expect(KitEffects.of(context).motion, KitMotionLevel.calm);
      // Calm: drawings still draw in, nothing loops.
      expect(KitMotion.loopsIn(context), isFalse);
      expect(KitMotion.reduced(context), isFalse);
      expect(KitHaptics.enabled, isTrue);

      await controller.setEffects(
        const KitEffects(motion: KitMotionLevel.full),
      );
      await tester.pump();
      context = underNavigator(tester);
      expect(KitMotion.loopsIn(context), isTrue);
      expect(KitHaptics.enabled, isTrue);

      await controller.setEffects(const KitEffects(motion: KitMotionLevel.off));
      await tester.pump();
      // Off: every drawing shows its finished frame, pages change at once.
      expect(KitMotion.reduced(underNavigator(tester)), isTrue);
    });
  });
  group('Glowing border while replying', () {
    test('is on by default: classic, one colour, normal speed', () async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      final effects = store.effects;
      expect(effects.activityGlow, isTrue);
      expect(effects.glowStyle, KitGlowStyle.classic);
      expect(effects.glowColours, KitGlowColours.one);
      expect(effects.glowSpeed, KitGlowSpeed.normal);
      expect(KitEffects.defaults.activityGlow, isTrue);
    });

    test(
      'migration: no stored choice reads on, a stored off stays off',
      () async {
        // An install from before the switch was on by default: the key was
        // never written (setEffects only writes a changed value).
        final (controller, store) = await _controller({
          'oc.effectsMotion': 'full',
        });
        addTearDown(controller.dispose);
        expect(store.effects.activityGlow, isTrue);
        // Someone who turned it on, and someone who turned it off, keep that.
        final (c2, s2) = await _controller({'oc.effectsActivityGlow': false});
        addTearDown(c2.dispose);
        expect(s2.effects.activityGlow, isFalse);
        final (c3, s3) = await _controller({'oc.effectsActivityGlow': true});
        addTearDown(c3.dispose);
        expect(s3.effects.activityGlow, isTrue);
        // Unknown stored options fall back to the defaults.
        final (c4, s4) = await _controller({
          'oc.effectsGlowStyle': 'sparkles',
          'oc.effectsGlowColours': 'rainbow',
          'oc.effectsGlowSpeed': 'warp',
        });
        addTearDown(c4.dispose);
        expect(s4.effects.glowStyle, KitGlowStyle.classic);
        expect(s4.effects.glowColours, KitGlowColours.one);
        expect(s4.effects.glowSpeed, KitGlowSpeed.normal);
      },
    );

    test('on, off and the style persist device-wide and restart', () async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await controller.setEffects(
        controller.effects.value.copyWith(activityGlow: false),
      );
      expect(store.prefs.getBool('oc.effectsActivityGlow'), isFalse);
      expect(
        store
            .profileScopedPreferenceKeys('3f2a9c1e-7d4b-4e21-9a0f-5c6d7e8f9a0b')
            .any((k) => k.startsWith('oc.effectsGlow')),
        isFalse,
      );
      expect(ProfileStore(prefs: store.prefs).effects.activityGlow, isFalse);
      await controller.setEffects(
        controller.effects.value.copyWith(
          activityGlow: true,
          glowStyle: KitGlowStyle.softRing,
        ),
      );
      expect(store.prefs.getString('oc.effectsGlowStyle'), 'softRing');
      final next = ProfileStore(prefs: store.prefs).effects;
      expect(next.activityGlow, isTrue);
      expect(next.glowStyle, KitGlowStyle.softRing);
      // One choice: the colours and the speed are the app's own.
      expect(next.glowColours, KitGlowColours.one);
      expect(next.glowSpeed, KitGlowSpeed.normal);
    });

    testWidgets('Reply glow is one choice: Off, Classic or Soft ring', (
      tester,
    ) async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await _openTab(tester, 'glow');
      for (final id in ['off', 'classic', 'softRing']) {
        expect(find.byKey(ValueKey('effects-glow-$id')), findsOneWidget);
      }
      // No switch, colours or speed of their own.
      expect(find.byKey(const ValueKey('effects-glow-switch')), findsNothing);
      expect(find.text('Two colours'), findsNothing);
      expect(find.text('Speed'), findsNothing);

      final off = find.byKey(const ValueKey('effects-glow-off'));
      await _show(tester, off);
      await tester.tap(off);
      await tester.pumpAndSettle();
      expect(controller.effects.value.activityGlow, isFalse);
      expect(store.prefs.getBool('oc.effectsActivityGlow'), isFalse);
    });

    testWidgets('Look: Off first, then the looks; Off keeps the last look', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(412, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await _openTab(tester, 'look');
      // No Live background switch: Off is the first look.
      expect(
        find.byKey(const ValueKey('effects-live-background-switch')),
        findsNothing,
      );
      final cards = find.byType(KitOptionCard);
      expect(tester.widget(cards.first).key, const ValueKey('live-style-off'));
      // Seven picks after Off, and "See all 20 looks".
      expect(cards, findsNWidgets(8));
      final all = find.byKey(const ValueKey('appearance-see-all-looks'));
      await _show(tester, all);
      await tester.tap(all);
      await tester.pumpAndSettle();
      expect(cards, findsNWidgets(kitLiveStyles.length + 1));

      // A look card chooses it at once and saves it for this device.
      final topo = find.byKey(const ValueKey('live-style-topo'));
      await _show(tester, topo);
      await tester.tap(topo);
      await tester.pumpAndSettle();
      expect(controller.effects.value.liveStyle, 'topo');
      expect(store.prefs.getString('oc.effectsLiveStyle'), 'topo');

      // Off: stored as the background off, the look kept for later.
      final off = find.byKey(const ValueKey('live-style-off'));
      await _show(tester, off);
      await tester.tap(off);
      await tester.pumpAndSettle();
      expect(controller.effects.value.liveBackground, isFalse);
      expect(store.prefs.getBool('oc.effectsBackgroundOn'), isFalse);
      expect(controller.effects.value.liveStyle, 'topo');
    });

    testWidgets('Strength saves the intensity', (tester) async {
      final (controller, store) = await _controller({
        'oc.effectsBackgroundOn': true,
      });
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await _openTab(tester, 'strength');
      final bold = find.byKey(const ValueKey('effects-live-intensity-bold'));
      await _show(tester, bold);
      await tester.tap(bold);
      await tester.pumpAndSettle();
      expect(controller.effects.value.liveIntensity, KitLiveIntensity.bold);
      expect(store.prefs.getString('oc.effectsLiveIntensity'), 'bold');
    });

    testWidgets('no preview: the page itself follows the choices', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('appearance-preview')), findsNothing);
      KitEffects shown() => KitEffects.of(
        tester.element(find.byKey(const ValueKey('appearance-settings'))),
      );
      await _openTab(tester, 'look');
      final topo = find.byKey(const ValueKey('live-style-topo'));
      await _show(tester, topo);
      await tester.tap(topo);
      await tester.pumpAndSettle();
      expect(shown().liveBackground, isTrue);
      expect(shown().liveStyle, 'topo');
      await tester.tap(
        find.byKey(const ValueKey('appearance-question-strength')),
      );
      await tester.pumpAndSettle();
      final bold = find.byKey(const ValueKey('effects-live-intensity-bold'));
      await _show(tester, bold);
      await tester.tap(bold);
      await tester.pumpAndSettle();
      expect(shown().liveIntensity, KitLiveIntensity.bold);
      await tester.tap(find.byKey(const ValueKey('appearance-guide-back')));
      await tester.pumpAndSettle();
      await _openTab(tester, 'glow');
      final off = find.byKey(const ValueKey('effects-glow-off'));
      await _show(tester, off);
      await tester.tap(off);
      await tester.pumpAndSettle();
      expect(shown().activityGlow, isFalse);
    });

    testWidgets('large text scrolls the whole page', (tester) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final (controller, _) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          theme: AppTheme.dark(),
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(2)),
            child: child!,
          ),
          home: AppearanceSettingsScreen(controller: controller),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('appearance-group-colours')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('each option saves when chosen', (tester) async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      await _openTab(tester, 'glow');
      final soft = find.byKey(const ValueKey('effects-glow-softRing'));
      await _show(tester, soft);
      await tester.tap(soft);
      await tester.pumpAndSettle();
      expect(store.prefs.getString('oc.effectsGlowStyle'), 'softRing');
      expect(controller.effects.value.activityGlow, isTrue);
      expect(controller.effects.value.glowStyle, KitGlowStyle.softRing);
      expect(controller.effects.value.glowColours, KitGlowColours.one);
      expect(controller.effects.value.glowSpeed, KitGlowSpeed.normal);
    });
  });
}
