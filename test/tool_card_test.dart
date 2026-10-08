// ToolCard (embedded-tool-card, chat-2): the host adapter that maps a server
// ToolState to a KitToolRow and fills its body with kit parts. Asserts what
// the person sees: the words on the line, the state (never "running" while
// it waits for them), capped output with "Open full output", exit codes in
// words, and the plain sub-agent line that opens its conversation.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/kit_status_mark.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

import 'support/v2_subagent_fixture.dart';

Future<void> _pumpTool(
  WidgetTester tester, {
  required String name,
  required ToolState state,
  ValueChanged<String>? onOpenSession,
  ValueChanged<String>? onRerunCommand,
  bool embedded = false,
  bool waitingForYou = false,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: child!,
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: ToolCard(
            toolName: name,
            state: state,
            embedded: embedded,
            waitingForYou: waitingForYou,
            onOpenSession: onOpenSession,
            onRerunCommand: onRerunCommand,
          ),
        ),
      ),
    ),
  );
}

Finder _text(String value) => find.textContaining(value, findRichText: true);

ToolState _shell({
  String status = 'completed',
  int? exit = 0,
  String output = 'All tests passed.',
  String command = 'flutter test',
}) => ToolState.fromJson({
  'status': status,
  'input': {'command': command, 'workdir': '/workspace'},
  if (status == 'completed') 'output': output,
  'metadata': {'exit': ?exit},
}, toolName: 'bash');

void main() {
  _shellTests();
  _stateTests();
  _editAndTodoTests();
  _taskTests();
}

