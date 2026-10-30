// Work Claude Code left running in the background (lib/domain/
// background_tasks.dart): Paseo's shell step that went to the background is
// matched to its later task_notification by the step's call id, and the app
// stops claiming it runs after the agent's process went down or after six
// hours. Payload shapes are Paseo 0.11.2's (claude/task-notification-tool-
// call.js and tool-call-detail-primitives.js; 0.9.2 is the same).
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/background_tasks.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/paseo/mappers.dart';

const _callId = 'toolu_01BuildOwner';
const _command =
    'cd /root/projects/IPTV_King && bash tool/build_tv_release.sh --owner';
final _started = DateTime.utc(2026, 10, 27, 7, 10);

MessageWithParts _bash({
  String callId = _callId,
  String output =
      'Command running in background with ID: b1x2y3. Output is being '
      'written to: /tmp/claude-1000/tasks/b1x2y3.output',
  String status = 'completed',
  DateTime? at,
  String command = _command,
  String? description,
}) => paseoItemMessage(
  'agent-1',
  {
    'type': 'tool_call',
    'callId': callId,
    'name': 'Bash',
    'status': status,
    'error': null,
    'detail': {
      'type': 'shell',
      'command': command,
      'cwd': '/root/projects/IPTV_King',
      'output': output,
      'description': ?description,
    },
  },
  id: callId,
  provider: 'claude',
  created: (at ?? _started).millisecondsSinceEpoch,
)!;

MessageWithParts _notice({
  String? toolUseId = _callId,
  String? taskId = 'b1x2y3',
  String status = 'completed',
}) => paseoItemMessage(
  'agent-1',
  {
    'type': 'tool_call',
    'callId': 'task_notification_${taskId ?? 'x'}',
    'name': 'task_notification',
    'status': 'completed',
    'error': null,
    'detail': {
      'type': 'plain_text',
      'label':
          'Background command "Build the owner APK" completed (exit code 0)',
      'icon': 'wrench',
    },
    'metadata': {
      'synthetic': true,
      'source': 'claude_task_notification',
      'taskId': ?taskId,
      'toolUseId': ?toolUseId,
      'status': status,
      'outputFile': '/tmp/claude-1000/tasks/b1x2y3.output',
    },
  },
  id: 'task_notification_${taskId ?? 'x'}',
  provider: 'claude',
  created: _started.add(const Duration(minutes: 9)).millisecondsSinceEpoch,
)!;

