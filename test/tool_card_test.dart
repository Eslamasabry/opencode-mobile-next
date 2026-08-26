import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

Future<void> _pumpTool(
  WidgetTester tester, {
  required String name,
  required ToolState state,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: ToolCard(toolName: name, state: state),
        ),
      ),
    ),
  );
}

void main() {
  testWidgets('renders the OpenCode shell contract without generic sections', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {'command': 'flutter test', 'workdir': '/workspace'},
        'output': 'All tests passed.\n<shell_metadata>ignored</shell_metadata>',
        'metadata': {'exit': 0},
      }, toolName: 'bash'),
    );

    expect(find.text('Shell'), findsOneWidget);
    expect(find.text('exit 0'), findsOneWidget);
    await tester.tap(find.text('Shell'));
    await tester.pump();
    expect(find.textContaining(r'$ flutter test'), findsOneWidget);
    expect(find.textContaining('All tests passed.'), findsOneWidget);
    expect(find.textContaining('shell_metadata'), findsNothing);
    expect(find.text('INPUT'), findsNothing);
    expect(find.text('OUTPUT'), findsNothing);
  });

  testWidgets('renders OpenCode edit metadata as a colored diff', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'edit',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {
          'filePath': '/workspace/lib/main.dart',
          'oldString': 'old line',
          'newString': 'new line',
        },
        'output': 'Edit applied successfully.',
        'metadata': {
          'filediff': {
            'patch': '@@ -1 +1 @@\n-old line\n+new line',
            'additions': 1,
            'deletions': 1,
          },
        },
      }, toolName: 'edit'),
    );

    expect(find.text('Edit'), findsOneWidget);
    expect(find.text('main.dart'), findsOneWidget);
    expect(find.text('+1 · −1'), findsOneWidget);
    await tester.tap(find.text('Edit'));
    await tester.pump();
    expect(find.text('+new line'), findsOneWidget);
    expect(find.text('-old line'), findsOneWidget);
  });

  testWidgets('renders OpenCode todo items as structured task rows', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'todowrite',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {
          'todos': [
            {'content': 'Inspect contract', 'status': 'completed'},
            {'content': 'Run simulator', 'status': 'in_progress'},
          ],
        },
        'output': 'Tasks updated.',
      }, toolName: 'todowrite'),
    );

    expect(find.text('Tasks'), findsOneWidget);
    expect(find.text('1/2 completed'), findsOneWidget);
    await tester.tap(find.text('Tasks'));
    await tester.pump();
    expect(find.text('Inspect contract'), findsOneWidget);
    expect(find.text('Run simulator'), findsOneWidget);
  });
}
