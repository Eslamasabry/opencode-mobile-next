// KitStartBox (the one big glowing field a new conversation starts with):
// its behaviour, and the still-motion registration G8 requires.
import 'kit_motion_still.dart';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';

Widget _host(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: child),
);

KitStartBox _box({
  TextEditingController? controller,
  FocusNode? focusNode,
  bool enabled = true,
}) => KitStartBox(
  fieldKey: const ValueKey('field'),
  controller: controller ?? TextEditingController(),
  focusNode: focusNode ?? FocusNode(),
  question: 'What would you like to build?',
  enabled: enabled,
);

void main() {
  kitMotionStillTests(
    'KitStartBox',
    builds: {'default': () => _box()},
    changes: {
      'locks while starting': KitMotionChange(
        build: () => _box(),
        act: (tester, stage) => stage.rebuild(_box(enabled: false)),
        shows: 'What would you like to build?',
      ),
    },
  );

  testWidgets('shows the question until something is typed', (tester) async {
    final controller = TextEditingController();
    await tester.pumpWidget(_host(_box(controller: controller)));
    expect(find.text('What would you like to build?'), findsOneWidget);
    await tester.enterText(find.byKey(const ValueKey('field')), 'A game');
    await tester.pumpAndSettle();
    expect(controller.text, 'A game');
  });

  // Unslop 2026-10-30: in Solid nothing glows while nothing runs; the glow
  // is the composer's while an agent works.
  List<BoxShadow> shadows(WidgetTester tester) => [
    for (final box in tester.widgetList<DecoratedBox>(
      find.descendant(
        of: find.byType(KitStartBox),
        matching: find.byType(DecoratedBox),
      ),
    ))
      ...switch (box.decoration) {
        final BoxDecoration d => d.boxShadow ?? const <BoxShadow>[],
        final ShapeDecoration d => d.shadows ?? const <BoxShadow>[],
        final KitSurfaceDecoration d => d.shadows,
        _ => const <BoxShadow>[],
      },
  ];

  Border edge(WidgetTester tester) => [
    for (final box in tester.widgetList<DecoratedBox>(
      find.descendant(
        of: find.byType(KitStartBox),
        matching: find.byType(DecoratedBox),
      ),
    ))
      if (box.decoration case BoxDecoration(border: final Border border))
        border,
  ].single;

  testWidgets('Solid: no glow at rest or in focus, a hairline at rest and '
      'the accent edge in focus', (tester) async {
    final focus = FocusNode();
    addTearDown(focus.dispose);
    await tester.pumpWidget(_host(_box(focusNode: focus)));
    await tester.pumpAndSettle();
    final roles = KitTokens.of(tester.element(find.byType(KitStartBox))).roles;
    expect(shadows(tester), isEmpty, reason: 'no halo at rest');
    expect(edge(tester).top.color, roles.hairline);
    focus.requestFocus();
    await tester.pumpAndSettle();
    expect(shadows(tester), isEmpty, reason: 'no halo in focus either');
    expect(edge(tester).top.color, roles.accent);
    expect(
      edge(tester).top.width,
      KitTokens.focusRingWidth(tester.element(find.byType(KitStartBox))),
    );
  });

  testWidgets('a field that is starting cannot be typed in', (tester) async {
    await tester.pumpWidget(_host(_box(enabled: false)));
    final field = tester.widget<TextField>(find.byKey(const ValueKey('field')));
    expect(field.enabled, isFalse);
  });

  testWidgets('is named for a screen reader by its question', (tester) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(_host(_box()));
    expect(
      find.bySemanticsLabel('What would you like to build?'),
      findsWidgets,
    );
    handle.dispose();
  });
}
