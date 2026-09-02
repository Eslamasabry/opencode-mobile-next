import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/widgets/agent_blocks.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';

/// Captures Clipboard.setData payloads so tests can assert on copies.
List<String> _captureClipboard(WidgetTester tester) {
  final copies = <String>[];
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    SystemChannels.platform,
    (call) async {
      if (call.method == 'Clipboard.setData') {
        copies.add((call.arguments as Map)['text'] as String);
      }
      return null;
    },
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      null,
    ),
  );
  return copies;
}

Future<void> _pump(
  WidgetTester tester,
  String markdown, {
  ValueChanged<String>? onChoice,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: MarkdownText(markdown, onChoice: onChoice),
        ),
      ),
    ),
  );
}

void main() {
  testWidgets('```choices renders tappable cards that report the option', (
    tester,
  ) async {
    final chosen = <String>[];
    await _pump(
      tester,
      'Pick one:\n\n```choices\nAdd tests first\n\nRefactor the parser\n```',
      onChoice: chosen.add,
    );

    expect(find.byKey(const Key('agent-choices-block')), findsOneWidget);
    expect(find.text('Add tests first'), findsOneWidget);
    expect(find.text('Refactor the parser'), findsOneWidget);
    // Blank lines never become options.
    expect(find.byKey(const Key('agent-choice-2')), findsNothing);
    // Cards meet the 48dp touch floor.
    expect(
      tester.getSize(find.byKey(const Key('agent-choice-0'))).height,
      greaterThanOrEqualTo(48),
    );

    await tester.tap(find.byKey(const Key('agent-choice-1')));
    await tester.pump();
    expect(chosen, ['Refactor the parser']);
    expect(find.byType(CodeBlock), findsNothing);
  });

  testWidgets('```choices without a handler copies the option and says so', (
    tester,
  ) async {
    final copies = _captureClipboard(tester);
    await _pump(tester, '```CHOICES\nShip it\n```');

    await tester.tap(find.byKey(const Key('agent-choice-0')));
    await tester.pumpAndSettle();

    expect(copies, ['Ship it']);
    expect(find.text('Copied. Paste it into the composer'), findsOneWidget);
  });

  testWidgets('```checklist renders read-only items with done state', (
    tester,
  ) async {
    await _pump(
      tester,
      '```checklist\n[x] Parse the fence\n[ ] Render the block\n- [X] Ship\n```',
    );

    expect(find.byKey(const Key('agent-checklist-block')), findsOneWidget);
    expect(find.text('Parse the fence'), findsOneWidget);
    expect(find.text('Render the block'), findsOneWidget);
    expect(find.text('Ship'), findsOneWidget);
    expect(find.byKey(const Key('agent-check-done')), findsNWidgets(2));
    expect(find.byKey(const Key('agent-check-open')), findsOneWidget);
    // Read-only: nothing to tap, and done items keep their text legible.
    expect(find.byType(Checkbox), findsNothing);
    final done = tester.widget<Text>(find.text('Parse the fence'));
    expect(done.style?.decoration, isNot(TextDecoration.lineThrough));
  });

  testWidgets('```command renders mono lines with 48dp copy buttons', (
    tester,
  ) async {
    final copies = _captureClipboard(tester);
    await _pump(tester, '```command\nflutter pub get\nflutter test\n```');

    expect(find.byKey(const Key('agent-command-block')), findsOneWidget);
    expect(find.text('Run on your computer'), findsOneWidget);
    expect(find.text('flutter pub get'), findsOneWidget);
    expect(find.text('flutter test'), findsOneWidget);

    final copy = find.byKey(const Key('agent-command-copy-1'));
    expect(tester.getSize(copy).height, greaterThanOrEqualTo(48));
    expect(tester.getSize(copy).width, greaterThanOrEqualTo(48));
    await tester.tap(copy);
    await tester.pumpAndSettle();
    expect(copies, ['flutter test']);
  });

  testWidgets('unknown fences keep the plain code block', (tester) async {
    await _pump(tester, '```dart\nvoid main() {}\n```');

    expect(find.byType(CodeBlock), findsOneWidget);
    expect(find.byKey(const Key('agent-choices-block')), findsNothing);
    expect(find.byKey(const Key('agent-checklist-block')), findsNothing);
    expect(find.byKey(const Key('agent-command-block')), findsNothing);
    expect(AgentBlockKinds.matches('dart'), isFalse);
    expect(AgentBlockKinds.matches('Checklist'), isTrue);
  });
}
