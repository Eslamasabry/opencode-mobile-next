// KitRowGroup (lib/ui/kit/kit_row.dart), added by the visual language merge
// before the wave-0b gates existed. Its reduced-motion samples (G8x, MOT-7)
// and the first behaviour checks live here; kit-KitRow-v2 rebuilds the part
// and adds its full contracts.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

KitRow _row(String title) => KitRow(
  leading: const KitRowIcon(AppIconography.terminal),
  title: title,
  trailing: const KitChevron(),
  onTap: () {},
);

Widget _group() => KitRowGroup(
  label: 'Working now',
  children: [
    _row('Speed up the CI pipeline'),
    _row('Explain the payments module'),
    _row('Fix flaky checkout test'),
  ],
);

void main() {
  kitMotionStillTests(
    'KitRowGroup',
    builds: {
      'default': _group,
      'no label': () => KitRowGroup(children: [_row('Model')]),
    },
  );

  testWidgets('KitRowGroup names its section as a heading above its rows', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        home: Scaffold(body: SingleChildScrollView(child: _group())),
      ),
    );
    expect(find.text('Working now'), findsOneWidget);
    expect(
      tester.getSemantics(find.text('Working now')),
      matchesSemantics(label: 'Working now', isHeader: true),
    );
    for (final title in [
      'Speed up the CI pipeline',
      'Explain the payments module',
      'Fix flaky checkout test',
    ]) {
      expect(find.text(title), findsOneWidget);
      expect(
        tester.getTopLeft(find.text(title)).dy,
        greaterThan(tester.getTopLeft(find.text('Working now')).dy),
      );
    }
    // One separator between each pair of rows, none above the first.
    // (The section rule above the label draws nothing in Solid.)
    expect(find.byKey(const ValueKey('kit-divider-line')), findsNWidgets(2));
    semantics.dispose();
  });

  testWidgets('KitRowGroup label starts at the gutter, not 4 dp inside it '
      '(slice-R4)', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        home: Scaffold(body: SingleChildScrollView(child: _group())),
      ),
    );
    expect(tester.getTopLeft(find.text('Working now')).dx, 16);
    // First thing in the scroll view: no section gap above.
    expect(tester.getTopLeft(find.text('Working now')).dy, 0);
  });
}
