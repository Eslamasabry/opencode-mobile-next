// KitMotionParts (docs/ux-system/kit-api/KitMotionParts.md): KitSwap,
// KitSpin, KitAnimatedBox, KitDim, KitAnimatedValue. Every part shows its
// final state on the first pump(), is instant under reduced motion, and
// settles for pumpAndSettle (nothing loops).
import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/effects.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_motion_parts.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_reveal.dart';

import 'kit_motion_still.dart';

/// One theme instance for every pump: a fresh `AppTheme.dark()` on a rebuild
/// would not compare equal and `MaterialApp`'s `AnimatedTheme` would animate
/// between the two (the harness moving, not the part under test).
final _theme = AppTheme.dark();

/// A widget with no paragraph in its render subtree, standing in for an
/// image or a drawing (KitDim dims those, never an [Icon]: an icon glyph
/// paints through a `RenderParagraph` like any other text, and dimming a
/// glyph is kit-KitIcon's job — see the Replaces table).
const Widget _mark = SizedBox.square(
  key: ValueKey('mark'),
  dimension: 24,
  child: ColoredBox(color: Color(0xFF3366CC)),
);
final _markFinder = find.byKey(const ValueKey('mark'));

/// An app around [child] with the person's effects choices and reduced
/// motion applied the way the real app does (`KitEffectsScope` plus
/// `MediaQuery.disableAnimations`).
Widget _harness(
  Widget child, {
  KitMotionLevel motion = KitMotionLevel.full,
  bool disableAnimations = false,
  Locale locale = const Locale('en'),
}) => KitEffectsScope(
  effects: KitEffects(motion: motion),
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: _theme,
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, widget) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(disableAnimations: disableAnimations),
      child: widget!,
    ),
    home: Scaffold(body: Center(child: child)),
  ),
);

/// The opacity actually painted over [finder]'s render object: the product
/// of every opacity render object between it and [KitDim] / [KitSwap] /
/// the root. Reads the render tree, not a widget's target, so it keeps
/// working whatever widgets a part builds inside.
double _paintedOpacity(WidgetTester tester, Finder finder) {
  var opacity = 1.0;
  RenderObject? node = tester.renderObject(finder);
  while (node != null) {
    if (node is RenderOpacity) opacity *= node.opacity;
    if (node is RenderAnimatedOpacityMixin) {
      opacity *= node.opacity.value;
    }
    node = node.parent;
  }
  return opacity;
}

/// The clockwise angle, in turns, at which [finder]'s render object paints
/// its local "down" (0, 1): 0 when upright, .5 when upside down. Read from
/// the paint transform to the screen, so it is what the person sees.
double _paintedTurns(WidgetTester tester, Finder finder) {
  final box = tester.renderObject(finder);
  final m = box.getTransformTo(null);
  final origin = MatrixUtils.transformPoint(m, Offset.zero);
  final down = MatrixUtils.transformPoint(m, const Offset(0, 1)) - origin;
  // Down (0, 1) is angle pi/2 on screen; a clockwise turn adds to it.
  final turns = (math.atan2(down.dy, down.dx) - math.pi / 2) / (2 * math.pi);
  final wrapped = (turns % 1 + 1) % 1;
  return wrapped > 1 - 1e-7 ? 0 : wrapped;
}

/// Every label in the live semantics tree, from its root down: what a
/// screen reader can reach.
List<String> _semanticLabels(WidgetTester tester) {
  var node = tester.getSemantics(find.byType(Scaffold));
  while (node.parent != null) {
    node = node.parent!;
  }
  final labels = <String>[];
  bool visit(SemanticsNode n) {
    if (n.label.isNotEmpty) labels.add(n.label);
    n.visitChildren(visit);
    return true;
  }

  visit(node);
  return labels;
}

/// A stateful child that counts its own initState and dispose calls, so a
/// test can prove KitSwap keeps its state while it leaves.
class _Counted extends StatefulWidget {
  const _Counted(this.label, this.log, {super.key});

  final String label;
  final Map<String, List<String>> log;

  @override
  State<_Counted> createState() => _CountedState();
}

class _CountedState extends State<_Counted> {
  @override
  void initState() {
    super.initState();
    widget.log.putIfAbsent(widget.label, () => []).add('init');
  }

