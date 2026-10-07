// KitKeyValue (label and value rows): its behaviour, and the still-motion
// registration G8 requires of every kit part.
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
  home: Scaffold(body: SingleChildScrollView(child: child)),
);

const _rows = [
  KitKeyValueRow(label: 'Duration', value: '4 min 12 s'),
  KitKeyValueRow(label: 'Files changed', value: '7'),
  KitKeyValueRow(
    label: 'Branch',
    value:
        'feature/a-very-long-branch-name-that-keeps-going-and-must-wrap-not-cut',
  ),
];

void main() {
  kitMotionStillTests(
    'KitKeyValue',
    builds: {'default': () => const KitKeyValue(rows: _rows)},
  );

  testWidgets('shows every label and value', (tester) async {
    await tester.pumpWidget(_host(const KitKeyValue(rows: _rows)));
    for (final row in _rows) {
      expect(find.text(row.label), findsOneWidget);
      expect(find.text(row.value), findsOneWidget);
    }
  });

  testWidgets('values use tabular figures', (tester) async {
    await tester.pumpWidget(_host(const KitKeyValue(rows: _rows)));
    final style = tester.widget<Text>(find.text('7')).style!;
    expect(style.fontFeatures, contains(const FontFeature.tabularFigures()));
  });

  testWidgets('a long value wraps inside a narrow window', (tester) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(_host(const KitKeyValue(rows: _rows)));
    expect(tester.takeException(), isNull);
    final box = tester.getSize(find.textContaining('feature/a-very-long'));
    expect(box.width, lessThanOrEqualTo(360));
    expect(box.height, greaterThan(30));
  });

  testWidgets('2.0 text stacks each value under its label', (tester) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _host(const KitKeyValue(rows: _rows), textScale: 2),
    );
    expect(tester.takeException(), isNull);
    expect(
      tester.getTopLeft(find.text('4 min 12 s')).dy,
      greaterThan(tester.getBottomLeft(find.text('Duration')).dy - 1),
    );
  });

  testWidgets('reads as one phrase per row', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_host(const KitKeyValue(rows: _rows)));
    expect(
      find.bySemanticsLabel(RegExp('Duration.*4 min 12 s', dotAll: true)),
      findsOneWidget,
    );
    handle.dispose();
  });

  testWidgets('no rows draws nothing', (tester) async {
    await tester.pumpWidget(_host(const KitKeyValue(rows: [])));
    expect(find.byType(KitText), findsNothing);
  });
}
