// KitComposerStatusStrip: the chips above the composer's field never lie on
// one another. A pause chip beside a long model label (the cold open of a
// Claude Code chat: "Auto-approve paused" and "Loading conversation
// selection…") used to scroll under the model chip and be cut off.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _paused = 'Auto-approve paused';
const _loading = 'Loading conversation selection…';

Future<void> _pump(
  WidgetTester tester, {
  required double width,
  double textScale = 1,
  TextDirection direction = TextDirection.ltr,
  List<Widget>? chips,
  String modelLabel = _loading,
}) async {
  tester.view.physicalSize = Size(width, 800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, app) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: Directionality(textDirection: direction, child: app!),
      ),
      home: Scaffold(
        body: Align(
          alignment: Alignment.topCenter,
          child: KitComposerStatusStrip(
            stripKey: const Key('strip'),
            chips:
                chips ??
                const [KitChip(icon: AppIconography.pause, label: _paused)],
            model: KitComposerChips.model(label: modelLabel, onPressed: () {}),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  for (final direction in TextDirection.values) {
    testWidgets('a lone model stays at the trailing edge · ${direction.name}', (
      tester,
    ) async {
      await _pump(
        tester,
        width: 412,
        direction: direction,
        chips: const [],
        modelLabel: 'Sonnet',
      );
      final model = tester.getRect(find.byType(KitComposerChips));
      final trailingGap = direction == TextDirection.ltr
          ? 412 - model.right
          : model.left;
      expect(trailingGap, lessThanOrEqualTo(16));
      expect(tester.takeException(), isNull);
    });
    for (final (width, scale) in [
      (412.0, 1.0),
      (360.0, 1.0),
      (320.0, 1.0),
      (320.0, 2.0),
    ]) {
      testWidgets('chips never overlap · ${width.toInt()} dp · '
          '$scale x text · ${direction.name}', (tester) async {
        await _pump(
          tester,
          width: width,
          textScale: scale,
          direction: direction,
        );
        expect(tester.takeException(), isNull);
        final chip = tester.getRect(find.byType(KitChip).first);
        final model = tester.getRect(find.byType(KitComposerChips));
        expect(
          chip.overlaps(model),
          isFalse,
          reason: 'chip $chip lies under the model chip $model',
        );
        // The left chip is whole: its box is not wider than the line, and
        // its words are all there to read.
        expect(find.text(_paused), findsOneWidget);
        expect(chip.left, greaterThanOrEqualTo(0));
        expect(chip.right, lessThanOrEqualTo(width));
        expect(model.right, lessThanOrEqualTo(width));
        expect(model.left, greaterThanOrEqualTo(0));
      });
    }
  }
}
