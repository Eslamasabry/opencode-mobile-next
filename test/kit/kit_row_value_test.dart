// KitRowValue (lib/ui/kit/kit_row.dart), added by the visual language merge
// before the wave-0b gates existed. Its reduced-motion samples (G8x, MOT-7)
// and the first behaviour checks live here; kit-KitRow-v2 rebuilds the part
// and adds its full contracts.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  home: Scaffold(
    body: SizedBox(
      width: 200,
      child: Align(alignment: AlignmentDirectional.centerEnd, child: child),
    ),
  ),
);

void main() {
  kitMotionStillTests(
    'KitRowValue',
    builds: {
      'default': () => const KitRowValue('Claude Sonnet 4'),
      'no chevron': () => const KitRowValue('Ask first', chevron: false),
      'count': () => const KitRowValue.count(3, '3 errors kept'),
    },
  );

  testWidgets('KitRowValue shows the value before a chevron', (tester) async {
    await tester.pumpWidget(_host(const KitRowValue('Claude Sonnet 4')));
    expect(find.text('Claude Sonnet 4'), findsOneWidget);
    expect(find.byIcon(AppIconography.chevronRight), findsOneWidget);
    expect(
      tester.getCenter(find.byIcon(AppIconography.chevronRight)).dx,
      greaterThan(tester.getCenter(find.text('Claude Sonnet 4')).dx),
    );
  });

  testWidgets('KitRowValue without a chevron shows only the value', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(const KitRowValue('Ask first', chevron: false)),
    );
    expect(find.text('Ask first'), findsOneWidget);
    expect(find.byIcon(AppIconography.chevronRight), findsNothing);
  });

  testWidgets('a long KitRowValue stays on one line and never overflows', (
    tester,
  ) async {
    const long =
        'Claude Sonnet 4 with extended thinking on the office workstation';
    await tester.pumpWidget(_host(const KitRowValue(long)));
    expect(tester.takeException(), isNull);
    final text = tester.widget<Text>(find.text(long));
    expect(text.maxLines, 1);
    expect(text.overflow, TextOverflow.ellipsis);
  });

  testWidgets('KitRowValue.count is a number with its words, and nothing '
      'at zero', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(_host(const KitRowValue.count(3, '3 errors kept')));
    expect(find.text('3'), findsOneWidget);
    expect(find.bySemanticsLabel('3 errors kept'), findsOneWidget);
    expect(find.byIcon(AppIconography.chevronRight), findsOneWidget);

    await tester.pumpWidget(_host(const KitRowValue.count(120, 'many')));
    expect(find.text('99+'), findsOneWidget);

    await tester.pumpWidget(_host(const KitRowValue.count(0, 'none kept')));
    expect(find.text('0'), findsNothing);
    expect(find.bySemanticsLabel('none kept'), findsNothing);
    expect(find.byIcon(AppIconography.chevronRight), findsOneWidget);
    semantics.dispose();
  });

  testWidgets('a prominent value is body size, the quiet one smaller', (
    tester,
  ) async {
    double size(String text) =>
        tester.widget<Text>(find.text(text)).style!.fontSize!;
    await tester.pumpWidget(_host(const KitRowValue('quiet')));
    final quiet = size('quiet');
    await tester.pumpWidget(_host(const KitRowValue('big', prominent: true)));
    expect(size('big'), greaterThan(quiet));
  });
}
