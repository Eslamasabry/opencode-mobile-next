// Behaviour of KitFrost, the chat's frosted glass (owner, 2026-10-30): the
// three strengths, every condition that keeps it solid, the fade, the
// composer's inner well, the stored Glass choice, and KitComposer.layer
// blurring only while the transcript is scrolled under the composer.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings/glass_choice_control.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit_motion_still.dart';

final _blurs = find.byType(BackdropFilter);

Widget _app(
  Widget home, {
  KitEffects effects = KitEffects.defaults,
  bool disableAnimations = false,
  bool highContrast = false,
  bool dark = false,
}) => KitEffectsScope(
  effects: effects,
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: dark ? AppTheme.dark() : AppTheme.light(),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        disableAnimations: disableAnimations,
        highContrast: highContrast,
      ),
      child: child!,
    ),
    home: Scaffold(body: home),
  ),
);

Widget _pane({required bool shown, KitFrostStrength? strength}) => Builder(
  builder: (context) => KitFrost(
    role: KitFrostRole.composer,
    strength: strength,
    shown: shown,
    tint: KitTokens.of(context).roles.surface2,
    borderRadius: BorderRadius.circular(26),
    child: const KitGlass(
      child: SizedBox(width: 300, height: 56, child: Text('Steer')),
    ),
  ),
);

/// A long reversed transcript under a composer layer.
Widget _layer({Widget? above, bool wide = false}) => KitComposer.layer(
  body: ListView.builder(
    key: const ValueKey('transcript'),
    reverse: true,
    itemCount: 80,
    itemBuilder: (_, i) => SizedBox(height: 40, child: Text('Line $i')),
  ),
  above: above,
  // [wide]: the field fills the composer's width, as the app's does.
  composer: KitGlass(
    child: SizedBox(
      width: wide ? double.infinity : null,
      height: 56,
      child: const Text('Steer'),
    ),
  ),
);

