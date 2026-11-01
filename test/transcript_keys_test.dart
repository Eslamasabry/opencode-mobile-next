import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/model_shortcuts.dart';

Widget _app(List<TranscriptKey> seen, List<bool> variants) => MaterialApp(
  home: Scaffold(
    body: ModelShortcuts(
      onCycle: ({bool reverse = false, bool favoritesOnly = false}) async {},
      onCycleVariant: ({bool reverse = false}) async => variants.add(reverse),
      onTranscriptKey: seen.add,
      child: const Center(child: TextField(autofocus: true, key: Key('field'))),
    ),
  ),
);

void main() {
  testWidgets('page and end keys stay with the text field while typing', (
    tester,
  ) async {
    final seen = <TranscriptKey>[];
    await tester.pumpWidget(_app(seen, []));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.pageUp);
    await tester.sendKeyEvent(LogicalKeyboardKey.end);
    expect(seen, isEmpty);
  });

  testWidgets('page and end keys move the transcript outside a field', (
    tester,
  ) async {
    final seen = <TranscriptKey>[];
    await tester.pumpWidget(_app(seen, []));
    await tester.pump();
    FocusManager.instance.primaryFocus?.unfocus();
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.pageUp);
    await tester.sendKeyEvent(LogicalKeyboardKey.pageDown);
    await tester.sendKeyEvent(LogicalKeyboardKey.home);
    await tester.sendKeyEvent(LogicalKeyboardKey.end);
    expect(seen, [
      TranscriptKey.pageUp,
      TranscriptKey.pageDown,
      TranscriptKey.oldest,
      TranscriptKey.latest,
    ]);
  });

  testWidgets('F4 and Shift+F4 cycle the thinking level, even while typing', (
    tester,
  ) async {
    final variants = <bool>[];
    await tester.pumpWidget(_app([], variants));
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.f4);
    await tester.sendKeyDownEvent(LogicalKeyboardKey.shiftLeft);
    await tester.sendKeyEvent(LogicalKeyboardKey.f4);
    await tester.sendKeyUpEvent(LogicalKeyboardKey.shiftLeft);
    expect(variants, [false, true]);
  });
}
