// KitChart (bar and line charts): summary, edge data, legend, stillness.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Widget _host(
  Widget child, {
  double textScale = 1,
  bool still = false,
  TextDirection direction = TextDirection.ltr,
}) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, app) => Directionality(
    textDirection: direction,
    child: MediaQuery(
      data: MediaQuery.of(context).copyWith(
        textScaler: TextScaler.linear(textScale),
        disableAnimations: still,
      ),
      child: app!,
    ),
  ),
  home: Scaffold(body: ListView(children: [child])),
);

final _months = [for (var i = 1; i <= 12; i++) 'M$i'];

KitChart _three(KitChartKind kind) => KitChart(
  kind: kind,
  unit: 'ms',
  labels: _months,
  series: [
    KitChartSeries(name: 'API', values: [for (var i = 0; i < 12; i++) i * 3.0]),
    KitChartSeries(name: 'DB', values: [for (var i = 0; i < 12; i++) 40.0 - i]),
    KitChartSeries(name: 'UI', values: [for (var i = 0; i < 12; i++) 10.0]),
  ],
);

void main() {
  kitMotionStillTests(
    'KitChart',
    builds: {
      'bar': () => _three(KitChartKind.bar),
      'line': () => _three(KitChartKind.line),
      'zero': () => const KitChart(
        kind: KitChartKind.bar,
        labels: ['a', 'b'],
        series: [
          KitChartSeries(name: 'x', values: [0, 0]),
        ],
      ),
    },
  );

  test('the summary names kind, series, points and the highest value', () {
    expect(
      KitChart.summaryOf(
        kind: KitChartKind.bar,
        labels: _months,
        series: _three(KitChartKind.bar).series,
        unit: 'ms',
      ),
      'Bar chart, 3 series, 12 points; highest DB, M1: 40 ms',
    );
  });

  test('a single series leaves the series name out of "highest"', () {
    expect(
      KitChart.summaryOf(
        kind: KitChartKind.line,
        labels: const ['Mon', 'Tue'],
        series: const [
          KitChartSeries(name: 'Hits', values: [1.5, 2.25]),
        ],
      ),
      'Line chart, 1 series, 2 points; highest Tue: 2.25',
    );
  });

  testWidgets('semantics carries the summary', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_host(_three(KitChartKind.bar), still: true));
    await tester.pump();
    expect(
      find.bySemanticsLabel(
        'Bar chart, 3 series, 12 points; highest DB, M1: 40 ms',
      ),
      findsOneWidget,
    );
    handle.dispose();
  });

  testWidgets('a legend appears only with several series', (tester) async {
    await tester.pumpWidget(_host(_three(KitChartKind.line), still: true));
    expect(find.text('API'), findsOneWidget);
    expect(find.text('DB'), findsOneWidget);
    await tester.pumpWidget(
      _host(
        const KitChart(
          kind: KitChartKind.line,
          labels: ['a'],
          series: [
            KitChartSeries(name: 'Only', values: [1]),
          ],
        ),
        still: true,
      ),
    );
    expect(find.text('Only'), findsNothing);
  });

  testWidgets('the unit shows above the plot', (tester) async {
    await tester.pumpWidget(_host(_three(KitChartKind.bar), still: true));
    expect(find.text('ms'), findsOneWidget);
  });

  testWidgets('edge data draws without an error', (tester) async {
    const cases = <(KitChartKind, List<double>)>[
      (KitChartKind.bar, [0, 0, 0]),
      (KitChartKind.line, [0, 0, 0]),
      (KitChartKind.bar, [-3, -1, -7]),
      (KitChartKind.line, [-3, 4, -7]),
      (KitChartKind.line, [5]),
      (KitChartKind.bar, [5]),
      (KitChartKind.bar, [0.001, 0.002]),
      (KitChartKind.line, [1e9, 2e9]),
    ];
    for (final (kind, values) in cases) {
      await tester.pumpWidget(
        _host(
          KitChart(
            kind: kind,
            labels: const ['a', 'b', 'c'],
            series: [KitChartSeries(name: 's', values: values)],
          ),
          still: true,
        ),
      );
      expect(tester.takeException(), isNull, reason: '$kind $values');
    }
    await tester.pumpWidget(
      _host(
        const KitChart(kind: KitChartKind.bar, labels: [], series: []),
        still: true,
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('30 points and 2.0 text, RTL, no overflow at 360 px', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      _host(
        KitChart(
          kind: KitChartKind.line,
          unit: 'files',
          labels: [for (var i = 0; i < 30; i++) 'Day $i'],
          series: [
            for (final n in ['One', 'Two', 'Three'])
              KitChartSeries(
                name: n,
                values: [for (var i = 0; i < 30; i++) i * 1.0],
              ),
          ],
        ),
        textScale: 2,
        direction: TextDirection.rtl,
        still: true,
      ),
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('animates in unless motion is reduced', (tester) async {
    await tester.pumpWidget(_host(_three(KitChartKind.bar)));
    expect(tester.hasRunningAnimations, isTrue);
    await tester.pumpAndSettle();
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpWidget(_host(_three(KitChartKind.bar), still: true));
    expect(tester.hasRunningAnimations, isFalse);
  });
}
