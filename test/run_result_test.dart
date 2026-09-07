import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/run_result.dart';

MessageWithParts _msg(
  String id, {
  required String role,
  required int created,
  int? completed,
  String? finish,
  String? error,
  MessageErrorKind? errorKind,
  String? agent,
  String? model,
  List<Part> parts = const [],
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'ses_1',
    role: role,
    agent: agent,
    modelID: model,
    time: MsgTime(created: created, completed: completed),
    errorText: error,
    errorKind: errorKind,
    finish: finish,
  ),
  parts: parts,
);

Part _tool(
  String id,
  String name, {
  String status = 'completed',
  Map<String, dynamic> input = const {},
  Map<String, dynamic>? metadata,
  String output = '',
  bool pruned = false,
  bool executed = true,
}) => Part(
  id: id,
  callID: id,
  type: 'tool',
  toolName: name,
  toolState: ToolState(
    status: status,
    input: input,
    output: output,
    metadata: metadata,
    pruned: pruned,
    executed: executed,
  ),
);

void main() {
  group('grouping', () {
    test('the run is the assistant steps after the latest user message', () {
      final result = RunResult.fromMessages('ses_1', [
        // Deliberately out of order; newest first like a server page.
        _msg(
          'a3',
          role: 'assistant',
          created: 30,
          completed: 31,
          finish: 'stop',
        ),
        _msg('u2', role: 'user', created: 20),
        _msg(
          'a1',
          role: 'assistant',
          created: 11,
          completed: 12,
          finish: 'stop',
          parts: [
            _tool('t-old', 'edit', input: {'filePath': 'old.dart'}),
          ],
        ),
        _msg('u0', role: 'user', created: 10),
        _msg(
          'a2',
          role: 'assistant',
          created: 25,
          completed: 26,
          finish: 'tool-calls',
          parts: [
            _tool('t-new', 'write', input: {'filePath': 'new.dart'}),
          ],
        ),
      ])!;
      expect(result.runID, 'a2');
      expect(result.lastStepID, 'a3');
      expect(result.userMessageID, 'u2');
      expect(result.stepCount, 2);
      expect(result.boundaryKnown, isTrue);
      expect(result.changedFiles.map((f) => f.path), ['new.dart']);
      expect(result.startedAt, DateTime.fromMillisecondsSinceEpoch(20));
      expect(result.finishedAt, DateTime.fromMillisecondsSinceEpoch(31));
    });

    test('no assistant step after the latest user message means no run', () {
      expect(
        RunResult.fromMessages('ses_1', [
          _msg('a1', role: 'assistant', created: 1, completed: 2),
          _msg('u2', role: 'user', created: 3),
        ]),
        isNull,
      );
      expect(RunResult.fromMessages('ses_1', const []), isNull);
    });

    test('a missing user boundary is reported, not assumed', () {
      final partial = RunResult.fromMessages('ses_1', [
        _msg('a1', role: 'assistant', created: 1, completed: 2, finish: 'stop'),
      ])!;
      expect(partial.boundaryKnown, isFalse);
      final complete = RunResult.fromMessages('ses_1', [
        _msg('a1', role: 'assistant', created: 1, completed: 2, finish: 'stop'),
      ], historyComplete: true)!;
      expect(complete.boundaryKnown, isTrue);
      expect(complete.startedAt, DateTime.fromMillisecondsSinceEpoch(1));
    });
  });

  group('outcome', () {
    RunResult single({
      int? completed = 2,
      String? finish,
      String? error,
      MessageErrorKind? kind,
    }) => RunResult.fromMessages('ses_1', [
      _msg('u', role: 'user', created: 0),
      _msg(
        'a',
        role: 'assistant',
        created: 1,
        completed: completed,
        finish: finish,
        error: error,
        errorKind: kind,
      ),
    ])!;

    test('is taken from server facts only', () {
      expect(single(finish: 'stop').outcome.kind, RunOutcomeKind.completed);
      expect(single(finish: 'length').outcome.kind, RunOutcomeKind.cutOff);
      expect(
        single(finish: 'tool-calls').outcome.kind,
        RunOutcomeKind.notReported,
      );
      expect(single().outcome.kind, RunOutcomeKind.notReported);
      expect(single().outcome.finish, isNull);
      expect(
        single(completed: null, finish: null).outcome.kind,
        RunOutcomeKind.running,
      );
      final failed = single(
        error: 'ProviderError: rate limited\n  at x',
        kind: MessageErrorKind.unknown,
      );
      expect(failed.outcome.kind, RunOutcomeKind.failed);
      expect(failed.outcome.errorHeadline, 'rate limited');
      expect(
        single(error: 'aborted', kind: MessageErrorKind.aborted).outcome.kind,
        RunOutcomeKind.aborted,
      );
      expect(
        single(
          error: 'too long',
          kind: MessageErrorKind.outputLength,
        ).outcome.kind,
        RunOutcomeKind.cutOff,
      );
    });

    test('the newest step decides; earlier errors become a note', () {
      final result = RunResult.fromMessages('ses_1', [
        _msg('u', role: 'user', created: 0),
        _msg(
          'a1',
          role: 'assistant',
          created: 1,
          completed: 2,
          error: 'boom',
          errorKind: MessageErrorKind.unknown,
        ),
        _msg('a2', role: 'assistant', created: 3, completed: 4, finish: 'stop'),
      ])!;
      expect(result.outcome.kind, RunOutcomeKind.completed);
      expect(result.earlierStepErrors, 1);
      final reversed = RunResult.fromMessages('ses_1', [
        _msg('u', role: 'user', created: 0),
        _msg('a1', role: 'assistant', created: 1, completed: 2, finish: 'stop'),
        _msg(
          'a2',
          role: 'assistant',
          created: 3,
          completed: 4,
          error: 'boom',
          errorKind: MessageErrorKind.unknown,
        ),
      ])!;
      expect(reversed.outcome.kind, RunOutcomeKind.failed);
      expect(reversed.earlierStepErrors, 0);
    });
  });

  group('evidence', () {
    test('changed files come only from completed, executed edit tools', () {
      final result = RunResult.fromMessages('ses_1', [
        _msg('u', role: 'user', created: 0),
        _msg(
          'a',
          role: 'assistant',
          created: 1,
          completed: 2,
          finish: 'stop',
          parts: [
            _tool('t1', 'edit', input: {'filePath': 'lib/a.dart'}),
            _tool('t2', 'write', input: {'filePath': 'lib/b.dart'}),
            _tool(
              't3',
              'patch',
              metadata: {
                'files': [
                  {'path': 'lib/c.dart'},
                  {'path': 'lib/d.dart'},
                ],
              },
            ),
            _tool(
              't4',
              'edit',
              status: 'error',
              input: {'filePath': 'lib/failed.dart'},
            ),
            _tool(
              't5',
              'edit',
              executed: false,
              input: {'filePath': 'lib/never-ran.dart'},
            ),
            _tool('t6', 'edit', input: {'filePath': 'lib/a.dart'}),
            _tool('t7', 'read', input: {'filePath': 'lib/read-only.dart'}),
          ],
        ),
      ])!;
      expect(result.changedFiles.map((f) => f.path), [
        'lib/a.dart',
        'lib/b.dart',
        'lib/c.dart',
        'lib/d.dart',
      ]);
      expect(result.changedFiles.first.change, RunFileChange.edited);
      expect(result.changedFiles.first.partID, 't6');
      expect(result.changedFiles[1].change, RunFileChange.written);
      expect(result.changedFiles[2].change, RunFileChange.patched);
      expect(result.toolCount, 7);
      expect(result.erroredToolCount, 1);
      expect(result.hasToolEvidence, isTrue);
    });

    test('commands keep recorded exit codes and never invent them', () {
      final result = RunResult.fromMessages('ses_1', [
        _msg('u', role: 'user', created: 0),
        _msg(
          'a',
          role: 'assistant',
          created: 1,
          completed: 2,
          finish: 'stop',
          parts: [
            _tool(
              'c1',
              'bash',
              input: {'command': 'flutter test test/x.dart'},
              metadata: {'exit': 0},
              output: 'All tests passed!',
            ),
            _tool(
              'c2',
              'bash',
              status: 'error',
              input: {'command': 'npm test'},
              metadata: {'exit': 1},
            ),
            _tool('c3', 'bash', input: {'command': 'ls test'}),
            _tool(
              'c4',
              'shell',
              input: {'command': 'pytest -q'},
              metadata: {'exit': '0'},
              pruned: true,
            ),
            _tool(
              'c5',
              'bash',
              status: 'running',
              input: {'command': 'sleep 100'},
            ),
          ],
        ),
      ])!;
      expect(result.commands.length, 4);
      expect(result.commands[0].exitCode, 0);
      expect(result.commands[0].looksLikeTest, isTrue);
      expect(result.commands[0].failed, isFalse);
      expect(result.commands[1].exitCode, 1);
      expect(result.commands[1].failed, isTrue);
      expect(result.commands[1].looksLikeTest, isTrue);
      expect(result.commands[2].exitCode, isNull);
      expect(result.commands[2].looksLikeTest, isFalse);
      expect(result.commands[3].exitCode, 0);
      expect(result.commands[3].outputPruned, isTrue);
      expect(result.prunedToolCount, 1);
    });

    test('test-command detection is textual and conservative', () {
      expect(RunResult.looksLikeTestCommand('flutter test'), isTrue);
      expect(RunResult.looksLikeTestCommand('cd app && npm run test'), isTrue);
      expect(RunResult.looksLikeTestCommand('cargo test --all'), isTrue);
      expect(RunResult.looksLikeTestCommand('go test ./...'), isTrue);
      expect(RunResult.looksLikeTestCommand('pytest tests/'), isTrue);
      expect(RunResult.looksLikeTestCommand('make test'), isTrue);
      expect(
        RunResult.looksLikeTestCommand('git commit -m "add test"'),
        isFalse,
      );
      expect(RunResult.looksLikeTestCommand('ls tests'), isFalse);
      expect(RunResult.looksLikeTestCommand('echo testing'), isFalse);
      expect(RunResult.looksLikeTestCommand('npm run build'), isFalse);
      expect(RunResult.looksLikeTestCommand(''), isFalse);
    });

    test('no tool parts is reported as no evidence, not as no changes', () {
      final result = RunResult.fromMessages('ses_1', [
        _msg('u', role: 'user', created: 0),
        _msg(
          'a',
          role: 'assistant',
          created: 1,
          completed: 2,
          finish: 'stop',
          parts: [Part(type: 'text', text: 'I edited three files.')],
        ),
      ])!;
      expect(result.hasToolEvidence, isFalse);
      expect(result.changedFiles, isEmpty);
      expect(result.commands, isEmpty);
    });

    test('lists and command text are bounded', () {
      final long = 'x' * 300;
      final result = RunResult.fromMessages('ses_1', [
        _msg('u', role: 'user', created: 0),
        _msg(
          'a',
          role: 'assistant',
          created: 1,
          completed: 2,
          finish: 'stop',
          parts: [
            for (var i = 0; i < 60; i++)
              _tool('c$i', 'bash', input: {'command': i == 0 ? long : 'ls'}),
            for (var i = 0; i < 60; i++)
              _tool('e$i', 'edit', input: {'filePath': 'lib/f$i.dart'}),
          ],
        ),
      ])!;
      expect(result.commands.length, RunResult.maxCommands);
      expect(result.changedFiles.length, RunResult.maxChangedFiles);
      expect(result.truncated, isTrue);
      expect(
        result.commands.first.command.length,
        RunResult.maxCommandLength + 1,
      );
      expect(result.commands.first.command.endsWith('…'), isTrue);
    });
  });
}
