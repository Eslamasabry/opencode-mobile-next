// A settled KitReveal never clips: the shadow and outline of what it holds
// (an opened command box) reach past its edge (owner phone, 2026-10-29).
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/motion/kit_reveal.dart';

Widget _host(bool on) => MaterialApp(
  theme: AppTheme.dark(),
  home: Scaffold(
    body: KitReveal(child: on ? const SizedBox(height: 40) : null),
  ),
);

RenderClipRect _clip(WidgetTester tester) =>
    tester.renderObject<RenderClipRect>(
      find.descendant(
        of: find.byType(KitReveal),
        matching: find.byType(ClipRect),
      ),
    );

void main() {
  testWidgets('clips while it folds, not once settled', (tester) async {
    await tester.pumpWidget(_host(false));
    await tester.pumpWidget(_host(true));
    await tester.pump(const Duration(milliseconds: 50));
    expect(_clip(tester).clipBehavior, Clip.hardEdge);
    await tester.pumpAndSettle();
    expect(_clip(tester).clipBehavior, Clip.none);
  });

  testWidgets('a part present from the first build is not clipped', (
    tester,
  ) async {
    await tester.pumpWidget(_host(true));
    expect(_clip(tester).clipBehavior, Clip.none);
  });
}
