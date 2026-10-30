// KitSliverRowGroup (lib/ui/kit/kit_sliver_row_group.dart, slice-R4): a
// list's head rows and its lazy rows on one surface1 panel, hairlines inset
// to the words, the label on the same inset as KitRowGroup's.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_divider.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_row_parts.dart' show KitRowIcon;
import 'package:opencode_mobile/ui/kit/kit_sliver_row_group.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';

import 'kit_motion_still.dart';

KitRow _row(String title) => KitRow(
  leading: const KitRowIcon(AppIconography.chat),
  title: title,
  onTap: () {},
);

Widget _recent({int count = 1000, String? label = 'Recent'}) =>
    KitSliverRowGroup(
      label: label,
      itemCount: count,
      itemBuilder: (context, index) => _row('Conversation $index'),
      children: [_row('New conversation'), _row('Open a folder')],
    );

Widget _app(List<Widget> slivers) => MaterialApp(
  theme: AppTheme.dark(),
  home: Scaffold(body: CustomScrollView(slivers: slivers)),
);

void main() {
  kitMotionStillTests(
    'KitSliverRowGroup',
    builds: {
      'default': () => CustomScrollView(slivers: [_recent(count: 20)]),
    },
  );

  testWidgets('builds only the rows in view, head rows first', (tester) async {
    await tester.pumpWidget(_app([_recent()]));
    expect(find.text('Recent'), findsOneWidget);
    expect(find.text('New conversation'), findsOneWidget);
    expect(find.text('Conversation 0'), findsOneWidget);
    expect(find.text('Conversation 999'), findsNothing);
    expect(
      tester.getTopLeft(find.text('Open a folder')).dy,
      lessThan(tester.getTopLeft(find.text('Conversation 0')).dy),
    );
    // One hairline above every built row but the first.
    final built = find.byType(KitRow).evaluate().length;
    expect(built, lessThan(40));
    expect(find.byType(KitDivider), findsNWidgets(built - 1));
    // Inset to the words, since the rows lead with an icon tile.
    final divider = tester.widget<KitDivider>(find.byType(KitDivider).first);
    expect(divider.inset, KitDividerInset.text);

    await tester.scrollUntilVisible(
      find.text('Conversation 999'),
      2000,
      scrollable: find.byType(Scrollable),
    );
    expect(find.text('Conversation 999'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Solid sets the rows on the page: a bare panel, rows '
      'reaching the screen edge, words flush with the label', (tester) async {
    await tester.pumpWidget(_app([_recent(count: 3)]));
    final sliver = tester.widget<DecoratedSliver>(find.byType(DecoratedSliver));
    expect(KitSurfaceDecoration.isBare(sliver.decoration), isTrue);
    expect(find.byType(DecoratedSliver), findsOneWidget);
    // No rounded ends clip the rows' press fill.
    expect(
      find.ancestor(of: find.byType(KitRow), matching: find.byType(ClipRRect)),
      findsNothing,
    );
    expect(tester.getTopLeft(find.byType(KitRow).first).dx, 0);
  });

  testWidgets('its label starts where a KitRowGroup label does, with the '
      'section gap only after something', (tester) async {
    await tester.pumpWidget(
      _app([
        SliverToBoxAdapter(
          child: KitRowGroup(
            key: const ValueKey('box'),
            label: 'Pinned',
            children: [_row('Release checklist')],
          ),
        ),
        _recent(count: 3),
      ]),
    );
    expect(tester.getTopLeft(find.text('Pinned')).dy, 0);
    expect(
      tester.getTopLeft(find.text('Recent')).dx,
      tester.getTopLeft(find.text('Pinned')).dx,
    );
    expect(tester.getTopLeft(find.text('Recent')).dx, 16);
    final tokens = KitTokens.of(tester.element(find.text('Recent')));
    expect(
      tester.getTopLeft(find.text('Recent')).dy -
          tester.getBottomLeft(find.byKey(const ValueKey('box'))).dy,
      tokens.sectionGap,
    );
  });

  testWidgets('as the first sliver its label has no gap above', (tester) async {
    await tester.pumpWidget(_app([_recent(count: 3)]));
    expect(tester.getTopLeft(find.text('Recent')).dy, 0);
  });

  testWidgets('an empty group draws nothing, not a label over nothing', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app([
        const KitSliverRowGroup(label: 'Recent'),
        const SliverToBoxAdapter(child: Text('after')),
      ]),
    );
    expect(find.text('Recent'), findsNothing);
    expect(tester.getTopLeft(find.text('after')).dy, 0);
  });

  testWidgets('keyed rows are found after the head rows', (tester) async {
    final group = KitSliverRowGroup(
      itemCount: 2,
      itemBuilder: (context, index) =>
          KitRow(key: ValueKey('c$index'), title: 'Item $index', onTap: () {}),
      findChildIndexCallback: (key) => switch (key) {
        ValueKey<String>(value: 'c0') => 0,
        ValueKey<String>(value: 'c1') => 1,
        _ => null,
      },
      children: [_row('Head')],
    );
    await tester.pumpWidget(_app([group]));
    expect(find.text('Head'), findsOneWidget);
    expect(find.text('Item 1'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
