// KitVitalsStrip (lib/ui/kit/kit_vitals_strip.dart): the readings and the
// sentence, at most four cells, loading dashes, the 1.3× cap on frame text
// (the sentence follows the system size), two-by-two at large text, the
// reading width cap, one semantics label per cell, and the tappable line.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

KitVital _v(String value, String word) =>
    KitVital(value: value, word: word, semanticsLabel: '$word: $value');

final _four = [
  _v('Cool', 'Phone heat'),
  _v('2.1 GB', 'Memory free'),
  _v('12%', 'Linux CPU'),
  _v('34 °C', 'Battery'),
];

Future<void> _pump(
  WidgetTester tester,
  Widget strip, {
  double textScale = 1,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      home: MediaQuery(
        data: MediaQueryData(
          size: size,
          textScaler: TextScaler.linear(textScale),
        ),
        child: Scaffold(body: ListView(children: [strip])),
      ),
    ),
  );
  await tester.pump(const Duration(milliseconds: 300));
}

double _fontSize(WidgetTester tester, String text) {
  final rich = tester.widget<RichText>(
    find.descendant(of: find.text(text), matching: find.byType(RichText)).first,
  );
  return rich.textScaler.scale(rich.text.style?.fontSize ?? 14);
}

void main() {
  testWidgets('values over words, one sentence, at most four cells', (
    tester,
  ) async {
    await _pump(
      tester,
      KitVitalsStrip(vitals: _four, summary: 'Linux uses 1.4 GB of memory.'),
    );
    for (final text in ['Cool', 'Phone heat', '2.1 GB', '12%', '34 °C']) {
      expect(find.text(text), findsOneWidget, reason: text);
    }
    expect(
      () => KitVitalsStrip(vitals: [..._four, _v('9', 'Fifth')]),
      throwsAssertionError,
    );
    expect(find.text('Linux uses 1.4 GB of memory.'), findsOneWidget);
    // One row of four on a phone at normal text.
    expect(
      tester.getTopLeft(find.text('Cool')).dy,
      tester.getTopLeft(find.text('34 °C')).dy,
    );
  });

  testWidgets('loading shows dashes with their words', (tester) async {
    await _pump(
      tester,
      KitVitalsStrip(
        loading: true,
        vitals: [_v('', 'Phone heat'), _v('', 'Memory free')],
      ),
    );
    expect(find.text('—'), findsNWidgets(2));
    expect(find.text('Memory free'), findsOneWidget);
  });

  testWidgets('frame text stops at 1.3x; the sentence does not', (
    tester,
  ) async {
    await _pump(tester, KitVitalsStrip(vitals: _four, summary: 'A sentence.'));
    final word = _fontSize(tester, 'Phone heat');
    final sentence = _fontSize(tester, 'A sentence.');
    await _pump(
      tester,
      KitVitalsStrip(vitals: _four, summary: 'A sentence.'),
      textScale: 3,
    );
    expect(
      _fontSize(tester, 'Phone heat'),
      moreOrLessEquals(word * KitVitalsStrip.frameTextCap, epsilon: 0.01),
    );
    expect(_fontSize(tester, 'A sentence.'), greaterThan(sentence * 2.5));
    expect(tester.takeException(), isNull);
  });

  testWidgets('large text or a narrow screen: two by two', (tester) async {
    await _pump(tester, KitVitalsStrip(vitals: _four), textScale: 2);
    expect(
      tester.getTopLeft(find.text('Cool')).dy,
      tester.getTopLeft(find.text('2.1 GB')).dy,
    );
    expect(
      tester.getTopLeft(find.text('12%')).dy,
      greaterThan(tester.getTopLeft(find.text('Cool')).dy),
    );
    await _pump(
      tester,
      KitVitalsStrip(vitals: _four),
      size: const Size(320, 700),
    );
    expect(
      tester.getTopLeft(find.text('12%')).dy,
      greaterThan(tester.getTopLeft(find.text('Cool')).dy),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('never wider than the reading cap', (tester) async {
    await _pump(
      tester,
      KitVitalsStrip(vitals: _four, stripKey: const ValueKey('strip')),
      size: const Size(1600, 900),
    );
    expect(
      tester.getSize(find.byKey(const ValueKey('strip'))).width,
      lessThanOrEqualTo(KitLayout.readingWidth),
    );
  });

  testWidgets('one label per cell; a tappable sentence is a button', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    var taps = 0;
    await _pump(
      tester,
      KitVitalsStrip(
        vitals: _four,
        summary: 'Hot. node is using 180% CPU.',
        summaryLabel: 'Open Running now',
        onSummaryTap: () => taps++,
      ),
    );
    expect(find.bySemanticsLabel('Phone heat: Cool'), findsOneWidget);
    expect(find.bySemanticsLabel('Memory free: 2.1 GB'), findsOneWidget);
    await tester.tap(find.text('Hot. node is using 180% CPU.'));
    await tester.pump(const Duration(milliseconds: 300));
    expect(taps, 1);
    semantics.dispose();
  });
}