void _shellTests() {
  testWidgets('Claude Code looking up tools reads "Load tools"', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'toolsearch',
      state: ToolState.fromJson({
        'status': 'completed',
        'input': {'query': 'select:mcp__oc-ui__show'},
        'output': 'Loaded.',
        'metadata': <String, Object?>{},
      }, toolName: 'toolsearch'),
    );
    expect(find.text('Load tools'), findsOneWidget);
    expect(find.text('toolsearch'), findsNothing);
  });

  testWidgets('a tool an MCP server adds reads in words with its server', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'mcp__github__create_issue',
      state: ToolState.fromJson({
        'status': 'completed',
        'input': {'title': 'Bug'},
        'output': 'Created.',
        'metadata': <String, Object?>{},
      }, toolName: 'mcp__github__create_issue'),
    );
    expect(_text('create issue'), findsOneWidget);
    expect(_text('github'), findsOneWidget);
    expect(_text('mcp__'), findsNothing);
  });

  // Claude Code's background-task notice (owner phone, 2026-10-08) read
  // "task_notification" under a wrench.
  for (final (status, words) in [
    ('completed', 'Background task finished'),
    ('failed', 'Background task failed'),
    ('killed', 'Background task stopped'),
  ]) {
    testWidgets('a background-task notice reads "$words"', (tester) async {
      await _pumpTool(
        tester,
        name: 'task_notification',
        state: ToolState.fromJson({
          'status': 'completed',
          'input': {'status': status, 'summary': 'Release build'},
          'output': 'done',
          'metadata': <String, Object?>{},
        }, toolName: 'task_notification'),
      );
      expect(_text(words), findsOneWidget);
      expect(_text('Release build'), findsOneWidget);
      expect(_text('task_notification'), findsNothing);
    });
  }

  testWidgets('an unknown tool id reads as words, not snake_case', (
    tester,
  ) async {
    for (final (name, words) in [
      ('render_mermaid_diagram', 'Render mermaid diagram'),
      ('ExitWorktree', 'Exit worktree'),
    ]) {
      await _pumpTool(
        tester,
        name: name,
        state: ToolState.fromJson({
          'status': 'completed',
          'title': name,
          'input': <String, Object?>{},
          'output': 'ok',
          'metadata': <String, Object?>{},
        }, toolName: name),
      );
      expect(_text(words), findsOneWidget);
      expect(_text(name), findsNothing);
    }
  });

  // The agent card tool before (or instead of) its card: a running call, or
  // one the connection draws no card for. Its server sets the raw name as the
  // call's title, which must not win either.
  for (final name in ['oc-ui_show', 'mcp__oc-ui__show']) {
    testWidgets('the agent card tool reads "Show card", never "$name"', (
      tester,
    ) async {
      await _pumpTool(
        tester,
        name: name,
        state: ToolState.fromJson({
          'status': 'running',
          'title': name,
          'input': {'id': 'plan', 'title': 'Pick a plan', 'body': []},
          'metadata': <String, Object?>{},
        }, toolName: name),
      );
      expect(_text('Show card'), findsOneWidget);
      expect(_text('Pick a plan'), findsOneWidget);
      expect(_text('oc-ui'), findsNothing);
    });
  }

  testWidgets('a passing command says so in words, not "exit 0"', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'bash',
      state: _shell(
        output: 'All tests passed.\n<shell_metadata>ignored</shell_metadata>',
      ),
    );
    expect(find.text('Shell'), findsOneWidget);
    expect(find.text('exit 0'), findsNothing);
    expect(find.text('Failed'), findsNothing);
    await tester.tap(find.text('Shell'));
    await tester.pump();
    expect(find.byKey(const Key('tool-shell-command')), findsOneWidget);
    expect(_text('All tests passed.'), findsWidgets);
    expect(_text('Passed · exit code 0'), findsOneWidget);
    expect(_text('shell_metadata'), findsNothing);
  });

  testWidgets('a non-zero exit is a failed step with its code in words', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'bash',
      state: _shell(exit: 1, output: '1 test failed'),
    );
    expect(find.text('Failed'), findsOneWidget);
    await tester.tap(find.text('Shell'));
    await tester.pump();
    expect(_text('Failed · exit code 1'), findsOneWidget);
  });

  testWidgets('long output is capped at 12 lines with Open full output', (
    tester,
  ) async {
    final output = List.generate(40, (i) => 'line ${i + 1}').join('\n');
    await _pumpTool(
      tester,
      name: 'bash',
      state: _shell(output: output),
    );
    await tester.tap(find.text('Shell'));
    await tester.pump();
    expect(_text('line 12'), findsWidgets);
    expect(_text('line 13'), findsNothing);
    expect(find.text('Open full output'), findsOneWidget);
  });

  testWidgets('Run this command again appears only when the host offers it', (
    tester,
  ) async {
    await _pumpTool(tester, name: 'bash', state: _shell());
    await tester.tap(find.text('Shell'));
    await tester.pump();
    expect(find.text('Run this command again'), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
    final reruns = <String>[];
    await _pumpTool(
      tester,
      name: 'bash',
      state: _shell(),
      onRerunCommand: reruns.add,
    );
    await tester.tap(find.text('Shell'));
    await tester.pump();
    await tester.ensureVisible(find.text('Run this command again'));
    await tester.tap(find.text('Run this command again'));
    expect(reruns, ['flutter test']);
  });

  testWidgets('an embedded failure starts open with the error output', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'bash',
      embedded: true,
      state: ToolState.fromJson(const {
        'status': 'error',
        'input': {'command': 'npm test'},
        'error': 'Error: Checkout test failed',
      }, toolName: 'bash'),
    );
    expect(find.byKey(const Key('embedded-tool-row')), findsOneWidget);
    expect(find.byKey(const Key('embedded-tool-error-output')), findsOneWidget);
    expect(_text('Checkout test failed'), findsWidgets);
    expect(_text('Error:'), findsNothing);
  });
}

