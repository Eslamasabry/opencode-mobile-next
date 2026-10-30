// Fix group C of the style recheck (docs/audits/style-coherence-recheck-
// 2026-10-21.md §6, F3 and F6): the rules between a group's rows stop at
// the page margin when the rows reach into it (Paper, N3); framed groups
// set one under another keep a gap (Glass, Soft, Brutal, V11); a table's
// header band sits inside the table's frame, not over it (Brutal, N6).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

const _width = 400.0;

Future<void> _pump(
  WidgetTester tester,
  KitSurfaceStyle style,
  Widget child, {
  TextDirection direction = TextDirection.ltr,
}) async {
  tester.view
    ..physicalSize = const Size(_width, 900)
    ..devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: Directionality(textDirection: direction, child: child!),
      ),
      home: KitEffectsScope(
        effects: KitEffects(surfaceStyle: style),
        child: kitStyleTheme(child: Scaffold(body: child)),
      ),
    ),
  );
}

final _line = find.byKey(const ValueKey('kit-divider-line'));

/// Inside the mini table's panel: its sideways scroller.
final _miniBody = find.descendant(
  of: find.byType(KitMiniTable),
  matching: find.byType(SingleChildScrollView),
);
final _md = find.byType(KitMarkdown);

List<KitRow> _rows() => [
  for (final title in ['Server', 'Agent', 'Help'])
    KitRow(title: title, onTap: () {}),
];

/// Whether [decoration] paints a fill of its own.
bool _fills(Decoration decoration) => switch (decoration) {
  KitSurfaceDecoration(:final fill) => fill.a > 0,
  BoxDecoration(:final color) => color != null && color.a > 0,
  ShapeDecoration(:final color) => color != null && color.a > 0,
  _ => true,
};

/// The fills painted behind [word] inside [table] (the table's own panel
/// is outside it).
List<Decoration> _headerFills(
  WidgetTester tester,
  String word, {
  required Finder table,
}) => [
  for (final box in tester.widgetList<DecoratedBox>(
    find.ancestor(
      of: find.text(word),
      matching: find.descendant(of: table, matching: find.byType(DecoratedBox)),
    ),
  ))
    if (_fills(box.decoration)) box.decoration,
];

void main() {
  group('F3: row rules end at the margin', () {
    for (final direction in TextDirection.values) {
      testWidgets('Paper, KitRowGroup, ${direction.name}', (tester) async {
        await _pump(
          tester,
          KitSurfaceStyle.paper,
          KitRowGroup(children: _rows()),
          direction: direction,
        );
        final gutter = KitTokens.of(tester.element(_line.first)).gutter;
        expect(_line, findsNWidgets(2));
        for (final line in tester.elementList(_line)) {
          final rect = tester.getRect(
            find.byElementPredicate((e) => e == line),
          );
          if (direction == TextDirection.ltr) {
            expect(rect.right, _width - gutter);
          } else {
            expect(rect.left, gutter);
          }
        }
      });
    }

    testWidgets('Paper, KitSliverRowGroup', (tester) async {
      await _pump(
        tester,
        KitSurfaceStyle.paper,
        CustomScrollView(slivers: [KitSliverRowGroup(children: _rows())]),
      );
      final gutter = KitTokens.of(tester.element(_line.first)).gutter;
      expect(_line, findsNWidgets(2));
      for (final line in tester.elementList(_line)) {
        final rect = tester.getRect(find.byElementPredicate((e) => e == line));
        expect(rect.right, _width - gutter);
      }
    });

    testWidgets('Solid: the rules still run to the panel\'s end', (
      tester,
    ) async {
      await _pump(
        tester,
        KitSurfaceStyle.solid,
        KitRowGroup(children: _rows()),
      );
      final gutter = KitTokens.of(tester.element(_line.first)).gutter;
      expect(tester.getRect(_line.first).right, _width - gutter);
    });
  });

  group('F3: framed groups one under another keep a gap', () {
    Widget twoGroups() => Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          children: [KitRow(title: 'Model', onTap: () {})],
        ),
        KitRowGroup(
          children: [KitRow(title: 'Voice', onTap: () {})],
        ),
      ],
    );

    double between(WidgetTester tester) =>
        tester.getTopLeft(find.text('Voice')).dy -
        tester.getBottomLeft(find.text('Model')).dy;

    testWidgets('Glass, Soft and Brutal part them; Solid and Paper do not', (
      tester,
    ) async {
      await _pump(tester, KitSurfaceStyle.solid, twoGroups());
      final flush = between(tester);
      final gap = KitTokens.of(tester.element(find.text('Model'))).space3;
      for (final style in [KitSurfaceStyle.soft, KitSurfaceStyle.brutal]) {
        await _pump(tester, style, twoGroups());
        expect(between(tester), flush + gap, reason: style.name);
      }
      await _pump(tester, KitSurfaceStyle.paper, twoGroups());
      expect(between(tester), flush);
    });

    testWidgets('a group first in its place, or after other things, keeps '
        'its place', (tester) async {
      Widget after(Widget above) => Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          above,
          KitRowGroup(
            children: [KitRow(title: 'Voice', onTap: () {})],
          ),
        ],
      );
      await _pump(
        tester,
        KitSurfaceStyle.brutal,
        after(const SizedBox(height: 40)),
      );
      expect(tester.getTopLeft(find.byType(KitRowGroup)).dy, 40);
      final top = tester.getTopLeft(find.text('Voice')).dy;
      await _pump(
        tester,
        KitSurfaceStyle.brutal,
        after(const SizedBox(height: 40, child: Text('Above'))),
      );
      expect(tester.getTopLeft(find.text('Voice')).dy, top);
    });
  });

  group('F6: a table\'s header band sits inside its frame', () {
    const mini = KitMiniTable(
      columns: ['File', 'Added'],
      rows: [
        KitMiniTableRow(['lib/main.dart', '12']),
      ],
    );
    const markdown = KitMarkdown(
      '| Path | Lines |\n| --- | ---: |\n| lib/main.dart | 3 |',
      selectable: false,
    );

    testWidgets('Solid keeps its header fill', (tester) async {
      await _pump(tester, KitSurfaceStyle.solid, mini);
      expect(_headerFills(tester, 'File', table: _miniBody), isNotEmpty);
      await _pump(tester, KitSurfaceStyle.solid, markdown);
      expect(_headerFills(tester, 'Path', table: _md), isNotEmpty);
    });

    for (final style in [KitSurfaceStyle.brutal, KitSurfaceStyle.paper]) {
      testWidgets('${style.name}: no band over the frame\'s edge', (
        tester,
      ) async {
        await _pump(tester, style, mini);
        expect(_headerFills(tester, 'File', table: _miniBody), isEmpty);
        await _pump(tester, style, markdown);
        expect(_headerFills(tester, 'Path', table: _md), isEmpty);
      });
    }
  });
}
