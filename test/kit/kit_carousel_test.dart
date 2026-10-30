// KitCarousel (docs/ux-system/kit-api/KitCarousel.md): cards to swipe through
// with the next one peeking, where you are shown as dots or a bar, and every
// card its own target.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _carousel({int count = 5, int initial = 0, ValueChanged<int>? onTap}) =>
    KitCarousel(
      semanticsLabel: 'Themes',
      itemCount: count,
      itemHeight: 120,
      initialIndex: initial,
      itemBuilder: (context, i) => KitOptionCard.plain(
        key: ValueKey('card-$i'),
        icon: AppIconography.sparkle,
        label: 'Card $i',
        caption: 'About $i',
        selected: i == initial,
        onPressed: () => onTap?.call(i),
      ),
    );

Widget _app(Widget child, {TextDirection? direction, double scale = 1}) =>
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, app) => Directionality(
        textDirection: direction ?? TextDirection.ltr,
        child: MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: TextScaler.linear(scale)),
          child: app!,
        ),
      ),
      home: Scaffold(
        body: Align(alignment: Alignment.topCenter, child: child),
      ),
    );

void main() {
  kitMotionStillTests(
    'KitCarousel',
    builds: {
      'five cards': () => _carousel(),
      'many cards': () => _carousel(count: 30),
    },
    changes: {
      'fewer cards': KitMotionChange(
        build: () => _carousel(),
        act: (tester, stage) => stage.rebuild(_carousel(count: 4)),
        shows: 'Card 0',
      ),
    },
  );

  testWidgets('shows a card and lets the next one peek in', (tester) async {
    tester.view.physicalSize = const Size(412, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(_carousel()));
    expect(find.text('Card 0'), findsOneWidget);
    final next = tester.getRect(find.byKey(const ValueKey('card-1')));
    expect(next.left, lessThan(412));
    expect(next.right, greaterThan(412), reason: 'the next card is cut off');
  });

  testWidgets('every card is a target; a swipe brings the others', (
    tester,
  ) async {
    final taps = <int>[];
    await tester.pumpWidget(_app(_carousel(onTap: taps.add)));
    await tester.tap(find.byKey(const ValueKey('card-0')));
    expect(taps, [0]);
    await tester.dragUntilVisible(
      find.byKey(const ValueKey('card-4')),
      find.byType(PageView),
      const Offset(-200, 0),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('card-4')));
    expect(taps, [0, 4]);
  });

  testWidgets('opens at the initial card', (tester) async {
    await tester.pumpWidget(_app(_carousel(initial: 3)));
    final rect = tester.getRect(find.byKey(const ValueKey('card-3')));
    expect(rect.left, lessThan(60));
  });

  testWidgets('says which card of how many is in view', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_app(_carousel(initial: 2)));
    final node = find.semantics.byLabel('Themes').evaluate().single;
    expect(node.value, '3 of 5');
    handle.dispose();
  });

  testWidgets('few cards show dots, many show a bar', (tester) async {
    await tester.pumpWidget(_app(_carousel()));
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(_app(_carousel(count: 30)));
    expect(tester.takeException(), isNull);
  });

  testWidgets('right to left starts at the start edge and does not overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_app(_carousel(), direction: TextDirection.rtl));
    final rect = tester.getRect(find.byKey(const ValueKey('card-0')));
    expect(rect.right, greaterThan(300));
    expect(tester.takeException(), isNull);
  });

  testWidgets('grows with the text size without overflow', (tester) async {
    await tester.pumpWidget(_app(_carousel(), scale: 2));
    expect(tester.takeException(), isNull);
  });
}
