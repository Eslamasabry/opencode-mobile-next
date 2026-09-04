import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/model_shortcuts.dart';

void main() {
  testWidgets('F2 cycles from a focused composer without changing its draft', (
    tester,
  ) async {
    final controller = TextEditingController(text: 'Keep my unfinished prompt');
    addTearDown(controller.dispose);
    final calls = <(bool, bool)>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ModelShortcuts(
            onCycle:
                ({bool reverse = false, bool favoritesOnly = false}) async {
                  calls.add((reverse, favoritesOnly));
                },
            child: TextField(controller: controller, autofocus: true),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.f2);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.f2);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    expect(calls, [(false, false), (true, false)]);
    expect(controller.text, 'Keep my unfinished prompt');
    expect(FocusManager.instance.primaryFocus?.hasFocus, isTrue);
  });

  testWidgets('a dialog above the chat prevents model shortcuts', (
    tester,
  ) async {
    var calls = 0;
    final navigator = GlobalKey<NavigatorState>();
    await tester.pumpWidget(
      MaterialApp(
        navigatorKey: navigator,
        home: Scaffold(
          body: ModelShortcuts(
            onCycle:
                ({bool reverse = false, bool favoritesOnly = false}) async {
                  calls++;
                },
            child: const TextField(autofocus: true),
          ),
        ),
      ),
    );
    await tester.pump();
    showDialog<void>(
      context: navigator.currentContext!,
      builder: (_) => const AlertDialog(content: TextField(autofocus: true)),
    );
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.f2);
    expect(calls, 0);
    navigator.currentState!.pop();
    await tester.pumpAndSettle();
  });

  testWidgets('touch menu offers recent and favorite cycling', (tester) async {
    final calls = <(bool, bool)>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: ModelCycleButton(
            hasRecent: true,
            hasFavorites: true,
            onCycle:
                ({bool reverse = false, bool favoritesOnly = false}) async {
                  calls.add((reverse, favoritesOnly));
                },
          ),
        ),
      ),
    );
    for (final label in [
      'Next recent model · F2',
      'Previous recent model · Shift+F2',
      'Next favorite model',
    ]) {
      await tester.tap(find.byTooltip('Switch model for this session'));
      await tester.pumpAndSettle();
      await tester.tap(find.text(label));
      await tester.pumpAndSettle();
    }
    expect(calls, [(false, false), (true, false), (false, true)]);
    expect(tester.takeException(), isNull);
  });
}
