// One moving part while a reply runs (owner rule): the glowing border OR
// Stop's turning ring, never both. The decision is `_movingPart` in the
// composer kit; these tests look at what actually moves.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';

final _stopRing = find.byKey(const ValueKey('kit-composer-stop-ring'));
final _glowRing = find.byWidgetPredicate(
  (w) =>
      w.key is ValueKey<String> &&
      (w.key! as ValueKey<String>).value.startsWith('kit-activity-glow-') &&
      !(w.key! as ValueKey<String>).value.endsWith('halo'),
);

Future<void> _run(
  WidgetTester tester,
  KitEffects effects, {
  bool reduced = false,
}) async {
  KitMotion.loops = true;
  addTearDown(() => KitMotion.loops = false);
  final controller = TextEditingController();
  final focus = FocusNode();
  addTearDown(controller.dispose);
  addTearDown(focus.dispose);
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
          body: KitComposer(
            controller: controller,
            focusNode: focus,
            hint: 'Ask',
            onSend: () {},
            onStop: () {},
            busy: true,
          ),
        ),
      ),
    ),
  );
  await tester.pump(const Duration(milliseconds: 300));
}

/// How many frames the box asks for in the next second: 0 when nothing
/// ticks.
Future<int> _frames(WidgetTester tester) async {
  var n = 0;
  for (var i = 0; i < 10; i++) {
    if (tester.binding.hasScheduledFrame ||
        tester.binding.transientCallbackCount > 0) {
      n++;
    }
    await tester.pump(const Duration(milliseconds: 100));
  }
  return n;
}

Future<void> _unmount(WidgetTester tester) =>
    tester.pumpWidget(const SizedBox.shrink());

void main() {
  testWidgets('glow on: the glow moves, Stop holds a still track', (
    tester,
  ) async {
    await _run(tester, KitEffects.defaults);
    expect(_glowRing, findsOneWidget);
    expect(_stopRing, findsOneWidget);
    // Stop paints its track only: one arc, no travelling arc.
    expect(tester.renderObject(_stopRing), paints..arc());
    expect(
      tester.renderObject(_stopRing),
      isNot(
        paints
          ..arc()
          ..arc(),
      ),
    );
    // Soft ring too.
    await _unmount(tester);
    await _run(
      tester,
      KitEffects.defaults.copyWith(glowStyle: KitGlowStyle.softRing),
    );
    expect(
      tester.renderObject(_stopRing),
      isNot(
        paints
          ..arc()
          ..arc(),
      ),
    );
    await _unmount(tester);
  });

  testWidgets('glow off: Stop turns, nothing else moves', (tester) async {
    await _run(tester, KitEffects.defaults.copyWith(activityGlow: false));
    expect(_glowRing, findsNothing);
    expect(
      tester.renderObject(_stopRing),
      paints
        ..arc()
        ..arc(),
    );
    expect(await _frames(tester), greaterThan(0));
    await _unmount(tester);
  });

  testWidgets('Calm and Off: neither moves', (tester) async {
    for (final glow in [true, false]) {
      for (final level in [KitMotionLevel.calm, KitMotionLevel.off]) {
        await _run(
          tester,
          KitEffects.defaults.copyWith(activityGlow: glow, motion: level),
        );
        expect(await _frames(tester), 0, reason: '$level glow=$glow');
        await _unmount(tester);
      }
    }
  });

  testWidgets('the system remove-animations: neither moves', (tester) async {
    for (final glow in [true, false]) {
      await _run(
        tester,
        KitEffects.defaults.copyWith(activityGlow: glow),
        reduced: true,
      );
      expect(await _frames(tester), 0, reason: 'glow=$glow');
      await _unmount(tester);
    }
  });
}
