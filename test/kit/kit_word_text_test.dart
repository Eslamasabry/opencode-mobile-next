// KitWordText: words that end at a whole word with "…" when they do not
// fit their lines (owner, 2026-10-22: "…and retry w…"), read in full.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

const _words =
    'Ran the checkout tests on a clean cache and retry when the first run '
    'fails because the price refresh races the coupon check';

Widget _app(Widget child, {TextDirection direction = TextDirection.ltr}) =>
    MaterialApp(
      theme: AppTheme.dark(),
      home: Directionality(
        textDirection: direction,
        child: Scaffold(
          body: Align(alignment: Alignment.topLeft, child: child),
        ),
      ),
    );

String _shown(WidgetTester tester) =>
    tester.widget<Text>(find.byType(Text)).data!;

void main() {
  testWidgets('words that fit are shown as they are', (tester) async {
    await tester.pumpWidget(
      _app(
        const SizedBox(
          width: 400,
          child: KitWordText('Read 3 files', maxLines: 1),
        ),
      ),
    );
    expect(_shown(tester), 'Read 3 files');
    expect(find.bySemanticsLabel('Read 3 files'), findsOneWidget);
  });

  testWidgets('a cut never leaves a full stop in front of the "…"', (
    tester,
  ) async {
    await tester.pumpWidget(_app(const SizedBox()));
    final context = tester.element(find.byType(Scaffold));
    final style = KitText.styleOf(context, KitTextRole.body);
    var cuts = 0;
    for (var width = 60.0; width < 320; width += 2) {
      final shown = KitWordText.cut(
        'Still have not read theme.js. Then I will write the tests! Really?',
        style: style,
        maxWidth: width,
        maxLines: 1,
        scaler: TextScaler.noScaling,
        direction: TextDirection.ltr,
      );
      if (!shown.endsWith('…')) continue;
      cuts++;
      expect(shown, isNot(matches(RegExp(r'[.!?,;:]…$'))), reason: '$width');
    }
    expect(cuts, greaterThan(5));
  });

  for (final lines in [1, 2]) {
    testWidgets('$lines line(s): cut after a whole word, with "…"', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(SizedBox(width: 220, child: KitWordText(_words, maxLines: lines))),
      );
      final shown = _shown(tester);
      expect(shown, endsWith('…'));
      final kept = shown.substring(0, shown.length - 1);
      expect(_words.startsWith(kept), isTrue);
      expect(_words[kept.length], ' ', reason: 'cut at "$kept"');
      // As many words as fit: one more would not.
      final paragraph = tester.renderObject<RenderParagraph>(
        find.byType(RichText),
      );
      expect(paragraph.didExceedMaxLines, isFalse);
      expect(find.bySemanticsLabel(_words), findsOneWidget);
    });
  }

  testWidgets('one word wider than the line falls back to the ellipsis', (
    tester,
  ) async {
    const long = 'checkout_bloc_price_refresh_coupon_race.dart';
    await tester.pumpWidget(
      _app(const SizedBox(width: 80, child: KitWordText(long, maxLines: 1))),
    );
    expect(_shown(tester), long);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Arabic cuts at a word too', (tester) async {
    const arabic =
        'شغّل اختبارات الدفع على ذاكرة نظيفة وأعد المحاولة عندما يفشل التشغيل الأول';
    await tester.pumpWidget(
      _app(
        const SizedBox(width: 160, child: KitWordText(arabic, maxLines: 1)),
        direction: TextDirection.rtl,
      ),
    );
    final shown = _shown(tester);
    expect(shown, endsWith('…'));
    final kept = shown.substring(0, shown.length - 1);
    expect(arabic[kept.length], ' ');
  });

  kitMotionStillTests(
    'KitWordText',
    builds: {
      'fits': () => const KitWordText('Read 3 files', maxLines: 1),
      'cut': () =>
          const SizedBox(width: 160, child: KitWordText(_words, maxLines: 2)),
    },
  );
}
