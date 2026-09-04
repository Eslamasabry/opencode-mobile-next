import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

Future<void> _pumpTool(
  WidgetTester tester, {
  required String name,
  required ToolState state,
  ValueChanged<String>? onOpenSession,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: SingleChildScrollView(
          child: ToolCard(
            toolName: name,
            state: state,
            onOpenSession: onOpenSession,
          ),
        ),
      ),
    ),
  );
}

void main() {
  _serverStateTests();
  _taskToolTests();
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
    BoxDecoration accent() =>
        tester
                .widget<AnimatedContainer>(
                  find.byKey(const Key('tool-card-accent')),
                )
                .decoration!
            as BoxDecoration;
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
    expect(
      formatToolDuration(const Duration(minutes: 12, seconds: 3)),
      '12m 03s',
    );
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
    final headerHeight = tester.getSize(find.byType(InkWell).first).height;
    expect(headerHeight, inInclusiveRange(48, 56));
    expect(find.byIcon(Icons.check_circle_outline_rounded), findsNothing);
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

// ---------------------------------------------------------------------------
// `task`: the parent agent delegating to a subagent.
// ---------------------------------------------------------------------------

void _taskToolTests() {
  const taskInput = {
    'subagent_type': 'explore',
    'description': 'Find X',
    'prompt': '# Do this\n\nLook at foo',
  };

  testWidgets('task cards show the agent chip, prompt and result', (
    tester,
  ) async {
    final opened = <String>[];
    await _pumpTool(
      tester,
      name: 'task',
      onOpenSession: opened.add,
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': taskInput,
        'output':
            '<task id="ses_child" state="completed">\n'
            '<task_result>\ndone\n</task_result>\n</task>',
        'metadata': {
          'parentSessionId': 'ses_parent',
          'sessionId': 'ses_child',
          'model': {'providerID': 'anthropic', 'modelID': 'claude-x'},
        },
      }, toolName: 'task'),
    );

    // Collapsed header: agent as title, description as subtitle, and the
    // child session's state as a trailing detail.
    expect(find.text('explore'), findsOneWidget);
    expect(find.text('Find X'), findsOneWidget);
    expect(find.text('completed'), findsOneWidget);
    expect(find.byKey(const Key('task-prompt')), findsNothing);

    await tester.tap(find.text('Find X'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('task-agent-chip')), findsOneWidget);
    expect(find.byKey(const Key('task-description')), findsOneWidget);
    expect(find.byKey(const Key('task-background-badge')), findsNothing);
    expect(find.text('Prompt from parent agent'), findsOneWidget);
    expect(find.textContaining('Do this', findRichText: true), findsWidgets);
    expect(
      find.textContaining('Look at foo', findRichText: true),
      findsWidgets,
    );
    // Three lines never need the collapse toggle.
    expect(find.byKey(const Key('task-prompt-toggle')), findsNothing);
    expect(find.byKey(const Key('task-result')), findsOneWidget);
    expect(find.textContaining('done', findRichText: true), findsWidgets);
    expect(find.textContaining('task_result'), findsNothing);
    expect(find.text('claude-x'), findsOneWidget);
    expect(find.byKey(const Key('task-working')), findsNothing);

    final open = find.byKey(const ValueKey('task-open-session'));
    expect(open, findsOneWidget);
    await tester.tap(open);
    expect(opened, ['ses_child']);
  });

  testWidgets('task cards hide the open-session action without a handler or '
      'child id', (tester) async {
    await _pumpTool(
      tester,
      name: 'task',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': taskInput,
        'output': '<task_result>done</task_result>',
        'metadata': {'sessionId': 'ses_child'},
      }, toolName: 'task'),
    );
    await tester.tap(find.text('Find X'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('task-open-session')), findsNothing);

    // Fresh tree so the second card starts collapsed again.
    await tester.pumpWidget(const SizedBox());
    await _pumpTool(
      tester,
      name: 'task',
      onOpenSession: (_) {},
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': taskInput,
        'output': '<task_result>done</task_result>',
      }, toolName: 'task'),
    );
    await tester.tap(find.text('Find X'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('task-open-session')), findsNothing);
  });

  testWidgets('long prompts collapse to a preview with a toggle', (
    tester,
  ) async {
    final prompt = List.generate(14, (i) => 'Line ${i + 1}').join('\n');
    await _pumpTool(
      tester,
      name: 'task',
      state: ToolState.fromJson({
        'status': 'completed',
        'input': {...taskInput, 'prompt': prompt},
        'output': '<task_result>done</task_result>',
      }, toolName: 'task'),
    );
    await tester.tap(find.text('Find X'));
    await tester.pumpAndSettle();

    expect(find.text('14 lines'), findsOneWidget);
    expect(find.textContaining('Line 8', findRichText: true), findsWidgets);
    expect(find.textContaining('Line 9', findRichText: true), findsNothing);
    final toggle = find.byKey(const Key('task-prompt-toggle'));
    expect(toggle, findsOneWidget);
    expect(find.text('Show full prompt'), findsOneWidget);

    await tester.tap(toggle);
    await tester.pumpAndSettle();
    expect(find.textContaining('Line 14', findRichText: true), findsWidgets);
    expect(find.text('Show less'), findsOneWidget);
  });

  testWidgets('a running task shows the subagent working line', (tester) async {
    await _pumpTool(
      tester,
      name: 'task',
      state: ToolState.fromJson(const {
        'status': 'running',
        'input': {...taskInput, 'background': true},
        'metadata': {'sessionId': 'ses_child', 'background': true},
      }, toolName: 'task'),
    );
    expect(find.text('background'), findsOneWidget);
    await tester.tap(find.text('Find X'));
    await tester.pump();
    expect(find.byKey(const Key('task-working')), findsOneWidget);
    expect(find.text('Subagent working…'), findsOneWidget);
    expect(find.byKey(const Key('task-background-badge')), findsOneWidget);
    expect(find.byKey(const Key('task-result')), findsNothing);
  });

  testWidgets('a failed task keeps the prompt and shows the error as result', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'task',
      state: ToolState.fromJson(const {
        'status': 'error',
        'input': taskInput,
        'error': '<task_error>Agent not found: "explore"</task_error>',
      }, toolName: 'task'),
    );
    // Error cards start expanded.
    expect(find.text('Prompt from parent agent'), findsOneWidget);
    expect(find.textContaining('Agent not found'), findsOneWidget);
    expect(find.textContaining('task_error'), findsNothing);
  });

  test('taskChildSessionId reads the server key and legacy spellings', () {
    ToolState withMeta(Map<String, dynamic> metadata) =>
        ToolState(status: 'completed', metadata: metadata);
    expect(taskChildSessionId(withMeta({'sessionId': 'a'})), 'a');
    expect(taskChildSessionId(withMeta({'sessionID': 'b'})), 'b');
    expect(taskChildSessionId(withMeta({'session_id': 'c'})), 'c');
    expect(taskChildSessionId(withMeta({'jobId': 'j'})), isNull);
    expect(taskChildSessionId(ToolState(status: 'completed')), isNull);
  });
}
