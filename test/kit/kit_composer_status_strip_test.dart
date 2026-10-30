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
  KitComposerStop? stop,
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
            stop: stop,
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
        // The chips scroll inside their own viewport; the model chip is
        // outside it, so the two never lie on one another.
        final line = tester.getRect(find.byKey(const Key('strip')));
        final model = tester.getRect(find.byType(KitComposerChips));
        expect(
          (line.center.dy - model.center.dy).abs(),
          lessThan(1),
          reason: 'chips $line and model chip $model are on two rows',
        );
        final start = direction == TextDirection.ltr;
        expect(
          start ? line.right <= model.left : line.left >= model.right,
          isTrue,
          reason: 'chips $line lie under the model chip $model',
        );
        expect(find.text(_paused), findsOneWidget);
      });
    }
  }

  for (final direction in TextDirection.values) {
    testWidgets('a long model name ends in an ellipsis, never cut at the '
        'screen edge · ${direction.name}', (tester) async {
      const long = 'Kilo Gateway/Google: Nano Banana Pro (Gemini 3 Pro Image)';
      await _pump(
        tester,
        width: 412,
        direction: direction,
        modelLabel: long,
        chips: const [
          KitChip(icon: AppIconography.pause, label: 'Ask first'),
          KitChip(icon: AppIconography.pause, label: 'Auto Accept'),
        ],
      );
      final model = tester.getRect(find.byType(KitComposerChips));
      expect(model.left, greaterThanOrEqualTo(0));
      expect(model.right, lessThanOrEqualTo(412));
      final label = tester.widget<Text>(find.text(long));
      expect(label.overflow, TextOverflow.ellipsis);
      expect(tester.takeException(), isNull);
    });

    // Emulator, build 4010: "Ask first" and "Auto Accept" took just over
    // half the line, and Auto Accept was cut mid-word while the model chip
    // had room to give.
    testWidgets('the chips before a long model keep their whole words while '
        'the model has room to give · ${direction.name}', (tester) async {
      const long = 'Kilo Gateway/Google: Nano Banana Pro (Gemini 3 Pro Image)';
      await _pump(
        tester,
        width: 600,
        direction: direction,
        modelLabel: long,
        chips: const [
          KitChip(icon: AppIconography.pause, label: 'Ask first'),
          KitChip(icon: AppIconography.pause, label: 'Auto Accept'),
        ],
      );
      final model = tester.getRect(find.byType(KitComposerChips));
      final auto = tester.getRect(find.text('Auto Accept'));
      if (direction == TextDirection.ltr) {
        expect(auto.right, lessThanOrEqualTo(model.left));
      } else {
        expect(auto.left, greaterThanOrEqualTo(model.right));
      }
      expect(model.width, greaterThan(100));
      expect(tester.takeException(), isNull);
    });
  }

  // Stop is the strip's trailing end (owner, 30 Oct 2026): fixed, a full
  // touch target, and the chips and the model chip give their room up first.
  for (final direction in TextDirection.values) {
    for (final (width, scale) in [(412.0, 1.0), (320.0, 1.0), (320.0, 2.0)]) {
      testWidgets('Stop stays whole at the trailing end · ${width.toInt()} dp '
          '· $scale x text · ${direction.name}', (tester) async {
        var stopped = 0;
        await _pump(
          tester,
          width: width,
          textScale: scale,
          direction: direction,
          stop: KitComposerStop(
            chipKey: const Key('stop'),
            label: 'Stop',
            spoken: 'Stop Claude Code',
            onPressed: () => stopped++,
          ),
        );
        expect(tester.takeException(), isNull);
        final stop = tester.getRect(find.byKey(const Key('stop')));
        expect(stop.width, greaterThanOrEqualTo(48));
        expect(stop.height, greaterThanOrEqualTo(48));
        expect(stop.left, greaterThanOrEqualTo(0));
        expect(stop.right, lessThanOrEqualTo(width));
        // The text keeps its whole word.
        expect(find.text('Stop'), findsOneWidget);
        // Beside the model chip, never under it; at the trailing edge.
        final model = tester.getRect(find.byType(KitComposerChips));
        if (direction == TextDirection.ltr) {
          expect(model.right, lessThanOrEqualTo(stop.left));
          expect(width - stop.right, lessThanOrEqualTo(16));
        } else {
          expect(stop.right, lessThanOrEqualTo(model.left));
          expect(stop.left, lessThanOrEqualTo(16));
        }
        await tester.tap(find.byKey(const Key('stop')));
        expect(stopped, 1);
      });
    }
  }

  testWidgets('a stuck Stop is amber, and it fades out with the turn', (
    tester,
  ) async {
    await _pump(
      tester,
      width: 412,
      chips: const [],
      modelLabel: 'Sonnet',
      stop: KitComposerStop(
        chipKey: const Key('stop'),
        label: 'Stop',
        spoken: 'Stop Claude Code',
        stuck: true,
        onPressed: () {},
      ),
    );
    final roles = KitTokens.of(
      tester.element(find.byKey(const Key('stop'))),
    ).roles;
    final label = tester.widget<Text>(find.text('Stop'));
    expect(label.style?.color, isNot(roles.text1));
    await _pump(tester, width: 412, chips: const [], modelLabel: 'Sonnet');
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const Key('stop')), findsNothing);
  });
}
