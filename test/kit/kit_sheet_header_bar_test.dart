// The sheet's picker layout (docs/ux-system/kit-api/KitSheet.md "Picker
// layout"): a leading back or close control, a trailing menu, one quiet line
// under the title, the pinned bar (text button at the start, the primary at
// the end), and a step that swaps the frame's content in place.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/kit/kit_menu.dart';
import 'package:opencode_mobile/ui/kit/kit_sheet.dart';

import 'kit_harness.dart';

class _Picker extends StatefulWidget {
  const _Picker({required this.log, this.longName = false});

  final List<String> log;
  final bool longName;

  @override
  State<_Picker> createState() => _PickerState();
}

class _PickerState extends State<_Picker> {
  bool _second = false;
  bool _hidden = false;

  @override
  Widget build(BuildContext context) => KitSheet(
    handle: false,
    step: _second ? 'second' : 'first',
    title: _second ? 'New project' : 'Projects',
    subtitle: _second ? 'In Projects' : null,
    leading: _second
        ? KitAction(
            label: 'Back',
            onPressed: () => setState(() => _second = false),
          )
        : KitAction(label: 'Close', onPressed: () => widget.log.add('close')),
    menu: _second
        ? const []
        : [
            KitMenuItem(
              label: 'Show hidden folders',
              checked: _hidden,
              onSelected: () => setState(() => _hidden = !_hidden),
            ),
          ],
    headerLine: _second ? null : const Text('This phone'),
    bar: !_second,
    secondary: _second
        ? null
        : KitAction(
            label: 'New project',
            onPressed: () => setState(() => _second = true),
          ),
    primary: KitAction(
      label: _second
          ? 'Create and open'
          : widget.longName
          ? 'Open a folder with a very long name that cannot fit the bar'
          : 'Open Projects',
      onPressed: () => widget.log.add(_second ? 'create' : 'open'),
    ),
    child: Text(_hidden ? 'hidden shown' : 'rows'),
  );
}

void main() {
  testWidgets('leading closes at the first step and goes back at the next', (
    tester,
  ) async {
    final log = <String>[];
    final context = await pumpKitHost(tester);
    unawaited(
      showKitFramedSheet<void>(context, builder: (_) => _Picker(log: log)),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('kit-sheet-close')), findsNothing);
    expect(find.byTooltip('Close'), findsOneWidget);
    expect(find.text('This phone'), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-sheet-bar')), findsOneWidget);

    await tester.tap(find.byTooltip('Close'));
    expect(log, ['close']);

    // The step swaps in place: one sheet, no second route.
    await tester.tap(find.text('New project'));
    await tester.pumpAndSettle();
    expect(find.byType(KitSheet), findsOneWidget);
    expect(find.text('In Projects'), findsOneWidget);
    expect(find.text('rows'), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-sheet-bar')), findsNothing);
    expect(find.byKey(const ValueKey('kit-sheet-menu')), findsNothing);
    await tester.tap(find.text('Create and open'));
    expect(log, ['close', 'create']);

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.text('Open Projects'), findsOneWidget);
  });

  testWidgets('the menu holds checkable items', (tester) async {
    final context = await pumpKitHost(tester);
    unawaited(
      showKitFramedSheet<void>(context, builder: (_) => _Picker(log: [])),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('kit-sheet-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Show hidden folders'));
    await tester.pumpAndSettle();
    expect(find.text('hidden shown'), findsOneWidget);
  });

  testWidgets('a long primary is ellipsized and the text button stays', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final context = await pumpKitHost(tester);
    unawaited(
      showKitFramedSheet<void>(
        context,
        builder: (_) => _Picker(log: [], longName: true),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    final bar = tester.getRect(find.byKey(const ValueKey('kit-sheet-bar')));
    final secondary = tester.getRect(find.text('New project'));
    expect(secondary.left, greaterThanOrEqualTo(bar.left));
    expect(find.text('New project'), findsOneWidget);
  });
}
