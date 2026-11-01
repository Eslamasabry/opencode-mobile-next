// The chat header at the largest text and display size: compact, every label
// still reachable, and Back read first in Arabic as in English.
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

// The emulator at font scale 2.0 and the largest display size: 838 x 1860 px
// at density 420 (2.625).
const _size = Size(319, 708);
const _title = 'Find latest finance hub project in /project';

Widget _bar({
  required double scale,
  Locale locale = const Locale('en'),
  String title = _title,
}) => MaterialApp(
  theme: AppTheme.light(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: MediaQuery(
    data: MediaQueryData(size: _size, textScaler: TextScaler.linear(scale)),
    child: Scaffold(
      body: KitScreen(
        body: const SizedBox(),
        topBar: KitTopBar(
          title: title,
          titleKey: const Key('title'),
          exit: KitTopBarExit.back,
          onExit: () {},
          exitKey: const Key('back'),
          actions: [
            KitAction(
              key: const Key('collapse'),
              icon: Icons.unfold_less,
              label: 'Collapse all steps',
              onPressed: () {},
            ),
          ],
          menu: [KitMenuItem(label: 'Rename', onSelected: () {})],
          scope: KitTopBarScope(
            chipKey: const Key('chip'),
            label: 'settings-demo',
            semanticsLabel: 'Project settings-demo. Opens its files.',
            icon: Icons.folder_open,
            onTap: () {},
            branch: KitTopBarBranch(
              chipKey: const Key('branch'),
              label: 'master',
              semanticsLabel: 'Git, branch master. Opens the Git actions.',
              onTap: () {},
            ),
          ),
        ),
      ),
    ),
  ),
);

/// The bar's semantics nodes in the order a screen reader visits them.
List<String> _reading(WidgetTester tester) {
  final out = <String>[];
  void walk(SemanticsNode node) {
    final text = node.label.isNotEmpty ? node.label : node.tooltip;
    if (text.isNotEmpty) out.add(text);
    // The order a screen reader visits them, not the paint order.
    node
        .debugListChildrenInOrder(DebugSemanticsDumpOrder.traversalOrder)
        .forEach(walk);
  }

  walk(
    tester.binding.renderViews.single.owner!.semanticsOwner!.rootSemanticsNode!,
  );
  return out;
}

void main() {
  for (final scale in [2.0, 3.0]) {
    testWidgets('the header is compact at ${scale}x text', (tester) async {
      tester.view.physicalSize = _size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final handle = tester.ensureSemantics();
      await tester.pumpWidget(_bar(scale: scale));
      await tester.pumpAndSettle();

      final height = tester.getSize(find.byType(KitTopBar)).height;
      expect(
        height / _size.height,
        lessThanOrEqualTo(0.2),
        reason: 'header is $height dp of ${_size.height}',
      );
      // Every label is still there for a screen reader.
      expect(
        find.bySemanticsLabel(RegExp('Find latest finance')),
        findsWidgets,
      );
      expect(
        find.bySemanticsLabel('Project settings-demo. Opens its files.'),
        findsOneWidget,
      );
      expect(
        find.bySemanticsLabel('Git, branch master. Opens the Git actions.'),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
      handle.dispose();
    });
  }

  testWidgets('Back is read before the title in Arabic at 2x text', (
    tester,
  ) async {
    tester.view.physicalSize = _size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_bar(scale: 2.0, locale: const Locale('ar')));
    await tester.pumpAndSettle();
    final order = _reading(tester);
    final back = order.indexWhere((l) => l.contains('رجوع') || l == 'Back');
    final title = order.indexWhere((l) => l.contains('Find latest'));
    expect(back, isNonNegative, reason: 'Back not found in $order');
    expect(title, isNonNegative, reason: 'title not found in $order');
    expect(back, lessThan(title), reason: '$order');
    handle.dispose();
  });

  testWidgets('Back is read before the title in English at 2x text', (
    tester,
  ) async {
    tester.view.physicalSize = _size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_bar(scale: 2.0));
    await tester.pumpAndSettle();
    final order = _reading(tester);
    expect(
      order.indexOf('Back'),
      lessThan(order.indexWhere((l) => l.contains('Find latest'))),
      reason: '$order',
    );
    handle.dispose();
  });
}