void main() {
  setUp(GlassChoiceStore.debugReset);

  group('strengths', () {
    test('each strength follows the study: blur, saturation, tint', () {
      expect(KitFrostStrength.weak.sigma, 6);
      expect(KitFrostStrength.balanced.sigma, 10);
      expect(KitFrostStrength.strong.sigma, 16);
      expect(KitFrostStrength.balanced.saturation, 1.4);
      expect(KitFrostStrength.balanced.tint, .70);
      for (final s in KitFrostStrength.values) {
        // The header carries 10% more tint, capped at 85%.
        expect(s.headerTint, greaterThan(s.tint));
        expect(s.headerTint, lessThanOrEqualTo(.85));
      }
      expect(KitFrost.noiseOpacity, .03);
      expect(KitFrost.highlightAlpha, .20);
    });

    test('the saturation matrix keeps grey grey and 1 is identity', () {
      final m = KitFrost.saturationMatrix(1.4);
      for (var row = 0; row < 3; row++) {
        final sum = m[row * 5] + m[row * 5 + 1] + m[row * 5 + 2];
        expect(sum, closeTo(1, 1e-9));
      }
      final identity = KitFrost.saturationMatrix(1);
      for (var row = 0; row < 3; row++) {
        for (var col = 0; col < 3; col++) {
          expect(identity[row * 5 + col], closeTo(row == col ? 1 : 0, 1e-9));
        }
      }
    });

    test('the choice picks the strength', () {
      KitFrostStrength? pick(KitGlassChoice choice) =>
          KitFrost.strengthFor(choice: choice, style: KitSurfaceStyle.solid);
      expect(pick(KitGlassChoice.off), isNull);
      expect(pick(KitGlassChoice.weak), KitFrostStrength.weak);
      expect(pick(KitGlassChoice.balanced), KitFrostStrength.balanced);
      expect(pick(KitGlassChoice.strong), KitFrostStrength.strong);
    });

    test('Saver, reduced motion and high contrast keep it solid', () {
      for (final (saver, reduced, contrast) in [
        (true, false, false),
        (false, true, false),
        (false, false, true),
      ]) {
        expect(
          KitFrost.strengthFor(
            choice: KitGlassChoice.strong,
            style: KitSurfaceStyle.solid,
            saver: saver,
            reducedMotion: reduced,
            highContrast: contrast,
          ),
          isNull,
        );
      }
    });

    test('only Solid (and Signal) let the two places turn to glass', () {
      for (final style in KitSurfaceStyle.values) {
        expect(
          KitFrost.styleAllows(style),
          style == KitSurfaceStyle.solid || style.name == 'signal',
          reason: style.name,
        );
      }
    });
  });

  group('strengthOf', () {
    Future<KitFrostStrength?> read(
      WidgetTester tester, {
      KitEffects effects = KitEffects.defaults,
      bool disableAnimations = false,
      bool highContrast = false,
    }) async {
      KitFrostStrength? got;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) {
              got = KitFrost.strengthOf(context);
              return const SizedBox();
            },
          ),
          effects: effects,
          disableAnimations: disableAnimations,
          highContrast: highContrast,
        ),
      );
      return got;
    }

    testWidgets('balanced by default', (tester) async {
      expect(await read(tester), KitFrostStrength.balanced);
    });

    testWidgets('solid under Saver, Animations Off, the system setting, '
        'high contrast, Off and a style of its own', (tester) async {
      expect(await read(tester, effects: KitEffects.defaults.underSaver), null);
      expect(
        await read(
          tester,
          effects: const KitEffects(motion: KitMotionLevel.off),
        ),
        null,
      );
      expect(await read(tester, disableAnimations: true), null);
      expect(await read(tester, highContrast: true), null);
      for (final style in [
        KitSurfaceStyle.soft,
        KitSurfaceStyle.brutal,
        KitSurfaceStyle.paper,
      ]) {
        expect(
          await read(tester, effects: KitEffects(surfaceStyle: style)),
          null,
          reason: style.name,
        );
      }
      GlassChoiceStore.choice.value = KitGlassChoice.off;
      expect(await read(tester), null);
    });
  });

  group('surface', () {
    testWidgets('no blur layer while nothing is under it', (tester) async {
      await tester.pumpWidget(
        _app(_pane(shown: false, strength: KitFrostStrength.balanced)),
      );
      expect(_blurs, findsNothing);
    });

    testWidgets('no blur layer when glass is off, even with content under', (
      tester,
    ) async {
      await tester.pumpWidget(_app(_pane(shown: true)));
      expect(_blurs, findsNothing);
    });

    testWidgets('fades to glass over about 180 ms and back', (tester) async {
      await tester.pumpWidget(
        _app(_pane(shown: false, strength: KitFrostStrength.balanced)),
      );
      await tester.pumpWidget(
        _app(_pane(shown: true, strength: KitFrostStrength.balanced)),
      );
      expect(_blurs, findsOneWidget);
      final well = KitFrost.wellOf(tester.element(find.text('Steer')))!;
      await tester.pump(const Duration(milliseconds: 90));
      expect(well.value, inExclusiveRange(0, 1));
      await tester.pump(const Duration(milliseconds: 120));
      expect(well.value, 1);
      await tester.pumpWidget(
        _app(_pane(shown: false, strength: KitFrostStrength.balanced)),
      );
      await tester.pumpAndSettle();
      expect(well.value, 0);
      expect(_blurs, findsNothing);
    });

    testWidgets('the field inside the pane becomes the denser well', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(_pane(shown: true, strength: KitFrostStrength.balanced)),
      );
      final box = tester.widget<DecoratedBox>(
        find
            .descendant(
              of: find.byType(KitGlass),
              matching: find.byType(DecoratedBox),
            )
            .first,
      );
      final color = (box.decoration as BoxDecoration).color!;
      expect(color.a, closeTo(KitFrost.wellAlpha, .01));
    });

    testWidgets('KitGlass anywhere else stays opaque', (tester) async {
      await tester.pumpWidget(
        _app(const KitGlass(child: SizedBox(width: 200, height: 40))),
      );
      final box = tester.widget<DecoratedBox>(
        find
            .descendant(
              of: find.byType(KitGlass),
              matching: find.byType(DecoratedBox),
            )
            .first,
      );
      expect((box.decoration as BoxDecoration).color!.a, 1);
    });
  });

  group('composer layer', () {
    Future<void> pumpLayer(
      WidgetTester tester, {
      Widget? above,
      KitEffects effects = KitEffects.defaults,
      bool wide = false,
    }) async {
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        _app(
          _layer(above: above, wide: wide),
          effects: effects,
        ),
      );
      await tester.pumpAndSettle();
    }

    Future<void> scrollUp(WidgetTester tester) async {
      await tester.drag(
        find.byKey(const ValueKey('transcript')),
        const Offset(0, 300),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('solid at the newest line; glass once scrolled under', (
      tester,
    ) async {
      await pumpLayer(tester);
      expect(_blurs, findsNothing);
      await scrollUp(tester);
      expect(_blurs, findsOneWidget);
      // Back at the newest line: nothing under the composer, no blur.
      await tester.drag(
        find.byKey(const ValueKey('transcript')),
        const Offset(0, -2000),
      );
      await tester.pumpAndSettle();
      expect(_blurs, findsNothing);
    });

    testWidgets('the parts over the composer share its pane', (tester) async {
      await pumpLayer(tester, above: const SizedBox(height: 60));
      await scrollUp(tester);
      expect(_blurs, findsOneWidget);
    });

    testWidgets('the field alone is the glass: no second outline', (
      tester,
    ) async {
      await pumpLayer(tester);
      expect(tester.widget<KitFrost>(find.byType(KitFrost)).edges, isFalse);
      await pumpLayer(tester, above: const SizedBox(height: 60));
      expect(tester.widget<KitFrost>(find.byType(KitFrost)).edges, isTrue);
    });

    testWidgets('a sideways chips line ends inside the pane, not across it', (
      tester,
    ) async {
      const strip = ValueKey('strip');
      await pumpLayer(
        tester,
        wide: true,
        above: KitComposerStatusStrip(
          stripKey: strip,
          chips: [for (var i = 0; i < 8; i++) KitChip(label: 'Chip number $i')],
        ),
      );
      final tokens = KitTokens.of(tester.element(find.byKey(strip)));
      final line = tester.getRect(find.byKey(strip));
      // The pane's edge stands one space2 out from the composer's.
      final paneEdge =
          tester.getRect(find.byType(KitGlass)).left - tokens.space2;
      expect(line.left, greaterThanOrEqualTo(paneEdge));
      // The first chip still starts on the composer's gutter.
      expect(
        tester.getRect(find.byType(KitChip).first).left,
        tester.getRect(find.byType(KitGlass)).left,
      );
    });

    testWidgets('stays solid under Saver', (tester) async {
      await pumpLayer(tester, effects: KitEffects.defaults.underSaver);
      await scrollUp(tester);
      expect(_blurs, findsNothing);
    });

    testWidgets('Off in Settings turns an open chat solid at once', (
      tester,
    ) async {
      await pumpLayer(tester);
      await scrollUp(tester);
      expect(_blurs, findsOneWidget);
      GlassChoiceStore.choice.value = KitGlassChoice.off;
      await tester.pump();
      expect(_blurs, findsNothing);
    });
  });

  group('Glass choice', () {
    testWidgets('stores the choice on this device', (tester) async {
      SharedPreferences.setMockInitialValues({});
      await tester.pumpWidget(_app(const GlassChoiceControl()));
      await tester.tap(find.byKey(const ValueKey('frost-glass-strong')));
      await tester.pump();
      expect(GlassChoiceStore.choice.value, KitGlassChoice.strong);
      final prefs = await SharedPreferences.getInstance();
      expect(prefs.getString(GlassChoiceStore.prefsKey), 'strong');
      expect(GlassChoiceStore.prefsKey, isNot(contains('.glass.')));
    });

    test('reads a stored choice once', () async {
      SharedPreferences.setMockInitialValues({'oc.glass': 'weak'});
      await GlassChoiceStore.ensureLoaded();
      expect(GlassChoiceStore.choice.value, KitGlassChoice.weak);
      expect(GlassChoiceStore.parse('glass'), isNull);
    });

    testWidgets('says why it is held under Saver', (tester) async {
      await tester.pumpWidget(
        _app(
          const GlassChoiceControl(),
          effects: KitEffects.defaults.underSaver,
        ),
      );
      final note = tester.widget<KitText>(
        find.byKey(const ValueKey('frost-glass-note')),
      );
      expect(note.text, contains('Saver'));
    });
  });

  kitMotionStillTests(
    'KitFrost',
    builds: {
      'glass': () => _pane(shown: true, strength: KitFrostStrength.balanced),
      'solid': () => _pane(shown: false, strength: KitFrostStrength.balanced),
    },
  );
}
