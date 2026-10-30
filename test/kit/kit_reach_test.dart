// KitReach (docs/ux-system/kit-api/KitReach.md): a flat list in a sheet runs
// its rows out over the sheet's rails, so their words and hairlines start
// on the sheet title's line (flat polish, sweep 3730: 36-model, 37-allmodels,
// 52-picker had them one row inset in, at x 77 of 838 under a title at 39).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

const _rail = 20.0;

Widget _sheet(Widget list) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(
    // The sheet body's shape: its rails, the reach it offers, the list.
    body: Padding(
      padding: const EdgeInsets.symmetric(horizontal: _rail),
      child: KitReach(reach: _rail, child: list),
    ),
  ),
);

final _line = find.byKey(const ValueKey('kit-divider-line'));

void main() {
  setUp(() {
    final view =
        TestWidgetsFlutterBinding.instance.platformDispatcher.views.first;
    view
      ..physicalSize = const Size(412, 915)
      ..devicePixelRatio = 1;
  });
  tearDown(
    () => TestWidgetsFlutterBinding.instance.platformDispatcher.views.first
        .reset(),
  );

  testWidgets('is zero outside a sheet, and inside what reaches out', (
    tester,
  ) async {
    late double outside, inside, within;
    await tester.pumpWidget(
      Builder(
        builder: (context) {
          outside = KitReach.of(context);
          return KitReach(
            reach: 20,
            child: Builder(
              builder: (context) {
                inside = KitReach.of(context);
                return Directionality(
                  textDirection: TextDirection.ltr,
                  child: KitReach.extend(
                    by: 16,
                    child: Builder(
                      builder: (context) {
                        within = KitReach.of(context);
                        return const SizedBox(height: 10);
                      },
                    ),
                  ),
                );
              },
            ),
          );
        },
      ),
    );
    expect(outside, 0);
    expect(inside, 20);
    expect(within, 0);
  });

  testWidgets('extend: as wide as its box, its child wider on each side, '
      'taps in the reach land', (tester) async {
    var taps = 0;
    await tester.pumpWidget(
      Directionality(
        textDirection: TextDirection.ltr,
        child: Align(
          alignment: Alignment.topLeft,
          child: SizedBox(
            width: 280,
            child: Padding(
              // A sheet body's rails, straight around what reaches out.
              padding: const EdgeInsets.symmetric(horizontal: 40),
              child: KitReach.extend(
                by: 16,
                child: GestureDetector(
                  key: const ValueKey('wide'),
                  behavior: HitTestBehavior.opaque,
                  onTap: () => taps++,
                  child: const SizedBox(height: 48),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    final rect = tester.getRect(find.byKey(const ValueKey('wide')));
    expect(rect.left, 24);
    expect(rect.width, 232);
    await tester.tapAt(const Offset(28, 24));
    expect(taps, 1);
  });

  testWidgets('a flat row group in a sheet starts its words, hairlines and '
      'label on the title line', (tester) async {
    await tester.pumpWidget(
      _sheet(
        KitRowGroup(
          label: 'Models',
          margin: EdgeInsets.zero,
          children: [
            KitRow(title: 'Big Pickle', onTap: () {}),
            KitRow(title: 'Beam', onTap: () {}),
          ],
        ),
      ),
    );
    expect(tester.getTopLeft(find.text('Models')).dx, _rail);
    expect(tester.getTopLeft(find.text('Big Pickle')).dx, _rail);
    expect(tester.getRect(_line).left, _rail);
    expect(tester.getRect(_line).right, 412 - _rail);
    // The press runs out over the rails, the platform's edge-to-edge row.
    final row = tester.getRect(
      find.ancestor(of: find.text('Big Pickle'), matching: find.byType(KitRow)),
    );
    expect(row.left, _rail - 16);
    expect(row.right, 412 - _rail + 16);
  });

  testWidgets('flat choice tiles in a sheet start their words and hairlines '
      'on the title line', (tester) async {
    await tester.pumpWidget(
      _sheet(
        KitChoiceTiles<String>(
          choices: const [
            KitChoice(value: 'a', title: 'In use', supporting: 'GLM'),
            KitChoice(value: 'b', title: 'Default', supporting: 'Big Pickle'),
          ],
          selected: 'a',
          onSelected: (_) {},
          more: KitChoiceMore(label: 'All models (9)', onTap: () {}),
        ),
      ),
    );
    expect(tester.getTopLeft(find.text('In use')).dx, _rail);
    expect(tester.getTopLeft(find.text('All models (9)')).dx, _rail);
    for (var i = 0; i < _line.evaluate().length; i++) {
      expect(tester.getRect(_line.at(i)).left, _rail);
      expect(tester.getRect(_line.at(i)).right, 412 - _rail);
    }
  });

  testWidgets('off a sheet nothing reaches: a page keeps its own inset', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        home: Scaffold(
          body: KitRowGroup(
            children: [KitRow(title: 'AI Team', onTap: () {})],
          ),
        ),
      ),
    );
    final tokens = KitTokens.of(tester.element(find.text('AI Team')));
    expect(tester.getTopLeft(find.text('AI Team')).dx, tokens.gutter);
  });
}
