// "Glowing border while replying" (KitComposer.activityGlow /
// KitEffects.activityGlow): on by default, drawn only while a reply runs, as
// the classic ring (the restored original) or the soft ring sweep. Calm is
// still; Motion Off and the system's remove-animations draw none.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';

Finder _glow([String? mode]) => mode == null
    ? find.byWidgetPredicate(
        (w) =>
            w.key is ValueKey<String> &&
            (w.key! as ValueKey<String>).value.startsWith('kit-activity-glow'),
      )
    : find.byKey(ValueKey('kit-activity-glow-$mode'));

final _softRing = KitEffects.defaults.copyWith(
  glowStyle: KitGlowStyle.softRing,
);

Future<void> _pump(
  WidgetTester tester, {
  required bool busy,
  bool? glow,
  KitEffects effects = KitEffects.defaults,
  bool reduced = false,
}) async {
  final controller = TextEditingController();
  final focus = FocusNode();
  addTearDown(controller.dispose);
  addTearDown(focus.dispose);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    KitEffectsScope(
      effects: effects,
      child: MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
          child: child!,
        ),
        home: Scaffold(
          body: Align(
            alignment: Alignment.bottomCenter,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: KitComposer(
                controller: controller,
                focusNode: focus,
                hint: 'Ask',
                onSend: () {},
                onStop: () {},
                busy: busy,
                activityGlow: glow,
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('on by default: the classic ring while a reply runs', (
    tester,
  ) async {
    await _pump(tester, busy: false);
    expect(_glow(), findsNothing);
    await _pump(tester, busy: true);
    expect(_glow('frame'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('kit-activity-glow-halo')),
      findsOneWidget,
    );
    await _pump(tester, busy: false);
    expect(_glow(), findsNothing);
  });

  testWidgets('the classic painter strokes the box border', (tester) async {
    await _pump(tester, busy: true);
    final box = tester.renderObject(_glow('frame'));
    expect(box, paints..rrect());
    // Two colours: the same painter, the partner hue as the highlight.
    await _pump(
      tester,
      busy: true,
      effects: KitEffects.defaults.copyWith(glowColours: KitGlowColours.two),
    );
    expect(tester.renderObject(_glow('frame')), paints..rrect());
  });

  testWidgets('soft ring: drawn only while a reply runs, no halo', (
    tester,
  ) async {
    await _pump(tester, busy: false, effects: _softRing);
    expect(_glow(), findsNothing);
    await _pump(tester, busy: true, effects: _softRing);
    expect(_glow('frame'), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-activity-glow-halo')), findsNothing);
    await _pump(tester, busy: false, effects: _softRing);
    expect(_glow(), findsNothing);
  });

  testWidgets('switched off: a running reply draws no glow', (tester) async {
    await _pump(
      tester,
      busy: true,
      effects: KitEffects.defaults.copyWith(activityGlow: false),
    );
    expect(_glow(), findsNothing);
    // The composer's own flag wins over the setting.
    await _pump(tester, busy: true, glow: false);
    expect(_glow(), findsNothing);
  });

  testWidgets('Calm draws one still glow, Off draws none (both styles)', (
    tester,
  ) async {
    for (final base in [KitEffects.defaults, _softRing]) {
      await _pump(
        tester,
        busy: true,
        effects: base.copyWith(motion: KitMotionLevel.calm),
      );
      expect(_glow('calm'), findsOneWidget);
      await _pump(
        tester,
        busy: true,
        effects: base.copyWith(motion: KitMotionLevel.off),
      );
      expect(_glow(), findsNothing);
      expect(
        find.byKey(const ValueKey('kit-activity-glow-halo')),
        findsNothing,
      );
    }
  });

  testWidgets('the system remove-animations draws none', (tester) async {
    await _pump(tester, busy: true, reduced: true);
    expect(_glow(), findsNothing);
    await _pump(tester, busy: true, reduced: true, effects: _softRing);
    expect(_glow(), findsNothing);
  });

  testWidgets('Stop and the box are unchanged with the glow on', (
    tester,
  ) async {
    await _pump(tester, busy: true);
    expect(find.bySemanticsLabel('Stop the reply'), findsWidgets);
    expect(find.text('Writing…'), findsNothing);
  });

  testWidgets('live classic: one lap at Normal, Slow and Fast within the cap', (
    tester,
  ) async {
    KitMotion.loops = true;
    addTearDown(() => KitMotion.loops = false);
    expect(1 / KitMotion.classicGlowLapsPerSecond, closeTo(3.6, 1e-9));
    for (final speed in KitGlowSpeed.values) {
      final controller = TextEditingController();
      final focus = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focus.dispose);
      await tester.pumpWidget(
        KitEffectsScope(
          effects: KitEffects.defaults.copyWith(glowSpeed: speed),
          child: MaterialApp(
            theme: AppTheme.dark(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: KitComposer(
                controller: controller,
                focusNode: focus,
                hint: 'Ask',
                onSend: () {},
                busy: true,
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 100));
      expect(_glow('live'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('kit-activity-glow-halo')),
        findsOneWidget,
      );
      // Halfway through the lap the highlight has moved.
      await tester.pump(const Duration(milliseconds: 700));
      expect(tester.hasRunningAnimations, isTrue);
      await tester.pumpWidget(const SizedBox.shrink());
    }
    expect(
      KitMotion.classicGlowLapsPerSecond * 2,
      lessThanOrEqualTo(KitMotion.glowMaxLapsPerSecond),
    );
  });

  testWidgets('live soft ring: travels slowly, one lap takes at least 6 s', (
    tester,
  ) async {
    KitMotion.loops = true;
    addTearDown(() => KitMotion.loops = false);
    final controller = TextEditingController();
    final focus = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focus.dispose);
    await tester.pumpWidget(
      KitEffectsScope(
        effects: _softRing.copyWith(glowSpeed: KitGlowSpeed.fast),
        child: MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: KitComposer(
              controller: controller,
              focusNode: focus,
              hint: 'Ask',
              onSend: () {},
              busy: true,
            ),
          ),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 100));
    expect(_glow('live'), findsOneWidget);
    expect(1 / KitMotion.activityGlowLapsPerSecond, greaterThanOrEqualTo(6));
    expect(
      KitMotion.activityGlowLapsPerSecond,
      lessThanOrEqualTo(KitMotion.edgeLightMaxLapsPerSecond),
    );
    // Unmount so no ticker outlives the test.
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
