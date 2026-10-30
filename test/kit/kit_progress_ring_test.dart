// KitProgressRing (docs/ux-system/kit-api/KitProgressRing.md): only real
// figures (a floored measured share, steps done of all, finished), the arc
// that eases forward and lands at once backwards, reduced motion, the
// trail, and its semantics (one ring node, the sentence a live region).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

const _ringKey = Key('ring-under-test');

Widget _app(Widget child, {bool reduce = false, Locale? locale}) => MaterialApp(
  theme: AppTheme.dark(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, widget) => MediaQuery(
    data: MediaQuery.of(context).copyWith(disableAnimations: reduce),
    child: widget!,
  ),
  home: Scaffold(
    body: Center(child: SizedBox(width: 400, child: child)),
  ),
);

KitProgressRing _measured(double value, {String headline = 'Downloading'}) =>
    KitProgressRing.measured(
      value: value,
      caption: '18 of 30 MB',
      headline: headline,
      semanticsLabel: 'Setup progress',
      trail: const [KitTrailMark.done, KitTrailMark.current],
      ringKey: _ringKey,
    );

/// The arc as painted now, read from the ring's painter.
double _arc(WidgetTester tester) {
  final paint = tester.widget<CustomPaint>(
    find.descendant(
      of: find.byType(KitProgressRing),
      matching: find.byWidgetPredicate(
        (w) => w is CustomPaint && w.painter != null,
      ),
    ),
  );
  // The painter is private; its arc is the only double that moves.
  return (paint.painter! as dynamic).arc as double;
}

void main() {
  kitMotionStillTests(
    'KitProgressRing',
    builds: {
      'measured': () => _measured(0.64),
      'steps': () => const KitProgressRing.steps(
        done: 2,
        of: 6,
        headline: 'Starting OpenCode',
        semanticsLabel: 'Setup progress',
      ),
      'finished': () => const KitProgressRing.finished(
        headline: 'All set',
        semanticsLabel: 'Setup progress',
      ),
    },
    changes: {
      'moves on': KitMotionChange(
        build: () => _measured(0.2),
        act: (tester, stage) =>
            stage.rebuild(_measured(0.8, headline: 'Unpacking')),
        shows: 'Unpacking',
      ),
    },
  );

  testWidgets('a measured share is floored and says its amount', (
    tester,
  ) async {
    await tester.pumpWidget(_app(_measured(0.649)));
    expect(find.text('64'), findsNothing); // inside a rich text
    expect(find.textContaining('64'), findsOneWidget);
    expect(find.textContaining('65'), findsNothing);
    expect(find.text('18 of 30 MB'), findsOneWidget);
  });

  testWidgets('steps say done of all, never a percent', (tester) async {
    await tester.pumpWidget(
      _app(
        const KitProgressRing.steps(
          done: 2,
          of: 6,
          headline: 'Installing OpenCode',
          semanticsLabel: 'Setup progress',
        ),
      ),
    );
    expect(find.textContaining('%'), findsNothing);
    expect(find.text('steps done'), findsOneWidget);
    expect(
      const KitProgressRing.steps(
        done: 2,
        of: 6,
        headline: '',
        semanticsLabel: '',
      ).fraction,
      closeTo(1 / 3, 1e-9),
    );
  });

  testWidgets('the arc eases forward and lands at once backwards', (
    tester,
  ) async {
    await tester.pumpWidget(_app(_measured(0.2)));
    expect(_arc(tester), closeTo(0.2, 1e-9));
    await tester.pumpWidget(_app(_measured(0.8)));
    await tester.pump(const Duration(milliseconds: 60));
    expect(_arc(tester), inExclusiveRange(0.2, 0.8));
    await tester.pumpAndSettle();
    expect(_arc(tester), closeTo(0.8, 1e-9));
    // A new stage measuring from zero: a new arc, not a run backwards.
    await tester.pumpWidget(_app(_measured(0.1)));
    expect(_arc(tester), closeTo(0.1, 1e-9));
  });

  testWidgets('reduced motion: every change is instant', (tester) async {
    await tester.pumpWidget(_app(_measured(0.2), reduce: true));
    await tester.pumpWidget(_app(_measured(0.9), reduce: true));
    await tester.pump();
    expect(_arc(tester), closeTo(0.9, 1e-9));
  });

  testWidgets('the ring is one node; the sentence is a live region', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_app(_measured(0.6)));
    expect(
      tester.getSemantics(find.byKey(_ringKey)),
      isSemantics(label: 'Setup progress', value: '60%, 18 of 30 MB'),
    );
    expect(
      tester.getSemantics(find.text('Downloading')),
      isSemantics(label: 'Downloading', isLiveRegion: true),
    );
    semantics.dispose();
  });

  testWidgets('Arabic draws its own percent sign and fits', (tester) async {
    await tester.pumpWidget(_app(_measured(0.6), locale: const Locale('ar')));
    expect(find.textContaining('٪'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
