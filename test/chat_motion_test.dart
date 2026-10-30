// Chat motion (owner request, 2026-10-27): reply words fade in as they
// arrive, the reply grows smoothly instead of pushing the transcript up at
// once, opened steps fold in and out and new ones arrive softly, the sent
// bubble lifts out of the composer, and a conversation opened from its row
// grows out of it. Each one is instant under reduced motion. The tests
// assert what the person sees (opacity, position, size, what is on screen),
// not how it is built.
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  KitEffects effects = KitEffects.defaults,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    KitEffectsScope(
      effects: effects,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        // Unbounded height, as in the transcript's list.
        home: Scaffold(
          body: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [SizedBox(width: 380, child: child)],
          ),
        ),
      ),
    ),
  );
}

/// Clip rects of [width] anywhere in the painted layer tree.
List<Rect> _clipsOfWidth(WidgetTester tester, double width) {
  final found = <Rect>[];
  void walk(Layer? layer) {
    if (layer is ClipRectLayer && layer.clipRect != null) {
      final r = layer.clipRect!;
      if ((r.width - width).abs() < .5) found.add(r);
    }
    if (layer is ContainerLayer) {
      for (var c = layer.firstChild; c != null; c = c.nextSibling) {
        walk(c);
      }
    }
  }

  walk(tester.binding.renderViews.first.debugLayer);
  return found;
}

const _off = KitEffects(motion: KitMotionLevel.off);
const _calm = KitEffects(motion: KitMotionLevel.calm);

/// The lowest alpha any drawn run of [words] has, over every rich text on
/// screen (1 when they are drawn at full strength).
double _alphaOf(WidgetTester tester, String words) {
  var lowest = 1.0;
  var found = false;
  for (final text in tester.widgetList<RichText>(find.byType(RichText))) {
    text.text.visitChildren((span) {
      if (span is TextSpan) {
        final own = (span.text ?? '').trim();
        if (own.isNotEmpty &&
            (words.contains(own) || own.contains(words.trim()))) {
          found = true;
          final color = span.style?.color;
          if (color != null && color.a < lowest) lowest = color.a;
        }
      }
      return true;
    });
  }
  expect(found, isTrue, reason: '"$words" is drawn');
  return lowest;
}

double _heightOf(WidgetTester tester, Finder finder) =>
    tester.getSize(finder).height;

