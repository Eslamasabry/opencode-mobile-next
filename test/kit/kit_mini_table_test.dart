// KitMiniTable (a header and rows that scroll sideways inside their panel):
// its behaviour, and the still-motion registration G8 requires.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(Widget child, {double textScale = 1}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: app!,
  ),
  home: Scaffold(body: ListView(children: [child])),
);

KitMiniTable _small() => const KitMiniTable(
  columns: ['File', 'Added', 'Removed'],
  rows: [
    KitMiniTableRow(['lib/main.dart', '12', '3']),
    KitMiniTableRow(['lib/ui/kit/kit.dart', '5', '0']),
  ],
);

KitMiniTable _wide({int rows = 20}) => KitMiniTable(
  columns: [for (var c = 0; c < 6; c++) 'Column number $c'],
  rows: [
    for (var r = 0; r < rows; r++)
      KitMiniTableRow([
        for (var c = 0; c < 6; c++) 'Row $r cell $c with some words',
      ]),
  ],
);

void main() {
  kitMotionStillTests(
    'KitMiniTable',
    builds: {'default': _small, 'wide': () => _wide(rows: 3)},
  );

  testWidgets('shows the header and every cell', (tester) async {
    await tester.pumpWidget(_host(_small()));
    for (final text in ['File', 'Added', 'Removed', 'lib/main.dart', '12']) {
      expect(find.text(text), findsOneWidget);
    }
  });

  testWidgets('numeric columns align to the end', (tester) async {
    await tester.pumpWidget(_host(_small()));
    final cell = tester.widget<Text>(find.text('12'));
    expect(cell.textAlign, TextAlign.end);
    final name = tester.widget<Text>(find.text('lib/main.dart'));
    expect(name.textAlign, TextAlign.start);
  });

  testWidgets('a wide table scrolls inside itself at 360 px, no overflow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_host(_wide()));
    expect(tester.takeException(), isNull);
    expect(tester.getSize(find.byType(KitMiniTable)).width, 360);
    final before = tester.getTopLeft(find.text('Column number 0')).dx;
    await tester.dragFrom(
      tester.getTopLeft(find.text('Column number 0')) + const Offset(8, 8),
      const Offset(-200, 0),
    );
    await tester.pump();
    expect(tester.takeException(), isNull);
    expect(
      tester.getTopLeft(find.text('Column number 0')).dx,
      lessThan(before),
    );
    expect(tester.getSize(find.byType(KitMiniTable)).width, 360);
  });

  testWidgets('2.0 text does not overflow at 360 px', (tester) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_host(_small(), textScale: 2));
    expect(tester.takeException(), isNull);
  });

  testWidgets('a short row is padded, not a crash', (tester) async {
    await tester.pumpWidget(
      _host(
        const KitMiniTable(
          columns: ['A', 'B'],
          rows: [
            KitMiniTableRow(['only one']),
          ],
        ),
      ),
    );
    expect(tester.takeException(), isNull);
  });
}
