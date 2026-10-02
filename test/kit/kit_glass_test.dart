// Behaviour of KitGlass, the plain solid surface: opaque surface2, the
// hairline, the one tight shadow, and the pair's two positions.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/glass/kit_glass.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

Future<void> _pump(WidgetTester tester, Widget child, {bool dark = false}) {
  tester.view.physicalSize = const Size(1200, 2400);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  return tester.pumpWidget(
    MaterialApp(
      theme: dark ? AppTheme.dark() : AppTheme.light(),
      home: Scaffold(
        body: Align(alignment: Alignment.topCenter, child: child),
      ),
    ),
  );
}

BoxDecoration _decoration(WidgetTester tester) {
  final box = tester.widget<DecoratedBox>(
    find
        .descendant(
          of: find.byType(KitGlass),
          matching: find.byType(DecoratedBox),
        )
        .first,
  );
  return box.decoration as BoxDecoration;
}

void main() {
  for (final dark in [false, true]) {
    testWidgets('is opaque surface2 with a hairline and the one shadow '
        '(${dark ? 'dark' : 'light'})', (tester) async {
      await _pump(
        tester,
        const KitGlass(child: SizedBox(width: 200, height: 60)),
        dark: dark,
      );
      final tokens = KitTokens.of(tester.element(find.byType(KitGlass)));
      final decoration = _decoration(tester);
      expect(decoration.color, tokens.roles.surface2);
      expect(decoration.color!.a, 1);
      expect(decoration.border, isNotNull);
      expect(decoration.boxShadow, tokens.surfaceShadows);
      expect(decoration.borderRadius, BorderRadius.circular(tokens.navRadius));
      // No blur, no shader, no translucency anywhere under it.
      expect(find.byType(BackdropFilter), findsNothing);
    });
  }

  testWidgets('shadow: false draws none', (tester) async {
    await _pump(
      tester,
      const KitGlass(shadow: false, child: SizedBox(width: 200, height: 60)),
    );
    expect(_decoration(tester).boxShadow, isNull);
  });

  testWidgets('pair: leading at the start, trailing at the end; joined '
      'brings trailing next to leading', (tester) async {
    Widget pair(bool joined) => SizedBox(
      width: 400,
      child: KitGlass.pair(
        joined: joined,
        leading: const SizedBox(key: Key('lead'), width: 100, height: 48),
        trailing: const SizedBox(key: Key('trail'), width: 48, height: 48),
      ),
    );
    await _pump(tester, pair(false));
    final left = tester.getTopLeft(find.byKey(const Key('lead'))).dx;
    final apart = tester.getTopRight(find.byKey(const Key('trail'))).dx;
    expect(apart - left, greaterThan(300));

    await _pump(tester, pair(true));
    final gap =
        tester.getTopLeft(find.byKey(const Key('trail'))).dx -
        tester.getTopRight(find.byKey(const Key('lead'))).dx;
    expect(gap, greaterThan(0));
    expect(gap, lessThan(24));
  });

  testWidgets('scrolledOf follows the page under trackScroll', (tester) async {
    late BuildContext inside;
    await _pump(
      tester,
      SizedBox(
        height: 400,
        child: KitGlass.trackScroll(
          child: Builder(
            builder: (context) {
              inside = context;
              return ListView(
                children: [
                  for (var i = 0; i < 40; i++)
                    SizedBox(height: 60, child: Text('row $i')),
                ],
              );
            },
          ),
        ),
      ),
    );
    expect(KitGlass.scrolledOf(inside), isFalse);
    await tester.drag(find.byType(ListView), const Offset(0, -300));
    await tester.pump();
    expect(KitGlass.scrolledOf(inside), isTrue);
  });
}
