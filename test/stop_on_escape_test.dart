import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/model_shortcuts.dart';

Widget _app({
  required bool enabled,
  required VoidCallback onStop,
  TextEditingController? controller,
}) => MaterialApp(
  home: Scaffold(
    body: StopOnEscape(
      enabled: enabled,
      onStop: onStop,
      child: Center(child: TextField(autofocus: true, controller: controller)),
    ),
  ),
);

void main() {
  testWidgets('Esc stops a running reply, even from the message box', (
    tester,
  ) async {
    var stops = 0;
    await tester.pumpWidget(_app(enabled: true, onStop: () => stops++));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    expect(stops, 1);
  });

  testWidgets('Esc does nothing when no reply is running', (tester) async {
    var stops = 0;
    await tester.pumpWidget(_app(enabled: false, onStop: () => stops++));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    expect(stops, 0);
  });

  testWidgets('Esc with a modifier held is left alone', (tester) async {
    var stops = 0;
    await tester.pumpWidget(_app(enabled: true, onStop: () => stops++));
    await tester.pump();
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    expect(stops, 0);
  });

  testWidgets('Esc belongs to the keyboard while text is being composed', (
    tester,
  ) async {
    var stops = 0;
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(enabled: true, onStop: () => stops++, controller: controller),
    );
    await tester.pump();
    controller.value = const TextEditingValue(
      text: 'ni',
      selection: TextSelection.collapsed(offset: 2),
      composing: TextRange(start: 0, end: 2),
    );
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    expect(stops, 0);
  });

  testWidgets('Esc closes a sheet above the chat without stopping it', (
    tester,
  ) async {
    var stops = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: StopOnEscape(
              enabled: true,
              onStop: () => stops++,
              child: TextButton(
                onPressed: () => showDialog<void>(
                  context: context,
                  builder: (_) => const AlertDialog(content: Text('Sheet')),
                ),
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    await tester.sendKeyEvent(LogicalKeyboardKey.escape);
    await tester.pumpAndSettle();
    expect(stops, 0);
    expect(find.text('Sheet'), findsNothing);
  });
}
