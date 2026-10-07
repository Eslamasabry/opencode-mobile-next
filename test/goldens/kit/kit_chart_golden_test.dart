// Gallery (gate G4) for KitChart: bar and line charts, at 412x915 and 1280x800, dark and
// light, at 2.0 text, and once right to left.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/kit/kit_chart_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_gallery.dart';

Widget _pad(Widget child) =>
    Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: child);

const _labels = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

final _states = <String, Widget Function()>{
  'default': () => _pad(
    const KitChart(
      kind: KitChartKind.bar,
      unit: 'ms',
      labels: _labels,
      series: [
        KitChartSeries(name: 'API', values: [12, 18, 9, 22, 30, 16]),
        KitChartSeries(name: 'Database', values: [8, 11, 14, 10, 19, 7]),
        KitChartSeries(name: 'Cache', values: [2, 3, 2, 4, 3, 2]),
      ],
    ),
  ),
  'line': () => _pad(
    const KitChart(
      kind: KitChartKind.line,
      unit: 'files',
      labels: _labels,
      series: [
        KitChartSeries(name: 'Added', values: [3, 7, 4, 10, 6, 12]),
        KitChartSeries(name: 'Removed', values: [1, 2, 6, 3, 2, 4]),
      ],
    ),
  ),
  'negative': () => _pad(
    const KitChart(
      kind: KitChartKind.bar,
      unit: '%',
      labels: _labels,
      series: [
        KitChartSeries(name: 'Change', values: [4, -3, 7, -8, 2, -1]),
      ],
    ),
  ),
  'single': () => _pad(
    const KitChart(
      kind: KitChartKind.line,
      labels: ['Today'],
      series: [
        KitChartSeries(name: 'Value', values: [5]),
      ],
    ),
  ),
  'zero': () => _pad(
    const KitChart(
      kind: KitChartKind.bar,
      labels: _labels,
      series: [
        KitChartSeries(name: 'Value', values: [0, 0, 0, 0, 0, 0]),
      ],
    ),
  ),
};

const _phone = Size(412, 915);

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in kitGalleryScaledSizes) {
      testWidgets('default · ${kitGallerySize(size)} · $mode', (tester) async {
        await kitGalleryPart(
          tester,
          name: kitGalleryName('kit_chart_default', size, light: light),
          size: size,
          light: light,
          child: _states['default']!(),
        );
      });
    }

    testWidgets('line · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_chart_line', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['line']!(),
      );
    });

    testWidgets('negative · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_chart_negative', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['negative']!(),
      );
    });

    testWidgets('single · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_chart_single', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['single']!(),
      );
    });

    testWidgets('zero · 412x915 · $mode', (tester) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName('kit_chart_zero', _phone, light: light),
        size: _phone,
        light: light,
        child: _states['zero']!(),
      );
    });
  }

  for (final size in kitGalleryScaledSizes) {
    testWidgets('default · 2.0 text · ${kitGallerySize(size)} · dark', (
      tester,
    ) async {
      await kitGalleryPart(
        tester,
        name: kitGalleryName(
          'kit_chart_default',
          size,
          light: false,
          text2: true,
        ),
        size: size,
        light: false,
        textScale: 2,
        child: _states['default']!(),
      );
    });
  }

  testWidgets('default · right to left · dark', (tester) async {
    await kitGalleryPart(
      tester,
      name: kitGalleryName('kit_chart_default', _phone, light: false, ar: true),
      size: _phone,
      light: false,
      locale: const Locale('ar'),
      child: _states['default']!(),
    );
  });
}