void main() {
  group('Paseo mapper', () {
    test('a shell step that went to the background is marked with its id', () {
      final part = _bash().parts.single;
      expect(part.toolName, 'bash');
      expect(part.callID, _callId);
      expect(part.toolState.metadata?[backgroundTaskMetadataKey], {
        'id': 'b1x2y3',
      });
    });

    // Claude's Bash call has a one-line description; Paseo 0.9.1's shell
    // detail does not carry it, so this is the shape a later one may send.
    test("the step's description, when Paseo passes it, names the task", () {
      final message = _bash(description: '  Wait for the owner build  ');
      expect(
        message.parts.single.toolState.metadata?[backgroundTaskMetadataKey],
        {'id': 'b1x2y3', 'label': 'Wait for the owner build'},
      );
      final task = runningBackgroundTasks([
        message,
      ], now: _started.add(const Duration(minutes: 2))).single;
      expect(task.name, 'Wait for the owner build');
      expect(task.waiting, isFalse);
      // A blank one is no name.
      expect(
        _bash(
          description: ' ',
        ).parts.single.toolState.metadata?[backgroundTaskMetadataKey],
        {'id': 'b1x2y3'},
      );
    });

    test('an ordinary shell step carries no marker', () {
      final part = _bash(output: 'Built in 12 s').parts.single;
      expect(part.toolState.metadata?[backgroundTaskMetadataKey], isNull);
    });

    test('the notice keeps which call and task it ended', () {
      final input = _notice().parts.single.toolState.input;
      expect(input['toolUseId'], _callId);
      expect(input['taskId'], 'b1x2y3');
      expect(input['status'], 'completed');
      // The output file's path is not something the app shows.
      expect(input.values.join(' '), isNot(contains('/tmp/')));
    });
  });

  // Owner report 2026-10-29: after a network drop a Claude Code turn ended
  // on "API Error: Can't reach the API server …", drawn as the reply's
  // prose; nobody could tell the agent had stopped.
  group("Claude Code's API failure line", () {
    MessageWithParts said(String text, {String id = 'a9'}) => paseoItemMessage(
      'agent-1',
      {'type': 'assistant_message', 'text': text, 'messageId': id},
      id: id,
      provider: 'claude',
      created: _started.millisecondsSinceEpoch,
    )!;

    test('is a failed turn, never prose', () {
      for (final line in [
        "API Error: Can't reach the API server — check your internet or DNS "
            '(EAI_AGAIN)',
        'API Error: 529 {"type":"error","error":{"type":"overloaded_error"}}',
        'API Error: Request timed out.',
        'Claude AI usage limit reached|1761836400',
        'Credit balance is too low',
      ]) {
        final message = said(line);
        expect(message.parts, isEmpty, reason: line);
        expect(message.info.errorText, line);
        expect(message.info.providerID, 'claude');
      }
    });

    test('words that only mention it stay a reply', () {
      final message = said(
        'The API Error: line you saw came from the proxy; I fixed it.',
      );
      expect(message.info.errorText, isNull);
      expect(message.parts.single.text, startsWith('The API Error'));
    });

    test('the book holds the turn failed until the next prompt', () {
      final book = BackgroundTaskBook(clock: () => _started);
      book.replace('agent-1', [
        _bash(),
        said("API Error: Can't reach the API server (EAI_AGAIN)"),
      ]);
      expect(book.turnFailed('agent-1'), isTrue);
      expect(book.running('agent-1'), isTrue);
      // A task's notice is not the agent going on.
      book.observe('agent-1', _notice());
      expect(book.turnFailed('agent-1'), isTrue);
      book.observe(
        'agent-1',
        paseoItemMessage(
          'agent-1',
          {'type': 'user_message', 'text': 'Continue', 'messageId': 'u2'},
          id: 'u2',
          provider: 'claude',
        )!,
      );
      expect(book.turnFailed('agent-1'), isFalse);
      expect(
        lastTurnFailedIn([_bash(), said('API Error: Request timed out.')]),
        isTrue,
      );
      expect(
        lastTurnFailedIn([
          said('API Error: Request timed out.'),
          said('Picked up again; the build is done.', id: 'a10'),
        ]),
        isFalse,
      );
    });
  });

  group('runningBackgroundTasks', () {
    final now = _started.add(const Duration(minutes: 2));

    test('a background command runs until its notice names it', () {
      final running = runningBackgroundTasks([_bash()], now: now);
      expect(running, hasLength(1));
      final task = running.single;
      expect(task.callID, _callId);
      expect(task.messageID, _callId);
      expect(task.name, 'build_tv_release.sh');
      expect(task.since.isAtSameMomentAs(_started), isTrue);
      expect(runningBackgroundTasks([_bash(), _notice()], now: now), isEmpty);
    });

    test('a notice that names only the task id ends it too', () {
      expect(
        runningBackgroundTasks([_bash(), _notice(toolUseId: null)], now: now),
        isEmpty,
      );
    });

    test("another task's notice leaves it running", () {
      expect(
        runningBackgroundTasks([
          _bash(),
          _notice(toolUseId: 'toolu_other', taskId: 'zz9'),
        ], now: now),
        hasLength(1),
      );
    });

    test('several tasks, oldest first', () {
      final second = _bash(
        callId: 'toolu_02Tests',
        at: _started.add(const Duration(minutes: 1)),
      );
      expect(
        runningBackgroundTasks([
          _bash(),
          second,
        ], now: now).map((t) => t.callID),
        [_callId, 'toolu_02Tests'],
      );
    });

    test('older than six hours it stops showing', () {
      expect(
        runningBackgroundTasks([
          _bash(),
        ], now: _started.add(const Duration(hours: 5, minutes: 59))),
        hasLength(1),
      );
      expect(
        runningBackgroundTasks([
          _bash(),
        ], now: _started.add(backgroundTaskMaxAge)),
        isEmpty,
      );
    });

    test('the agent process seen down after it started ends it', () {
      expect(
        runningBackgroundTasks(
          [_bash()],
          now: now,
          runtimeGapAt: _started.add(const Duration(minutes: 1)),
        ),
        isEmpty,
      );
      // A process that came back before the work started keeps it.
      expect(
        runningBackgroundTasks(
          [_bash()],
          now: now,
          runtimeGapAt: _started.subtract(const Duration(minutes: 1)),
        ),
        hasLength(1),
      );
    });

    // Owner, 2026-10-29: "Still running in the background · grep" for a
    // loop that waits for a build to print its last line.
    test('a shell loop names what it waits on', () {
      final task = runningBackgroundTasks([
        _bash(
          command:
              'until grep -q "BUILD SUCCESSFUL" /tmp/build.log; do sleep 30; '
              'done',
        ),
      ], now: now).single;
      expect(task.waiting, isTrue);
      expect(task.name, 'grep');
      expect(
        task.command,
        'until grep -q "BUILD SUCCESSFUL" /tmp/build.log; do sleep 30; done',
      );
      expect(
        runningBackgroundTasks([_bash()], now: now).single.waiting,
        isFalse,
      );
    });

    test('a step still running is the live turn, not background work', () {
      expect(
        runningBackgroundTasks([_bash(status: 'running')], now: now),
        isEmpty,
      );
    });
  });

  group('backgroundTaskName', () {
    test('the program or script, never its folder', () {
      expect(backgroundTaskName(_command), 'build_tv_release.sh');
      expect(
        backgroundTaskName('flutter build apk --release'),
        'flutter build apk',
      );
      expect(
        backgroundTaskName('FOO=1 nohup ./gradlew assembleRelease > log 2>&1'),
        'gradlew assembleRelease',
      );
      expect(
        backgroundTaskName('timeout 600 python3 -u tools/serve.py --port 8'),
        'serve.py',
      );
      expect(backgroundTaskName('sleep 300'), 'sleep');
      expect(backgroundTaskName(''), 'a command');
    });
  });

  group('backgroundTaskWaitsOn', () {
    test('the program a loop at the end of a command waits on', () {
      expect(
        backgroundTaskWaitsOn('until grep -q ready log; do sleep 30; done'),
        'grep',
      );
      expect(
        backgroundTaskWaitsOn(
          'while ! curl -sf localhost:8080 >/dev/null; do sleep 5; done',
        ),
        'curl',
      );
      expect(
        backgroundTaskWaitsOn(
          "timeout 600 bash -c 'until grep -q X f; do sleep 30; done'",
        ),
        'grep',
      );
      expect(
        backgroundTaskWaitsOn(
          'cd app && until adb shell getprop sys.boot_completed | grep 1; '
          'do sleep 2; done',
        ),
        'adb',
      );
      // A bracket test names what it looks at, else the body's command.
      expect(
        backgroundTaskWaitsOn(
          'while [ ! -f build/out/app.apk ]; do sleep 10; done',
        ),
        'app.apk',
      );
      expect(
        backgroundTaskWaitsOn('while true; do flutter test; sleep 9; done'),
        'flutter',
      );
    });

    test('a command that does not end in a loop waits on nothing', () {
      expect(backgroundTaskWaitsOn(_command), isNull);
      expect(
        backgroundTaskWaitsOn('until grep -q ready log; do sleep 3; done; ls'),
        isNull,
      );
      expect(backgroundTaskWaitsOn(''), isNull);
    });
  });

  group('BackgroundTaskBook', () {
    test('a full read, a streamed notice and a process gap', () {
      var now = _started.add(const Duration(minutes: 2));
      final book = BackgroundTaskBook(clock: () => now);
      final changes = <void>[];
      final sub = book.changes.listen(changes.add);
      addTearDown(sub.cancel);

      book.replace('agent-1', [_bash()]);
      expect(book.running('agent-1'), isTrue);
      book.observe('agent-1', _notice());
      expect(book.running('agent-1'), isFalse);

      book.replace('agent-1', [_bash(callId: 'toolu_03')]);
      expect(book.running('agent-1'), isTrue);
      book.runtimeGap('agent-1', now);
      expect(book.running('agent-1'), isFalse);
      expect(book.gapAt('agent-1'), now);

      // Work started after the process came back runs again.
      now = now.add(const Duration(minutes: 5));
      book.observe(
        'agent-1',
        _bash(callId: 'toolu_04', at: now.subtract(const Duration(minutes: 1))),
      );
      expect(book.running('agent-1'), isTrue);
      expect(book.running('agent-2'), isFalse);
      expect(book.revision, greaterThan(0));
    });
  });
}
