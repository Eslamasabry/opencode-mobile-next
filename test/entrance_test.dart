import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/entrance.dart';

Widget _host(Widget child, {bool disableAnimations = false}) => MaterialApp(
  builder: (context, inner) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(disableAnimations: disableAnimations),
    child: inner!,
  ),
  home: Scaffold(body: child),
);

void main() {
  testWidgets('renders instantly with no wrapper when animations are off', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        const EntranceReveal(index: 5, child: Text('row')),
        disableAnimations: true,
      ),
    );

    expect(find.text('row'), findsOneWidget);
    expect(
      find.descendant(
        of: find.byType(EntranceReveal),
        matching: find.byType(Opacity),
      ),
      findsNothing,
    );
  });

  testWidgets('staggered entrance settles fully visible', (tester) async {
    await tester.pumpWidget(
      _host(const EntranceReveal(index: 8, child: Text('row'))),
    );

    await tester.pump(const Duration(milliseconds: 60));
    final midOpacity = tester
        .widget<Opacity>(
          find.descendant(
            of: find.byType(EntranceReveal),
            matching: find.byType(Opacity),
          ),
        )
        .opacity;
    expect(midOpacity, lessThan(1));

    await tester.pumpAndSettle();
    expect(
      tester
          .widget<Opacity>(
            find.descendant(
              of: find.byType(EntranceReveal),
              matching: find.byType(Opacity),
            ),
          )
          .opacity,
      1,
    );
  });

  testWidgets('disposal mid-flight leaves no pending timers', (tester) async {
    await tester.pumpWidget(
      _host(const EntranceReveal(index: 4, child: Text('row'))),
    );
    await tester.pump(const Duration(milliseconds: 40));
    await tester.pumpWidget(const SizedBox.shrink());
    // The test binding's own teardown asserts no timers remain.
  });
}
