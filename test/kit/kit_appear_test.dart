import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _host(Widget child, {bool reduced = false}) => MediaQuery(
  data: MediaQueryData(disableAnimations: reduced),
  child: Directionality(textDirection: TextDirection.ltr, child: child),
);

double _opacity(WidgetTester tester) => tester
    .widget<Opacity>(
      find.descendant(
        of: find.byType(KitAppear),
        matching: find.byType(Opacity),
      ),
    )
    .opacity;

void main() {
  kitMotionStillTests(
    'KitAppear',
    builds: {'arriving': () => const KitAppear(child: Text('row'))},
  );

  testWidgets('fades in and rises once', (tester) async {
    await tester.pumpWidget(_host(const KitAppear(child: Text('row'))));
    expect(_opacity(tester), 0);
    await tester.pump(KitMotion.standard ~/ 2);
    expect(_opacity(tester), inExclusiveRange(0, 1));
    await tester.pumpAndSettle();
    expect(_opacity(tester), 1);
    // A rebuild does not play it again.
    await tester.pumpWidget(_host(const KitAppear(child: Text('row'))));
    expect(_opacity(tester), 1);
  });

  testWidgets('reduced motion shows it at once', (tester) async {
    await tester.pumpWidget(
      _host(const KitAppear(child: Text('row')), reduced: true),
    );
    expect(_opacity(tester), 1);
  });
}
