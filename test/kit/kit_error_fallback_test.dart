// KitErrorFallback: what a part shows when it fails to build. It must read
// in the app's theme with a complete text style of its own (never the
// yellow, double-underlined bare-text fallback outside a Material), offer
// Back where a page can be left, and draw above every theme and navigator.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

TextStyle _messageStyle(WidgetTester tester) => tester
    .widget<RichText>(
      find.descendant(
        of: find.byKey(const ValueKey('kit-error-fallback')),
        matching: find.byType(RichText),
      ),
    )
    .text
    .style!;

void main() {
  testWidgets('outside any Material: the theme ink, no underline, Back', (
    tester,
  ) async {
    final navigator = GlobalKey<NavigatorState>();
    final theme = AppTheme.light();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        theme: theme,
        home: const SizedBox(),
      ),
    );
    navigator.currentState!.push(
      KitPageRoute<void>(builder: (_) => const KitErrorFallback()),
    );
    await tester.pumpAndSettle();
    final style = _messageStyle(tester);
    final roles = ThemeRoles.resolve(theme);
    expect(style.color, roles.text1);
    expect(style.decoration, TextDecoration.none);
    expect(style.fontFamily, isNot('monospace'));
    await tester.tap(find.byKey(const ValueKey('kit-error-fallback-back')));
    await tester.pumpAndSettle();
    expect(find.byType(KitErrorFallback), findsNothing);
  });

  testWidgets('above every theme and navigator: readable, no Back', (
    tester,
  ) async {
    await tester.pumpWidget(const KitErrorFallback());
    expect(tester.takeException(), isNull);
    final style = _messageStyle(tester);
    final roles = ThemeRoles.resolve(ThemeData());
    expect(
      contrastRatio(style.color!, roles.ground),
      greaterThanOrEqualTo(4.5),
    );
    expect(find.byKey(const ValueKey('kit-error-fallback-back')), findsNothing);
  });

  testWidgets('a small slot keeps its words and offers no Back', (
    tester,
  ) async {
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(navigatorKey: navigator, home: const SizedBox()),
    );
    navigator.currentState!.push(
      KitPageRoute<void>(
        builder: (_) => const Center(
          child: SizedBox(width: 160, height: 60, child: KitErrorFallback()),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('kit-error-fallback-back')), findsNothing);
  });

  kitMotionStillTests(
    'KitErrorFallback',
    builds: {'error': () => const KitErrorFallback()},
  );
}
