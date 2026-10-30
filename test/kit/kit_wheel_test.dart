import 'dart:ui' show SemanticsAction;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _app(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Material(child: Center(child: child)),
);

KitWheel _time() => KitWheel(
  columns: [
    KitWheelColumn<int>(
      values: const [7, 8, 9, 10, 11],
      selected: 9,
      label: (h) => '$h',
      onChanged: (_) {},
      semanticsLabel: 'Hour',
    ),
    KitWheelColumn<String>(
      values: const ['00', '15', '30', '45'],
      selected: '00',
      label: (m) => m,
      onChanged: (_) {},
      semanticsLabel: 'Minute',
    ),
  ],
);

void main() {
  // The wheel has no state of its own beyond its value (States: none).
  kitMotionStillTests('KitWheel', builds: {'default': _time});

  testWidgets('KitWheel rolls to a value by drag, tap, keys and the screen '
      'reader', (tester) async {
    var chosen = 2;
    await tester.pumpWidget(
      _app(
        StatefulBuilder(
          builder: (context, setState) => KitWheel(
            columns: [
              KitWheelColumn<int>(
                key: const ValueKey('hours'),
                values: const [1, 2, 3, 4, 5],
                selected: chosen,
                label: (h) => 'Every $h hours',
                onChanged: (h) => setState(() => chosen = h),
                semanticsLabel: 'How often',
              ),
            ],
          ),
        ),
      ),
    );
    final handle = tester.ensureSemantics();
    expect(
      tester.getSemantics(find.byKey(const ValueKey('hours'))),
      matchesSemantics(
        label: 'How often',
        value: 'Every 2 hours',
        increasedValue: 'Every 3 hours',
        decreasedValue: 'Every 1 hours',
        hasIncreaseAction: true,
        hasDecreaseAction: true,
        isFocusable: true,
        hasFocusAction: true,
      ),
    );

    // One row up rests on the next value.
    await tester.drag(
      find.byKey(const ValueKey('hours')),
      const Offset(0, -KitWheel.rowExtent),
    );
    await tester.pumpAndSettle();
    expect(chosen, 3);

    // A tap on a neighbour rolls to it.
    await tester.tap(find.text('Every 4 hours'));
    await tester.pumpAndSettle();
    expect(chosen, 4);

    // Arrow keys move one step.
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.arrowUp);
    await tester.pumpAndSettle();
    expect(chosen, 3);

    // The screen reader's increase.
    tester.semantics.performAction(
      find.semantics.byLabel('How often'),
      SemanticsAction.increase,
    );
    await tester.pumpAndSettle();
    expect(chosen, 4);
    handle.dispose();
  });

  testWidgets('KitSwitchRow.onOpen: the row opens, only the switch '
      'toggles', (tester) async {
    var opened = 0;
    var value = true;
    await tester.pumpWidget(
      _app(
        StatefulBuilder(
          builder: (context, setState) => KitSwitchRow(
            title: 'Run the tests',
            supporting: 'Every weekday at 9:00 AM',
            value: value,
            onOpen: () => opened++,
            openLabel: 'Edit schedule',
            onChanged: (v) => setState(() => value = v),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Run the tests'));
    await tester.pumpAndSettle();
    expect(opened, 1);
    expect(value, isTrue);
    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    expect(value, isFalse);
    expect(opened, 1);
  });
}
