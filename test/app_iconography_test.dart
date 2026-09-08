import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';

void main() {
  testWidgets(
    'RTL mirrors navigation but preserves terminal and branch shapes',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Directionality(
            textDirection: TextDirection.rtl,
            child: Row(
              children: [
                AppGlyph(AppIconography.back),
                AppGlyph(AppIconography.terminal),
                AppGlyph(AppIconography.branch),
              ],
            ),
          ),
        ),
      );
      Finder transformsFor(IconData data) => find.descendant(
        of: find.byWidgetPredicate(
          (widget) => widget is AppGlyph && widget.icon == data,
        ),
        matching: find.byType(Transform),
      );
      expect(transformsFor(AppIconography.back), findsOneWidget);
      expect(transformsFor(AppIconography.terminal), findsNothing);
      expect(transformsFor(AppIconography.branch), findsNothing);
    },
  );

  testWidgets('brand mark fits its slot and announces one optional label', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    try {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: AppBrandMark(size: 24, semanticLabel: 'OpenCode'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.getSize(find.byType(AppBrandMark)), const Size(24, 24));
      expect(find.bySemanticsLabel('OpenCode'), findsOneWidget);
      expect(tester.takeException(), isNull);
    } finally {
      semantics.dispose();
    }
  });

  testWidgets('selected glyph announces its meaning only once', (tester) async {
    final semantics = tester.ensureSemantics();
    try {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: AppGlyph(
              AppIconography.filesSelected,
              semanticLabel: 'Project files',
            ),
          ),
        ),
      );

      expect(find.bySemanticsLabel('Project files'), findsOneWidget);
      expect(tester.takeException(), isNull);
    } finally {
      semantics.dispose();
    }
  });

  testWidgets('small action glyph retains a labeled full-size button', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    try {
      var copies = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: IconButton(
              tooltip: 'Copy code',
              onPressed: () => copies++,
              icon: const AppGlyph(
                AppIconography.copy,
                size: AppIconography.inlineSize,
              ),
            ),
          ),
        ),
      );

      final button = find.byType(IconButton);
      final data = tester.getSemantics(button).getSemanticsData();
      expect('${data.label} ${data.tooltip}', contains('Copy code'));
      expect(tester.getSize(button).width, greaterThanOrEqualTo(48));
      expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
      await tester.tap(button);
      expect(copies, 1);
    } finally {
      semantics.dispose();
    }
  });

  testWidgets('selected navigation inherits size and color in high contrast', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: MediaQuery(
          data: MediaQueryData(highContrast: true),
          child: Scaffold(
            body: IconTheme(
              data: IconThemeData(size: 30, color: Colors.white),
              child: AppGlyph(AppIconography.activitySelected),
            ),
          ),
        ),
      ),
    );

    expect(tester.getSize(find.byType(AppGlyph)), const Size(30, 30));
    expect(tester.takeException(), isNull);
  });
}
