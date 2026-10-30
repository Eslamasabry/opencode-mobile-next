// The bar at text size 2.0 on a 412 dp phone: back arrow, title and the
// overflow share the first row; the server and project lines sit below.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';

/// The bar on a 390 dp phone with a project, its server and a branch.
/// (The test font is wide: one server-less line holds the three at 1.0.)
Future<void> _pumpScopeLine(
  WidgetTester tester,
  double textScale, {
  String? server = 'Test server',
}) async {
  tester.view.physicalSize = const Size(390, 760);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: Scaffold(
        body: Column(
          children: [
            KitTopBar(
              title: 'Fix checkout',
              exit: KitTopBarExit.back,
              onExit: () {},
              scope: KitTopBarScope(
                label: 'IPTV_King',
                semanticsLabel: 'Project IPTV_King',
                suffix: server,
                onTap: () {},
                branch: KitTopBarBranch(
                  label: 'Git',
                  semanticsLabel: 'Git',
                  onTap: () {},
                ),
              ),
            ),
            const Expanded(child: SizedBox()),
          ],
        ),
      ),
    ),
  );
  await tester.pump();
  expect(tester.takeException(), isNull);
}

void main() {
  group('the branch after the project', () {
    testWidgets('shares its line with a dot between them', (tester) async {
      await _pumpScopeLine(tester, 1, server: null);
      expect(find.text('\u00b7'), findsOneWidget);
      final project = tester.getRect(find.textContaining('IPTV_King'));
      final branch = tester.getRect(find.text('Git'));
      expect(branch.center.dy, moreOrLessEquals(project.center.dy, epsilon: 1));
    });

    testWidgets('at text size 2.0 starts its own line with no leading dot', (
      tester,
    ) async {
      await _pumpScopeLine(tester, 2);
      final project = tester.getRect(find.textContaining('IPTV_King'));
      final branch = tester.getRect(find.text('Git'));
      expect(branch.top, greaterThanOrEqualTo(project.bottom - 1));
      expect(find.text('\u00b7'), findsNothing);
    });
  });

  testWidgets('title stays level with the back arrow at text size 2.0', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915) * 3;
    tester.view.devicePixelRatio = 3;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.light(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(
            context,
          ).copyWith(textScaler: const TextScaler.linear(2.0)),
          child: child!,
        ),
        home: Scaffold(
          body: Column(
            children: [
              KitTopBar(
                titleKey: const Key('t'),
                title: 'plan',
                subtitle: 'Computer at 10.0.2.2',
                exit: KitTopBarExit.back,
                exitKey: const Key('back'),
                onExit: () {},
                scope: KitTopBarScope(
                  label: 'proj',
                  semanticsLabel: 'Project proj',
                  items: [KitMenuItem(label: 'Files', onSelected: () {})],
                ),
                menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
                menuKey: const Key('more'),
              ),
              const Expanded(child: SizedBox()),
            ],
          ),
        ),
      ),
    );
    await tester.pump();
    expect(tester.takeException(), isNull);
    final back = tester.getCenter(find.byKey(const Key('back')));
    final more = tester.getCenter(find.byKey(const Key('more')));
    final title = tester.getRect(find.text('plan'));
    // The arrow and the overflow sit on the title's line, not below it.
    expect(back.dy, lessThan(title.bottom + 1));
    expect(more.dy, lessThan(title.bottom + 1));
    expect(back.dy, greaterThan(title.top - 1));
  });
}
