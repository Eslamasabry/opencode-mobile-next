// Settings › Appearance › Motion (design standard §10): the one choice
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
  await tester.scrollUntilVisible(
    finder,
    120,
    scrollable: find.byType(Scrollable).first,
  );
  await tester.pumpAndSettle();
}

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

      await _show(tester, find.text('Motion'));
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
      await _show(tester, find.text('Motion'));
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

    test('switch and options persist device-wide and restart', () async {
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
          glowColours: KitGlowColours.two,
          glowSpeed: KitGlowSpeed.fast,
        ),
      );
      expect(store.prefs.getString('oc.effectsGlowStyle'), 'softRing');
      expect(store.prefs.getString('oc.effectsGlowColours'), 'two');
      expect(store.prefs.getString('oc.effectsGlowSpeed'), 'fast');
      final next = ProfileStore(prefs: store.prefs).effects;
      expect(next.activityGlow, isTrue);
      expect(next.glowStyle, KitGlowStyle.softRing);
      expect(next.glowColours, KitGlowColours.two);
      expect(next.glowSpeed, KitGlowSpeed.fast);
    });

    testWidgets('the options show only while the switch is on', (tester) async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      final row = find.byKey(const ValueKey('effects-glow'));
      await _show(tester, row);
      expect(find.text('Glowing border while replying'), findsOneWidget);
      final toggle = find.byKey(const ValueKey('effects-glow-switch'));
      expect(tester.widget<Switch>(toggle).value, isTrue);
      for (final id in ['style', 'colours', 'speed']) {
        expect(find.byKey(ValueKey('effects-glow-$id')), findsOneWidget);
      }
      await tester.tap(toggle);
      await tester.pumpAndSettle();
      expect(controller.effects.value.activityGlow, isFalse);
      expect(store.prefs.getBool('oc.effectsActivityGlow'), isFalse);
      for (final id in ['style', 'colours', 'speed']) {
        expect(find.byKey(ValueKey('effects-glow-$id')), findsNothing);
      }
    });

    testWidgets('each option saves when chosen', (tester) async {
      final (controller, store) = await _controller();
      addTearDown(controller.dispose);
      await tester.pumpWidget(_page(controller));
      await tester.pumpAndSettle();
      for (final (key, pref, value) in [
        ('effects-glow-style-softRing', 'oc.effectsGlowStyle', 'softRing'),
        ('effects-glow-colours-two', 'oc.effectsGlowColours', 'two'),
        ('effects-glow-speed-fast', 'oc.effectsGlowSpeed', 'fast'),
        ('effects-glow-speed-slow', 'oc.effectsGlowSpeed', 'slow'),
      ]) {
        final segment = find.byKey(ValueKey(key));
        await _show(tester, segment);
        await tester.tap(segment);
        await tester.pumpAndSettle();
        expect(store.prefs.getString(pref), value);
      }
      expect(controller.effects.value.glowStyle, KitGlowStyle.softRing);
      expect(controller.effects.value.glowColours, KitGlowColours.two);
      expect(controller.effects.value.glowSpeed, KitGlowSpeed.slow);
    });
  });
}
