// The chosen "Glowing border while replying" (KitComposer.activityGlow /
// KitEffects.activityGlow): a soft ring sweep around the whole box while a
// reply runs, in addition to the living edge. Off by default; Calm is still;
// Motion Off and the system's remove-animations draw none.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_turn.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';

const _live = KitTurnLive(activity: KitTurnActivity.writing, pace: .5);

Finder _glow([String? mode]) => mode == null
    ? find.byWidgetPredicate(
        (w) =>
            w.key is ValueKey<String> &&
            (w.key! as ValueKey<String>).value.startsWith('kit-activity-glow'),
      )
    : find.byKey(ValueKey('kit-activity-glow-$mode'));

Future<void> _pump(
  WidgetTester tester, {
  required KitTurnLive? rail,
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
                busy: rail != null,
                rail: rail,
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
  testWidgets('off by default: a running reply draws no glow', (tester) async {
    await _pump(tester, rail: _live);
    expect(_glow(), findsNothing);
  });

  testWidgets('on: drawn only while a reply runs', (tester) async {
    await _pump(tester, rail: null, glow: true);
    expect(_glow(), findsNothing);
    await _pump(tester, rail: _live, glow: true);
    expect(_glow('frame'), findsOneWidget);
    await _pump(tester, rail: null, glow: true);
    expect(_glow(), findsNothing);
  });

  testWidgets('follows the Appearance switch when not told', (tester) async {
    await _pump(
      tester,
      rail: _live,
      effects: KitEffects.defaults.copyWith(activityGlow: true),
    );
    expect(_glow('frame'), findsOneWidget);
  });

  testWidgets('Calm draws one still glow, Off draws none', (tester) async {
    await _pump(
      tester,
      rail: _live,
      glow: true,
      effects: KitEffects.defaults.copyWith(motion: KitMotionLevel.calm),
    );
    expect(_glow('calm'), findsOneWidget);
    await _pump(
      tester,
      rail: _live,
      glow: true,
      effects: KitEffects.defaults.copyWith(motion: KitMotionLevel.off),
    );
    expect(_glow(), findsNothing);
  });

  testWidgets('the system remove-animations draws none', (tester) async {
    await _pump(tester, rail: _live, glow: true, reduced: true);
    expect(_glow(), findsNothing);
  });

  testWidgets('living edge and Stop are unchanged with the glow on', (
    tester,
  ) async {
    await _pump(tester, rail: _live, glow: true);
    expect(find.text('Writing…'), findsOneWidget);
  });

  testWidgets('live: travels slowly, one lap takes at least 6 s', (
    tester,
  ) async {
    KitMotion.loops = true;
    addTearDown(() => KitMotion.loops = false);
    final controller = TextEditingController();
    final focus = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focus.dispose);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: KitComposer(
            controller: controller,
            focusNode: focus,
            hint: 'Ask',
            onSend: () {},
            rail: _live,
            activityGlow: true,
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