void _stateTests() {
  test('formatToolDuration uses tenths under a minute and m/ss past it', () {
    expect(formatToolDuration(const Duration(milliseconds: 800)), '0.8s');
    expect(formatToolDuration(const Duration(milliseconds: 12400)), '12.4s');
    expect(formatToolDuration(const Duration(seconds: 65)), '1m 05s');
    expect(
      formatToolDuration(const Duration(minutes: 12, seconds: 3)),
      '12m 03s',
    );
  });

  testWidgets('a finished step shows how long it took; a running one not', (
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
    expect(find.text('12 seconds'), findsOneWidget);

    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState(
        status: 'running',
        input: const {'command': 'ls'},
        startedAt: start,
      ),
    );
    expect(find.text('12 seconds'), findsNothing);
    expect(
      find.byWidgetPredicate(
        (w) => w is KitStatusMark && w.state == KitMarkState.working,
      ),
      findsOneWidget,
    );
  });

  testWidgets('a call blocked on the person says Waiting for you, no spinner', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'bash',
      waitingForYou: true,
      state: ToolState(status: 'running', input: const {'command': 'rm x'}),
    );
    expect(find.text('Waiting for you'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(
      find.byWidgetPredicate(
        (w) => w is KitStatusMark && w.state == KitMarkState.working,
      ),
      findsNothing,
    );

    // A running question tool always waits for the person's answer.
    await _pumpTool(
      tester,
      name: 'question',
      state: ToolState(
        status: 'running',
        input: const {
          'questions': [
            {'question': 'Which branch?'},
          ],
        },
      ),
    );
    expect(find.text('Waiting for you'), findsOneWidget);
  });

  testWidgets('pruned output is said inside the opened step', (tester) async {
    await _pumpTool(
      tester,
      name: 'read',
      state: ToolState(
        status: 'completed',
        input: const {'filePath': '/a/b.txt'},
        pruned: true,
      ),
    );
    expect(find.byKey(const Key('tool-pruned')), findsNothing);
    await tester.tap(find.text('Read'));
    await tester.pump();
    expect(find.byKey(const Key('tool-pruned')), findsOneWidget);
    expect(find.text('Output pruned'), findsOneWidget);
  });

  testWidgets('a call the server never ran says Not run, never failed', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'bash',
      state: ToolState(
        status: 'pending',
        input: const {'command': 'rm -rf build'},
        executed: false,
      ),
    );
    expect(find.text('Not run'), findsOneWidget);
    expect(find.text('Failed'), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    final row = tester.widget<KitToolRow>(find.byType(KitToolRow));
    expect(row.status, KitToolStatus.notRun);
  });

  testWidgets('a produced file stays visible under the folded line', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'render',
      state: ToolState(
        status: 'completed',
        input: const {'target': 'report'},
        outputFiles: [
          ToolOutputFile(path: '/tmp/report.csv', mimeType: 'text/csv'),
        ],
      ),
    );
    expect(find.byKey(const Key('tool-output-file')), findsOneWidget);
    expect(find.text('report.csv'), findsOneWidget);
  });

  testWidgets('copying output uses the kit copy of the whole text', (
    tester,
  ) async {
    final writes = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      SystemChannels.platform,
      (call) async {
        if (call.method == 'Clipboard.setData') {
          writes.add((call.arguments as Map)['text'] as String);
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
    final output = List.generate(30, (i) => 'row $i').join('\n');
    await _pumpTool(
      tester,
      name: 'bash',
      state: _shell(output: output),
    );
    await tester.tap(find.text('Shell'));
    await tester.pump();
    final copies = find.bySemanticsLabel(RegExp('^Copy'));
    expect(copies, findsWidgets);
    await tester.tap(copies.last);
    await tester.pump();
    expect(writes, contains(output));
    await tester.pump(const Duration(seconds: 3));
  });
}

void _editAndTodoTests() {
  testWidgets('an edit shows +n −n on the line and its diff when opened', (
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
    expect(_text('+1'), findsWidgets);
    expect(_text('−1'), findsWidgets);
    await tester.tap(find.text('Edit'));
    await tester.pump();
    expect(_text('new line'), findsWidgets);
    expect(_text('old line'), findsWidgets);
  });

  testWidgets('the plan step reads one count, the same as its list', (
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
            {'content': 'Dropped idea', 'status': 'cancelled'},
          ],
        },
        'output': 'Tasks updated.',
      }, toolName: 'todowrite'),
    );
    // Cancelled tasks are not tracked: 1 of 2, on the line and in the list.
    expect(_text('1 of 2 done'), findsOneWidget);
    await tester.tap(find.byType(KitToolRow));
    await tester.pump();
    expect(find.text('Inspect contract'), findsOneWidget);
    expect(find.text('Run simulator'), findsOneWidget);
    expect(_text('1 of 2 done'), findsNWidgets(2));
  });
}