  @override
  void dispose() {
    widget.log[widget.label]!.add('dispose');
    super.dispose();
  }

  @override
  Widget build(BuildContext context) =>
      SizedBox(key: ValueKey('box-${widget.label}'), width: 120, height: 40);
}

const _star = SizedBox(key: ValueKey('star'), width: 24, height: 8);
final _starFinder = find.byKey(const ValueKey('star'));
final _chevronFinder = find.byIcon(AppIconography.chevronDown);

void main() {
  kitMotionStillTests(
    'KitSwap',
    builds: {'default': () => const KitSwap(child: KitText('Working'))},
  );
  kitMotionStillTests(
    'KitSpin',
    builds: {
      'quarter': () =>
          const KitSpin(turns: 0.25, child: SizedBox.square(dimension: 24)),
    },
  );
  kitMotionStillTests(
    'KitAnimatedBox',
    builds: {
      'surface2': () => const KitAnimatedBox(
        level: KitSurfaceLevel.surface2,
        child: KitText('Working'),
      ),
    },
  );
  kitMotionStillTests(
    'KitDim',
    builds: {
      'dimmed': () => const KitDim(
        child: SizedBox.square(
          dimension: 24,
          child: ColoredBox(color: Color(0xFF3D6BFF)),
        ),
      ),
    },
  );
  kitMotionStillTests(
    'KitAnimatedValue',
    builds: {
      'value': () => KitAnimatedValue(
        value: 0.4,
        builder: (context, value) => KitText('${(value * 100).round()} %'),
      ),
    },
  );

  group('first build is final (no mount animation)', () {
    testWidgets('KitSwap', (tester) async {
      await tester.pumpWidget(
        _harness(const KitSwap(child: Text('one', key: ValueKey('one')))),
      );
      expect(tester.hasRunningAnimations, isFalse);
      expect(_paintedOpacity(tester, find.text('one')), 1);
    });

    testWidgets('KitSpin', (tester) async {
      await tester.pumpWidget(
        _harness(const KitSpin(turns: .25, child: _star)),
      );
      expect(tester.hasRunningAnimations, isFalse);
      expect(_paintedTurns(tester, _starFinder), closeTo(.25, 1e-9));
    });

    testWidgets('KitSpin.chevron', (tester) async {
      await tester.pumpWidget(_harness(const KitSpin.chevron(expanded: true)));
      expect(tester.hasRunningAnimations, isFalse);
      expect(_paintedTurns(tester, _chevronFinder), closeTo(.5, 1e-9));
    });

    testWidgets('KitAnimatedBox', (tester) async {
      await tester.pumpWidget(
        _harness(
          const KitAnimatedBox(
            level: KitSurfaceLevel.surface2,
            child: SizedBox(width: 40, height: 40),
          ),
        ),
      );
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('KitDim', (tester) async {
      await tester.pumpWidget(_harness(const KitDim(child: _mark)));
      expect(tester.hasRunningAnimations, isFalse);
      expect(_paintedOpacity(tester, _markFinder), KitTokens.disabledAlpha);
    });

    testWidgets('KitAnimatedValue', (tester) async {
      double? seen;
      await tester.pumpWidget(
        _harness(
          KitAnimatedValue(
            value: .3,
            builder: (context, value) {
              seen = value;
              return const SizedBox.shrink();
            },
          ),
        ),
      );
      expect(tester.hasRunningAnimations, isFalse);
      expect(seen, .3);
    });
  });

  group('reduced motion settles after one pump() (G8, MOT-7)', () {
    Future<void> expectSettles(
      WidgetTester tester, {
      required Widget Function() before,
      required Widget Function() after,
      required bool disableAnimations,
      required KitMotionLevel motion,
    }) async {
      await tester.pumpWidget(
        _harness(
          before(),
          disableAnimations: disableAnimations,
          motion: motion,
        ),
      );
      await tester.pumpAndSettle();
      await tester.pumpWidget(
        _harness(after(), disableAnimations: disableAnimations, motion: motion),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(tester.hasRunningAnimations, isFalse);
    }

    for (final (label, disableAnimations, motion) in [
      ('system reduced motion', true, KitMotionLevel.full),
      ('Effects Animations: Off', false, KitMotionLevel.off),
    ]) {
      testWidgets('KitSwap ($label)', (tester) async {
        await expectSettles(
          tester,
          before: () => const KitSwap(child: Text('a', key: ValueKey('a'))),
          after: () => const KitSwap(child: Text('b', key: ValueKey('b'))),
          disableAnimations: disableAnimations,
          motion: motion,
        );
        expect(find.text('a'), findsNothing);
        expect(find.text('b'), findsOneWidget);
      });

      testWidgets('KitSpin ($label)', (tester) async {
        await expectSettles(
          tester,
          before: () => const KitSpin(turns: 0, child: _star),
          after: () => const KitSpin(turns: .5, child: _star),
          disableAnimations: disableAnimations,
          motion: motion,
        );
        expect(_paintedTurns(tester, _starFinder), closeTo(.5, 1e-9));
      });

      testWidgets('KitSpin.chevron ($label)', (tester) async {
        await expectSettles(
          tester,
          before: () => const KitSpin.chevron(expanded: false),
          after: () => const KitSpin.chevron(expanded: true),
          disableAnimations: disableAnimations,
          motion: motion,
        );
        expect(_paintedTurns(tester, _chevronFinder), closeTo(.5, 1e-9));
      });

      testWidgets('KitAnimatedBox ($label)', (tester) async {
        await expectSettles(
          tester,
          before: () => const KitAnimatedBox(
            level: KitSurfaceLevel.surface1,
            child: SizedBox(width: 40, height: 40),
          ),
          after: () => const KitAnimatedBox(
            level: KitSurfaceLevel.surface3,
            child: SizedBox(width: 40, height: 40),
          ),
          disableAnimations: disableAnimations,
          motion: motion,
        );
      });

      testWidgets('KitDim ($label)', (tester) async {
        await expectSettles(
          tester,
          before: () => const KitDim(dimmed: false, child: _mark),
          after: () => const KitDim(child: _mark),
          disableAnimations: disableAnimations,
          motion: motion,
        );
        expect(_paintedOpacity(tester, _markFinder), KitTokens.disabledAlpha);
      });

      testWidgets('KitAnimatedValue ($label)', (tester) async {
        double? seen;
        Widget build(double value) => KitAnimatedValue(
          value: value,
          builder: (context, v) {
            seen = v;
            return const SizedBox.shrink();
          },
        );
        await expectSettles(
          tester,
          before: () => build(.2),
          after: () => build(.8),
          disableAnimations: disableAnimations,
          motion: motion,
        );
        expect(seen, .8);
      });
    }
  });

  group('KitSwap', () {
    for (final (pace, length) in [
      (KitPace.quick, KitMotion.quick),
      (KitPace.standard, KitMotion.standard),
    ]) {
      testWidgets('a keyed change cross-fades over exactly ${pace.name} '
          '(${length.inMilliseconds} ms), with no scale or size animation, and '
          'the outgoing child ignores taps and has no semantics', (
        tester,
      ) async {
        final taps = <String>[];
        Widget build(String key) => KitSwap(
          pace: pace,
          child: Semantics(
            key: ValueKey(key),
            label: key,
            button: true,
            child: GestureDetector(
              onTap: () => taps.add(key),
              child: SizedBox(
                key: ValueKey('box-$key'),
                width: 300,
                height: 80,
              ),
            ),
          ),
        );
        final semantics = tester.ensureSemantics();
        await tester.pumpWidget(_harness(build('a')));
        await tester.pumpAndSettle();
        final size = tester.getSize(find.byType(KitSwap));
        await tester.pumpWidget(_harness(build('b')));
        // A little real time, not zero: right at t=0 the incoming fade's
        // opacity is 0 and a fully transparent child is excluded from
        // semantics anyway, which would falsely look like exclusion.
        const step = Duration(milliseconds: 30);
        await tester.pump(step);

        expect(tester.hasRunningAnimations, isTrue);
        // Both children paint part-way: a cross-fade, not a cut.
        final leaving = _paintedOpacity(
          tester,
          find.byKey(const ValueKey('box-a')),
        );
        final arriving = _paintedOpacity(
          tester,
          find.byKey(const ValueKey('box-b')),
        );
        expect(leaving, inExclusiveRange(0, 1));
        expect(arriving, inExclusiveRange(0, 1));
        // No size animation: the swap keeps its settled size.
        expect(tester.getSize(find.byType(KitSwap)), size);
        // Scoped to KitSwap's own subtree: the app shell's page-transition
        // machinery (Android's default zoom route transition) legitimately
        // uses ScaleTransition/Transform elsewhere in the tree.
        final swap = find.byType(KitSwap);
        for (final type in [ScaleTransition, SizeTransition, Transform]) {
          expect(
            find.descendant(of: swap, matching: find.byType(type)),
            findsNothing,
          );
        }

        // The outgoing ('a') is excluded from semantics while it leaves:
        // read from the live semantics tree, what a screen reader gets.
        expect(_semanticLabels(tester), isNot(contains('a')));
        expect(_semanticLabels(tester), contains('b'));

        // The outgoing ignores taps: tapping its box does not call back.
        await tester.tapAt(
          tester.getCenter(find.byKey(const ValueKey('box-a'))),
        );
        expect(taps, isEmpty);

        // Settles after exactly the pace's length (30 ms already elapsed).
        await tester.pump(length - step - const Duration(milliseconds: 1));
        expect(tester.hasRunningAnimations, isTrue);
        await tester.pump(const Duration(milliseconds: 2));
        expect(tester.hasRunningAnimations, isFalse);
        expect(find.byKey(const ValueKey('a')), findsNothing);
        expect(_paintedOpacity(tester, find.byKey(const ValueKey('box-b'))), 1);

        semantics.dispose();
      });
    }

    testWidgets('the leaving child keeps its state until it has faded out', (
      tester,
    ) async {
      final log = <String, List<String>>{};
      Widget build(String label) =>
          KitSwap(child: _Counted(label, log, key: ValueKey(label)));
      await tester.pumpWidget(_harness(build('a')));
      await tester.pumpAndSettle();
      expect(log['a'], ['init']);

      await tester.pumpWidget(_harness(build('b')));
      await tester.pump(const Duration(milliseconds: 50));
      // Still the first State: not rebuilt from scratch, not disposed.
      expect(log['a'], ['init']);
      expect(log['b'], ['init']);
      expect(find.byKey(const ValueKey('box-a')), findsOneWidget);

      await tester.pumpAndSettle();
      expect(log['a'], ['init', 'dispose']);
      expect(log['b'], ['init']);
    });

    testWidgets(
      'a change mid-swap fades each leaving child on from its current opacity',
      (tester) async {
        final log = <String, List<String>>{};
        Widget build(String label) =>
            KitSwap(child: _Counted(label, log, key: ValueKey(label)));
        double opacityOf(String label) =>
            _paintedOpacity(tester, find.byKey(ValueKey('box-$label')));

        await tester.pumpWidget(_harness(build('a')));
        await tester.pumpAndSettle();
        await tester.pumpWidget(_harness(build('b')));
        await tester.pump(const Duration(milliseconds: 60));
        final aBefore = opacityOf('a');
        final bBefore = opacityOf('b');
        expect(bBefore, inExclusiveRange(0, 1));

        await tester.pumpWidget(_harness(build('c')));
        // No jump: both leaving children still show where they were.
        expect(find.byKey(const ValueKey('box-a')), findsOneWidget);
        expect(opacityOf('a'), closeTo(aBefore, 1e-9));
        expect(opacityOf('b'), closeTo(bBefore, 1e-9));

        await tester.pump(const Duration(milliseconds: 20));
        expect(opacityOf('b'), lessThan(bBefore));
        expect(log['a'], ['init']);
        expect(log['b'], ['init']);

        await tester.pumpAndSettle();
        expect(log['a'], ['init', 'dispose']);
        expect(log['b'], ['init', 'dispose']);
        expect(log['c'], ['init']);
        expect(opacityOf('c'), 1);
      },
    );

    testWidgets('pumpAndSettle completes (nothing loops)', (tester) async {
      await tester.pumpWidget(_harness(const KitSwap(child: Text('x'))));
      await tester.pumpWidget(
        _harness(const KitSwap(child: Text('y', key: ValueKey('y')))),
      );
      await tester.pumpAndSettle();
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('KitSwap.count', () {
    Widget count(int n, {KitMotionLevel motion = KitMotionLevel.full}) =>
        _harness(
          KitSwap.count(count: n, child: Text('$n')),
          motion: motion,
        );

    /// Where [text] paints, in global coordinates.
    double top(WidgetTester tester, String text) =>
        tester.getTopLeft(find.text(text)).dy;

    testWidgets('the first build shows the number at once', (tester) async {
      await tester.pumpWidget(count(3));
      expect(find.text('3'), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('a rising count rolls up: the new number comes from below, '
        'the old one leaves above, and both fade', (tester) async {
      await tester.pumpWidget(count(3));
      final rest = top(tester, '3');
      await tester.pumpWidget(count(4));
      await tester.pump(KitMotion.quick ~/ 3);
      expect(find.text('3'), findsOneWidget);
      expect(find.text('4'), findsOneWidget);
      expect(top(tester, '4'), greaterThan(rest));
      expect(top(tester, '3'), lessThan(rest));
      expect(_paintedOpacity(tester, find.text('4')), inExclusiveRange(0, 1));
      expect(_paintedOpacity(tester, find.text('3')), inExclusiveRange(0, 1));
      // The leaving number is read by nobody and takes no taps.
      expect(
        find.descendant(
          of: find.byWidgetPredicate(
            (w) => w is ExcludeSemantics && w.excluding,
          ),
          matching: find.text('3'),
        ),
        findsOneWidget,
      );
      await tester.pumpAndSettle();
      expect(find.text('3'), findsNothing);
      expect(top(tester, '4'), moreOrLessEquals(rest));
    });

    testWidgets('a falling count rolls down', (tester) async {
      await tester.pumpWidget(count(4));
      final rest = top(tester, '4');
      await tester.pumpWidget(count(3));
      await tester.pump(KitMotion.quick ~/ 3);
      expect(top(tester, '3'), lessThan(rest));
      expect(top(tester, '4'), greaterThan(rest));
      await tester.pumpAndSettle();
    });

    testWidgets('Animations: Calm only cross-fades, without travel', (
      tester,
    ) async {
      await tester.pumpWidget(count(3, motion: KitMotionLevel.calm));
      final rest = top(tester, '3');
      await tester.pumpWidget(count(4, motion: KitMotionLevel.calm));
      await tester.pump(KitMotion.quick ~/ 3);
      expect(find.text('3'), findsOneWidget);
      expect(top(tester, '4'), moreOrLessEquals(rest));
      expect(top(tester, '3'), moreOrLessEquals(rest));
      expect(_paintedOpacity(tester, find.text('4')), inExclusiveRange(0, 1));
      await tester.pumpAndSettle();
    });

    testWidgets('Animations: Off changes the number at once', (tester) async {
      await tester.pumpWidget(count(3, motion: KitMotionLevel.off));
      await tester.pumpWidget(count(4, motion: KitMotionLevel.off));
      await tester.pump();
      expect(find.text('3'), findsNothing);
      expect(find.text('4'), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('the same count rebuilt does not roll', (tester) async {
      await tester.pumpWidget(count(3));
      await tester.pumpWidget(count(3));
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('KitSpin', () {
    testWidgets('KitSpin(turns: .5) animates', (tester) async {
      await tester.pumpWidget(_harness(const KitSpin(turns: 0, child: _star)));
      await tester.pumpAndSettle();
      final before = tester.getSize(_starFinder);
      await tester.pumpWidget(_harness(const KitSpin(turns: .5, child: _star)));
      await tester.pump(const Duration(milliseconds: 50));
      expect(tester.hasRunningAnimations, isTrue);
      // Painted part-way round, and the layout is untouched (paint only).
      expect(_paintedTurns(tester, _starFinder), inExclusiveRange(0.01, .49));
      expect(tester.getSize(_starFinder), before);
      await tester.pumpAndSettle();
      expect(_paintedTurns(tester, _starFinder), closeTo(.5, 1e-9));
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('KitSpin.fixed(quarterTurns: 1) swaps width and height', (
      tester,
    ) async {
      await tester.pumpWidget(
        _harness(
          const KitSpin.fixed(
            quarterTurns: 1,
            child: SizedBox(width: 100, height: 40),
          ),
        ),
      );
      final size = tester.getSize(find.byType(RotatedBox));
      expect(size.width, 40);
      expect(size.height, 100);
      expect(tester.hasRunningAnimations, isFalse);
    });

    for (final direction in TextDirection.values) {
      testWidgets(
        'KitSpin.chevron(expanded: true) points up under ${direction.name} '
        'and turns over KitMotion.standard with emphasized',
        (tester) async {
          Widget build(bool expanded) => _harness(
            locale: direction == TextDirection.rtl
                ? const Locale('ar')
                : const Locale('en'),
            KitSpin.chevron(expanded: expanded),
          );
          await tester.pumpWidget(build(false));
          await tester.pumpAndSettle();
          expect(Directionality.of(tester.element(_chevronFinder)), direction);
          // chevronDown drawn upright: pointing down when folded.
          expect(_paintedTurns(tester, _chevronFinder), closeTo(0, 1e-9));

          await tester.pumpWidget(build(true));
          // Sampled against KitMotion.emphasized over KitMotion.standard.
          const step = Duration(milliseconds: 50);
          var elapsed = Duration.zero;
          while (elapsed + step < KitMotion.standard) {
            await tester.pump(step);
            elapsed += step;
            final t =
                elapsed.inMicroseconds / KitMotion.standard.inMicroseconds;
            expect(
              _paintedTurns(tester, _chevronFinder),
              closeTo(.5 * KitMotion.emphasized.transform(t), 1e-6),
              reason: 'at ${elapsed.inMilliseconds} ms',
            );
          }
          // Still turning 1 ms before KitMotion.standard, settled just past
          // it (an animation is done once its time EXCEEDS its duration).
          await tester.pump(
            KitMotion.standard - elapsed - const Duration(milliseconds: 1),
          );
          expect(tester.hasRunningAnimations, isTrue);
          await tester.pump(const Duration(milliseconds: 2));
          expect(tester.hasRunningAnimations, isFalse);
          // Pointing up: the painted glyph is turned half a turn, the same
          // way in both reading directions (clockwise, never mirrored).
          expect(_paintedTurns(tester, _chevronFinder), closeTo(.5, 1e-9));
        },
      );
    }

    testWidgets('pumpAndSettle completes (nothing loops)', (tester) async {
      await tester.pumpWidget(_harness(const KitSpin(turns: 0, child: _star)));
      await tester.pumpWidget(
        _harness(const KitSpin(turns: .75, child: _star)),
      );
      await tester.pumpAndSettle();
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('KitAnimatedBox', () {
    testWidgets(
      'a level change animates only the paint (RenderBox size is constant)',
      (tester) async {
        Widget build(KitSurfaceLevel level) => KitAnimatedBox(
          level: level,
          child: const SizedBox(width: 120, height: 60),
        );
        await tester.pumpWidget(_harness(build(KitSurfaceLevel.surface1)));
        await tester.pumpAndSettle();
        final before = tester.getSize(find.byType(KitAnimatedBox));

        await tester.pumpWidget(_harness(build(KitSurfaceLevel.surface3)));
        await tester.pump(const Duration(milliseconds: 50));
        expect(tester.hasRunningAnimations, isTrue);
        expect(tester.getSize(find.byType(KitAnimatedBox)), before);

        await tester.pumpAndSettle();
        expect(tester.getSize(find.byType(KitAnimatedBox)), before);
      },
    );

    testWidgets('a child size change snaps in one frame', (tester) async {
      await tester.pumpWidget(
        _harness(const KitAnimatedBox(child: SizedBox(width: 80, height: 40))),
      );
      await tester.pumpAndSettle();
      await tester.pumpWidget(
        _harness(const KitAnimatedBox(child: SizedBox(width: 160, height: 90))),
      );
      await tester.pump();
      expect(tester.getSize(find.byType(KitAnimatedBox)), const Size(160, 90));
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('outlined is 1 / dpr thick', (tester) async {
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        _harness(
          const KitAnimatedBox(
            outlined: true,
            child: SizedBox(width: 40, height: 40),
          ),
        ),
      );
      final hairline = KitTokens.of(
        tester.element(find.byType(KitAnimatedBox)),
      ).roles.hairline;
      // The edge is painted as a ring whose outer and inner edges are one
      // physical pixel (half a logical pixel at DPR 2) apart, in hairline.
      expect(
        find.byType(KitAnimatedBox),
        paints..something((method, args) {
          if (method != #drawDRRect) return false;
          final outer = args[0] as RRect;
          final inner = args[1] as RRect;
          final paint = args[2] as Paint;
          return paint.color.toARGB32() == hairline.toARGB32() &&
              inner.left - outer.left == .5 &&
              outer.right - inner.right == .5 &&
              inner.top - outer.top == .5 &&
              outer.bottom - inner.bottom == .5;
        }),
      );
    });

    testWidgets(
      'turning outlined on and off animates only the paint (RenderBox size '
      'and child offset are constant)',
      (tester) async {
        tester.view.devicePixelRatio = 3;
        addTearDown(tester.view.reset);
        const child = SizedBox(key: ValueKey('inside'), width: 96, height: 56);
        Widget build(bool outlined) => KitAnimatedBox(
          level: KitSurfaceLevel.surface1,
          outlined: outlined,
          child: child,
        );
        final box = find.byType(KitAnimatedBox);
        final inside = find.byKey(const ValueKey('inside'));
        await tester.pumpWidget(_harness(build(false)));
        await tester.pumpAndSettle();
        final size = tester.getSize(box);
        final offset = tester.getTopLeft(inside) - tester.getTopLeft(box);
        expect(size, const Size(96, 56));

        for (final outlined in [true, false]) {
          await tester.pumpWidget(_harness(build(outlined)));
          for (var i = 0; i < 4; i++) {
            await tester.pump(const Duration(milliseconds: 30));
            expect(tester.getSize(box), size);
            expect(tester.getTopLeft(inside) - tester.getTopLeft(box), offset);
          }
          await tester.pumpAndSettle();
          expect(tester.getSize(box), size);
          expect(tester.getTopLeft(inside) - tester.getTopLeft(box), offset);
        }
      },
    );

    testWidgets('pumpAndSettle completes (nothing loops)', (tester) async {
      await tester.pumpWidget(
        _harness(
          const KitAnimatedBox(
            level: KitSurfaceLevel.surface1,
            child: SizedBox(width: 20, height: 20),
          ),
        ),
      );
      await tester.pumpWidget(
        _harness(
          const KitAnimatedBox(
            level: KitSurfaceLevel.surface3,
            outlined: true,
            child: SizedBox(width: 20, height: 20),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('KitDim', () {
    testWidgets('over a KitText child fails its debug assert', (tester) async {
      await tester.pumpWidget(
        _harness(
          const KitDim(child: KitText('secret', role: KitTextRole.body)),
        ),
      );
      final error = tester.takeException();
      expect(
        error,
        isNotNull,
        reason: 'KitDim over text must assert (LOOK-14)',
      );
    });

    testWidgets('over an image it paints at disabledAlpha or staleAlpha', (
      tester,
    ) async {
      await tester.pumpWidget(_harness(const KitDim(child: _mark)));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(_paintedOpacity(tester, _markFinder), KitTokens.disabledAlpha);

      await tester.pumpWidget(
        _harness(const KitDim(level: KitDimLevel.stale, child: _mark)),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(_paintedOpacity(tester, _markFinder), KitTokens.staleAlpha);

      await tester.pumpWidget(
        _harness(const KitDim(dimmed: false, child: _mark)),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(_paintedOpacity(tester, _markFinder), 1);
    });

    testWidgets('pumpAndSettle completes (nothing loops)', (tester) async {
      await tester.pumpWidget(
        _harness(const KitDim(dimmed: false, child: _mark)),
      );
      await tester.pumpWidget(_harness(const KitDim(child: _mark)));
      await tester.pumpAndSettle();
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('KitAnimatedValue', () {
    testWidgets('0.2 to 0.8 eases over standard', (tester) async {
      double? seen;
      Widget build(double value) => KitAnimatedValue(
        value: value,
        builder: (context, v) {
          seen = v;
          return const SizedBox.shrink();
        },
      );
      await tester.pumpWidget(_harness(build(.2)));
      await tester.pumpAndSettle();
      expect(seen, .2);

      await tester.pumpWidget(_harness(build(.8)));
      await tester.pump(const Duration(milliseconds: 125));
      expect(tester.hasRunningAnimations, isTrue);
      expect(seen, greaterThan(.2));
      expect(seen, lessThan(.8));

      await tester.pumpAndSettle();
      expect(seen, .8);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('jump: true shows the new value at once', (tester) async {
      double? seen;
      Widget build(double value, {bool jump = false}) => KitAnimatedValue(
        value: value,
        jump: jump,
        builder: (context, v) {
          seen = v;
          return const SizedBox.shrink();
        },
      );
      await tester.pumpWidget(_harness(build(.2)));
      await tester.pumpAndSettle();
      await tester.pumpWidget(_harness(build(.8, jump: true)));
      await tester.pump();
      expect(seen, .8);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('pumpAndSettle completes (nothing loops)', (tester) async {
      Widget build(double value) => KitAnimatedValue(
        value: value,
        builder: (context, v) => const SizedBox.shrink(),
      );
      await tester.pumpWidget(_harness(build(0)));
      await tester.pumpWidget(_harness(build(1)));
      await tester.pumpAndSettle();
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('leaving parts start at once (KitMotion.exit in a reverse slot)', () {
    Widget reveal(bool shown) => _harness(
      Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          KitReveal(
            child: shown
                ? const SizedBox(height: 100, child: Text('Part'))
                : null,
          ),
        ],
      ),
    );

    testWidgets('a KitReveal part starts folding away at once', (tester) async {
      await tester.pumpWidget(reveal(true));
      await tester.pumpWidget(reveal(false));
      await tester.pump(const Duration(milliseconds: 50)); // 20 % of 250 ms
      final h = tester.getSize(find.byType(KitReveal)).height;
      expect(h, lessThan(67), reason: 'a third of it gone in the first fifth');
      await tester.pumpAndSettle();
      expect(find.text('Part'), findsNothing);
    });

    testWidgets('a KitReveal part is gone 150 ms after it is removed', (
      tester,
    ) async {
      await tester.pumpWidget(reveal(true));
      await tester.pumpWidget(reveal(false));
      await tester.pump(const Duration(milliseconds: 151));
      await tester.pump();
      expect(find.text('Part'), findsNothing);
    });

    testWidgets('a KitSwap cross-fade lets the old content go at once', (
      tester,
    ) async {
      Widget swap(String word) =>
          _harness(KitSwap(child: Text(word, key: ValueKey(word))));
      await tester.pumpWidget(swap('Old'));
      await tester.pumpWidget(swap('New'));
      await tester.pump(const Duration(milliseconds: 30)); // 20 % of 150 ms
      expect(_paintedOpacity(tester, find.text('Old')), lessThan(.7));
      await tester.pumpAndSettle();
    });
  });

  testWidgets('KitEntrance under Calm fades in place', (tester) async {
    Widget entrance(int trigger) => _harness(
      KitEntrance(trigger: trigger, child: const Text('Arrives')),
      motion: KitMotionLevel.calm,
    );
    await tester.pumpWidget(entrance(1));
    await tester.pumpAndSettle();
    final place = tester.getTopLeft(find.text('Arrives'));
    await tester.pumpWidget(entrance(2));
    await tester.pump(const Duration(milliseconds: 60));
    expect(_paintedOpacity(tester, find.text('Arrives')), lessThan(1));
    expect(tester.getTopLeft(find.text('Arrives')), place);
    await tester.pumpAndSettle();
  });

  test(
    'no Duration( literal and no Curves. in kit_motion_parts.dart (MOT-1)',
    () {
      final source = File(
        'lib/ui/kit/motion/kit_motion_parts.dart',
      ).readAsStringSync();
      expect(source.contains('Duration('), isFalse);
      expect(source.contains('Curves.'), isFalse);
    },
  );
}
