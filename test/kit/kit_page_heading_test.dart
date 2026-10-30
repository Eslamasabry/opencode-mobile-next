// KitPageHeading (lib/ui/kit/kit_page_heading.dart, vision 2026-10-20): a
// page's large title under a bar with no title, and one quiet line close
// under it that is either plain words or a switcher with its own target.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_page_heading.dart';

import 'kit_harness.dart';
import 'kit_motion_still.dart';

Future<void> _host(WidgetTester tester, Widget child) async {
  final context = await pumpKitHost(tester);
  unawaited(
    Navigator.of(
      context,
    ).push(MaterialPageRoute<void>(builder: (_) => Scaffold(body: child))),
  );
  await tester.pumpAndSettle();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  kitMotionStillTests(
    'KitPageHeading',
    builds: {
      'default': () => KitPageHeading(
        title: 'Files',
        line: 'neon-breaker',
        onLineTap: () {},
        lineTapLabel: 'Project neon-breaker. Switch project',
      ),
      'disabled': () =>
          const KitPageHeading(title: 'Files', line: 'neon-breaker / src'),
    },
  );

  testWidgets('the switcher line opens its chooser and names it', (
    tester,
  ) async {
    var taps = 0;
    await _host(
      tester,
      KitPageHeading(
        title: 'Files',
        line: 'neon-breaker',
        onLineTap: () => taps++,
        lineTapLabel: 'Project neon-breaker. Switch project',
        lineTapKey: const ValueKey('line'),
      ),
    );
    expect(find.text('Files'), findsOneWidget);
    expect(
      find.bySemanticsLabel('Project neon-breaker. Switch project'),
      findsOneWidget,
    );
    expect(
      tester.getSize(find.byKey(const ValueKey('line'))).height,
      greaterThanOrEqualTo(48),
    );
    // Close under the title: less than a whole target below it.
    expect(
      tester.getTopLeft(find.text('neon-breaker')).dy -
          tester.getBottomLeft(find.text('Files')).dy,
      lessThan(24),
    );
    await tester.tap(find.text('neon-breaker'));
    expect(taps, 1);
  });

  testWidgets('without a tap the line is plain words, and the title is the '
      'route\'s header', (tester) async {
    await _host(
      tester,
      const KitPageHeading(
        title: 'src',
        line: 'neon-breaker',
        namesRoute: true,
        lineTapKey: ValueKey('line'),
      ),
    );
    expect(find.byKey(const ValueKey('line')), findsNothing);
    expect(find.text('neon-breaker'), findsOneWidget);
    final title = tester.getSemantics(find.text('src'));
    expect(title.flagsCollection.isHeader, isTrue);
  });

  testWidgets('a title alone draws no line', (tester) async {
    await _host(tester, const KitPageHeading(title: 'Files'));
    expect(find.text('Files'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
