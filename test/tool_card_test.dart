// ToolCard (embedded-tool-card, chat-2): the host adapter that maps a server
// ToolState to a KitToolRow and fills its body with kit parts. Asserts what
// the person sees: the words on the line, the state (never "running" while
// it waits for them), capped output with "Open full output", exit codes in
// words, and the plain sub-agent line that opens its conversation.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_tool_row.dart';
import 'package:opencode_mobile/ui/kit/kit_details_fold.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';
import 'package:opencode_mobile/ui/kit/kit_key_value.dart';
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

/// [value] in a line whose name the app isolated for right-to-left text
/// (the U+2066..U+2069 marks around "researcher" in "Message to …").
Finder _named(String value) => find.byWidgetPredicate(
  (widget) =>
      widget is RichText &&
      widget.text
          .toPlainText()
          .replaceAll(RegExp('[\u2066-\u2069]'), '')
          .contains(value),
);

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
  _agentMessageTests();
}

// Owner phone, 2026-10-27: Claude Code's SendMessage drew the generic table,
// To, Summary, Message, Type, Recipient and Content, the recipient and the
// text each twice and the text right-aligned in the value column.
void _agentMessageTests() {
  const text =
      'Start on task #1: read the Browse suites, then tell me which tests '
      'still fail after the last edit.';
  ToolState message(String to) => ToolState.fromJson({
    'status': 'completed',
    'input': {'to': to, 'summary': 'assign task 1', 'message': text},
    'output': {'type': 'message', 'recipient': to, 'content': text},
    'metadata': <String, Object?>{},
  }, toolName: 'sendmessage');

  testWidgets('a message to an agent says who, and its text once', (
    tester,
  ) async {
    await _pumpTool(tester, name: 'sendmessage', state: message('researcher'));
    expect(_named('Message to researcher'), findsOneWidget);
    expect(_text('assign task 1'), findsOneWidget);
    await tester.tap(_named('Message to researcher'));
    await tester.pumpAndSettle();
    expect(_text('Start on task #1'), findsOneWidget);
    for (final label in ['To', 'Recipient', 'Content', 'Message', 'Type']) {
      expect(find.text(label), findsNothing, reason: label);
    }
    expect(find.byType(KitKeyValue), findsNothing);
    // Readable prose, from the start of the line.
    final left = tester.getTopLeft(_text('Start on task #1').first).dx;
    expect(left, lessThan(100));
  });

  testWidgets('a recipient that is an id is never the title', (tester) async {
    await _pumpTool(
      tester,
      name: 'sendmessage',
      state: message('a1b2c3d4e5f60718'),
    );
    expect(_text('assign task 1'), findsOneWidget);
    expect(_text('a1b2c3d4e5f60718'), findsNothing);
    await tester.tap(find.textContaining('assign task 1'));
    await tester.pumpAndSettle();
    expect(_text('Start on task #1'), findsOneWidget);
    expect(find.byType(KitDetailsFold), findsOneWidget);
  });

  testWidgets('a background agent writing back names the main conversation', (
    tester,
  ) async {
    await _pumpTool(tester, name: 'sendmessage', state: message('main'));
    expect(_named('Message to the main conversation'), findsOneWidget);
  });

  testWidgets('any tool: a value the call repeats shows once', (tester) async {
    await _pumpTool(
      tester,
      name: 'mcp__team__notify',
      state: ToolState.fromJson({
        'status': 'completed',
        'input': {'to': 'reviewer', 'recipient': 'reviewer', 'note': 'Ship it'},
        'output': {'recipient': 'reviewer', 'state': 'queued'},
        'metadata': <String, Object?>{},
      }, toolName: 'mcp__team__notify'),
    );
    await tester.tap(find.textContaining('notify'));
    await tester.pumpAndSettle();
    expect(find.text('reviewer'), findsOneWidget);
    expect(find.text('Recipient'), findsNothing);
    expect(find.text('queued'), findsOneWidget);
  });
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

  // Owner phone, 2026-10-11: a task cut off with its session read
  // "Background task finished" and said its one sentence three times; on
  // 2026-10-27 it still said it twice under "Background task ended".
  testWidgets('a task cut off with its session says so once, as stopped', (
    tester,
  ) async {
    const sentence =
        "Background shell command didn't finish before the previous session "
        'ended';
    await _pumpTool(
      tester,
      name: 'task_notification',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {'name': sentence},
        'output': sentence,
        'metadata': <String, Object?>{},
      }, toolName: 'task_notification'),
    );
    expect(
      _text('Background task stopped when the last conversation ended'),
      findsOneWidget,
    );
    expect(_text('Background task finished'), findsNothing);
    expect(_text(sentence), findsNothing);
    expect(find.text('Stopped'), findsOneWidget);
    await tester.tap(find.textContaining('Background task stopped'));
    await tester.pump();
    expect(_text(sentence), findsNothing);
    expect(find.text('Name'), findsNothing);
  });

  testWidgets('a named background agent reads its name and its end once', (
    tester,
  ) async {
    const sentence =
        'Background agent "Fix login" didn\'t finish before the previous '
        'session ended';
    await _pumpTool(
      tester,
      name: 'task_notification',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {'summary': sentence, 'status': 'stopped'},
        'output': '',
        'metadata': <String, Object?>{},
      }, toolName: 'task_notification'),
    );
    expect(
      _named('Fix login stopped when the last conversation ended'),
      findsOneWidget,
    );
    expect(_text('Background task'), findsNothing);
    expect(_text(sentence), findsNothing);
    expect(find.text('Stopped'), findsOneWidget);
  });

  testWidgets('a named task that finished reads "<name> finished"', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'task_notification',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {'summary': 'Agent "Review" completed', 'status': 'completed'},
        'output': 'Two findings, both fixed.',
        'metadata': <String, Object?>{},
      }, toolName: 'task_notification'),
    );
    expect(_named('Review finished'), findsOneWidget);
    await tester.tap(_named('Review finished'));
    await tester.pump();
    expect(_text('Agent "Review" completed'), findsNothing);
    expect(_text('Two findings, both fixed.'), findsOneWidget);
  });

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

  // Owner's phone, 2026-10-14: a finished card call the card could not be
  // drawn for showed its input as rows and the model's reply as details.
  for (final status in ['completed', 'error']) {
    testWidgets('a $status card call with no card is one quiet line', (
      tester,
    ) async {
      await _pumpTool(
        tester,
        name: 'oc-ui_show',
        state: ToolState.fromJson({
          'status': status,
          'title': 'oc-ui_show',
          'input': {
            'v': 1,
            'id': 'financehub-interactive-preview',
            'title': 'FinanceHub \u2014 interactive answer preview',
            'body': [
              {'type': 'text', 'text': 'Hello'},
            ],
          },
          'output': status == 'completed'
              ? 'Card accepted for display in OpenCode Mobile. If it asks a '
                    'question, end your turn and wait for the person\'s next '
                    'message tagged [oc-ui answer <id>]. This call does not '
                    'return their answer.'
              : 'Agent card unavailable or invalid.',
          'metadata': <String, Object?>{},
        }, toolName: 'oc-ui_show'),
      );
      expect(
        _text(
          status == 'completed' ? 'Showed a card' : "Couldn't show this card",
        ),
        findsOneWidget,
      );
      expect(
        _text('FinanceHub \u2014 interactive answer preview'),
        findsOneWidget,
      );
      // Nothing opens: no input rows, no model-facing reply, no details.
      expect(find.byType(KitToolRow), findsOneWidget);
      await tester.tap(find.byType(KitToolRow));
      await tester.pump(const Duration(milliseconds: 400));
      expect(_text('Id'), findsNothing);
      expect(_text('V'), findsNothing);
      expect(find.textContaining('Card accepted'), findsNothing);
      expect(find.textContaining('unavailable or invalid'), findsNothing);
      expect(find.text('Technical details'), findsNothing);
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
    expect(find.byKey(const Key('kit-step-command')), findsOneWidget);
    expect(_text('\$ flutter test'), findsOneWidget);
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

  testWidgets('long output shows its first 6 lines and Show all N lines', (
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
    expect(_text('line 6'), findsWidgets);
    expect(_text('line 7'), findsNothing);
    expect(find.text('Show all 40 lines'), findsOneWidget);
    await tester.tap(find.byKey(const Key('kit-step-show-all')));
    await tester.pump();
    expect(_text('line 40'), findsWidgets);
    expect(find.text('Show all 40 lines'), findsNothing);
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

  for (final status in ['running', 'pending']) {
    testWidgets('Run this command again waits for a $status command', (
      tester,
    ) async {
      await _pumpTool(
        tester,
        name: 'bash',
        state: _shell(status: status),
        onRerunCommand: (_) {},
      );
      await tester.tap(find.text('Shell'));
      await tester.pump();
      expect(find.text('Run this command again'), findsNothing);
    });
  }

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
    expect(find.byKey(const Key('kit-step-command')), findsOneWidget);
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
    expect(find.byKey(const ValueKey('kit-step-light')), findsOneWidget);
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

  testWidgets('a read with no content shows its line count, no empty box', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'read',
      state: ToolState(
        status: 'completed',
        input: const {'filePath': '/a/b.txt', 'limit': 70},
      ),
    );
    await tester.tap(find.text('Read'));
    await tester.pump();
    expect(find.text('(no output)'), findsNothing);
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

  testWidgets('the copy icon in the command box copies the command', (
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
    await tester.tap(find.byKey(const Key('kit-step-command-copy')));
    await tester.pump();
    expect(writes, ['flutter test']);
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

  testWidgets('an opened write shows its text as the numbered added diff', (
    tester,
  ) async {
    await _pumpTool(
      tester,
      name: 'write',
      state: ToolState.fromJson(const {
        'status': 'completed',
        'input': {
          'filePath': '/workspace/test/tour_test.dart',
          'content': 'library;\n\nimport \'dart:io\';',
        },
        'output': 'File written.',
        'metadata': <String, Object?>{},
      }, toolName: 'write'),
    );
    expect(find.text('Write'), findsOneWidget);
    // The host did not say whether the file existed: no guessed "Updated".
    expect(find.text('Updated'), findsNothing);
    await tester.tap(find.text('Write'));
    await tester.pump();
    expect(find.byType(KitDiffView), findsOneWidget);
    expect(_text("import 'dart:io';"), findsWidgets);
    expect(find.textContaining('Open full output'), findsNothing);
  });

  testWidgets('a task-list update is one line that does not open', (
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
    // The list itself is the tasks chip and its sheet, never a card here.
    expect(_text('Updated tasks'), findsOneWidget);
    expect(_text('1 of 2 done'), findsNothing);
    await tester.tap(find.byType(KitToolRow));
    await tester.pump();
    expect(find.text('Inspect contract'), findsNothing);
    expect(find.text('Run simulator'), findsNothing);
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
    // The server's own words sit under Details (no raw errors as copy).
    final error = find.byKey(const Key('standalone-tool-error-output'));
    expect(error, findsOneWidget);
    await tester.tap(
      find.descendant(of: error, matching: find.text('Details')),
    );
    await tester.pumpAndSettle();
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
