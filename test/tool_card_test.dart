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
  _serverStateTests();
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

  testWidgets('a running tool card carries a primary left accent', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState.fromJson(const {
        'status': 'running',
        'input': {'command': 'flutter test'},
      }, toolName: 'bash'),
    );
    final theme = Theme.of(tester.element(find.byType(ToolCard)));
    BoxDecoration accent() => tester
        .widget<AnimatedContainer>(find.byKey(const Key('tool-card-accent')))
        .decoration! as BoxDecoration;
    expect(accent().border!.top.width, 0);
    expect((accent().border! as Border).left.width, 2);
    expect((accent().border! as Border).left.color, theme.colorScheme.primary);

    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {'command': 'flutter test'},
        'output': 'ok',
      }, toolName: 'bash'),
    );
    await tester.pumpAndSettle();
    expect((accent().border! as Border).left.color, Colors.transparent);
  });
}

// ---------------------------------------------------------------------------
// Widened tool state: duration, pruned output, never-run calls.
// ---------------------------------------------------------------------------

void _serverStateTests() {
  test('formatToolDuration uses tenths under a minute and m/ss past it', () {
    expect(formatToolDuration(const Duration(milliseconds: 800)), '0.8s');
    expect(formatToolDuration(const Duration(milliseconds: 12400)), '12.4s');
    expect(formatToolDuration(const Duration(seconds: 65)), '1m 05s');
    expect(formatToolDuration(const Duration(minutes: 12, seconds: 3)), '12m 03s');
  });

  testWidgets('completed tools show their run time in the header', (
    tester,
  ) async {
    final start = DateTime.fromMillisecondsSinceEpoch(1000);
    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState(
        status: 'completed',
        input: const {'command': 'ls'},
        output: 'ok',
        startedAt: start,
        completedAt: start.add(const Duration(milliseconds: 12400)),
      ),
    );
    expect(find.byKey(const Key('tool-duration')), findsOneWidget);
    expect(find.text('12.4s'), findsOneWidget);
    expect(find.byKey(const Key('tool-pruned')), findsNothing);
    expect(find.byKey(const Key('tool-not-run')), findsNothing);
  });

  testWidgets('running tools show no duration yet', (tester) async {
    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState(
        status: 'running',
        input: const {'command': 'ls'},
        startedAt: DateTime.fromMillisecondsSinceEpoch(1000),
      ),
    );
    expect(find.byKey(const Key('tool-duration')), findsNothing);
  });

  testWidgets('pruned output is announced under the header', (tester) async {
    await _pumpTool(
      tester,
      name: 'read',
      state: ToolState(
        status: 'completed',
        input: const {'filePath': '/a/b.txt'},
        pruned: true,
      ),
    );
    expect(find.byKey(const Key('tool-pruned')), findsOneWidget);
    expect(find.text('Output pruned'), findsOneWidget);
  });

  testWidgets('never-run calls grey out with a Not run state', (tester) async {
    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState(
        status: 'pending',
        input: const {'command': 'rm -rf build'},
        executed: false,
      ),
    );
    expect(find.byKey(const Key('tool-not-run')), findsOneWidget);
    expect(find.text('Not run'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    final title = tester.widget<Text>(find.text('Shell'));
    expect(title.style?.decoration, isNot(TextDecoration.lineThrough));
    final semantics = tester.getSemantics(find.byType(InkWell).first);
    expect(semantics.label, contains('not run'));
  });
}