void _taskTests() {
  const taskInput = {
    'subagent_type': 'explore',
    'description': 'Find X',
    'prompt': '# Do this\n\nLook at foo',
  };

  testWidgets('a sub-agent the host can open is the plain agent line', (
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
        'metadata': {'sessionId': 'ses_child'},
      }, toolName: 'task'),
    );
    expect(_text('Delegated to explore · Done'), findsOneWidget);
    expect(find.text('Find X'), findsOneWidget);
    expect(find.byKey(const Key('task-prompt')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('task-open-session')));
    expect(opened, ['ses_child']);
  });

  testWidgets('a v2 background launch says it runs in the background', (
    tester,
  ) async {
    String? opened;
    await _pumpTool(
      tester,
      name: 'subagent',
      state: v2SubagentState(),
      onOpenSession: (id) => opened = id,
    );
    expect(_text('Delegated to explore'), findsOneWidget);
    expect(_text('Started in the background'), findsOneWidget);
    expect(_text('DO NOT sleep'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('task-open-session')));
    expect(opened, 'ses_child_v2');
  });

  testWidgets('without a conversation to open, the step folds prompt and '
      'result', (tester) async {
    await _pumpTool(
      tester,
      name: 'task',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': taskInput,
        'output': '<task_result>done</task_result>',
        'metadata': {
          'sessionId': 'ses_child',
          'model': {'providerID': 'anthropic', 'modelID': 'claude-x'},
        },
      }, toolName: 'task'),
    );
    expect(find.text('Delegated to explore'), findsOneWidget);
    expect(find.byKey(const ValueKey('task-open-session')), findsNothing);
    await tester.tap(find.text('Delegated to explore'));
    await tester.pump();
    expect(find.byKey(const Key('task-description')), findsOneWidget);
    expect(find.text('Model · claude-x'), findsOneWidget);
    expect(find.text('Prompt from parent agent'), findsOneWidget);
    expect(_text('Look at foo'), findsNothing);
    expect(find.byKey(const Key('task-result')), findsOneWidget);
    expect(_text('done'), findsWidgets);
    expect(_text('task_result'), findsNothing);
  });

  testWidgets('a running delegation says working; a failed one starts open', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'subagent',
      state: v2SubagentState(status: 'running', background: false),
    );
    await tester.tap(find.byType(KitToolRow));
    await tester.pump();
    expect(find.byKey(const Key('task-working')), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
    await _pumpTool(
      tester,
      name: 'task',
      state: ToolState.fromJson(const {
        'status': 'error',
        'input': taskInput,
        'error': '<task_error>Agent not found: "explore"</task_error>',
      }, toolName: 'task'),
    );
    expect(find.text('Failed'), findsOneWidget);
    expect(find.text('Prompt from parent agent'), findsOneWidget);
    expect(_text('Agent not found'), findsWidgets);
    expect(_text('task_error'), findsNothing);
  });

  testWidgets('a sub-agent the server did not execute is Not run, no open', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'subagent',
      state: v2SubagentState(
        status: 'running',
        background: false,
        executed: false,
      ),
      onOpenSession: (_) {},
    );
    expect(find.text('Not run'), findsOneWidget);
    expect(find.byKey(const ValueKey('task-open-session')), findsNothing);
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
