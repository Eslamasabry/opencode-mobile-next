// KitStreamCaret: the soft caret after the words of a reply still being
// written. It breathes only where ambient loops may run, takes no width of
// its own, and is steady under reduced motion.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _words({bool caret = true}) => Text.rich(
  TextSpan(
    text: 'On it',
    children: [if (caret) const WidgetSpan(child: KitStreamCaret())],
  ),
);

Widget _app(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  home: Scaffold(body: Center(child: child)),
);

void main() {
  tearDown(() => KitMotion.loops = false);

  kitMotionStillTests('KitStreamCaret', builds: {'after words': _words});

  testWidgets('takes no width: the words do not shift', (tester) async {
    await tester.pumpWidget(_app(_words(caret: false)));
    final without = tester.getSize(find.byType(RichText)).width;
    await tester.pumpWidget(_app(_words()));
    expect(find.byType(KitStreamCaret), findsOneWidget);
    expect(tester.getSize(find.byType(KitStreamCaret)).width, 0);
    expect(tester.getSize(find.byType(RichText)).width, closeTo(without, 1));
  });

  testWidgets('breathes only where loops may run', (tester) async {
    KitMotion.loops = false;
    await tester.pumpWidget(_app(_words()));
    expect(tester.hasRunningAnimations, isFalse);
    KitMotion.loops = true;
    await tester.pumpWidget(_app(_words()));
    await tester.pumpWidget(const SizedBox());
    await tester.pumpWidget(_app(_words()));
    expect(tester.hasRunningAnimations, isTrue);
    await tester.pumpWidget(const SizedBox());
  });
}
