// KitScrollFade: a sideways line fades only the edges with more past them,
// and the fade follows the scroll (impeccable audit 2026-10-28, #6).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_scroll_fade.dart';

import 'kit_motion_still.dart';

Future<void> _pump(WidgetTester tester, double contentWidth) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      home: Scaffold(
        body: Center(
          child: SizedBox(
            width: 200,
            child: KitScrollFade(
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: SizedBox(width: contentWidth, height: 40),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// A line too long for its 200 dp box.
Widget _longLine() => const SizedBox(
  width: 200,
  child: KitScrollFade(
    child: SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Text('Auto Accept · Ask first · Big Pickle · tail'),
    ),
  ),
);

({bool start, bool end}) _fades(WidgetTester tester) =>
    KitScrollFade.fadesOf(tester.element(find.byType(KitScrollFade)));

void main() {
  testWidgets('a line that fits fades nowhere', (tester) async {
    await _pump(tester, 120);
    expect(_fades(tester), (start: false, end: false));
  });

  testWidgets('a line that runs past its end fades there, then at its start '
      'once scrolled to the end', (tester) async {
    await _pump(tester, 600);
    expect(_fades(tester), (start: false, end: true));
    await tester.drag(
      find.byType(SingleChildScrollView),
      const Offset(-800, 0),
    );
    await tester.pumpAndSettle();
    expect(_fades(tester), (start: true, end: false));
  });

  kitMotionStillTests(
    'KitScrollFade',
    builds: {'a long line': _longLine},
    changes: {
      'scrolled to its end': KitMotionChange(
        build: _longLine,
        act: (tester, stage) async {
          final position = tester
              .state<ScrollableState>(find.byType(Scrollable))
              .position;
          position.jumpTo(position.maxScrollExtent);
        },
        shows: 'Auto Accept · Ask first · Big Pickle · tail',
      ),
    },
  );
}