void main() {
  group('reply words fade in as they arrive', () {
    testWidgets('new words start transparent and reach full strength', (
      tester,
    ) async {
      await _pump(tester, const KitMarkdown('Hello', streaming: true));
      await tester.pumpAndSettle();
      expect(_alphaOf(tester, 'Hello'), 1);

      await _pump(
        tester,
        const KitMarkdown('Hello brave new world', streaming: true),
      );
      expect(
        _alphaOf(tester, ' brave new world'),
        lessThan(.2),
        reason: 'the words that just arrived start almost transparent',
      );
      expect(_alphaOf(tester, 'Hello'), 1, reason: 'older words never fade');
      await tester.pump(const Duration(milliseconds: 120));
      final halfway = _alphaOf(tester, ' brave new world');
      expect(halfway, inExclusiveRange(.2, 1));
      await tester.pumpAndSettle();
      expect(_alphaOf(tester, 'Hello brave new world'), 1);
      expect(tester.binding.hasScheduledFrame, isFalse, reason: 'settled');
    });

    testWidgets('under reduced motion the words are there at once', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitMarkdown('Hello', streaming: true),
        effects: _off,
      );
      await _pump(
        tester,
        const KitMarkdown('Hello brave new world', streaming: true),
        effects: _off,
      );
      expect(_alphaOf(tester, 'Hello brave new world'), 1);
    });

    testWidgets('a finished reply shows what changed at once', (tester) async {
      await _pump(tester, const KitMarkdown('Hello'));
      await _pump(tester, const KitMarkdown('Hello brave new world'));
      expect(_alphaOf(tester, 'Hello brave new world'), 1);
    });

    testWidgets('a reply drawn when the page opens is not faded', (
      tester,
    ) async {
      await _pump(
        tester,
        KitMarkdown('Already ${'written ' * 60}', streaming: true),
      );
      expect(_alphaOf(tester, 'Already'), 1);
    });
  });

  group('the reply grows smoothly', () {
    const lines = 'One\n\nTwo\n\nThree\n\nFour\n\nFive';

    testWidgets('the glide clip leaves room for descenders, then lets go', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitMarkdown('One', streaming: true, selectable: false),
      );
      await tester.pumpAndSettle();
      await _pump(
        tester,
        const KitMarkdown(
          'One\n\nTypography gypsy jumpy',
          streaming: true,
          selectable: false,
        ),
      );
      await tester.pump(const Duration(milliseconds: 60));
      final box = tester.renderObject<RenderBox>(find.byType(KitMarkdown));
      final words = tester.renderObject<RenderBox>(
        find
            .descendant(
              of: find.byType(KitMarkdown),
              matching: find.byType(IgnorePointer),
            )
            .first,
      );
      expect(
        box.size.height,
        lessThan(words.size.height - 8),
        reason: 'gliding',
      );
      // The clip around the words reaches below the box's own height.
      final clips = _clipsOfWidth(tester, box.size.width);
      expect(clips, isNotEmpty);
      expect(
        clips.map((c) => c.height).reduce(math.max),
        greaterThanOrEqualTo(box.size.height + 3),
        reason: 'glyph tails hang below the line box',
      );

      await tester.pumpAndSettle();
      expect(box.size.height, words.size.height);
      expect(
        _clipsOfWidth(
          tester,
          box.size.width,
        ).where((c) => c.height < words.size.height - 1),
        isEmpty,
        reason: 'no clip left once the box has arrived',
      );
    });

    testWidgets('a new line grows the reply over a moment', (tester) async {
      await _pump(
        tester,
        const KitMarkdown('One', streaming: true, selectable: false),
      );
      await tester.pumpAndSettle();
      final before = _heightOf(tester, find.byType(KitMarkdown));

      await _pump(
        tester,
        const KitMarkdown(lines, streaming: true, selectable: false),
      );
      await tester.pump(const Duration(milliseconds: 16));
      final early = _heightOf(tester, find.byType(KitMarkdown));
      await tester.pumpAndSettle();
      final after = _heightOf(tester, find.byType(KitMarkdown));
      expect(after, greaterThan(before + 60));
      expect(early, lessThan(after - 20), reason: 'still growing');
      expect(early, greaterThanOrEqualTo(before));
    });

    testWidgets(
      'a line that lands mid-glide carries on from the height shown',
      (tester) async {
        await _pump(
          tester,
          const KitMarkdown('One', streaming: true, selectable: false),
        );
        await tester.pumpAndSettle();
        final start = _heightOf(tester, find.byType(KitMarkdown));
        await _pump(
          tester,
          const KitMarkdown(
            'One\n\nTwo\n\nThree',
            streaming: true,
            selectable: false,
          ),
        );
        await tester.pump(const Duration(milliseconds: 44));
        final early = _heightOf(tester, find.byType(KitMarkdown));
        await tester.pump(const Duration(milliseconds: 16));
        final mid = _heightOf(tester, find.byType(KitMarkdown));
        expect(mid, greaterThan(start));
        await _pump(
          tester,
          const KitMarkdown(lines, streaming: true, selectable: false),
        );
        final after = _heightOf(tester, find.byType(KitMarkdown));
        expect(
          after,
          closeTo(mid, 1e-6),
          reason: 'carries on from the height shown',
        );
        await tester.pump(const Duration(milliseconds: 16));
        final next = _heightOf(tester, find.byType(KitMarkdown));
        expect(next, greaterThan(after));
        // The frame after the new line moves about as far as the frame
        // before it: no surge (a fresh ease-out restarts at its top speed),
        // and no stall (a spring from rest would drop the speed it had).
        expect(
          (next - after) / (mid - early),
          inInclusiveRange(.9, 1.8),
          reason: 'keeps its speed',
        );
        await tester.pump(const Duration(milliseconds: 300));
        final settled = _heightOf(tester, find.byType(KitMarkdown));
        await tester.pumpAndSettle();
        expect(
          _heightOf(tester, find.byType(KitMarkdown)),
          closeTo(settled, 1),
        );
      },
    );

    testWidgets('under reduced motion it takes its height at once', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitMarkdown('One', streaming: true, selectable: false),
        effects: _off,
      );
      await _pump(
        tester,
        const KitMarkdown(lines, streaming: true, selectable: false),
        effects: _off,
      );
      final now = _heightOf(tester, find.byType(KitMarkdown));
      await tester.pumpAndSettle();
      expect(_heightOf(tester, find.byType(KitMarkdown)), now);
    });
  });

  group('work line steps fold and arrive', () {
    Widget line({required bool expanded, int steps = 3}) => KitWorkLine(
      counts: const KitWorkCounts(ran: 3),
      state: KitWorkState.done,
      expanded: expanded,
      stepsKey: const ValueKey('steps'),
      steps: [
        for (var i = 0; i < steps; i++)
          SizedBox(
            key: ValueKey('step-$i'),
            height: 32,
            child: Text('Step $i'),
          ),
      ],
    );

    testWidgets('a group folds shut smoothly', (tester) async {
      await _pump(tester, line(expanded: true));
      await tester.pumpAndSettle();
      final open = _heightOf(tester, find.byType(KitWorkLine));

      await _pump(tester, line(expanded: false));
      await tester.pump(const Duration(milliseconds: 100));
      final folding = _heightOf(tester, find.byType(KitWorkLine));
      expect(find.text('Step 0'), findsOneWidget, reason: 'still folding');
      await tester.pumpAndSettle();
      final shut = _heightOf(tester, find.byType(KitWorkLine));
      expect(find.byKey(const ValueKey('steps')), findsNothing);
      expect(folding, inExclusiveRange(shut, open));
    });

    testWidgets('under reduced motion it folds at once', (tester) async {
      await _pump(tester, line(expanded: true), effects: _off);
      await _pump(tester, line(expanded: false), effects: _off);
      await tester.pump();
      expect(find.byKey(const ValueKey('steps')), findsNothing);
    });

    testWidgets('a step that arrives while open fades and rises in', (
      tester,
    ) async {
      await _pump(tester, line(expanded: true));
      await tester.pumpAndSettle();
      await _pump(tester, line(expanded: true, steps: 4));
      await tester.pump(const Duration(milliseconds: 16));
      final fade = tester.widget<Opacity>(
        find
            .ancestor(of: find.text('Step 3'), matching: find.byType(Opacity))
            .first,
      );
      expect(fade.opacity, lessThan(.5));
      final settled = tester.getTopLeft(find.text('Step 2')).dy + 32;
      expect(tester.getTopLeft(find.text('Step 3')).dy, greaterThan(settled));
      // The steps already shown stay as they are.
      final older = tester.widget<Opacity>(
        find
            .ancestor(of: find.text('Step 0'), matching: find.byType(Opacity))
            .first,
      );
      expect(older.opacity, 1);
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.text('Step 3')).dy, settled);
    });

    testWidgets('under Calm an arriving step fades in without rising', (
      tester,
    ) async {
      await _pump(tester, line(expanded: true), effects: _calm);
      await tester.pumpAndSettle();
      await _pump(tester, line(expanded: true, steps: 4), effects: _calm);
      await tester.pump(const Duration(milliseconds: 16));
      final fade = tester.widget<Opacity>(
        find
            .ancestor(of: find.text('Step 3'), matching: find.byType(Opacity))
            .first,
      );
      expect(fade.opacity, lessThan(.5), reason: 'still fading in');
      final settled = tester.getTopLeft(find.text('Step 2')).dy + 32;
      expect(tester.getTopLeft(find.text('Step 3')).dy, settled);
      await tester.pumpAndSettle();
    });
  });

  group('the sent bubble lifts out of the composer', () {
    KitMessage prompt({Rect? from}) => KitMessage.prompt(
      bubbleKey: const ValueKey('bubble'),
      body: const KitMarkdown('Fix the login bug', selectable: false),
      liftFrom: from,
    );

    testWidgets('it rises from where it was typed into its place', (
      tester,
    ) async {
      await _pump(tester, prompt());
      final place = tester.getRect(find.byKey(const ValueKey('bubble')));

      await _pump(tester, const SizedBox());
      await _pump(tester, prompt(from: const Rect.fromLTWH(16, 840, 380, 48)));
      await tester.pump(const Duration(milliseconds: 60));
      final rising = tester.getRect(find.byKey(const ValueKey('bubble')));
      expect(rising.center.dy, greaterThan(place.center.dy + 100));
      await tester.pumpAndSettle();
      expect(tester.getRect(find.byKey(const ValueKey('bubble'))), place);
    });

    testWidgets('under Calm it fades in at its place', (tester) async {
      await _pump(tester, prompt(), effects: _calm);
      final place = tester.getRect(find.byKey(const ValueKey('bubble')));
      await _pump(tester, const SizedBox(), effects: _calm);
      await _pump(
        tester,
        prompt(from: const Rect.fromLTWH(16, 840, 380, 48)),
        effects: _calm,
      );
      await tester.pump(const Duration(milliseconds: 60));
      expect(tester.getRect(find.byKey(const ValueKey('bubble'))), place);
      expect(
        tester
            .widgetList<Opacity>(
              find.ancestor(
                of: find.byKey(const ValueKey('bubble')),
                matching: find.byType(Opacity),
              ),
            )
            .any((opacity) => opacity.opacity < 1),
        isTrue,
        reason: 'still fading in',
      );
      await tester.pumpAndSettle();
    });

    testWidgets('under reduced motion it is simply there', (tester) async {
      await _pump(tester, prompt(), effects: _off);
      final place = tester.getRect(find.byKey(const ValueKey('bubble')));
      await _pump(tester, const SizedBox(), effects: _off);
      await _pump(
        tester,
        prompt(from: const Rect.fromLTWH(16, 840, 380, 48)),
        effects: _off,
      );
      expect(tester.getRect(find.byKey(const ValueKey('bubble'))), place);
      expect(
        tester
            .widgetList<Opacity>(
              find.ancestor(
                of: find.byKey(const ValueKey('bubble')),
                matching: find.byType(Opacity),
              ),
            )
            .where((opacity) => opacity.opacity < 1),
        isEmpty,
        reason: 'drawn at once',
      );
    });
  });

  group('a conversation grows out of its row', () {
    Future<BuildContext> host(WidgetTester tester, {bool reduced = false}) =>
        _host(tester, reduced: reduced);

    testWidgets('opening grows from the row and Back shrinks into it', (
      tester,
    ) async {
      final row = await host(tester);
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(_growing(), findsOneWidget);
      // The page underneath stays where it is.
      expect(tester.getTopLeft(find.text('Row')), const Offset(0, 300));
      await tester.pumpAndSettle();
      expect(find.text('Chat'), findsOneWidget);

      Navigator.of(tester.element(find.text('Chat'))).pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(_growing(), findsOneWidget, reason: 'shrinks back in');
      await tester.pumpAndSettle();
      expect(find.text('Chat'), findsNothing);

      // The next page, opened any other way, does not grow.
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Other')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(_growing(), findsNothing);
      await tester.pumpAndSettle();
    });

    testWidgets('the back gesture keeps the predictive preview', (
      tester,
    ) async {
      final row = await host(tester);
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pumpAndSettle();

      Future<void> send(String method, [Object? arguments]) =>
          tester.binding.defaultBinaryMessenger.handlePlatformMessage(
            SystemChannels.backGesture.name,
            SystemChannels.backGesture.codec.encodeMethodCall(
              MethodCall(method, arguments),
            ),
            (_) {},
          );
      Map<String, Object> event(double progress) => {
        'touchOffset': [5.0, 300.0],
        'progress': progress,
        'swipeEdge': 0,
      };
      await send('startBackGesture', event(0));
      await send('updateBackGestureProgress', event(.4));
      await tester.pump();
      expect(find.text('Row'), findsOneWidget, reason: 'previewed under');
      expect(_growing(), findsNothing, reason: "Android's own preview");
      expect(
        find.ancestor(
          of: find.text('Chat'),
          matching: find.byWidgetPredicate(
            (w) =>
                '${w.runtimeType}' ==
                '_PredictiveBackSharedElementPageTransition',
          ),
        ),
        findsOneWidget,
      );
      await send('commitBackGesture');
      await tester.pumpAndSettle();
      expect(find.text('Chat'), findsNothing);
    });

    testWidgets('the list underneath dims while the page grows', (
      tester,
    ) async {
      final row = await host(tester);
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(
        tester.renderObject(_growing()),
        paints..something((method, arguments) {
          if (method != #drawRect) return false;
          final paint = arguments[1] as Paint;
          return arguments[0] == const Rect.fromLTWH(0, 0, 412, 915) &&
              paint.color.a > 0 &&
              paint.color.a <= .32 + 1e-6;
        }),
      );
      await tester.pumpAndSettle();
    });

    testWidgets('the page rides the growing box, from the row upward', (
      tester,
    ) async {
      final row = await host(tester);
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      // About 40 % grown: the page's top is between the row and the top.
      final top = tester.getTopLeft(find.text('Chat')).dy;
      expect(top, greaterThan(100));
      expect(top, lessThan(300));
      await tester.pumpAndSettle();
      expect(tester.getTopLeft(find.text('Chat')).dy, lessThan(1));
    });

    testWidgets('Back starts shrinking at once and is done in 300 ms', (
      tester,
    ) async {
      final row = await host(tester);
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pumpAndSettle();
      Navigator.of(tester.element(find.text('Chat'))).pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 30));
      // Mirrored path: already about a tenth of the way to the row.
      expect(tester.getTopLeft(find.text('Chat')).dy, greaterThan(15));
      await tester.pump(const Duration(milliseconds: 271));
      await tester.pump();
      expect(find.text('Chat'), findsNothing);
    });

    testWidgets('a Back during the opening turns back from where the page is', (
      tester,
    ) async {
      final row = await host(tester);
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      final before = tester.getTopLeft(find.text('Chat')).dy;
      Navigator.of(tester.element(find.text('Chat'))).pop();
      await tester.pump();
      expect(
        (tester.getTopLeft(find.text('Chat')).dy - before).abs(),
        lessThan(8),
        reason: 'no jump when the direction flips',
      );
      await tester.pumpAndSettle();
      expect(find.text('Chat'), findsNothing);
    });

    testWidgets('a page opened any other way closes as it opened', (
      tester,
    ) async {
      final row = await host(tester);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Other')));
      await tester.pumpAndSettle();
      Navigator.of(tester.element(find.text('Other'))).pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 320));
      expect(find.text('Other'), findsOneWidget, reason: 'still closing');
      await tester.pumpAndSettle();
      expect(find.text('Other'), findsNothing);
    });

    testWidgets('under Animations: Calm the page still grows', (tester) async {
      final row = await _host(
        tester,
        effects: const KitEffects(motion: KitMotionLevel.calm),
      );
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 60));
      expect(_growing(), findsOneWidget);
      await tester.pumpAndSettle();
    });

    testWidgets('under reduced motion the page opens as always', (
      tester,
    ) async {
      final row = await host(tester, reduced: true);
      KitPageOrigin.mark(row);
      pushKitPage<void>(row, (_) => const Scaffold(body: Text('Chat')));
      await tester.pump();
      expect(_growing(), findsNothing);
      await tester.pumpAndSettle();
      KitPageOrigin.forget();
    });
  });
}

Finder _growing() =>
    find.byWidgetPredicate((w) => '${w.runtimeType}' == '_GrowFromRow');

/// A page with one 80 dp row at y = 300; returns the row's context.
Future<BuildContext> _host(
  WidgetTester tester, {
  bool reduced = false,
  KitEffects? effects,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  late BuildContext row;
  await tester.pumpWidget(
    KitEffectsScope(
      effects: effects ?? (reduced ? _off : KitEffects.defaults),
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 300),
              Builder(
                builder: (context) {
                  row = context;
                  return const SizedBox(
                    height: 80,
                    width: 412,
                    child: Align(
                      alignment: Alignment.topLeft,
                      child: Text('Row'),
                    ),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    ),
  );
  return row;
}
