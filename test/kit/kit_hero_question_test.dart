// KitHeroQuestion (docs/ux-system/kit-api/KitHeroQuestion.md): the one big
// question a first page opens on.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

const _question = 'Where should your agent run?';

Future<void> _pump(
  WidgetTester tester, {
  required Size size,
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: Directionality(textDirection: direction, child: widget!),
      ),
      home: const Scaffold(
        body: Padding(
          padding: EdgeInsets.all(16),
          child: KitHeroQuestion(_question),
        ),
      ),
    ),
  );
}

double _size(WidgetTester tester) =>
    tester.widget<Text>(find.text(_question)).style!.fontSize!;

void main() {
  kitMotionStillTests(
    'KitHeroQuestion',
    builds: {'default': () => const KitHeroQuestion(_question)},
  );

  testWidgets('is the page heading, in display type', (tester) async {
    await _pump(tester, size: const Size(412, 915));
    expect(
      tester.getSemantics(find.text(_question)),
      isSemantics(isHeader: true, label: _question),
    );
    // Solid: 40 at 500, hierarchy from size alone (unslop, 03e88e386).
    expect(_size(tester), 40);
    expect(
      tester.widget<Text>(find.text(_question)).style!.fontWeight,
      FontWeight.w500,
    );
  });

  testWidgets('a narrow phone steps down a size', (tester) async {
    await _pump(tester, size: const Size(320, 640));
    expect(_size(tester), 32);
  });

  for (final direction in TextDirection.values) {
    testWidgets('200 % text grows it only a little and wraps ($direction)', (
      tester,
    ) async {
      await _pump(
        tester,
        size: const Size(320, 640),
        textScale: 2,
        direction: direction,
      );
      expect(tester.takeException(), isNull);
      final scaler = MediaQuery.textScalerOf(
        tester.element(find.text(_question)),
      );
      expect(scaler.scale(32), closeTo(36.8, .01));
      final rect = tester.getRect(find.text(_question));
      expect(rect.left, greaterThanOrEqualTo(0));
      expect(rect.right, lessThanOrEqualTo(320));
    });
  }
}
